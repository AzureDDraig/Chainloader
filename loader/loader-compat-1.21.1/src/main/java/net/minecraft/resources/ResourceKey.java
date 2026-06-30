package net.minecraft.resources;

/**
 * Compile-time stub for net.minecraft.resources.ResourceKey.
 */
public class ResourceKey<T> {
    private final ResourceLocation registryName;
    private final ResourceLocation location;

    private ResourceKey(ResourceLocation registryName, ResourceLocation location) {
        this.registryName = registryName;
        this.location = location;
    }

    public static <T> ResourceKey<T> create(ResourceLocation registryName, ResourceLocation location) {
        return new ResourceKey<>(registryName, location);
    }

    public static <T> ResourceKey<T> create(ResourceKey<?> registryKey, ResourceLocation location) {
        return new ResourceKey<>(registryKey.location(), location);
    }

    public ResourceLocation location() {
        return location;
    }

    public ResourceLocation registryName() {
        return registryName;
    }
}
