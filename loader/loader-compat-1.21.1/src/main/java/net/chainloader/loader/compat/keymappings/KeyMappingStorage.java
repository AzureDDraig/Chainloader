package net.chainloader.loader.compat.keymappings;

import net.minecraft.client.KeyMapping;
import net.chainloader.loader.compat.bridge.EventBridgeHelper;

public class KeyMappingStorage {
    public static void register(KeyMapping keyMapping) {
        EventBridgeHelper.registerKeyMapping(keyMapping);
    }
}
