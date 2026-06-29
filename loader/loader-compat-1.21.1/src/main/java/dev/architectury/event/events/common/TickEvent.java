package dev.architectury.event.events.common;

import dev.architectury.event.Event;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;

public class TickEvent {
    @FunctionalInterface
    public interface Server {
        void tick(MinecraftServer server);
    }

    @FunctionalInterface
    public interface Player {
        void tick(net.minecraft.world.entity.player.Player player);
    }

    public static Event<Server> SERVER_POST = new Event<>() {
        @Override
        public void register(Server listener) {}
    };

    public static Event<Player> PLAYER_POST = new Event<>() {
        @Override
        public void register(Player listener) {}
    };
}
