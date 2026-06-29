package mezz.jei.api.registration;

import mezz.jei.api.recipe.RecipeType;

/**
 * Mockup of JEI's IRecipeCatalystRegistration interface.
 */
public interface IRecipeCatalystRegistration {
    void addRecipeCatalyst(Object ingredient, RecipeType<?>... recipeTypes);
}
