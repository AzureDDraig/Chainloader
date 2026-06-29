package net.fabricmc.fabric.api.blockrenderlayer.v1;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ItemBlockRenderTypes;

public interface BlockRenderLayerMap {
    BlockRenderLayerMap INSTANCE = new BlockRenderLayerMap() {
        @Override
        public void putBlock(Block block, RenderType renderType) {
            try {
                ItemBlockRenderTypes.TYPE_BY_BLOCK.put(block, renderType);
                System.out.println("[ChainLoader] Registered BlockRenderLayer for block " + block + " -> " + renderType);
            } catch (Throwable t) {
                System.err.println("Failed to putBlock in ItemBlockRenderTypes:");
                t.printStackTrace();
            }
        }

        @Override
        public void putBlocks(RenderType renderType, Block... blocks) {
            for (Block block : blocks) {
                putBlock(block, renderType);
            }
        }

        @Override
        public void putFluid(Fluid fluid, RenderType renderType) {
            try {
                ItemBlockRenderTypes.TYPE_BY_FLUID.put(fluid, renderType);
                System.out.println("[ChainLoader] Registered BlockRenderLayer for fluid " + fluid + " -> " + renderType);
            } catch (Throwable t) {
                System.err.println("Failed to putFluid in ItemBlockRenderTypes:");
                t.printStackTrace();
            }
        }

        @Override
        public void putFluids(RenderType renderType, Fluid... fluids) {
            for (Fluid fluid : fluids) {
                putFluid(fluid, renderType);
            }
        }
    };

    void putBlock(Block block, RenderType renderType);
    void putBlocks(RenderType renderType, Block... blocks);
    void putFluid(Fluid fluid, RenderType renderType);
    void putFluids(RenderType renderType, Fluid... fluids);
}
