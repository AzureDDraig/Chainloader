package net.minecraftforge.registries;

import java.util.function.Consumer;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

public class RegisterEvent extends net.minecraftforge.eventbus.api.Event {
    public interface RegisterHelper<T> {
        void register(ResourceLocation name, T value);
    }

    private final ResourceKey<? extends Registry<?>> registryKey;

    public RegisterEvent(ResourceKey<? extends Registry<?>> registryKey) {
        this.registryKey = registryKey;
    }

    public ResourceKey<? extends Registry<?>> getRegistryKey() {
        return registryKey;
    }

    @SuppressWarnings("unchecked")
    private static <T> Registry<T> getRegistry(ResourceKey<? extends Registry<T>> registryKey) {
        // Use the context classloader (ChainClassLoader) to resolve BuiltInRegistries to the real
        // obfuscated class 'lt', not the compile-time stub which has null fields.
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        try {
            Class<?> builtinClass = cl != null 
                ? Class.forName("net.minecraft.core.registries.BuiltInRegistries", true, cl)
                : Class.forName("net.minecraft.core.registries.BuiltInRegistries");
            // Pass 1: Direct key match on all static Registry fields
            for (java.lang.reflect.Field field : builtinClass.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                    try {
                        field.setAccessible(true);
                        Object val = field.get(null);
                        if (val instanceof Registry) {
                            Registry<?> reg = (Registry<?>) val;
                            if (reg.key().equals(registryKey)) {
                                return (Registry<T>) reg;
                            }
                        }
                    } catch (Throwable ignored) {}
                }
            }
            // Pass 2: Try root registry lookup (may fail if holders are unbound)
            for (java.lang.reflect.Field field : builtinClass.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                    try {
                        field.setAccessible(true);
                        Object val = field.get(null);
                        if (val instanceof Registry) {
                            Registry<?> root = (Registry<?>) val;
                            if ("minecraft:root".equals(root.key().location().toString())) {
                                Registry<T> reg = (Registry<T>) ((Registry) root).get((ResourceKey) registryKey);
                                if (reg != null) {
                                    return reg;
                                }
                            }
                        }
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable t) {
            System.err.println("[ChainLoader] Forge RegisterEvent: Error resolving registry for " + registryKey + ": " + t.getMessage());
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
    private static <T> void registerCleanly(Registry<T> registry, ResourceLocation name, T value) {
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
    public <T> void register(ResourceKey<? extends Registry<T>> registryKey, Consumer<RegisterHelper<T>> consumer) {
        if (this.registryKey != null && !this.registryKey.equals(registryKey)) {
            return;
        }

        Registry<T> registry = getRegistry(registryKey);
        if (registry == null) {
            System.err.println("[ChainLoader] Forge RegisterEvent: Could not find registry for key: " + registryKey);
            return;
        }
        
        RegisterHelper<T> helper = new RegisterHelper<T>() {
            @Override
            public void register(ResourceLocation name, T value) {
                registerCleanly(registry, name, value);
                System.out.println("[ChainLoader] Forge RegisterEvent: Registered " + name + " to registry " + registry.key().location());
            }
        };
        
        try {
            consumer.accept(helper);
        } catch (Throwable t) {
            System.err.println("[ChainLoader] Forge RegisterEvent: Failed during registration for registry " + registryKey + ":");
            t.printStackTrace();
        }
    }
}
