package net.chainloader.loader.compat.asset;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Runtime namespace and asset path remapper for compatibility shims.
 * <p>
 * Legacy mods (e.g., Fabric mods running on a Forge-like wrapper, or older mods referencing
 * outdated naming schemes) often hardcode texture and model paths, namespaces, or specific
 * resource locations.
 * </p>
 * <p>
 * This class provides a centralized registry for relocating asset namespaces (e.g., "fabric" -> "chainloader"),
 * remapping exact asset paths, and prefix matching. It implements {@link VirtualAssetPack.PathRelocator}
 * to coordinate directly with the in-memory virtual asset pack. It also implements an ASM-based
 * bytecode transformer to dynamically patch string constants and ResourceLocation instantiation
 * in mod classes at runtime.
 * </p>
 */
public class AssetPathRelocator implements VirtualAssetPack.PathRelocator {

    private static final Logger LOGGER = Logger.getLogger(AssetPathRelocator.class.getName());
    private static final AssetPathRelocator INSTANCE = new AssetPathRelocator();

    public static AssetPathRelocator getInstance() {
        return INSTANCE;
    }

    // Map of old namespace to new namespace (e.g., "fabric" -> "chainloader")
    private final Map<String, String> namespaceMappings = new ConcurrentHashMap<>();

    // Map of old full path to new full path (e.g., "legacy_mod:textures/entity/old.png" -> "new_mod:textures/entity/new.png")
    private final Map<String, String> pathMappings = new ConcurrentHashMap<>();

    // Map of path prefixes (e.g., "legacy_mod:models/block/" -> "new_mod:models/block/")
    private final Map<String, String> prefixMappings = new ConcurrentHashMap<>();

    private AssetPathRelocator() {
        // Pre-populate with default compatibility mappings
        registerDefaultMappings();
    }

    private void registerDefaultMappings() {
        // Register standard mod compat namespace relocations
        registerNamespaceMapping("fabric", "chainloader");
        registerNamespaceMapping("legacy_compat", "chainloader");
    }

    /**
     * Registers a namespace remapping rule.
     *
     * @param sourceNamespace The original namespace (e.g., "mymod")
     * @param targetNamespace The target namespace to relocate to (e.g., "mymod_remapped")
     */
    public void registerNamespaceMapping(String sourceNamespace, String targetNamespace) {
        if (sourceNamespace != null && targetNamespace != null) {
            namespaceMappings.put(sourceNamespace, targetNamespace);
            LOGGER.info(() -> String.format("Registered namespace mapping: '%s' -> '%s'", sourceNamespace, targetNamespace));
        }
    }

    /**
     * Registers a complete path remapping rule (e.g., for redirecting a specific model or texture).
     *
     * @param sourcePath The original path string (e.g., "mymod:textures/old.png")
     * @param targetPath The target path string (e.g., "mymod:textures/new.png")
     */
    public void registerPathMapping(String sourcePath, String targetPath) {
        if (sourcePath != null && targetPath != null) {
            pathMappings.put(sourcePath, targetPath);
            LOGGER.fine(() -> String.format("Registered path mapping: '%s' -> '%s'", sourcePath, targetPath));
        }
    }

    /**
     * Registers a path prefix remapping rule (e.g., for folder structure relocations).
     *
     * @param sourcePrefix The original path prefix (e.g., "mymod:textures/old_dir/")
     * @param targetPrefix The target path prefix (e.g., "mymod:textures/new_dir/")
     */
    public void registerPrefixMapping(String sourcePrefix, String targetPrefix) {
        if (sourcePrefix != null && targetPrefix != null) {
            prefixMappings.put(sourcePrefix, targetPrefix);
            LOGGER.fine(() -> String.format("Registered prefix mapping: '%s' -> '%s'", sourcePrefix, targetPrefix));
        }
    }

    /**
     * Relocates a raw string representation of an asset path or resource location.
     *
     * @param input The input path string (e.g., "mymod:textures/block.png" or "textures/block.png")
     * @return The relocated path string, or the original if no mappings match
     */
    public String relocateString(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        // 0. Ensure path is singularized (blocks -> block, items -> item)
        input = input.replace("textures/blocks/", "textures/block/")
                     .replace("textures/items/", "textures/item/")
                     .replace("models/blocks/", "models/block/")
                     .replace("models/items/", "models/item/")
                     .replace("tags/blocks/", "tags/block/")
                     .replace("tags/items/", "tags/item/");

        // Support zip entry paths (e.g., "assets/namespace/path..." or "data/namespace/path...")
        if (input.startsWith("assets/") && input.length() > 7) {
            String sub = input.substring(7);
            int slash = sub.indexOf('/');
            if (slash > 0) {
                String ns = sub.substring(0, slash);
                String path = sub.substring(slash + 1);
                String relocatedNs = relocateString(ns);
                String relocatedPath = relocateString(path);
                return singularizePath("assets/" + relocatedNs + "/" + relocatedPath);
            }
        }
        if (input.startsWith("data/") && input.length() > 5) {
            String sub = input.substring(5);
            int slash = sub.indexOf('/');
            if (slash > 0) {
                String ns = sub.substring(0, slash);
                String path = sub.substring(slash + 1);
                String relocatedNs = relocateString(ns);
                String relocatedPath = relocateString(path);
                return singularizePath("data/" + relocatedNs + "/" + relocatedPath);
            }
        }

        String relocated = input;
        boolean mapped = false;

        // 1. Check exact path mappings first
        String remapped = pathMappings.get(input);
        if (remapped != null) {
            relocated = remapped;
            mapped = true;
        }

        // 2. Check prefix mappings
        if (!mapped) {
            for (Map.Entry<String, String> entry : prefixMappings.entrySet()) {
                if (input.startsWith(entry.getKey())) {
                    relocated = entry.getValue() + input.substring(entry.getKey().length());
                    mapped = true;
                    break;
                }
            }
        }

        return singularizePath(relocated);
    }

    /**
     * Map plural assets/models paths to singular.
     * (e.g. textures/blocks/ -> textures/block/, textures/items/ -> textures/item/, etc.)
     */
    public String singularizePath(String path) {
        if (path == null) {
            return null;
        }
        String result = path;
        result = result.replace("textures/blocks/", "textures/block/");
        result = result.replace("textures/items/", "textures/item/");
        result = result.replace("models/blocks/", "models/block/");
        result = result.replace("models/items/", "models/item/");
        return result;
    }


    /**
     * Relocates a ResourceLocation based on registered mappings.
     * Used by the Virtual Asset Pack and other runtime modules.
     *
     * @param location The original ResourceLocation
     * @return The relocated ResourceLocation, or the original if no mappings match
     */
    public ResourceLocation relocate(ResourceLocation location) {
        if (location == null) {
            return null;
        }

        String originalStr = location.toString();
        String relocatedStr = relocateString(originalStr);

        if (originalStr.equals(relocatedStr)) {
            return location;
        }

        return new ResourceLocation(relocatedStr);
    }

    /**
     * Relocates a namespace string directly.
     *
     * @param namespace The original namespace
     * @return The relocated namespace, or the original if no mapping exists
     */
    public String relocateNamespace(String namespace) {
        if (namespace == null) {
            return null;
        }
        return namespaceMappings.getOrDefault(namespace, namespace);
    }

    /**
     * Implements VirtualAssetPack.PathRelocator for direct integration with the virtual asset pack.
     */
    @Override
    public ResourceLocation relocate(PackType type, ResourceLocation location) {
        return relocate(location);
    }

    /**
     * Registers this relocator instance into the given VirtualAssetPack.
     *
     * @param pack The target VirtualAssetPack
     */
    public void registerTo(VirtualAssetPack pack) {
        pack.registerPathRelocator(this);
        LOGGER.info(() -> "Registered AssetPathRelocator to VirtualAssetPack: " + pack.packId());
    }

    public Map<String, String> getNamespaceMappings() {
        return Collections.unmodifiableMap(namespaceMappings);
    }

    public Map<String, String> getPathMappings() {
        return Collections.unmodifiableMap(pathMappings);
    }

    public Map<String, String> getPrefixMappings() {
        return Collections.unmodifiableMap(prefixMappings);
    }

    // --- Bytecode Remapping / Relocation Engine ---

    /**
     * Fast constant pool scanner that inspects the bytecode's constant pool structure.
     * If the constant pool does not contain any reference to our target namespaces or paths,
     * we can safely skip the heavy ASM parsing pass.
     *
     * @param classBytes The raw bytecode of the class
     * @return True if the class might contain relocation targets; false otherwise
     */
    public boolean containsRelocationTargets(byte[] classBytes) {
        if (classBytes == null || classBytes.length < 10) {
            return false;
        }

        // If there are no custom mappings registered, we can skip
        if (namespaceMappings.isEmpty() && pathMappings.isEmpty() && prefixMappings.isEmpty()) {
            return false;
        }

        try (DataInputStream dis = new DataInputStream(new ByteArrayInputStream(classBytes))) {
            int magic = dis.readInt();
            if (magic != 0xCAFEBABE) {
                return false;
            }

            dis.skipBytes(4); // minor and major versions
            int constantPoolCount = dis.readUnsignedShort();

            for (int i = 1; i < constantPoolCount; i++) {
                int tag = dis.readUnsignedByte();
                switch (tag) {
                    case 1: // UTF-8 constant
                        String value = dis.readUTF();
                        
                        // Check namespace mappings
                        for (String sourceNamespace : namespaceMappings.keySet()) {
                            if (value.contains(sourceNamespace)) {
                                return true;
                            }
                        }
                        // Check path mappings
                        for (String sourcePath : pathMappings.keySet()) {
                            if (value.contains(sourcePath)) {
                                return true;
                            }
                        }
                        // Check prefix mappings
                        for (String sourcePrefix : prefixMappings.keySet()) {
                            if (value.contains(sourcePrefix)) {
                                return true;
                            }
                        }
                        break;
                    case 3: // Integer
                    case 4: // Float
                        dis.skipBytes(4);
                        break;
                    case 5: // Long
                    case 6: // Double
                        dis.skipBytes(8);
                        i++;
                        break;
                    case 7: // Class reference
                    case 8: // String reference
                    case 16: // MethodType reference
                    case 19: // Module
                    case 20: // Package
                        dis.skipBytes(2);
                        break;
                    case 15: // MethodHandle
                        dis.skipBytes(3);
                        break;
                    case 9: // Fieldref
                    case 10: // Methodref
                    case 11: // InterfaceMethodref
                    case 12: // NameAndType
                    case 17: // Dynamic
                    case 18: // InvokeDynamic
                        dis.skipBytes(4);
                        break;
                    default:
                        // Unknown tag, fallback to true for safety
                        return true;
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error scanning class constant pool for asset relocations, falling back to full pass", e);
            return true;
        }

        return false;
    }

    /**
     * Transforms the given class bytecode, patching string constants containing relocated
     * asset and model paths.
     *
     * @param className The name of the class (dot notation)
     * @param classBytes The raw bytecode
     * @return The transformed bytecode, or the original if unmodified
     */
    public byte[] transform(String className, byte[] classBytes) {
        if (classBytes == null || classBytes.length == 0) {
            return classBytes;
        }

        if (!containsRelocationTargets(classBytes)) {
            return classBytes;
        }

        LOGGER.log(Level.FINE, "Patcher: Scanning class {0} for model/texture string remapping", className);

        try {
            ClassReader classReader = new ClassReader(classBytes);
            ClassWriter classWriter = new ClassWriter(classReader, ClassWriter.COMPUTE_MAXS);
            
            ClassVisitor cv = new AssetRemappingClassVisitor(Opcodes.ASM9, classWriter);
            classReader.accept(cv, ClassReader.EXPAND_FRAMES);
            
            return classWriter.toByteArray();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to patch asset paths in class " + className, e);
            return classBytes;
        }
    }

    /**
     * ClassVisitor that inspects string constants and rewrites them if they match remapping rules.
     */
    private class AssetRemappingClassVisitor extends ClassVisitor {

        public AssetRemappingClassVisitor(int api, ClassVisitor classVisitor) {
            super(api, classVisitor);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
            if (mv == null) return null;
            return new AssetRemappingMethodVisitor(api, mv);
        }
    }

    /**
     * MethodVisitor that intercepts constant loading and rewrites string values containing resource paths.
     */
    private class AssetRemappingMethodVisitor extends MethodVisitor {

        public AssetRemappingMethodVisitor(int api, MethodVisitor methodVisitor) {
            super(api, methodVisitor);
        }

        @Override
        public void visitLdcInsn(Object value) {
            if (value instanceof String) {
                String strValue = (String) value;
                String relocated = relocateString(strValue);
                if (!strValue.equals(relocated)) {
                    LOGGER.log(Level.FINEST, "Remapped constant string in bytecode: \"{0}\" -> \"{1}\"", new Object[]{strValue, relocated});
                    super.visitLdcInsn(relocated);
                    return;
                }
            }
            super.visitLdcInsn(value);
        }
    }
}
