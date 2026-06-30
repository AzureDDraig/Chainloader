package net.minecraft.core;

import java.util.Iterator;
import java.util.Set;
import java.util.Optional;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

public interface Registry<T> extends Iterable<T> {
    ResourceKey<? extends Registry<T>> key();
    T get(ResourceLocation loc);
    T get(ResourceKey<?> key);
    int getRawId(T value);
    Optional<ResourceKey<T>> getResourceKey(T value);
    
    static <V, T extends V> T register(Registry<V> registry, ResourceLocation loc, T value) {
        return null;
    }
    
    default ResourceLocation getKey(T value) {
        return null;
    }
    
    default Set<ResourceLocation> keySet() {
        return null;
    }

    default Iterator<T> iterator() {
        return null;
    }
}
