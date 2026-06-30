# Blockstates & Block Registry

Minecraft 1.21.1 enforces strict registration rules for block states. If a block state is queried but does not exist in the blockstate lookup map, the game engine returns `-1` and crashes. Legacy mods registered block states statically or during classloading, which breaks on the new platform. 

This document explains block registration shimming and blockstate lookup synchronization in ChainLoader.

---

## 1. Block Registry Translation

Block objects registered by legacy mods are captured by the `RegistryStager`:
1. Legacy registration calls are intercepted and staged:
   ```java
   RegistryStager.registerLegacyBlock("legacy_mod", "ruby_ore", () -> new Block(...));
   ```
2. During the NeoForge registry cycle, `RegistrySynchronizer` binds these blocks to the native registry (`minecraft:block`).
3. Bytecode transformation injects the missing `setRegistryName` methods into the `Block` class, ensuring compatibility with legacy mod setup chains.

---

## 2. Blockstate Registry Population (`populateModBlockStates`)

In Minecraft 1.21.1, blockstates must be populated in the internal `Block.BLOCK_STATE_REGISTRY` (field `q` in obfuscated environment). If states are missing from this registry, the game fails to serialize/deserialize them.

To fix this, `EventBridgeHelper.populateModBlockStates` is invoked dynamically during the startup sequence:
1. **Reflection Retrieval**: Accesses the private static `BLOCK_STATE_REGISTRY` field on the `Block` class.
2. **Scan**: Iterates over all blocks in the game's block registry (`BuiltInRegistries.BLOCK`).
3. **State Extraction**: Invokes `getStateDefinition().getPossibleStates()` on each block.
4. **Injection**: Checks if each possible state is present in the blockstate registry (querying ID). If the ID returns `-1` (unregistered), it invokes the `add` method to insert the state:
   ```java
   // Conceptual behavior
   if (BLOCK_STATE_REGISTRY.getId(state) == -1) {
       BLOCK_STATE_REGISTRY.add(state);
   }
   ```
This runtime correction ensures that all blockstates declared by legacy mods are recognized by the game serializer.

---

## 3. Interaction and Method Redirection

Minecraft 1.20.5+ split block interactions into `useWithoutItem` and `useResult`. Legacy overrides of the `use` method are translated using the following transforms:

### 3.1 Bytecode Injection (`use_legacy`)
The `BytecodeTransformer` modifies `BlockBehaviour` (`dtb`) to inject a compatibility `use_legacy` method:
* **Legacy Signature**: `use_legacy(BlockState, Level, BlockPos, Player, InteractionHand, BlockHitResult)`
* **Bridge Delegate**: Translates the arguments and delegates to the new `useWithoutItem` method (obfuscated as method `a` on `BlockBehaviour`):
  ```java
  public InteractionResult use_legacy(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
      return this.useWithoutItem(state, level, pos, player, hit);
  }
  ```

### 3.2 Super Call Redirection (`superUse`)
When a legacy mod extends `Block` and calls `super.use(...)`, it results in a linkage error because the superclass no longer contains that signature. ChainLoader intercepts these calls and redirects them to `EventBridgeHelper.superUse(...)`:
* **MethodHandle Lookup**: Uses `MethodHandles.privateLookupIn` to look up `useWithoutItem` (or its obfuscated name `a`) in the `BlockBehaviour` class.
* **Binding**: Binds the block instance and invokes the method safely:
  ```java
  return (InteractionResult) superUseHandle.bindTo(block).invoke(state, level, pos, player, hit);
  ```
This redirection maintains inheritance rules and allows overridden block logic to behave correctly.
