package me.shedaniel.rei.api.common.category;

import net.minecraft.resources.ResourceLocation;

/**
 * Mockup of REI's CategoryIdentifier class.
 */
public final class CategoryIdentifier<T> {
    private final ResourceLocation id;

    private CategoryIdentifier(ResourceLocation id) {
        this.id = id;
    }

    public static <T> CategoryIdentifier<T> of(ResourceLocation id) {
        return new CategoryIdentifier<>(id);
    }

    public static <T> CategoryIdentifier<T> of(String namespace, String path) {
        return new CategoryIdentifier<>(new ResourceLocation(namespace, path));
    }

    public ResourceLocation getIdentifier() {
        return id;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof CategoryIdentifier)) return false;
        return id.equals(((CategoryIdentifier<?>) obj).id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
