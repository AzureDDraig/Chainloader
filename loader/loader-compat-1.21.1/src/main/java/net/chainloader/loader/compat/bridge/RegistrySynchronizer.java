package net.chainloader.loader.compat.bridge;

import net.chainloader.loader.compat.registry.RegistryStager;
import net.chainloader.loader.compat.fabric.FabricLoaderShim;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.IEventBus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * RegistrySynchronizer acts as the central lazy registry synchronization loader.
 * It manages the lifecycle state transitions of dynamic registries, coordinates 
 * the binding cycles of staged legacy entries, and interfaces with event 
 * translation layers and Fabric API ports.
 */
public class RegistrySynchronizer {

    private static final Logger LOGGER = Logger.getLogger("ChainLoader-RegistrySynchronizer");
    private static final RegistrySynchronizer INSTANCE = new RegistrySynchronizer();

    public static RegistrySynchronizer getInstance() {
        return INSTANCE;
    }

    /**
     * Represents the states of a registry during the loader startup cycle.
     */
    public enum LifecycleState {
        UNINITIALIZED,
        STAGING,
        BINDING,
        COMPLETED,
        FROZEN
    }

    // Active registry lifecycle states
    private final Map<String, LifecycleState> registryStates = new ConcurrentHashMap<>();

    // Mock modern database of registered elements: Registry ID -> (Entry ID -> Registry Value)
    private final Map<String, Map<String, Object>> activeRegistries = new ConcurrentHashMap<>();

    // Listeners hooked into registry binding events (e.g. for Fabric API port or Event Translator coordination)
    private final List<BindingCycleListener> listeners = Collections.synchronizedList(new ArrayList<>());

    // Forge/NeoForge Event Bus references for posting Registry events
    private IEventBus forgeEventBus;

    private RegistrySynchronizer() {
        // Initialize default states for standard registries
        registryStates.put("minecraft:block", LifecycleState.UNINITIALIZED);
        registryStates.put("minecraft:item", LifecycleState.UNINITIALIZED);
        registryStates.put("minecraft:entity_type", LifecycleState.UNINITIALIZED);
    }

    /**
     * Sets the Forge compatibility event bus to allow posting of registration events.
     *
     * @param eventBus The Forge/NeoForge event bus.
     */
    public void setForgeEventBus(IEventBus eventBus) {
        this.forgeEventBus = eventBus;
    }

    /**
     * Registers a listener to monitor registry binding lifecycle events.
     *
     * @param listener The listener instance.
     */
    public void registerListener(BindingCycleListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    /**
     * Transition a registry to a new lifecycle state.
     *
     * @param registryId The registry identifier.
     * @param newState   The new state to transition to.
     */
    public void transitionState(String registryId, LifecycleState newState) {
        LifecycleState oldState = registryStates.put(registryId, newState);
        LOGGER.info(String.format("Registry '%s' transitioned: %s -> %s", registryId, oldState, newState));
    }

    /**
     * Retrieves the current lifecycle state of a specific registry.
     *
     * @param registryId The registry identifier.
     * @return The active lifecycle state.
     */
    public LifecycleState getRegistryState(String registryId) {
        return registryStates.getOrDefault(registryId, LifecycleState.UNINITIALIZED);
    }

    /**
     * Populates mock legacy entries into the RegistryStager.
     * This simulates the static initialization phase of legacy mods.
     */
    public void mockStaging() {
        LOGGER.info("Executing mock staging of legacy mod registry entries...");
        
        transitionState("minecraft:block", LifecycleState.STAGING);
        transitionState("minecraft:item", LifecycleState.STAGING);
        
        // Stage legacy blocks
        RegistryStager.registerLegacyBlock("legacy_mod", "ruby_ore", () -> "Block[legacy_mod:ruby_ore]");
        RegistryStager.registerLegacyBlock("legacy_mod", "ruby_block", () -> "Block[legacy_mod:ruby_block]");

        // Stage legacy items
        RegistryStager.registerLegacyItem("legacy_mod", "ruby", () -> "Item[legacy_mod:ruby]");
        RegistryStager.registerLegacyItem("legacy_mod", "ruby_pickaxe", () -> "Item[legacy_mod:ruby_pickaxe]");

        // Stage a custom entity type
        RegistryStager.getInstance().stageLazy("minecraft:entity_type", "legacy_mod:ruby_golem", 
                () -> "EntityType[legacy_mod:ruby_golem]");

        LOGGER.info("Mock staging completed successfully. Staged entries are ready for binding.");
    }

    /**
     * Executes the binding cycle for a specific registry.
     * This retrieves all staged elements from the RegistryStager, injects them into
     * our active/modern registry, and coordinates notifications across Fabric and Forge layers.
     *
     * @param registryId The registry identifier.
     * @return The number of successfully bound entries.
     */
    public int executeBindingCycle(String registryId) {
        LOGGER.info(String.format("Starting binding cycle for registry '%s'...", registryId));
        transitionState(registryId, LifecycleState.BINDING);

        Map<String, Object> registryMap = activeRegistries.computeIfAbsent(registryId, k -> new ConcurrentHashMap<>());
        AtomicInteger count = new AtomicInteger(0);

        // Notify listeners that binding is about to start
        notifyListenersBefore(registryId);

        // Inject the staged entries using the RegistryStager
        int injectedCount = RegistryStager.getInstance().inject(registryId, (entryId, value) -> {
            registryMap.put(entryId, value);
            count.incrementAndGet();

            LOGGER.fine(String.format("Bound entry: %s -> %s", entryId, value));

            // Fabric API Port Coordination:
            // Dispatch dynamic registry event via FabricLoaderShim to simulate Fabric API Registry Entry callbacks
            FabricLoaderShim.getInstance().<FabricRegistryEntryCallback>dispatchEvent("fabric:registry_entry_added", listener -> {
                listener.onEntryAdded(registryId, entryId, value);
            });
        });

        // Forge/NeoForge Event Translator Coordination:
        // Post a mock RegisterEvent on the compatibility Event Bus to notify translating systems
        if (forgeEventBus != null && injectedCount > 0) {
            ForgeRegistryEvent mockEvent = new ForgeRegistryEvent(registryId, registryMap);
            forgeEventBus.post(mockEvent);
            LOGGER.info(String.format("Posted Forge RegistryEvent for '%s' to event bus.", registryId));
        }

        // Notify listeners that binding completed
        notifyListenersAfter(registryId, injectedCount);

        transitionState(registryId, LifecycleState.COMPLETED);
        
        // Simulating immediate freeze post-binding cycle
        transitionState(registryId, LifecycleState.FROZEN);

        return injectedCount;
    }

    private void notifyListenersBefore(String registryId) {
        synchronized (listeners) {
            for (BindingCycleListener listener : listeners) {
                try {
                    listener.beforeBindingCycle(registryId);
                } catch (Exception e) {
                    LOGGER.severe("Error in BindingCycleListener beforeBindingCycle: " + e.getMessage());
                }
            }
        }
    }

    private void notifyListenersAfter(String registryId, int count) {
        synchronized (listeners) {
            for (BindingCycleListener listener : listeners) {
                try {
                    listener.afterBindingCycle(registryId, count);
                } catch (Exception e) {
                    LOGGER.severe("Error in BindingCycleListener afterBindingCycle: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Gets all bound objects in a registry.
     *
     * @param registryId The registry identifier.
     * @return An unmodifiable map of entryId to value.
     */
    public Map<String, Object> getRegistryEntries(String registryId) {
        Map<String, Object> map = activeRegistries.get(registryId);
        return map != null ? Collections.unmodifiableMap(map) : Collections.emptyMap();
    }

    /**
     * Emulated Fabric API callback for registry modifications.
     */
    @FunctionalInterface
    public interface FabricRegistryEntryCallback {
        void onEntryAdded(String registryId, String entryId, Object value);
    }

    /**
     * Listener interface to monitor registry synchronization stages.
     */
    public interface BindingCycleListener {
        void beforeBindingCycle(String registryId);
        void afterBindingCycle(String registryId, int boundCount);
    }

    /**
     * Emulated Forge/NeoForge RegisterEvent class for event translation.
     */
    public static class ForgeRegistryEvent extends Event {
        private final String registryId;
        private final Map<String, Object> entries;

        public ForgeRegistryEvent(String registryId, Map<String, Object> entries) {
            this.registryId = registryId;
            this.entries = entries;
        }

        public String getRegistryId() {
            return registryId;
        }

        public Map<String, Object> getEntries() {
            return entries;
        }
    }
}
