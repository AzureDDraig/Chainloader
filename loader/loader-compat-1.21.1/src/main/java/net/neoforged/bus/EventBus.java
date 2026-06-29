package net.neoforged.bus;

import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class EventBus implements IEventBus {
    private static final boolean DEBUG = false;
    private final List<Consumer<Object>> generalListeners = new ArrayList<>();
    private final Map<Class<?>, List<Consumer<Object>>> listeners = new ConcurrentHashMap<>();

    @Override
    public void register(Object target) {
        if (target == null) return;
        
        Class<?> clazz = target instanceof Class ? (Class<?>) target : target.getClass();
        boolean isStatic = target instanceof Class;

        if (DEBUG) {
            System.out.println("[DEBUG EVENTBUS] [" + System.identityHashCode(this) + "] Registering target: " + clazz.getName() + " loaded by: " + clazz.getClassLoader());
        }
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
                if (DEBUG) {
                    System.out.println("[DEBUG EVENTBUS] [" + System.identityHashCode(this) + "]   Found listener method: " + method.getName() + " with param: " + paramType.getName() + " loaded by: " + paramType.getClassLoader());
                }
                method.setAccessible(true);
                addListener(paramType, event -> {
                    try {
                        method.invoke(isStatic ? null : target, event);
                    } catch (Exception e) {
                        System.err.println("Error invoking NeoForge event handler " + method.getName() + ":");
                        e.printStackTrace();
                    }
                });
            }
        }
    }

    @Override
    public void addListener(Consumer<?> consumer) {
        generalListeners.add((Consumer<Object>) consumer);
    }

    @Override
    public void addListener(Object consumer) {
        if (consumer instanceof Consumer) {
            generalListeners.add((Consumer<Object>) consumer);
        }
    }

    @Override
    public <T> void addListener(EventPriority priority, boolean receiveCancelled, Class<T> eventType, Consumer<T> consumer) {
        addListener(eventType, (Consumer<T>) consumer);
    }

    @Override
    public <T> void addListener(EventPriority priority, Class<T> eventType, Consumer<T> consumer) {
        addListener(eventType, (Consumer<T>) consumer);
    }

    public <T> void addListener(Class<T> eventType, Consumer<T> consumer) {
        listeners.computeIfAbsent(eventType, k -> new ArrayList<>()).add((Consumer<Object>) consumer);
    }

    @Override
    public boolean post(Object event) {
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
        if (DEBUG) {
            System.out.println("[DEBUG EVENTBUS] [" + System.identityHashCode(this) + "] Posting event: " + current.getName() + " loaded by: " + current.getClassLoader());
        }
        while (current != null && current != Object.class) {
            List<Consumer<Object>> eventListeners = listeners.get(current);
            if (DEBUG) {
                System.out.println("[DEBUG EVENTBUS] [" + System.identityHashCode(this) + "]   Checking listeners for: " + current.getName() + " (has listeners? " + (eventListeners != null) + ")");
                for (Class<?> key : listeners.keySet()) {
                    System.out.println("[DEBUG EVENTBUS] [" + System.identityHashCode(this) + "]     Key in map: " + key.getName() + " loaded by: " + key.getClassLoader() + " (equals? " + key.equals(current) + ")");
                }
            }
            if (eventListeners != null) {
                // Use a copy to avoid ConcurrentModificationException if a listener modifies the bus
                List<Consumer<Object>> copy = new ArrayList<>(eventListeners);
                for (Consumer<Object> listener : copy) {
                    try {
                        listener.accept(event);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
            current = current.getSuperclass();
        }

        List<Consumer<Object>> generalCopy = new ArrayList<>(generalListeners);
        for (Consumer<Object> listener : generalCopy) {
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

        if (event instanceof Event) {
            Event ev = (Event) event;
            return ev.isCancelable() && ev.isCanceled();
        }
        return false;
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
