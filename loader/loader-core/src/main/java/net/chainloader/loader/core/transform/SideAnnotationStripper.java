package net.chainloader.loader.core.transform;

import net.chainloader.api.environment.EnvType;
import net.chainloader.loader.core.ChainClassLoader;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Strips classes, fields, and methods that are annotated with environment-specific annotations
 * (such as Fabric's {@code @Environment} or Forge/NeoForge's {@code @OnlyIn}) if they target a side
 * different from the active environment.
 * 
 * <p>To coordinate with subagents like the Mixin redirector and Access Widener, this class provides
 * queryable registries of stripped members to prevent applying modifications to non-existent code.</p>
 */
public class SideAnnotationStripper implements ChainClassLoader.ClassTransformer {

    private static final Logger LOGGER = Logger.getLogger(SideAnnotationStripper.class.getName());

    // Global registries for cross-subagent coordination
    private static final Set<String> STRIPPED_CLASSES = ConcurrentHashMap.newKeySet();
    private static final Set<String> STRIPPED_METHODS = ConcurrentHashMap.newKeySet();
    private static final Set<String> STRIPPED_FIELDS = ConcurrentHashMap.newKeySet();

    private final EnvType currentEnv;
    private final Map<String, AnnotationRule> annotationRules = new ConcurrentHashMap<>();

    // Instance registries for tracking
    private final Set<String> instanceStrippedClasses = ConcurrentHashMap.newKeySet();
    private final Set<String> instanceStrippedMethods = ConcurrentHashMap.newKeySet();
    private final Set<String> instanceStrippedFields = ConcurrentHashMap.newKeySet();

    /**
     * Exception thrown when a class itself is stripped, to be handled by the ClassLoader
     * to throw a ClassNotFoundException.
     */
    public static class SideStrippedException extends RuntimeException {
        private final String className;
        private final EnvType requiredEnv;
        private final EnvType currentEnv;

        public SideStrippedException(String className, EnvType requiredEnv, EnvType currentEnv) {
            super("Class " + className + " is stripped because it is annotated for " + requiredEnv + " but current environment is " + currentEnv);
            this.className = className;
            this.requiredEnv = requiredEnv;
            this.currentEnv = currentEnv;
        }

        public String getClassName() {
            return className;
        }

        public EnvType getRequiredEnv() {
            return requiredEnv;
        }

        public EnvType getCurrentEnv() {
            return currentEnv;
        }
    }

    /**
     * Defines a rule for parsing a side annotation.
     */
    public static class AnnotationRule {
        public final String annotationDesc;
        public final String enumDesc;
        public final Map<String, EnvType> valueMapping;

        public AnnotationRule(String annotationDesc, String enumDesc, Map<String, EnvType> valueMapping) {
            this.annotationDesc = annotationDesc;
            this.enumDesc = enumDesc;
            this.valueMapping = Collections.unmodifiableMap(new HashMap<>(valueMapping));
        }
    }

    /**
     * Creates a new SideAnnotationStripper for the given environment.
     *
     * @param currentEnv the current active environment type (CLIENT or SERVER)
     */
    public SideAnnotationStripper(EnvType currentEnv) {
        this.currentEnv = Objects.requireNonNull(currentEnv, "currentEnv cannot be null");
        registerDefaultRules();
    }

    private void registerDefaultRules() {
        // 1. Fabric @Environment annotation
        Map<String, EnvType> fabricMap = new HashMap<>();
        fabricMap.put("CLIENT", EnvType.CLIENT);
        fabricMap.put("SERVER", EnvType.SERVER);
        registerRule("Lnet/fabricmc/api/Environment;", "Lnet/fabricmc/api/EnvType;", fabricMap);

        // 2. Forge @OnlyIn annotation
        Map<String, EnvType> forgeMap = new HashMap<>();
        forgeMap.put("CLIENT", EnvType.CLIENT);
        forgeMap.put("DEDICATED_SERVER", EnvType.SERVER);
        registerRule("Lnet/minecraftforge/api/distmarker/OnlyIn;", "Lnet/minecraftforge/api/distmarker/Dist;", forgeMap);

        // 3. NeoForge @OnlyIn annotation
        Map<String, EnvType> neoForgeMap = new HashMap<>();
        neoForgeMap.put("CLIENT", EnvType.CLIENT);
        neoForgeMap.put("DEDICATED_SERVER", EnvType.SERVER);
        registerRule("Lnet/neoforged/api/distmarker/OnlyIn;", "Lnet/neoforged/api/distmarker/Dist;", neoForgeMap);
    }

    /**
     * Registers a custom side annotation rule.
     *
     * @param annotationDesc the descriptor of the annotation class (e.g. "Lmy/mod/MySideAnnotation;")
     * @param enumDesc       the descriptor of the enum class used for the environment value
     * @param valueMapping   a mapping from enum constant names to ChainLoader's EnvType
     */
    public void registerRule(String annotationDesc, String enumDesc, Map<String, EnvType> valueMapping) {
        annotationRules.put(annotationDesc, new AnnotationRule(annotationDesc, enumDesc, valueMapping));
    }

    @Override
    public byte[] transform(String className, byte[] classBytes) {
        if (classBytes == null || classBytes.length == 0) {
            return classBytes;
        }

        // Fast-path preview: avoid parsing using ASM if the constant pool does not contain our annotations
        if (!containsSideAnnotations(classBytes)) {
            return classBytes;
        }

        try {
            ClassReader reader = new ClassReader(classBytes);
            ClassNode classNode = new ClassNode();
            reader.accept(classNode, 0);

            String internalName = className.replace('.', '/');

            // 1. Check class-level annotations
            if (classNode.visibleAnnotations != null) {
                for (AnnotationNode annot : classNode.visibleAnnotations) {
                    EnvType required = getAnnotatedEnvironment(annot);
                    if (required != null && required != currentEnv) {
                        STRIPPED_CLASSES.add(internalName);
                        instanceStrippedClasses.add(internalName);
                        LOGGER.log(Level.WARNING, "Stripping class {0} because it is annotated for {1}", new Object[]{className, required});
                        throw new SideStrippedException(className, required, currentEnv);
                    }
                }
            }
            if (classNode.invisibleAnnotations != null) {
                for (AnnotationNode annot : classNode.invisibleAnnotations) {
                    EnvType required = getAnnotatedEnvironment(annot);
                    if (required != null && required != currentEnv) {
                        STRIPPED_CLASSES.add(internalName);
                        instanceStrippedClasses.add(internalName);
                        LOGGER.log(Level.WARNING, "Stripping class {0} because it is annotated for {1}", new Object[]{className, required});
                        throw new SideStrippedException(className, required, currentEnv);
                    }
                }
            }

            boolean modified = false;

            // 2. Filter fields
            if (classNode.fields != null) {
                Iterator<FieldNode> fieldIterator = classNode.fields.iterator();
                while (fieldIterator.hasNext()) {
                    FieldNode field = fieldIterator.next();
                    boolean stripField = false;

                    if (field.visibleAnnotations != null) {
                        for (AnnotationNode annot : field.visibleAnnotations) {
                            EnvType required = getAnnotatedEnvironment(annot);
                            if (required != null && required != currentEnv) {
                                stripField = true;
                                break;
                            }
                        }
                    }
                    if (!stripField && field.invisibleAnnotations != null) {
                        for (AnnotationNode annot : field.invisibleAnnotations) {
                            EnvType required = getAnnotatedEnvironment(annot);
                            if (required != null && required != currentEnv) {
                                stripField = true;
                                break;
                            }
                        }
                    }

                    if (stripField) {
                        String fieldKey = internalName + "." + field.name + ":" + field.desc;
                        STRIPPED_FIELDS.add(fieldKey);
                        instanceStrippedFields.add(fieldKey);
                        LOGGER.log(Level.INFO, "Stripping field {0} from class {1} (requires different side)", new Object[]{field.name, className});
                        fieldIterator.remove();
                        modified = true;
                    }
                }
            }

            // 3. Filter methods
            if (classNode.methods != null) {
                Iterator<MethodNode> methodIterator = classNode.methods.iterator();
                while (methodIterator.hasNext()) {
                    MethodNode method = methodIterator.next();
                    boolean stripMethod = false;

                    if (method.visibleAnnotations != null) {
                        for (AnnotationNode annot : method.visibleAnnotations) {
                            EnvType required = getAnnotatedEnvironment(annot);
                            if (required != null && required != currentEnv) {
                                stripMethod = true;
                                break;
                            }
                        }
                    }
                    if (!stripMethod && method.invisibleAnnotations != null) {
                        for (AnnotationNode annot : method.invisibleAnnotations) {
                            EnvType required = getAnnotatedEnvironment(annot);
                            if (required != null && required != currentEnv) {
                                stripMethod = true;
                                break;
                            }
                        }
                    }

                    if (stripMethod) {
                        String methodKey = internalName + "." + method.name + method.desc;
                        STRIPPED_METHODS.add(methodKey);
                        instanceStrippedMethods.add(methodKey);
                        LOGGER.log(Level.INFO, "Stripping method {0}{1} from class {2} (requires different side)", new Object[]{method.name, method.desc, className});
                        methodIterator.remove();
                        modified = true;
                    }
                }
            }

            if (modified) {
                ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
                classNode.accept(writer);
                return writer.toByteArray();
            }

        } catch (SideStrippedException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to strip side annotations for " + className, e);
        }

        return classBytes;
    }

    private boolean containsSideAnnotations(byte[] classBytes) {
        for (String desc : annotationRules.keySet()) {
            byte[] descBytes = desc.getBytes(StandardCharsets.UTF_8);
            if (indexOf(classBytes, descBytes) != -1) {
                return true;
            }
        }
        return false;
    }

    private static int indexOf(byte[] array, byte[] target) {
        if (target.length == 0) return 0;
        outer:
        for (int i = 0; i < array.length - target.length + 1; i++) {
            for (int j = 0; j < target.length; j++) {
                if (array[i + j] != target[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    private EnvType getAnnotatedEnvironment(AnnotationNode annotationNode) {
        if (annotationNode == null || annotationNode.desc == null) {
            return null;
        }

        AnnotationRule rule = annotationRules.get(annotationNode.desc);
        if (rule == null) {
            return null;
        }

        if (annotationNode.values != null) {
            for (int i = 0; i < annotationNode.values.size(); i += 2) {
                String name = (String) annotationNode.values.get(i);
                Object val = annotationNode.values.get(i + 1);

                if ("value".equals(name)) {
                    if (val instanceof String[]) {
                        String[] enumData = (String[]) val;
                        if (enumData.length == 2 && enumData[0].equals(rule.enumDesc)) {
                            String enumValue = enumData[1];
                            return rule.valueMapping.get(enumValue);
                        }
                    }
                }
            }
        }
        return null;
    }

    // --- Coordination API for Mixin and Access Compiler ---

    /**
     * Checks if a class has been stripped.
     *
     * @param className fully qualified class name in dot or slash notation
     * @return true if the class was stripped due to mismatching environment
     */
    public static boolean isClassStripped(String className) {
        return STRIPPED_CLASSES.contains(className.replace('.', '/'));
    }

    /**
     * Checks if a method has been stripped from a class.
     *
     * @param owner class name in dot or slash notation
     * @param name  method name
     * @param desc  method descriptor (e.g. "(Lnet/minecraft/client/gui/GuiGraphics;)V")
     * @return true if the method was stripped due to mismatching environment
     */
    public static boolean isMethodStripped(String owner, String name, String desc) {
        return STRIPPED_METHODS.contains(owner.replace('.', '/') + "." + name + desc);
    }

    /**
     * Checks if a field has been stripped from a class.
     *
     * @param owner class name in dot or slash notation
     * @param name  field name
     * @param desc  field descriptor (e.g. "Lnet/minecraft/client/color/block/BlockColors;")
     * @return true if the field was stripped due to mismatching environment
     */
    public static boolean isFieldStripped(String owner, String name, String desc) {
        return STRIPPED_FIELDS.contains(owner.replace('.', '/') + "." + name + ":" + desc);
    }

    /**
     * Checks if a class has been stripped in this specific stripper instance.
     */
    public boolean isInstanceClassStripped(String className) {
        return instanceStrippedClasses.contains(className.replace('.', '/'));
    }

    /**
     * Checks if a method has been stripped in this specific stripper instance.
     */
    public boolean isInstanceMethodStripped(String owner, String name, String desc) {
        return instanceStrippedMethods.contains(owner.replace('.', '/') + "." + name + desc);
    }

    /**
     * Checks if a field has been stripped in this specific stripper instance.
     */
    public boolean isInstanceFieldStripped(String owner, String name, String desc) {
        return instanceStrippedFields.contains(owner.replace('.', '/') + "." + name + ":" + desc);
    }
}
