package net.chainloader.loader.core.transform;

import net.chainloader.loader.core.ChainClassLoader;
import org.objectweb.asm.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * DynamicMixinRedirector is an ASM-based class transformer responsible for intercepting
 * spongepowered Mixin classes and dynamically remapping target method descriptors in injector annotations
 * (such as {@code @Inject}, {@code @Redirect}, {@code @ModifyArg}, etc.) on the fly.
 *
 * <p>This class also supports scanning Mixin JSON configurations to automatically register
 * classes for signature redirection, and provides pipelines to coordinate with the
 * Annotation Stripper and Access Compiler subagents.</p>
 */
public class DynamicMixinRedirector implements ChainClassLoader.ClassTransformer {

    private static final Logger LOGGER = Logger.getLogger(DynamicMixinRedirector.class.getName());

    private final Map<String, String> classMappings = new HashMap<>();
    private final Map<String, String> methodMappings = new HashMap<>();
    private final Set<String> registeredMixins = new HashSet<>();

    private AnnotationStripper annotationStripper;
    private AccessCompiler accessCompiler;

    /**
     * Interface representing the Annotation Stripper component.
     * In the ChainLoader pipeline, the Annotation Stripper runs to clean up environment-specific
     * or unwanted annotations before/after mixin targets are processed.
     */
    @FunctionalInterface
    public interface AnnotationStripper {
        /**
         * Strips target annotations from the class bytecode.
         *
         * @param className the name of the class (dot format)
         * @param classBytes the original bytecode
         * @return the stripped bytecode
         */
        byte[] strip(String className, byte[] classBytes);
    }

    /**
     * Interface representing the Access Compiler/Widener component.
     * Ensures that any field/method access levels needed by Mixin injectors are expanded/compiled
     * (e.g., from private/protected to public/non-final) before Mixin application.
     */
    @FunctionalInterface
    public interface AccessCompiler {
        /**
         * Widens or compiles access rules on class bytecode.
         *
         * @param className the name of the class (dot format)
         * @param classBytes the original bytecode
         * @return the transformed bytecode with expanded access modifiers
         */
        byte[] compileAccess(String className, byte[] classBytes);
    }

    public DynamicMixinRedirector() {
    }

    /**
     * Registers the AnnotationStripper subagent/component for coordinated processing.
     */
    public void setAnnotationStripper(AnnotationStripper stripper) {
        this.annotationStripper = stripper;
    }

    /**
     * Registers the AccessCompiler subagent/component for coordinated processing.
     */
    public void setAccessCompiler(AccessCompiler compiler) {
        this.accessCompiler = compiler;
    }

    /**
     * Registers a class name mapping rule.
     * e.g., "net/minecraft/class_3218" -> "net/minecraft/server/level/ServerLevel"
     */
    public void registerClassMap(String original, String target) {
        classMappings.put(original.replace('.', '/'), target.replace('.', '/'));
    }

    /**
     * Registers a method signature remapping rule.
     * The key and value can be in format "methodName(desc)" or "owner.methodName(desc)".
     */
    public void registerMethodMap(String originalSig, String targetSig) {
        methodMappings.put(originalSig, targetSig);
    }

    /**
     * Registers a fully qualified mixin class name (dot format) to be intercepted.
     */
    public void registerMixinClass(String className) {
        registeredMixins.add(className.replace('/', '.'));
        LOGGER.log(Level.FINE, "Registered mixin class for redirection: {0}", className);
    }

    /**
     * Checks if the given class name is a registered mixin class.
     */
    public boolean isMixinClass(String className) {
        return registeredMixins.contains(className.replace('/', '.'));
    }

    /**
     * Scans and parses a Mixin JSON configuration file content, extracting the package name
     * and mixin classes (from "mixins", "client", and "server" arrays) to register them.
     *
     * @param jsonContent the raw content of the Mixin JSON config
     */
    public void registerMixinConfig(String jsonContent) {
        if (jsonContent == null || jsonContent.isEmpty()) {
            return;
        }

        // 1. Extract package prefix using Regex
        Pattern packagePattern = Pattern.compile("\"package\"\\s*:\\s*\"([^\"]+)\"");
        Matcher packageMatcher = packagePattern.matcher(jsonContent);
        String mixinPackage = "";
        if (packageMatcher.find()) {
            mixinPackage = packageMatcher.group(1);
            if (!mixinPackage.endsWith(".")) {
                mixinPackage += ".";
            }
        }

        // 2. Extract elements from arrays: "mixins", "client", "server"
        List<String> mixinArrays = List.of("mixins", "client", "server");
        for (String arrayKey : mixinArrays) {
            Pattern arrayPattern = Pattern.compile("\"" + arrayKey + "\"\\s*:\\s*\\[([^\\]]*)\\]", Pattern.DOTALL);
            Matcher arrayMatcher = arrayPattern.matcher(jsonContent);
            if (arrayMatcher.find()) {
                String arrayContent = arrayMatcher.group(1);
                // Extract all quoted strings
                Pattern stringPattern = Pattern.compile("\"([^\"]+)\"");
                Matcher stringMatcher = stringPattern.matcher(arrayContent);
                while (stringMatcher.find()) {
                    String relativeClass = stringMatcher.group(1);
                    String fullyQualifiedClass = mixinPackage + relativeClass;
                    registerMixinClass(fullyQualifiedClass);
                }
            }
        }
    }

    /**
     * Scans Mixin configurations in the classpath/resources and registers their mixin classes.
     *
     * @param classLoader the classloader to retrieve resources from
     * @param mixinConfigFiles the list of mixin config resource paths
     */
    public void scanMixinConfigs(ClassLoader classLoader, List<String> mixinConfigFiles) {
        for (String configPath : mixinConfigFiles) {
            try (InputStream is = classLoader.getResourceAsStream(configPath)) {
                if (is != null) {
                    byte[] bytes = is.readAllBytes();
                    String content = new String(bytes, StandardCharsets.UTF_8);
                    registerMixinConfig(content);
                } else {
                    LOGGER.log(Level.WARNING, "Could not find Mixin configuration file: {0}", configPath);
                }
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Failed to read Mixin configuration: " + configPath, e);
            }
        }
    }

    /**
     * Performs dynamic signature remapping of a Mixin injector target selector string.
     *
     * <p>Handles the following format variations:
     * - {@code methodName}
     * - {@code methodName(Params)Return}
     * - {@code Lowner/Class;methodName(Params)Return}
     * - {@code owner/Class.methodName(Params)Return}
     * </p>
     *
     * @param selector the target selector inside injector annotations
     * @return the remapped target selector
     */
    public String remapSelector(String selector) {
        if (selector == null || selector.isEmpty()) {
            return selector;
        }

        String owner = null;
        String methodName = selector;
        String descriptor = null;
        boolean isLFormat = false;
        boolean isDotFormat = false;

        // Check if there is an owner class in either Lowner; or owner. format
        if (selector.startsWith("L") && selector.contains(";")) {
            int semicolonIndex = selector.indexOf(';');
            owner = selector.substring(1, semicolonIndex);
            methodName = selector.substring(semicolonIndex + 1);
            isLFormat = true;
        } else {
            int dotIndex = selector.indexOf('.');
            if (dotIndex != -1) {
                owner = selector.substring(0, dotIndex);
                methodName = selector.substring(dotIndex + 1);
                isDotFormat = true;
            }
        }

        // Check if method signature has parameters/descriptors
        int parenIndex = methodName.indexOf('(');
        if (parenIndex != -1) {
            descriptor = methodName.substring(parenIndex);
            methodName = methodName.substring(0, parenIndex);
        }

        // Remap owner class
        String remappedOwner = owner;
        if (owner != null) {
            remappedOwner = classMappings.getOrDefault(owner, owner);
        }

        // Remap descriptors
        String remappedDescriptor = descriptor;
        if (descriptor != null) {
            remappedDescriptor = remapDescriptor(descriptor);
        }

        // Search for specific method signature remappings
        String remappedMethodName = methodName;
        if (remappedDescriptor != null) {
            String fullSigKey = methodName + remappedDescriptor;
            if (methodMappings.containsKey(fullSigKey)) {
                String targetSig = methodMappings.get(fullSigKey);
                int targetParen = targetSig.indexOf('(');
                if (targetParen != -1) {
                    remappedMethodName = targetSig.substring(0, targetParen);
                    remappedDescriptor = targetSig.substring(targetParen);
                } else {
                    remappedMethodName = targetSig;
                }
            } else if (remappedOwner != null) {
                String ownerSigKey = remappedOwner + "." + methodName + remappedDescriptor;
                if (methodMappings.containsKey(ownerSigKey)) {
                    String targetSig = methodMappings.get(ownerSigKey);
                    int targetParen = targetSig.indexOf('(');
                    if (targetParen != -1) {
                        remappedMethodName = targetSig.substring(0, targetParen);
                        remappedDescriptor = targetSig.substring(targetParen);
                    } else {
                        remappedMethodName = targetSig;
                    }
                }
            }
        } else {
            // Check for simple method name mapping without descriptor
            if (methodMappings.containsKey(methodName)) {
                remappedMethodName = methodMappings.get(methodName);
            }
        }

        // Reconstruct remapped selector
        StringBuilder sb = new StringBuilder();
        if (remappedOwner != null) {
            if (isLFormat) {
                sb.append("L").append(remappedOwner).append(";");
            } else if (isDotFormat) {
                sb.append(remappedOwner).append(".");
            }
        }
        sb.append(remappedMethodName);
        if (remappedDescriptor != null) {
            sb.append(remappedDescriptor);
        }

        return sb.toString();
    }

    private String remapDescriptor(String descriptor) {
        if (descriptor == null || descriptor.isEmpty()) {
            return descriptor;
        }

        StringBuilder sb = new StringBuilder();
        int len = descriptor.length();
        for (int i = 0; i < len; i++) {
            char c = descriptor.charAt(i);
            if (c == 'L') {
                int semi = descriptor.indexOf(';', i);
                if (semi != -1) {
                    String className = descriptor.substring(i + 1, semi);
                    String remappedClass = classMappings.getOrDefault(className, className);
                    sb.append('L').append(remappedClass).append(';');
                    i = semi;
                } else {
                    sb.append(c);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * ClassLoader transformer integration method. Intercepts and transforms
     * Mixin classes dynamically using ASM to rewrite target signature annotations.
     */
    @Override
    public byte[] transform(String className, byte[] classBytes) {
        if (classBytes == null || classBytes.length == 0) {
            return classBytes;
        }

        if (!isMixinClass(className)) {
            return classBytes;
        }

        LOGGER.log(Level.FINE, "Applying dynamic mixin redirection to: {0}", className);
        try {
            ClassReader classReader = new ClassReader(classBytes);
            ClassWriter classWriter = new ClassWriter(classReader, ClassWriter.COMPUTE_MAXS);

            MixinRedirectorClassVisitor visitor = new MixinRedirectorClassVisitor(
                Opcodes.ASM9,
                classWriter,
                this
            );

            classReader.accept(visitor, ClassReader.EXPAND_FRAMES);
            return classWriter.toByteArray();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to apply dynamic mixin redirection to " + className, e);
            return classBytes;
        }
    }

    /**
     * Executes the coordinated ChainLoader bytecode transformation pipeline.
     * This coordinates:
     * 1. Access Compiler (widens class/method/field access to satisfy injectors).
     * 2. Annotation Stripper (removes platform-specific or incompatible side-only annotations).
     * 3. Dynamic Mixin Redirector (remaps Mixin injector target signatures).
     *
     * @param className the dot-separated binary name of the class
     * @param classBytes the raw bytecode of the class
     * @return the fully transformed bytecode
     */
    public byte[] transformPipeline(String className, byte[] classBytes) {
        byte[] currentBytes = classBytes;

        // 1. Coordinate with the Access Compiler
        if (accessCompiler != null) {
            byte[] nextBytes = accessCompiler.compileAccess(className, currentBytes);
            if (nextBytes != null) {
                currentBytes = nextBytes;
            }
        }

        // 2. Coordinate with the Annotation Stripper
        if (annotationStripper != null) {
            byte[] nextBytes = annotationStripper.strip(className, currentBytes);
            if (nextBytes != null) {
                currentBytes = nextBytes;
            }
        }

        // 3. Apply Mixin target signature redirection
        if (isMixinClass(className)) {
            currentBytes = transform(className, currentBytes);
        }

        return currentBytes;
    }

    // --- ASM Class, Method, and Annotation Visitors ---

    private static class MixinRedirectorClassVisitor extends ClassVisitor {
        private final DynamicMixinRedirector redirector;

        public MixinRedirectorClassVisitor(int api, ClassVisitor cv, DynamicMixinRedirector redirector) {
            super(api, cv);
            this.redirector = redirector;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
            if (mv != null) {
                return new MixinRedirectorMethodVisitor(api, mv, redirector);
            }
            return null;
        }
    }

    private static class MixinRedirectorMethodVisitor extends MethodVisitor {
        private final DynamicMixinRedirector redirector;

        public MixinRedirectorMethodVisitor(int api, MethodVisitor mv, DynamicMixinRedirector redirector) {
            super(api, mv);
            this.redirector = redirector;
        }

        @Override
        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            AnnotationVisitor av = super.visitAnnotation(descriptor, visible);
            if (av != null && isMixinInjectorAnnotation(descriptor)) {
                return new MixinAnnotationRedirector(api, av, redirector);
            }
            return av;
        }

        private boolean isMixinInjectorAnnotation(String desc) {
            return desc.equals("Lorg/spongepowered/asm/mixin/injection/Inject;")
                || desc.equals("Lorg/spongepowered/asm/mixin/injection/Redirect;")
                || desc.equals("Lorg/spongepowered/asm/mixin/injection/ModifyArg;")
                || desc.equals("Lorg/spongepowered/asm/mixin/injection/ModifyArgs;")
                || desc.equals("Lorg/spongepowered/asm/mixin/injection/ModifyConstant;")
                || desc.equals("Lorg/spongepowered/asm/mixin/injection/ModifyVariable;");
        }
    }

    private static class MixinAnnotationRedirector extends AnnotationVisitor {
        private final DynamicMixinRedirector redirector;

        public MixinAnnotationRedirector(int api, AnnotationVisitor av, DynamicMixinRedirector redirector) {
            super(api, av);
            this.redirector = redirector;
        }

        @Override
        public void visit(String name, Object value) {
            if ("method".equals(name) && value instanceof String) {
                String remapped = redirector.remapSelector((String) value);
                super.visit(name, remapped);
            } else {
                super.visit(name, value);
            }
        }

        @Override
        public AnnotationVisitor visitArray(String name) {
            AnnotationVisitor av = super.visitArray(name);
            if ("method".equals(name) && av != null) {
                return new MixinAnnotationArrayRedirector(api, av, redirector);
            }
            return av;
        }
    }

    private static class MixinAnnotationArrayRedirector extends AnnotationVisitor {
        private final DynamicMixinRedirector redirector;

        public MixinAnnotationArrayRedirector(int api, AnnotationVisitor av, DynamicMixinRedirector redirector) {
            super(api, av);
            this.redirector = redirector;
        }

        @Override
        public void visit(String name, Object value) {
            if (value instanceof String) {
                String remapped = redirector.remapSelector((String) value);
                super.visit(name, remapped);
            } else {
                super.visit(name, value);
            }
        }
    }

    // --- Main Demo / Validation Method ---

    public static void main(String[] args) {
        System.out.println("=== DynamicMixinRedirector Demonstration ===");

        DynamicMixinRedirector redirector = new DynamicMixinRedirector();

        // 1. Setup Class mappings
        redirector.registerClassMap("net/minecraft/class_3218", "net/minecraft/server/level/ServerLevel");
        redirector.registerClassMap("net/minecraft/class_1297", "net/minecraft/world/entity/Entity");

        // 2. Setup Method signature remappings
        redirector.registerMethodMap(
            "tick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/Entity;)V",
            "tick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/ProfitInfo;)V"
        );

        // 3. Register mock subagents for coordination
        redirector.setAccessCompiler((className, bytes) -> {
            System.out.println("[Coordinated AccessCompiler] Widening access boundaries for: " + className);
            return bytes;
        });

        redirector.setAnnotationStripper((className, bytes) -> {
            System.out.println("[Coordinated AnnotationStripper] Cleaning environment/side markers for: " + className);
            return bytes;
        });

        // 4. Scan simulated Mixin JSON Configuration
        String rawJson = "{\n" +
            "  \"required\": true,\n" +
            "  \"package\": \"net.chainloader.mixin.test\",\n" +
            "  \"mixins\": [\n" +
            "    \"ServerTickMixin\"\n" +
            "  ],\n" +
            "  \"client\": [\n" +
            "    \"ClientRenderMixin\"\n" +
            "  ]\n" +
            "}";

        System.out.println("\n--- Parsing Mixin JSON Configuration ---");
        redirector.registerMixinConfig(rawJson);
        System.out.println("net.chainloader.mixin.test.ServerTickMixin registered? " +
            redirector.isMixinClass("net.chainloader.mixin.test.ServerTickMixin"));
        System.out.println("net.chainloader.mixin.test.ClientRenderMixin registered? " +
            redirector.isMixinClass("net.chainloader.mixin.test.ClientRenderMixin"));

        // 5. Test Selector Remapping
        System.out.println("\n--- Testing Target Signature Selector Remapping ---");
        String original = "Lnet/minecraft/class_3218;tick(Lnet/minecraft/class_3218;Lnet/minecraft/class_1297;)V";
        String remapped = redirector.remapSelector(original);
        System.out.println("Original: " + original);
        System.out.println("Remapped: " + remapped);

        // 6. Test Pipeline Execution
        System.out.println("\n--- Running Bytecode Pipeline ---");
        byte[] mockBytes = new byte[]{0x0A, 0x0B, 0x0C, 0x0D};
        byte[] resultBytes = redirector.transformPipeline("net.chainloader.mixin.test.ServerTickMixin", mockBytes);
        System.out.println("Pipeline completed successfully.");
    }
}
