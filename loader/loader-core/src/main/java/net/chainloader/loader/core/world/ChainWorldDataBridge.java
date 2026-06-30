package net.chainloader.loader.core.world;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public class ChainWorldDataBridge {
    private static final Map<String, ChainWorldData> worldDataRegistry = new ConcurrentHashMap<>();

    public static void registerWorldData(String name, ChainWorldData data) {
        worldDataRegistry.put(name, data);
    }

    public static ChainWorldData getWorldData(String name) {
        return worldDataRegistry.get(name);
    }
}
