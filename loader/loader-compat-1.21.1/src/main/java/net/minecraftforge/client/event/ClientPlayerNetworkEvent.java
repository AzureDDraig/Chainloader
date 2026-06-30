package net.minecraftforge.client.event;

import net.minecraftforge.eventbus.api.Event;

public class ClientPlayerNetworkEvent extends Event {
    public static class LoggedInEvent extends ClientPlayerNetworkEvent {
    }
    public static class LoggedOutEvent extends ClientPlayerNetworkEvent {
    }
}
