package me.shedaniel.clothconfig2.api;

import me.shedaniel.clothconfig2.impl.builders.*;
import net.minecraft.network.chat.Component;
import net.minecraft.text.Text;

/**
 * Compatibility shim class for Cloth Config's ConfigEntryBuilder.
 */
public class ConfigEntryBuilder {
    private static final ConfigEntryBuilder INSTANCE = new ConfigEntryBuilder();

    public static ConfigEntryBuilder create() {
        return INSTANCE;
    }

    private ConfigEntryBuilder() {}

    // Boolean Toggle
    public BooleanToggleBuilder startBooleanToggle(Text name, boolean value) {
        return new BooleanToggleBuilder(name, value);
    }
    public BooleanToggleBuilder startBooleanToggle(Component name, boolean value) {
        return new BooleanToggleBuilder(name, value);
    }

    // String field
    public StringFieldBuilder startStrField(Text name, String value) {
        return new StringFieldBuilder(name, value);
    }
    public StringFieldBuilder startStrField(Component name, String value) {
        return new StringFieldBuilder(name, value);
    }

    // Int field
    public IntFieldBuilder startIntField(Text name, int value) {
        return new IntFieldBuilder(name, value);
    }
    public IntFieldBuilder startIntField(Component name, int value) {
        return new IntFieldBuilder(name, value);
    }

    // Long field
    public LongFieldBuilder startLongField(Text name, long value) {
        return new LongFieldBuilder(name, value);
    }
    public LongFieldBuilder startLongField(Component name, long value) {
        return new LongFieldBuilder(name, value);
    }

    // Double field
    public DoubleFieldBuilder startDoubleField(Text name, double value) {
        return new DoubleFieldBuilder(name, value);
    }
    public DoubleFieldBuilder startDoubleField(Component name, double value) {
        return new DoubleFieldBuilder(name, value);
    }

    // Float field
    public FloatFieldBuilder startFloatField(Text name, float value) {
        return new FloatFieldBuilder(name, value);
    }
    public FloatFieldBuilder startFloatField(Component name, float value) {
        return new FloatFieldBuilder(name, value);
    }

    // Enum selector
    public <T extends Enum<T>> EnumListBuilder<T> startEnumSelector(Text name, Class<T> type, T value) {
        return new EnumListBuilder<>(name, type, value);
    }
    public <T extends Enum<T>> EnumListBuilder<T> startEnumSelector(Component name, Class<T> type, T value) {
        return new EnumListBuilder<>(name, type, value);
    }
}
