package net.neoforged.neoforge.registries;

import java.util.function.Supplier;

public class DeferredHolder<T, I extends T> implements Supplier<I> {
    private final Supplier<? extends I> supplier;

    public DeferredHolder(Supplier<? extends I> supplier) {
        this.supplier = supplier;
    }

    @Override
    public I get() {
        return supplier.get();
    }
}
