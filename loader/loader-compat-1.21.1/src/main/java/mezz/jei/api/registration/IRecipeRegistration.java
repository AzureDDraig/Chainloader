package mezz.jei.api.registration;

import java.util.Collection;
import mezz.jei.api.recipe.RecipeType;

/**
 * Mockup of JEI's IRecipeRegistration interface.
 */
public interface IRecipeRegistration {
    <T> void addRecipes(RecipeType<T> recipeType, Collection<T> recipes);
}
