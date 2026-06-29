package net.chainloader.loader.compat.pack;

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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * An in-memory virtual pack resource engine.
 * Storing assets and data in RAM and serving them as streams on-demand prevents unnecessary disk writing.
 * This class implements Minecraft's {@link PackResources} interface to be fully compatible with the resource/datapack managers.
 */
public class VirtualPackResources implements PackResources {

    private final String packId;
    private final boolean isBuiltin;
    private final net.minecraft.server.packs.PackLocationInfo locationInfo;
    
    // In-memory maps storing pack type and resource locations mapped to dynamically generated content
    private final Map<PackType, Map<ResourceLocation, Supplier<byte[]>>> resources = new HashMap<>();
    
    // Track namespaces registered per pack type
    private final Map<PackType, Set<String>> namespaces = new HashMap<>();

    public VirtualPackResources(String packId, boolean isBuiltin) {
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
            this.resources.put(type, new HashMap<>());
            this.namespaces.put(type, new HashSet<>());
        }
        
        // Register some default mocked tag and blockstate assets
        prepopulateMockData();
    }

    /**
     * Registers a raw dynamic resource to the in-memory engine.
     *
     * @param type            The pack type (CLIENT_RESOURCES for assets, SERVER_DATA for datapack data)
     * @param location        The resource location (e.g. namespace:blockstates/test_block.json)
     * @param contentSupplier The supplier providing the JSON string
     */
    public void register(PackType type, ResourceLocation location, Supplier<String> contentSupplier) {
        this.resources.get(type).put(location, () -> contentSupplier.get().getBytes(StandardCharsets.UTF_8));
        this.namespaces.get(type).add(location.getNamespace());
    }

    /**
     * Registers a JSON blockstate asset under the CLIENT_RESOURCES pack type.
     *
     * @param namespace The mod/asset namespace (e.g., "chainloader")
     * @param blockName The name of the block (e.g., "virtual_block")
     * @param json      The JSON content representing the blockstate
     */
    public void registerBlockstate(String namespace, String blockName, String json) {
        ResourceLocation location = new ResourceLocation(namespace, "blockstates/" + blockName + ".json");
        register(PackType.CLIENT_RESOURCES, location, () -> json);
    }

    /**
     * Registers a JSON tag data asset under the SERVER_DATA pack type.
     *
     * @param namespace The data namespace (e.g., "minecraft")
     * @param registry  The registry path (e.g., "blocks")
     * @param tagName   The tag name (e.g., "mineable/pickaxe")
     * @param json      The JSON content representing the tag values
     */
    public void registerTag(String namespace, String registry, String tagName, String json) {
        ResourceLocation location = new ResourceLocation(namespace, "tags/" + registry + "/" + tagName + ".json");
        register(PackType.SERVER_DATA, location, () -> json);
    }

    @Nullable
    @Override
    public IoSupplier<InputStream> getRootResource(String... paths) {
        // Root resources like pack.mcmeta are typically located at the root.
        // We serve a virtual pack.mcmeta dynamically.
        String pathStr = String.join("/", paths);
        if ("pack.mcmeta".equals(pathStr)) {
            String mockMcmeta = "{\n" +
                    "  \"pack\": {\n" +
                    "    \"description\": \"ChainLoader Virtual Compatibility Pack\",\n" +
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
        Map<ResourceLocation, Supplier<byte[]>> typeMap = this.resources.get(type);
        if (typeMap == null) return null;
        
        Supplier<byte[]> supplier = typeMap.get(location);
        if (supplier != null) {
            return () -> new ByteArrayInputStream(supplier.get());
        }
        return null;
    }

    @Override
    public void listResources(PackType type, String namespace, String path, PackResources.ResourceOutput resourceOutput) {
        Map<ResourceLocation, Supplier<byte[]>> typeMap = this.resources.get(type);
        if (typeMap == null) return;

        // We check if the registered resource location's path starts with the requested path prefix.
        for (Map.Entry<ResourceLocation, Supplier<byte[]>> entry : typeMap.entrySet()) {
            ResourceLocation loc = entry.getKey();
            if (loc.getNamespace().equals(namespace) && loc.getPath().startsWith(path)) {
                Supplier<byte[]> contentSupplier = entry.getValue();
                resourceOutput.accept(loc, () -> new ByteArrayInputStream(contentSupplier.get()));
            }
        }
    }

    @Override
    public Set<String> getNamespaces(PackType type) {
        return this.namespaces.getOrDefault(type, Collections.emptySet());
    }

    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T> T getMetadataSection(MetadataSectionSerializer<T> serializer) throws IOException {
        if ("pack".equals(serializer.getMetadataSectionName())) {
            return (T) new PackMetadataSection(Component.literal("ChainLoader Virtual Compatibility Pack"), 34, Optional.empty());
        }
        return null;
    }

    @Override
    public String packId() {
        return this.packId;
    }

    public String getName() {
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
        // Clear maps to assist GC
        for (Map<ResourceLocation, Supplier<byte[]>> map : this.resources.values()) {
            map.clear();
        }
        for (Set<String> set : this.namespaces.values()) {
            set.clear();
        }
    }

    /**
     * Pre-populates mock data for blockstates and tags to simulate and test serving of RAM streams.
     */
    private void prepopulateMockData() {
        // Mock blockstate: map a virtual block to its models/variants
        String mockBlockstate = "{\n" +
                "  \"variants\": {\n" +
                "    \"\": {\n" +
                "      \"model\": \"chainloader:block/virtual_block\"\n" +
                "    }\n" +
                "  }\n" +
                "}";
        registerBlockstate("chainloader", "virtual_block", mockBlockstate);

        // Mock tag: register virtual_block to the mineable/pickaxe block tag list
        String mockBlockTag = "{\n" +
                "  \"replace\": false,\n" +
                "  \"values\": [\n" +
                "    \"chainloader:virtual_block\"\n" +
                "  ]\n" +
                "}";
        registerTag("minecraft", "blocks", "mineable/pickaxe", mockBlockTag);
        
        // Mock tag: register a custom mod tag list for items
        String mockItemTag = "{\n" +
                "  \"replace\": false,\n" +
                "  \"values\": [\n" +
                "    \"chainloader:virtual_item\"\n" +
                "  ]\n" +
                "}";
        registerTag("chainloader", "items", "compat_items", mockItemTag);
    }
}
