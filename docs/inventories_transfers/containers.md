# Containers

In Minecraft, container menus (historically known as "screen handlers" in Fabric) coordinate inventory synchronization and user interface interaction between the server and client. Legacy Fabric and Forge mods often use custom factories and data-sync protocols (like Fabric's `ExtendedScreenHandlerFactory`) to send extra initialization data (like BlockPos or energy levels) when opening a container.

ChainLoader bridges these custom menu opening protocols onto NeoForge 1.21.1's container systems using a synchronized client buffer bridge.

---

## The Client Buffer Synchronization Bridge

Fabric's `ExtendedScreenHandlerFactory` allows the server to write custom byte data to a packet buffer, which the client reads when creating the container screen:

```java
// Fabric's ExtendedScreenHandlerFactory
public interface ExtendedScreenHandlerFactory extends MenuProvider {
    void writeScreenOpeningData(ServerPlayer player, FriendlyByteBuf buf);
}
```

On NeoForge, opening a container with extra data typically requires a custom payload packet or a custom menu provider class. ChainLoader bridges this transparently without packet registrations:

### 1. Intercepting Menu Opening (`openMenuBridge`)
When a mod calls `player.openMenu(provider)`, ChainLoader redirects the call to `EventBridgeHelper.openMenuBridge`:

```java
public static OptionalInt openMenuBridge(Player player, MenuProvider provider) {
    if (player instanceof ServerPlayer serverPlayer) {
        if (provider instanceof ExtendedScreenHandlerFactory extended) {
            // 1. Calculate the next container synchronization ID
            int syncId = serverPlayer.containerCounter + 1;
            
            // 2. Allocate a temporary packet buffer
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            
            // 3. Invoke the mod's serializer to write custom data
            extended.writeScreenOpeningData(serverPlayer, buf);
            
            // 4. Cache the buffer under the sync ID
            clientBuffers.put(syncId, buf);
            
            // 5. Open the vanilla menu
            return serverPlayer.openMenu(provider);
        }
    }
    return player.openMenu(provider);
}
```

### 2. Consuming the Sync Buffer on Client Creation
On the client, the menu type must instantiate the screen handler. ChainLoader's `ScreenHandlerRegistry` defines a custom `ExtendedMenuType` that intercepts creation:

```java
public static class ExtendedMenuType<T extends AbstractContainerMenu> extends MenuType<T> {
    private final ExtendedClientHandlerFactory<T> factory;

    public ExtendedMenuType(ExtendedClientHandlerFactory<T> factory) {
        super((syncId, inventory) -> null, null);
        this.factory = factory;
    }

    @Override
    public T create(int syncId, Inventory inventory) {
        // Query and remove the cached buffer matching the container sync ID
        FriendlyByteBuf buf = EventBridgeHelper.getAndRemoveClientBuffer(syncId);
        if (buf != null) {
            return factory.create(syncId, inventory, buf);
        }
        return null;
    }
}
```

By caching the buffer using the container's unique `syncId`, the client retrieves the exact byte data sent by the server for that specific menu transaction, allowing custom screen handler state (such as block entities or network synchronizations) to initialize correctly.
