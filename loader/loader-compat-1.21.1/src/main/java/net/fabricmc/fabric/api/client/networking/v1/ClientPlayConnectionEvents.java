package net.fabricmc.fabric.api.client.networking.v1;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;

public final class ClientPlayConnectionEvents {
    public static final Event<Init> INIT = new Event<>(Init.class);
    public static final Event<Join> JOIN = new Event<>(Join.class);
    public static final Event<Disconnect> DISCONNECT = new Event<>(Disconnect.class);

    static {
        DISCONNECT.register((handler, client) -> {
            ClientPlayNetworking.clearConnectionStates(handler);
        });
    }

    @FunctionalInterface
    public interface Init {
        void onPlayInit(ClientPacketListener handler, Minecraft client);
    }

    @FunctionalInterface
    public interface Join {
        void onPlayReady(ClientPacketListener handler, PacketSender sender, Minecraft client);
    }

    @FunctionalInterface
    public interface Disconnect {
        void onPlayDisconnect(ClientPacketListener handler, Minecraft client);
    }
}
