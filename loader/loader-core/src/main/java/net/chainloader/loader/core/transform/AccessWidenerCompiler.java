package net.chainloader.loader.core.transform;

import net.chainloader.loader.access.AccessWidener;
import net.chainloader.loader.access.AccessWidener.AccessType;
import net.chainloader.loader.access.AccessWidener.EntryKey;
import org.objectweb.asm.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * AccessWidenerCompiler compiles high-level Access Widener rules into high-performance
 * runtime ASM visitors and class transformers.
 *
 * <p>To maximize classloading speed, it uses a compiled fast-path registry that skips classes
 * without rules, and coordinates with the Mixin Redirector and Annotation Stripper subagents
 * to apply transformations in a single unified ASM pass.</p>
 */
public class AccessWidenerCompiler {

    private static final Logger LOGGER = Logger.getLogger(AccessWidenerCompiler.class.getName());

    // Compiled lookup structures for O(1) checks during transformation
    private final Set<String> compiledClasses = ConcurrentHashMap.newKeySet();
    private final Map<String, Map<String, AccessType>> compiledFields = new ConcurrentHashMap<>();
    private final Map<String, Map<String, AccessType>> compiledMethods = new ConcurrentHashMap<>();

    // Registered subagent components
    private MixinRedirector mixinRedirector;
    private AnnotationStripper annotationStripper;

    /**
     * Interface for the Mixin Redirector subagent to coordinate and hook into the compilation/transformation pipeline.
     */
    public interface MixinRedirector {
        /**
         * Returns a redirected owner, name, or descriptor for a method call/field access.
         * Useful if access widening requires callsite redirection (e.g. private to public final virtual calls).
         *
         * @param owner the owner class
         * @param name the member name
         * @param desc the member descriptor
         * @param opcode the instruction opcode
         * @return the redirected target string representation, or null if no redirection is needed
         */
        String redirectMethodCall(String owner, String name, String desc, int opcode);

        /**
         * Adapts method visits inside the compiled visitor.
         *
         * @param className the name of the containing class
         * @param access the method access flags
         * @param name the method name
         * @param desc the method descriptor
         * @param mv the parent method visitor
         * @return the wrapped method visitor
         */
        MethodVisitor wrapMethodVisitor(String className, int access, String name, String desc, MethodVisitor mv);
    }

    /**
     * Interface for the Annotation Stripper subagent to coordinate and hook into the compilation/transformation pipeline.
     */
    public interface AnnotationStripper {
        /**
         * Determines if a given annotation descriptor should be stripped during compilation/transformation.
         *
         * @param desc the annotation descriptor
         * @return true if the annotation should be stripped, false otherwise
         */
        boolean shouldStripAnnotation(String desc);
    }

    public AccessWidenerCompiler() {
    }

    /**
     * Sets the Mixin redirector subagent for pipeline coordination.
     *
     * @param mixinRedirector the Mixin redirector implementation
     */
    public void setMixinRedirector(MixinRedirector mixinRedirector) {
        this.mixinRedirector = mixinRedirector;
        LOGGER.info("AccessWidenerCompiler: MixinRedirector subagent coordinated successfully.");
    }

    /**
     * Sets the Annotation stripper subagent for pipeline coordination.
     *
     * @param annotationStripper the Annotation stripper implementation
     */
    public void setAnnotationStripper(AnnotationStripper annotationStripper) {
        this.annotationStripper = annotationStripper;
        LOGGER.info("AccessWidenerCompiler: AnnotationStripper subagent coordinated successfully.");
    }

    /**
     * Compiles the rules from an AccessWidener instance into optimized runtime lookup structures.
     *
     * @param accessWidener the source access widener configuration
     */
    public void compile(AccessWidener accessWidener) {
        Objects.requireNonNull(accessWidener, "AccessWidener cannot be null");
        LOGGER.info("AccessWidenerCompiler: Compiling access configurations...");

        // 1. Compile class rules
        for (Map.Entry<String, Set<AccessType>> entry : accessWidener.getClassRules().entrySet()) {
            String className = entry.getKey();
            compiledClasses.add(className);
        }

        // 2. Compile field rules
        for (Map.Entry<EntryKey, Set<AccessType>> entry : accessWidener.getFieldRules().entrySet()) {
            EntryKey key = entry.getKey();
            String owner = key.getOwner();
            String fieldIdentifier = key.getName() + ":" + key.getDesc();
            
            // Resolve dominant access rule
            AccessType dominantType = getDominantAccessType(entry.getValue());
            compiledFields.computeIfAbsent(owner, k -> new ConcurrentHashMap<>()).put(fieldIdentifier, dominantType);
            compiledClasses.add(owner);
        }

        // 3. Compile method rules
        for (Map.Entry<EntryKey, Set<AccessType>> entry : accessWidener.getMethodRules().entrySet()) {
            EntryKey key = entry.getKey();
            String owner = key.getOwner();
            String methodIdentifier = key.getName() + ":" + key.getDesc();

            // Resolve dominant access rule
            AccessType dominantType = getDominantAccessType(entry.getValue());
            compiledMethods.computeIfAbsent(owner, k -> new ConcurrentHashMap<>()).put(methodIdentifier, dominantType);
            compiledClasses.add(owner);
        }

        LOGGER.log(Level.INFO, "Compiled access widener rules for {0} classes.", compiledClasses.size());
    }

    /**
     * Resolves the dominant access type when multiple directive types are present.
     */
    private AccessType getDominantAccessType(Set<AccessType> types) {
        if (types.contains(AccessType.MUTABLE)) {
            return AccessType.MUTABLE;
        }
        if (types.contains(AccessType.EXTENDABLE)) {
            return AccessType.EXTENDABLE;
        }
        return AccessType.ACCESSIBLE;
    }

    /**
     * Returns whether a class is targeted for any form of access widening.
     *
     * @param className the class name
     * @return true if the class has widening rules, false otherwise
     */
    public boolean isClassTargeted(String className) {
        return compiledClasses.contains(className.replace('.', '/'));
    }

    /**
     * Checks if a field has been compiled for access widening.
     *
     * @param owner the declaring class name
     * @param name the field name
     * @param desc the field descriptor
     * @return true if the field has widening rules, false otherwise
     */
    public boolean isFieldWidened(String owner, String name, String desc) {
        Map<String, AccessType> fields = compiledFields.get(owner.replace('.', '/'));
        return fields != null && fields.containsKey(name + ":" + desc);
    }

    /**
     * Checks if a method has been compiled for access widening.
     *
     * @param owner the declaring class name
     * @param name the method name
     * @param desc the method descriptor
     * @return true if the method has widening rules, false otherwise
     */
    public boolean isMethodWidened(String owner, String name, String desc) {
        Map<String, AccessType> methods = compiledMethods.get(owner.replace('.', '/'));
        return methods != null && methods.containsKey(name + ":" + desc);
    }

    /**
     * Compiles an ASM ClassVisitor for the given class, applying compiled access widening rules
     * along with coordinated Mixin redirecting and Annotation stripping in a single pass.
     *
     * @param api the ASM API level
     * @param cv  the delegate ClassVisitor
     * @param className the name of the class being visited (internal name format, e.g., net/minecraft/client/Minecraft)
     * @return an optimized ClassVisitor
     */
    public ClassVisitor compileVisitor(int api, ClassVisitor cv, String className) {
        String internalName = className.replace('.', '/');
        
        // Fast-path: Check if we have any business with this class, mixin redirector, or annotation stripper
        boolean hasAccessWidenerRules = compiledClasses.contains(internalName);
        boolean hasStripper = (annotationStripper != null);
        boolean hasRedirector = (mixinRedirector != null);

        if (!hasAccessWidenerRules && !hasStripper && !hasRedirector) {
            return cv; // Pass-through
        }

        return new CompiledAccessWidenerVisitor(api, cv, internalName, hasAccessWidenerRules);
    }

    /**
     * Helper to create a ClassTransformer wrapper around this compiler.
     *
     * @return the ClassTransformer
     */
    public net.chainloader.loader.core.ChainClassLoader.ClassTransformer createClassTransformer() {
        return (className, classBytes) -> {
            if (classBytes == null || classBytes.length == 0) {
                return classBytes;
            }

            String internalName = className.replace('.', '/');
            if (!isClassTargeted(internalName) && annotationStripper == null && mixinRedirector == null) {
                return classBytes;
            }

            try {
                ClassReader reader = new ClassReader(classBytes);
                ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
                ClassVisitor visitor = compileVisitor(Opcodes.ASM9, writer, internalName);
                reader.accept(visitor, ClassReader.EXPAND_FRAMES);
                return writer.toByteArray();
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Failed executing compiled transformations on class " + className, e);
                return classBytes;
            }
        };
    }

    /**
     * The unified ClassVisitor compiled by this compiler, carrying out all transformations in a single pass.
     */
    private class CompiledAccessWidenerVisitor extends ClassVisitor {
        private final String classInternalName;
        private final boolean applyWidening;

        public CompiledAccessWidenerVisitor(int api, ClassVisitor cv, String classInternalName, boolean applyWidening) {
            super(api, cv);
            this.classInternalName = classInternalName;
            this.applyWidening = applyWidening;
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            if (applyWidening) {
                access = widenClassAccess(access);
            }
            super.visit(version, access, name, signature, superName, interfaces);
        }

        @Override
        public void visitInnerClass(String name, String outerName, String innerName, int access) {
            if (applyWidening && compiledClasses.contains(name)) {
                access = widenClassAccess(access);
            }
            super.visitInnerClass(name, outerName, innerName, access);
        }

        private int widenClassAccess(int access) {
            access &= ~(Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED);
            access |= Opcodes.ACC_PUBLIC;
            access &= ~Opcodes.ACC_FINAL;
            return access;
        }

        @Override
        public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
            if (applyWidening) {
                Map<String, AccessType> fields = compiledFields.get(classInternalName);
                if (fields != null) {
                    AccessType type = fields.get(name + ":" + descriptor);
                    if (type != null) {
                        if (type == AccessType.ACCESSIBLE || type == AccessType.MUTABLE) {
                            access &= ~(Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED);
                            access |= Opcodes.ACC_PUBLIC;
                        }
                        if (type == AccessType.MUTABLE) {
                            access &= ~Opcodes.ACC_FINAL;
                        }
                    }
                }
            }

            FieldVisitor fv = super.visitField(access, name, descriptor, signature, value);
            if (fv != null && annotationStripper != null) {
                fv = new StrippingFieldVisitor(api, fv);
            }
            return fv;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            if (applyWidening) {
                Map<String, AccessType> methods = compiledMethods.get(classInternalName);
                if (methods != null) {
                    AccessType type = methods.get(name + ":" + descriptor);
                    if (type != null) {
                        if (type == AccessType.EXTENDABLE) {
                            access &= ~Opcodes.ACC_FINAL;
                            if ((access & Opcodes.ACC_PUBLIC) == 0) {
                                access &= ~(Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED);
                                access |= Opcodes.ACC_PROTECTED;
                            }
                        } else if (type == AccessType.ACCESSIBLE) {
                            boolean wasPrivate = (access & Opcodes.ACC_PRIVATE) != 0;
                            access &= ~(Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED);
                            access |= Opcodes.ACC_PUBLIC;
                            if (wasPrivate && !"<init>".equals(name) && !"<clinit>".equals(name)) {
                                access |= Opcodes.ACC_FINAL;
                            }
                        }
                    }
                }
            }

            MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
            if (mv != null) {
                if (mixinRedirector != null) {
                    mv = mixinRedirector.wrapMethodVisitor(classInternalName, access, name, descriptor, mv);
                }
                if (annotationStripper != null) {
                    mv = new StrippingMethodVisitor(api, mv);
                }
            }
            return mv;
        }

        @Override
        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            if (annotationStripper != null && annotationStripper.shouldStripAnnotation(descriptor)) {
                LOGGER.log(Level.FINE, "Stripping class annotation: {0}", descriptor);
                return null;
            }
            return super.visitAnnotation(descriptor, visible);
        }
    }

    /**
     * MethodVisitor wrapper that handles stripping of annotations during class method parsing.
     */
    private class StrippingMethodVisitor extends MethodVisitor {
        public StrippingMethodVisitor(int api, MethodVisitor mv) {
            super(api, mv);
        }

        @Override
        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            if (annotationStripper != null && annotationStripper.shouldStripAnnotation(descriptor)) {
                LOGGER.log(Level.FINE, "Stripping method annotation: {0}", descriptor);
                return null;
            }
            return super.visitAnnotation(descriptor, visible);
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
            if (mixinRedirector != null) {
                String redirected = mixinRedirector.redirectMethodCall(owner, name, descriptor, opcode);
                if (redirected != null) {
                    int dotIndex = redirected.indexOf('.');
                    int colonIndex = redirected.indexOf(':');
                    if (dotIndex != -1 && colonIndex != -1) {
                        String newOwner = redirected.substring(0, dotIndex);
                        String newName = redirected.substring(dotIndex + 1, colonIndex);
                        String newDesc = redirected.substring(colonIndex + 1);
                        super.visitMethodInsn(opcode, newOwner, newName, newDesc, isInterface);
                        return;
                    }
                }
            }
            super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
        }
    }

    /**
     * FieldVisitor wrapper that handles stripping of annotations during class field parsing.
     */
    private class StrippingFieldVisitor extends FieldVisitor {
        public StrippingFieldVisitor(int api, FieldVisitor fv) {
            super(api, fv);
        }

        @Override
        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            if (annotationStripper != null && annotationStripper.shouldStripAnnotation(descriptor)) {
                LOGGER.log(Level.FINE, "Stripping field annotation: {0}", descriptor);
                return null;
            }
            return super.visitAnnotation(descriptor, visible);
        }
    }
}
