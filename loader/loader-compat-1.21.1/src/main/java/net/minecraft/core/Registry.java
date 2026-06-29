package net.minecraft.core;

import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

/**
 * Compile-time stub for net.minecraft.core.Registry.
 */
public interface Registry<T> {
    ResourceKey<? extends Registry<T>> key();
    T get(ResourceLocation id);
    T get(ResourceKey<?> key);
    int getRawId(T value);
    java.util.Optional<ResourceKey<T>> getResourceKey(T value);

    public static <V, T extends V> T register(Registry<V> registry, ResourceLocation name, T value) {
        return value;
    }
}
