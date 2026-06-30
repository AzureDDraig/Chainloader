package net.chainloader.loader.core.registry;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.Queue;

public class ChainRegistryBridge {
    public static class RegistryEntry {
        public final String registryName; // e.g. "mob_effect", "block", "item"
        public final String entryId;      // e.g. "waystones:warp"
        public final Object value;

        public RegistryEntry(String registryName, String entryId, Object value) {
            this.registryName = registryName;
            this.entryId = entryId;
            this.value = value;
        }
    }

    private static final Queue<RegistryEntry> pendingEntries = new ConcurrentLinkedQueue<>();

    public static void register(String registryName, String entryId, Object value) {
        pendingEntries.add(new RegistryEntry(registryName, entryId, value));
    }

    public static Queue<RegistryEntry> getPendingEntries() {
        return pendingEntries;
    }
}
