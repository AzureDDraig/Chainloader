package net.minecraft.client.renderer.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity;

public class EntityRenderers {
    public static <T extends Entity> void register(EntityType<? extends T> type, EntityRendererProvider<T> provider) {}
}
