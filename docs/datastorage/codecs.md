# Codec Mapping & Serialization

Minecraft 1.21.1 uses Data Codecs (via Mojang's Serialization library) and Network Stream Codecs (`StreamCodec`) to manage the loading, saving, and network transmission of registry objects. This includes block states, items, entities, and crucially, recipes.

ChainLoader provides a dynamic remapping and runtime proxying system to bridge legacy recipe serializers (which read JSON files directly via `JsonObject` and write/read network packets via raw `FriendlyByteBuf` buffers) to modern `MapCodec` and `StreamCodec` instances.

---

## 1. Recipe Serializer Codec Requirements

In modern Minecraft, all recipe types must register a `RecipeSerializer` that provides:
1.  **`MapCodec<Recipe>`**: Returned by the `codec()` method, used for loading recipe definitions from data packs (JSON).
2.  **`StreamCodec<RegistryFriendlyByteBuf, Recipe>`**: Returned by the `streamCodec()` method, used for synchronizing recipes to the client.

In legacy versions (e.g. 1.20 and prior), recipe serializers only declared methods like:
*   `read(ResourceLocation id, JsonObject json)`
*   `read(ResourceLocation id, FriendlyByteBuf buf)`
*   `write(FriendlyByteBuf buf, T recipe)`

---

## 2. Bytecode Injection via InterfaceCodecBridgeVisitor

ChainLoader intercepts the loading of classes implementing `net/minecraft/world/item/crafting/RecipeSerializer` (or obfuscated counterparts like `cze`, `net/minecraft/class_1865`) and injects default concrete implementations for abstract codec methods:

```java
// BytecodeTransformer.java - InterfaceCodecBridgeVisitor
if (isInterface && isRecipeSerializer && ("a".equals(name) || "codec".equals(name)) && "()Lcom/mojang/serialization/MapCodec;".equals(descriptor) && (access & org.objectweb.asm.Opcodes.ACC_ABSTRACT) != 0) {
    int newAccess = access & ~org.objectweb.asm.Opcodes.ACC_ABSTRACT;
    org.objectweb.asm.MethodVisitor mv = super.visitMethod(newAccess, name, descriptor, signature, exceptions);
    mv.visitCode();
    mv.visitVarInsn(org.objectweb.asm.Opcodes.ALOAD, 0); // Load 'this'
    mv.visitMethodInsn(org.objectweb.asm.Opcodes.INVOKESTATIC, "net/chainloader/loader/compat/bridge/EventBridgeHelper", "getRecipeSerializerCodec", "(Ljava/lang/Object;)Lcom/mojang/serialization/MapCodec;", false);
    mv.visitInsn(org.objectweb.asm.Opcodes.ARETURN);
    mv.visitMaxs(1, 1);
    mv.visitEnd();
    modified = true;
    return null;
}
```

This replaces abstract interface methods with a static delegator calling into `EventBridgeHelper`.

---

## 3. Legacy Recipe Codec Bridges

The delegates return custom implementations of `MapCodec` and `StreamCodec` that use Java Reflection to find and invoke the legacy mod's serialization methods.

### 3.1. LegacyRecipeMapCodec

Maps modern GDF `DynamicOps` and `MapLike` structures to GSON `JsonObject` for legacy readers.

```java
public static class LegacyRecipeMapCodec extends com.mojang.serialization.MapCodec<Object> {
    private final Object serializer;

    public LegacyRecipeMapCodec(Object serializer) {
        this.serializer = serializer;
    }

    @Override
    public <S> com.mojang.serialization.DataResult<Object> decode(com.mojang.serialization.DynamicOps<S> ops, com.mojang.serialization.MapLike<S> input) {
        try {
            java.util.stream.Stream<com.mojang.datafixers.util.Pair<S, S>> entriesStream = input.entries();
            java.util.Map<S, S> map = new java.util.HashMap<>();
            entriesStream.forEach(pair -> map.put(pair.getFirst(), pair.getSecond()));
            S mapVal = ops.createMap(map);
            com.google.gson.JsonElement jsonElement = ops.convertTo(com.mojang.serialization.JsonOps.INSTANCE, mapVal);
            
            if (jsonElement.isJsonObject()) {
                com.google.gson.JsonObject jsonObject = jsonElement.getAsJsonObject();
                java.lang.reflect.Method readMethod = null;
                for (java.lang.reflect.Method m : serializer.getClass().getMethods()) {
                    if (m.getName().equals("read") || m.getName().equals("m_6729_")) {
                        Class<?>[] params = m.getParameterTypes();
                        if (params.length == 2 && params[0].getName().contains("ResourceLocation") && params[1].getName().contains("JsonObject")) {
                            readMethod = m;
                            break;
                        }
                    }
                }
                if (readMethod != null) {
                    readMethod.setAccessible(true);
                    Object dummyLoc = new net.minecraft.resources.ResourceLocation("minecraft", "dummy_recipe");
                    Object recipe = readMethod.invoke(serializer, dummyLoc, jsonObject);
                    return com.mojang.serialization.DataResult.success(recipe);
                }
            }
        } catch (Throwable t) {
            t.printStackTrace();
            return com.mojang.serialization.DataResult.error(t::getMessage);
        }
        return com.mojang.serialization.DataResult.error(() -> "Failed to decode legacy recipe using " + serializer.getClass().getName());
    }

    @Override
    public <S> com.mojang.serialization.RecordBuilder<S> encode(Object input, com.mojang.serialization.DynamicOps<S> ops, com.mojang.serialization.RecordBuilder<S> prefix) {
        return prefix; // Legacy recipes are read-only (saving back to json is handled by developers manually)
    }

    @Override
    public <S> java.util.stream.Stream<S> keys(com.mojang.serialization.DynamicOps<S> ops) {
        return java.util.stream.Stream.empty();
    }
}
```

### 3.2. LegacyRecipeStreamCodec

Decodes packets by searching for single-argument `read(FriendlyByteBuf)` or two-argument `read(ResourceLocation, FriendlyByteBuf)` methods, and encodes packets by calling `write(FriendlyByteBuf, Recipe)` on the legacy serializer.

```java
public static class LegacyRecipeStreamCodec implements net.minecraft.network.codec.StreamCodec<net.minecraft.network.FriendlyByteBuf, Object> {
    private final Object serializer;

    public LegacyRecipeStreamCodec(Object serializer) {
        this.serializer = serializer;
    }

    @Override
    public Object decode(net.minecraft.network.FriendlyByteBuf buf) {
        try {
            java.lang.reflect.Method readMethod = null;
            for (java.lang.reflect.Method m : serializer.getClass().getMethods()) {
                if (m.getName().equals("read") || m.getName().equals("m_6729_")) {
                    Class<?>[] params = m.getParameterTypes();
                    if (params.length == 1 && params[0].getName().contains("FriendlyByteBuf")) {
                        readMethod = m;
                        break;
                    }
                }
            }
            if (readMethod == null) {
                // Fallback to two-argument read method
                for (java.lang.reflect.Method m : serializer.getClass().getMethods()) {
                    if (m.getName().equals("read") || m.getName().equals("m_6729_")) {
                        Class<?>[] params = m.getParameterTypes();
                        if (params.length == 2 && params[0].getName().contains("ResourceLocation") && params[1].getName().contains("FriendlyByteBuf")) {
                            readMethod = m;
                            break;
                        }
                    }
                }
                if (readMethod != null) {
                    readMethod.setAccessible(true);
                    Object dummyLoc = new net.minecraft.resources.ResourceLocation("minecraft", "dummy_recipe");
                    return readMethod.invoke(serializer, dummyLoc, buf);
                }
            } else {
                readMethod.setAccessible(true);
                return readMethod.invoke(serializer, buf);
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
        throw new RuntimeException("Failed to decode legacy recipe packet using " + serializer.getClass().getName());
    }

    @Override
    public void encode(net.minecraft.network.FriendlyByteBuf buf, Object value) {
        try {
            java.lang.reflect.Method writeMethod = null;
            for (java.lang.reflect.Method m : serializer.getClass().getMethods()) {
                if (m.getName().equals("write") || m.getName().equals("m_6730_")) {
                    Class<?>[] params = m.getParameterTypes();
                    if (params.length == 2 && params[0].getName().contains("FriendlyByteBuf")) {
                        writeMethod = m;
                        break;
                    }
                }
            }
            if (writeMethod != null) {
                writeMethod.setAccessible(true);
                writeMethod.invoke(serializer, buf, value);
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}
```
