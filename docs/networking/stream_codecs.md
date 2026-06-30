# Custom Packet Payloads & Stream Codec Stubbing

Minecraft 1.21.1 relies on Mojang's new network-centric `StreamCodec` interface to encode and decode packet fields on the netty network threads. Custom packets must implement the `CustomPacketPayload` interface and provide a `StreamCodec` signature mapped to their payload type.

To allow legacy mods (which expect direct network buffer writing) to write data, ChainLoader stubs out the modern Mojang stream codec classes and dynamically swaps the game's internal codec registries with a custom mutable mapper.

---

## 1. Stream Codec Stubs

ChainLoader provides stubs for Mojang's codec structures within the `net.minecraft.network.codec` package of the compatibility layer:

*   **`StreamCodec`**: The parent interface containing helper methods like `of(StreamEncoder, StreamDecoder)`.
*   **`StreamEncoder`**: Functional interface representing `(B buf, V value) -> void` serialization.
*   **`StreamDecoder`**: Functional interface representing `(B buf) -> V` deserialization.

### StreamCodec Stub Implementation
```java
package net.minecraft.network.codec;

public interface StreamCodec<B, V> {
    void encode(B buf, V value);
    V decode(B buf);

    static <B, V> StreamCodec<B, V> of(StreamEncoder<B, V> encoder, StreamDecoder<B, V> decoder) {
        return new StreamCodec<B, V>() {
            @Override
            public void encode(B buf, V value) {
                encoder.encode(buf, value);
            }
            @Override
            public V decode(B buf) {
                return decoder.decode(buf);
            }
        };
    }
}
```

---

## 2. ChainloaderPayload Stream Codec

`ChainloaderPayload` utilizes `StreamCodec.of` to serialize and deserialize the raw network byte streams:

```java
public record ChainloaderPayload(ResourceLocation id, byte[] data) implements CustomPacketPayload {
    public static StreamCodec<FriendlyByteBuf, ChainloaderPayload> STREAM_CODEC(ResourceLocation channelId) {
        return StreamCodec.of(
            (buf, payload) -> {
                // Write byte array size followed by bytes
                buf.writeByteArray(payload.data());
            },
            buf -> {
                // Read byte array from the netty buffer
                byte[] bytes = buf.readByteArray();
                return new ChainloaderPayload(channelId, bytes);
            }
        );
    }
    
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return new Type<>(id);
    }
}
```

---

## 3. Dynamic Codec Injection & Map Swapping

In Minecraft 1.21.1, the game's class `CustomPacketPayload` maps custom channel IDs to their corresponding codecs. By default, this map is immutable and fails to register dynamic channels declared by legacy mods at runtime.

To solve this, ChainLoader uses class transformation (`BytecodeTransformer.java`) to intercept the constructor of the payload codec class and injects a call to replace its internal lookup map with a mutable, thread-safe `ConcurrentHashMap`.

### Swap Intercept (BytecodeTransformer.java)
```java
if ("registerCustomPacketPayloadCodecInstance".equals(name)) {
    // Calls EventBridgeHelper.registerCustomPacketPayloadCodecInstance(this, map)
}
```

### Map Swapping and Registration (EventBridgeHelper.java)
```java
private static final java.util.Map<Object, java.util.concurrent.ConcurrentHashMap<net.minecraft.resources.ResourceLocation, Object>> codecInstances = new java.util.concurrent.ConcurrentHashMap<>();

public static void registerCustomPacketPayloadCodecInstance(Object codecInstance, java.util.Map<?, ?> map) {
    System.out.println("[EventBridgeHelper] Registering CustomPacketPayload codec instance: " + codecInstance);
    
    java.util.concurrent.ConcurrentHashMap<net.minecraft.resources.ResourceLocation, Object> mutableMap = new java.util.concurrent.ConcurrentHashMap<>();
    for (java.util.Map.Entry<?, ?> entry : map.entrySet()) {
        mutableMap.put((net.minecraft.resources.ResourceLocation) entry.getKey(), entry.getValue());
    }
    
    // Inject all currently known legacy channels
    for (net.minecraft.resources.ResourceLocation channelId : REGISTERED_CHANNELS) {
        mutableMap.put(channelId, ChainloaderPayload.STREAM_CODEC(channelId));
    }
    
    try {
        java.lang.reflect.Field mapField = null;
        Class<?> clazz = codecInstance.getClass();
        try {
            mapField = clazz.getDeclaredField("a"); // Obfuscated map field
        } catch (NoSuchFieldException e) {
            // Fallback: search by type
            for (java.lang.reflect.Field f : clazz.getDeclaredFields()) {
                if (java.util.Map.class.isAssignableFrom(f.getType())) {
                    mapField = f;
                    break;
                }
            }
        }
        if (mapField != null) {
            mapField.setAccessible(true);
            mapField.set(codecInstance, mutableMap);
            codecInstances.put(codecInstance, mutableMap);
        }
    } catch (Throwable t) {
        System.err.println("[EventBridgeHelper] Failed to replace codec map field: " + t.getMessage());
    }
}
```

Whenever a new channel is dynamically registered via Fabric or Architectury's network APIs, `EventBridgeHelper.ensureChannelRegistered` is called, which appends the channel and its stream codec to all active codec instances on the fly, preventing `IllegalArgumentException` packet decoding crashes.
