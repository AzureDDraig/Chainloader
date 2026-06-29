package net.minecraft.client;

public class OptionInstance<T> {
    private T value;

    public OptionInstance(T defaultValue) {
        this.value = defaultValue;
    }

    public T get() {
        return value;
    }

    public void set(T value) {
        this.value = value;
    }
}
