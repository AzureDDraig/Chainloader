package net.minecraftforge.client.event;

import net.minecraftforge.eventbus.api.Event;

public class RegisterColorHandlersEvent extends Event {
    public static class Item extends RegisterColorHandlersEvent {
    }
    public static class Block extends RegisterColorHandlersEvent {
    }
}
