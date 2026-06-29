package net.chainloader.api.event;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Simple host-side event bus for ChainLoader.
 */
public class ChainEventBus {
    private final ConcurrentHashMap<Class<? extends ChainEvent>, List<Consumer<? extends ChainEvent>>> listeners = new ConcurrentHashMap<>();

    public <T extends ChainEvent> void register(Class<T> eventType, Consumer<T> listener) {
        listeners.computeIfAbsent(eventType, k -> new ArrayList<>()).add(listener);
    }

    @SuppressWarnings("unchecked")
    public <T extends ChainEvent> boolean post(T event) {
        if (event == null) return false;

        Class<?> current = event.getClass();
        while (ChainEvent.class.isAssignableFrom(current)) {
            List<Consumer<? extends ChainEvent>> eventListeners = listeners.get(current);
            if (eventListeners != null) {
                for (Consumer<? extends ChainEvent> listener : eventListeners) {
                    ((Consumer<T>) listener).accept(event);
                }
            }
            current = current.getSuperclass();
        }

        if (event instanceof CancelableEvent) {
            return ((CancelableEvent) event).isCanceled();
        }
        return false;
    }
}
