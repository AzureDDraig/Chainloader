package net.chainloader.loader.transformer;

import net.chainloader.loader.compat.Chainlink;
import net.chainloader.loader.core.ChainLauncher;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The {@code BytecodeTransformer} class is responsible for remapping bytecode packages and class names
 * at runtime. It queries the active {@link Chainlink} modules for specific remapping rules.
 */
public class BytecodeTransformer {

    private static final Logger LOGGER = Logger.getLogger(BytecodeTransformer.class.getName());

    private static BytecodeTransformer instance;
    public static BytecodeTransformer getInstance() {
        return instance;
    }

    private static ClassLoader activeClassLoader;
    public static void setClassLoader(ClassLoader classLoader) {
        activeClassLoader = classLoader;
    }

    private final Map<String, String> INTERMEDIARY_METHOD_MAPPINGS = new ConcurrentHashMap<>();
    private final Map<String, String> INTERMEDIARY_FIELD_MAPPINGS = new ConcurrentHashMap<>();
    private final Map<String, String> SEARGE_METHOD_MAPPINGS = new ConcurrentHashMap<>();
    private final Map<String, String> SEARGE_FIELD_MAPPINGS = new ConcurrentHashMap<>();
    private final Map<String, String> MOJANG_METHOD_MAPPINGS = new ConcurrentHashMap<>();
    private final Map<String, String> MOJANG_FIELD_MAPPINGS = new ConcurrentHashMap<>();
    private final Map<String, String> OBF_TO_DEOBF_CLASS_MAPPINGS = new ConcurrentHashMap<>();
    private final Map<String, String> OBF_TO_MOJANG_CLASS_MAPPINGS = new ConcurrentHashMap<>();

    private final Map<String, String> mappings = new ConcurrentHashMap<>();
    private final Map<String, String> classMappings = new ConcurrentHashMap<>();
    private final Map<String, String> packageMappings = new ConcurrentHashMap<>();

    private final ChainRemapper remapper = new ChainRemapper();

    public String map(String internalName) {
        return remapper.map(internalName);
    }

    public String mapMethodName(String owner, String name, String descriptor) {
        return remapper.mapMethodName(owner, name, descriptor);
    }

    public BytecodeTransformer() {
        instance = this;
    }

    public void addClassMapping(String src, String target) {
        classMappings.put(src, target);
        mappings.put(src, target);
        if (!target.startsWith("net/minecraft/") && !target.startsWith("net/fabricmc/") && !target.startsWith("net/minecraftforge/") && !target.startsWith("net/neoforged/")) {
            if (!src.contains("class_") && !src.contains("field_") && !src.contains("method_")) {
                OBF_TO_MOJANG_CLASS_MAPPINGS.put(target, src);
            }
            OBF_TO_DEOBF_CLASS_MAPPINGS.put(target, src);
        }
    }

    public void addPackageMapping(String src, String target) {
        packageMappings.put(src, target);
        mappings.put(src, target);
    }

    public void addMojangMethodMapping(String key, String value) {
        MOJANG_METHOD_MAPPINGS.put(key, value);
    }

    public void addMojangFieldMapping(String key, String value) {
        MOJANG_FIELD_MAPPINGS.put(key, value);
    }

    public void addIntermediaryMethodMapping(String key, String value) {
        INTERMEDIARY_METHOD_MAPPINGS.put(key, value);
    }

    public void addIntermediaryFieldMapping(String key, String value) {
        INTERMEDIARY_FIELD_MAPPINGS.put(key, value);
    }

    public void addSeargeMethodMapping(String key, String value) {
        SEARGE_METHOD_MAPPINGS.put(key, value);
    }

    public void addSeargeFieldMapping(String key, String value) {
        SEARGE_FIELD_MAPPINGS.put(key, value);
    }

    public String mapClassName(String className) {
        String slash = className.replace('.', '/');
        String mapped = map(slash);
        return mapped != null ? mapped.replace('/', '.') : className;
    }

    public Map<String, String> getMappings() {
        return Collections.unmodifiableMap(mappings);
    }

    public byte[] transform(String className, byte[] classBytes) {
        if (classBytes == null || classBytes.length == 0) {
            return classBytes;
        }
        if (className.startsWith("com.mojang.serialization.") || className.startsWith("com.mojang.datafixers.")) {
            return classBytes;
        }

        // Inject generic default method codec() MapCodec bridge for placement modifiers and other registry types refactored in 1.21.1
        try {
            ClassReader cr = new ClassReader(classBytes);
            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            InterfaceCodecBridgeVisitor bridgeVisitor = new InterfaceCodecBridgeVisitor(Opcodes.ASM9, cw, className);
            cr.accept(bridgeVisitor, 0);
            if (bridgeVisitor.modified) {
                System.out.println("[ChainLoader] Injected legacy MapCodec/Codec bridge into: " + className);
                classBytes = cw.toByteArray();
            }
        } catch (Throwable t) {
            System.err.println("[ChainLoader] Failed to check/inject MapCodec bridge for: " + className);
            t.printStackTrace();
        }

        // Intercept vanilla classes to inject setRegistryName and getRegistryName methods for legacy Forge mods compatibility
        String classSlash = className.replace('.', '/');
        if ("net/minecraft/world/level/block/Block".equals(classSlash) ||
            "net/minecraft/world/item/Item".equals(classSlash) ||
            "net/minecraft/world/level/material/Fluid".equals(classSlash)) {
            try {
                System.out.println("[ChainLoader] Injecting setRegistryName/getRegistryName methods into: " + className);
                classBytes = injectRegistryNameMethods(classBytes, classSlash);
            } catch (Throwable t) {
                System.err.println("[ChainLoader] Failed to inject registry name methods into " + className + ":");
                t.printStackTrace();
            }
        }

        boolean isCreativeModeTab = "cta".equals(className) || "cta$a".equals(className) || 
                                    "net.minecraft.world.item.CreativeModeTab".equals(className) || 
                                    "net.minecraft.world.item.CreativeModeTab$Builder".equals(className);
        if (!isCreativeModeTab) {
            if (className.indexOf('.') == -1 || className.startsWith("net.minecraft.") || className.startsWith("com.mojang.")) {
                return classBytes;
            }
        }

        // Fast path check
        if (!containsRemapTargets(classBytes)) {
            return classBytes;
        }

        System.out.println("[ChainLoader] Remapping class: " + className);

        try {
            ClassReader classReader = new ClassReader(classBytes);
            ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            
            ChainRemapper remapper = new ChainRemapper();
            ClassRemapper classRemapper = new ClassRemapper(classWriter, remapper);
            
            classReader.accept(classRemapper, ClassReader.EXPAND_FRAMES);
            return classWriter.toByteArray();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to remap class " + className, e);
            return classBytes;
        }
    }

    private byte[] injectRegistryNameMethods(byte[] classBytes, String classSlashName) {
        String returnDesc = "L" + classSlashName + ";";
        ClassReader cr = new ClassReader(classBytes);
        ClassWriter cw = new ClassWriter(0);
        org.objectweb.asm.ClassVisitor cv = new org.objectweb.asm.ClassVisitor(Opcodes.ASM9, cw) {
            @Override
            public void visitEnd() {
                // 1. setRegistryName(Lnet/minecraft/resources/ResourceLocation;)LBlock; or LItem; or LFluid;
                org.objectweb.asm.MethodVisitor mv = super.visitMethod(Opcodes.ACC_PUBLIC, "setRegistryName", "(Lnet/minecraft/resources/ResourceLocation;)" + returnDesc, null, null);
                mv.visitCode();
                mv.visitVarInsn(Opcodes.ALOAD, 0);
                mv.visitVarInsn(Opcodes.ALOAD, 1);
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, "net/chainloader/loader/compat/bridge/RegistryHelper", "setRegistryName", "(Ljava/lang/Object;Lnet/minecraft/resources/ResourceLocation;)Ljava/lang/Object;", false);
                mv.visitTypeInsn(Opcodes.CHECKCAST, classSlashName);
                mv.visitInsn(Opcodes.ARETURN);
                mv.visitMaxs(2, 2);
                mv.visitEnd();

                // 2. setRegistryName(Ljava/lang/String;)LBlock; etc.
                mv = super.visitMethod(Opcodes.ACC_PUBLIC, "setRegistryName", "(Ljava/lang/String;)" + returnDesc, null, null);
                mv.visitCode();
                mv.visitVarInsn(Opcodes.ALOAD, 0);
                mv.visitVarInsn(Opcodes.ALOAD, 1);
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, "net/chainloader/loader/compat/bridge/RegistryHelper", "setRegistryName", "(Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/Object;", false);
                mv.visitTypeInsn(Opcodes.CHECKCAST, classSlashName);
                mv.visitInsn(Opcodes.ARETURN);
                mv.visitMaxs(2, 2);
                mv.visitEnd();

                // 3. getRegistryName()Lnet/minecraft/resources/ResourceLocation;
                mv = super.visitMethod(Opcodes.ACC_PUBLIC, "getRegistryName", "()Lnet/minecraft/resources/ResourceLocation;", null, null);
                mv.visitCode();
                mv.visitVarInsn(Opcodes.ALOAD, 0);
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, "net/chainloader/loader/compat/bridge/RegistryHelper", "getRegistryName", "(Ljava/lang/Object;)Lnet/minecraft/resources/ResourceLocation;", false);
                mv.visitInsn(Opcodes.ARETURN);
                mv.visitMaxs(1, 1);
                mv.visitEnd();

                super.visitEnd();
            }
        };
        cr.accept(cv, 0);
        return cw.toByteArray();
    }

    /**
     * Collects remap target markers from all active Chainlink modules.
     * Falls back to a minimal built-in set if no modules are active yet.
     */
    private java.util.Set<String> collectRemapTargetMarkers() {
        java.util.Set<String> markers = new java.util.HashSet<>();
        // Gather markers from all active Chainlink modules
        for (Chainlink link : ChainLauncher.getActiveLinks()) {
            Collection<String> moduleMarkers = link.getRemapTargetMarkers();
            if (moduleMarkers != null) {
                markers.addAll(moduleMarkers);
            }
        }
        // Fallback: if no Chainlinks are active yet (early bootstrap), use minimal defaults
        if (markers.isEmpty()) {
            markers.add("net/minecraft");
            markers.add("net.minecraft");
            markers.add("com/mojang");
            markers.add("com.mojang");
        }
        return markers;
    }

    public boolean containsRemapTargets(byte[] classBytes) {
        if (classBytes == null || classBytes.length < 10) {
            return false;
        }

        java.util.Set<String> markers = collectRemapTargetMarkers();

        try (DataInputStream dis = new DataInputStream(new ByteArrayInputStream(classBytes))) {
            int magic = dis.readInt();
            if (magic != 0xCAFEBABE) {
                return false;
            }
            
            dis.skipBytes(4);
            int constantPoolCount = dis.readUnsignedShort();

            for (int i = 1; i < constantPoolCount; i++) {
                int tag = dis.readUnsignedByte();
                switch (tag) {
                    case 1:
                        String value = dis.readUTF();
                        // Check if the constant pool string contains or matches any marker
                        for (String marker : markers) {
                            if (marker.length() <= 4) {
                                // Short markers (obfuscated class names) must match exactly
                                if (marker.equals(value)) {
                                    return true;
                                }
                            } else {
                                // Longer markers are substring-checked (package prefixes)
                                if (value.contains(marker)) {
                                    return true;
                                }
                            }
                        }
                        break;
                    case 3:
                    case 4:
                        dis.skipBytes(4);
                        break;
                    case 5:
                    case 6:
                        dis.skipBytes(8);
                        i++;
                        break;
                    case 7:
                    case 8:
                    case 16:
                    case 19:
                    case 20:
                        dis.skipBytes(2);
                        break;
                    case 15:
                        dis.skipBytes(3);
                        break;
                    case 9:
                    case 10:
                    case 11:
                    case 12:
                    case 17:
                    case 18:
                        dis.skipBytes(4);
                        break;
                    default:
                        return true;
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error scanning constant pool, falling back to full remapping pass", e);
            return true;
        }

        return false;
    }

    private final Map<String, Boolean> isWidgetCache = new ConcurrentHashMap<>();
    public boolean isWidgetRecursiveCached(String owner) {
        if (owner == null) return false;
        return isWidgetCache.computeIfAbsent(owner, this::isWidgetRecursive);
    }

    private boolean isWidgetRecursive(String owner) {
        if (owner == null || "java/lang/Object".equals(owner)) {
            return false;
        }
        String slash = owner.replace('.', '/');
        slash = map(slash);
        if (isWidget(slash)) {
            return true;
        }
        String classResourceName = slash + ".class";
        ClassLoader cl = activeClassLoader;
        if (cl == null) {
            cl = Thread.currentThread().getContextClassLoader();
        }
        if (cl == null) {
            cl = BytecodeTransformer.class.getClassLoader();
        }
        if (cl == null) {
            cl = ClassLoader.getSystemClassLoader();
        }
        try (java.io.InputStream is = cl.getResourceAsStream(classResourceName)) {
            if (is != null) {
                ClassReader cr = new ClassReader(is);
                String superName = cr.getSuperName();
                if (superName != null) {
                    return isWidgetRecursive(superName);
                }
            }
        } catch (Exception e) {
            // Ignore
        }
        return false;
    }

    private final Map<String, Boolean> isBlockCache = new ConcurrentHashMap<>();
    public boolean isBlockRecursiveCached(String owner) {
        if (owner == null) return false;
        return isBlockCache.computeIfAbsent(owner, this::isBlockRecursive);
    }

    private boolean isBlockRecursive(String owner) {
        if (owner == null || "java/lang/Object".equals(owner)) {
            return false;
        }
        String slash = owner.replace('.', '/');
        slash = map(slash);
        if (slash.equals("net/minecraft/world/level/block/Block") || slash.equals("dfy") ||
            slash.equals("net/minecraft/world/level/block/state/BlockBehaviour") || slash.equals("dtb")) {
            return true;
        }
        String classResourceName = slash + ".class";
        ClassLoader cl = activeClassLoader;
        if (cl == null) {
            cl = Thread.currentThread().getContextClassLoader();
        }
        if (cl == null) {
            cl = BytecodeTransformer.class.getClassLoader();
        }
        if (cl == null) {
            cl = ClassLoader.getSystemClassLoader();
        }
        try (java.io.InputStream is = cl.getResourceAsStream(classResourceName)) {
            if (is != null) {
                ClassReader cr = new ClassReader(is);
                String superName = cr.getSuperName();
                if (superName != null) {
                    return isBlockRecursive(superName);
                }
            }
        } catch (Exception e) {
            // Ignore
        }
        return false;
    }

    private final Map<String, Boolean> isItemCache = new ConcurrentHashMap<>();
    public boolean isItemRecursiveCached(String owner) {
        if (owner == null) return false;
        return isItemCache.computeIfAbsent(owner, this::isItemRecursive);
    }

    private boolean isItemRecursive(String owner) {
        if (owner == null || "java/lang/Object".equals(owner)) {
            return false;
        }
        String slash = owner.replace('.', '/');
        slash = map(slash);
        if (slash.equals("net/minecraft/world/item/Item") || slash.equals("cul")) {
            return true;
        }
        String classResourceName = slash + ".class";
        ClassLoader cl = activeClassLoader;
        if (cl == null) {
            cl = Thread.currentThread().getContextClassLoader();
        }
        if (cl == null) {
            cl = BytecodeTransformer.class.getClassLoader();
        }
        if (cl == null) {
            cl = ClassLoader.getSystemClassLoader();
        }
        try (java.io.InputStream is = cl.getResourceAsStream(classResourceName)) {
            if (is != null) {
                ClassReader cr = new ClassReader(is);
                String superName = cr.getSuperName();
                if (superName != null) {
                    return isItemRecursive(superName);
                }
            }
        } catch (Exception e) {
            // Ignore
        }
        return false;
    }

    private boolean isScreen(String owner) {
        // Delegate to active Chainlink modules
        for (Chainlink link : ChainLauncher.getActiveLinks()) {
            if (link.isScreenClass(owner)) {
                return true;
            }
        }
        return false;
    }

    private boolean isWidget(String owner) {
        // Delegate to active Chainlink modules
        for (Chainlink link : ChainLauncher.getActiveLinks()) {
            if (link.isWidgetClass(owner)) {
                return true;
            }
        }
        return false;
    }

    private boolean isListener(String owner) {
        // Delegate to active Chainlink modules
        for (Chainlink link : ChainLauncher.getActiveLinks()) {
            if (link.isListenerClass(owner)) {
                return true;
            }
        }
        return false;
    }

    public String getDeobfClassName(String owner) {
        if (owner == null) return null;
        String slashOwner = owner.replace('.', '/');
        String deobf = OBF_TO_MOJANG_CLASS_MAPPINGS.get(slashOwner);
        if (deobf == null) {
            deobf = OBF_TO_DEOBF_CLASS_MAPPINGS.get(slashOwner);
        }
        return deobf != null ? deobf : slashOwner;
    }

    public void loadMojangMappings(String path) {
        File mappingsFile = new File(path);
        if (!mappingsFile.exists()) {
            String gameDirProp = System.getProperty("chainloader.gameDir");
            if (gameDirProp != null) {
                mappingsFile = new File(gameDirProp, path);
            }
        }
        
        if (mappingsFile.exists()) {
            System.out.println("[ChainLoader] Parsing Mojang client mappings from " + mappingsFile.getAbsolutePath() + "...");
            long startTime = System.currentTimeMillis();
            int classCount = 0;
            int methodCount = 0;
            int fieldCount = 0;
            try (BufferedReader reader = new BufferedReader(new FileReader(mappingsFile))) {
                String line;
                String currentDeobfClass = null;
                String currentObfClass = null;
                while ((line = reader.readLine()) != null) {
                    if (line.isEmpty()) {
                        continue;
                    }
                    if (line.trim().startsWith("#")) {
                        continue;
                    }
                    if (!line.startsWith(" ")) {
                        int arrowIdx = line.indexOf(" -> ");
                        if (arrowIdx != -1 && line.endsWith(":")) {
                            String deobf = line.substring(0, arrowIdx).trim();
                            String obf = line.substring(arrowIdx + 4, line.length() - 1).trim();
                            
                            currentDeobfClass = deobf.replace('.', '/');
                            currentObfClass = obf.replace('.', '/');
                            
                            addClassMapping(currentDeobfClass, currentObfClass);
                            classCount++;
                        } else {
                            currentDeobfClass = null;
                            currentObfClass = null;
                        }
                    } else if (currentDeobfClass != null) {
                        String trimmed = line.trim();
                        int arrowIdx = trimmed.indexOf(" -> ");
                        if (arrowIdx != -1) {
                            String deobfPart = trimmed.substring(0, arrowIdx).trim();
                            String obfPart = trimmed.substring(arrowIdx + 4).trim();
                            
                            int colonIdx = deobfPart.lastIndexOf(':');
                            String methodOrField = colonIdx != -1 ? deobfPart.substring(colonIdx + 1) : deobfPart;
                            
                            if (methodOrField.contains("(")) {
                                int parenIdx = methodOrField.indexOf('(');
                                String returnTypeAndName = methodOrField.substring(0, parenIdx).trim();
                                int lastSpaceIdx = returnTypeAndName.lastIndexOf(' ');
                                String methodName = lastSpaceIdx != -1 ? returnTypeAndName.substring(lastSpaceIdx + 1) : returnTypeAndName;
                                
                                String key = currentDeobfClass + "." + methodName;
                                addMojangMethodMapping(key, obfPart);
                                methodCount++;
                            } else {
                                int lastSpaceIdx = methodOrField.lastIndexOf(' ');
                                String fieldName = lastSpaceIdx != -1 ? methodOrField.substring(lastSpaceIdx + 1) : methodOrField;
                                
                                String key = currentDeobfClass + "." + fieldName;
                                addMojangFieldMapping(key, obfPart);
                                fieldCount++;
                            }
                        }
                    }
                }
                long duration = System.currentTimeMillis() - startTime;
                System.out.println("[ChainLoader] Parsed Mojang: " + classCount + " classes, " + methodCount + " methods, " + fieldCount + " fields in " + duration + "ms.");
            } catch (Exception e) {
                System.err.println("[ChainLoader] Failed to read mappings: " + e.getMessage());
            }
        }
    }

    public void loadTinyMappings(String path) {
        File intermediaryFile = new File(path);
        if (!intermediaryFile.exists()) {
            String gameDirProp = System.getProperty("chainloader.gameDir");
            if (gameDirProp != null) {
                intermediaryFile = new File(gameDirProp, path);
            }
        }
        
        if (intermediaryFile.exists()) {
            System.out.println("[ChainLoader] Parsing Intermediary mappings from " + intermediaryFile.getAbsolutePath() + "...");
            long startTime = System.currentTimeMillis();
            int classCount = 0;
            int methodCount = 0;
            int fieldCount = 0;
            try (BufferedReader reader = new BufferedReader(new FileReader(intermediaryFile))) {
                String line;
                reader.readLine();
                while ((line = reader.readLine()) != null) {
                    if (line.isEmpty()) {
                        continue;
                    }
                    if (line.startsWith("c\t")) {
                        String[] parts = line.split("\t");
                        if (parts.length >= 3) {
                            String obf = parts[1].trim();
                            String Inter = parts[2].trim();
                            addClassMapping(Inter, obf);
                            classCount++;
                        }
                    } else if (line.startsWith("\tf\t")) {
                        String[] parts = line.split("\t");
                        if (parts.length >= 5) {
                            String obf = parts[3].trim();
                            String Inter = parts[4].trim();
                            addIntermediaryFieldMapping(Inter, obf);
                            fieldCount++;
                        }
                    } else if (line.startsWith("\tm\t")) {
                        String[] parts = line.split("\t");
                        if (parts.length >= 5) {
                            String obf = parts[3].trim();
                            String Inter = parts[4].trim();
                            addIntermediaryMethodMapping(Inter, obf);
                            methodCount++;
                        }
                    }
                }
                long duration = System.currentTimeMillis() - startTime;
                System.out.println("[ChainLoader] Parsed Intermediary: " + classCount + " classes, " + methodCount + " methods, " + fieldCount + " fields in " + duration + "ms.");
            } catch (Exception e) {
                System.err.println("[ChainLoader] Failed to read Intermediary mappings: " + e.getMessage());
            }
        }
    }

    public void loadSeargeMappings(String path) {
        File tsrgFile = new File(path);
        if (!tsrgFile.exists()) {
            String gameDirProp = System.getProperty("chainloader.gameDir");
            if (gameDirProp != null) {
                tsrgFile = new File(gameDirProp, path);
            }
        }
        
        if (tsrgFile.exists()) {
            System.out.println("[ChainLoader] Parsing Searge mappings from " + tsrgFile.getAbsolutePath() + "...");
            long startTime = System.currentTimeMillis();
            int methodCount = 0;
            int fieldCount = 0;
            try (BufferedReader reader = new BufferedReader(new FileReader(tsrgFile))) {
                String line;
                reader.readLine();
                while ((line = reader.readLine()) != null) {
                    if (line.isEmpty() || line.startsWith("#")) {
                        continue;
                    }
                    if (line.startsWith("\t\t")) {
                        continue;
                    }
                    if (line.startsWith("\t")) {
                        String trimmed = line.trim();
                        String[] parts = trimmed.split("\\s+");
                        if (parts.length >= 2) {
                            String obf = parts[0];
                            if (parts[1].startsWith("(")) {
                                if (parts.length >= 3) {
                                    String srgName = parts[2];
                                    addSeargeMethodMapping(srgName, obf);
                                    methodCount++;
                                }
                            } else {
                                String srgName = parts[1];
                                addSeargeFieldMapping(srgName, obf);
                                fieldCount++;
                            }
                        }
                    }
                }
                long duration = System.currentTimeMillis() - startTime;
                System.out.println("[ChainLoader] Parsed Searge: " + methodCount + " methods, " + fieldCount + " fields in " + duration + "ms.");
            } catch (Exception e) {
                System.err.println("[ChainLoader] Failed to read Searge mappings: " + e.getMessage());
            }
        }
    }

    private class ChainRemapper extends Remapper {
        private final Map<String, String> recursiveMethodCache = new java.util.concurrent.ConcurrentHashMap<>();
        private final Map<String, String> recursiveFieldCache = new java.util.concurrent.ConcurrentHashMap<>();

        private String lookupFieldMappingRecursive(String owner, String name, String descriptor) {
            if (owner == null || "java/lang/Object".equals(owner)) {
                return null;
            }

            String deobfOwner = getDeobfClassName(owner);
            String mojangKey = deobfOwner + "." + name;
            String mappedName = MOJANG_FIELD_MAPPINGS.get(mojangKey);
            if (mappedName != null) {
                return mappedName;
            }

            String classResourceName = owner.replace('.', '/') + ".class";
            ClassLoader cl = activeClassLoader;
            if (cl == null) {
                cl = Thread.currentThread().getContextClassLoader();
            }
            if (cl == null) {
                cl = BytecodeTransformer.class.getClassLoader();
            }
            if (cl == null) {
                cl = ClassLoader.getSystemClassLoader();
            }

            try (java.io.InputStream is = cl.getResourceAsStream(classResourceName)) {
                if (is != null) {
                    ClassReader cr = new ClassReader(is);
                    String superName = cr.getSuperName();
                    String[] interfaces = cr.getInterfaces();

                    if (superName != null && !"java/lang/Object".equals(superName)) {
                        String mapped = lookupFieldMappingRecursive(superName, name, descriptor);
                        if (mapped != null) {
                            return mapped;
                        }
                    }

                    if (interfaces != null) {
                        for (String itf : interfaces) {
                            String mapped = lookupFieldMappingRecursive(itf, name, descriptor);
                            if (mapped != null) {
                                return mapped;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                // Ignore
            }

            return null;
        }

        public boolean isWidgetRecursiveCached(String owner) {
            return BytecodeTransformer.this.isWidgetRecursiveCached(owner);
        }

        private String lookupMethodMappingRecursive(String owner, String name, String descriptor) {
            if (owner == null || "java/lang/Object".equals(owner)) {
                return null;
            }

            String deobfOwner = getDeobfClassName(owner);
            String mojangKey = deobfOwner + "." + name;
            String mappedName = MOJANG_METHOD_MAPPINGS.get(mojangKey);
            if (mappedName != null) {
                return mappedName;
            }

            String classResourceName = owner.replace('.', '/') + ".class";
            ClassLoader cl = activeClassLoader;
            if (cl == null) {
                cl = Thread.currentThread().getContextClassLoader();
            }
            if (cl == null) {
                cl = BytecodeTransformer.class.getClassLoader();
            }
            if (cl == null) {
                cl = ClassLoader.getSystemClassLoader();
            }

            try (java.io.InputStream is = cl.getResourceAsStream(classResourceName)) {
                if (is != null) {
                    ClassReader cr = new ClassReader(is);
                    String superName = cr.getSuperName();
                    String[] interfaces = cr.getInterfaces();

                    if (superName != null && !"java/lang/Object".equals(superName)) {
                        String mapped = lookupMethodMappingRecursive(superName, name, descriptor);
                        if (mapped != null) {
                            return mapped;
                        }
                    }

                    if (interfaces != null) {
                        for (String itf : interfaces) {
                            String mapped = lookupMethodMappingRecursive(itf, name, descriptor);
                            if (mapped != null) {
                                return mapped;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                // Ignore
            }

            return null;
        }

        private boolean isClass(String owner, String deobfName, String obfName) {
            if (owner == null) return false;
            String ownerSlash = owner.replace('.', '/');
            String deobfSlash = deobfName.replace('.', '/');
            String obfSlash = obfName.replace('.', '/');
            return ownerSlash.equals(deobfSlash) || ownerSlash.equals(obfSlash);
        }

        private String getDeobfClassName(String owner) {
            return BytecodeTransformer.this.getDeobfClassName(owner);
        }

        @Override
        public String mapMethodName(String owner, String name, String descriptor) {
            // 1. Query active Chainlink modules
            String mappedDesc = mapMethodDesc(descriptor);
            for (Chainlink link : ChainLauncher.getActiveLinks()) {
                String mapped = link.mapMethod(owner, name, mappedDesc);
                if (mapped == null) {
                    mapped = link.mapMethod(owner, name, descriptor);
                }
                if (mapped != null) {
                    return mapped;
                }
            }

            // 2. Fallbacks to parsed mappings
            if (name.startsWith("method_") || name.startsWith("comp_")) {
                String mapped = INTERMEDIARY_METHOD_MAPPINGS.get(name);
                if (mapped != null) {
                    return mapped;
                }
            }
            if (name.startsWith("m_")) {
                String mapped = SEARGE_METHOD_MAPPINGS.get(name);
                if (mapped != null) {
                    return mapped;
                }
            }

            String deobfOwner = getDeobfClassName(owner);
            String mojangKey = deobfOwner + "." + name;
            String mappedName = MOJANG_METHOD_MAPPINGS.get(mojangKey);
            if (mappedName != null) {
                return mappedName;
            }

            // Recursive lookup
            String cacheKey = owner + "." + name + descriptor;
            String cached = recursiveMethodCache.get(cacheKey);
            if (cached != null) {
                return cached.isEmpty() ? super.mapMethodName(owner, name, descriptor) : cached;
            }

            String mapped = lookupMethodMappingRecursive(owner, name, descriptor);
            if (mapped != null) {
                recursiveMethodCache.put(cacheKey, mapped);
                return mapped;
            } else {
                recursiveMethodCache.put(cacheKey, "");
            }

            return super.mapMethodName(owner, name, descriptor);
        }

        @Override
        public String mapFieldName(String owner, String name, String descriptor) {
            // 1. Query active Chainlink modules
            for (Chainlink link : ChainLauncher.getActiveLinks()) {
                String mapped = link.mapField(owner, name, descriptor);
                if (mapped != null) {
                    return mapped;
                }
            }

            // 2. Fallbacks
            String deobfOwner = getDeobfClassName(owner);
            String mojangKey = deobfOwner + "." + name;
            String mappedName = MOJANG_FIELD_MAPPINGS.get(mojangKey);
            if (mappedName != null) {
                return mappedName;
            }
            if (name.startsWith("field_") || name.startsWith("comp_")) {
                String mapped = INTERMEDIARY_FIELD_MAPPINGS.get(name);
                if (mapped != null) {
                    return mapped;
                }
            }
            if (name.startsWith("f_")) {
                String mapped = SEARGE_FIELD_MAPPINGS.get(name);
                if (mapped != null) {
                    return mapped;
                }
            }

            // Recursive lookup
            String cacheKey = owner + "." + name + descriptor;
            String cached = recursiveFieldCache.get(cacheKey);
            if (cached != null) {
                return cached.isEmpty() ? super.mapFieldName(owner, name, descriptor) : cached;
            }

            String mapped = lookupFieldMappingRecursive(owner, name, descriptor);
            if (mapped != null) {
                recursiveFieldCache.put(cacheKey, mapped);
                return mapped;
            } else {
                recursiveFieldCache.put(cacheKey, "");
            }

            return super.mapFieldName(owner, name, descriptor);
        }

        @Override
        public String map(String internalName) {
            if (internalName == null) {
                return null;
            }
            
            // Query active Chainlink modules
            for (Chainlink link : ChainLauncher.getActiveLinks()) {
                String mapped = link.mapClass(internalName);
                if (mapped != null) {
                    internalName = mapped;
                }
            }

            // Transitive O(1) Class map lookup
            String current = internalName;
            while (true) {
                String mapped = classMappings.get(current);
                if (mapped != null && !mapped.equals(current)) {
                    current = mapped;
                } else {
                    break;
                }
            }
            if (!current.equals(internalName)) {
                return current;
            }
            
            // Inner class check
            int dollarIdx = internalName.indexOf('$');
            if (dollarIdx != -1) {
                String outer = internalName.substring(0, dollarIdx);
                String outerMapped = map(outer);
                if (outerMapped != null) {
                    return outerMapped + internalName.substring(dollarIdx);
                }
            }
            
            // Package prefix check
            for (Map.Entry<String, String> entry : packageMappings.entrySet()) {
                String oldPrefix = entry.getKey();
                if (internalName.startsWith(oldPrefix + "/")) {
                    String newPrefix = entry.getValue();
                    String suffix = internalName.substring(oldPrefix.length());
                    return map(newPrefix + suffix);
                }
            }
            
            return super.map(internalName);
        }

        @Override
        public Object mapValue(Object value) {
            if (value instanceof String) {
                String strValue = (String) value;
                if (strValue.isEmpty()) {
                    return value;
                }
                
                String mapped = classMappings.get(strValue);
                if (mapped != null) {
                    return mapped;
                }
                
                int dollarIdx = strValue.indexOf('$');
                if (dollarIdx != -1) {
                    String outer = strValue.substring(0, dollarIdx);
                    String outerMapped = classMappings.get(outer);
                    if (outerMapped != null) {
                        return outerMapped + strValue.substring(dollarIdx);
                    }
                }
                
                String dotToSlash = strValue.replace('.', '/');
                String mappedDot = classMappings.get(dotToSlash);
                if (mappedDot != null) {
                    return mappedDot.replace('/', '.');
                }
                
                int dotDollarIdx = strValue.indexOf('$');
                if (dotDollarIdx != -1) {
                    String outerDot = strValue.substring(0, dotDollarIdx).replace('.', '/');
                    String outerMapped = classMappings.get(outerDot);
                    if (outerMapped != null) {
                        return outerMapped.replace('/', '.') + strValue.substring(dotDollarIdx);
                    }
                }
                
                for (Map.Entry<String, String> entry : packageMappings.entrySet()) {
                    String oldPrefix = entry.getKey();
                    if (strValue.startsWith(oldPrefix + "/")) {
                        String newPrefix = entry.getValue();
                        return newPrefix + strValue.substring(oldPrefix.length());
                    }
                }
                
                for (Map.Entry<String, String> entry : packageMappings.entrySet()) {
                    String oldPrefix = entry.getKey();
                    String oldPrefixDot = oldPrefix.replace('/', '.');
                    if (strValue.startsWith(oldPrefixDot + ".")) {
                        String newPrefixDot = entry.getValue().replace('/', '.');
                        return newPrefixDot + strValue.substring(oldPrefixDot.length());
                    }
                }
            }
            return super.mapValue(value);
        }
    }

    private static class InterfaceCodecBridgeVisitor extends org.objectweb.asm.ClassVisitor {
        private final String className;
        private boolean isInterface;
        public boolean modified = false;

        public InterfaceCodecBridgeVisitor(int api, org.objectweb.asm.ClassVisitor cv, String className) {
            super(api, cv);
            this.className = className;
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            this.isInterface = (access & org.objectweb.asm.Opcodes.ACC_INTERFACE) != 0;
            super.visit(version, access, name, signature, superName, interfaces);
        }

        @Override
        public org.objectweb.asm.MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            String slashName = className.replace('.', '/');
            boolean isRecipeSerializer = slashName.equals("net/minecraft/class_1865") || slashName.equals("net/minecraft/world/item/crafting/RecipeSerializer") || slashName.equals("cze");
            
            if (isInterface && isRecipeSerializer && ("a".equals(name) || "codec".equals(name)) && "()Lcom/mojang/serialization/MapCodec;".equals(descriptor) && (access & org.objectweb.asm.Opcodes.ACC_ABSTRACT) != 0) {
                int newAccess = access & ~org.objectweb.asm.Opcodes.ACC_ABSTRACT;
                org.objectweb.asm.MethodVisitor mv = super.visitMethod(newAccess, name, descriptor, signature, exceptions);
                mv.visitCode();
                mv.visitVarInsn(org.objectweb.asm.Opcodes.ALOAD, 0); // load 'this'
                mv.visitMethodInsn(org.objectweb.asm.Opcodes.INVOKESTATIC, "net/chainloader/loader/compat/bridge/EventBridgeHelper", "getRecipeSerializerCodec", "(Ljava/lang/Object;)Lcom/mojang/serialization/MapCodec;", false);
                mv.visitInsn(org.objectweb.asm.Opcodes.ARETURN);
                mv.visitMaxs(1, 1);
                mv.visitEnd();
                modified = true;
                return null;
            }
            
            boolean isStreamCodecDesc = "()Lyx;".equals(descriptor) || "()Lnet/minecraft/network/codec/StreamCodec;".equals(descriptor);
            if (isInterface && isRecipeSerializer && ("b".equals(name) || "streamCodec".equals(name)) && isStreamCodecDesc && (access & org.objectweb.asm.Opcodes.ACC_ABSTRACT) != 0) {
                int newAccess = access & ~org.objectweb.asm.Opcodes.ACC_ABSTRACT;
                org.objectweb.asm.MethodVisitor mv = super.visitMethod(newAccess, name, descriptor, signature, exceptions);
                mv.visitCode();
                mv.visitVarInsn(org.objectweb.asm.Opcodes.ALOAD, 0); // load 'this'
                String targetReturnClass = "()Lyx;".equals(descriptor) ? "yx" : "net/minecraft/network/codec/StreamCodec";
                String targetReturnDesc = "()Lyx;".equals(descriptor) ? "Lyx;" : "Lnet/minecraft/network/codec/StreamCodec;";
                mv.visitMethodInsn(org.objectweb.asm.Opcodes.INVOKESTATIC, "net/chainloader/loader/compat/bridge/EventBridgeHelper", "getRecipeSerializerStreamCodec", "(Ljava/lang/Object;)L" + targetReturnClass + ";", false);
                mv.visitTypeInsn(org.objectweb.asm.Opcodes.CHECKCAST, targetReturnClass);
                mv.visitInsn(org.objectweb.asm.Opcodes.ARETURN);
                mv.visitMaxs(1, 1);
                mv.visitEnd();
                modified = true;
                return null;
            }

            if (isInterface && ("a".equals(name) || "codec".equals(name)) && "()Lcom/mojang/serialization/MapCodec;".equals(descriptor) && (access & org.objectweb.asm.Opcodes.ACC_ABSTRACT) != 0) {
                // Change method from abstract to concrete (remove ACC_ABSTRACT)
                int newAccess = access & ~org.objectweb.asm.Opcodes.ACC_ABSTRACT;
                org.objectweb.asm.MethodVisitor mv = super.visitMethod(newAccess, name, descriptor, signature, exceptions);
                // Write a default method body!
                mv.visitCode();
                mv.visitVarInsn(org.objectweb.asm.Opcodes.ALOAD, 0); // load 'this'
                mv.visitMethodInsn(org.objectweb.asm.Opcodes.INVOKESTATIC, "net/chainloader/loader/compat/bridge/EventBridgeHelper", "getLegacyCodecBridge", "(Ljava/lang/Object;)Lcom/mojang/serialization/MapCodec;", false);
                mv.visitInsn(org.objectweb.asm.Opcodes.ARETURN);
                mv.visitMaxs(1, 1);
                mv.visitEnd();
                modified = true;
                return null; // Skip abstract method body generation
            }
            return super.visitMethod(access, name, descriptor, signature, exceptions);
        }
    }
}
