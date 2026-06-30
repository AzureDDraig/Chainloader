# Configuration Stage Custom Packet Registration

Minecraft 1.20.2 introduced the **Configuration network phase** (also known as the config stage). This network sub-state runs after authentication but before the client joins the world (the Play stage). It is designed to exchange registry values, feature flags, configuration files, and custom mod handshakes.

In NeoForge 1.21.1, registering custom payloads for the configuration stage follows a structure similar to the play stage but utilizes isolated handlers and configuration tasks.

---

## 1. Config Phase Payload Registration

Within the `RegisterPayloadHandlersEvent`, the namespace-isolated `PayloadRegistrar` can declare configuration packets:

```java
neoforgeBus.addListener(
    RegisterPayloadHandlersEvent.class,
    event -> {
        PayloadRegistrar registrar = event.registrar("my_mod_id");
        
        // Registering a configuration-phase packet
        registrar.configurationBidirectional(
            MyConfigPayload.TYPE,
            MyConfigPayload.STREAM_CODEC,
            (payload, context) -> {
                // Handle the config payload
            }
        );
    }
);
```

Unlike the play phase, configuration handlers run in the `IPayloadContext` belonging to the configuration packet listener.

---

## 2. Configuration Handshake Tasks

Minecraft configuration tasks implement `net.minecraft.network.protocol.common.custom.CustomPacketPayload` and are driven by `ConfigurationTask` classes. These tasks must announce when they complete via a callback so the network engine can proceed to the next task in the queue.

For legacy mods (which expect standard handshake packets to trigger immediately after login), ChainLoader automatically captures config-stage packet requests and wraps them inside play/configuration bidirectional handlers. 

If a legacy packet is sent during the configuration stage:
1.  ChainLoader packages the payload inside a `ChainloaderPayload`.
2.  It sends the packet using `ClientboundCustomPayloadPacket` or `ServerboundCustomPayloadPacket`.
3.  The receiver (captured via event bridges like `ServerPlayConnectionEvents.JOIN`) executes the payload handler.

---

## 3. Handling Handshake Channels

Legacy mods that use custom login channels (like Fabric's `ServerLoginNetworking` or Forge's network handshake events) register their channels during early startup. ChainLoader's routing layer ensures these channels are announced in both stages:

*   **Registry Synchronizer Compatibility**: During FML common setup, ChainLoader posts the `RegisterPayloadHandlersEvent` on all NeoForge and Forge mod event buses.
*   **Dual Phase Mapping**: When registering a channel inside `EventBridgeHelper.registerChannel`, ChainLoader routes the `ChainloaderPayload` under the target namespace. Because the payload maps back to a raw byte array stream, it can be parsed and dispatched regardless of whether the network state is in `CONFIGURATION` or `PLAY` phase, avoiding connection teardowns due to "unknown custom payload" exceptions.
