package net.minecraftforge.common.util;

@FunctionalInterface
public interface NonNullConsumer<T> {
    void accept(T t);
}
