package net.neoforged.bus.api;

public interface IEventBus {
    void register(Object target);
    default void addListener(java.util.function.Consumer<?> consumer) {}
    default void addListener(Object consumer) {}
    default <T> void addListener(EventPriority priority, boolean receiveCancelled, Class<T> eventType, java.util.function.Consumer<T> consumer) {}
    default <T> void addListener(EventPriority priority, Class<T> eventType, java.util.function.Consumer<T> consumer) {}
    default boolean post(Object event) { return false; }
}

