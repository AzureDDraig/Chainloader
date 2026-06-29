package net.minecraft.client.renderer.blockentity;

@FunctionalInterface
public interface BlockEntityRendererProvider<T extends net.minecraft.world.level.block.entity.BlockEntity> {
    BlockEntityRenderer<T> create(Context context);

    public static class Context {}
}
