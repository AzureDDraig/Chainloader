# Mobs, Players & Level Redirection

Minecraft 1.21.1 refactored level accessors on player and entity classes, widening the return types of level getters. Legacy mods expecting specific level subclass types encounter runtime linkage errors. 

This document explains level query redirection, player ticking bridges, and entity lifetime translations.

---

## 1. Player Level Query Redirection (`getServerLevelBridge`)

In Minecraft 1.20.1, the player level getter was:
`ServerPlayer.getLevel()Lnet/minecraft/server/level/ServerLevel;`

In 1.21.1, the getter was renamed to `level()` and its return type widened:
`ServerPlayer.level()Lnet/minecraft/world/level/Level;`

When legacy mods invoke the old getter, it results in a `NoSuchMethodError` because the signature matching `()Lnet/minecraft/server/level/ServerLevel;` does not exist on the player class.

### 1.1 Bytecode Interception Rules
`BytecodeTransformer` scans all method instruction streams. If it detects a call to a player level getter on a `Player` or `ServerPlayer` instance (using either Mojang mappings, Yarn mappings, or obfuscated names), it replaces the call:

| Intercepted Owner | Intercepted Method | Obfuscated Mappings | Redirected Bridge Method |
| :--- | :--- | :--- | :--- |
| `ServerPlayer` / `Player` | `getLevel` / `level` | `m_9236_`, `dO` | `EventBridgeHelper.getServerLevelBridge(Object)` |

### 1.2 The Bridge Implementation
`EventBridgeHelper.getServerLevelBridge(Object)` accepts the player object, checks its class type, extracts the level, and performs a covariant cast:
```java
public static ServerLevel getServerLevelBridge(Object playerObj) {
    if (playerObj instanceof ServerPlayer player) {
        return (ServerLevel) (Object) player.level;
    }
    return null;
}
```
This return-type casting satisfies the JVM instruction verifier and returns the exact `ServerLevel` instance legacy mods expect.

---

## 2. Player Ticking & Ticking Event Bridges

Legacy mods execute continuous logic (e.g., checking player inventories, updating active potion effects, or tick-down status timers) using player tick events.

### 2.1 Forge/NeoForge Tick Routing
ChainLoader hooks into the native tick loops of the hosting platform:
* **Forge**: Subscribes to `TickEvent.PlayerTickEvent`.
* **NeoForge**: Subscribes to `PlayerTickEvent` (via `NeoForgeEventListener`).

### 2.2 Tick Propagation (`postPlayerTick`)
When the native setup tick event fires, the listener delegates the callback to `ChainEventBridge`:
```java
public class ForgeEventListener {
    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        ChainEventBridge.postPlayerTick(event.player);
    }
}
```
`ChainEventBridge.postPlayerTick(Player)` propagates the player instance downstream:
1. Posts the tick event to the native ChainLoader host event bus.
2. Evaluates active Fabric mods and invokes Fabric's `ServerTickEvents.START_PLAYER_TICK` or `END_PLAYER_TICK` callbacks.
3. Synchronizes player status changes back to the game thread.
