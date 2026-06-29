package net.minecraftforge.network;

import java.util.concurrent.CompletableFuture;
import net.minecraft.world.entity.player.Player;

public class NetworkEvent {
    public static class Context {
        private final Player sender;

        public Context(Player sender) {
            this.sender = sender;
        }

        public Player getSender() {
            return this.sender;
        }

        public void setPacketHandled(boolean handled) {
            // no-op
        }

        public CompletableFuture<Void> enqueueWork(Runnable runnable) {
            runnable.run();
            return CompletableFuture.completedFuture(null);
        }
    }
}
