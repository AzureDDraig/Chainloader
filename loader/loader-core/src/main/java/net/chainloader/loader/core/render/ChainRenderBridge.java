package net.chainloader.loader.core.render;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public class ChainRenderBridge {
    private static final Map<String, ChainItemRenderer> itemRenderers = new ConcurrentHashMap<>();

    public static void registerItemRenderer(String itemId, ChainItemRenderer renderer) {
        itemRenderers.put(itemId, renderer);
    }

    public static ChainItemRenderer getItemRenderer(String itemId) {
        return itemRenderers.get(itemId);
    }
}
