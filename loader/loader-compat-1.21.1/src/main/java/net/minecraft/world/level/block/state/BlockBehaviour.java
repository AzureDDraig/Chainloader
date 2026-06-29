package net.minecraft.world.level.block.state;

public abstract class BlockBehaviour {
    public net.minecraft.world.InteractionResult useWithoutItem(BlockState state, net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos, net.minecraft.world.entity.player.Player player, net.minecraft.world.phys.BlockHitResult hit) {
        return null;
    }
}
