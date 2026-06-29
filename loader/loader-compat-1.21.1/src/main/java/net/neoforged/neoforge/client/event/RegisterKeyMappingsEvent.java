package net.neoforged.neoforge.client.event;

import net.neoforged.bus.api.Event;
import net.minecraft.client.KeyMapping;
import net.chainloader.loader.compat.keymappings.KeyMappingStorage;

public class RegisterKeyMappingsEvent extends Event {
    public void register(KeyMapping keyMapping) {
        KeyMappingStorage.register(keyMapping);
    }
}
