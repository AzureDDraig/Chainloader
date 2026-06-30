package net.minecraftforge.common.util;

@FunctionalInterface
public interface NonNullFunction<T, R> {
    R apply(T t);
}
