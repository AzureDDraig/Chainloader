package net.chainloader.loader.access;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.*;

/**
 * AccessWidener is the runtime bytecode transformation engine for ChainLoader.
 * It parses access widener configurations (compatible with the Fabric access-widener format)
 * and rewrites class, field, and method access modifiers (private/protected/final) to public/protected/non-final
 * using an ASM {@link ClassVisitor}.
 */
public class AccessWidener {

    /**
     * Defines the type of access modification to apply.
     */
    public enum AccessType {
        /**
         * Makes a class, field, or method public.
         */
        ACCESSIBLE,
        /**
         * Makes a class public and non-final, or a method protected/public and non-final.
         */
        EXTENDABLE,
        /**
         * Makes a field non-final (mutable).
         */
        MUTABLE
    }

    /**
     * Represents a unique identifier for a class member (field or method).
     */
    public static final class EntryKey {
        private final String owner;
        private final String name;
        private final String desc;

        public EntryKey(String owner, String name, String desc) {
            this.owner = Objects.requireNonNull(owner, "owner cannot be null").replace('.', '/');
            this.name = Objects.requireNonNull(name, "name cannot be null");
            this.desc = Objects.requireNonNull(desc, "desc cannot be null");
        }

        public String getOwner() {
            return owner;
        }

        public String getName() {
            return name;
        }

        public String getDesc() {
            return desc;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            EntryKey entryKey = (EntryKey) o;
            return owner.equals(entryKey.owner) &&
                   name.equals(entryKey.name) &&
                   desc.equals(entryKey.desc);
        }

        @Override
        public int hashCode() {
            return Objects.hash(owner, name, desc);
        }

        @Override
        public String toString() {
            return owner + "." + name + " " + desc;
        }
    }

    private final Map<String, Set<AccessType>> classAccess = new HashMap<>();
    private final Map<EntryKey, Set<AccessType>> methodAccess = new HashMap<>();
    private final Map<EntryKey, Set<AccessType>> fieldAccess = new HashMap<>();

    private String headerVersion;
    private String headerNamespace;

    public AccessWidener() {
        // Programmatic default rules
        addClassRule("bsr", AccessType.ACCESSIBLE);
        addFieldRule("bsr", "r", "Ldcw;", AccessType.ACCESSIBLE);
        addFieldRule("bsr", "r", "Ldcw;", AccessType.MUTABLE);
        addClassRule("cmu", AccessType.ACCESSIBLE);
        addFieldRule("cmu", "d", "Z", AccessType.ACCESSIBLE);
        addFieldRule("cmu", "d", "Z", AccessType.MUTABLE);
    }

    /**
     * Parses an access widener configuration from the given reader.
     * The input should follow the standard Fabric access-widener format:
     * <pre>
     * accessWidener v1 named
     * accessible class net/minecraft/class_310
     * extendable method net/minecraft/class_310 method_1514 ()V
     * mutable field net/minecraft/class_310 field_1234 I
     * </pre>
     *
     * @param reader the source reader containing the configuration
     * @throws IOException if an I/O error occurs or the format is invalid
     */
    public void parse(Reader reader) throws IOException {
        try (BufferedReader br = new BufferedReader(reader)) {
            String line;
            int lineNumber = 0;
            boolean headerParsed = false;

            while ((line = br.readLine()) != null) {
                lineNumber++;
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                String[] parts = line.split("\\s+");
                if (!headerParsed) {
                    if (!"accessWidener".equals(parts[0])) {
                        throw new IOException("Invalid access widener header on line " + lineNumber + ": expected 'accessWidener'");
                    }
                    if (parts.length < 3) {
                        throw new IOException("Invalid access widener header on line " + lineNumber + ": expected 'accessWidener <version> <namespace>'");
                    }
                    this.headerVersion = parts[1];
                    this.headerNamespace = parts[2];
                    headerParsed = true;
                    continue;
                }

                if (parts.length < 3) {
                    throw new IOException("Invalid rule on line " + lineNumber + ": expected '<directive> <type> <args>'");
                }

                String directive = parts[0];
                String type = parts[1];

                AccessType accessType = parseDirective(directive);
                if (accessType == null) {
                    throw new IOException("Unknown directive on line " + lineNumber + ": '" + directive + "'");
                }

                switch (type) {
                    case "class": {
                        String className = parts[2].replace('.', '/');
                        addClassRule(className, accessType);
                        break;
                    }
                    case "field": {
                        if (parts.length < 5) {
                            throw new IOException("Invalid field rule on line " + lineNumber + ": expected '<directive> field <className> <fieldName> <fieldDesc>'");
                        }
                        String className = parts[2].replace('.', '/');
                        String fieldName = parts[3];
                        String fieldDesc = parts[4];
                        addFieldRule(className, fieldName, fieldDesc, accessType);
                        break;
                    }
                    case "method": {
                        if (parts.length < 5) {
                            throw new IOException("Invalid method rule on line " + lineNumber + ": expected '<directive> method <className> <methodName> <methodDesc>'");
                        }
                        String className = parts[2].replace('.', '/');
                        String methodName = parts[3];
                        String methodDesc = parts[4];
                        addMethodRule(className, methodName, methodDesc, accessType);
                        break;
                    }
                    default:
                        throw new IOException("Unknown target type on line " + lineNumber + ": '" + type + "'");
                }
            }

            if (!headerParsed) {
                throw new IOException("Empty or invalid access widener file: missing header line starting with 'accessWidener'");
            }
        }
    }

    private AccessType parseDirective(String directive) {
        switch (directive) {
            case "accessible":
            case "transitive-accessible":
                return AccessType.ACCESSIBLE;
            case "extendable":
            case "transitive-extendable":
                return AccessType.EXTENDABLE;
            case "mutable":
            case "transitive-mutable":
                return AccessType.MUTABLE;
            default:
                return null;
        }
    }

    public void addClassRule(String className, AccessType type) {
        classAccess.computeIfAbsent(className, k -> EnumSet.noneOf(AccessType.class)).add(type);
    }

    public void addFieldRule(String className, String fieldName, String fieldDesc, AccessType type) {
        // Declaring class must be accessible if field is accessible or mutable
        addClassRule(className, AccessType.ACCESSIBLE);
        EntryKey key = new EntryKey(className, fieldName, fieldDesc);
        fieldAccess.computeIfAbsent(key, k -> EnumSet.noneOf(AccessType.class)).add(type);
    }

    public void addMethodRule(String className, String methodName, String methodDesc, AccessType type) {
        // Declaring class must be extendable/accessible if method is
        if (type == AccessType.EXTENDABLE) {
            addClassRule(className, AccessType.EXTENDABLE);
        } else {
            addClassRule(className, AccessType.ACCESSIBLE);
        }
        EntryKey key = new EntryKey(className, methodName, methodDesc);
        methodAccess.computeIfAbsent(key, k -> EnumSet.noneOf(AccessType.class)).add(type);
    }

    public String getHeaderVersion() {
        return headerVersion;
    }

    public String getHeaderNamespace() {
        return headerNamespace;
    }

    public Map<String, Set<AccessType>> getClassRules() {
        return Collections.unmodifiableMap(classAccess);
    }

    public Map<EntryKey, Set<AccessType>> getFieldRules() {
        return Collections.unmodifiableMap(fieldAccess);
    }

    public Map<EntryKey, Set<AccessType>> getMethodRules() {
        return Collections.unmodifiableMap(methodAccess);
    }

    /**
     * Creates an ASM {@link ClassVisitor} that will apply these access widener rules to bytecode on the fly.
     * Defaults to the ASM9 API level.
     *
     * @param cv the delegate class visitor
     * @return the rewriting class visitor
     */
    public ClassVisitor createVisitor(ClassVisitor cv) {
        return new AccessWidenerClassVisitor(Opcodes.ASM9, cv);
    }

    /**
     * Creates an ASM {@link ClassVisitor} that will apply these access widener rules to bytecode on the fly.
     *
     * @param api the ASM API level (e.g. {@link Opcodes#ASM9})
     * @param cv  the delegate class visitor
     * @return the rewriting class visitor
     */
    public ClassVisitor createVisitor(int api, ClassVisitor cv) {
        return new AccessWidenerClassVisitor(api, cv);
    }

    private class AccessWidenerClassVisitor extends ClassVisitor {
        private String currentClassName;

        public AccessWidenerClassVisitor(int api, ClassVisitor cv) {
            super(api, cv);
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            this.currentClassName = name;
            Set<AccessType> rules = classAccess.get(name);
            if (rules != null) {
                access = widenClassAccess(access, rules);
            }
            super.visit(version, access, name, signature, superName, interfaces);
        }

        @Override
        public void visitInnerClass(String name, String outerName, String innerName, int access) {
            Set<AccessType> rules = classAccess.get(name);
            if (rules != null) {
                access = widenClassAccess(access, rules);
            }
            super.visitInnerClass(name, outerName, innerName, access);
        }

        @Override
        public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
            EntryKey key = new EntryKey(currentClassName, name, descriptor);
            Set<AccessType> rules = fieldAccess.get(key);
            if (rules != null) {
                access = widenFieldAccess(access, rules);
            }
            return super.visitField(access, name, descriptor, signature, value);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            EntryKey key = new EntryKey(currentClassName, name, descriptor);
            Set<AccessType> rules = methodAccess.get(key);
            if (rules != null) {
                access = widenMethodAccess(name, access, rules);
            }
            return super.visitMethod(access, name, descriptor, signature, exceptions);
        }

        private int widenClassAccess(int access, Set<AccessType> rules) {
            if (rules.contains(AccessType.EXTENDABLE)) {
                access &= ~Opcodes.ACC_FINAL;
            }
            if (rules.contains(AccessType.ACCESSIBLE) || rules.contains(AccessType.EXTENDABLE)) {
                access &= ~(Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED);
                access |= Opcodes.ACC_PUBLIC;
            }
            return access;
        }

        private int widenFieldAccess(int access, Set<AccessType> rules) {
            if (rules.contains(AccessType.ACCESSIBLE)) {
                access &= ~(Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED);
                access |= Opcodes.ACC_PUBLIC;
            }
            if (rules.contains(AccessType.MUTABLE)) {
                access &= ~Opcodes.ACC_FINAL;
            }
            return access;
        }

        private int widenMethodAccess(String methodName, int access, Set<AccessType> rules) {
            if (rules.contains(AccessType.EXTENDABLE)) {
                access &= ~Opcodes.ACC_FINAL;
                // If not already public, make it protected
                if ((access & Opcodes.ACC_PUBLIC) == 0) {
                    access &= ~(Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED);
                    access |= Opcodes.ACC_PROTECTED;
                }
            } else if (rules.contains(AccessType.ACCESSIBLE)) {
                boolean wasPrivate = (access & Opcodes.ACC_PRIVATE) != 0;
                access &= ~(Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED);
                access |= Opcodes.ACC_PUBLIC;
                // Add final to originally private methods (excluding constructors and static initializers)
                // to maintain virtual call target semantics
                if (wasPrivate && !"<init>".equals(methodName) && !"<clinit>".equals(methodName)) {
                    access |= Opcodes.ACC_FINAL;
                }
            }
            return access;
        }
    }
}
