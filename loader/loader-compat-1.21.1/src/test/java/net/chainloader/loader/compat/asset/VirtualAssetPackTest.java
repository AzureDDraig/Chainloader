package net.chainloader.loader.compat.asset;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.IoSupplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Unit tests verifying VirtualAssetPack resource generation, RAM stream serving,
 * path relocator coordination, and dynamic index patching.
 */
public class VirtualAssetPackTest {

    private VirtualAssetPack assetPack;

    @BeforeEach
    public void setUp() {
        assetPack = new VirtualAssetPack("test-virtual-pack", false);
    }

    @Test
    public void testBaseResourceServing() throws Exception {
        // Register a test asset
        String blockstateJson = "{\"variants\":{\"*\"}}";
        assetPack.registerBlockstate("testmod", "my_block", blockstateJson);

        ResourceLocation location = new ResourceLocation("testmod", "blockstates/my_block.json");
        IoSupplier<InputStream> supplier = assetPack.getResource(PackType.CLIENT_RESOURCES, location);
        assertNotNull(supplier, "Should retrieve registered in-memory resource");

        try (InputStream is = supplier.get()) {
            byte[] bytes = is.readAllBytes();
            String result = new String(bytes, StandardCharsets.UTF_8);
            assertEquals(blockstateJson, result);
        }
    }

    @Test
    public void testMockDataPrepopulation() throws Exception {
        // Retrieve the pre-populated blockstate (should have been patched by the default index patcher)
        ResourceLocation location = new ResourceLocation("chainloader", "blockstates/virtual_block.json");
        IoSupplier<InputStream> supplier = assetPack.getResource(PackType.CLIENT_RESOURCES, location);
        assertNotNull(supplier);

        try (InputStream is = supplier.get()) {
            String result = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(result.contains("compat_patched"), "Default index patcher should patch blockstate");
        }

        // Retrieve the pre-populated block tag (should have been patched by default index patcher)
        ResourceLocation tagLoc = new ResourceLocation("minecraft", "tags/blocks/mineable/pickaxe.json");
        IoSupplier<InputStream> tagSupplier = assetPack.getResource(PackType.SERVER_DATA, tagLoc);
        assertNotNull(tagSupplier);

        try (InputStream is = tagSupplier.get()) {
            String result = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(result.contains("patched_virtual_block"), "Default index patcher should patch tag");
        }
    }

    @Test
    public void testPathRelocation() throws Exception {
        // Request legacy namespace resource location
        ResourceLocation legacyLocation = new ResourceLocation("legacy_mod", "blockstates/virtual_block.json");

        // This should be relocated by prepopulated relocator to "chainloader:blockstates/virtual_block.json"
        IoSupplier<InputStream> supplier = assetPack.getResource(PackType.CLIENT_RESOURCES, legacyLocation);
        assertNotNull(supplier, "Relocator should handle redirection of legacy resources");

        try (InputStream is = supplier.get()) {
            String result = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(result.contains("chainloader:block/virtual_block"), "Should return content from relocated target");
        }
    }

    @Test
    public void testCustomRelocatorAndPatcher() throws Exception {
        // Add a custom path relocator that maps "custom_namespace:input.json" -> "chainloader:blockstates/virtual_block.json"
        assetPack.registerPathRelocator((type, location) -> {
            if ("custom_namespace".equals(location.getNamespace())) {
                return new ResourceLocation("chainloader", "blockstates/virtual_block.json");
            }
            return location;
        });

        // Add a custom index patcher that appends a suffix
        assetPack.registerIndexPatcher((location, rawJson) -> rawJson + "\n// CUSTOM_PATCH");

        ResourceLocation customLocation = new ResourceLocation("custom_namespace", "input.json");
        IoSupplier<InputStream> supplier = assetPack.getResource(PackType.CLIENT_RESOURCES, customLocation);
        assertNotNull(supplier);

        try (InputStream is = supplier.get()) {
            String result = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(result.contains("chainloader:block/virtual_block"), "Should be redirected to virtual blockstate");
            assertTrue(result.endsWith("// CUSTOM_PATCH"), "Should have custom index patch applied");
        }
    }

    @Test
    public void testListResourcesWithRelocationAndPatching() {
        List<ResourceLocation> listed = new ArrayList<>();

        // Query listResources for CLIENT_RESOURCES on the "chainloader" namespace
        assetPack.listResources(PackType.CLIENT_RESOURCES, "chainloader", "blockstates", (location, streamSupplier) -> {
            listed.add(location);
            try (InputStream is = streamSupplier.get()) {
                String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                assertTrue(content.contains("compat_patched"), "Listed resource streams must be index patched");
            } catch (Exception e) {
                fail(e);
            }
        });

        assertTrue(listed.contains(new ResourceLocation("chainloader", "blockstates/virtual_block.json")));
    }

    @Test
    public void testGetNamespaces() {
        // Ensure namespaces contains the relocated names
        Set<String> clientNamespaces = assetPack.getNamespaces(PackType.CLIENT_RESOURCES);
        assertTrue(clientNamespaces.contains("chainloader"));

        Set<String> serverNamespaces = assetPack.getNamespaces(PackType.SERVER_DATA);
        assertTrue(serverNamespaces.contains("minecraft"));
    }
}
