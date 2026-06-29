package net.neoforged.neoforge.event.server;

import net.neoforged.bus.api.Event;
import net.minecraft.server.MinecraftServer;

public class ServerStoppedEvent extends Event {
    private final MinecraftServer server;

    public ServerStoppedEvent(MinecraftServer server) {
        this.server = server;
    }

    public MinecraftServer getServer() {
        return server;
    }
}
