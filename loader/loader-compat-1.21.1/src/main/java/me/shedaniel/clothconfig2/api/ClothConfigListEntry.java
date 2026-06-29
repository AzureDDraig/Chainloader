package me.shedaniel.clothconfig2.api;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * Concrete implementation of AbstractConfigListEntry holding the configuration values and callbacks.
 */
public class ClothConfigListEntry<T> extends AbstractConfigListEntry<T> {
    private final String fieldName;
    private final String categoryName;
    private final T defaultValue;
    private T currentValue;
    private final Consumer<T> saveConsumer;
    private final String tooltipText;
    private Object min;
    private Object max;

    public ClothConfigListEntry(String categoryName, String fieldName, T defaultValue, T currentValue, Consumer<T> saveConsumer, String tooltipText) {
        this.categoryName = categoryName;
        this.fieldName = fieldName;
        this.defaultValue = defaultValue;
        this.currentValue = currentValue;
        this.saveConsumer = saveConsumer;
        this.tooltipText = tooltipText;
    }

    @Override
    public String getFieldName() {
        return fieldName;
    }

    @Override
    public String getCategoryName() {
        return categoryName;
    }

    @Override
    public T getValue() {
        return currentValue;
    }

    public void setValue(T val) {
        this.currentValue = val;
    }

    @Override
    public Optional<T> getDefaultValue() {
        return Optional.ofNullable(defaultValue);
    }

    @Override
    public String getTooltipText() {
        return tooltipText;
    }

    public Object getMin() {
        return min;
    }

    public void setMin(Object min) {
        this.min = min;
    }

    public Object getMax() {
        return max;
    }

    public void setMax(Object max) {
        this.max = max;
    }

    @Override
    public void save() {
        if (saveConsumer != null) {
            saveConsumer.accept(currentValue);
        }
    }
}
