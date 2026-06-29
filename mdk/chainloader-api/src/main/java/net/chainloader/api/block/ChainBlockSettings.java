package net.chainloader.api.block;

import java.util.function.ToIntFunction;
import net.minecraft.block.BlockState;

/**
 * Platform-agnostic settings class for creating Minecraft blocks.
 * <p>
 * This class abstracts away differences in block property definition between Fabric and NeoForge.
 * Under the hood, ChainLoader's platform implementations translate this class into platform-specific properties:
 * <ul>
 *   <li><b>Fabric:</b> Maps to {@code net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings} 
 *       or {@code net.minecraft.block.AbstractBlock.Settings}.</li>
 *   <li><b>NeoForge:</b> Maps to {@code net.minecraft.world.level.block.state.BlockBehaviour.Properties}.</li>
 * </ul>
 * </p>
 *
 * <h3>Example Usage:</h3>
 * <pre>{@code
 * ChainBlockSettings settings = ChainBlockSettings.create()
 *     .strength(2.0f, 6.0f)
 *     .friction(0.6f)
 *     .lightLevel(state -> state.get(LIT) ? 15 : 0);
 * }</pre>
 */
public class ChainBlockSettings {

    private float hardness = 0.0f;
    private float resistance = 0.0f;
    private float friction = 0.6f;
    private ToIntFunction<BlockState> lightEmission = (state) -> 0;

    /**
     * Creates a new instance of block settings with default parameters.
     *
     * @return A new ChainBlockSettings instance.
     */
    public static ChainBlockSettings create() {
        return new ChainBlockSettings();
    }

    protected ChainBlockSettings() {}

    /**
     * Sets both the hardness and resistance of the block.
     * <p>
     * <b>Fabric mapping:</b> {@code FabricBlockSettings.strength(hardness, resistance)}<br>
     * <b>NeoForge mapping:</b> {@code BlockBehaviour.Properties.destroyTime(hardness).explosionResistance(resistance)}
     * </p>
     *
     * @param hardness   The block hardness (determines mining speed).
     * @param resistance The block explosion resistance.
     * @return This settings instance for chaining.
     */
    public ChainBlockSettings strength(float hardness, float resistance) {
        this.hardness = hardness;
        this.resistance = resistance;
        return this;
    }

    /**
     * Sets only the hardness of the block.
     * <p>
     * <b>Fabric mapping:</b> {@code FabricBlockSettings.hardness(hardness)}<br>
     * <b>NeoForge mapping:</b> {@code BlockBehaviour.Properties.destroyTime(hardness)}
     * </p>
     *
     * @param hardness The block hardness.
     * @return This settings instance for chaining.
     */
    public ChainBlockSettings hardness(float hardness) {
        this.hardness = hardness;
        return this;
    }

    /**
     * Sets only the explosion resistance of the block.
     * <p>
     * <b>Fabric mapping:</b> {@code FabricBlockSettings.resistance(resistance)}<br>
     * <b>NeoForge mapping:</b> {@code BlockBehaviour.Properties.explosionResistance(resistance)}
     * </p>
     *
     * @param resistance The block explosion resistance.
     * @return This settings instance for chaining.
     */
    public ChainBlockSettings resistance(float resistance) {
        this.resistance = resistance;
        return this;
    }

    /**
     * Sets the friction (slipperiness) of the block.
     * <p>
     * <b>Fabric mapping:</b> {@code FabricBlockSettings.slipperiness(friction)}<br>
     * <b>NeoForge mapping:</b> {@code BlockBehaviour.Properties.friction(friction)}
     * </p>
     *
     * @param friction The friction value (e.g., 0.6 for normal block, 0.98 for ice).
     * @return This settings instance for chaining.
     */
    public ChainBlockSettings friction(float friction) {
        this.friction = friction;
        return this;
    }

    /**
     * Sets a dynamic light level supplier for the block.
     * <p>
     * <b>Fabric mapping:</b> {@code FabricBlockSettings.luminance(lightEmission)}<br>
     * <b>NeoForge mapping:</b> {@code BlockBehaviour.Properties.lightLevel(lightEmission)}
     * </p>
     *
     * @param lightEmission A function that determines the light level (0-15) based on the block state.
     * @return This settings instance for chaining.
     */
    public ChainBlockSettings lightLevel(ToIntFunction<BlockState> lightEmission) {
        if (lightEmission == null) {
            throw new IllegalArgumentException("Light emission supplier cannot be null");
        }
        this.lightEmission = lightEmission;
        return this;
    }

    /**
     * Sets a constant light level for the block.
     *
     * @param light The static light level (0-15).
     * @return This settings instance for chaining.
     */
    public ChainBlockSettings lightLevel(int light) {
        if (light < 0 || light > 15) {
            throw new IllegalArgumentException("Light level must be between 0 and 15");
        }
        this.lightEmission = (state) -> light;
        return this;
    }

    /**
     * Gets the hardness value.
     *
     * @return The hardness.
     */
    public float getHardness() {
        return hardness;
    }

    /**
     * Gets the explosion resistance value.
     *
     * @return The resistance.
     */
    public float getResistance() {
        return resistance;
    }

    /**
     * Gets the friction value.
     *
     * @return The friction.
     */
    public float getFriction() {
        return friction;
    }

    /**
     * Gets the light emission supplier.
     *
     * @return The light emission function.
     */
    public ToIntFunction<BlockState> getLightEmission() {
        return lightEmission;
    }
}
