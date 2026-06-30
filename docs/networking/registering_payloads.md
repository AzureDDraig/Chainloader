# Custom Packet Payload Registration & Registry Routing

In Minecraft 1.21.1, packet handlers are no longer registered dynamically at random points during mod initialization. NeoForge requires all custom packet payloads to be registered under their exact channel namespace during the common mod setup phase, specifically within the `RegisterPayloadHandlersEvent` on the mod event bus.

ChainLoader provides a deferred registration system that captures legacy registrations (e.g., Fabric's early static receiver registration) and routes them into the modern NeoForge lifecycle when the game bootstraps.

---

## 1. Capturing Early Registrations

Legacy mods register custom packet channels early in their lifecycle (e.g. inside `ModInitializer.onInitialize()` or static blocks) using methods like `ClientPlayNetworking.registerGlobalReceiver` or `ServerPlayNetworking.registerGlobalReceiver`.

ChainLoader intercepts these calls in the Fabric network shims and adds them to a global thread-safe registry:

```java
// ClientPlayNetworking.java (Shim)
public static boolean registerGlobalReceiver(ResourceLocation channelName, PlayChannelHandler channelHandler) {
    GLOBAL_RECEIVERS.put(channelName, channelHandler);
    
    // Register channel dynamically with the core remapper
    net.chainloader.loader.compat.bridge.EventBridgeHelper.ensureChannelRegistered(channelName);
    return true;
}
```

---

## 2. Deferred Registry Injection (onMinecraftInit)

During the client/server initialization sequence, Minecraft calls the main bootstrap class. ChainLoader hooks into this sequence and invokes `EventBridgeHelper.onMinecraftInit(Object)` which handles the deferred mod setup lifecycle.

Inside `onMinecraftInit`:
1.  **Mod Event Buses** are gathered from the scanner.
2.  **`registerPayloadHandlers`** is invoked to subscribe listeners to the NeoForge mod event buses.

```java
// EventBridgeHelper.java - registerPayloadHandlers
private static void registerPayloadHandlers(java.util.Set<Object> buses) {
    for (Object bus : buses) {
        if (bus instanceof net.neoforged.bus.api.IEventBus neoforgeBus) {
            neoforgeBus.addListener(
                net.neoforged.bus.api.EventPriority.NORMAL,
                net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent.class,
                event -> {
                    System.out.println("[EventBridgeHelper] Received RegisterPayloadHandlersEvent on mod bus: " + neoforgeBus);
                    
                    // Route all registered client play receivers
                    for (ResourceLocation channelId : ClientPlayNetworking.getGlobalReceivers()) {
                        PayloadRegistrar registrar = event.registrar(channelId.getNamespace());
                        registerChannel(registrar, channelId);
                    }
                    
                    // Route all registered server play receivers
                    for (ResourceLocation channelId : ServerPlayNetworking.getGlobalReceivers()) {
                        PayloadRegistrar registrar = event.registrar(channelId.getNamespace());
                        registerChannel(registrar, channelId);
                    }
                }
            );
        }
    }
}
```

---

## 3. Namespace Isolation & Routing

NeoForge requires a `PayloadRegistrar` to be scoped to a single namespace (representing the mod ID). Routing packets under an incorrect namespace will cause registrar registration failures.

ChainLoader resolves this by querying:
```java
PayloadRegistrar registrar = event.registrar(channelId.getNamespace());
```
This isolates the registrar to the exact namespace of the packet channel ID (e.g., `event.registrar("naturescompass")`), ensuring that the resulting `CustomPacketPayload` and its `StreamCodec` are successfully mapped and registered within NeoForge's internal networking registries without namespace conflicts.

---

## 4. Dynamic Class Scanning for Packet Channels

Because `RegisterPayloadHandlersEvent` is fired by NeoForge *before* Minecraft bootstraps and runs the Fabric mod initializers (where `ClientPlayNetworking.registerGlobalReceiver` is called), the receivers list is initially empty when the event runs.

To ensure all channels are registered in time, `ModScanner.java` scans all `.class` files in the mod JARs during the initial boot phase. It extracts all string constants matching a valid `ResourceLocation` namespace:path format:
- Format: `^[a-z0-9_.-]+:[a-z0-9_.-/]+$`
- Non-default namespaces (excluding standard namespaces like `minecraft`, `neoforge`, `forge`, `fabric`, `c`).

These discovered channels are collected in `ModScanner.getDiscoveredPacketChannels()` and automatically registered in `RegisterPayloadHandlersEvent` on the mod bus alongside the legacy static receivers:

```java
// Register all discovered channels from ModScanner
for (String channelStr : net.chainloader.loader.core.ModScanner.getDiscoveredPacketChannels()) {
    int colonIdx = channelStr.indexOf(':');
    if (colonIdx > 0 && colonIdx < channelStr.length() - 1) {
        net.minecraft.resources.ResourceLocation channelId = 
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                channelStr.substring(0, colonIdx), 
                channelStr.substring(colonIdx + 1)
            );
        if (channelId != null) {
            net.neoforged.neoforge.network.registration.PayloadRegistrar registrar = event.registrar(channelId.getNamespace());
            registerChannel(registrar, channelId);
        }
    }
}
```
This hybrid approach guarantees that both statically declared channels and dynamically registered receivers are correctly registered within NeoForge's frozen registrar before game load.

---

## 5. Unique Class-Per-Payload Constraint (1.20.5+ / 1.21)

In Minecraft 1.20.5 and newer, Mojang's and NeoForge's packet systems index playbound custom payload packets by their concrete Java class:
```java
Map<Class<? extends CustomPacketPayload>, PayloadConfiguration>
```
If multiple packet channels (e.g. `waystones:known_waystones` and `waystones:waystone_update`) utilize the same class representation (like a generic `ChainloaderPayload` container), only the last registered payload handler configuration is preserved, overwriting any previous registrations. Consequently, clientbound packets sent for overwritten channels trigger:
`[Render thread/WARN]: Unknown custom packet payload: [channel]`

### Solution: Dynamic ASM Class Generation

To bypass this class-uniqueness restriction, ChainLoader dynamically compiles and loads a unique subclass of `CustomPacketPayload` for each discovered packet channel on-the-fly at runtime.

Inside `EventBridgeHelper.java`:
1.  **Class Generation**: When a channel is registered via `ensureChannelRegistered(channelId)`, ChainLoader generates raw class bytes utilizing ASM ClassWriter:
    *   **Class Name**: `net.chainloader.loader.compat.bridge.DynamicPayload_[namespace]_[path]`
    *   **Dynamic Class Name Mapping**: To avoid `NoClassDefFoundError` in production where Minecraft classes are obfuscated (e.g. `net/minecraft/resources/ResourceLocation` mapped to `akr`), ChainLoader dynamically translates all referenced class names to their runtime mapped names using the `BytecodeTransformer` mapping registry (`BytecodeTransformer.getInstance().map(deobfName)`).
    *   **Implemented Interface**: The runtime mapped class name for `net.minecraft.network.protocol.common.custom.CustomPacketPayload`.
    *   **Fields**: `private final byte[] data`
    *   **Methods**: Getter `data()`, Constructor `(byte[])`, and `type()` returning `new CustomPacketPayload.Type<>(channelId)` (utilizing the mapped names for `CustomPacketPayload$Type` and `ResourceLocation`).
2.  **ClassLoader Injection**: The class bytes are injected into the classloader via `loader.defineDynamicClass(className, bytes)` inside `ChainClassLoader`.
3.  **Generic Codec Generation**: A reflection-based `StreamCodec` is instantiated to serialize and deserialize the dynamic class instances dynamically:
    ```java
    net.minecraft.network.codec.StreamCodec.of(
        (buf, payload) -> buf.writeByteArray(payload.data()),
        buf -> constructor.newInstance(buf.readByteArray())
    )
    ```
4.  **NeoForge Registration**: The uniquely generated class type, generic codec, and payload handler are then passed to `registrar.playBidirectional()`.

This guarantees that every channel has its own distinct type signature in the JVM, fully satisfying NeoForge's class-per-payload restriction and ensuring all mod network interactions succeed.


