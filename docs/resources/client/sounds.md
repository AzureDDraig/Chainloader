# Sound Events Registrations

Sound events bridge the game's logic (playing a sound by ID) to the actual audio files (`.ogg`) and configuration files (`sounds.json`). ChainLoader provides registry remapping and resource pack routing to support sound events in legacy mods.

---

## Registry Mapping and Obfuscation

In Minecraft 1.21.1, the registry for sound events is `BuiltInRegistries.SOUND_EVENT`. Under obfuscated environments, the field in `BuiltInRegistries` is remapped.

ChainLoader's bytecode transformer handles this in `Chainlink1_21_1_Base.java` by remapping references to `BuiltInRegistries.SOUND_EVENT` (which is `b` in 1.21.1):

```java
if (isClass(owner, "net/minecraft/core/registries/BuiltInRegistries", "lt")) {
    // ...
    if (name.equals("SOUND_EVENT")) return "b";
    // ...
}
```

---

## Legacy Registry Names Compatibility

Forge mods historically used the `.setRegistryName` and `.getRegistryName` methods on registry objects (including `SoundEvent`). ChainLoader implements this via `RegistryHelper` which stores these associations in a `WeakHashMap` without altering the class hierarchy:

```java
package net.chainloader.loader.compat.bridge;

import net.minecraft.resources.ResourceLocation;
import java.util.WeakHashMap;

public class RegistryHelper {
    public static final WeakHashMap<Object, ResourceLocation> REGISTRY_NAMES = new WeakHashMap<>();

    public static Object setRegistryName(Object obj, ResourceLocation name) {
        REGISTRY_NAMES.put(obj, name);
        return obj;
    }
    // ... overrides for string arguments ...
}
```

When a legacy Forge mod instantiates a sound event and calls `.setRegistryName("mymod:block.click")`, the bytecode rewriter redirects it to `RegistryHelper.setRegistryName`, caching the association.

---

## Unified Core Registration

During event loop execution, the event translator fires registration events. Sound events are collected into `ChainRegistryBridge` and registered to the game's actual registry:

```java
while (!ChainRegistryBridge.getPendingEntries().isEmpty()) {
    ChainRegistryBridge.RegistryEntry entry = ChainRegistryBridge.getPendingEntries().poll();
    if (entry != null && "sound_event".equals(entry.registryName)) {
        WritableRegistry<SoundEvent> registry = (WritableRegistry<SoundEvent>) 
            BuiltInRegistries.REGISTRY.get(new ResourceLocation("minecraft", "sound_event"));
        
        Registry.register(registry, new ResourceLocation(entry.entryId), (SoundEvent) entry.value);
    }
}
```

This registers the `SoundEvent` with the game engine, enabling it to be referenced in code (e.g. `level.playSound(...)`).

---

## Audio Asset Loading & JSON Mapping

A registered sound event requires an audio file and a sound definition entry in `sounds.json`. 

1. **`sounds.json` Parsing**: The file `assets/<namespace>/sounds.json` defines sound event categories and maps them to `.ogg` audio files.
2. **Resource Relocation**: `VanillaAssetPatcher` and `AssetPathRelocator` scan mod JARs and automatically relocate namespaces (e.g., `assets/fabric/sounds.json` -> `assets/chainloader/sounds.json`) and audio files (e.g., `assets/fabric/sounds/click.ogg` -> `assets/chainloader/sounds/click.ogg`).
3. **Merging**: If multiple virtual packs define `sounds.json` for the same namespace, the json files are merged in memory, ensuring all custom sounds are loaded by Minecraft's audio engine.
