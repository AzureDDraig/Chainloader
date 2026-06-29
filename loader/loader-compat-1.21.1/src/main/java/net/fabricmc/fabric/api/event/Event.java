package net.fabricmc.fabric.api.event;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

public class Event<T> {
    private final List<T> listeners = new ArrayList<>();
    private T invoker;
    private Class<?> cachedInterface;

    public Event() {
        try {
            java.lang.reflect.Type superclass = getClass().getGenericSuperclass();
            if (superclass instanceof java.lang.reflect.ParameterizedType) {
                java.lang.reflect.Type[] params = ((java.lang.reflect.ParameterizedType) superclass).getActualTypeArguments();
                if (params.length > 0 && params[0] instanceof Class) {
                    this.cachedInterface = (Class<?>) params[0];
                    return;
                }
            }
        } catch (Throwable t) {
            // Ignore
        }
        this.cachedInterface = null;
    }

    public Event(Class<?> type) {
        this.cachedInterface = type;
    }

    public void register(T listener) {
        if (listener != null) {
            listeners.add(listener);
            if (cachedInterface == null) {
                // Find interface implemented by this listener
                for (Class<?> iface : listener.getClass().getInterfaces()) {
                    if (iface.isAnnotationPresent(FunctionalInterface.class) || iface.getMethods().length > 0) {
                        cachedInterface = iface;
                        break;
                    }
                }
                if (cachedInterface == null && listener.getClass().getInterfaces().length > 0) {
                    cachedInterface = listener.getClass().getInterfaces()[0];
                }
            }
            invoker = null; // Invalidate cache
        }
    }

    @SuppressWarnings("unchecked")
    public T invoker() {
        if (invoker == null) {
            Class<?> iface = cachedInterface;
            if (iface == null) {
                // Return a dummy proxy implementing Runnable so we don't return null
                return (T) Proxy.newProxyInstance(
                    Event.class.getClassLoader(),
                    new Class<?>[]{ Runnable.class },
                    (proxy, method, args) -> null
                );
            }
            invoker = (T) Proxy.newProxyInstance(
                iface.getClassLoader(),
                new Class<?>[]{ iface },
                (proxy, method, args) -> {
                    Object lastResult = null;
                    for (T listener : listeners) {
                        try {
                            try {
                                lastResult = method.invoke(listener, args);
                            } catch (Throwable t) {
                                Throwable actual = t;
                                if (t instanceof java.lang.reflect.InvocationTargetException) {
                                    actual = ((java.lang.reflect.InvocationTargetException) t).getTargetException();
                                }
                                if (actual instanceof IllegalArgumentException || 
                                    actual instanceof AbstractMethodError || 
                                    actual instanceof NoSuchMethodError) {
                                    // Try to adapt arguments for other methods on the listener class with same name
                                    boolean invoked = false;
                                    Class<?> current = listener.getClass();
                                    while (current != null && !invoked) {
                                        for (java.lang.reflect.Method m : current.getDeclaredMethods()) {
                                            if (m.getName().equals(method.getName())) {
                                                Object[] adaptedArgs = adaptArguments(args, m.getParameterTypes());
                                                if (adaptedArgs != null) {
                                                    m.setAccessible(true);
                                                    lastResult = m.invoke(listener, adaptedArgs);
                                                    invoked = true;
                                                    break;
                                                }
                                            }
                                        }
                                        current = current.getSuperclass();
                                    }
                                    if (!invoked) {
                                        if (actual instanceof RuntimeException) throw (RuntimeException) actual;
                                        if (actual instanceof Error) throw (Error) actual;
                                        throw new RuntimeException(actual);
                                    }
                                } else {
                                    if (actual instanceof RuntimeException) throw (RuntimeException) actual;
                                    if (actual instanceof Error) throw (Error) actual;
                                    throw new RuntimeException(actual);
                                }
                            }
                        } catch (Throwable e) {
                            e.printStackTrace();
                        }
                    }
                    if (method.getReturnType() == boolean.class) {
                        return lastResult != null ? lastResult : true;
                    }
                    return lastResult;
                }
            );
        }
        return invoker;
    }

    private static Object[] adaptArguments(Object[] args, Class<?>[] paramTypes) {
        if (args == null) return new Object[0];
        Object[] result = new Object[paramTypes.length];
        int argIdx = 0;
        for (int i = 0; i < paramTypes.length; i++) {
            Class<?> pType = paramTypes[i];
            boolean found = false;
            while (argIdx < args.length) {
                Object arg = args[argIdx++];
                if (arg == null || pType.isAssignableFrom(arg.getClass()) || 
                    (pType == boolean.class && arg instanceof Boolean) ||
                    (pType == int.class && arg instanceof Integer) ||
                    (pType == float.class && arg instanceof Float) ||
                    (pType == double.class && arg instanceof Double)) {
                    result[i] = arg;
                    found = true;
                    break;
                }
            }
            if (!found) {
                return null;
            }
        }
        return result;
    }
}

