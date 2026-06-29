package dev.architectury.registry.client.keymappings;

import net.minecraft.client.KeyMapping;

public class KeyMappingRegistry {
    public static void register(KeyMapping keyMapping) {
        System.out.println("[ChainLoader] Mock KeyMappingRegistry registered key: " + keyMapping);
    }
}
