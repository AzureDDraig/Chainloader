package net.chainloader.loader.shim;

import sun.misc.Unsafe;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * JavaVersionShim acts as a compatibility layer and version boundary bridge for ChainLoader.
 * 
 * <p>Under modern Java runtimes (Java 9, 16, 17, 21+), access to class internals is restricted
 * by JVM modularization and strong encapsulation. Traditional reflection calls like
 * {@code setAccessible(true)} throw {@code InaccessibleObjectException} or emit strong warnings.
 * </p>
 * 
 * <p>This shim bridges the version boundaries by mapping legacy reflective operations to:
 * <ol>
 *   <li><b>VarHandles:</b> The modern, performant JDK 9+ alternative for safe field access.</li>
 *   <li><b>sun.misc.Unsafe offsets:</b> The ultimate JVM bypass that operates directly on memory addresses,
 *       circumventing module boundaries and access checks completely.</li>
 * </ol>
 * </p>
 * 
 * <p>Fallbacks are evaluated dynamically:
 * <pre>
 *   Reflection (setAccessible) -> VarHandles (via Trusted Lookup) -> Unsafe memory offsets
 * </pre>
 * </p>
 */
public final class JavaVersionShim {
    private static final Logger LOGGER = Logger.getLogger(JavaVersionShim.class.getName());

    private static final Unsafe UNSAFE;
    private static final MethodHandles.Lookup TRUSTED_LOOKUP;
    private static final boolean HAS_VAR_HANDLES;
    private static final long ACCESSIBLE_OVERRIDE_OFFSET;

    static {
        // 1. Resolve sun.misc.Unsafe
        Unsafe unsafeTemp = null;
        try {
            Field theUnsafeField = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafeField.setAccessible(true);
            unsafeTemp = (Unsafe) theUnsafeField.get(null);
        } catch (Throwable t) {
            LOGGER.log(Level.WARNING, "Failed to resolve sun.misc.Unsafe via reflection", t);
        }
        UNSAFE = unsafeTemp;

        // 2. Resolve Trusted MethodHandles.Lookup (IMPL_LOOKUP)
        MethodHandles.Lookup lookupTemp = null;
        if (UNSAFE != null) {
            try {
                // IMPL_LOOKUP is a package-private static lookup within MethodHandles.Lookup
                // that possesses full privilege access (trusted lookup).
                Field implLookupField = MethodHandles.Lookup.class.getDeclaredField("IMPL_LOOKUP");
                Object base = UNSAFE.staticFieldBase(implLookupField);
                long offset = UNSAFE.staticFieldOffset(implLookupField);
                lookupTemp = (MethodHandles.Lookup) UNSAFE.getObject(base, offset);
            } catch (Throwable t) {
                LOGGER.log(Level.WARNING, "Failed to retrieve IMPL_LOOKUP using Unsafe, falling back to standard lookup", t);
            }
        }
        if (lookupTemp == null) {
            lookupTemp = MethodHandles.lookup();
        }
        TRUSTED_LOOKUP = lookupTemp;

        // 3. Check for VarHandle presence (Java 9+)
        boolean varHandlesPresent = false;
        try {
            Class.forName("java.lang.invoke.VarHandle");
            varHandlesPresent = true;
        } catch (ClassNotFoundException e) {
            LOGGER.info("VarHandles are not available on this JVM version (likely Java 8).");
        }
        HAS_VAR_HANDLES = varHandlesPresent;

        // 4. Resolve the 'override' field offset in AccessibleObject
        long overrideOffsetTemp = -1;
        if (UNSAFE != null) {
            try {
                Field overrideField = AccessibleObject.class.getDeclaredField("override");
                overrideOffsetTemp = UNSAFE.objectFieldOffset(overrideField);
            } catch (Throwable t) {
                LOGGER.log(Level.FINE, "Failed to find 'override' field in AccessibleObject", t);
            }
        }
        ACCESSIBLE_OVERRIDE_OFFSET = overrideOffsetTemp;

        LOGGER.info(String.format("JavaVersionShim initialized: [Unsafe=%b, TrustedLookup=%s, VarHandles=%b]",
                UNSAFE != null,
                TRUSTED_LOOKUP == MethodHandles.lookup() ? "Standard" : "Trusted",
                HAS_VAR_HANDLES));
    }

    private JavaVersionShim() {}

    /**
     * Attempts to force an {@link AccessibleObject} (Field, Method, Constructor) to be accessible.
     * Maps standard reflection checks directly to Unsafe offset modification if normal reflection throws an exception.
     *
     * @param accessor the object to make accessible
     * @throws RuntimeException if access cannot be forced by any means
     */
    public static void setAccessible(AccessibleObject accessor) {
        try {
            accessor.setAccessible(true);
        } catch (Throwable t) {
            // Under Java 16+, setAccessible(true) will throw InaccessibleObjectException on core JDK classes.
            // We bypass this restriction by directly writing true to the 'override' field using Unsafe.
            if (UNSAFE != null && ACCESSIBLE_OVERRIDE_OFFSET != -1) {
                try {
                    UNSAFE.putBoolean(accessor, ACCESSIBLE_OVERRIDE_OFFSET, true);
                    LOGGER.log(Level.FINE, "Bypassed encapsulation check for {0} using Unsafe override", accessor);
                } catch (Throwable ut) {
                    LOGGER.log(Level.SEVERE, "Failed Unsafe bypass for setAccessible on " + accessor, ut);
                    throw new RuntimeException("Failed to bypass encapsulation boundaries", t);
                }
            } else {
                throw new RuntimeException("Encapsulation boundary violation: unable to bypass setAccessible", t);
            }
        }
    }

    /**
     * Reads a field value from an instance, falling back to VarHandles or Unsafe if reflection is blocked.
     *
     * @param field    the field to retrieve
     * @param instance the object instance (null for static fields)
     * @return the value of the field
     * @throws RuntimeException if all lookup shims fail
     */
    public static Object getFieldValue(Field field, Object instance) {
        // 1. Attempt standard reflection
        try {
            setAccessible(field);
            return field.get(instance);
        } catch (Throwable t) {
            LOGGER.log(Level.FINE, "Reflection read failed for field " + field + "; falling back to modern APIs", t);
        }

        // 2. Fall back to VarHandle (Java 9+)
        if (HAS_VAR_HANDLES) {
            try {
                VarHandle handle = TRUSTED_LOOKUP.unreflectVarHandle(field);
                if (Modifier.isStatic(field.getModifiers())) {
                    return handle.get();
                } else {
                    return handle.get(instance);
                }
            } catch (Throwable t) {
                LOGGER.log(Level.FINE, "VarHandle read failed for field " + field + "; falling back to Unsafe", t);
            }
        }

        // 3. Fall back to Unsafe memory offsets
        if (UNSAFE != null) {
            try {
                if (Modifier.isStatic(field.getModifiers())) {
                    Object base = UNSAFE.staticFieldBase(field);
                    long offset = UNSAFE.staticFieldOffset(field);
                    return getUnsafeValue(base, offset, field.getType());
                } else {
                    long offset = UNSAFE.objectFieldOffset(field);
                    return getUnsafeValue(instance, offset, field.getType());
                }
            } catch (Throwable t) {
                LOGGER.log(Level.SEVERE, "All field read shims failed for: " + field, t);
                throw new RuntimeException("Failed to read field value via reflection, VarHandle, and Unsafe", t);
            }
        }

        throw new UnsupportedOperationException("No access mechanism available on this platform to read field: " + field);
    }

    /**
     * Writes a value to a field, including final fields, bypassing JVM restriction constraints.
     *
     * @param field    the target field
     * @param instance the object instance (null for static fields)
     * @param value    the value to set
     * @throws RuntimeException if all modification shims fail
     */
    public static void setFieldValue(Field field, Object instance, Object value) {
        // 1. Attempt standard reflection (if field is not final)
        try {
            setAccessible(field);
            // Java 12+ prevents modifying final fields via reflection by removing access to the 'modifiers' field.
            // Therefore, we bypass reflection for final fields and proceed to VarHandle/Unsafe.
            if (!Modifier.isFinal(field.getModifiers())) {
                field.set(instance, value);
                return;
            }
        } catch (Throwable t) {
            LOGGER.log(Level.FINE, "Reflection write failed for field " + field + "; attempting bypasses", t);
        }

        // 2. Fall back to VarHandle (Java 9+)
        if (HAS_VAR_HANDLES) {
            try {
                VarHandle handle = TRUSTED_LOOKUP.unreflectVarHandle(field);
                if (Modifier.isStatic(field.getModifiers())) {
                    handle.set(value);
                } else {
                    handle.set(instance, value);
                }
                return;
            } catch (Throwable t) {
                LOGGER.log(Level.FINE, "VarHandle write failed for field " + field + "; falling back to Unsafe", t);
            }
        }

        // 3. Fall back to Unsafe memory offsets
        if (UNSAFE != null) {
            try {
                if (Modifier.isStatic(field.getModifiers())) {
                    Object base = UNSAFE.staticFieldBase(field);
                    long offset = UNSAFE.staticFieldOffset(field);
                    setUnsafeValue(base, offset, field.getType(), value);
                } else {
                    long offset = UNSAFE.objectFieldOffset(field);
                    setUnsafeValue(instance, offset, field.getType(), value);
                }
                return;
            } catch (Throwable t) {
                LOGGER.log(Level.SEVERE, "All field write shims failed for: " + field, t);
                throw new RuntimeException("Failed to write field value via reflection, VarHandle, and Unsafe", t);
            }
        }

        throw new UnsupportedOperationException("No access mechanism available on this platform to write field: " + field);
    }

    /**
     * Invokes a method, using trusted lookups or direct reflection overrides to bypass boundaries.
     *
     * @param method   the method to invoke
     * @param instance the target instance (null for static methods)
     * @param args     arguments for the method invocation
     * @return the result of the invocation
     * @throws RuntimeException if all invocation shims fail
     */
    public static Object invokeMethod(Method method, Object instance, Object... args) {
        // 1. Attempt standard reflection
        try {
            setAccessible(method);
            return method.invoke(instance, args);
        } catch (Throwable t) {
            LOGGER.log(Level.FINE, "Reflection method invocation failed; attempting Trusted Lookup unreflection", t);
            
            // 2. Fall back to unreflecting through Trusted Lookup
            try {
                MethodHandle handle = TRUSTED_LOOKUP.unreflect(method);
                if (!Modifier.isStatic(method.getModifiers())) {
                    handle = handle.bindTo(instance);
                }
                return handle.invokeWithArguments(args);
            } catch (Throwable mt) {
                LOGGER.log(Level.SEVERE, "All method invocation shims failed for: " + method, mt);
                throw new RuntimeException("Failed to invoke method via reflection and MethodHandles", t);
            }
        }
    }

    // --- Unsafe Helpers ---

    private static Object getUnsafeValue(Object target, long offset, Class<?> type) {
        if (!type.isPrimitive()) {
            return UNSAFE.getObject(target, offset);
        }
        if (type == int.class) {
            return UNSAFE.getInt(target, offset);
        }
        if (type == boolean.class) {
            return UNSAFE.getBoolean(target, offset);
        }
        if (type == long.class) {
            return UNSAFE.getLong(target, offset);
        }
        if (type == double.class) {
            return UNSAFE.getDouble(target, offset);
        }
        if (type == float.class) {
            return UNSAFE.getFloat(target, offset);
        }
        if (type == char.class) {
            return UNSAFE.getChar(target, offset);
        }
        if (type == byte.class) {
            return UNSAFE.getByte(target, offset);
        }
        if (type == short.class) {
            return UNSAFE.getShort(target, offset);
        }
        throw new IllegalArgumentException("Unknown primitive type: " + type);
    }

    private static void setUnsafeValue(Object target, long offset, Class<?> type, Object value) {
        if (!type.isPrimitive()) {
            UNSAFE.putObject(target, offset, value);
            return;
        }
        if (type == int.class) {
            UNSAFE.putInt(target, offset, ((Number) value).intValue());
            return;
        }
        if (type == boolean.class) {
            UNSAFE.putBoolean(target, offset, (Boolean) value);
            return;
        }
        if (type == long.class) {
            UNSAFE.putLong(target, offset, ((Number) value).longValue());
            return;
        }
        if (type == double.class) {
            UNSAFE.putDouble(target, offset, ((Number) value).doubleValue());
            return;
        }
        if (type == float.class) {
            UNSAFE.putFloat(target, offset, ((Number) value).floatValue());
            return;
        }
        if (type == char.class) {
            UNSAFE.putChar(target, offset, (Character) value);
            return;
        }
        if (type == byte.class) {
            UNSAFE.putByte(target, offset, ((Number) value).byteValue());
            return;
        }
        if (type == short.class) {
            UNSAFE.putShort(target, offset, ((Number) value).shortValue());
            return;
        }
        throw new IllegalArgumentException("Unknown primitive type: " + type);
    }
}
