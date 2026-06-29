package me.shedaniel.clothconfig2.api;

import java.util.ArrayList;
import java.util.List;

/**
 * Compatibility shim class for Cloth Config's ConfigCategory.
 */
public class ConfigCategory {
    private final String name;
    private final List<AbstractConfigListEntry<?>> entries = new ArrayList<>();

    public ConfigCategory(Object name) {
        this.name = name != null ? getComponentString(name) : "General";
    }

    public String getName() {
        return name;
    }

    public ConfigCategory addEntry(AbstractConfigListEntry<?> entry) {
        if (entry != null) {
            entries.add(entry);
        }
        return this;
    }

    public void removeEntry(AbstractConfigListEntry<?> entry) {
        entries.remove(entry);
    }

    @SuppressWarnings("rawtypes")
    public List<AbstractConfigListEntry> getEntries() {
        return new ArrayList<>(entries);
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
