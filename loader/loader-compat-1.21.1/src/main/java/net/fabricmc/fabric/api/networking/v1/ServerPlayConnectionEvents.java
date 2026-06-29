package net.fabricmc.fabric.api.networking.v1;

import net.fabricmc.fabric.api.event.Event;

public class ServerPlayConnectionEvents {
    public interface Join {
        void onPlayReady(Object handler, Object sender, Object server);
    }

    public interface Disconnect {
        void onPlayDisconnect(Object handler, Object server);
    }

    public interface Init {
        void onPlayInit(Object handler, Object server);
    }

    public static final Event<Join> JOIN = new Event<>(Join.class);
    public static final Event<Disconnect> DISCONNECT = new Event<>(Disconnect.class);
    public static final Event<Init> INIT = new Event<>(Init.class);

    static {
        DISCONNECT.register((handler, server) -> {
            ServerPlayNetworking.clearConnectionStates(handler);
        });
    }
}
