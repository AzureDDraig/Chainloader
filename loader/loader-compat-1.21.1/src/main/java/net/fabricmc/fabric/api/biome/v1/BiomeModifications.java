package net.fabricmc.fabric.api.biome.v1;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.GenerationStep;
import java.util.function.Predicate;

public final class BiomeModifications {
    public static void addFeature(Predicate<BiomeSelectionContext> selector, GenerationStep.Decoration step, ResourceKey<?> placedFeatureKey) {
        // Stub implementation
    }
}
