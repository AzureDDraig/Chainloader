package net.neoforged.neoforge.registries;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.neoforged.bus.api.IEventBus;

public class DeferredRegister<T> {
    private final String modId;
    private final ResourceKey<? extends Registry<T>> registryKey;
    private final Registry<T> registry;
    private final List<Entry<T>> entries = new ArrayList<>();

    private static class Entry<T> {
        final String name;
        final Supplier<? extends T> supplier;

        Entry(String name, Supplier<? extends T> supplier) {
            this.name = name;
            this.supplier = supplier;
        }
    }

    @SuppressWarnings("unchecked")
    private DeferredRegister(ResourceKey<? extends Registry<T>> registryKey, Registry<T> registry, String modId) {
        this.modId = modId;
        this.registryKey = registryKey;
        this.registry = registry;
    }

    @SuppressWarnings("unchecked")
    public static <B> DeferredRegister<B> create(ResourceKey<? extends Registry<B>> registryKey, String modId) {
        return new DeferredRegister<>((ResourceKey<? extends Registry<B>>) registryKey, null, modId);
    }

    public static <B> DeferredRegister<B> create(Registry<B> registry, String modId) {
        return new DeferredRegister<>(null, registry, modId);
    }

    public <I extends T> DeferredHolder<T, I> register(String name, Supplier<? extends I> supplier) {
        entries.add(new Entry<>(name, (Supplier<? extends T>) supplier));
        return new DeferredHolder<>(supplier);
    }

    @SuppressWarnings("unchecked")
    private static <T> Registry<T> getRegistry(ResourceKey<? extends Registry<T>> registryKey) {
        try {
            if (net.minecraft.core.registries.BuiltInRegistries.WRITABLE_REGISTRY != null) {
                Registry<T> reg = (Registry<T>) net.minecraft.core.registries.BuiltInRegistries.WRITABLE_REGISTRY.get((ResourceKey) registryKey);
                if (reg != null) {
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
                            return (Registry<T>) root;
                        }
                        if ("minecraft:root".equals(root.key().location().toString())) {
                            Registry<T> reg = (Registry<T>) ((Registry) root).get((ResourceKey) registryKey);
                            if (reg != null) {
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

    private static void unfreezeAndEnableIntrusive(Object registry, String name) {
        try {
            Class<?> clazz = registry.getClass();
            while (clazz != null && clazz != Object.class) {
                // 1. Find and set the frozen field (boolean, or named "frozen" or "l")
                java.lang.reflect.Field frozenField = null;
                try {
                    frozenField = clazz.getDeclaredField("frozen");
                } catch (NoSuchFieldException e1) {
                    try {
                        frozenField = clazz.getDeclaredField("l");
                    } catch (NoSuchFieldException e2) {
                        for (java.lang.reflect.Field f : clazz.getDeclaredFields()) {
                            if (f.getType() == boolean.class) {
                                frozenField = f;
                                break;
                            }
                        }
                    }
                }
                if (frozenField != null) {
                    try {
                        frozenField.setAccessible(true);
                        if (frozenField.getBoolean(registry)) {
                            System.out.println("[ChainLoader] Unfreezing registry " + clazz.getName() + " for " + name);
                            frozenField.setBoolean(registry, false);
                        }
                    } catch (Throwable t) {
                        // Ignore
                    }
                }

                // 2. Find and set the intrusiveHolders field (Map, not final/static, or named "intrusiveHolders" or "m")
                java.lang.reflect.Field intrusiveHoldersField = null;
                try {
                    intrusiveHoldersField = clazz.getDeclaredField("intrusiveHolders");
                } catch (NoSuchFieldException e1) {
                    try {
                        intrusiveHoldersField = clazz.getDeclaredField("m");
                    } catch (NoSuchFieldException e2) {
                        for (java.lang.reflect.Field f : clazz.getDeclaredFields()) {
                            if (java.util.Map.class.isAssignableFrom(f.getType())) {
                                int mod = f.getModifiers();
                                if (!java.lang.reflect.Modifier.isStatic(mod) && !java.lang.reflect.Modifier.isFinal(mod)) {
                                    intrusiveHoldersField = f;
                                    break;
                                }
                            }
                        }
                    }
                }
                if (intrusiveHoldersField != null) {
                    try {
                        intrusiveHoldersField.setAccessible(true);
                        Object existing = intrusiveHoldersField.get(registry);
                        if (existing == null) {
                            System.out.println("[ChainLoader] Enabling intrusive holders on registry " + clazz.getName() + " (field: " + intrusiveHoldersField.getName() + ") for " + name);
                            intrusiveHoldersField.set(registry, new java.util.IdentityHashMap<>());
                        }
                    } catch (Throwable t) {
                        // Ignore
                    }
                }
                
                clazz = clazz.getSuperclass();
            }
        } catch (Throwable t) {
            System.err.println("[ChainLoader] Error in unfreezeAndEnableIntrusive for " + name + ":");
            t.printStackTrace();
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> void registerCleanly(Registry<T> registry, net.minecraft.resources.ResourceLocation name, T value) {
        unfreezeAndEnableIntrusive(registry, name.toString());

        Registry.register(registry, name, value);

        try {
            // Also notify Fabric API port that an entry has been added
            net.chainloader.loader.compat.fabric.FabricLoaderShim.getInstance().dispatchEvent("fabric:registry_entry_added", listener -> {
                try {
                    Class<?> callbackClass = Class.forName("net.chainloader.loader.compat.bridge.RegistrySynchronizer$FabricRegistryEntryCallback");
                    java.lang.reflect.Method m = callbackClass.getMethod("onEntryAdded", String.class, String.class, Object.class);
                    m.invoke(listener, registry.key().location().toString(), name.toString(), value);
                } catch (Throwable t) {
                    // Ignore
                }
            });
        } catch (Throwable t) {
            // Ignore
        }
    }

    @SuppressWarnings("unchecked")
    public void register(IEventBus bus) {
        Registry<T> reg = null;
        if (this.registry != null) {
            reg = this.registry;
        } else if (this.registryKey != null) {
            reg = getRegistry(this.registryKey);
        }
        
        if (reg == null) {
            System.err.println("[ChainLoader] Warning: Could not resolve registry for NeoForge DeferredRegister modId=" + modId);
            return;
        }
        
        for (Entry<T> entry : entries) {
            try {
                T value = entry.supplier.get();
                net.minecraft.resources.ResourceLocation id = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(modId, entry.name);
                registerCleanly(reg, id, value);
                System.out.println("[ChainLoader] NeoForge DeferredRegister: Registered " + id + " to registry " + reg.key().location());
            } catch (Throwable t) {
                System.err.println("[ChainLoader] NeoForge DeferredRegister: Failed to register " + entry.name + " for mod " + modId + ":");
                t.printStackTrace();
            }
        }
    }
}
