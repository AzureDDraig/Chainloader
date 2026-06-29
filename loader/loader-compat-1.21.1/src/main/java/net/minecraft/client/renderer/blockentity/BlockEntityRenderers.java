package net.minecraft.client.renderer.blockentity;

import net.minecraft.world.level.block.entity.BlockEntityType;

public class BlockEntityRenderers {
    public static <T extends net.minecraft.world.level.block.entity.BlockEntity> void register(BlockEntityType<? extends T> type, BlockEntityRendererProvider<T> provider) {}
}
