package me.shedaniel.clothconfig2.impl.builders;

import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import me.shedaniel.clothconfig2.api.ClothConfigListEntry;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Compatibility shim class for Cloth Config's DoubleFieldBuilder.
 */
public class DoubleFieldBuilder {
    private final String label;
    private final double value;
    private double defaultValue;
    private Consumer<Double> saveConsumer;
    private String tooltip;
    private Double min;
    private Double max;

    public DoubleFieldBuilder(Object name, double value) {
        this.label = getComponentString(name);
        this.value = value;
    }

    public DoubleFieldBuilder setDefaultValue(double defaultValue) {
        this.defaultValue = defaultValue;
        return this;
    }

    public DoubleFieldBuilder setSaveConsumer(Consumer<Double> saveConsumer) {
        this.saveConsumer = saveConsumer;
        return this;
    }

    public DoubleFieldBuilder setTooltip(Object... tooltip) {
        if (tooltip != null && tooltip.length > 0) {
            this.tooltip = getComponentString(tooltip[0]);
        }
        return this;
    }

    public DoubleFieldBuilder setMin(double min) {
        this.min = min;
        return this;
    }

    public DoubleFieldBuilder setMax(double max) {
        this.max = max;
        return this;
    }

    public DoubleFieldBuilder setErrorSupplier(Function<Double, Optional<Object>> errorSupplier) {
        return this;
    }

    public AbstractConfigListEntry<Double> build() {
        ClothConfigListEntry<Double> entry = new ClothConfigListEntry<>(null, label, defaultValue, value, saveConsumer, tooltip);
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
