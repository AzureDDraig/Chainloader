package net.chainloader.api.event;

import java.util.List;
import java.util.ArrayList;

public class ChainEvents {
    public static class Event<T> {
        private final List<T> listeners = new ArrayList<>();
        private T invoker;
        private Class<?> cachedInterface;

        public Event() {}
        public Event(Class<T> type, Object merger) {
            this.cachedInterface = type;
        }

        public void register(T listener) {
            if (listener != null) {
                listeners.add(listener);
                if (cachedInterface == null) {
                    for (Class<?> iface : listener.getClass().getInterfaces()) {
                        cachedInterface = iface;
                        break;
                    }
                    if (cachedInterface == null && listener.getClass().getInterfaces().length > 0) {
                        cachedInterface = listener.getClass().getInterfaces()[0];
                    }
                }
                invoker = null; // Clear cached invoker
            }
        }

        @SuppressWarnings("unchecked")
        public T invoker() {
            if (invoker == null) {
                Class<?> iface = cachedInterface;
                if (iface == null) {
                    return (T) java.lang.reflect.Proxy.newProxyInstance(
                        ChainEvents.class.getClassLoader(),
                        new Class<?>[]{ Runnable.class },
                        (proxy, method, args) -> null
                    );
                }
                invoker = (T) java.lang.reflect.Proxy.newProxyInstance(
                    iface.getClassLoader(),
                    new Class<?>[]{ iface },
                    (proxy, method, args) -> {
                        for (T listener : listeners) {
                            try {
                                method.invoke(listener, args);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                        if (method.getReturnType() == boolean.class) {
                            return true;
                        }
                        return null;
                    }
                );
            }
            return invoker;
        }
    }
}
