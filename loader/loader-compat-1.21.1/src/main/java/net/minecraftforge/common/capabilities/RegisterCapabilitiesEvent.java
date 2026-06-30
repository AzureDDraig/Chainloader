package net.minecraftforge.common.capabilities;

import net.minecraftforge.eventbus.api.Event;

public class RegisterCapabilitiesEvent extends Event {
    public <T> void register(Class<T> type) {
    }
}
