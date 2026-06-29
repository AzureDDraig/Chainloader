package me.shedaniel.clothconfig2.impl.builders;

import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import me.shedaniel.clothconfig2.api.ClothConfigListEntry;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Compatibility shim class for Cloth Config's StringFieldBuilder.
 */
public class StringFieldBuilder {
    private final String label;
    private final String value;
    private String defaultValue;
    private Consumer<String> saveConsumer;
    private String tooltip;

    public StringFieldBuilder(Object name, String value) {
        this.label = getComponentString(name);
        this.value = value;
    }

    public StringFieldBuilder setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
        return this;
    }

    public StringFieldBuilder setSaveConsumer(Consumer<String> saveConsumer) {
        this.saveConsumer = saveConsumer;
        return this;
    }

    public StringFieldBuilder setTooltip(Object... tooltip) {
        if (tooltip != null && tooltip.length > 0) {
            this.tooltip = getComponentString(tooltip[0]);
        }
        return this;
    }

    public StringFieldBuilder setErrorSupplier(Function<String, Optional<Object>> errorSupplier) {
        return this;
    }

    public AbstractConfigListEntry<String> build() {
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
