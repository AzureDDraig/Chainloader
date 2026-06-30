# Custom Stream & Packet Buffer Value I/O

Networking in Minecraft 1.21.1 uses strongly-typed custom packet payloads implementing `CustomPacketPayload`. Rather than writing arbitrary bytes to a channel, packets are encoded/decoded using registries and typed stream codecs. 

ChainLoader shims legacy networking (e.g. Fabric's `PacketByteBufs`/`ServerPlayNetworking` and Forge's `SimpleChannel`/`FriendlyByteBuf` writing) by intercepting the packet stream and wrapping the raw byte data inside a unified payload structure.

---

## 1. Packet Buffer Shimming (FriendlyByteBuf)

In modern Minecraft, `FriendlyByteBuf` (and its subclass `RegistryFriendlyByteBuf`) remains the primary raw byte writing interface. ChainLoader provides compile-time shims for `FriendlyByteBuf` in the compatibility module:

```java
package net.minecraft.network;

import io.netty.buffer.ByteBuf;

public class FriendlyByteBuf {
    public FriendlyByteBuf(ByteBuf parent) {}
    public int readableBytes() { return 0; }
    public FriendlyByteBuf readBytes(byte[] dst) { return null; }
    public FriendlyByteBuf writeByteArray(byte[] array) { return null; }
    public byte[] readByteArray() { return null; }
}
```

This stub compiles against legacy code, while at runtime, calls map directly to modern Minecraft's `FriendlyByteBuf` class loaded from the runtime jar.

---

## 2. Dynamic Payload Packaging (ChainloaderPayload)

When a legacy mod attempts to send a packet over a custom channel (identified by a `ResourceLocation` namespace), it generates a `FriendlyByteBuf` containing custom values. ChainLoader intercepts the network write and wraps the data inside a `ChainloaderPayload`.

### Structure of ChainloaderPayload
`ChainloaderPayload` implements the modern `CustomPacketPayload` interface and defines a static stream codec that handles basic byte array serialization:

```java
package net.chainloader.loader.compat.bridge;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ChainloaderPayload(ResourceLocation id, byte[] data) implements CustomPacketPayload {
    public static StreamCodec<FriendlyByteBuf, ChainloaderPayload> STREAM_CODEC(ResourceLocation channelId) {
        return StreamCodec.of(
            (buf, payload) -> {
                buf.writeByteArray(payload.data());
            },
            buf -> {
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

## 3. Receiving & Decoding Packet Buffers

When a packet of type `ChainloaderPayload` is received from the network, ChainLoader extracts the raw byte data, reconstructs a `FriendlyByteBuf`, and enqueues execution to process the packet on the main client or server thread.

### Decoding and Dispatch Cycle (EventBridgeHelper.java)
```java
private static void registerChannel(PayloadRegistrar registrar, ResourceLocation channelId) {
    ensureChannelRegistered(channelId);
    
    CustomPacketPayload.Type<ChainloaderPayload> payloadType = new CustomPacketPayload.Type<>(channelId);
    
    registrar.playBidirectional(
        payloadType,
        ChainloaderPayload.STREAM_CODEC(channelId),
        (payload, context) -> {
            boolean isClient = context.flow() == PacketFlow.CLIENTBOUND;
            
            // Enqueue work to execute on the main thread
            context.enqueueWork(() -> {
                try {
                    // Wrap the received byte data back into a FriendlyByteBuf
                    FriendlyByteBuf buf = new FriendlyByteBuf(
                        io.netty.buffer.Unpooled.wrappedBuffer(payload.data())
                    );
                    
                    if (isClient) {
                        if (FMLEnvironment.dist.isClient()) {
                            ClientPayloadHandlerHelper.receiveClientPayload(channelId, buf);
                        }
                    } else {
                        ServerPlayer player = (ServerPlayer) context.player();
                        ServerPlayNetworking.PlayChannelHandler handler = 
                            ServerPlayNetworking.getGlobalReceiver(channelId);
                        if (handler != null) {
                            handler.receive(player.server, player, player.connection, buf, null);
                        }
                    }
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            });
        }
    );
}
```
This design allows legacy mod channels to transparently write and read primitive data (integers, strings, positions) as a flat stream of bytes while remaining compatible with modern Minecraft's type-checked payload registrar.
