package net.fabricmc.fabric.api.client.model;

import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.resources.ResourceLocation;
import java.util.function.Consumer;

@Deprecated
@FunctionalInterface
public interface ModelAppender extends ExtraModelProvider {
    @Override
    default void provideExtraModels(ResourceManager manager, Consumer<ResourceLocation> out) {
        appendAll(manager, out);
    }

    void appendAll(ResourceManager manager, Consumer<ResourceLocation> out);
}
