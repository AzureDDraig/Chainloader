# Key Mappings Registration

Keybinds in Minecraft allow players to trigger mod actions (e.g. opening compass UI, turning on overlays). In modern Minecraft and NeoForge 1.21.1, keybinds must be registered during the client setup cycle using `RegisterKeyMappingsEvent` on the mod event bus. 

ChainLoader captures legacy registrations (such as Fabric's early static key mapping registries or Architectury's `KeyMappingRegistry`) and dynamically injects them into the game's active options array using Unsafe reflections.

---

## 1. Key Mapping Storage Shim

Legacy mods declare key binds early during initialization. ChainLoader provides shims for libraries like Architectury or Fabric key binders to capture these mappings:

```java
// KeyMappingRegistry.java (Architectury Shim)
package dev.architectury.registry.client.keymappings;

import net.minecraft.client.KeyMapping;
import net.chainloader.loader.compat.keymappings.KeyMappingStorage;

public class KeyMappingRegistry {
    public static void register(KeyMapping keyMapping) {
        KeyMappingStorage.register(keyMapping);
    }
}
```

The `KeyMappingStorage` redirects the request to `EventBridgeHelper.registerKeyMapping(keyMapping)`, which logs the mapping and queues it in a static cache:

```java
public static final List<KeyMapping> customKeyMappings = new ArrayList<>();

public static void registerKeyMapping(KeyMapping keyMapping) {
    if (keyMapping != null && !customKeyMappings.contains(keyMapping)) {
        customKeyMappings.add(keyMapping);
        System.out.println("[ChainLoader] Registered custom key mapping: " + keyMapping.getName());
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.options != null && mc.options.keyMappings != null) {
                // Inject immediately if options are already loaded
                injectKeyMappings(mc);
            }
        } catch (Throwable t) {
            // Ignore if called before Minecraft is fully initialized
        }
    }
}
```

---

## 2. Dynamic Option Array Splice (Unsafe Injection)

When Minecraft's options are loaded (or during `onMinecraftInit` setup phases), ChainLoader splices the custom key mappings into the `Options.keyMappings` array. Because this field is final and locked by the JVM, standard Java reflection fails. ChainLoader uses `sun.misc.Unsafe` to write the new expanded array into the field slot.

### Splicing Logic Flow
1.  **Field Scan**: ChainLoader scans all fields in `Options.class` to locate the `KeyMapping[]` array (obfuscated field name varies).
2.  **Array Expansion**: A new array is allocated: `new KeyMapping[original.length + newKeybinds.size()]`.
3.  **Copy**: Original mappings are copied to the beginning, and new mappings are inserted at the end.
4.  **Offset Rewrite**: The field offset is determined using `unsafe.objectFieldOffset`, and the new array reference is written:
    ```java
    long offset = unsafe.objectFieldOffset(keyMappingsField);
    unsafe.putObject(mc.options, offset, newArray);
    ```
5.  **Option File Reload**: The options are reloaded from disk (`mc.options.load()`) to apply saved player keys to the newly injected options list.

This injection process ensures the controls screen displays the custom keybind categories and lets players configure binding keys without GUI layout crashes.
