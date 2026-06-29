package net.fabricmc.fabric.api.biome.v1;

import java.util.function.Predicate;

public final class BiomeSelectors {
    public static Predicate<BiomeSelectionContext> all() {
        return context -> true;
    }
    
    public static Predicate<BiomeSelectionContext> foundInOverworld() {
        return context -> true;
    }

    public static Predicate<BiomeSelectionContext> foundInTheNether() {
        return context -> true;
    }

    public static Predicate<BiomeSelectionContext> foundInTheEnd() {
        return context -> true;
    }
}
