package net.minecraft.world.level.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import java.util.Set;

public class BlockEntityType<T extends BlockEntity> {
    public static final BlockEntityType<?> CHEST = null;

    public interface BlockEntitySupplier<T extends BlockEntity> {
        T create(BlockPos pos, BlockState state);
    }

    public static class Builder<T extends BlockEntity> {
        private final BlockEntitySupplier<? extends T> supplier;
        private final Set<Block> blocks;

        private Builder(BlockEntitySupplier<? extends T> supplier, Set<Block> blocks) {
            this.supplier = supplier;
            this.blocks = blocks;
        }

        public static <T extends BlockEntity> Builder<T> of(BlockEntitySupplier<? extends T> supplier, Block... blocks) {
            return new Builder<>(supplier, Set.of(blocks));
        }

        public BlockEntityType<T> build(com.mojang.datafixers.types.Type<?> type) {
            return new BlockEntityType<>();
        }
    }
}
