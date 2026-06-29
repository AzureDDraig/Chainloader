package net.minecraft.entity;

public class EntityType<T extends Entity> {
    public interface EntityFactory<T extends Entity> {
        T create(EntityType<T> type, net.minecraft.world.World world);
    }
}
