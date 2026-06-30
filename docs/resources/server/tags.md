# Tags: Tag Loading and TagsUpdatedEvent

Tags are groups of items, blocks, fluids, or other registry entries defined via JSON files under `data/<namespace>/tags/<registry_type>/*.json`. Like textures, tags underwent a directory structure singularization (e.g. `tags/blocks/` -> `tags/block/` and `tags/items/` -> `tags/item/`). Additionally, tag synchronization triggers a lifecycle event when loading completes.

ChainLoader provides automatic tag singularization and stubbed event listeners to keep legacy tag systems functional on NeoForge 1.21.1.

---

## Tag Directory Singularization

Legacy mods define item and block tags under the plural folders:
* `data/<namespace>/tags/blocks/*.json`
* `data/<namespace>/tags/items/*.json`

To ensure these are loaded by Minecraft 1.21.1's tag manager, the `AssetPathRelocator` singularizes the folder names during resource pack population:

```java
// From AssetPathRelocator.java
input = input.replace("tags/blocks/", "tags/block/")
             .replace("tags/items/", "tags/item/");
```

### The Path Relocation Flow
1. **Mod Scanning**: `VanillaAssetPatcher` detects tag JSONs inside a mod JAR.
2. **Relocation**: The relocator maps `data/fabric/tags/items/ores.json` to `data/chainloader/tags/item/ores.json`.
3. **Data Registration**: The tag file is stored in `VirtualAssetPack` under `PackType.SERVER_DATA`.
4. **Vanilla Load**: The server's tag loader parses the singularized folders, merging tag values into `BuiltInRegistries` and data-pack-driven registries.

---

## `TagsUpdatedEvent` Event Bridging

In NeoForge 1.21.1, the game post-loads and syncs tags to the client, firing the `TagsUpdatedEvent`. Legacy mods (especially Forge-based ones) subscribed to `TagsUpdatedEvent` to rebuild custom recipe registries, clear caches, or update capabilities that depend on item tags (like tool tier classifications).

### The NeoForge Event Stub

ChainLoader implements a compatibility stub for `TagsUpdatedEvent` on the NeoForge event bus:

```java
package net.neoforged.neoforge.event;

import net.neoforged.bus.api.Event;

public class TagsUpdatedEvent extends Event {}
```

### Event Flow
1. **Tags Loaded**: When the server completes tag serialization, or when the client receives the tag sync packet, NeoForge fires its native `TagsUpdatedEvent`.
2. **Interception**: ChainLoader's event bus catches this event and invokes all registered listeners on the compat event bus.
3. **Legacy Execution**: Mod callbacks receive the event, allowing them to rebuild search indexes, refresh item tags in UI displays (such as EMI, REI, or JEI viewers), and ensure tag-based logic operates with the latest data.
