package net.fabricmc.fabric.api.client.keybinding.v1;

import net.minecraft.client.KeyMapping;
import net.chainloader.loader.compat.bridge.EventBridgeHelper;

public final class KeyBindingHelper {
    public static KeyMapping registerKeyBinding(KeyMapping keyBinding) {
        System.out.println("[ChainLoader] Fabric KeyBindingHelper registered key: " + keyBinding);
        EventBridgeHelper.registerKeyMapping(keyBinding);
        return keyBinding;
    }
}
