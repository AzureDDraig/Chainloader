package com.tterrag.registrate.util.nullness;

@FunctionalInterface
public interface NonNullFunction<T, R> {
    R apply(T t);
}
