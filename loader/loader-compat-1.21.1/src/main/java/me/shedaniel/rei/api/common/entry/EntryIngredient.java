package me.shedaniel.rei.api.common.entry;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Mockup of REI's EntryIngredient class.
 */
public class EntryIngredient extends ArrayList<EntryStack<?>> {
    public static EntryIngredient of(EntryStack<?> stack) {
        EntryIngredient ingredient = new EntryIngredient();
        ingredient.add(stack);
        return ingredient;
    }

    public static EntryIngredient of(Collection<? extends EntryStack<?>> stacks) {
        EntryIngredient ingredient = new EntryIngredient();
        ingredient.addAll(stacks);
        return ingredient;
    }
}
