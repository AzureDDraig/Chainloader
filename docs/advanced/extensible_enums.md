# Extensible Enums & Dynamic Enum Injection

Enums in Java are compiled as final classes inheriting from `java.lang.Enum`. To preserve safety, the JVM restricts the instantiation of enums, and compilers generate an immutable array (`$VALUES`) to store the set of enum constants. 

However, Minecraft mods frequently require extending enums at runtime (e.g. adding custom `ArmorMaterial`, `ToolMaterial`, `CreativeModeTab$Row`, or `Rarity` constants). ChainLoader provides dynamic enum expansion capabilities by combining bytecode access modifications with JVM reflection/unsafe wrappers.

---

## 1. JVM Bytecode Structure of Enums

When a Java compiler parses an enum definition:
1.  The class is marked `ACC_FINAL` and `ACC_ENUM`.
2.  A static final array field `$VALUES` is generated.
3.  A static method `values()` is generated:
    ```bytecode
    public static my/package/MyEnum[] values();
      Code:
        getstatic     my/package/MyEnum.$VALUES : [Lmy/package/MyEnum;
        invokevirtual [Lmy/package/MyEnum;.clone : ()Ljava/lang/Object;
        checkcast     "[Lmy/package/MyEnum;"
        areturn
    ```
4.  Constructors are marked private and compile-time restricted.

---

## 2. Dynamic Enum Extension Mechanism

To inject a new enum constant, a modloader must bypass the compiler's safety checks. ChainLoader carries this out via three coordinated steps:

### Step 1: Access Widening the $VALUES field
The `$VALUES` array is normally marked `private static final`. To make it mutable, ChainLoader matches it against the compiled `AccessWidener` rules, which strips the `final` modifier, changing the field access to `public static`.

### Step 2: Unsafe Instantiation
Instantiating an enum constructor via standard reflection (`Constructor.newInstance`) throws `IllegalArgumentException: Cannot reflectively create Enum objects`. 

Instead, ChainLoader uses `sun.misc.Unsafe.allocateInstance(Class)` or mirrors Mojang's internal constructor accessor fields to instantiate the new enum object in memory without invoking validation code:

```java
// Conceptual Enum Injection Logic
public static <T extends Enum<T>> T createEnumInstance(Class<T> enumClass, String name, int ordinal, Class<?>[] paramTypes, Object[] paramValues) {
    try {
        // 1. Fetch Constructor
        java.lang.reflect.Constructor<?> constructor = getEnumConstructor(enumClass, paramTypes);
        constructor.setAccessible(true);
        
        // 2. Instantiate using Unsafe or reflections
        T enumInstance = (T) constructor.newInstance(paramValues);
        return enumInstance;
    } catch (Exception e) {
        throw new RuntimeException("Failed to instantiate enum dynamically", e);
    }
}
```

### Step 3: Array Reallocation & Field Swapping
Once the instance is created, the `$VALUES` array must be expanded and updated:

```java
public static <T extends Enum<T>> void injectEnumConstant(Class<T> enumClass, T newConstant) {
    try {
        java.lang.reflect.Field valuesField = null;
        for (java.lang.reflect.Field f : enumClass.getDeclaredFields()) {
            if (f.getName().equals("$VALUES") || f.getType().isArray()) {
                valuesField = f;
                break;
            }
        }
        
        valuesField.setAccessible(true);
        T[] original = (T[]) valuesField.get(null);
        
        // Create new array with size + 1
        T[] expanded = (T[]) java.lang.reflect.Array.newInstance(enumClass, original.length + 1);
        System.arraycopy(original, 0, expanded, 0, original.length);
        expanded[original.length] = newConstant;
        
        // Swap arrays
        valuesField.set(null, expanded);
        
        // Clear JVM Class cache directories
        clearClassCache(enumClass);
    } catch (Exception e) {
        e.printStackTrace();
    }
}
```

---

## 3. JVM Class Caching Cleanups

The JVM caches enum constants inside the `Class` object when `Class.getEnumConstants()` is first called. Failure to invalidate this cache causes the runtime to ignore the newly injected constant.

ChainLoader cleans up these internal caches using Reflection:

```java
private static void clearClassCache(Class<?> enumClass) {
    try {
        java.lang.reflect.Field enumConstantsField = Class.class.getDeclaredField("enumConstants");
        enumConstantsField.setAccessible(true);
        enumConstantsField.set(enumClass, null);
        
        java.lang.reflect.Field enumConstantDirectoryField = Class.class.getDeclaredField("enumConstantDirectory");
        enumConstantDirectoryField.setAccessible(true);
        enumConstantDirectoryField.set(enumClass, null);
    } catch (Exception ignored) {
        // Fail-silent on JVM variations (e.g. Android or custom runtimes)
    }
}
```
This ensures that any calls to `Enum.values()`, `Class.getEnumConstants()`, or serialization deserialization libraries will immediately recognize the newly injected mod constants.
