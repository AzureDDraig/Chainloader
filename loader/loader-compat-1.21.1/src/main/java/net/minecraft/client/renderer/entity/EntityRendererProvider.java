package net.minecraft.client.renderer.entity;

import net.minecraft.world.entity.Entity;

@FunctionalInterface
public interface EntityRendererProvider<T extends Entity> {
    Object create(Context context);

    public static class Context {}
}
