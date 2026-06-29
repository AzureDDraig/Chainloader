package mezz.jei.api.registration;

import mezz.jei.api.recipe.category.IRecipeCategory;

/**
 * Mockup of JEI's IRecipeCategoryRegistration interface.
 */
public interface IRecipeCategoryRegistration {
    void addRecipeCategories(IRecipeCategory<?>... recipeCategories);
}
