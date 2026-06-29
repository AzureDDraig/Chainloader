package net.neoforged.neoforge.client.event;

import net.neoforged.bus.api.Event;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.resources.model.BakedModel;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

public class ModelEvent extends Event {

    public static class RegisterAdditional extends ModelEvent {
        private final Set<ResourceLocation> models = new HashSet<>();

        public void register(ResourceLocation model) {
            models.add(model);
        }

        public Set<ResourceLocation> getModels() {
            return models;
        }
    }

    public static class BakingCompleted extends ModelEvent {
        private final Map<ResourceLocation, BakedModel> models;

        public BakingCompleted(Map<ResourceLocation, BakedModel> models) {
            this.models = models;
        }

        public Map<ResourceLocation, BakedModel> getModels() {
            return models;
        }
    }

    public static class ModifyBakingResult extends ModelEvent {
        private final Map<ResourceLocation, BakedModel> models;

        public ModifyBakingResult(Map<ResourceLocation, BakedModel> models) {
            this.models = models;
        }

        public Map<ResourceLocation, BakedModel> getModels() {
            return models;
        }
    }

    public static class RegisterGeometryLoaders extends ModelEvent {
        public void register(ResourceLocation id, Object loader) {
            // stub
        }
    }
}
