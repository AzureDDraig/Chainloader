package net.chainloader.loader.core.event;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;
import java.util.function.Consumer;

public class ChainEventBridge {
    public static class PlayerTickEvent {
        public final Object player;
        public PlayerTickEvent(Object player) { this.player = player; }
    }

    private static final List<Consumer<PlayerTickEvent>> playerTickListeners = new CopyOnWriteArrayList<>();

    public static void registerPlayerTickListener(Consumer<PlayerTickEvent> listener) {
        playerTickListeners.add(listener);
    }

    public static void postPlayerTick(Object player) {
        PlayerTickEvent event = new PlayerTickEvent(player);
        for (Consumer<PlayerTickEvent> listener : playerTickListeners) {
            listener.accept(event);
        }
    }
}
