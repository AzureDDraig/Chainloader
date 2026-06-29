package dev.emi.emi.api.recipe;

import net.minecraft.resources.ResourceLocation;

/**
 * Mockup of EMI's EmiRecipeCategory class.
 */
public class EmiRecipeCategory {
    private final ResourceLocation id;
    private final Object icon;

    public EmiRecipeCategory(ResourceLocation id, Object icon) {
        this.id = id;
        this.icon = icon;
    }

    public ResourceLocation getId() {
        return id;
    }

    public Object getIcon() {
        return icon;
    }
}
