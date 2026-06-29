package net.chainloader.api.event;

import net.minecraft.client.MinecraftClient;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.lang.reflect.Array;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/**
 * Unified entry point for ChainLoader events.
 * Provides access to event registration and invokes internal hooks.
 */
public final class ChainEvents {

    private ChainEvents() {} // Prevent instantiation

    // --- Client Events ---

    /**
     * Event fired when the client is fully initialized and started.
     */
    public static final Event<ClientStarted> CLIENT_STARTED = new Event<>(ClientStarted.class, (listeners) -> (client) -> {
        for (ClientStarted listener : listeners) {
            listener.onClientStarted(client);
        }
    });

    /**
     * Event fired when the client begins its shutdown sequence.
     */
    public static final Event<ClientStopping> CLIENT_STOPPING = new Event<>(ClientStopping.class, (listeners) -> (client) -> {
        for (ClientStopping listener : listeners) {
            listener.onClientStopping(client);
        }
    });

    /**
     * Event fired at the end of each client tick.
     */
    public static final Event<ClientTick> CLIENT_TICK = new Event<>(ClientTick.class, (listeners) -> (client) -> {
        for (ClientTick listener : listeners) {
            listener.onClientTick(client);
        }
    });

    // --- Server Events ---

    /**
     * Event fired when the server begins starting up (before world load).
     */
    public static final Event<ServerStarting> SERVER_STARTING = new Event<>(ServerStarting.class, (listeners) -> (server) -> {
        for (ServerStarting listener : listeners) {
            listener.onServerStarting(server);
        }
    });

    /**
     * Event fired after the server is fully started.
     */
    public static final Event<ServerStarted> SERVER_STARTED = new Event<>(ServerStarted.class, (listeners) -> (server) -> {
        for (ServerStarted listener : listeners) {
            listener.onServerStarted(server);
        }
    });

    /**
     * Event fired when the server begins stopping.
     */
    public static final Event<ServerStopping> SERVER_STOPPING = new Event<>(ServerStopping.class, (listeners) -> (server) -> {
        for (ServerStopping listener : listeners) {
            listener.onServerStopping(server);
        }
    });

    /**
     * Event fired after the server has shut down.
     */
    public static final Event<ServerStopped> SERVER_STOPPED = new Event<>(ServerStopped.class, (listeners) -> (server) -> {
        for (ServerStopped listener : listeners) {
            listener.onServerStopped(server);
        }
    });

    /**
     * Event fired at the end of each server tick.
     */
    public static final Event<ServerTick> SERVER_TICK = new Event<>(ServerTick.class, (listeners) -> (server) -> {
        for (ServerTick listener : listeners) {
            listener.onServerTick(server);
        }
    });

    // --- Player Events ---

    /**
     * Event fired when a player joins the server.
     */
    public static final Event<PlayerJoin> PLAYER_JOIN = new Event<>(PlayerJoin.class, (listeners) -> (player) -> {
        for (PlayerJoin listener : listeners) {
            listener.onPlayerJoin(player);
        }
    });

    /**
     * Event fired when a player leaves the server.
     */
    public static final Event<PlayerLeave> PLAYER_LEAVE = new Event<>(PlayerLeave.class, (listeners) -> (player) -> {
        for (PlayerLeave listener : listeners) {
            listener.onPlayerLeave(player);
        }
    });

    // --- Hook Methods (for Internal Loader / Mixins) ---

    /**
     * Invokes the {@link #CLIENT_STARTED} event.
     */
    public static void clientStarted(MinecraftClient client) {
        CLIENT_STARTED.invoker().onClientStarted(client);
    }

    /**
     * Invokes the {@link #CLIENT_STOPPING} event.
     */
    public static void clientStopping(MinecraftClient client) {
        CLIENT_STOPPING.invoker().onClientStopping(client);
    }

    /**
     * Invokes the {@link #CLIENT_TICK} event.
     */
    public static void clientTick(MinecraftClient client) {
        CLIENT_TICK.invoker().onClientTick(client);
    }

    /**
     * Invokes the {@link #SERVER_STARTING} event.
     */
    public static void serverStarting(MinecraftServer server) {
        SERVER_STARTING.invoker().onServerStarting(server);
    }

    /**
     * Invokes the {@link #SERVER_STARTED} event.
     */
    public static void serverStarted(MinecraftServer server) {
        SERVER_STARTED.invoker().onServerStarted(server);
    }

    /**
     * Invokes the {@link #SERVER_STOPPING} event.
     */
    public static void serverStopping(MinecraftServer server) {
        SERVER_STOPPING.invoker().onServerStopping(server);
    }

    /**
     * Invokes the {@link #SERVER_STOPPED} event.
     */
    public static void serverStopped(MinecraftServer server) {
        SERVER_STOPPED.invoker().onServerStopped(server);
    }

    /**
     * Invokes the {@link #SERVER_TICK} event.
     */
    public static void serverTick(MinecraftServer server) {
        SERVER_TICK.invoker().onServerTick(server);
    }

    /**
     * Invokes the {@link #PLAYER_JOIN} event.
     */
    public static void playerJoin(ServerPlayerEntity player) {
        PLAYER_JOIN.invoker().onPlayerJoin(player);
    }

    /**
     * Invokes the {@link #PLAYER_LEAVE} event.
     */
    public static void playerLeave(ServerPlayerEntity player) {
        PLAYER_LEAVE.invoker().onPlayerLeave(player);
    }

    // --- Listener Interfaces ---

    @FunctionalInterface
    public interface ClientStarted {
        void onClientStarted(MinecraftClient client);
    }

    @FunctionalInterface
    public interface ClientStopping {
        void onClientStopping(MinecraftClient client);
    }

    @FunctionalInterface
    public interface ClientTick {
        void onClientTick(MinecraftClient client);
    }

    @FunctionalInterface
    public interface ServerStarting {
        void onServerStarting(MinecraftServer server);
    }

    @FunctionalInterface
    public interface ServerStarted {
        void onServerStarted(MinecraftServer server);
    }

    @FunctionalInterface
    public interface ServerStopping {
        void onServerStopping(MinecraftServer server);
    }

    @FunctionalInterface
    public interface ServerStopped {
        void onServerStopped(MinecraftServer server);
    }

    @FunctionalInterface
    public interface ServerTick {
        void onServerTick(MinecraftServer server);
    }

    @FunctionalInterface
    public interface PlayerJoin {
        void onPlayerJoin(ServerPlayerEntity player);
    }

    @FunctionalInterface
    public interface PlayerLeave {
        void onPlayerLeave(ServerPlayerEntity player);
    }

    // --- Generic Event Core ---

    /**
     * Event dispatching utility class that handles listener registration and invoker generation.
     *
     * @param <T> the listener interface type
     */
    public static final class Event<T> {
        private final Class<? super T> type;
        private final Function<T[], T> invokerFactory;
        private final CopyOnWriteArrayList<T> listeners = new CopyOnWriteArrayList<>();
        private volatile T invoker;

        public Event(Class<? super T> type, Function<T[], T> invokerFactory) {
            this.type = type;
            this.invokerFactory = invokerFactory;
            updateInvoker();
        }

        /**
         * Register a new listener for this event.
         *
         * @param listener the listener to register
         */
        public void register(T listener) {
            if (listener == null) {
                throw new NullPointerException("Listener cannot be null");
            }
            listeners.add(listener);
            updateInvoker();
        }

        /**
         * Get the invoker instance. Calling methods on the returned object
         * will execute all registered listeners.
         *
         * @return the invoker
         */
        public T invoker() {
            return this.invoker;
        }

        @SuppressWarnings("unchecked")
        private void updateInvoker() {
            T[] array = (T[]) Array.newInstance(type, listeners.size());
            listeners.toArray(array);
            this.invoker = invokerFactory.apply(array);
        }
    }
}
