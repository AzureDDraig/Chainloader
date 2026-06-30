# Loot Tables

Loot tables in Minecraft are data-driven JSON configurations defining how items drop from blocks, entities, chest containers, and gameplay events (stored under `data/<namespace>/loot_table/*.json`). 

Modifications to these tables (like adding a custom item to a vanilla dungeon chest or mob drop) are handled differently across APIs: Fabric uses code-based event callbacks (`LootTableEvents`), while NeoForge uses data-driven **Global Loot Modifiers** or registry-bus events.

ChainLoader bridges Fabric's loot table events onto NeoForge's runtime execution.

---

## Directory Path Singularization

Older Minecraft versions and legacy mods stored loot tables in the plural `loot_tables` directory. In 1.21.1, the directory is singularized to `loot_type` or `loot_table`.

`AssetPathRelocator` automatically normalizes these paths during mod ZIP scanning:
* **Path Remapping**: `data/fabric/loot_tables/chests/dungeon.json` -> `data/chainloader/loot_table/chests/dungeon.json`
* This allows Minecraft's vanilla data pack manager to load custom loot tables into the correct directories.

---

## Bridging Loot Table Modification Events

Fabric mods dynamically modify loot tables by registering listeners to `LootTableEvents.MODIFY` or `LootTableEvents.REPLACE`:

```java
// Legacy Fabric registration
LootTableEvents.MODIFY.register((resourceManager, lootManager, id, tableBuilder, source) -> {
    if (id.equals(LootTables.ABANDONED_MINESHAFT)) {
        LootPool.Builder poolBuilder = LootPool.builder()
            .rolls(ConstantLootNumberProvider.create(1))
            .with(ItemEntry.builder(MyItems.MAGIC_RING));
        tableBuilder.pool(poolBuilder);
    }
});
```

To support this behavior on NeoForge 1.21.1:
1. **NeoForge Event Interception**: ChainLoader's compatibility layer listens to NeoForge's loot table load events (posted when loot tables are compiled from JSON).
2. **Event Translation**: The handler translates the event's context and delegates the call directly to Fabric's `LootTableEvents` invokers:

```java
// NeoForge event handler
public void onLootTableLoad(LootTableSourceEvent event) {
    ResourceLocation tableId = event.getName();
    LootTable.Builder builder = event.getTableBuilder();
    
    // Fire Fabric's MODIFY callback, passing the active builder
    LootTableEvents.MODIFY.invoker().modifyLootTable(
        getResourceManager(),
        getLootManager(),
        tableId,
        builder,
        getLootTableSource(event)
    );
}
```

3. **In-place Compilation**: The modifications made by the Fabric listener directly affect the `LootTable.Builder` instance. NeoForge then compiles the modified builder, applying all modifications seamlessly before the loot table is registered in-game.
