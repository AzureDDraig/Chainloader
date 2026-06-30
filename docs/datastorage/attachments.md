# Data Attachments

Saving and attaching custom persistent data to entities, chunks, levels, and worlds is a fundamental requirement for modding. 
* **Forge**: Used Capability attachments on worlds (`World`) and level saved data via `DimensionDataStorage`.
* **Fabric**: Used persistent state managers or custom saved data registries.
* **NeoForge 1.21.1**: Uses the native **Data Attachments** system (registering `AttachmentType`s) alongside the standard `DimensionDataStorage` system.

ChainLoader bridges legacy persistent storage calls onto NeoForge's attachment and saved data frameworks using `ChainWorldDataBridge` and bytecode redirection.

---

## World and Level Saved Data Redirection

Minecraft utilizes `DimensionDataStorage` to save data files (such as map states or custom quest logs) in the world's `data/` folder. In older versions, retrieving or creating these files took direct factory functions or class builders. In 1.21.1, the API requires a `SavedData.Factory` wrapper.

### Bytecode Redirection in `DimensionDataStorage`
When a legacy mod retrieves custom world saved data:

```java
// Legacy SavedData retrieval
MyQuestData data = level.getDataStorage().computeIfAbsent(MyQuestData::read, MyQuestData::new, "quest_log");
```

The bytecode rewriter `Chainlink1_21_1_Base` intercepts the `computeIfAbsent` call (matching legacy signatures) and redirects it to `EventBridgeHelper.computeIfAbsent`:

```java
// From Chainlink1_21_1_Base.java
boolean isDimensionDataStorage = "net/minecraft/world/level/storage/DimensionDataStorage".equals(owner) || "crg".equals(owner);
boolean isComputeIfAbsent = "computeIfAbsent".equals(methodName) || "a".equals(methodName);
if (isDimensionDataStorage && isComputeIfAbsent && isOldSignature) {
    methodName = "computeIfAbsent";
    // ... redirects to EventBridgeHelper ...
}
```

---

## `ChainWorldDataBridge` Integration

Inside `EventBridgeHelper.computeIfAbsent(storage, readFunction, constructor, name)`:
1. **Bridge Registration**: The legacy callbacks are wrapped and registered to `ChainWorldDataBridge`:

```java
// From EventBridgeHelper.java
net.chainloader.loader.core.world.ChainWorldDataBridge.registerWorldData(name, new net.chainloader.loader.core.world.ChainWorldData() {
    @Override
    public void read(Object nbt) {
        readFunction.apply((net.minecraft.nbt.CompoundTag) nbt);
    }
    @Override
    public Object write(Object nbt) {
        return nbt;
    }
});
```

2. **Modern Factory Creation**: The helper constructs a modern 1.21.1 `SavedData.Factory` object:
   ```java
   net.minecraft.world.level.saveddata.SavedData.Factory<T> factory = new net.minecraft.world.level.saveddata.SavedData.Factory<>(
       constructor,
       (nbt, provider) -> readFunction.apply(nbt),
       net.minecraft.util.datafix.DataFixTypes.LEVEL
   );
   ```
3. **Execution**: The factory is passed to the storage's modern `computeIfAbsent` method:
   ```java
   return storage.computeIfAbsent(factory, name);
   ```

---

## Bridging Custom World Capabilities to NeoForge Data Attachments

Forge mods historically attached custom data to worlds or levels using capability providers. To bridge these onto NeoForge's modern **Data Attachments** system:

1. **Attachment Type Registration**: During initialization, ChainLoader registers a dynamic `AttachmentType` with NeoForge for every legacy world capability:
   ```java
   // NeoForge registration
   public static final Supplier<AttachmentType<CompoundTag>> CAPABILITY_ATTACHMENT = 
       ATTACHMENT_TYPES.register("capability_attachment", () -> AttachmentType.builder(() -> new CompoundTag()).serialize().build());
   ```
2. **NBT Read/Write Delegation**: When the world is loaded or saved, NeoForge serializes data attachments. The bridge delegates this to the legacy capabilities:
   * **Write**: Serializes the legacy capability state to NBT and saves it in the level attachment.
   * **Read**: Restores the legacy capability state from the attachment's NBT data.

This ensures that persistent machinery states, quest logs, and custom world parameters are successfully loaded and saved without structure loss.
