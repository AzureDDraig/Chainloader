package me.shedaniel.clothconfig2.api;

import java.util.Optional;

/**
 * Compatibility shim class for Cloth Config's AbstractConfigListEntry.
 */
public abstract class AbstractConfigListEntry<T> {
    
    public abstract String getFieldName();
    
    public abstract String getCategoryName();

    public abstract T getValue();

    public abstract Optional<T> getDefaultValue();

    public abstract void save();
    
    public abstract String getTooltipText();
}
