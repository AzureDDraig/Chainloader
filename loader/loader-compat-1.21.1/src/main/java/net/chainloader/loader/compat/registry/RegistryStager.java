package net.chainloader.loader.compat.registry;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * RegistryStager manages the lazy accumulation and deferred injection of registry entries.
 * <p>
 * Legacy mods (e.g., designed for older Minecraft/loader versions) often register blocks,
 * items, and other game objects statically or during early initialization phases. In modern
 * environments with dynamic, frozen, or data-driven registries, early registration can cause
 * class-loading issues, registry order violations, or crashes due to frozen registries.
 * </p>
 * <p>
 * This class intercepts or gathers these legacy allocations and stages them in lazy registration lists.
 * During the appropriate modern dynamic registry cycle (e.g., registry entry events, registry bootstrapping,
 * or right before registry freezing), the loader invokes this stager to inject the deferred allocations.
 * </p>
 */
public class RegistryStager {

    private static final Logger LOGGER = Logger.getLogger("ChainLoader-RegistryStager");
    private static final RegistryStager INSTANCE = new RegistryStager();

    public static RegistryStager getInstance() {
        return INSTANCE;
    }

    // Map of Registry Identifier -> List of Staged Entries
    private final Map<String, List<StagedEntry<?>>> stagedRegistries = new ConcurrentHashMap<>();

    private RegistryStager() {}

    /**
     * Stage an object for lazy registration.
     *
     * @param registryId The identifier of the target registry (e.g., "minecraft:block").
     * @param entryId    The identifier of the entry (e.g., "mymod:fancy_block").
     * @param value      The object to register.
     * @param <T>        The type of the registry entry.
     */
    public <T> void stage(String registryId, String entryId, T value) {
        stageLazy(registryId, entryId, () -> value);
    }

    /**
     * Stage an object supplier for lazy registration, deferring instantiation if necessary.
     *
     * @param registryId    The identifier of the target registry (e.g., "minecraft:block").
     * @param entryId       The identifier of the entry (e.g., "mymod:fancy_block").
     * @param valueSupplier The supplier of the object to register.
     * @param <T>           The type of the registry entry.
     */
    public <T> void stageLazy(String registryId, String entryId, Supplier<T> valueSupplier) {
        Objects.requireNonNull(registryId, "Registry ID cannot be null");
        Objects.requireNonNull(entryId, "Entry ID cannot be null");
        Objects.requireNonNull(valueSupplier, "Value supplier cannot be null");

        stagedRegistries.computeIfAbsent(registryId, k -> Collections.synchronizedList(new ArrayList<>()))
                .add(new StagedEntry<>(entryId, valueSupplier));

        LOGGER.fine(() -> String.format("Staged lazy registration for '%s' in registry '%s'", entryId, registryId));
    }

    /**
     * Clear all staged registrations.
     */
    public void clear() {
        stagedRegistries.clear();
        LOGGER.info("Cleared all staged registry entries.");
    }

    /**
     * Gets the list of staged entries for a specific registry.
     *
     * @param registryId The registry identifier.
     * @return An unmodifiable view of staged entries for the registry, or an empty list if none exist.
     */
    public List<StagedEntry<?>> getStagedEntries(String registryId) {
        List<StagedEntry<?>> entries = stagedRegistries.get(registryId);
        if (entries == null) {
            return Collections.emptyList();
        }
        synchronized (entries) {
            return List.copyOf(entries);
        }
    }

    /**
     * Dynamic injection callback interface.
     * Used by the modern loader to perform actual registry binding.
     *
     * @param <T> The target registry value type.
     */
    @FunctionalInterface
    public interface RegistryBinder<T> {
        /**
         * Binds/registers an entry into the modern registry.
         *
         * @param entryId The identifier of the entry.
         * @param value   The instantiated object to register.
         */
        void bind(String entryId, T value);
    }

    /**
     * Process and inject all staged entries for a given registry ID using the provided binder.
     * This is intended to be called during modern dynamic registry cycles.
     *
     * @param registryId The target registry ID (e.g. "minecraft:block").
     * @param binder     The registry binder that performs the low-level registration.
     * @param <T>        The expected registry type.
     * @return The number of entries successfully injected.
     */
    @SuppressWarnings("unchecked")
    public <T> int inject(String registryId, RegistryBinder<T> binder) {
        Objects.requireNonNull(registryId, "Registry ID cannot be null");
        Objects.requireNonNull(binder, "Binder cannot be null");

        List<StagedEntry<?>> entries = stagedRegistries.remove(registryId);
        if (entries == null || entries.isEmpty()) {
            LOGGER.fine(() -> String.format("No staged entries to inject for registry '%s'", registryId));
            return 0;
        }

        int successCount = 0;
        synchronized (entries) {
            for (StagedEntry<?> entry : entries) {
                try {
                    T value = (T) entry.valueSupplier().get();
                    binder.bind(entry.entryId(), value);
                    successCount++;
                } catch (ClassCastException e) {
                    LOGGER.severe(String.format("Failed to inject '%s' into '%s': Type mismatch. Expected type compatible with binder.",
                            entry.entryId(), registryId));
                } catch (Exception e) {
                    LOGGER.severe(String.format("Unexpected error injecting '%s' into registry '%s': %s",
                            entry.entryId(), registryId, e.getMessage()));
                }
            }
        }

        LOGGER.info(String.format("Successfully injected %d staged entries into registry '%s'", successCount, registryId));
        return successCount;
    }

    /**
     * Represents a staged registry entry.
     */
    public record StagedEntry<T>(String entryId, Supplier<T> valueSupplier) {
        public StagedEntry {
            Objects.requireNonNull(entryId, "entryId cannot be null");
            Objects.requireNonNull(valueSupplier, "valueSupplier cannot be null");
        }
    }

    // --- Legacy Mod API Mockup Helper Methods ---

    /**
     * Mock helper representing block registration from a legacy mod.
     *
     * @param modId         The mod ID of the legacy mod.
     * @param name          The block resource path/name.
     * @param blockSupplier Supplier for the block object.
     */
    public static void registerLegacyBlock(String modId, String name, Supplier<Object> blockSupplier) {
        String registryId = "minecraft:block";
        String entryId = modId + ":" + name;
        getInstance().stageLazy(registryId, entryId, blockSupplier);
    }

    /**
     * Mock helper representing item registration from a legacy mod.
     *
     * @param modId        The mod ID of the legacy mod.
     * @param name         The item resource path/name.
     * @param itemSupplier Supplier for the item object.
     */
    public static void registerLegacyItem(String modId, String name, Supplier<Object> itemSupplier) {
        String registryId = "minecraft:item";
        String entryId = modId + ":" + name;
        getInstance().stageLazy(registryId, entryId, itemSupplier);
    }
}
