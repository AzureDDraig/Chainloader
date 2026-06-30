package net.chainloader.loader.core.world;

public interface ChainWorldData {
    void read(Object nbt);
    Object write(Object nbt);
}
