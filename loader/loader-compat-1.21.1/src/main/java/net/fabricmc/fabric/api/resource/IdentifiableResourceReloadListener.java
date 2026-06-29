package net.fabricmc.fabric.api.resource;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.PreparableReloadListener;

public interface IdentifiableResourceReloadListener extends PreparableReloadListener {
    ResourceLocation getFabricId();
}
