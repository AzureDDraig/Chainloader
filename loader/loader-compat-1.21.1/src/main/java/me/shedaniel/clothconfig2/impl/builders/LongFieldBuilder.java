package me.shedaniel.clothconfig2.impl.builders;

import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import me.shedaniel.clothconfig2.api.ClothConfigListEntry;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Compatibility shim class for Cloth Config's LongFieldBuilder.
 */
public class LongFieldBuilder {
    private final String label;
    private final long value;
    private long defaultValue;
    private Consumer<Long> saveConsumer;
    private String tooltip;
    private Long min;
    private Long max;

    public LongFieldBuilder(Object name, long value) {
        this.label = getComponentString(name);
        this.value = value;
    }

    public LongFieldBuilder setDefaultValue(long defaultValue) {
        this.defaultValue = defaultValue;
        return this;
    }

    public LongFieldBuilder setSaveConsumer(Consumer<Long> saveConsumer) {
        this.saveConsumer = saveConsumer;
        return this;
    }

    public LongFieldBuilder setTooltip(Object... tooltip) {
        if (tooltip != null && tooltip.length > 0) {
            this.tooltip = getComponentString(tooltip[0]);
        }
        return this;
    }

    public LongFieldBuilder setMin(long min) {
        this.min = min;
        return this;
    }

    public LongFieldBuilder setMax(long max) {
        this.max = max;
        return this;
    }

    public LongFieldBuilder setErrorSupplier(Function<Long, Optional<Object>> errorSupplier) {
        return this;
    }

    public AbstractConfigListEntry<Long> build() {
        ClothConfigListEntry<Long> entry = new ClothConfigListEntry<>(null, label, defaultValue, value, saveConsumer, tooltip);
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
