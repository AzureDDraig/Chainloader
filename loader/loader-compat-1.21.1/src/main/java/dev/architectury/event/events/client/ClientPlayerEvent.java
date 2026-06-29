package dev.architectury.event.events.client;

import dev.architectury.event.Event;
import net.minecraft.client.player.LocalPlayer;

public class ClientPlayerEvent {
    public interface ClientPlayerJoin {
        void join(LocalPlayer player);
    }
    public interface ClientPlayerQuit {
        void quit(LocalPlayer player);
    }

    public static Event<ClientPlayerJoin> CLIENT_PLAYER_JOIN = new Event<>() {
        @Override
        public void register(ClientPlayerJoin listener) {}
    };

    public static Event<ClientPlayerQuit> CLIENT_PLAYER_QUIT = new Event<>() {
        @Override
        public void register(ClientPlayerQuit listener) {}
    };
}
