package net.chainloader.loader.compat.asset;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.server.packs.resources.IoSupplier;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

/**
 * An in-memory virtual PackResources implementation that generates, stores,
 * and dynamically relocates or patches JSON assets (like tags and blockstates) in RAM.
 *
 * This class coordinates with path relocators and index patchers to modify resources
 * dynamically on-the-fly, allowing fabric-to-forge compatibility layers to redirect
 * resource locations and patch tag indices without writing to disk.
 */
public class VirtualAssetPack implements PackResources {

    private final String packId;
    private final boolean isBuiltin;
    private final net.minecraft.server.packs.PackLocationInfo locationInfo;

    // Thread-safe in-memory storage for resources per PackType
    private final Map<PackType, Map<ResourceLocation, Supplier<byte[]>>> resources = new ConcurrentHashMap<>();
    private final Map<PackType, Set<String>> namespaces = new ConcurrentHashMap<>();

    // Registered relocators and patchers for coordination
    private final List<PathRelocator> pathRelocators = new CopyOnWriteArrayList<>();
    private final List<IndexPatcher> indexPatchers = new CopyOnWriteArrayList<>();

    public VirtualAssetPack(String packId, boolean isBuiltin) {
        this.packId = packId;
        this.isBuiltin = isBuiltin;
        this.locationInfo = new net.minecraft.server.packs.PackLocationInfo(
            packId,
            Component.literal(packId),
            net.minecraft.server.packs.repository.PackSource.BUILT_IN,
            Optional.empty()
        );

        // Initialize maps for all PackTypes
        for (PackType type : PackType.values()) {
            this.resources.put(type, new ConcurrentHashMap<>());
            this.namespaces.put(type, ConcurrentHashMap.newKeySet());
        }

        // Add default mock data and shims for demonstration/coordination
        prepopulateMockData();
    }

    /**
     * Interface for transforming/relocating asset paths on-the-fly.
     * Useful when loader shims redirect requests from legacy fabric mods to VirtualAssetPack namespace paths.
     */
    @FunctionalInterface
    public interface PathRelocator {
        /**
         * Relocates a requested resource location to a new target resource location.
         *
         * @param type     The pack type (CLIENT_RESOURCES or SERVER_DATA)
         * @param location The requested resource location
         * @return The relocated resource location, or the original location if no relocation is needed
         */
        ResourceLocation relocate(PackType type, ResourceLocation location);
    }

    /**
     * Interface for dynamically patching/editing JSON content of resources stored in RAM.
     * Useful for merging blockstate variants or tags across multiple virtual or loaded assets.
     */
    @FunctionalInterface
    public interface IndexPatcher {
        /**
         * Patches the string representation of a JSON resource.
         *
         * @param location The relocated resource location being requested
         * @param rawJson  The raw registered JSON content
         * @return The patched JSON content
         */
        String patch(ResourceLocation location, String rawJson);
    }

    /**
     * Registers a path relocator for on-the-fly resource redirection.
     */
    public void registerPathRelocator(PathRelocator relocator) {
        if (relocator != null) {
            this.pathRelocators.add(relocator);
        }
    }

    /**
     * Registers an index patcher for dynamic JSON manipulation.
     */
    public void registerIndexPatcher(IndexPatcher patcher) {
        if (patcher != null) {
            this.indexPatchers.add(patcher);
        }
    }

    /**
     * Registers a raw dynamic resource to the in-memory engine.
     */
    public void register(PackType type, ResourceLocation location, Supplier<String> contentSupplier) {
        this.resources.get(type).put(location, () -> contentSupplier.get().getBytes(StandardCharsets.UTF_8));
        this.namespaces.get(type).add(location.getNamespace());
    }

    /**
     * Registers a raw byte supplier to the in-memory engine.
     */
    public void registerBytes(PackType type, ResourceLocation location, Supplier<byte[]> contentSupplier) {
        System.out.println("[ChainLoader DEBUG] registerBytes on pack " + this.packId + "@" + System.identityHashCode(this) + ": type=" + type + " (class: " + type.getClass().getName() + "), location=" + location);
        this.resources.get(type).put(location, contentSupplier);
        this.namespaces.get(type).add(location.getNamespace());
        System.out.println("[ChainLoader DEBUG]   Map size for " + type + " is now " + this.resources.get(type).size());
    }

    /**
     * Registers a JSON blockstate asset under the CLIENT_RESOURCES pack type.
     */
    public void registerBlockstate(String namespace, String blockName, String json) {
        ResourceLocation location = new ResourceLocation(namespace, "blockstates/" + blockName + ".json");
        register(PackType.CLIENT_RESOURCES, location, () -> json);
    }

    /**
     * Registers a JSON tag data asset under the SERVER_DATA pack type.
     */
    public void registerTag(String namespace, String registry, String tagName, String json) {
        ResourceLocation location = new ResourceLocation(namespace, "tags/" + registry + "/" + tagName + ".json");
        register(PackType.SERVER_DATA, location, () -> json);
    }

    @Nullable
    @Override
    public IoSupplier<InputStream> getRootResource(String... paths) {
        String pathStr = String.join("/", paths);
        if ("pack.mcmeta".equals(pathStr)) {
            String mockMcmeta = "{\n" +
                    "  \"pack\": {\n" +
                    "    \"description\": \"ChainLoader Virtual Compatibility Asset Pack\",\n" +
                    "    \"pack_format\": 34\n" +
                    "  }\n" +
                    "}";
            return () -> new ByteArrayInputStream(mockMcmeta.getBytes(StandardCharsets.UTF_8));
        }
        return null;
    }

    @Nullable
    @Override
    public IoSupplier<InputStream> getResource(PackType type, ResourceLocation location) {
        // Step 1: Apply path relocation coordination
        ResourceLocation relocatedLocation = location;
        for (PathRelocator relocator : pathRelocators) {
            relocatedLocation = relocator.relocate(type, relocatedLocation);
        }

        Map<ResourceLocation, Supplier<byte[]>> typeMap = this.resources.get(type);
        if (typeMap == null) return null;

        Supplier<byte[]> supplier = typeMap.get(relocatedLocation);
        if (supplier == null && (relocatedLocation.getNamespace().equals("waystones") || relocatedLocation.getNamespace().equals("naturescompass"))) {
            System.out.println("[ChainLoader DEBUG] getResource not found for: " + relocatedLocation + " (class: " + relocatedLocation.getClass().getName() + ", hash: " + relocatedLocation.hashCode() + ")");
            for (ResourceLocation key : typeMap.keySet()) {
                if (key.getPath().contains("attuned_shard") || key.getPath().contains("naturescompass")) {
                    System.out.println("  Map contains key: " + key + " (class: " + key.getClass().getName() + ", hash: " + key.hashCode() + ", equals: " + key.equals(relocatedLocation) + ")");
                }
            }
        }
        if (supplier != null) {
            final ResourceLocation finalLocation = relocatedLocation;
            return () -> {
                byte[] rawBytes = supplier.get();
                // Step 2: Apply index patching coordination if the resource is JSON
                if (finalLocation.getPath().endsWith(".json")) {
                    String json = new String(rawBytes, StandardCharsets.UTF_8);
                    for (IndexPatcher patcher : indexPatchers) {
                        json = patcher.patch(finalLocation, json);
                    }
                    return new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
                }
                return new ByteArrayInputStream(rawBytes);
            };
        }

        return null;
    }

    @Override
    public void listResources(PackType type, String namespace, String path, PackResources.ResourceOutput resourceOutput) {
        Map<ResourceLocation, Supplier<byte[]>> typeMap = this.resources.get(type);
        if (typeMap == null) return;

        for (Map.Entry<ResourceLocation, Supplier<byte[]>> entry : typeMap.entrySet()) {
            ResourceLocation originalLocation = entry.getKey();

            // Relocate resource path to see if it matches requested namespace/path
            ResourceLocation relocatedLocation = originalLocation;
            for (PathRelocator relocator : pathRelocators) {
                relocatedLocation = relocator.relocate(type, relocatedLocation);
            }

            if (relocatedLocation.getNamespace().equals(namespace) && relocatedLocation.getPath().startsWith(path)) {
                final ResourceLocation finalLocation = relocatedLocation;
                final Supplier<byte[]> originalSupplier = entry.getValue();

                resourceOutput.accept(relocatedLocation, () -> {
                    byte[] rawBytes = originalSupplier.get();
                    if (finalLocation.getPath().endsWith(".json")) {
                        String json = new String(rawBytes, StandardCharsets.UTF_8);
                        for (IndexPatcher patcher : indexPatchers) {
                            json = patcher.patch(finalLocation, json);
                        }
                        return new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
                    }
                    return new ByteArrayInputStream(rawBytes);
                });
            }
        }
    }

    @Override
    public Set<String> getNamespaces(PackType type) {
        System.out.println("[ChainLoader DEBUG] getNamespaces called on pack " + this.packId + "@" + System.identityHashCode(this) + " for type=" + type + " (class: " + type.getClass().getName() + ")");
        System.out.println("[ChainLoader DEBUG] this.resources keys: " + this.resources.keySet());
        for (Map.Entry<PackType, Map<ResourceLocation, Supplier<byte[]>>> entry : this.resources.entrySet()) {
             System.out.println("  Map Key: " + entry.getKey() + " (class: " + entry.getKey().getClass().getName() + "), value size: " + entry.getValue().size());
        }
        // Include both registered namespaces and any namespace produced by relocators
        Set<String> allNamespaces = ConcurrentHashMap.newKeySet();
        allNamespaces.addAll(this.namespaces.getOrDefault(type, Collections.emptySet()));

        // Also query relocators to see if they introduce namespaces dynamically
        Map<ResourceLocation, Supplier<byte[]>> typeMap = this.resources.get(type);
        if (typeMap != null) {
            for (ResourceLocation original : typeMap.keySet()) {
                ResourceLocation relocated = original;
                for (PathRelocator relocator : pathRelocators) {
                    relocated = relocator.relocate(type, relocated);
                }
                allNamespaces.add(relocated.getNamespace());
            }
        }
        System.out.println("[ChainLoader DEBUG] getNamespaces returning for type " + type + ": " + allNamespaces);
        return allNamespaces;
    }

    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T> T getMetadataSection(MetadataSectionSerializer<T> serializer) throws IOException {
        if ("pack".equals(serializer.getMetadataSectionName())) {
            return (T) new PackMetadataSection(Component.literal("ChainLoader Virtual Compatibility Asset Pack"), 34, Optional.empty());
        }
        return null;
    }

    @Override
    public String packId() {
        return this.packId;
    }

    @Override
    public boolean isBuiltin() {
        return this.isBuiltin;
    }

    @Override
    public net.minecraft.server.packs.PackLocationInfo location() {
        return this.locationInfo;
    }

    @Override
    public void close() {
        // No-op to preserve in-memory assets across resource manager reloads
    }

    /**
     * Pre-populates mock data to demonstrate tag/blockstate streams serving
     * and coordination with path relocator and index patcher shims.
     */
    private void prepopulateMockData() {
        // Register mock blockstate in chainloader namespace
        String mockBlockstate = "{\n" +
                "  \"variants\": {\n" +
                "    \"\": {\n" +
                "      \"model\": \"chainloader:block/virtual_block\"\n" +
                "    }\n" +
                "  }\n" +
                "}";
        registerBlockstate("chainloader", "virtual_block", mockBlockstate);

        // Register mock tag in minecraft namespace
        String mockBlockTag = "{\n" +
                "  \"replace\": false,\n" +
                "  \"values\": [\n" +
                "    \"chainloader:virtual_block\"\n" +
                "  ]\n" +
                "}";
        registerTag("minecraft", "blocks", "mineable/pickaxe", mockBlockTag);

        // Register a mock path relocator to show relocation coordination.
        // It redirects requests for "legacy_mod:virtual_block" to "chainloader:virtual_block".
        registerPathRelocator((type, location) -> {
            if ("legacy_mod".equals(location.getNamespace()) && location.getPath().contains("virtual_block")) {
                String newPath = location.getPath().replace("legacy_mod", "chainloader");
                return new ResourceLocation("chainloader", newPath);
            }
            return location;
        });

        // Register a mock index patcher to show patching coordination.
        // It injects a "patched" property or tag value into blockstates or tags dynamically.
        registerIndexPatcher((location, rawJson) -> {
            if (location.getPath().contains("blockstates/virtual_block.json")) {
                // Dynamically add a compatibility flag or state variant
                return rawJson.replace("\"model\": \"chainloader:block/virtual_block\"",
                        "\"model\": \"chainloader:block/virtual_block\",\n      \"compat_patched\": true");
            }
            if (location.getPath().contains("tags/blocks/mineable/pickaxe.json")) {
                // Dynamically inject an extra block into the pickaxe mineable list
                return rawJson.replace("\"chainloader:virtual_block\"",
                        "\"chainloader:virtual_block\",\n    \"chainloader:patched_virtual_block\"");
            }
            return rawJson;
        });
    }
}
