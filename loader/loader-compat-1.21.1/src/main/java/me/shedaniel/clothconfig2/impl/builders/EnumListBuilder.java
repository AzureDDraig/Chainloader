package me.shedaniel.clothconfig2.impl.builders;

import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import me.shedaniel.clothconfig2.api.ClothConfigListEntry;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Compatibility shim class for Cloth Config's EnumListBuilder.
 */
public class EnumListBuilder<T extends Enum<T>> {
    private final String label;
    private final Class<T> type;
    private final T value;
    private T defaultValue;
    private Consumer<T> saveConsumer;
    private String tooltip;

    public EnumListBuilder(Object name, Class<T> type, T value) {
        this.label = getComponentString(name);
        this.type = type;
        this.value = value;
    }

    public EnumListBuilder<T> setDefaultValue(T defaultValue) {
        this.defaultValue = defaultValue;
        return this;
    }

    public EnumListBuilder<T> setSaveConsumer(Consumer<T> saveConsumer) {
        this.saveConsumer = saveConsumer;
        return this;
    }

    public EnumListBuilder<T> setTooltip(Object... tooltip) {
        if (tooltip != null && tooltip.length > 0) {
            this.tooltip = getComponentString(tooltip[0]);
        }
        return this;
    }

    public EnumListBuilder<T> setEnumNameProvider(Function<T, Object> enumNameProvider) {
        return this;
    }

    public EnumListBuilder<T> setErrorSupplier(Function<T, Optional<Object>> errorSupplier) {
        return this;
    }

    public AbstractConfigListEntry<T> build() {
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
