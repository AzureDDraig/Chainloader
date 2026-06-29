package me.shedaniel.rei.api.client.registry.category;

import me.shedaniel.rei.api.common.category.CategoryIdentifier;

/**
 * Mockup of REI's CategoryRegistry interface.
 */
public interface CategoryRegistry {
    void register(Object category);
    void addWorkstations(CategoryIdentifier<?> category, Object... workstations);
}
