package net.chainloader.api.registry;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.Registry;

/**
 * A platform-agnostic registry manager that wraps the underlying platform's registries
 * (Forge/NeoForge/Fabric). This allows mods written for ChainLoader to register content
 * in a single, unified way.
 *
 * @param <T> The type of the objects stored in this registry.
 */
public class ChainRegistry<T> {
    private final ResourceKey<? extends Registry<T>> registryKey;
    private final String modId;
    private final Map<ResourceLocation, RegistryEntry<T>> entries = new HashMap<>();
    private final IRegistryBackend<T> backend;

    /**
     * Creates a new unified registry wrapper.
     *
     * @param registryKey The resource key of the Minecraft registry.
     * @param modId       The mod ID registering these objects.
     */
    public ChainRegistry(ResourceKey<? extends Registry<T>> registryKey, String modId) {
        this.registryKey = registryKey;
        this.modId = modId;
        this.backend = RegistryBackendFactory.create(registryKey);
    }

    /**
     * Registers a new object with the specified path.
     *
     * @param path          The registry path/id (e.g., "my_block").
     * @param valueSupplier A supplier that returns the instance to register.
     * @param <I>           The exact type of the registered object.
     * @return A {@link RegistryEntry} wrapping the registered object.
     */
    @SuppressWarnings("unchecked")
    public <I extends T> RegistryEntry<I> register(String path, Supplier<I> valueSupplier) {
        ResourceLocation id = new ResourceLocation(modId, path);
        
        // Register using the platform-specific backend
        Supplier<I> registeredSupplier = backend.register(id, valueSupplier);
        
        RegistryEntry<I> entry = new RegistryEntry<>(id, registeredSupplier);
        entries.put(id, (RegistryEntry<T>) entry);
        return entry;
    }

    /**
     * Gets all registered entries in this registry wrapper.
     *
     * @return A collection of all registered entries.
     */
    public Collection<RegistryEntry<T>> getEntries() {
        return entries.values();
    }

    /**
     * Gets the registry resource key.
     *
     * @return The registry key.
     */
    public ResourceKey<? extends Registry<T>> getRegistryKey() {
        return registryKey;
    }

    /**
     * Interface representing the platform-specific registry backend.
     * This is implemented internally by ChainLoader's platform loaders (Forge, Fabric, NeoForge).
     */
    public interface IRegistryBackend<T> {
        <I extends T> Supplier<I> register(ResourceLocation id, Supplier<I> supplier);
    }

    /**
     * Factory class to instantiate the appropriate registry backend based on the active loader.
     */
    public static class RegistryBackendFactory {
        private static IRegistryBackendFactoryProvider provider;

        public static void registerProvider(IRegistryBackendFactoryProvider newProvider) {
            provider = newProvider;
        }

        @SuppressWarnings("unchecked")
        public static <T> IRegistryBackend<T> create(ResourceKey<? extends Registry<T>> registryKey) {
            if (provider == null) {
                // Fallback / mock implementation if no platform loader has registered a provider yet
                return new MockRegistryBackend<>();
            }
            return provider.create(registryKey);
        }
    }

    /**
     * Provider interface for creating platform-specific registry backends.
     */
    public interface IRegistryBackendFactoryProvider {
        <T> IRegistryBackend<T> create(ResourceKey<? extends Registry<T>> registryKey);
    }

    /**
     * A mock backend that simulates registration for unit testing or compile-time environments.
     */
    private static class MockRegistryBackend<T> implements IRegistryBackend<T> {
        @Override
        public <I extends T> Supplier<I> register(ResourceLocation id, Supplier<I> supplier) {
            return supplier;
        }
    }
}
