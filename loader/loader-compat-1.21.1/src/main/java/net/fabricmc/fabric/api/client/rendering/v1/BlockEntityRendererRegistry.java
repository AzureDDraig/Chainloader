package net.fabricmc.fabric.api.client.rendering.v1;

import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Helper class for registering block entity renderers.
 */
public final class BlockEntityRendererRegistry {
    private BlockEntityRendererRegistry() {}

    /**
     * Register a block entity renderer.
     *
     * @param type     the block entity type
     * @param provider the renderer provider
     * @param <T>      the block entity type
     */
    @SuppressWarnings("unchecked")
    public static <T extends BlockEntity> void register(BlockEntityType<? extends T> type, BlockEntityRendererProvider<T> provider) {
        try {
            net.minecraft.client.renderer.blockentity.BlockEntityRenderers.register(type, provider);
            System.out.println("[ChainLoader] Registered Fabric block entity renderer for " + type);
            net.chainloader.loader.compat.bridge.FabricApiPort.BlockEntityRendererRegistry.register(type, provider);
        } catch (Throwable t) {
            System.err.println("Failed to register block entity renderer for " + type + ":");
            t.printStackTrace();
        }
    }
}
