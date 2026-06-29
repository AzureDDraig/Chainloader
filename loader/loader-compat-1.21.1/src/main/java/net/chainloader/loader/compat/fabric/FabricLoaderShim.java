package net.chainloader.loader.compat.fabric;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Compatibility shim and event emulator for Fabric Loader and Fabric API.
 * Bridges Fabric-specific loader queries and events to ChainLoader's unified mod environment.
 */
public class FabricLoaderShim {
    private static final FabricLoaderShim INSTANCE = new FabricLoaderShim();

    private final Map<String, UnifiedModInstance> registeredMods = new ConcurrentHashMap<>();
    private final Map<String, List<Object>> entrypoints = new ConcurrentHashMap<>();
    private final Map<String, EventShim<?>> registeredEvents = new ConcurrentHashMap<>();
    
    private Path gameDirectory;
    private Path configDirectory;
    private boolean isDevelopment = false;

    private FabricLoaderShim() {
        // Private constructor for singleton
    }

    /**
     * Returns the singleton instance of the FabricLoaderShim.
     *
     * @return The active shim instance.
     */
    public static FabricLoaderShim getInstance() {
        return INSTANCE;
    }

    /**
     * Checks if a mod with the specified ID is currently registered/loaded in the unified environment.
     *
     * @param id The mod ID to check.
     * @return True if loaded; false otherwise.
     */
    public boolean isModLoaded(String id) {
        return registeredMods.containsKey(id);
    }

    /**
     * Retrieves the ModContainer compatibility wrapper for the specified mod ID.
     *
     * @param id The mod ID.
     * @return An Optional containing the ModContainerShim, or empty if not found.
     */
    public Optional<ModContainerShim> getModContainer(String id) {
        UnifiedModInstance mod = registeredMods.get(id);
        return mod != null ? Optional.of(new ModContainerShim(mod)) : Optional.empty();
    }

    /**
     * Retrieves all loaded mods wrapped as compatibility containers.
     *
     * @return A collection of all registered ModContainerShim instances.
     */
    public Collection<ModContainerShim> getAllMods() {
        List<ModContainerShim> containers = new ArrayList<>();
        for (UnifiedModInstance mod : registeredMods.values()) {
            containers.add(new ModContainerShim(mod));
        }
        return Collections.unmodifiableCollection(containers);
    }

    /**
     * Registers a unified mod instance with this compatibility layer.
     *
     * @param mod The unified mod instance.
     */
    public void registerUnifiedMod(UnifiedModInstance mod) {
        if (mod != null && mod.getId() != null) {
            registeredMods.put(mod.getId(), mod);
        }
    }

    /**
     * Gets the game root directory.
     *
     * @return The game directory path.
     */
    public Path getGameDir() {
        return gameDirectory;
    }

    /**
     * Sets the game root directory.
     *
     * @param gameDirectory The game directory path.
     */
    public void setGameDir(Path gameDirectory) {
        this.gameDirectory = gameDirectory;
    }

    /**
     * Gets the configuration directory.
     *
     * @return The config directory path.
     */
    public Path getConfigDir() {
        return configDirectory;
    }

    /**
     * Sets the configuration directory.
     *
     * @param configDirectory The config directory path.
     */
    public void setConfigDir(Path configDirectory) {
        this.configDirectory = configDirectory;
    }

    /**
     * Checks if running in a development environment.
     *
     * @return True if in development; false otherwise.
     */
    public boolean isDevelopmentEnvironment() {
        return isDevelopment;
    }

    /**
     * Sets the development environment flag.
     *
     * @param isDevelopment True if in development.
     */
    public void setDevelopmentEnvironment(boolean isDevelopment) {
        this.isDevelopment = isDevelopment;
    }

    /**
     * Gets emulated Fabric entrypoint instances registered under the given key.
     *
     * @param key  The entrypoint key (e.g., "main", "client", "server").
     * @param type The expected class type of the entrypoint.
     * @param <T>  The entrypoint type.
     * @return A list of entrypoint instances matching the type.
     */
    @SuppressWarnings("unchecked")
    public <T> List<T> getEntrypoints(String key, Class<T> type) {
        List<Object> list = entrypoints.get(key);
        if (list == null) {
            return Collections.emptyList();
        }
        List<T> result = new ArrayList<>();
        for (Object obj : list) {
            if (type.isInstance(obj)) {
                result.add((T) obj);
            }
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * Registers an entrypoint instance for Fabric-style entrypoint lookup.
     *
     * @param key        The entrypoint key.
     * @param entrypoint The entrypoint instance.
     */
    public void registerEntrypoint(String key, Object entrypoint) {
        entrypoints.computeIfAbsent(key, k -> new java.util.concurrent.CopyOnWriteArrayList<>())
                .add(entrypoint);
    }

    /**
     * Registers a callback listener to a specific emulated Fabric event.
     *
     * @param eventId  The unique identifier of the Fabric event.
     * @param listener The listener callback implementation.
     */
    @SuppressWarnings("unchecked")
    public <T> void registerEventCallback(String eventId, T listener) {
        registeredEvents.computeIfAbsent(eventId, id -> new EventShim<>(id))
                .register(listener);
    }

    /**
     * Dispatches an event to all registered Fabric-style callback listeners.
     *
     * @param eventId The event identifier.
     * @param invoker A consumer that invokes the callbacks with the appropriate arguments.
     * @param <T>     The event listener callback interface type.
     */
    @SuppressWarnings("unchecked")
    public <T> void dispatchEvent(String eventId, Consumer<T> invoker) {
        EventShim<T> eventShim = (EventShim<T>) registeredEvents.get(eventId);
        if (eventShim != null) {
            for (T listener : eventShim.getListeners()) {
                try {
                    invoker.accept(listener);
                } catch (Exception e) {
                    System.err.println("Error dispatching Fabric event [" + eventId + "] to listener: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * Represents a unified mod instance in ChainLoader's architecture.
     * Acts as the single source of truth for mod info, metadata, and origin path.
     */
    public static class UnifiedModInstance {
        private final String id;
        private final String version;
        private final String name;
        private final String description;
        private final Path originPath;
        private final List<String> authors;
        private final Map<String, String> dependencies;
        private final Map<String, Object> customMetadata;

        public UnifiedModInstance(String id, String version, String name, String description, Path originPath,
                                  List<String> authors, Map<String, String> dependencies, Map<String, Object> customMetadata) {
            this.id = Objects.requireNonNull(id, "Mod ID cannot be null");
            this.version = version != null ? version : "0.0.0";
            this.name = name != null ? name : id;
            this.description = description != null ? description : "";
            this.originPath = originPath;
            this.authors = authors != null ? List.copyOf(authors) : List.of();
            this.dependencies = dependencies != null ? Map.copyOf(dependencies) : Map.of();
            this.customMetadata = customMetadata != null ? Map.copyOf(customMetadata) : Map.of();
        }

        public String getId() { return id; }
        public String getVersion() { return version; }
        public String getName() { return name; }
        public String getDescription() { return description; }
        public Path getOriginPath() { return originPath; }
        public List<String> getAuthors() { return authors; }
        public Map<String, String> getDependencies() { return dependencies; }
        public Map<String, Object> getCustomMetadata() { return customMetadata; }
    }

    /**
     * Emulates Fabric's {@code net.fabricmc.loader.api.ModContainer}.
     */
    public static class ModContainerShim {
        private final UnifiedModInstance unifiedMod;

        public ModContainerShim(UnifiedModInstance unifiedMod) {
            this.unifiedMod = Objects.requireNonNull(unifiedMod, "Unified mod instance cannot be null");
        }

        public UnifiedModInstance getUnifiedMod() {
            return unifiedMod;
        }

        public String getId() {
            return unifiedMod.getId();
        }

        public String getVersion() {
            return unifiedMod.getVersion();
        }

        public Path getOriginPath() {
            return unifiedMod.getOriginPath();
        }

        public Map<String, Object> getMetadata() {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("id", unifiedMod.getId());
            metadata.put("name", unifiedMod.getName());
            metadata.put("version", unifiedMod.getVersion());
            metadata.put("description", unifiedMod.getDescription());
            metadata.put("authors", unifiedMod.getAuthors());
            metadata.put("dependencies", unifiedMod.getDependencies());
            metadata.putAll(unifiedMod.getCustomMetadata());
            return Collections.unmodifiableMap(metadata);
        }
    }

    /**
     * Emulates Fabric's event registration container.
     */
    private static class EventShim<T> {
        private final String eventId;
        private final List<Object> listeners = new java.util.concurrent.CopyOnWriteArrayList<>();

        public EventShim(String eventId) {
            this.eventId = eventId;
        }

        public String getEventId() {
            return eventId;
        }

        public void register(Object listener) {
            listeners.add(listener);
        }

        @SuppressWarnings("unchecked")
        public List<T> getListeners() {
            return (List<T>) (List<?>) listeners;
        }
    }
}
