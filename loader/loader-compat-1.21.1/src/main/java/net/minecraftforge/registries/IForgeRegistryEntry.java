package net.minecraftforge.registries;

import net.minecraft.resources.ResourceLocation;

public interface IForgeRegistryEntry<T> {
    T setRegistryName(ResourceLocation name);
    ResourceLocation getRegistryName();
    Class<T> getRegistryType();
}
