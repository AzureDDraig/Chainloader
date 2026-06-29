package me.shedaniel.rei.api.common.entry;

/**
 * Mockup of REI's EntryStack interface.
 */
public interface EntryStack<T> {
    T getValue();
    Class<T> getType();

    static <T> EntryStack<T> of(T value) {
        return new EntryStack<T>() {
            @Override
            public T getValue() { return value; }
            @SuppressWarnings("unchecked")
            @Override
            public Class<T> getType() { return (Class<T>) value.getClass(); }
        };
    }
}
