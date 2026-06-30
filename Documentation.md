# ChainLoader Platform Compatibility & Implementation Specification

This specification documents the architecture, remapping layers, bytecode transformations, and API shims designed to bridge legacy Forge and Fabric mods onto the NeoForge 1.21.1 platform.

---

## 1. Architectural Overview

ChainLoader operates as a runtime compatibility translation layer between compiled legacy mod binaries (built for Minecraft 1.19/1.20) and the NeoForge 1.21.1 execution environment. It achieves runtime interoperability through three primary mechanics:

1. **Dynamic Class Remapping**: Renames and re-targets legacy class, field, and method descriptors to match their 1.21.1 equivalents at load-time.
2. **ASM-based Bytecode Transformation**: Intercepts class loading to modify instruction blocks, redirecting incompatible method invocations and adjusting visibility modifiers.
3. **Runtime API Shims**: Exposes missing legacy framework classes (e.g., Forge's `WorldWorkerManager` or Fabric's `ModelLoadingRegistry`) and translates platform events dynamically.

---

## 2. Bytecode Transformations & Redirects

To prevent runtime link errors (such as `NoSuchMethodError` or `NoSuchFieldError`), the `BytecodeTransformer` interceptor performs instruction re-writing on legacy class streams.

### 2.1 Entity and Player Level Queries (`getLevel`)
* **Legacy Descriptor**: `ServerPlayer.getLevel()Lnet/minecraft/server/level/ServerLevel;` (or obfuscated `m_9236_()`)
* **Platform Discrepancy (1.21.1)**: The level getter was renamed to `level()` and its return type covariantly widened to `Level` (`Lnet/minecraft/world/level/Level;`), breaking legacy mod references that expect a `ServerLevel`.
* **Redirection Rule**:
  | Target Call Site | Intercepted Identifier | Routed Bridge Method |
  | :--- | :--- | :--- |
  | `INVOKEVIRTUAL net/minecraft/server/level/ServerPlayer` | `getLevel`, `m_9236_`, `level`, `dO` | `EventBridgeHelper.getServerLevelBridge(Object)` |
* **Bridge Implementation**: Safely checks the current execution context and casts `player.level` to `ServerLevel`, resolving return-type covariance.

### 2.2 Biome Precipitation & Temperature Queries
* **Legacy Descriptor**: `Biome.getPrecipitation(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/biome/Biome$Precipitation;` (or obfuscated `m_47480_()`)
* **Platform Discrepancy (1.21.1)**: Method signatures and return types changed internally within biome climate settings.
* **Redirection Rule**:
  | Target Call Site | Intercepted Identifier | Routed Bridge Method |
  | :--- | :--- | :--- |
  | `INVOKEVIRTUAL net/minecraft/world/level/biome/Biome` | `getPrecipitation`, `m_47480_` | `EventBridgeHelper.getPrecipitationBridge(Object)` |
* **Bridge Implementation**: Accepts a widened parameter object, checks the current biome climate configuration, and invokes `getBaseTemperature()` dynamically to prevent visibility violations.

### 2.3 Biome Humidity Fallback (`hasHumidity`)
* **Legacy Descriptor**: `Biome.hasHumidity()Z` (or obfuscated `m_47533_()`)
* **Platform Discrepancy (1.21.1)**: Removed from the `Biome` class definition.
* **Redirection Rule**:
  | Target Call Site | Intercepted Identifier | Routed Bridge Method |
  | :--- | :--- | :--- |
  | `INVOKEVIRTUAL net/minecraft/world/level/biome/Biome` | `hasHumidity`, `m_47533_` | `EventBridgeHelper.hasHumidityBridge(Object)` |
* **Bridge Implementation**: Evaluates the climate settings downfall parameter at runtime to calculate downfall/humidity presence.

### 2.4 Nature's Compass Screen Widget Ticking
* **Target Class**: `com.chaosthedude.naturescompass.gui.NaturesCompassScreen`
* **Discrepancy**: Legacy screen tick updates expected a different naming convention from 1.21.1 widget ticking.
* **Redirection Rule**:
  - Intercepts calls to `TransparentTextField.tick()` and redirects the virtual instruction call site to target the obfuscated 1.21.1 equivalent method `m_94120_()`.

---

## 3. Data & State Synchronization (Item Components)

Minecraft 1.20.5+ replaced the legacy NBT tag system on item stacks with typed Data Components. Mods compiling against the legacy `CompoundTag` APIs fail to mutate or read item state on newer platforms.

### 3.1 Custom NBT-to-Component Bridge (`TrackedCompoundTag`)
* **Target Interception**: Calls to `ItemStack.getTag()` (`m_41783_()`) are redirected to `EventBridgeHelper.getItemStackNbt(Object)`.
* **Bridge Implementation**: Wraps the NBT tag inside a custom `TrackedCompoundTag` subclass of `CompoundTag`.
* **Synchronization Loop**:
  - Overrides all mutating method signatures (`putInt`, `putString`, `putBoolean`, `remove`, `put`, etc.).
  - After any write operation, it forces a write-back to the stack's data components:
    ```java
    stack.set(DataComponents.CUSTOM_DATA, CustomData.of(this));
    ```
  - Keeps empty tags active with a placeholder key (`"chainloader_nbt_compat"`) to avoid early validation null returns.

### 3.2 Component Get/Remove Mapping Alignment
* **Mapping Correction**:
  - Corrected the mapping of `ItemStack.get(DataComponentType)` inside `Chainlink1_21_1_Base` to map to method `a` (getter) instead of method `c` (which represents component removal).
  - Explicitly mapped the component `remove` method to method `c`.

---

## 4. UI & Widget Class Modification

Runtime class transformations are performed on client screen classes to match the 1.21.1 hierarchy.

### 4.1 Widget Rendering Final Modifier Removal
* **Target Class**: `AbstractWidget` (and child classes)
* **Adjustment**: Removes the `final` modifier from the `renderWidget` method (obfuscated name `b`/`a` in 1.21.1) to allow custom mod controls to override custom rendering methods without triggering JVM `VerifyError` or `LinkageError`.

### 4.2 Checkbox Legacy Constructor Backport
* **Target Class**: `net.minecraft.client.gui.components.Checkbox` (`fio`)
* **Adjustment**: Dynamically injects the legacy constructor `<init>(IIIILnet/minecraft/network/chat/Component;Z)V` into `Checkbox` at class loading.
* **Super Call Resolution**: Redirects the super constructor call to target the direct 1.21.1 superclass `AbstractButton` (`fid`) instead of `AbstractWidget` (`fik`), preserving correct constructor hierarchy.

### 4.3 Wildcard Descriptor Fallback in AccessWidener
* **Components**: `net.chainloader.loader.access.AccessWidener` and `net.chainloader.loader.core.transform.AccessWidenerCompiler`
* **Adjustment**: Implemented a fallback lookup using a wildcard descriptor (`"*"`) within both standard and compiled class/field/method visitors (`CompiledAccessWidenerVisitor`). This resolves class loading/remapping issues where field types or method signatures are dynamically obfuscated or mapped at runtime, allowing access widening rules to apply reliably.

### 4.4 Class Member Access Widening Rules
* **CreativeModeTabs (`ctb.j`)**: Widen access to field `j` in class `ctb` using the wildcard descriptor (`"*"`). This resolves the `IllegalAccessError` where `ctb.j` has descriptor `Lakq;` (CreativeModeTab) instead of `Ljava/util/List;`.
* **Entity Level Field (`bsr.r`)**: Widen access to field `r` (originally `Entity.level` or `f_19853_`) in class `bsr` (`Entity`) using the wildcard descriptor (`"*"`). This resolves the runtime `IllegalAccessError` when legacy mod helper methods (such as `PlayerUtils.cheatModeEnabled()`) access the level field directly.

---

## 5. API Compatibility Shims (Forge & Fabric)

Missing system registries and background loop utilities are re-implemented as shims.

### 5.1 WorldWorkerManager (Forge Background Queue)
* **Class Path**: `net.minecraftforge.common.WorldWorkerManager`
* **Purpose**: Runs background tick-based server tasks (such as Nature's Compass's multi-tick `BiomeSearchWorker`).
* **Event Binding**:
  - Registered to the NeoForge Logical Server tick loop via `ServerTickEvent$Post`.
  - To avoid `ClassNotFoundException` in the early boot context when NeoForge classes are not yet loaded, registration is deferred/lazily loaded inside `WorldWorkerManager.addWorker(IWorker)`.
  - When the first worker is added, the active class loader is queried to resolve and register the server tick listener dynamically:
    ```java
    ClassLoader cl = WorldWorkerManager.class.getClassLoader();
    Class<?> serverTickPostClass = Class.forName("net.neoforged.neoforge.event.tick.ServerTickEvent$Post", true, cl);
    net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
        net.neoforged.bus.api.EventPriority.NORMAL,
        serverTickPostClass,
        (java.util.function.Consumer) (event -> {
            tick();
        })
    );
    ```

### 5.2 Custom Payload Packet Bridging
* **Target Classes**: `ClientPlayNetworking`, `ServerPlayNetworking`
* **Purpose**: Route legacy Fabric custom payload packets through NeoForge's `RegisterPayloadHandlersEvent` registration.
* **Mechanism**:
  - Exposes `ChainloaderPayload` implementing `CustomPacketPayload` with a custom stream codec.
  - Registers channel encoders/decoders dynamically on `RegisterPayloadHandlersEvent`. To match the Mojang runtime signature exactly, the `StreamCodec.of` method signature is stubbed using Minecraft's `StreamEncoder` and `StreamDecoder` functional interfaces rather than Java's generic `BiConsumer`/`Function`. This prevents bytecode signature mismatch errors and subsequent `ClassCastException`/`NoSuchMethodError` client disconnections when custom network channels are registered and payload bytes are processed.
  - **Dynamic Namespace Routing**: Rather than using a hardcoded placeholder namespace, the event listener queries `event.registrar(channelId.getNamespace())` for each packet channel. This registers the codecs under their exact mod namespace, satisfying NeoForge's internal routing constraints.
  - **Deferred Handler Registration**: To ensure all mod channels are successfully registered, `registerPayloadHandlers` invocation is deferred from `initializeMods` (which runs too early, before Fabric/Forge mod initializers run and populate the global receivers list) to `onMinecraftInit` (when all receivers are fully populated).
  - **Dedicated Server Compatibility**: Client-only references (such as `Minecraft` and clientbound handlers) are isolated in a static nested `ClientPayloadHandlerHelper` class. The clientbound path is only triggered if `FMLEnvironment.dist.isClient()` is true, ensuring dedicated servers do not encounter `NoClassDefFoundError` crashes upon class loading or execution.
  - Intercepts packet sending and wraps payload byte arrays inside `ServerboundCustomPayloadPacket` / `ClientboundCustomPayloadPacket` respectively, ensuring seamless client-server network synchronization.

### 5.3 Creative Mode Tab Signature Redirect
* **Descriptor Gap**: Remapped runtime signatures of `CreativeModeTabs` getters cause mismatch with ASM-redirected methods at compile-time.
* **Resolution**: Return types of `EventBridgeHelper.getCreativeModeTab` are widened to `Object`, and the redirect method visitor visits descriptor `(Ljava/lang/Object;)Ljava/lang/Object;` to avoid return-type mismatch errors.

### 5.4 Model Loading & Registration Bridge
* **Target Classes**: `net.fabricmc.fabric.api.client.model.ModelLoadingRegistry`, `net.fabricmc.fabric.api.client.model.ExtraModelProvider`
* **Adjustment**: Intercepts Fabric's registration hook and routes additional models dynamically to NeoForge's `ModelEvent.RegisterAdditional` and `ModelEvent.BakingCompleted` event handlers.
