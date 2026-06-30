package net.minecraftforge.common.util;

@FunctionalInterface
public interface NonNullSupplier<T> {
    T get();
}
