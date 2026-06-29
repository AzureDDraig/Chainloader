package net.chainloader.api.creative;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

/**
 * Platform-agnostic builder and manager for Minecraft Creative Tabs (ItemGroups).
 * <p>
 * This class allows registering custom creative tabs and injecting items dynamically
 * into existing tabs (both vanilla and custom).
 * </p>
 *
 * <h3>Under-the-Hood Mappings:</h3>
 * <ul>
 *   <li><b>Fabric Custom Tab:</b> Built via {@code FabricItemGroup.builder()} and registered in the ItemGroup registry.</li>
 *   <li><b>NeoForge Custom Tab:</b> Registered on the {@code DeferredRegister} for {@code Registries.CREATIVE_MODE_TAB}.</li>
 *   <li><b>Fabric Dynamic Injection:</b> Uses {@code ItemGroupEvents.modifyEntriesEvent(tabKey).register(entries -> ...)}.</li>
 *   <li><b>NeoForge Dynamic Injection:</b> Uses the {@code BuildCreativeModeTabContentsEvent} event.</li>
 * </ul>
 */
public final class ChainCreativeTab {

    private static final List<ChainCreativeTab> CUSTOM_TABS = new ArrayList<>();
    private static final Map<ResourceKey<ItemGroup>, List<Consumer<List<ItemStack>>>> TAB_MODIFIERS = new HashMap<>();

    private final ResourceLocation id;
    private final Supplier<ItemStack> iconSupplier;
    private final Text displayName;
    private final List<Supplier<ItemStack>> items;

    private ChainCreativeTab(Builder builder) {
        this.id = builder.id;
        this.iconSupplier = builder.iconSupplier;
        this.displayName = builder.displayName;
        this.items = new ArrayList<>(builder.items);
    }

    public ResourceLocation getId() {
        return id;
    }

    public Supplier<ItemStack> getIconSupplier() {
        return iconSupplier;
    }

    public Text getDisplayName() {
        return displayName;
    }

    public List<Supplier<ItemStack>> getItems() {
        return items;
    }

    /**
     * Registers a consumer to modify an existing creative tab.
     * <p>
     * Modders call this to dynamically inject items into standard tabs (like building blocks, ingredients, etc.).
     * </p>
     *
     * @param tabKey   The registry key of the item group to modify.
     * @param modifier The callback to add items to the item group.
     */
    public static void modify(ResourceKey<ItemGroup> tabKey, Consumer<List<ItemStack>> modifier) {
        if (tabKey == null || modifier == null) {
            throw new IllegalArgumentException("Arguments cannot be null");
        }
        TAB_MODIFIERS.computeIfAbsent(tabKey, k -> new ArrayList<>()).add(modifier);
        
        // Under the hood:
        // Fabric: ItemGroupEvents.modifyEntriesEvent(tabKey).register(entries -> {
        //             List<ItemStack> list = new ArrayList<>();
        //             modifier.accept(list);
        //             for (ItemStack stack : list) entries.add(stack);
        //         });
        // NeoForge: Event listener hooks BuildCreativeModeTabContentsEvent and does:
        //           if (event.getTabKey() == tabKey) {
        //               List<ItemStack> list = new ArrayList<>();
        //               modifier.accept(list);
        //               for (ItemStack stack : list) event.accept(stack);
        //           }
    }

    /**
     * Retrieves all custom creative tabs registered.
     * Used internally by platform specific bootloaders.
     *
     * @return The list of registered custom creative tabs.
     */
    public static List<ChainCreativeTab> getCustomTabs() {
        return CUSTOM_TABS;
    }

    /**
     * Retrieves all tab modification listeners.
     * Used internally by platform specific bootloaders.
     *
     * @return The map of registered item modifiers.
     */
    public static Map<ResourceKey<ItemGroup>, List<Consumer<List<ItemStack>>>> getTabModifiers() {
        return TAB_MODIFIERS;
    }

    /**
     * Builder class to construct custom {@link ChainCreativeTab} instances.
     */
    public static class Builder {
        private final ResourceLocation id;
        private Supplier<ItemStack> iconSupplier = () -> ItemStack.EMPTY;
        private Text displayName = Text.literal("Unnamed Tab");
        private final List<Supplier<ItemStack>> items = new ArrayList<>();

        private Builder(ResourceLocation id) {
            this.id = id;
        }

        /**
         * Creates a new builder for a custom creative tab.
         *
         * @param id The resource location identifier.
         * @return A new Builder.
         */
        public static Builder create(ResourceLocation id) {
            if (id == null) {
                throw new IllegalArgumentException("ResourceLocation cannot be null");
            }
            return new Builder(id);
        }

        /**
         * Sets the icon of the creative tab.
         *
         * @param iconSupplier A supplier that returns the ItemStack representing the icon.
         * @return This builder for chaining.
         */
        public Builder icon(Supplier<ItemStack> iconSupplier) {
            if (iconSupplier == null) {
                throw new IllegalArgumentException("Icon supplier cannot be null");
            }
            this.iconSupplier = iconSupplier;
            return this;
        }

        /**
         * Sets the display name (title text) of the creative tab.
         *
         * @param displayName The text to display when the user hovers over/views the tab.
         * @return This builder for chaining.
         */
        public Builder displayName(Text displayName) {
            if (displayName == null) {
                throw new IllegalArgumentException("Display name cannot be null");
            }
            this.displayName = displayName;
            return this;
        }

        /**
         * Adds an item stack to this custom tab.
         *
         * @param itemStackSupplier Supplier of the ItemStack to add.
         * @return This builder for chaining.
         */
        public Builder add(Supplier<ItemStack> itemStackSupplier) {
            if (itemStackSupplier == null) {
                throw new IllegalArgumentException("ItemStack supplier cannot be null");
            }
            this.items.add(itemStackSupplier);
            return this;
        }

        /**
         * Builds and registers the custom creative tab.
         *
         * @return The registered ChainCreativeTab instance.
         */
        public ChainCreativeTab register() {
            ChainCreativeTab tab = new ChainCreativeTab(this);
            CUSTOM_TABS.add(tab);
            return tab;
        }
    }
}
