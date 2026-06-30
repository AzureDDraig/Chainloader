package net.minecraft.core.component;

import java.util.Set;

public interface DataComponentMap {
    DataComponentMap EMPTY = null;

    <T> T get(DataComponentType<? extends T> type);
    boolean has(DataComponentType<?> type);
    Set<DataComponentType<?>> keySet();
}
