package net.chainloader.api.entity;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.SpawnRestriction;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import net.minecraft.resources.ResourceKey;

/**
 * Platform-agnostic configuration and builder for custom Minecraft entities.
 * <p>
 * This class abstracts the registration details (size, fire immunity, saving, tracking)
 * and spawn behaviors (biome constraints, spawning restrictions, dimension boundaries).
 * </p>
 *
 * <h3>Under-the-Hood Mappings:</h3>
 * <ul>
 *   <li><b>Fabric spawn setups:</b> Automatically registered during mod startup using
 *       {@code SpawnRestriction.register(entityType, location, heightmap, predicate)}.</li>
 *   <li><b>NeoForge spawn setups:</b> Automatically intercepted and registered inside the
 *       {@code RegisterSpawnPlacementsEvent} event on the mod bus.</li>
 *   <li><b>Dimension Boundaries:</b> Intercepted in custom biome/spawning modifiers
 *       (Fabric Biome API / NeoForge Biome Modifiers) to restrict entity spawning to allowed dimensions.</li>
 * </ul>
 *
 * @param <T> The class type of the custom Entity.
 */
public class ChainEntityType<T extends Entity> {

    private final EntityType.EntityFactory<T> factory;
    private final SpawnGroup spawnGroup;
    private final float width;
    private final float height;
    private final boolean fireImmune;
    private final int trackingRange;
    private final int updateInterval;
    private final boolean saveable;
    private final Set<ResourceKey<World>> allowedDimensions;
    
    // Spawning placements
    private final SpawnRestriction.Location spawnLocation;
    private final Heightmap.Type heightmapType;
    private final SpawnRestriction.Predicate<T> spawnPredicate;

    private ChainEntityType(Builder<T> builder) {
        this.factory = builder.factory;
        this.spawnGroup = builder.spawnGroup;
        this.width = builder.width;
        this.height = builder.height;
        this.fireImmune = builder.fireImmune;
        this.trackingRange = builder.trackingRange;
        this.updateInterval = builder.updateInterval;
        this.saveable = builder.saveable;
        this.allowedDimensions = Collections.unmodifiableSet(builder.allowedDimensions);
        this.spawnLocation = builder.spawnLocation;
        this.heightmapType = builder.heightmapType;
        this.spawnPredicate = builder.spawnPredicate;
    }

    public EntityType.EntityFactory<T> getFactory() {
        return factory;
    }

    public SpawnGroup getSpawnGroup() {
        return spawnGroup;
    }

    public float getWidth() {
        return width;
    }

    public float getHeight() {
        return height;
    }

    public boolean isFireImmune() {
        return fireImmune;
    }

    public int getTrackingRange() {
        return trackingRange;
    }

    public int getUpdateInterval() {
        return updateInterval;
    }

    public boolean isSaveable() {
        return saveable;
    }

    public Set<ResourceKey<World>> getAllowedDimensions() {
        return allowedDimensions;
    }

    public SpawnRestriction.Location getSpawnLocation() {
        return spawnLocation;
    }

    public Heightmap.Type getHeightmapType() {
        return heightmapType;
    }

    public SpawnRestriction.Predicate<T> getSpawnPredicate() {
        return spawnPredicate;
    }

    /**
     * Builder class for creating {@link ChainEntityType} instances.
     *
     * @param <T> The type of the Entity.
     */
    public static class Builder<T extends Entity> {
        private final EntityType.EntityFactory<T> factory;
        private final SpawnGroup spawnGroup;
        private float width = 0.6f;
        private float height = 1.8f;
        private boolean fireImmune = false;
        private int trackingRange = 5;
        private int updateInterval = 3;
        private boolean saveable = true;
        private final Set<ResourceKey<World>> allowedDimensions = new HashSet<>();
        
        // Spawn placements default to null (no spawn rules)
        private SpawnRestriction.Location spawnLocation = null;
        private Heightmap.Type heightmapType = null;
        private SpawnRestriction.Predicate<T> spawnPredicate = null;

        private Builder(EntityType.EntityFactory<T> factory, SpawnGroup spawnGroup) {
            this.factory = factory;
            this.spawnGroup = spawnGroup;
        }

        /**
         * Creates a new builder for an entity type.
         *
         * @param factory    The factory to create instances of the entity.
         * @param spawnGroup The classification/spawn category of the entity.
         * @param <T>        The entity class type.
         * @return A new Builder.
         */
        public static <T extends Entity> Builder<T> create(EntityType.EntityFactory<T> factory, SpawnGroup spawnGroup) {
            return new Builder<>(factory, spawnGroup);
        }

        /**
         * Sets the physical dimensions of the entity.
         *
         * @param width  The width of the entity.
         * @param height The height of the entity.
         * @return This builder for chaining.
         */
        public Builder<T> dimensions(float width, float height) {
            this.width = width;
            this.height = height;
            return this;
        }

        /**
         * Makes the entity immune to fire, lava, and burning damage.
         *
         * @return This builder for chaining.
         */
        public Builder<T> fireImmune() {
            this.fireImmune = true;
            return this;
        }

        /**
         * Sets the tracking range (how far away client gets synchronization updates).
         *
         * @param trackingRange Tracking range in chunks.
         * @return This builder for chaining.
         */
        public Builder<T> trackingRange(int trackingRange) {
            this.trackingRange = trackingRange;
            return this;
        }

        /**
         * Sets the update interval (ticks between client synchronization).
         *
         * @param updateInterval Update interval.
         * @return This builder for chaining.
         */
        public Builder<T> updateInterval(int updateInterval) {
            this.updateInterval = updateInterval;
            return this;
        }

        /**
         * Sets whether the entity should be saved to disk.
         *
         * @param saveable True to persist the entity, false to discard.
         * @return This builder for chaining.
         */
        public Builder<T> saveable(boolean saveable) {
            this.saveable = saveable;
            return this;
        }

        /**
         * Restricts spawning/existence of this entity to specific dimension keys.
         *
         * @param dimension The resource key of the dimension (e.g. World.OVERWORLD).
         * @return This builder for chaining.
         */
        public Builder<T> allowDimension(ResourceKey<World> dimension) {
            this.allowedDimensions.add(dimension);
            return this;
        }

        /**
         * Configures the natural spawn placement rules for the entity.
         *
         * @param location  The placement constraint (e.g. ON_GROUND).
         * @param heightmap The heightmap type (e.g. MOTION_BLOCKING_NO_LEAVES).
         * @param predicate The condition logic that checks if the entity can spawn.
         * @return This builder for chaining.
         */
        public Builder<T> spawnSetup(SpawnRestriction.Location location, Heightmap.Type heightmap, SpawnRestriction.Predicate<T> predicate) {
            this.spawnLocation = location;
            this.heightmapType = heightmap;
            this.spawnPredicate = predicate;
            return this;
        }

        /**
         * Constructs the final platform-agnostic ChainEntityType.
         *
         * @return A configured ChainEntityType instance.
         */
        public ChainEntityType<T> build() {
            return new ChainEntityType<>(this);
        }
    }
}
