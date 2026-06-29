package net.fabricmc.fabric.api.event.lifecycle.v1;

import net.fabricmc.fabric.api.event.Event;

public class ServerLifecycleEvents {
    public interface ServerStarting {
        void onServerStarting(Object server);
    }
    public interface ServerStarted {
        void onServerStarted(Object server);
    }
    public interface ServerStopping {
        void onServerStopping(Object server);
    }
    public interface ServerStopped {
        void onServerStopped(Object server);
    }
    public interface EndDataPackReload {
        void onEndDataPackReload(Object server, Object resourceManager, boolean success);
    }

    public static final Event<ServerStarting> SERVER_STARTING = new Event<>(ServerStarting.class);
    public static final Event<ServerStarted> SERVER_STARTED = new Event<>(ServerStarted.class);
    public static final Event<ServerStopping> SERVER_STOPPING = new Event<>(ServerStopping.class);
    public static final Event<ServerStopped> SERVER_STOPPED = new Event<>(ServerStopped.class);
    public static final Event<EndDataPackReload> END_DATA_PACK_RELOAD = new Event<>(EndDataPackReload.class);
}
