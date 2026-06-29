package me.shedaniel.rei.api.client.plugins;

import me.shedaniel.rei.api.client.registry.category.CategoryRegistry;
import me.shedaniel.rei.api.client.registry.display.DisplayRegistry;

/**
 * Mockup of REI's REIClientPlugin interface.
 */
public interface REIClientPlugin {
    default void registerCategories(CategoryRegistry registry) {}
    default void registerDisplays(DisplayRegistry registry) {}
}
