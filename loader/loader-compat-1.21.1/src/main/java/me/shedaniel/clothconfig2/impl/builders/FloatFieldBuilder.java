package me.shedaniel.clothconfig2.impl.builders;

import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import me.shedaniel.clothconfig2.api.ClothConfigListEntry;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Compatibility shim class for Cloth Config's FloatFieldBuilder.
 */
public class FloatFieldBuilder {
    private final String label;
    private final float value;
    private float defaultValue;
    private Consumer<Float> saveConsumer;
    private String tooltip;
    private Float min;
    private Float max;

    public FloatFieldBuilder(Object name, float value) {
        this.label = getComponentString(name);
        this.value = value;
    }

    public FloatFieldBuilder setDefaultValue(float defaultValue) {
        this.defaultValue = defaultValue;
        return this;
    }

    public FloatFieldBuilder setSaveConsumer(Consumer<Float> saveConsumer) {
        this.saveConsumer = saveConsumer;
        return this;
    }

    public FloatFieldBuilder setTooltip(Object... tooltip) {
        if (tooltip != null && tooltip.length > 0) {
            this.tooltip = getComponentString(tooltip[0]);
        }
        return this;
    }

    public FloatFieldBuilder setMin(float min) {
        this.min = min;
        return this;
    }

    public FloatFieldBuilder setMax(float max) {
        this.max = max;
        return this;
    }

    public FloatFieldBuilder setErrorSupplier(Function<Float, Optional<Object>> errorSupplier) {
        return this;
    }

    public AbstractConfigListEntry<Float> build() {
        ClothConfigListEntry<Float> entry = new ClothConfigListEntry<>(null, label, defaultValue, value, saveConsumer, tooltip);
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
