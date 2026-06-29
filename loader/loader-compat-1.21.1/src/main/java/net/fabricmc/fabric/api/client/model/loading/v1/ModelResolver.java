package net.fabricmc.fabric.api.client.model.loading.v1;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.resources.model.UnbakedModel;

@FunctionalInterface
public interface ModelResolver {
    UnbakedModel resolveModel(Context context);

    interface Context {
        ResourceLocation id();

        UnbakedModel getOrLoadModel(ResourceLocation id);

        Object loader();
    }
}
