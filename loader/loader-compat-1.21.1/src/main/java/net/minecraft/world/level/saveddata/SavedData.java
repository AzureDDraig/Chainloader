package net.minecraft.world.level.saveddata;

import net.minecraft.nbt.CompoundTag;

public abstract class SavedData {
    public abstract CompoundTag save(CompoundTag nbt);

    public static class Factory<T extends SavedData> {
        public Factory(java.util.function.Supplier<T> constructor,
                       java.util.function.BiFunction<CompoundTag, Object, T> reader,
                       net.minecraft.util.datafix.DataFixTypes dataFixType) {}
    }
}
