package net.neoforged.neoforge.client.event;

import net.neoforged.bus.api.Event;

public class RegisterClientTooltipComponentFactoriesEvent extends Event {
    public void register(Class<?> type, java.util.function.Function<?, ?> factory) {
        System.out.println("[RegisterClientTooltipComponentFactoriesEvent] Registered factory for class: " + type.getName());
    }
}
