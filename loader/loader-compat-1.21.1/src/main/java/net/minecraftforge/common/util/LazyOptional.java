package net.minecraftforge.common.util;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class LazyOptional<T> {
    private final Supplier<T> supplier;
    private T value;
    private boolean resolved;

    private LazyOptional(Supplier<T> supplier) {
        this.supplier = supplier;
    }

    public static <T> LazyOptional<T> of(Supplier<T> supplier) {
        return new LazyOptional<>(supplier);
    }

    private static final LazyOptional<?> EMPTY = new LazyOptional<>(() -> null);
    
    @SuppressWarnings("unchecked")
    public static <T> LazyOptional<T> empty() {
        return (LazyOptional<T>) EMPTY;
    }

    public boolean isPresent() {
        if (!resolved && supplier != null) {
            value = supplier.get();
            resolved = true;
        }
        return value != null;
    }

    public T orElseThrow() {
        if (!isPresent()) {
            throw new IllegalStateException("LazyOptional is empty");
        }
        return value;
    }

    public T orElse(T other) {
        return isPresent() ? value : other;
    }

    public void ifPresent(Consumer<? super T> action) {
        if (isPresent()) {
            action.accept(value);
        }
    }

    public <U> LazyOptional<U> map(Function<? super T, ? extends U> mapper) {
        return isPresent() ? LazyOptional.of(() -> mapper.apply(value)) : empty();
    }
}
