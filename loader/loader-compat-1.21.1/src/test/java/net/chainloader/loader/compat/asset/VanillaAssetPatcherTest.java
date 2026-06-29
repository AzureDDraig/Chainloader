package net.chainloader.loader.compat.asset;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class VanillaAssetPatcherTest {

    private VanillaAssetPatcher patcher;

    @BeforeEach
    public void setUp() {
        patcher = VanillaAssetPatcher.getInstance();
        patcher.clear();
        
        // Register test-specific namespace and path mappings
        AssetPathRelocator.getInstance().registerNamespaceMapping("old_mod", "new_mod");
        AssetPathRelocator.getInstance().registerPathMapping("old_mod:textures/blocks/dirt.png", "new_mod:textures/block/super_dirt.png");
    }

    @Test
    public void testPathRelocationAndSingularization() {
        byte[] dummyContent = "dummy_texture_bytes".getBytes(StandardCharsets.UTF_8);
        
        // Register plural and legacy namespace asset
        patcher.registerVirtualAsset("old_mod/textures/blocks/dirt.png", dummyContent);
        
        // It should be relocated to "new_mod" namespace and singularized to "textures/block/super_dirt.png"
        String expectedPath = "new_mod/textures/block/super_dirt.png";
        assertTrue(patcher.hasVirtualAsset(patcher.resolvePath(expectedPath)), "Virtual asset should be registered under relocated and singularized path");
        
        // Resolve path check
        String resolved = patcher.relocateAssetPath("old_mod/textures/blocks/dirt.png");
        assertEquals("new_mod/textures/block/super_dirt.png", resolved);
    }

    @Test
    public void testSyncBypassAndVanillaOverrides() {
        VirtualAssetPack pack = new VirtualAssetPack("test-bypass-pack", false);
        
        // 1. Register a vanilla override (minecraft namespace)
        pack.registerBytes(PackType.CLIENT_RESOURCES, 
            new ResourceLocation("minecraft", "textures/blocks/stone.png"), 
            () -> "stone_bytes".getBytes(StandardCharsets.UTF_8));
            
        // 2. Register a custom JSON asset (must be synced even if not minecraft namespace)
        pack.registerBytes(PackType.CLIENT_RESOURCES, 
            new ResourceLocation("custom_mod", "models/blocks/custom.json"), 
            () -> "{}".getBytes(StandardCharsets.UTF_8));
            
        // 3. Register a custom non-JSON, non-minecraft asset (should be bypassed)
        pack.registerBytes(PackType.CLIENT_RESOURCES, 
            new ResourceLocation("custom_mod", "textures/blocks/bypassed.png"), 
            () -> "bypassed_bytes".getBytes(StandardCharsets.UTF_8));

        // Sync vanilla overrides
        patcher.populateModResources(); // Under the hood this will test populate/sync mechanisms
        
        // Let's call syncWithPackResources directly or syncVanillaOverrides using reflection/access
        patcher.syncWithPackResources(pack);
        
        // Verify stone (vanilla override) is synced, singularized, and relocated
        assertTrue(patcher.hasVirtualAsset(patcher.relocateAssetPath("minecraft/textures/blocks/stone.png")), 
            "Vanilla override (stone) should be synced and singularized");
            
        // Verify custom JSON (model) is synced and singularized
        assertTrue(patcher.hasVirtualAsset(patcher.relocateAssetPath("custom_mod/models/blocks/custom.json")), 
            "Custom JSON asset should be synced and singularized");
            
        // Verify custom non-JSON, non-minecraft is NOT synced (sync bypass)
        assertFalse(patcher.hasVirtualAsset(patcher.relocateAssetPath("custom_mod/textures/blocks/bypassed.png")), 
            "Non-JSON custom asset should bypass sync to save RAM/time");
    }

    @Test
    public void testPatchAssetIndex() {
        byte[] stoneContent = "stone_override".getBytes(StandardCharsets.UTF_8);
        patcher.registerVirtualAsset("minecraft/textures/blocks/stone.png", stoneContent);

        String originalJson = "{\n" +
                "  \"objects\": {\n" +
                "    \"minecraft/textures/block/stone.png\": {\n" +
                "      \"hash\": \"original_hash\",\n" +
                "      \"size\": 10\n" +
                "    }\n" +
                "  }\n" +
                "}";

        String patchedJson = patcher.patchAssetIndex(originalJson);
        
        // Should contain the new hash computed from 'stone_override'
        assertTrue(patchedJson.contains("minecraft/textures/block/stone.png"));
        assertFalse(patchedJson.contains("original_hash"), "Vanilla asset index entry should be overridden");
    }
}
