# Data Maps

NeoForge 1.21.1 introduced **Data Maps**, a first-class feature for attaching metadata to registry objects (such as items, blocks, and entity types) via data-driven JSON files. In legacy Forge and Fabric mods, these associations were typically managed using either hardcoded code registries (like Fabric's `CompostingChanceRegistry` and `FuelRegistry`) or legacy tag files (like `#minecraft:coals` for fuels).

ChainLoader bridges legacy code-based and tag-based metadata definitions onto NeoForge's Data Map system at runtime.

---

## The Legacy to NeoForge Data Map Bridge

NeoForge includes several built-in data maps that replace legacy registries:
* `neoforge:furnace_fuel` (formerly `FuelRegistry` or `#minecraft:coals` tags)
* `neoforge:compostable` (formerly `CompostingChanceRegistry`)
* `neoforge:loot_share` or block/entity attributes.

ChainLoader coordinates this data bridge through two channels:

### 1. Bridging Fabric Code Registries
When legacy Fabric mods call programmatic registries inside their initializers:

```java
// Legacy Fabric code registration
CompostingChanceRegistry.INSTANCE.add(MyItems.MAGIC_LEAF, 0.65F);
FuelRegistry.INSTANCE.add(MyItems.MAGIC_WOOD, 300);
```

ChainLoader intercept these calls via its `FabricApiPort` shims. The shims cache these registrations locally:

```java
public static class FuelRegistry {
    private final Map<Object, Integer> fuelTimes = new ConcurrentHashMap<>();
    public void add(Object item, int cookTime) {
        fuelTimes.put(item, cookTime);
    }
}
```

During server startup:
* The cached values are injected directly into NeoForge's Data Map registry events (such as `RegisterDataMapTypesEvent`).
* This registers them as native Data Map entries, ensuring the vanilla furnace or composter logic reads them correctly.

### 2. Translating Legacy Tags to Data Maps
Older data packs defined furnace fuels using item tag JSONs:
* **Legacy Tag**: `data/minecraft/tags/items/furnace_fuel.json` containing `"values": ["mymod:peat"]`.
* **NeoForge Data Map**: Expects fuel definitions in `data/minecraft/data_maps/item/furnace_fuel.json` associating items with a burn time value:
  ```json
  {
    "values": {
      "mymod:peat": {
        "burn_time": 1600
      }
    }
  }
  ```

ChainLoader automatically parses legacy fuel/composting tags and translates their values into virtual NeoForge Data Map files registered under `PackType.SERVER_DATA` in the `VirtualAssetPack`, preserving the expected behavior of data-driven item additions.
