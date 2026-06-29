package net.fabricmc.fabric.api.client.model;

import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.resources.ResourceLocation;
import java.util.function.Consumer;

@FunctionalInterface
public interface ExtraModelProvider {
    void provideExtraModels(ResourceManager manager, Consumer<ResourceLocation> out);
}
