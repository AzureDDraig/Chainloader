package net.neoforged.neoforge.client.event;

import net.neoforged.bus.api.Event;

public class ClientPlayerNetworkEvent extends Event {
    public static class LoggingOut extends ClientPlayerNetworkEvent {}
    public static class LoggingIn extends ClientPlayerNetworkEvent {}
}
