package net.minecraftforge.registries;

import java.util.function.Supplier;

public class RegistryObject<T> implements Supplier<T> {
    private final Supplier<? extends T> supplier;

    public RegistryObject(Supplier<? extends T> supplier) {
        this.supplier = supplier;
    }

    @Override
    public T get() {
        return supplier.get();
    }
}
