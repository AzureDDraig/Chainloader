# Advancements: Advancement Data Pack Loading

Advancements in Minecraft are data-driven JSON configurations stored in data packs under `data/<namespace>/advancements/`. Due to changes in namespaces and data structures (such as the introduction of Data Components in 1.20.5), legacy advancement configurations must be remapped at runtime.

---

## Mod JAR Scanning and Virtual Storage

During server boot or singleplayer world loading, `VanillaAssetPatcher.populateModResources()` scans discovered mod files for advancements:

1. **Path Identification**: ZIP entries starting with `data/` and containing `/advancements/` are detected.
2. **Namespace & Path Relocation**: The entry path is relocated using `AssetPathRelocator`:
   * `data/fabric/advancements/main.json` -> `data/chainloader/advancements/main.json`.
3. **Registration**: The relocated advancements are registered to the virtual asset pack under `PackType.SERVER_DATA`:

```java
modPack.registerBytes(
    net.minecraft.server.packs.PackType.SERVER_DATA, 
    new ResourceLocation(relocatedNamespace, relocatedPath), 
    createJarResourceSupplier(mod.jarFile, originalEntryName)
);
```

---

## Runtime Advancement JSON Patching

To ensure advancements load without errors in Minecraft 1.21.1, the JSON content is dynamically patched before being served to the `ServerAdvancementManager`.

### 1. Namespace Remapping
Any hardcoded namespaces inside the advancement triggers, rewards, or display items (e.g., `fabric:some_item` or `legacy_compat:some_item`) are string-replaced with the consolidated namespace (e.g. `chainloader:some_item`).

### 2. Item & Tag Condition Translation
Minecraft 1.20.5+ replaced raw NBT checks in advancement criteria with Data Components.
* **Legacy Format**: Advancements checking items with NBT criteria historically looked like:
  ```json
  "item": "mymod:magic_wand",
  "nbt": "{Spell:\"fire\"}"
  ```
* **Dynamic Remapping**: When parsing advancement JSONs, ChainLoader's server data parser can rewrite these criteria on the fly, translation-patching NBT queries into modern Data Component checks:
  ```json
  "item": "mymod:magic_wand",
  "components": {
    "mymod:spell": "fire"
  }
  ```

---

## Server Loading and Syncing

1. **ServerAdvancementManager Hook**: Once the `VirtualAssetPack` is injected into the server resource pack list, the vanilla `ServerAdvancementManager` compiles them during the reload phase.
2. **Validation**: Vanilla validate and register the advancements. Since paths and namespaces have been relocated, all triggers (like `minecraft:inventory_changed` or custom criteria) are bound successfully.
3. **Player Syncing**: Registered advancements are synced to clients using vanilla networking, ensuring the advancement tabs and toast notifications render correctly in the player's UI.
