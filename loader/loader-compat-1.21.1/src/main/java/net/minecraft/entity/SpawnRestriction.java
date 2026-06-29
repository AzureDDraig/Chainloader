package net.minecraft.entity;

public class SpawnRestriction {
    public enum Location {
        ON_GROUND,
        IN_WATER,
        IN_LAVA;
    }

    public interface Predicate<T extends Entity> {
        boolean test(EntityType<T> type, net.minecraft.world.World world, Object reason, Object pos, Object random);
    }
}
