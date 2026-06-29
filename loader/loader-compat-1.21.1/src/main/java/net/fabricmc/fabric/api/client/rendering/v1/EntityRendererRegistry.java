package net.fabricmc.fabric.api.client.rendering.v1;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity;

public final class EntityRendererRegistry {
    @SuppressWarnings("unchecked")
    public static <T extends Entity> void register(EntityType<? extends T> entityType, EntityRendererProvider<T> provider) {
        try {
            net.minecraft.client.renderer.entity.EntityRenderers.register((EntityType) entityType, (EntityRendererProvider) provider);
            System.out.println("[ChainLoader] Registered Fabric entity renderer for " + entityType);
        } catch (Throwable t) {
            System.err.println("Failed to register entity renderer for " + entityType + ":");
            t.printStackTrace();
        }
    }
}
