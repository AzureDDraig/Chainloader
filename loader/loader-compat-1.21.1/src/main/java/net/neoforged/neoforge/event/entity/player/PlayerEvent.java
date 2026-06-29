package net.neoforged.neoforge.event.entity.player;

import net.neoforged.bus.api.Event;
import net.minecraft.world.entity.player.Player;

public class PlayerEvent extends Event {
    private final Player player;

    public PlayerEvent(Player player) {
        this.player = player;
    }

    public Player getEntity() {
        return player;
    }

    public Player getPlayer() {
        return player;
    }

    public static class PlayerLoggedInEvent extends PlayerEvent {
        public PlayerLoggedInEvent(Player player) {
            super(player);
        }
    }
}
