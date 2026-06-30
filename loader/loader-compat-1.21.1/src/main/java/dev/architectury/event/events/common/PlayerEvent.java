package dev.architectury.event.events.common;

import dev.architectury.event.Event;
import net.minecraft.server.level.ServerPlayer;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class PlayerEvent {
    @FunctionalInterface
    public interface PlayerJoin {
        void join(ServerPlayer player);
    }

    @FunctionalInterface
    public interface PlayerQuit {
        void quit(ServerPlayer player);
    }

    private static final List<PlayerJoin> JOIN_LISTENERS = new CopyOnWriteArrayList<>();
    private static final List<PlayerQuit> QUIT_LISTENERS = new CopyOnWriteArrayList<>();

    public static Event<PlayerJoin> PLAYER_JOIN = new Event<>() {
        @Override
        public void register(PlayerJoin listener) {
            if (listener != null) {
                System.out.println("[Architectury Stub] Registered PlayerJoin listener: " + listener.getClass().getName());
                JOIN_LISTENERS.add(listener);
            }
        }
    };

    public static Event<PlayerQuit> PLAYER_QUIT = new Event<>() {
        @Override
        public void register(PlayerQuit listener) {
            if (listener != null) {
                System.out.println("[Architectury Stub] Registered PlayerQuit listener: " + listener.getClass().getName());
                QUIT_LISTENERS.add(listener);
            }
        }
    };

    public static List<PlayerJoin> getJoinListeners() {
        return JOIN_LISTENERS;
    }

    public static List<PlayerQuit> getQuitListeners() {
        return QUIT_LISTENERS;
    }
}

