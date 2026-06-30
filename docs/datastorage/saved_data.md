# Level Saved Data & BlockEntity NBT Shims

In Minecraft 1.21.1, the world save system requires registry access when loading or writing world data (`SavedData` and `BlockEntity`). Methods that previously accepted only a `CompoundTag` now require a second parameter: `HolderLookup.Provider`.

ChainLoader uses bytecode transformation to dynamically bridge legacy subclasses (which override the older single-argument methods) to the modern multi-argument signatures.

---

## 1. The SavedData / BlockEntity API Shift

In older Minecraft versions, saving/loading was decoupled from registry access:
```java
// Legacy Signatures
public abstract CompoundTag save(CompoundTag nbt);
public void load(CompoundTag nbt);
```

In 1.21.1, registries are required to deserialize complex data (e.g. items, fluid stacks):
```java
// Modern Signatures
public abstract CompoundTag save(CompoundTag nbt, HolderLookup.Provider registries);
public void load(CompoundTag nbt, HolderLookup.Provider registries);
```

---

## 2. Dynamic Bridge Injection via SavedDataBridgeVisitor

`BytecodeTransformer.java` defines `SavedDataBridgeVisitor` which checks if a loaded class is a subclass of `BlockEntity` or `SavedData`. If it overrides the legacy 1-argument method but lacks the 2-argument counterpart, the visitor dynamically generates the 2-argument bridge.

### Bridge Logic flow
When a modern game cycle calls the 2-argument `save` or `load` method on a class:
1. The injected method catches the `HolderLookup.Provider` parameter.
2. It sets the provider in a thread-local store: `EventBridgeHelper.setCurrentNbtProvider(provider)`.
3. It delegates execution to the mod's legacy 1-argument method.
4. It clears the thread-local store: `EventBridgeHelper.setCurrentNbtProvider(null)`.

### Injected Bytecode Example (BlockEntity loadAdditional Bridge)
```java
// Injected loadAdditional(CompoundTag, HolderLookup.Provider) Method:
mv.visitCode();

// EventBridgeHelper.setCurrentNbtProvider(provider)
mv.visitVarInsn(org.objectweb.asm.Opcodes.ALOAD, 2);
mv.visitMethodInsn(org.objectweb.asm.Opcodes.INVOKESTATIC, "net/chainloader/loader/compat/bridge/EventBridgeHelper", "setCurrentNbtProvider", "(Ljava/lang/Object;)V", false);

// super.loadAdditional(tag, provider)
mv.visitVarInsn(org.objectweb.asm.Opcodes.ALOAD, 0);
mv.visitVarInsn(org.objectweb.asm.Opcodes.ALOAD, 1);
mv.visitVarInsn(org.objectweb.asm.Opcodes.ALOAD, 2);
mv.visitMethodInsn(org.objectweb.asm.Opcodes.INVOKESPECIAL, superName, loadName, desc2V, false);

// this.load(tag) (calls the legacy mod's implementation, dynamically resolved)
mv.visitVarInsn(org.objectweb.asm.Opcodes.ALOAD, 0);
mv.visitVarInsn(org.objectweb.asm.Opcodes.ALOAD, 1);
mv.visitMethodInsn(org.objectweb.asm.Opcodes.INVOKEVIRTUAL, name, blockEntityLoad1, desc1V, false);

// EventBridgeHelper.setCurrentNbtProvider(null)
mv.visitInsn(org.objectweb.asm.Opcodes.ACONST_NULL);
mv.visitMethodInsn(org.objectweb.asm.Opcodes.INVOKESTATIC, "net/chainloader/loader/compat/bridge/EventBridgeHelper", "setCurrentNbtProvider", "(Ljava/lang/Object;)V", false);

mv.visitInsn(org.objectweb.asm.Opcodes.RETURN);
mv.visitMaxs(3, 3);
mv.visitEnd();
```

> [!NOTE]
> **Dynamic Name Resolution**: The visitor resolves the correct names of the 1-argument methods dynamically (e.g. `load`/`saveAdditional` in Mojang-mapped dev environments vs `a`/`b` in obfuscated production environments) by querying the `Remapper` instead of relying on hardcoded obfuscated names. This prevents runtime `NoSuchMethodError` crashes when loading or saving NBT data.

---

## 3. ThreadLocal Registry Context

When the legacy mod executes its 1-argument method, it may call other Minecraft methods that require a registry provider (e.g. constructing an `ItemStack` from NBT). 

Inside these redirected methods, ChainLoader retrieves the active provider from the `ThreadLocal` context:

### EventBridgeHelper Registry Fetch
```java
public static final ThreadLocal<Object> CURRENT_NBT_PROVIDER = new ThreadLocal<>();

public static Object getCurrentNbtProvider() {
    return CURRENT_NBT_PROVIDER.get();
}

public static void setCurrentNbtProvider(Object provider) {
    if (provider == null) {
        CURRENT_NBT_PROVIDER.remove();
    } else {
        CURRENT_NBT_PROVIDER.set(provider);
    }
}
```

If the `ThreadLocal` is empty (e.g. legacy NBT parsing occurs outside a standard save cycle), `EventBridgeHelper.getRegistries()` falls back to querying the active server or client level:

```java
public static net.minecraft.core.HolderLookup.Provider getRegistries() {
    if (currentServer != null) {
        return ((net.minecraft.server.MinecraftServer) currentServer).registryAccess();
    }
    try {
        net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
        if (client != null && client.level != null) {
            return client.level.registryAccess();
        }
    } catch (Throwable t) {
        // Ignore
    }
    return net.minecraft.core.RegistryAccess.EMPTY;
}
```
This multi-tiered lookup ensures that legacy code can safely perform registry-dependent NBT operations under all circumstances.

---

## 4. DimensionDataStorage Redirects

Minecraft 1.21.1 modified the signatures of `DimensionDataStorage` methods:
- Legacy `get(Function<CompoundTag, T> readFunction, String name)` became `get(SavedData.Factory<T> factory, String name)`.
- Legacy `computeIfAbsent(Function<CompoundTag, T> readFunction, Supplier<T> supplier, String name)` became `computeIfAbsent(SavedData.Factory<T> factory, String name)`.

To prevent legacy mods (such as Waystones) from throwing `NoSuchMethodError` when retrieving persistent data, ChainLoader's remapper intercepts calls to these legacy methods and redirects them to compatibility wrappers in `EventBridgeHelper`:

- **Redirection Rule**:
  - Intercepts INVOKEVIRTUAL calls on `DimensionDataStorage` (or obfuscated `eqz` or Yarn `net/minecraft/class_26`) for `computeIfAbsent` / `getOrCreate` (Yarn `method_17924`) and `get` (Yarn `method_120`).
  - Redirects them to INVOKESTATIC `EventBridgeHelper.computeIfAbsent` and `EventBridgeHelper.get` respectively.
- **Wrappers**:
  - `EventBridgeHelper.computeIfAbsent(Object storageObj, Function constructor, Function readFunction, String name)`
  - `EventBridgeHelper.get(Object storageObj, Function readFunction, String name)`
  - These wrappers wrap the legacy `readFunction` and `constructor` callbacks inside a modern 1.21.1 `SavedData.Factory` and call the modern `DimensionDataStorage` methods cleanly.

