package me.shedaniel.rei.api.common.display;

import java.util.List;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.entry.EntryIngredient;

/**
 * Mockup of REI's Display interface.
 */
public interface Display {
    List<EntryIngredient> getInputEntries();
    List<EntryIngredient> getOutputEntries();
    CategoryIdentifier<?> getCategoryIdentifier();
}
