package net.minecraftforge.event;

import net.minecraftforge.eventbus.api.Event;

public class RegisterCommandsEvent extends Event {
    private final com.mojang.brigadier.CommandDispatcher dispatcher;
    public RegisterCommandsEvent(com.mojang.brigadier.CommandDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }
    public com.mojang.brigadier.CommandDispatcher getDispatcher() {
        return dispatcher;
    }
}
