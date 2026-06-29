package net.minecraftforge.common;

public class ForgeConfigSpec implements net.minecraftforge.fml.config.IConfigSpec {
    public static class ConfigValue<T> {
        private final T defaultValue;
        public ConfigValue(T defaultValue) {
            this.defaultValue = defaultValue;
        }
        public T get() {
            return defaultValue;
        }
    }
    
    public static class BooleanValue extends ConfigValue<Boolean> {
        public BooleanValue(Boolean val) {
            super(val);
        }
    }
    
    public static class IntValue extends ConfigValue<Integer> {
        public IntValue(Integer val) {
            super(val);
        }
    }
    
    public static class DoubleValue extends ConfigValue<Double> {
        public DoubleValue(Double val) {
            super(val);
        }
    }

    public static class EnumValue<T extends java.lang.Enum<T>> extends ConfigValue<T> {
        public EnumValue(T val) {
            super(val);
        }
    }

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
}
