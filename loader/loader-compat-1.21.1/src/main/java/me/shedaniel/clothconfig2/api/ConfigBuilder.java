package me.shedaniel.clothconfig2.api;

import me.shedaniel.clothconfig2.impl.builders.*;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.text.Text;
import net.chainloader.loader.compat.lib.ConfigLibShim;

import java.util.*;

/**
 * Compatibility shim class for Cloth Config's ConfigBuilder.
 */
public class ConfigBuilder {
    private Object title;
    private String titleString = "Mod Config";
    private Screen parentScreen;
    private final Map<String, ConfigCategory> categories = new LinkedHashMap<>();
    private Runnable savingRunnable;

    public static ConfigBuilder create() {
        return new ConfigBuilder();
    }

    private ConfigBuilder() {}

    public ConfigBuilder setParentScreen(Screen parent) {
        this.parentScreen = parent;
        return this;
    }

    public ConfigBuilder setTitle(Text title) {
        this.title = title;
        this.titleString = title.getString();
        return this;
    }

    public ConfigBuilder setTitle(Component title) {
        this.title = title;
        this.titleString = title.getString();
        return this;
    }

    public ConfigCategory getOrCreateCategory(Text categoryName) {
        return getOrCreateCategory(categoryName.getString());
    }

    public ConfigCategory getOrCreateCategory(Component categoryName) {
        return getOrCreateCategory(categoryName.getString());
    }

    private ConfigCategory getOrCreateCategory(String name) {
        return categories.computeIfAbsent(name, ConfigCategory::new);
    }

    public ConfigEntryBuilder entryBuilder() {
        return ConfigEntryBuilder.create();
    }

    public void setSavingRunnable(Runnable savingRunnable) {
        this.savingRunnable = savingRunnable;
    }

    public Runnable getSavingRunnable() {
        return savingRunnable;
    }

    public List<ConfigCategory> getCategories() {
        return new ArrayList<>(categories.values());
    }

    @SuppressWarnings("unchecked")
    public Screen build() {
        List<ConfigLibShim.ConfigOption> options = new ArrayList<>();

        for (ConfigCategory category : categories.values()) {
            for (AbstractConfigListEntry<?> entry : category.getEntries()) {
                if (entry instanceof ClothConfigListEntry) {
                    ClothConfigListEntry<?> e = (ClothConfigListEntry<?>) entry;
                    
                    Class<?> type = e.getValue() != null ? e.getValue().getClass() : Object.class;
                    if (type == Object.class && e.getDefaultValue().isPresent()) {
                        type = e.getDefaultValue().get().getClass();
                    }

                    ConfigLibShim.ConfigOption opt = new ConfigLibShim.ConfigOption(
                        category.getName(),
                        e.getFieldName(),
                        type,
                        e.getDefaultValue().orElse(null),
                        e.getValue(),
                        val -> ((ClothConfigListEntry<Object>) e).setValue(val),
                        e.getTooltipText()
                    );

                    if (e.getMin() != null) opt.setMin(e.getMin());
                    if (e.getMax() != null) opt.setMax(e.getMax());

                    options.add(opt);
                }
            }
        }

        // Register to the central ConfigLibShim
        ConfigLibShim.getInstance().registerConfig(titleString, this, options);

        // Return compile-friendly Screen instance
        return new Screen(Component.literal(titleString));
    }
}
