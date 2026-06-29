package net.minecraftforge.client.event;

import net.minecraftforge.eventbus.api.Event;
import net.minecraft.client.KeyMapping;
import net.chainloader.loader.compat.keymappings.KeyMappingStorage;

public class RegisterKeyMappingsEvent extends Event {
    public void register(KeyMapping keyMapping) {
        KeyMappingStorage.register(keyMapping);
    }
}
