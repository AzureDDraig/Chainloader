package net.minecraft.world.level.storage;

import net.minecraft.world.level.saveddata.SavedData;

public class DimensionDataStorage {
    public <T extends SavedData> T computeIfAbsent(SavedData.Factory<T> factory, String name) {
        return null;
    }

    public <T extends SavedData> T get(SavedData.Factory<T> factory, String name) {
        return null;
    }
}
