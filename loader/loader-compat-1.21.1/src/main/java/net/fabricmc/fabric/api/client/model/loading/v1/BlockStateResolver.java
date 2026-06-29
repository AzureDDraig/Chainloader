package net.fabricmc.fabric.api.client.model.loading.v1;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.resources.model.UnbakedModel;

@FunctionalInterface
public interface BlockStateResolver {
    void resolveBlockStates(Context context);

    interface Context {
        Block block();

        void setModel(BlockState state, UnbakedModel model);

        UnbakedModel getOrLoadModel(ResourceLocation id);

        Object loader();
    }
}
