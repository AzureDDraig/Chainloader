package dev.architectury.event.events.common;

import dev.architectury.event.Event;
import net.minecraft.server.MinecraftServer;

public class LifecycleEvent {
    @FunctionalInterface
    public interface ServerState {
        void stateChanged(MinecraftServer server);
    }

    public static Event<ServerState> SERVER_STARTING = new Event<>() {
        @Override
        public void register(ServerState listener) {}
    };

    public static Event<ServerState> SERVER_STOPPING = new Event<>() {
        @Override
        public void register(ServerState listener) {}
    };
}
