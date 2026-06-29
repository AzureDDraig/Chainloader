package net.minecraft.client.render.entity;

public interface EntityRendererFactory<T extends net.minecraft.entity.Entity> {
    Object create(Context context);

    class Context {
    }
}
