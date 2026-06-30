package dev.architectury.event.events.client;

import dev.architectury.event.Event;
import net.minecraft.client.player.LocalPlayer;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class ClientPlayerEvent {
    public interface ClientPlayerJoin {
        void join(LocalPlayer player);
    }
    public interface ClientPlayerQuit {
        void quit(LocalPlayer player);
    }

    private static final List<ClientPlayerJoin> JOIN_LISTENERS = new CopyOnWriteArrayList<>();
    private static final List<ClientPlayerQuit> QUIT_LISTENERS = new CopyOnWriteArrayList<>();

    public static Event<ClientPlayerJoin> CLIENT_PLAYER_JOIN = new Event<>() {
        @Override
        public void register(ClientPlayerJoin listener) {
            if (listener != null) {
                System.out.println("[Architectury Stub] Registered ClientPlayerJoin listener: " + listener.getClass().getName());
                JOIN_LISTENERS.add(listener);
            }
        }
    };

    public static Event<ClientPlayerQuit> CLIENT_PLAYER_QUIT = new Event<>() {
        @Override
        public void register(ClientPlayerQuit listener) {
            if (listener != null) {
                System.out.println("[Architectury Stub] Registered ClientPlayerQuit listener: " + listener.getClass().getName());
                QUIT_LISTENERS.add(listener);
            }
        }
    };

    public static List<ClientPlayerJoin> getJoinListeners() {
        return JOIN_LISTENERS;
    }

    public static List<ClientPlayerQuit> getQuitListeners() {
        return QUIT_LISTENERS;
    }
}

