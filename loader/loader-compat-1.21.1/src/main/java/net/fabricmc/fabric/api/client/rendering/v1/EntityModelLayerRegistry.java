package net.fabricmc.fabric.api.client.rendering.v1;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import java.util.Map;
import java.util.HashMap;

public final class EntityModelLayerRegistry {
    private static final Map<ModelLayerLocation, TexturedModelDataProvider> PROVIDERS = new HashMap<>();

    @FunctionalInterface
    public interface TexturedModelDataProvider {
        LayerDefinition createModelData();
    }

    public static void registerModelLayer(ModelLayerLocation modelLayer, TexturedModelDataProvider provider) {
        PROVIDERS.put(modelLayer, provider);
        System.out.println("[ChainLoader] Registered Fabric model layer: " + modelLayer);
    }

    public static Map<ModelLayerLocation, LayerDefinition> createRoots() {
        Map<ModelLayerLocation, LayerDefinition> roots = new HashMap<>();
        for (Map.Entry<ModelLayerLocation, TexturedModelDataProvider> entry : PROVIDERS.entrySet()) {
            try {
                roots.put(entry.getKey(), entry.getValue().createModelData());
            } catch (Throwable t) {
                System.err.println("Failed to construct model data for layer " + entry.getKey() + ":");
                t.printStackTrace();
            }
        }
        return roots;
    }
}
