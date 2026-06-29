package dev.architectury.platform.forge;

import net.minecraftforge.eventbus.api.IEventBus;

public class EventBuses {
    public static void registerModEventBus(String modId, IEventBus eventBus) {
        System.out.println("[ChainLoader] Mock EventBuses registered mod event bus for: " + modId);
    }
}
