package me.shedaniel.clothconfig2.impl.builders;

import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import me.shedaniel.clothconfig2.api.ClothConfigListEntry;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Compatibility shim class for Cloth Config's IntFieldBuilder.
 */
public class IntFieldBuilder {
    private final String label;
    private final int value;
    private int defaultValue;
    private Consumer<Integer> saveConsumer;
    private String tooltip;
    private Integer min;
    private Integer max;

    public IntFieldBuilder(Object name, int value) {
        this.label = getComponentString(name);
        this.value = value;
    }

    public IntFieldBuilder setDefaultValue(int defaultValue) {
        this.defaultValue = defaultValue;
        return this;
    }

    public IntFieldBuilder setSaveConsumer(Consumer<Integer> saveConsumer) {
        this.saveConsumer = saveConsumer;
        return this;
    }

    public IntFieldBuilder setTooltip(Object... tooltip) {
        if (tooltip != null && tooltip.length > 0) {
            this.tooltip = getComponentString(tooltip[0]);
        }
        return this;
    }

    public IntFieldBuilder setMin(int min) {
        this.min = min;
        return this;
    }

    public IntFieldBuilder setMax(int max) {
        this.max = max;
        return this;
    }

    public IntFieldBuilder setErrorSupplier(Function<Integer, Optional<Object>> errorSupplier) {
        return this;
    }

    public AbstractConfigListEntry<Integer> build() {
        ClothConfigListEntry<Integer> entry = new ClothConfigListEntry<>(null, label, defaultValue, value, saveConsumer, tooltip);
        if (min != null) entry.setMin(min);
        if (max != null) entry.setMax(max);
        return entry;
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
