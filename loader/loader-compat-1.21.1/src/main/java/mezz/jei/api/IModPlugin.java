package mezz.jei.api;

import mezz.jei.api.registration.*;
import net.minecraft.resources.ResourceLocation;

/**
 * Mockup of JEI's IModPlugin interface.
 */
public interface IModPlugin {
    ResourceLocation getPluginUid();
    default void registerCategories(IRecipeCategoryRegistration registration) {}
    default void registerRecipes(IRecipeRegistration registration) {}
    default void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {}
}
