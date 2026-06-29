package dev.emi.emi.api.recipe;

import java.util.List;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.resources.ResourceLocation;

/**
 * Mockup of EMI's EmiRecipe interface.
 */
public interface EmiRecipe {
    EmiRecipeCategory getCategory();
    ResourceLocation getId();
    List<EmiIngredient> getInputs();
    List<EmiStack> getOutputs();
    int getDisplayWidth();
    int getDisplayHeight();
}
