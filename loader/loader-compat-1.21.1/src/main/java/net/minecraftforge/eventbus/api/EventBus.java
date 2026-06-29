package net.minecraftforge.eventbus.api;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Mock implementation of the Forge/NeoForge EventBus.
 */
public class EventBus implements IEventBus {
    private final List<Consumer<Event>> generalListeners = new ArrayList<>();
    private final ConcurrentHashMap<Class<? extends Event>, List<Consumer<? extends Event>>> listeners = new ConcurrentHashMap<>();

    @Override
    public void register(Object target) {
        if (target == null) return;
        
        Class<?> clazz = target instanceof Class ? (Class<?>) target : target.getClass();
        boolean isStatic = target instanceof Class;

        for (Method method : clazz.getDeclaredMethods()) {
            if (method.isAnnotationPresent(SubscribeEvent.class)) {
                if (method.getParameterCount() != 1) {
                    continue;
                }
                
                if (isStatic && !Modifier.isStatic(method.getModifiers())) {
                    continue;
                }
                if (!isStatic && Modifier.isStatic(method.getModifiers())) {
                    continue;
                }

                Class<?> paramType = method.getParameterTypes()[0];
                if (Event.class.isAssignableFrom(paramType)) {
                    @SuppressWarnings("unchecked")
                    Class<? extends Event> eventType = (Class<? extends Event>) paramType;
                    
                    method.setAccessible(true);
                    addListener(eventType, event -> {
                        try {
                            method.invoke(isStatic ? null : target, event);
                        } catch (Exception e) {
                            throw new RuntimeException("Error invoking event handler " + method.getName(), e);
                        }
                    });
                }
            }
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends Event> void addListener(Consumer<T> consumer) {
        generalListeners.add((Consumer<Event>) consumer);
    }

    public <T extends Event> void addListener(Class<T> eventType, Consumer<T> consumer) {
        listeners.computeIfAbsent(eventType, k -> new ArrayList<>()).add(consumer);
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean post(Event event) {
        if (event == null) return false;

        Class<?> current = event.getClass();
        if (current.getName().contains("ModelEvent$RegisterAdditional")) {
            try {
                Class<?> helper = Class.forName("net.chainloader.loader.compat.bridge.EventBridgeHelper", true, current.getClassLoader());
                helper.getMethod("bridgeFabricModelLoading", Object.class).invoke(null, event);
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
        while (Event.class.isAssignableFrom(current)) {
            List<Consumer<? extends Event>> eventListeners = listeners.get(current);
            if (eventListeners != null) {
                for (Consumer<? extends Event> listener : eventListeners) {
                    ((Consumer<Event>) listener).accept(event);
                }
            }
            current = current.getSuperclass();
        }

        for (Consumer<Event> listener : generalListeners) {
            try {
                listener.accept(event);
            } catch (ClassCastException e) {
                if (isParameterMismatch(e, event)) {
                    continue;
                }
                e.printStackTrace();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return event.isCancelable() && event.isCanceled();
    }

    private boolean isParameterMismatch(ClassCastException e, Object event) {
        String msg = e.getMessage();
        if (msg == null) {
            return e.getStackTrace().length == 0;
        }
        String eventName = event.getClass().getName();
        if (msg.contains(eventName) && msg.contains("cannot be cast to")) {
            return true;
        }
        return false;
    }
}
