package net.minecraftforge.common;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class WorldWorkerManager {
    public interface IWorker {
        boolean doWork();
    }

    private static final Queue<IWorker> WORKERS = new ConcurrentLinkedQueue<>();
    private static volatile boolean listenerRegistered = false;

    public static void addWorker(IWorker worker) {
        if (worker != null) {
            System.out.println("[WorldWorkerManager] Registered new worker: " + worker.getClass().getName());
            WORKERS.add(worker);
            
            if (!listenerRegistered) {
                synchronized (WorldWorkerManager.class) {
                    if (!listenerRegistered) {
                        try {
                            ClassLoader cl = WorldWorkerManager.class.getClassLoader();
                            Class<?> serverTickPostClass = Class.forName("net.neoforged.neoforge.event.tick.ServerTickEvent$Post", true, cl);
                            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                                net.neoforged.bus.api.EventPriority.NORMAL,
                                serverTickPostClass,
                                (java.util.function.Consumer) (event -> {
                                    tick();
                                })
                            );
                            listenerRegistered = true;
                            System.out.println("[WorldWorkerManager] Successfully registered ServerTickEvent listener dynamically.");
                        } catch (Throwable t) {
                            System.err.println("[WorldWorkerManager] Failed to register ServerTickEvent listener dynamically:");
                            t.printStackTrace();
                        }
                    }
                }
            }
        }
    }

    public static void tick() {
        if (WORKERS.isEmpty()) {
            return;
        }
        System.out.println("[WorldWorkerManager] Ticking " + WORKERS.size() + " active workers...");
        long startTime = System.currentTimeMillis();
        long timeBudget = 15; // 15 ms per tick
        
        while (!WORKERS.isEmpty()) {
            if (System.currentTimeMillis() - startTime > timeBudget) {
                break;
            }
            IWorker worker = WORKERS.peek();
            if (worker == null) {
                WORKERS.poll();
                continue;
            }
            try {
                boolean hasMoreWork = worker.doWork();
                if (!hasMoreWork) {
                    System.out.println("[WorldWorkerManager] Worker completed successfully: " + worker.getClass().getName());
                    WORKERS.remove(worker);
                }
            } catch (Throwable t) {
                System.err.println("[WorldWorkerManager] Error ticking worker " + worker.getClass().getName() + ":");
                t.printStackTrace();
                WORKERS.remove(worker);
            }
        }
    }
}
