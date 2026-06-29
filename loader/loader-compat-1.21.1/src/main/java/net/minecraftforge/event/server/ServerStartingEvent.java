package net.minecraftforge.event.server;

import net.minecraftforge.eventbus.api.Event;
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
