package me.shedaniel.clothconfig2.impl.builders;

import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import me.shedaniel.clothconfig2.api.ClothConfigListEntry;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Compatibility shim class for Cloth Config's BooleanToggleBuilder.
 */
public class BooleanToggleBuilder {
    private final String label;
    private final boolean value;
    private boolean defaultValue;
    private Consumer<Boolean> saveConsumer;
    private String tooltip;

    public BooleanToggleBuilder(Object name, boolean value) {
        this.label = getComponentString(name);
        this.value = value;
    }

    public BooleanToggleBuilder setDefaultValue(boolean defaultValue) {
        this.defaultValue = defaultValue;
        return this;
    }

    public BooleanToggleBuilder setSaveConsumer(Consumer<Boolean> saveConsumer) {
        this.saveConsumer = saveConsumer;
        return this;
    }

    public BooleanToggleBuilder setTooltip(Object... tooltip) {
        if (tooltip != null && tooltip.length > 0) {
            this.tooltip = getComponentString(tooltip[0]);
        }
        return this;
    }

    public BooleanToggleBuilder setErrorSupplier(Function<Boolean, Optional<Object>> errorSupplier) {
        return this;
    }

    public BooleanToggleBuilder setRequirement(Supplier<Boolean> requirement) {
        return this;
    }

    public AbstractConfigListEntry<Boolean> build() {
        return new ClothConfigListEntry<>(null, label, defaultValue, value, saveConsumer, tooltip);
    }

    private static String getComponentString(Object component) {
        if (component == null) return "";
        if (component instanceof String) return (String) component;
        try {
            java.lang.reflect.Method getString = component.getClass().getMethod("getString");
            return (String) getString.invoke(component);
        } catch (Exception e) {
            return component.toString();
        }
    }
}
