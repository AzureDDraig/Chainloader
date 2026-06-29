package net.chainloader.loader.mixin;

import net.chainloader.loader.transformer.BytecodeTransformer;
import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MixinPatcher is responsible for dynamically parsing, intercepting, and patching
 * Minecraft Mixin configuration files and their target method descriptors at runtime.
 * 
 * In a multi-version or highly modded environment, method signatures (descriptors)
 * and target inject points may drift between Minecraft versions or mapping changes.
 * This class provides mechanisms to:
 * 1. Parse and dynamically patch mixin JSON configuration files (e.g. conditionally excluding mixins).
 * 2. Rewrite method signature descriptors for injection targets (like {@code @Inject}, {@code @Redirect})
 *    on the fly to ensure Mixin compatibility without requiring manual recompilation.
 */
public class MixinPatcher {

    private final Map<String, String> descriptorRemapTable = new HashMap<>();
    private final Map<String, String> targetMethodRemapTable = new HashMap<>();
    private final Set<String> disabledMixins = new HashSet<>();

    /**
     * Registers a class name mapping rule.
     * Example: "net/minecraft/class_3218" -> "net/minecraft/server/level/ServerLevel"
     * 
     * @param original the original class name in internal JVM format (e.g. net/minecraft/class_3218)
     * @param target the target class name in internal JVM format (e.g. net/minecraft/server/level/ServerLevel)
     */
    public void registerClassMap(String original, String target) {
        descriptorRemapTable.put("L" + original + ";", "L" + target + ";");
    }

    /**
     * Registers a method descriptor replacement rule.
     * Example:
     *   original: "tick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/Entity;)V"
     *   patched:  "tick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/ProfitInfo;)V"
     * 
     * @param originalSignature the original method signature target
     * @param patchedSignature the updated method signature target
     */
    public void registerMethodRemap(String originalSignature, String patchedSignature) {
        targetMethodRemapTable.put(originalSignature, patchedSignature);
    }

    /**
     * Disables a specific mixin class dynamically.
     * 
     * @param mixinClassName the fully qualified class name or relative class name of the mixin to exclude
     */
    public void disableMixin(String mixinClassName) {
        disabledMixins.add(mixinClassName);
    }

    /**
     * Simulates reading, parsing, patching, and writing a Mixin JSON configuration file.
     * This handles conditional mixin stripping based on active features/mods and environment variables.
     * 
     * @param inputJson the raw mixin config JSON string
     * @return the patched mixin config JSON string
     */
    public String patchMixinConfigJson(String inputJson) {
        // A robust and clean parser mock using string manipulation for self-containment.
        // In a real loader, this would use a lightweight JSON library (like Gson, Jackson, or custom parser).
        
        System.out.println("[MixinPatcher] Parsing and patching Mixin JSON configuration...");

        // Parse key-value arrays using regular expressions
        // We look for arrays under "mixins", "client", "server" keys
        String patchedJson = inputJson;
        patchedJson = patchJsonArray(patchedJson, "mixins");
        patchedJson = patchJsonArray(patchedJson, "client");
        patchedJson = patchJsonArray(patchedJson, "server");

        return patchedJson;
    }

    private String patchJsonArray(String json, String arrayKey) {
        // Regex to match "arrayKey": [ ... ]
        Pattern arrayPattern = Pattern.compile("\"" + arrayKey + "\"[\\s]*:[\\s]*\\[([^\\]]*)\\]", Pattern.DOTALL);
        Matcher matcher = arrayPattern.matcher(json);
        if (matcher.find()) {
            String arrayContent = matcher.group(1);
            String[] elements = arrayContent.split(",");
            List<String> preservedElements = new ArrayList<>();

            for (String element : elements) {
                String trimmed = element.trim();
                if (trimmed.isEmpty()) continue;

                // Strip quotes to check if this mixin is disabled
                String mixinName = trimmed.replace("\"", "");
                if (disabledMixins.contains(mixinName)) {
                    System.out.println("[MixinPatcher] Removing disabled mixin class: " + mixinName);
                    continue;
                }
                preservedElements.add(trimmed);
            }

            // Reconstruct the JSON array format
            StringBuilder newArrayBuilder = new StringBuilder();
            newArrayBuilder.append("\"").append(arrayKey).append("\": [\n");
            for (int i = 0; i < preservedElements.size(); i++) {
                newArrayBuilder.append("    ").append(preservedElements.get(i));
                if (i < preservedElements.size() - 1) {
                    newArrayBuilder.append(",");
                }
                newArrayBuilder.append("\n");
            }
            newArrayBuilder.append("  ]");

            return json.substring(0, matcher.start()) + newArrayBuilder.toString() + json.substring(matcher.end());
        }
        return json;
    }

    /**
     * Remaps a method descriptor using the registered class remapping table.
     * This handles complex signatures including object types and arrays.
     * 
     * @param descriptor the method descriptor, e.g., {@code (Lnet/minecraft/class_3218;Lnet/minecraft/class_1297;)V}
     * @return the remapped method descriptor
     */
    public String remapDescriptor(String descriptor) {
        if (descriptor == null || descriptor.isEmpty()) {
            return descriptor;
        }

        BytecodeTransformer bt = BytecodeTransformer.getInstance();
        if (bt == null) {
            String remapped = descriptor;
            // Apply registered replacements (usually Loriginal; -> Ltarget;)
            for (Map.Entry<String, String> entry : descriptorRemapTable.entrySet()) {
                remapped = remapped.replace(entry.getKey(), entry.getValue());
            }
            return remapped;
        }

        StringBuilder sb = new StringBuilder();
        int len = descriptor.length();
        for (int i = 0; i < len; i++) {
            char c = descriptor.charAt(i);
            if (c == 'L') {
                int semi = descriptor.indexOf(';', i);
                if (semi != -1) {
                    String className = descriptor.substring(i + 1, semi);
                    String remappedClass = bt.map(className);
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
     * Rewrites an injection point method reference dynamically.
     * Injection points in Mixin typically refer to target methods via:
     * {@code Lpackage/ClassName;methodName(Lparam/Type;)V}
     * or simply {@code methodName(Lparam/Type;)V}.
     * 
     * @param methodRef the method reference or selector in the Mixin annotation (e.g. {@code @Inject(method = "...")})
     * @return the patched method reference target
     */
    public String patchInjectionTarget(String methodRef) {
        if (methodRef == null || methodRef.isEmpty()) {
            return methodRef;
        }

        BytecodeTransformer bt = BytecodeTransformer.getInstance();
        if (bt == null) {
            if (targetMethodRemapTable.containsKey(methodRef)) {
                return targetMethodRemapTable.get(methodRef);
            }
            return methodRef;
        }

        String owner = null;
        String methodName = methodRef;
        String descriptor = null;
        boolean isLFormat = false;
        boolean isDotFormat = false;

        if (methodRef.startsWith("L") && methodRef.contains(";")) {
            int semicolonIndex = methodRef.indexOf(';');
            owner = methodRef.substring(1, semicolonIndex);
            methodName = methodRef.substring(semicolonIndex + 1);
            isLFormat = true;
        } else {
            int dotIndex = methodRef.indexOf('.');
            if (dotIndex != -1) {
                owner = methodRef.substring(0, dotIndex);
                methodName = methodRef.substring(dotIndex + 1);
                isDotFormat = true;
            }
        }

        int parenIndex = methodName.indexOf('(');
        if (parenIndex != -1) {
            descriptor = methodName.substring(parenIndex);
            methodName = methodName.substring(0, parenIndex);
        }

        String remappedOwner = owner;
        if (owner != null) {
            remappedOwner = bt.map(owner);
        }

        String remappedDescriptor = descriptor;
        if (descriptor != null) {
            remappedDescriptor = remapDescriptor(descriptor);
        }

        String remappedMethodName = methodName;
        if (owner != null) {
            remappedMethodName = bt.mapMethodName(owner, methodName, descriptor != null ? descriptor : "");
        } else {
            remappedMethodName = bt.mapMethodName("java/lang/Object", methodName, descriptor != null ? descriptor : "");
        }

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

        String result = sb.toString();
        System.out.println("[MixinPatcher] Patched injection target: " + methodRef + " -> " + result);
        return result;
    }

    /**
     * Simulates the ASM-based bytecode transformation that intercepts Mixin class annotations
     * and rewrites target method signatures dynamically before loading.
     * 
     * @param className the mixin class name being transformed
     * @param classNode a mock representation of the ClassNode structure from ASM
     * @return true if any modifications were made, false otherwise
     */
    public boolean transformMixinBytecode(String className, MockClassNode classNode) {
        boolean modified = false;
        System.out.println("[MixinPatcher] Inspecting Mixin Class bytecode: " + className);

        for (MockMethodNode method : classNode.methods) {
            for (MockAnnotationNode annotation : method.annotations) {
                if (isMixinInjectorAnnotation(annotation.desc)) {
                    // Injector annotations like @Inject, @Redirect have a "method" attribute (String or Array of Strings)
                    Object methodValue = annotation.values.get("method");
                    if (methodValue instanceof String) {
                        String originalTarget = (String) methodValue;
                        String patchedTarget = patchInjectionTarget(originalTarget);
                        if (!originalTarget.equals(patchedTarget)) {
                            annotation.values.put("method", patchedTarget);
                            modified = true;
                        }
                    } else if (methodValue instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<String> targets = (List<String>) methodValue;
                        for (int i = 0; i < targets.size(); i++) {
                            String originalTarget = targets.get(i);
                            String patchedTarget = patchInjectionTarget(originalTarget);
                            if (!originalTarget.equals(patchedTarget)) {
                                targets.set(i, patchedTarget);
                                modified = true;
                            }
                        }
                    }
                }
            }
        }

        if (modified) {
            System.out.println("[MixinPatcher] Successfully patched injection point descriptors in: " + className);
        }
        return modified;
    }

    private boolean isMixinInjectorAnnotation(String desc) {
        return desc.equals("Lorg/spongepowered/asm/mixin/injection/Inject;")
            || desc.equals("Lorg/spongepowered/asm/mixin/injection/Redirect;")
            || desc.equals("Lorg/spongepowered/asm/mixin/injection/ModifyArg;")
            || desc.equals("Lorg/spongepowered/asm/mixin/injection/ModifyArgs;")
            || desc.equals("Lorg/spongepowered/asm/mixin/injection/ModifyConstant;")
            || desc.equals("Lorg/spongepowered/asm/mixin/injection/ModifyVariable;");
    }

    // --- Mock ASM API Representation for Class Interception ---

    public static class MockClassNode {
        public String name;
        public List<MockMethodNode> methods = new ArrayList<>();

        public MockClassNode(String name) {
            this.name = name;
        }
    }

    public static class MockMethodNode {
        public String name;
        public String desc;
        public List<MockAnnotationNode> annotations = new ArrayList<>();

        public MockMethodNode(String name, String desc) {
            this.name = name;
            this.desc = desc;
        }
    }

    public static class MockAnnotationNode {
        public String desc;
        public Map<String, Object> values = new HashMap<>();

        public MockAnnotationNode(String desc) {
            this.desc = desc;
        }
    }

    /**
     * A simple demonstration showing how the MixinPatcher works.
     */
    public static void main(String[] args) {
        MixinPatcher patcher = new MixinPatcher();

        // 1. Setup Remap Rules
        // Map intermediary class names to mapped names
        patcher.registerClassMap("net/minecraft/class_3218", "net/minecraft/server/level/ServerLevel");
        patcher.registerClassMap("net/minecraft/class_1297", "net/minecraft/world/entity/Entity");
        
        // Map specific method signatures (e.g. when method signature adds a parameter in a newer version)
        patcher.registerMethodRemap(
            "tick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/Entity;)V",
            "tick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/ProfitInfo;)V"
        );

        // 2. Disable specific mixins conditionally
        patcher.disableMixin("ServerCrashFixMixin");

        // 3. Patch Mixin JSON configuration file
        String rawJson = "{\n" +
                "  \"required\": true,\n" +
                "  \"package\": \"net.chainloader.mixin\",\n" +
                "  \"mixins\": [\n" +
                "    \"ServerCrashFixMixin\",\n" +
                "    \"PlayerTickMixin\"\n" +
                "  ],\n" +
                "  \"client\": [\n" +
                "    \"RenderMixin\"\n" +
                "  ]\n" +
                "}";

        System.out.println("--- Original Mixin JSON ---");
        System.out.println(rawJson);
        System.out.println("---------------------------\n");

        String patchedJson = patcher.patchMixinConfigJson(rawJson);

        System.out.println("\n--- Patched Mixin JSON ---");
        System.out.println(patchedJson);
        System.out.println("---------------------------\n");

        // 4. Transform ASM bytecode (mocking the transformation of a Mixin class)
        MockClassNode mockClass = new MockClassNode("net/chainloader/mixin/PlayerTickMixin");
        MockMethodNode mockMethod = new MockMethodNode("onTick", "(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V");
        
        MockAnnotationNode injectAnnotation = new MockAnnotationNode("Lorg/spongepowered/asm/mixin/injection/Inject;");
        // Set the method target containing the old signature and old intermediary classes
        injectAnnotation.values.put("method", "Lnet/minecraft/class_3218;tick(Lnet/minecraft/class_3218;Lnet/minecraft/class_1297;)V");
        mockMethod.annotations.add(injectAnnotation);
        mockClass.methods.add(mockMethod);

        System.out.println("--- Before Bytecode Transformation ---");
        System.out.println("Inject Method Target: " + injectAnnotation.values.get("method"));
        System.out.println("--------------------------------------\n");

        boolean didTransform = patcher.transformMixinBytecode(mockClass.name, mockClass);

        System.out.println("\n--- After Bytecode Transformation (modified: " + didTransform + ") ---");
        System.out.println("Inject Method Target: " + injectAnnotation.values.get("method"));
        System.out.println("--------------------------------------\n");
    }
}
