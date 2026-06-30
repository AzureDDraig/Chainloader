package net.minecraftforge.registries;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import java.util.Iterator;

public class ForgeRegistryWrapper<T> implements IForgeRegistry<T> {
    private final ResourceKey<? extends Registry<T>> registryKey;
    private Registry<T> registry;

    @SuppressWarnings("unchecked")
    public ForgeRegistryWrapper(ResourceKey<?> registryKey) {
        this.registryKey = (ResourceKey<? extends Registry<T>>) registryKey;
    }

    @SuppressWarnings("unchecked")
    private Registry<T> getRegistry() {
        if (registry != null) {
            return registry;
        }
        try {
            if (net.minecraft.core.registries.BuiltInRegistries.WRITABLE_REGISTRY != null) {
                Registry<T> reg = (Registry<T>) net.minecraft.core.registries.BuiltInRegistries.WRITABLE_REGISTRY.get((ResourceKey) registryKey);
                if (reg != null) {
                    registry = reg;
                    return reg;
                }
            }
        } catch (Throwable t) {
            // Ignore and fallback
        }
        try {
            Class<?> builtinClass = Class.forName("net.minecraft.core.registries.BuiltInRegistries");
            for (java.lang.reflect.Field field : builtinClass.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                    field.setAccessible(true);
                    Object val = field.get(null);
                    if (val instanceof Registry) {
                        Registry<?> root = (Registry<?>) val;
                        if (root.key().equals(registryKey)) {
                            registry = (Registry<T>) root;
                            return registry;
                        }
                        if ("minecraft:root".equals(root.key().location().toString())) {
                            Registry<T> reg = (Registry<T>) ((Registry) root).get((ResourceKey) registryKey);
                            if (reg != null) {
                                registry = reg;
                                return reg;
                            }
                        }
                    }
                }
            }
        } catch (Throwable t) {
            // Ignore
        }
        return null;
    }

    @Override
    public void register(T value) {
    }

    @Override
    @SuppressWarnings("unchecked")
    public void registerAll(T... values) {
    }

    @Override
    public ResourceLocation getKey(T value) {
        Registry<T> reg = getRegistry();
        return reg != null ? reg.getKey(value) : null;
    }

    @Override
    public Iterator<T> iterator() {
        Registry<T> reg = getRegistry();
        return reg != null ? reg.iterator() : java.util.Collections.emptyIterator();
    }
}
