package net.neoforged.fml.event.lifecycle;

import java.util.concurrent.CompletableFuture;

public class ParallelDispatchEvent extends net.neoforged.bus.api.Event {
    public CompletableFuture<Void> enqueueWork(Runnable runnable) {
        runnable.run();
        return CompletableFuture.completedFuture(null);
    }
}
