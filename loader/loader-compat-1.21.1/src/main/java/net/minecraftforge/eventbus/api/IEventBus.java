package net.minecraftforge.eventbus.api;

import java.util.function.Consumer;

/**
 * Mockup of the Forge/NeoForge IEventBus interface.
 */
public interface IEventBus {
    void register(Object target);
    <T extends Event> void addListener(Consumer<T> consumer);
    default <T extends Event> void addListener(Class<T> eventType, Consumer<T> consumer) {}
    boolean post(Event event);
}
