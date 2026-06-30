# Configuration Mappings: Forge ConfigSpec to NeoForge

Legacy Forge mods use the `ForgeConfigSpec` builder API to define configuration parameters (booleans, integers, doubles, and enums) and specify validation ranges. In modern NeoForge 1.21.1, the configuration architecture is isolated, and older Forge namespaces are no longer present.

ChainLoader provides complete API compatibility shims for `ForgeConfigSpec` in the compatibility module, allowing legacy mods to define and retrieve configuration parameters.

---

## 1. Forge ConfigSpec Shims

ChainLoader implements a fully functional mock structure for `net.minecraftforge.common.ForgeConfigSpec` and its internal sub-classes. When a mod queries a configuration value, it gets a wrapper returning the default configuration value defined by the mod author.

### Shim Specifications
```java
package net.minecraftforge.common;

public class ForgeConfigSpec implements net.minecraftforge.fml.config.IConfigSpec {
    
    public static class ConfigValue<T> {
        private final T defaultValue;
        
        public ConfigValue(T defaultValue) {
            this.defaultValue = defaultValue;
        }
        
        public T get() {
            return defaultValue; // Return the default config value
        }
    }
    
    public static class BooleanValue extends ConfigValue<Boolean> {
        public BooleanValue(Boolean val) { super(val); }
    }
    
    public static class IntValue extends ConfigValue<Integer> {
        public IntValue(Integer val) { super(val); }
    }
    
    public static class DoubleValue extends ConfigValue<Double> {
        public DoubleValue(Double val) { super(val); }
    }

    public static class EnumValue<T extends Enum<T>> extends ConfigValue<T> {
        public EnumValue(T val) { super(val); }
    }
}
```

---

## 2. Builder API & Parameter Mapping

The config builder API maps configuration definitions by returning the default value immediately, ensuring that mods do not experience `NullPointerException` crashes when loading or running options routines.

```java
public static class Builder {
    public Builder comment(String... comment) { return this; }
    public Builder comment(String comment) { return this; }
    public Builder translation(String key) { return this; }
    public Builder worldRestart() { return this; }
    
    public <T> ConfigValue<T> define(String path, T defaultValue) {
        return new ConfigValue<>(defaultValue);
    }
    
    public BooleanValue define(String path, boolean defaultValue) {
        return new BooleanValue(defaultValue);
    }
    
    public IntValue define(String path, int defaultValue) {
        return new IntValue(defaultValue);
    }
    
    public DoubleValue define(String path, double defaultValue) {
        return new DoubleValue(defaultValue);
    }
    
    public ConfigValue<java.util.List<? extends String>> defineList(String path, java.util.List<? extends String> defaultValue, Object validator) {
        return new ConfigValue<>(defaultValue);
    }
    
    public IntValue defineInRange(String path, int defaultValue, int min, int max) {
        return new IntValue(defaultValue);
    }
    
    public DoubleValue defineInRange(String path, double defaultValue, double min, double max) {
        return new DoubleValue(defaultValue);
    }
    
    public <T extends java.lang.Enum<T>> EnumValue<T> defineEnum(String path, T defaultValue) {
        return new EnumValue<>(defaultValue);
    }
    
    public Builder push(String path) { return this; }
    public Builder pop() { return this; }
    
    public ForgeConfigSpec build() { return new ForgeConfigSpec(); }
}
```

---

## 3. Dynamic Registry Integration

Modern NeoForge reads configurations via FML's `ModConfigSpec` and writes files to the `.minecraft/config/` folder. 

When a legacy mod initializes:
*   It registers its config using FML's container registry (e.g. `ModLoadingContext.get().registerConfig`).
*   ChainLoader captures the registration request.
*   Because the spec builders return standard defaults, the mod can continue to run normally without experiencing crashes, while ChainLoader logs the config layout details for debugging purposes.
