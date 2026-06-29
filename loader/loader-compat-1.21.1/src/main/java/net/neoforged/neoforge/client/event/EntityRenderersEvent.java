package net.neoforged.neoforge.client.event;

import net.neoforged.bus.api.Event;
import net.minecraft.world.entity.EntityType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import java.util.function.Supplier;
import java.util.Map;
import java.util.HashMap;

public class EntityRenderersEvent extends Event {

    public static class RegisterRenderers extends EntityRenderersEvent {
        private final Map<EntityType<?>, EntityRendererProvider<?>> entityRenderers = new HashMap<>();
        private final Map<BlockEntityType<?>, BlockEntityRendererProvider<?>> blockEntityRenderers = new HashMap<>();

        public <T extends net.minecraft.world.entity.Entity> void registerEntityRenderer(EntityType<? extends T> entityType, EntityRendererProvider<T> provider) {
            entityRenderers.put(entityType, provider);
        }

        public <T extends net.minecraft.world.level.block.entity.BlockEntity> void registerBlockEntityRenderer(BlockEntityType<? extends T> blockEntityType, BlockEntityRendererProvider<T> provider) {
            blockEntityRenderers.put(blockEntityType, provider);
        }

        public Map<EntityType<?>, EntityRendererProvider<?>> getEntityRenderers() {
            return entityRenderers;
        }

        public Map<BlockEntityType<?>, BlockEntityRendererProvider<?>> getBlockEntityRenderers() {
            return blockEntityRenderers;
        }
    }

    public static class RegisterLayerDefinitions extends EntityRenderersEvent {
        private final Map<ModelLayerLocation, Supplier<LayerDefinition>> layerDefinitions = new HashMap<>();

        public void registerLayerDefinition(ModelLayerLocation layerLocation, Supplier<LayerDefinition> provider) {
            layerDefinitions.put(layerLocation, provider);
        }

        public Map<ModelLayerLocation, Supplier<LayerDefinition>> getLayerDefinitions() {
            return layerDefinitions;
        }
    }
}
