package net.minecraftforge.registries;

import net.minecraft.resources.ResourceLocation;
import java.util.Iterator;

public interface IForgeRegistry<T> extends Iterable<T> {
    void register(T value);
    @SuppressWarnings("unchecked")
    void registerAll(T... values);
    ResourceLocation getKey(T value);
}
