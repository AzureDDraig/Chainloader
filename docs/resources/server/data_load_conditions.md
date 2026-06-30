# Data Load Conditions: Fabric vs NeoForge

Modern Minecraft modloaders allow data pack files (like recipes, loot tables, advancements, and tags) to be loaded conditionally based on factors like mod presence or registry contents. 

Fabric mods implement this using the **Fabric Resource Conditions API** (`fabric:load_conditions`), while NeoForge 1.21.1 uses **NeoForge Resource Conditions** (`neoforge:conditions`). ChainLoader dynamically translates these load conditions on-the-fly when parsing data resources.

---

## Condition Structure Comparison

### Fabric JSON Condition Format
Fabric resource conditions are placed under a `"fabric:load_conditions"` array at the root level of the JSON document:

```json
{
  "type": "minecraft:crafting_shaped",
  "fabric:load_conditions": [
    {
      "condition": "fabric:all_mods_loaded",
      "values": [
        "techreborn",
        "indrev"
      ]
    }
  ]
}
```

### NeoForge JSON Condition Format
NeoForge resource conditions are placed under a `"neoforge:conditions"` array:

```json
{
  "type": "minecraft:crafting_shaped",
  "neoforge:conditions": [
    {
      "type": "neoforge:mod_loaded",
      "modid": "techreborn"
    },
    {
      "type": "neoforge:mod_loaded",
      "modid": "indrev"
    }
  ]
}
```

---

## Runtime Condition Translation Rules

When `VanillaAssetPatcher` registers data resources in the `VirtualAssetPack`, the JSON content patcher inspects data files (under the `data/` directory) and maps Fabric condition objects to their NeoForge equivalents:

| Fabric Condition Key | Fabric Parameter Structure | NeoForge Condition Type | NeoForge Parameter Structure |
| :--- | :--- | :--- | :--- |
| `fabric:all_mods_loaded` | `"values": ["mod_a", "mod_b"]` | `neoforge:mod_loaded` | Multi-entry or `neoforge:and` |
| `fabric:any_mods_loaded` | `"values": ["mod_a", "mod_b"]` | `neoforge:any` | Nested list under `"values"` |
| `fabric:registry_contains`| `"registry": "item"`, `"value": "mymod:item"` | `neoforge:registry_contains` | `"registry": "item"`, `"value": "mymod:item"` |
| `fabric:not` | `"value": { ... }` | `neoforge:not` | `"value": { ... }` |
| `fabric:and` | `"values": [ { ... }, { ... } ]`| `neoforge:and` | `"values": [ { ... }, { ... } ]` |
| `fabric:or` | `"values": [ { ... }, { ... } ]`| `neoforge:or` | `"values": [ { ... }, { ... } ]` |

---

## Translation Implementation Details

During virtual asset indexing:
1. **JSON Interception**: The json resource stream is parsed into a tree structure.
2. **Key Conversion**: If the root node contains `"fabric:load_conditions"`, the array is removed and rewritten as `"neoforge:conditions"`.
3. **Array Remapping**: The translator iterates over the Fabric conditions list, mapping each `"condition"` property to `"type"` and translating the structure of arguments (e.g. converting string lists in `"values"` to single-mod check entries).
4. **Logical Nesting**: Nested operators (`not`, `and`, `or`) are parsed recursively, ensuring complex logical conditions from Fabric data packs are fully preserved in the NeoForge data pack system.
