package mezz.jei.api.recipe;

import net.minecraft.resources.ResourceLocation;

/**
 * Mockup of JEI's RecipeType class.
 */
public final class RecipeType<T> {
    private final ResourceLocation uid;
    private final Class<? extends T> recipeClass;

    public static <T> RecipeType<T> create(String namespace, String path, Class<? extends T> recipeClass) {
        return new RecipeType<>(new ResourceLocation(namespace, path), recipeClass);
    }

    public RecipeType(ResourceLocation uid, Class<? extends T> recipeClass) {
        this.uid = uid;
        this.recipeClass = recipeClass;
    }

    public ResourceLocation getUid() {
        return uid;
    }

    public Class<? extends T> getRecipeClass() {
        return recipeClass;
    }
}
