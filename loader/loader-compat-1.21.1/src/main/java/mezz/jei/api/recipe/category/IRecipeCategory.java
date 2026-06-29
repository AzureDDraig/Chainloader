package mezz.jei.api.recipe.category;

import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.recipe.RecipeType;
import net.minecraft.resources.ResourceLocation;

/**
 * Mockup of JEI's IRecipeCategory interface.
 */
public interface IRecipeCategory<T> {
    RecipeType<T> getRecipeType();
    String getTitle();
    IDrawable getBackground();
    IDrawable getIcon();

    default ResourceLocation getRegistryName() {
        return getRecipeType().getUid();
    }
}
