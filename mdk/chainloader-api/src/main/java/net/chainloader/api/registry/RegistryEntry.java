package net.chainloader.api.registry;

import java.util.function.Supplier;
import net.minecraft.resources.ResourceLocation;

/**
 * Represents a registered object in the ChainLoader unified registry system.
 * This class wraps the registered object and its corresponding identifier, providing
 * a platform-agnostic way to retrieve the object across both Forge and Fabric.
 *
 * @param <T> The type of the registered object.
 */
public class RegistryEntry<T> implements Supplier<T> {
    private final ResourceLocation id;
    private final Supplier<T> valueSupplier;
    private T value;

    /**
     * Creates a new registry entry.
     *
     * @param id            The registry identifier of the object.
     * @param valueSupplier A supplier containing the registered object.
     */
    public RegistryEntry(ResourceLocation id, Supplier<T> valueSupplier) {
        this.id = id;
        this.valueSupplier = valueSupplier;
    }

    /**
     * Helper constructor using namespace and path.
     *
     * @param namespace     The namespace.
     * @param path          The path.
     * @param valueSupplier A supplier containing the registered object.
     */
    public RegistryEntry(String namespace, String path, Supplier<T> valueSupplier) {
        this(new ResourceLocation(namespace, path), valueSupplier);
    }

    /**
     * Gets the full identifier of the registered object.
     *
     * @return The resource location identifier.
     */
    public ResourceLocation getId() {
        return id;
    }

    /**
     * Gets the namespace of the registered object's identifier.
     *
     * @return The namespace.
     */
    public String getNamespace() {
        return id.getNamespace();
    }

    /**
     * Gets the path of the registered object's identifier.
     *
     * @return The path.
     */
    public String getPath() {
        return id.getPath();
    }

    /**
     * Retrieves the registered object. If the object has not been resolved yet,
     * it evaluates the supplier.
     *
     * @return The registered object.
     */
    @Override
    public T get() {
        if (value == null) {
            value = valueSupplier.get();
        }
        return value;
    }
}
