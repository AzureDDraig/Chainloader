package dev.architectury.event.events.common;

import dev.architectury.event.Event;
import net.minecraft.server.level.ServerPlayer;

public class PlayerEvent {
    @FunctionalInterface
    public interface PlayerJoin {
        void join(ServerPlayer player);
    }

    @FunctionalInterface
    public interface PlayerQuit {
        void quit(ServerPlayer player);
    }

    public static Event<PlayerJoin> PLAYER_JOIN = new Event<>() {
        @Override
        public void register(PlayerJoin listener) {}
    };

    public static Event<PlayerQuit> PLAYER_QUIT = new Event<>() {
        @Override
        public void register(PlayerQuit listener) {}
    };
}
