package net.chainloader.loader.compat.asset;

import net.chainloader.loader.compat.pack.VirtualPackResources;
import net.minecraft.server.packs.PackResources;

import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * VanillaAssetPatcher is the runtime manager and patcher hook for Minecraft's asset indexes 
 * and resource pack repositories.
 * <p>
 * This class serves as a central registry and coordination hub for:
 * 1. Dynamically patching the vanilla Minecraft assets index JSON to inject custom resources.
 * 2. Caching in-memory (virtual) asset bytes and routing file-based object lookups to memory.
 * 3. Handling path relocation rules, mapping original vanilla asset paths to custom assets.
 * 4. Injecting virtual resource packs (such as {@link VirtualPackResources} or {@link VirtualAssetPack}) into the game's repository.
 * </p>
 */
public class VanillaAssetPatcher {

    private static final Logger LOGGER = Logger.getLogger("ChainLoader-VanillaAssetPatcher");
    private static final VanillaAssetPatcher INSTANCE = new VanillaAssetPatcher();

    // Mapping of asset path key (e.g. "minecraft/lang/en_us.json") to its virtual metadata
    private final Map<String, AssetMetadata> virtualAssetMetadata = new ConcurrentHashMap<>();

    // Cache of hash string to raw byte contents for dynamic virtual assets
    private final Map<String, byte[]> virtualAssetCache = new ConcurrentHashMap<>();

    // Relocation registry mapping original asset path keys to relocated target asset path keys
    private final Map<String, String> relocations = new ConcurrentHashMap<>();

    // Keep track of synchronized PackResources instances (covers both VirtualPackResources and VirtualAssetPack)
    private final Set<PackResources> registeredPacks = ConcurrentHashMap.newKeySet();

    private VanillaAssetPatcher() {}

    /**
     * Retrieves the singleton instance of the VanillaAssetPatcher.
     *
     * @return The active patcher instance.
     */
    public static VanillaAssetPatcher getInstance() {
        return INSTANCE;
    }

    /**
     * Clears all registered virtual assets, caches, relocations, and registered packs.
     */
    public void clear() {
        virtualAssetMetadata.clear();
        virtualAssetCache.clear();
        relocations.clear();
        registeredPacks.clear();
        LOGGER.info("Cleared all virtual asset patcher registries.");
    }

    /**
     * Registers a virtual asset with raw content. The hash and size are automatically computed.
     *
     * @param path    The asset path key (e.g., "minecraft/textures/block/gold_block.png").
     * @param content The byte content of the asset.
     */
    public void registerVirtualAsset(String path, byte[] content) {
        Objects.requireNonNull(path, "Asset path cannot be null");
        Objects.requireNonNull(content, "Asset content cannot be null");

        String hash = computeSha1(content);
        long size = content.length;

        virtualAssetMetadata.put(path, new AssetMetadata(hash, size));
        virtualAssetCache.put(hash, content);

        LOGGER.fine(() -> String.format("Registered virtual asset: '%s' (hash: %s, size: %d bytes)", path, hash, size));
    }

    /**
     * Registers a virtual asset by providing its metadata without immediate caching of contents.
     * Use this when content is served dynamically by other providers or pack shims.
     *
     * @param path The asset path key.
     * @param hash The precomputed SHA-1 hash.
     * @param size The size in bytes.
     */
    public void registerVirtualAsset(String path, String hash, long size) {
        Objects.requireNonNull(path, "Asset path cannot be null");
        Objects.requireNonNull(hash, "Hash cannot be null");

        virtualAssetMetadata.put(path, new AssetMetadata(hash, size));
        LOGGER.fine(() -> String.format("Registered virtual asset metadata: '%s' (hash: %s, size: %d)", path, hash, size));
    }

    /**
     * Registers a path relocation rule.
     * If Minecraft requests the original path, it will be redirected to use the relocated path's metadata.
     *
     * @param originalPath The original/vanilla path to redirect (e.g., "minecraft/textures/block/stone.png").
     * @param targetPath   The new path to redirect to (e.g., "chainloader/textures/block/custom_stone.png").
     */
    public void registerRelocation(String originalPath, String targetPath) {
        Objects.requireNonNull(originalPath, "Original path cannot be null");
        Objects.requireNonNull(targetPath, "Target path cannot be null");

        relocations.put(originalPath, targetPath);
        LOGGER.info(() -> String.format("Registered asset path relocation: '%s' -> '%s'", originalPath, targetPath));
    }

    /**
     * Resolves an asset path key applying the registered relocation rules recursively.
     *
     * @param path The path to resolve.
     * @return The final resolved path key, or the original path if no rules matched.
     */
    public String resolvePath(String path) {
        if (path == null) return null;
        String resolved = path;
        int limit = 10; // Prevent circular reference loops
        while (relocations.containsKey(resolved) && limit-- > 0) {
            resolved = relocations.get(resolved);
        }
        return resolved;
    }

    /**
     * Relocates an asset path key (e.g. "minecraft/textures/block/stone.png") using both 
     * local relocations and global {@link AssetPathRelocator} rules.
     *
     * @param assetPath The original asset path.
     * @return The final relocated asset path.
     */
    public String relocateAssetPath(String assetPath) {
        if (assetPath == null) return null;
        
        // 1. First resolve using local relocations
        String resolved = resolvePath(assetPath);
        
        // 2. Resolve using AssetPathRelocator
        // AssetPathRelocator uses ResourceLocation format: namespace:path
        int slashIndex = resolved.indexOf('/');
        if (slashIndex > 0) {
            String namespace = resolved.substring(0, slashIndex);
            String pathPart = resolved.substring(slashIndex + 1);
            String resourceLocationStr = namespace + ":" + pathPart;
            
            // Relocate using the global relocator
            String relocatedStr = AssetPathRelocator.getInstance().relocateString(resourceLocationStr);
            
            // Convert back to asset path format: namespace/path
            int colonIndex = relocatedStr.indexOf(':');
            if (colonIndex > 0) {
                resolved = relocatedStr.substring(0, colonIndex) + "/" + relocatedStr.substring(colonIndex + 1);
            }
        }
        return resolved;
    }

    /**
     * Checks if a virtual asset content cache contains the specified object hash.
     *
     * @param hash The asset object hash.
     * @return True if cached; false otherwise.
     */
    public boolean hasVirtualAsset(String hash) {
        return hash != null && virtualAssetCache.containsKey(hash);
    }

    /**
     * Retrieves the cached byte content of a virtual asset by its hash.
     *
     * @param hash The asset object hash.
     * @return The raw bytes, or null if not found.
     */
    public byte[] getVirtualAssetContent(String hash) {
        return hash != null ? virtualAssetCache.get(hash) : null;
    }

    /**
     * Registers a PackResources instance to be injected into the game's PackRepository,
     * without immediately scanning and caching all its resources into the asset index.
     *
     * @param pack The pack resources instance.
     */
    public void registerPack(PackResources pack) {
        if (pack != null) {
            registeredPacks.add(pack);
            LOGGER.info(() -> "Registered PackResources without indexing: " + pack.packId());
        }
    }

    /**
     * Synchronizes this patcher with a generic {@link PackResources} instance (such as {@link VirtualPackResources} 
     * or {@link VirtualAssetPack}).
     * <p>
     * It registers all of its CLIENT_RESOURCES as virtual assets in the patcher index,
     * and if it is an instance of {@link VirtualAssetPack}, it registers an index patcher hook
     * to apply path relocations dynamically.
     * </p>
     *
     * @param pack The pack resources instance.
     */
    public void syncWithPackResources(PackResources pack) {
        if (pack == null) return;
        registeredPacks.add(pack);
        LOGGER.info(() -> "Synchronizing asset index patcher with pack: " + pack.packId());

        try {
            // Retrieve resources map from the pack class via reflection
            Field resourcesField = pack.getClass().getDeclaredField("resources");
            resourcesField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<?, Map<?, Supplier<byte[]>>> resources = (Map<?, Map<?, Supplier<byte[]>>>) resourcesField.get(pack);

            for (Map.Entry<?, Map<?, Supplier<byte[]>>> entry : resources.entrySet()) {
                Enum<?> packType = (Enum<?>) entry.getKey();
                // Filter CLIENT_RESOURCES (assets)
                if ("CLIENT_RESOURCES".equals(packType.name())) {
                    Map<?, Supplier<byte[]>> clientMap = entry.getValue();
                    for (Map.Entry<?, Supplier<byte[]>> resourceEntry : clientMap.entrySet()) {
                        Object resourceLocation = resourceEntry.getKey();
                        Supplier<byte[]> contentSupplier = resourceEntry.getValue();

                        if (resourceLocation != null && contentSupplier != null) {
                            // Extract namespace and path from ResourceLocation using direct calls
                            net.minecraft.resources.ResourceLocation loc = (net.minecraft.resources.ResourceLocation) resourceLocation;
                            String namespace = loc.getNamespace();
                            String path = loc.getPath();

                            // Asset index keys map to namespace + "/" + path
                            String assetPath = namespace + "/" + path;
                            byte[] content = contentSupplier.get();
                            if (content != null) {
                                registerVirtualAsset(assetPath, content);
                                LOGGER.fine(() -> "Synced asset: " + assetPath);
                            }
                        }
                    }
                }
            }

            // Register the IndexPatcher hook if it is VirtualAssetPack to dynamically apply relocations in JSON assets
            if (pack instanceof VirtualAssetPack) {
                VirtualAssetPack assetPack = (VirtualAssetPack) pack;
                assetPack.registerIndexPatcher((location, rawJson) -> {
                    String patchedJson = rawJson;
                    boolean modified = false;
                    // Apply local relocations
                    for (Map.Entry<String, String> relocation : relocations.entrySet()) {
                        String original = relocation.getKey();
                        String target = relocation.getValue();
                        if (patchedJson.contains(original)) {
                            patchedJson = patchedJson.replace(original, target);
                            modified = true;
                        }
                    }
                    // Apply relocations from global AssetPathRelocator
                    for (Map.Entry<String, String> entry : AssetPathRelocator.getInstance().getNamespaceMappings().entrySet()) {
                        String originalNamespace = entry.getKey();
                        String targetNamespace = entry.getValue();
                        String origColon = "\"" + originalNamespace + ":";
                        String targetColon = "\"" + targetNamespace + ":";
                        if (patchedJson.contains(origColon)) {
                            patchedJson = patchedJson.replace(origColon, targetColon);
                            modified = true;
                        }
                        String origSlash = "\"" + originalNamespace + "/";
                        String targetSlash = "\"" + targetNamespace + "/";
                        if (patchedJson.contains(origSlash)) {
                            patchedJson = patchedJson.replace(origSlash, targetSlash);
                            modified = true;
                        }
                    }
                    for (Map.Entry<String, String> entry : AssetPathRelocator.getInstance().getPathMappings().entrySet()) {
                        if (patchedJson.contains(entry.getKey())) {
                            patchedJson = patchedJson.replace(entry.getKey(), entry.getValue());
                            modified = true;
                        }
                    }
                    if (modified) {
                        LOGGER.fine(() -> "Dynamic JSON relocation applied for resource: " + location);
                    }
                    return patchedJson;
                });
            }

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to sync assets with pack resources via reflection", e);
        }
    }

    /**
     * Synchronizes this patcher with a {@link VirtualPackResources} instance.
     *
     * @param virtualPack The virtual pack resources instance.
     */
    public void syncWithVirtualPack(VirtualPackResources virtualPack) {
        syncWithPackResources(virtualPack);
    }

    /**
     * Synchronizes this patcher with a {@link VirtualAssetPack} instance.
     *
     * @param virtualPack The virtual asset pack instance.
     */
    public void syncWithVirtualAssetPack(VirtualAssetPack virtualPack) {
        syncWithPackResources(virtualPack);
    }

    /**
     * Patches the raw vanilla Minecraft assets index JSON by injecting all registered virtual assets
     * and applying path relocation rules.
     *
     * @param originalJson The original asset index JSON string.
     * @return The modified asset index JSON string.
     */
    public String patchAssetIndex(String originalJson) {
        if (originalJson == null || originalJson.trim().isEmpty()) {
            return originalJson;
        }

        try {
            LOGGER.info("Patching vanilla asset index JSON structure...");
            JsonParser parser = new JsonParser(originalJson);
            Object parsed = parser.parseValue();
            if (!(parsed instanceof Map)) {
                LOGGER.warning("Asset index JSON is not a root-level map.");
                return originalJson;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> root = (Map<String, Object>) parsed;
            Object objectsVal = root.get("objects");
            if (!(objectsVal instanceof Map)) {
                LOGGER.warning("Could not find 'objects' map in asset index JSON.");
                return originalJson;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> objectsMap = (Map<String, Object>) objectsVal;

            // 1. Inject registered virtual assets metadata
            for (Map.Entry<String, AssetMetadata> entry : virtualAssetMetadata.entrySet()) {
                String path = entry.getKey();
                AssetMetadata meta = entry.getValue();

                Map<String, Object> metaMap = new LinkedHashMap<>();
                metaMap.put("hash", meta.hash());
                metaMap.put("size", meta.size());

                objectsMap.put(path, metaMap);
                LOGGER.fine(() -> "Injected virtual asset metadata: " + path + " (hash: " + meta.hash() + ")");
            }

            // 2. Process relocations (both local registry and global AssetPathRelocator)
            // A. Relocate all existing keys in the index
            List<String> currentKeys = new ArrayList<>(objectsMap.keySet());
            for (String originalPath : currentKeys) {
                String targetPath = relocateAssetPath(originalPath);

                if (!originalPath.equals(targetPath)) {
                    Object targetMeta = null;
                    if (objectsMap.containsKey(targetPath)) {
                        targetMeta = objectsMap.get(targetPath);
                    } else if (virtualAssetMetadata.containsKey(targetPath)) {
                        AssetMetadata meta = virtualAssetMetadata.get(targetPath);
                        Map<String, Object> metaMap = new LinkedHashMap<>();
                        metaMap.put("hash", meta.hash());
                        metaMap.put("size", meta.size());
                        targetMeta = metaMap;
                    }

                    if (targetMeta != null) {
                        objectsMap.put(originalPath, targetMeta);
                        LOGGER.info(() -> String.format("Relocated asset index entry: '%s' -> '%s'", originalPath, targetPath));
                    }
                }
            }

            // B. Inject explicit local relocations that might not have been in the index keys
            for (Map.Entry<String, String> relocation : relocations.entrySet()) {
                String originalPath = relocation.getKey();
                String targetPath = relocateAssetPath(relocation.getValue());

                if (!objectsMap.containsKey(originalPath)) {
                    Object targetMeta = null;
                    if (objectsMap.containsKey(targetPath)) {
                        targetMeta = objectsMap.get(targetPath);
                    } else if (virtualAssetMetadata.containsKey(targetPath)) {
                        AssetMetadata meta = virtualAssetMetadata.get(targetPath);
                        Map<String, Object> metaMap = new LinkedHashMap<>();
                        metaMap.put("hash", meta.hash());
                        metaMap.put("size", meta.size());
                        targetMeta = metaMap;
                    }

                    if (targetMeta != null) {
                        objectsMap.put(originalPath, targetMeta);
                        LOGGER.info(() -> String.format("Injected explicit relocated asset index entry: '%s' -> '%s'", originalPath, targetPath));
                    }
                }
            }

            // 3. Serialize back to JSON string
            return JsonSerializer.serialize(root);

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to patch asset index JSON", e);
            return originalJson;
        }
    }

    /**
     * Intercepts Minecraft's stream-opening process for hash-based object files.
     * If the hash matches a registered virtual asset, it yields a {@link ByteArrayInputStream}
     * containing the in-memory bytes, preventing unnecessary disk I/O.
     *
     * @param hash             The object SHA-1 hash.
     * @param fallbackSupplier The fallback supplier returning the stream for a physical file.
     * @return The intercepted or fallback InputStream.
     * @throws IOException If opening the fallback stream fails.
     */
    public InputStream getAssetInputStream(String hash, Supplier<InputStream> fallbackSupplier) throws IOException {
        byte[] cachedBytes = getVirtualAssetContent(hash);
        if (cachedBytes != null) {
            LOGGER.info(() -> "Serving virtual asset from RAM for hash: " + hash);
            return new ByteArrayInputStream(cachedBytes);
        }
        return fallbackSupplier.get();
    }

    public Object[] createRepositorySources(Object[] originalSources) {
        populateModResources();
        if (originalSources == null) return new Object[0];
        
        List<Object> list = new ArrayList<>(Arrays.asList(originalSources));
        for (PackResources virtualPack : registeredPacks) {
            try {
                boolean alreadyExists = false;
                for (Object src : originalSources) {
                    if (java.lang.reflect.Proxy.isProxyClass(src.getClass()) 
                        && java.lang.reflect.Proxy.getInvocationHandler(src).getClass().getName().contains("VanillaAssetPatcher")) {
                        alreadyExists = true;
                        break;
                    }
                }
                if (alreadyExists) continue;

                Class<?> repositorySourceClass = null;
                try {
                    repositorySourceClass = Class.forName("net.minecraft.server.packs.repository.RepositorySource");
                } catch (ClassNotFoundException e) {
                    try {
                        repositorySourceClass = Class.forName("net.minecraft.server.packs.repository.PackProvider");
                    } catch (ClassNotFoundException ex) {
                        LOGGER.warning("Could not resolve Minecraft RepositorySource/PackProvider class.");
                    }
                }
                if (repositorySourceClass == null) continue;

                Object proxySource = java.lang.reflect.Proxy.newProxyInstance(
                    repositorySourceClass.getClassLoader(),
                    new Class<?>[]{repositorySourceClass},
                    (proxy, method, args) -> {
                        String name = method.getName();
                        if (name.equals("loadPacks") || method.getParameterTypes().length == 1) {
                            Object consumer = args[0];
                            Object packInstance = createMinecraftPack(virtualPack);
                            if (packInstance != null) {
                                Method acceptMethod = java.util.function.Consumer.class.getMethod("accept", Object.class);
                                acceptMethod.invoke(consumer, packInstance);
                            }
                            return null;
                        }
                        if (name.equals("hashCode")) {
                            return System.identityHashCode(proxy);
                        }
                        if (name.equals("equals")) {
                            return proxy == args[0];
                        }
                        if (name.equals("toString")) {
                            return "VirtualPackRepositorySource[" + virtualPack.packId() + "]";
                        }
                        return null;
                    }
                );
                list.add(proxySource);
                LOGGER.info("Created and appended virtual pack provider to repository sources: " + virtualPack.packId());
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Failed to create virtual repository source", e);
            }
        }
        Object[] result = (Object[]) java.lang.reflect.Array.newInstance(originalSources.getClass().getComponentType(), list.size());
        return list.toArray(result);
    }

    /**
     * Mock hook demonstrating how to inject custom virtual pack resources into Minecraft's
     * PackRepository at runtime using reflection.
     *
     * @param packRepository The net.minecraft.server.packs.repository.PackRepository instance.
     */
    public void injectVirtualPackToRepository(Object packRepository) {
        if (packRepository == null) return;
        LOGGER.info("Attempting to inject virtual packs into Minecraft's PackRepository...");

        for (PackResources virtualPack : registeredPacks) {
            try {
                // Find sources collection in PackRepository
                Field sourcesField = null;
                for (Field field : packRepository.getClass().getDeclaredFields()) {
                    if (Collection.class.isAssignableFrom(field.getType())) {
                        sourcesField = field;
                        break;
                    }
                }

                if (sourcesField == null) {
                    LOGGER.warning("Could not find sources collection in PackRepository.");
                    continue;
                }

                sourcesField.setAccessible(true);
                @SuppressWarnings("unchecked")
                Collection<Object> sources = (Collection<Object>) sourcesField.get(packRepository);

                if (sources == null) {
                    LOGGER.warning("PackRepository sources collection is null.");
                    continue;
                }

                // Resolve RepositorySource or PackProvider class
                Class<?> repositorySourceClass = null;
                try {
                    repositorySourceClass = Class.forName("net.minecraft.server.packs.repository.RepositorySource");
                } catch (ClassNotFoundException e) {
                    try {
                        repositorySourceClass = Class.forName("net.minecraft.server.packs.repository.PackProvider");
                    } catch (ClassNotFoundException ex) {
                        LOGGER.warning("Could not resolve Minecraft RepositorySource/PackProvider class.");
                    }
                }

                if (repositorySourceClass == null) continue;

                // Create a Proxy implementing RepositorySource
                Object proxySource = java.lang.reflect.Proxy.newProxyInstance(
                    repositorySourceClass.getClassLoader(),
                    new Class<?>[]{repositorySourceClass},
                    (proxy, method, args) -> {
                        String name = method.getName();
                        if (name.equals("loadPacks") || method.getParameterTypes().length == 1) {
                            Object consumer = args[0];
                            Object packInstance = createMinecraftPack(virtualPack);
                            if (packInstance != null) {
                                Method acceptMethod = java.util.function.Consumer.class.getMethod("accept", Object.class);
                                acceptMethod.invoke(consumer, packInstance);
                            }
                            return null;
                        }
                        if (name.equals("hashCode")) {
                            return System.identityHashCode(proxy);
                        }
                        if (name.equals("equals")) {
                            return proxy == args[0];
                        }
                        if (name.equals("toString")) {
                            return "VirtualPackRepositorySource[" + virtualPack.packId() + "]";
                        }
                        return null;
                    }
                );

                synchronized (sources) {
                    // Check if already injected
                    boolean alreadyInjected = false;
                    for (Object src : sources) {
                        if (java.lang.reflect.Proxy.isProxyClass(src.getClass()) 
                            && java.lang.reflect.Proxy.getInvocationHandler(src).getClass().getName().contains("VanillaAssetPatcher")) {
                            alreadyInjected = true;
                            break;
                        }
                    }
                    if (!alreadyInjected) {
                        try {
                            sources.add(proxySource);
                            LOGGER.info("Injected virtual pack provider for: " + virtualPack.packId());
                        } catch (UnsupportedOperationException e) {
                            // Read-only collection fallback: replace it
                            if (sources instanceof Set) {
                                Set<Object> newSources = new HashSet<>(sources);
                                newSources.add(proxySource);
                                sourcesField.set(packRepository, Collections.unmodifiableSet(newSources));
                            } else {
                                List<Object> newSources = new ArrayList<>(sources);
                                newSources.add(proxySource);
                                sourcesField.set(packRepository, Collections.unmodifiableList(newSources));
                            }
                            LOGGER.info("Injected virtual pack provider via replacement for: " + virtualPack.packId());
                        }
                    }
                }
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Failed to inject virtual pack resources into repository", e);
            }
        }
    }

    private Object createMinecraftPack(PackResources virtualPack) {
        try {
            // Class name mappings:
            // net.minecraft.server.packs.repository.Pack -> atm
            // net.minecraft.server.packs.PackLocationInfo -> asp
            // net.minecraft.server.packs.repository.Pack$ResourcesSupplier -> atm$c
            // net.minecraft.server.packs.PackType -> ass
            // net.minecraft.server.packs.repository.PackSelectionConfig -> asr
            // net.minecraft.network.chat.Component -> wz
            // net.minecraft.server.packs.repository.PackSource -> atq
            // net.minecraft.server.packs.repository.Pack$Position -> atm$b
            
            Class<?> packClass = Class.forName("atm");
            Class<?> locationInfoClass = Class.forName("asp");
            Class<?> resourcesSupplierClass = Class.forName("atm$c");
            Class<?> packTypeClass = Class.forName("ass");
            Class<?> selectionConfigClass = Class.forName("asr");
            Class<?> componentClass = Class.forName("wz");
            Class<?> packSourceClass = Class.forName("atq");
            Class<?> positionClass = Class.forName("atm$b");
            
            // 1. Create Component for the title: Component.literal("ChainLoader Mod Resources")
            Method literalMethod = null;
            for (Method m : componentClass.getDeclaredMethods()) {
                if (m.getName().equals("literal") || (m.getParameterTypes().length == 1 && m.getParameterTypes()[0] == String.class && m.getReturnType() == componentClass)) {
                    literalMethod = m;
                    break;
                }
            }
            Object titleComponent;
            if (literalMethod != null) {
                titleComponent = literalMethod.invoke(null, "ChainLoader Mod Resources");
            } else {
                Method bMethod = componentClass.getMethod("b", String.class);
                titleComponent = bMethod.invoke(null, "ChainLoader Mod Resources");
            }
            
            // 2. Get PackSource.DEFAULT (field b in atq)
            Field defaultSourceField = packSourceClass.getDeclaredField("b");
            defaultSourceField.setAccessible(true);
            Object defaultSource = defaultSourceField.get(null);
            
            // 3. Create PackLocationInfo
            java.lang.reflect.Constructor<?> locConstructor = locationInfoClass.getConstructor(
                String.class, componentClass, packSourceClass, java.util.Optional.class
            );
            Object locationInfo = locConstructor.newInstance(
                "chainloader-mod-resources", titleComponent, defaultSource, java.util.Optional.empty()
            );
            
            // 4. Create Pack$ResourcesSupplier using BuiltInPackSource.fixedResources(PackResources)
            Class<?> builtInSourceClass = Class.forName("atj");
            Method fixedResourcesMethod = null;
            for (Method m : builtInSourceClass.getDeclaredMethods()) {
                if (m.getName().equals("b") && m.getParameterTypes().length == 1 && m.getParameterTypes()[0].isAssignableFrom(virtualPack.getClass())) {
                    fixedResourcesMethod = m;
                    break;
                }
            }
            if (fixedResourcesMethod == null) {
                fixedResourcesMethod = builtInSourceClass.getDeclaredMethod("b", Class.forName("asq"));
            }
            fixedResourcesMethod.setAccessible(true);
            Object resourcesSupplier = fixedResourcesMethod.invoke(null, virtualPack);
            
            // 5. Get PackType.CLIENT_RESOURCES (field b in ass)
            Field clientResourcesField = packTypeClass.getDeclaredField("b");
            clientResourcesField.setAccessible(true);
            Object clientResources = clientResourcesField.get(null);
            
            // 6. Create PackSelectionConfig
            Field topPositionField = positionClass.getDeclaredField("a");
            topPositionField.setAccessible(true);
            Object topPosition = topPositionField.get(null);
            
            java.lang.reflect.Constructor<?> selectionConstructor = selectionConfigClass.getConstructor(
                boolean.class, positionClass, boolean.class
            );
            Object selectionConfig = selectionConstructor.newInstance(
                true, topPosition, false
            );
            
            // 7. Create Pack using Pack.readMetaAndCreate
            Method readMetaMethod = null;
            for (Method m : packClass.getDeclaredMethods()) {
                if (m.getName().equals("a") && m.getParameterTypes().length == 4 
                    && m.getParameterTypes()[0] == locationInfoClass 
                    && m.getParameterTypes()[1] == resourcesSupplierClass
                    && m.getParameterTypes()[2] == packTypeClass
                    && m.getParameterTypes()[3] == selectionConfigClass) {
                    readMetaMethod = m;
                    break;
                }
            }
            
            if (readMetaMethod != null) {
                readMetaMethod.setAccessible(true);
                Object pack = readMetaMethod.invoke(null, locationInfo, resourcesSupplier, clientResources, selectionConfig);
                LOGGER.info("Successfully created Minecraft Pack object for: " + virtualPack.packId());
                return pack;
            } else {
                LOGGER.severe("Could not find readMetaAndCreate method on Pack class.");
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to instantiate Minecraft Pack wrapper for virtual pack", e);
        }
        return null;
    }

    private String computeSha1(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] hashBytes = digest.digest(bytes);
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to compute SHA-1 hash", e);
            return "";
        }
    }

    // --- AssetMetadata definition ---
    public record AssetMetadata(String hash, long size) {
        public AssetMetadata {
            Objects.requireNonNull(hash, "hash cannot be null");
        }
    }

    // --- Clean and robust self-contained JSON Parser ---
    private static class JsonParser {
        private final String src;
        private int cursor = 0;

        public JsonParser(String src) {
            this.src = src;
        }

        public Object parseValue() {
            skipWhitespace();
            if (cursor >= src.length()) return null;
            char c = src.charAt(cursor);
            if (c == '{') {
                return parseObject();
            } else if (c == '[') {
                return parseArray();
            } else if (c == '"') {
                return parseString();
            } else if (Character.isDigit(c) || c == '-') {
                return parseNumber();
            } else if (src.startsWith("true", cursor)) {
                cursor += 4;
                return Boolean.TRUE;
            } else if (src.startsWith("false", cursor)) {
                cursor += 5;
                return Boolean.FALSE;
            } else if (src.startsWith("null", cursor)) {
                cursor += 4;
                return null;
            }
            throw new RuntimeException("Unexpected character at " + cursor + ": " + c);
        }

        private Map<String, Object> parseObject() {
            cursor++; // skip '{'
            Map<String, Object> map = new LinkedHashMap<>();
            while (true) {
                skipWhitespace();
                if (cursor >= src.length()) throw new RuntimeException("Unterminated JSON object");
                if (src.charAt(cursor) == '}') {
                    cursor++;
                    break;
                }
                if (src.charAt(cursor) == ',') {
                    cursor++;
                    continue;
                }
                String key = parseString();
                skipWhitespace();
                if (cursor >= src.length() || src.charAt(cursor) != ':') {
                    throw new RuntimeException("Expected ':' at character position " + cursor);
                }
                cursor++; // skip ':'
                Object val = parseValue();
                map.put(key, val);
            }
            return map;
        }

        private List<Object> parseArray() {
            cursor++; // skip '['
            List<Object> list = new ArrayList<>();
            while (true) {
                skipWhitespace();
                if (cursor >= src.length()) throw new RuntimeException("Unterminated JSON array");
                if (src.charAt(cursor) == ']') {
                    cursor++;
                    break;
                }
                if (src.charAt(cursor) == ',') {
                    cursor++;
                    continue;
                }
                Object val = parseValue();
                list.add(val);
            }
            return list;
        }

        private String parseString() {
            cursor++; // skip opening '"'
            StringBuilder sb = new StringBuilder();
            while (cursor < src.length()) {
                char c = src.charAt(cursor);
                if (c == '"') {
                    cursor++;
                    return sb.toString();
                } else if (c == '\\') {
                    cursor++;
                    if (cursor >= src.length()) throw new RuntimeException("Unterminated JSON escape sequence");
                    char escaped = src.charAt(cursor);
                    if (escaped == 'n') sb.append('\n');
                    else if (escaped == 'r') sb.append('\r');
                    else if (escaped == 't') sb.append('\t');
                    else if (escaped == '"') sb.append('"');
                    else if (escaped == '\\') sb.append('\\');
                    else sb.append(escaped);
                } else {
                    sb.append(c);
                }
                cursor++;
            }
            throw new RuntimeException("Unterminated JSON string");
        }

        private Number parseNumber() {
            int start = cursor;
            if (src.charAt(cursor) == '-') cursor++;
            boolean isDouble = false;
            while (cursor < src.length()) {
                char c = src.charAt(cursor);
                if (Character.isDigit(c)) {
                    cursor++;
                } else if (c == '.' || c == 'e' || c == 'E') {
                    isDouble = true;
                    cursor++;
                } else {
                    break;
                }
            }
            String numStr = src.substring(start, cursor);
            if (isDouble) return Double.parseDouble(numStr);
            return Long.parseLong(numStr);
        }

        private void skipWhitespace() {
            while (cursor < src.length()) {
                char c = src.charAt(cursor);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                    cursor++;
                } else {
                    break;
                }
            }
        }
    }

    // --- Clean and robust self-contained JSON Serializer ---
    private static class JsonSerializer {
        public static String serialize(Object obj) {
            StringBuilder sb = new StringBuilder();
            serializeValue(obj, sb, 0);
            return sb.toString();
        }

        private static void serializeValue(Object obj, StringBuilder sb, int indent) {
            if (obj == null) {
                sb.append("null");
            } else if (obj instanceof String) {
                sb.append("\"").append(escapeString((String) obj)).append("\"");
            } else if (obj instanceof Number || obj instanceof Boolean) {
                sb.append(obj);
            } else if (obj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) obj;
                sb.append("{\n");
                int nextIndent = indent + 2;
                int i = 0;
                for (Map.Entry<String, Object> entry : map.entrySet()) {
                    indent(sb, nextIndent);
                    sb.append("\"").append(escapeString(entry.getKey())).append("\": ");
                    serializeValue(entry.getValue(), sb, nextIndent);
                    if (++i < map.size()) {
                        sb.append(",");
                    }
                    sb.append("\n");
                }
                indent(sb, indent);
                sb.append("}");
            } else if (obj instanceof List) {
                List<?> list = (List<?>) obj;
                sb.append("[\n");
                int nextIndent = indent + 2;
                for (int i = 0; i < list.size(); i++) {
                    indent(sb, nextIndent);
                    serializeValue(list.get(i), sb, nextIndent);
                    if (i < list.size() - 1) {
                        sb.append(",");
                    }
                    sb.append("\n");
                }
                indent(sb, indent);
                sb.append("]");
            } else {
                sb.append("\"").append(escapeString(obj.toString())).append("\"");
            }
        }

        private static void indent(StringBuilder sb, int count) {
            for (int i = 0; i < count; i++) {
                sb.append(' ');
            }
        }

        private static String escapeString(String s) {
            return s.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
        }
    }

    /**
     * Demonstrates and tests the asset index patching and path relocation functionality.
     */
    public static void main(String[] args) {
        VanillaAssetPatcher patcher = VanillaAssetPatcher.getInstance();

        System.out.println("=== Testing VanillaAssetPatcher ===");

        // 1. Register virtual assets (e.g. blockstates, textures)
        String mockCustomModel = "{\n  \"parent\": \"block/cube_all\",\n  \"textures\": {\n    \"all\": \"chainloader:block/custom_stone\"\n  }\n}";
        patcher.registerVirtualAsset("chainloader/models/block/custom_stone.json", mockCustomModel.getBytes(StandardCharsets.UTF_8));
        
        // 2. Register path relocation (re-mapping vanilla stone blockstate to our custom model)
        patcher.registerRelocation("minecraft/models/block/stone.json", "chainloader/models/block/custom_stone.json");

        // 3. Mock original vanilla assets index JSON
        String mockVanillaIndexJson = "{\n" +
                "  \"objects\": {\n" +
                "    \"minecraft/models/block/stone.json\": {\n" +
                "      \"hash\": \"8867a57a3e3579b29cbdfc63e23d778fb8488e00\",\n" +
                "      \"size\": 114\n" +
                "    },\n" +
                "    \"minecraft/sounds/ambient/cave/cave1.ogg\": {\n" +
                "      \"hash\": \"399e211bb37e7df487e411b333cfc8adca3a2d59\",\n" +
                "      \"size\": 17822\n" +
                "    }\n" +
                "  }\n" +
                "}";

        System.out.println("\n--- Original Assets Index JSON ---");
        System.out.println(mockVanillaIndexJson);

        // 4. Run the patcher
        String patchedJson = patcher.patchAssetIndex(mockVanillaIndexJson);

        System.out.println("\n--- Patched Assets Index JSON ---");
        System.out.println(patchedJson);
        System.out.println("==================================");
    }

    private boolean populated = false;

    public synchronized void populateModResources() {
        if (populated) {
            LOGGER.info("Mod resources already populated. Skipping duplicate population.");
            return;
        }
        populated = true;
        LOGGER.info("Starting population of resources from discovered mod JARs...");
        VirtualAssetPack modPack = new VirtualAssetPack("chainloader-mod-resources", false);
        
        try {
            List<net.chainloader.loader.core.ModScanner.DiscoveredMod> discoveredMods = 
                net.chainloader.loader.core.ModScanner.getDiscoveredMods();
            
            for (net.chainloader.loader.core.ModScanner.DiscoveredMod mod : discoveredMods) {
                if (mod.jarFile == null) {
                    continue;
                }
                
                LOGGER.info("Scanning mod JAR for resources: " + mod.jarFile.getName());
                try (java.util.zip.ZipFile zipFile = new java.util.zip.ZipFile(mod.jarFile)) {
                    Enumeration<? extends java.util.zip.ZipEntry> entries = zipFile.entries();
                    while (entries.hasMoreElements()) {
                        java.util.zip.ZipEntry entry = entries.nextElement();
                        String name = entry.getName();
                        
                        if (entry.isDirectory()) {
                            continue;
                        }
                        
                        String relocatedName = net.chainloader.loader.compat.asset.AssetPathRelocator.getInstance().relocateString(name);
                        
                        // Parse assets/namespace/path or data/namespace/path
                        if (relocatedName.startsWith("assets/") && relocatedName.length() > 7) {
                            String sub = relocatedName.substring(7);
                            int firstSlash = sub.indexOf('/');
                            if (firstSlash > 0) {
                                String namespace = sub.substring(0, firstSlash);
                                String path = sub.substring(firstSlash + 1);
                                net.minecraft.resources.ResourceLocation loc = new net.minecraft.resources.ResourceLocation(namespace, path);
                                
                                // Register using lazy stream supplier to avoid high RAM usage
                                modPack.registerBytes(net.minecraft.server.packs.PackType.CLIENT_RESOURCES, loc, 
                                    createJarResourceSupplier(mod.jarFile, name));
                                LOGGER.fine(() -> "Registered CLIENT_RESOURCE from JAR: " + loc);
                            }
                        } else if (relocatedName.startsWith("data/") && relocatedName.length() > 5) {
                            String sub = relocatedName.substring(5);
                            int firstSlash = sub.indexOf('/');
                            if (firstSlash > 0) {
                                String namespace = sub.substring(0, firstSlash);
                                String path = sub.substring(firstSlash + 1);
                                net.minecraft.resources.ResourceLocation loc = new net.minecraft.resources.ResourceLocation(namespace, path);
                                
                                modPack.registerBytes(net.minecraft.server.packs.PackType.SERVER_DATA, loc, 
                                    createJarResourceSupplier(mod.jarFile, name));
                                LOGGER.fine(() -> "Registered SERVER_DATA from JAR: " + loc);
                            }
                        }
                    }
                } catch (IOException e) {
                    LOGGER.log(Level.SEVERE, "Failed to scan mod JAR: " + mod.jarFile, e);
                }
            }
            
            // Register and sync this virtual pack to VanillaAssetPatcher
            this.syncWithPackResources(modPack);
            
        } catch (Throwable t) {
            LOGGER.log(Level.SEVERE, "Failed to populate mod resources from JARs", t);
        }
    }

    private Supplier<byte[]> createJarResourceSupplier(File jarFile, String entryName) {
        return () -> {
            try (java.util.zip.ZipFile zipFile = new java.util.zip.ZipFile(jarFile)) {
                java.util.zip.ZipEntry entry = zipFile.getEntry(entryName);
                if (entry != null) {
                    try (InputStream is = zipFile.getInputStream(entry);
                         ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                        byte[] buffer = new byte[4096];
                        int read;
                        while ((read = is.read(buffer)) != -1) {
                            baos.write(buffer, 0, read);
                        }
                        return baos.toByteArray();
                    }
                }
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Failed to read lazy resource " + entryName + " from JAR " + jarFile, e);
            }
            return new byte[0];
        };
    }

    private void syncVanillaOverrides(VirtualAssetPack pack) {
        try {
            Field resourcesField = pack.getClass().getDeclaredField("resources");
            resourcesField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<?, Map<?, Supplier<byte[]>>> resources = (Map<?, Map<?, Supplier<byte[]>>>) resourcesField.get(pack);

            for (Map.Entry<?, Map<?, Supplier<byte[]>>> entry : resources.entrySet()) {
                Enum<?> packType = (Enum<?>) entry.getKey();
                if ("CLIENT_RESOURCES".equals(packType.name())) {
                    Map<?, Supplier<byte[]>> clientMap = entry.getValue();
                    for (Map.Entry<?, Supplier<byte[]>> resourceEntry : clientMap.entrySet()) {
                        Object resourceLocation = resourceEntry.getKey();
                        Supplier<byte[]> contentSupplier = resourceEntry.getValue();

                        if (resourceLocation != null && contentSupplier != null) {
                            net.minecraft.resources.ResourceLocation loc = (net.minecraft.resources.ResourceLocation) resourceLocation;
                            String namespace = loc.getNamespace();
                            String path = loc.getPath();

                            if ("minecraft".equals(namespace) || path.endsWith(".json")) {
                                String assetPath = namespace + "/" + path;
                                byte[] content = contentSupplier.get();
                                if (content != null) {
                                    registerVirtualAsset(assetPath, content);
                                    LOGGER.info("Synced vanilla override asset: " + assetPath);
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to sync vanilla override assets", e);
        }
    }

    /**
     * Intercepts reading of the vanilla asset index JSON file, patches it dynamically,
     * and returns a BufferedReader reading from the patched JSON string in memory.
     */
    public static BufferedReader patchBufferedReader(java.nio.file.Path path, java.nio.charset.Charset cs) throws IOException {
        LOGGER.info("Intercepting asset index BufferedReader for: " + path.toAbsolutePath());
        String originalJson;
        try {
            originalJson = java.nio.file.Files.readString(path, cs);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to read original asset index JSON file", e);
            return java.nio.file.Files.newBufferedReader(path, cs);
        }

        String patchedJson = getInstance().patchAssetIndex(originalJson);
        return new BufferedReader(new StringReader(patchedJson));
    }
}
