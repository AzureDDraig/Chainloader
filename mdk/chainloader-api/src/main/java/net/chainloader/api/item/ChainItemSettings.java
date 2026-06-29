package net.chainloader.api.item;

import net.minecraft.item.FoodComponent;
import net.minecraft.item.Item;
import net.minecraft.util.Rarity;
import net.minecraft.resources.ResourceLocation;

/**
 * Platform-agnostic settings class for creating Minecraft items.
 * <p>
 * This class wraps standard item attributes such as stack limits, durability, rarity,
 * food properties, and creative tab bindings. ChainLoader translations map these to:
 * <ul>
 *   <li><b>Fabric:</b> Maps to {@code net.fabricmc.fabric.api.item.v1.FabricItemSettings}.</li>
 *   <li><b>NeoForge:</b> Maps to {@code net.minecraft.world.item.Item.Properties}.</li>
 * </ul>
 * </p>
 *
 * <h3>Example Usage:</h3>
 * <pre>{@code
 * ChainItemSettings settings = ChainItemSettings.create()
 *     .maxCount(16)
 *     .rarity(Rarity.RARE)
 *     .food(new FoodComponent.Builder().hunger(4).saturationModifier(0.3f).build())
 *     .creativeTab(new ResourceLocation("minecraft", "ingredients"));
 * }</pre>
 */
public class ChainItemSettings {

    private int maxCount = 64;
    private int maxDamage = 0;
    private Rarity rarity = Rarity.COMMON;
    private FoodComponent foodComponent = null;
    private Item recipeRemainder = null;
    private ResourceLocation creativeTab = null;

    /**
     * Creates a new instance of item settings with default parameters.
     *
     * @return A new ChainItemSettings instance.
     */
    public static ChainItemSettings create() {
        return new ChainItemSettings();
    }

    protected ChainItemSettings() {}

    /**
     * Sets the maximum stack size of the item.
     * <p>
     * <b>Fabric/NeoForge mapping:</b> {@code maxCount(maxCount)}
     * </p>
     *
     * @param maxCount The maximum number of items in a single stack (e.g. 1, 16, 64).
     * @return This settings instance for chaining.
     */
    public ChainItemSettings maxCount(int maxCount) {
        if (maxCount < 1 || maxCount > 64) {
            throw new IllegalArgumentException("Stack size must be between 1 and 64");
        }
        this.maxCount = maxCount;
        return this;
    }

    /**
     * Sets the maximum durability (damage capacity) of the item.
     * <p>
     * <b>Fabric/NeoForge mapping:</b> {@code maxDamage(maxDamage)}
     * </p>
     *
     * @param maxDamage The maximum durability.
     * @return This settings instance for chaining.
     */
    public ChainItemSettings maxDamage(int maxDamage) {
        if (maxDamage < 0) {
            throw new IllegalArgumentException("Max damage cannot be negative");
        }
        this.maxDamage = maxDamage;
        return this;
    }

    /**
     * Sets the item's rarity, which determines the color of its tooltip name.
     * <p>
     * <b>Fabric/NeoForge mapping:</b> {@code rarity(rarity)}
     * </p>
     *
     * @param rarity The Minecraft rarity level (e.g. COMMON, UNCOMMON, RARE, EPIC).
     * @return This settings instance for chaining.
     */
    public ChainItemSettings rarity(Rarity rarity) {
        if (rarity == null) {
            throw new IllegalArgumentException("Rarity cannot be null");
        }
        this.rarity = rarity;
        return this;
    }

    /**
     * Attaches a food component, making this item edible.
     * <p>
     * <b>Fabric/NeoForge mapping:</b> {@code food(foodComponent)}
     * </p>
     *
     * @param foodComponent The food component configuring hunger, saturation, and status effects.
     * @return This settings instance for chaining.
     */
    public ChainItemSettings food(FoodComponent foodComponent) {
        this.foodComponent = foodComponent;
        return this;
    }

    /**
     * Sets the recipe remainder item (e.g., leaving a bucket behind after using milk).
     * <p>
     * <b>Fabric/NeoForge mapping:</b> {@code recipeRemainder(recipeRemainder)}
     * </p>
     *
     * @param recipeRemainder The item returned to the player after crafting.
     * @return This settings instance for chaining.
     */
    public ChainItemSettings recipeRemainder(Item recipeRemainder) {
        this.recipeRemainder = recipeRemainder;
        return this;
    }

    /**
     * Binds this item to a specific creative tab.
     * <p>
     * <i>Note:</i> In vanilla Minecraft, items are registered to creative tabs via tab injection.
     * Under the hood, ChainLoader registers this item to the specified creative tab automatically:
     * <ul>
     *   <li><b>Fabric:</b> Intercepted and injected via {@code ItemGroupEvents.modifyEntriesEvent(tabKey).register(...)}.</li>
     *   <li><b>NeoForge:</b> Intercepted and injected via {@code BuildCreativeModeTabContentsEvent}.</li>
     * </ul>
     * </p>
     *
     * @param creativeTab The resource location of the target creative tab.
     * @return This settings instance for chaining.
     */
    public ChainItemSettings creativeTab(ResourceLocation creativeTab) {
        this.creativeTab = creativeTab;
        return this;
    }

    /**
     * Gets the max count value.
     *
     * @return The max stack size.
     */
    public int getMaxCount() {
        return maxCount;
    }

    /**
     * Gets the max durability value.
     *
     * @return The max durability.
     */
    public int getMaxDamage() {
        return maxDamage;
    }

    /**
     * Gets the rarity value.
     *
     * @return The rarity.
     */
    public Rarity getRarity() {
        return rarity;
    }

    /**
     * Gets the food component.
     *
     * @return The food component, or null if not food.
     */
    public FoodComponent getFoodComponent() {
        return foodComponent;
    }

    /**
     * Gets the recipe remainder item.
     *
     * @return The recipe remainder, or null if none.
     */
    public Item getRecipeRemainder() {
        return recipeRemainder;
    }

    /**
     * Gets the creative tab resource location.
     *
     * @return The creative tab location, or null if none.
     */
    public ResourceLocation getCreativeTab() {
        return creativeTab;
    }
}
