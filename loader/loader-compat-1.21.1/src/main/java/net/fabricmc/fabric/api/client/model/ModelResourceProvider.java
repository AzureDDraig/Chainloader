package net.fabricmc.fabric.api.client.model;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

public interface ModelResourceProvider {
    Object loadModelResource(ResourceLocation resourceId, ResourceManager resourceManager);
}
