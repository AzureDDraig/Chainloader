package dev.emi.emi.api;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;

/**
 * Mockup of EMI's EmiRegistry interface.
 */
public interface EmiRegistry {
    void addCategory(EmiRecipeCategory category);
    void addRecipe(EmiRecipe recipe);
    void addWorkstation(EmiRecipeCategory category, EmiIngredient workstation);
}
