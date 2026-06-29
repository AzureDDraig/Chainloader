package net.neoforged.neoforge.event.server;

import net.neoforged.bus.api.Event;
import net.minecraft.server.MinecraftServer;

public class ServerStartingEvent extends Event {
    private final MinecraftServer server;

    public ServerStartingEvent(MinecraftServer server) {
        this.server = server;
    }

    public MinecraftServer getServer() {
        return server;
    }
}
