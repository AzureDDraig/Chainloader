package net.neoforged.neoforge.network.handling;

import net.minecraft.world.entity.player.Player;
import java.util.concurrent.CompletableFuture;

public interface IPayloadContext {
    CompletableFuture<Void> enqueueWork(Runnable runnable);
    Player player();
}
