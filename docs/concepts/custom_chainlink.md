# Creating a Custom Chainlink

Developers can create custom, mod-specific **Chainlink** modules to apply targeted patches, bytecode transformations, or API redirects to specific legacy mods. This guide explains how to write, configure, and register a custom Chainlink module.

---

## 1. Subclassing `Chainlink1_21_1_Base`
The easiest way to build a custom Chainlink is to extend `net.chainloader.loader.compat.Chainlink1_21_1_Base`. This inherits standard remapping helpers for Minecraft 1.21.1 and lets you focus on mod-specific overrides.

Here is a template for a custom Chainlink targeting a legacy mod named `examplemod`:

```java
package net.chainloader.loader.compat;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class CustomExampleModChainlink extends Chainlink1_21_1_Base {

    @Override
    public String getSupportedVersionRange() {
        // Matches Minecraft versions from 1.20 to 1.21.1
        return "[1.20, 1.21.1]";
    }

    @Override
    public String getSupportedLoaderType() {
        // Run this Chainlink when the candidate mod was built for Fabric
        return "fabric";
    }

    @Override
    public void onWakeUp(ClassLoader classLoader) {
        System.out.println("[Custom Example Chainlink] Activating compatibility layer for examplemod...");
        super.onWakeUp(classLoader);
        
        // Initialize custom Event listeners or register mod-specific shims here
    }

    @Override
    public String mapMethod(String owner, String name, String descriptor) {
        // Redirect legacy method calls to bridge helpers
        if ("com/examplemod/item/CustomSwordItem".equals(owner) && "use_legacy".equals(name)) {
            // Remap to a static EventBridgeHelper method
            return "superUse"; 
        }
        return super.mapMethod(owner, name, descriptor);
    }

    @Override
    public byte[] transform(String className, byte[] bytes) {
        // Target custom class files to apply ASM transformations
        if ("com/examplemod/item/CustomSwordItem".equals(className)) {
            System.out.println("[Custom Example Chainlink] Remapping custom sword methods...");
            // Use ASM ClassReader and ClassWriter to modify class bytecode
            return applyCustomASM(bytes);
        }
        return super.transform(className, bytes);
    }

    @Override
    public Collection<String> getRemapTargetMarkers() {
        // Add class prefixes that signal to the fast-path remapper that this class needs remapping
        return List.of("com/examplemod/");
    }
    
    private byte[] applyCustomASM(byte[] bytes) {
        // Implement custom bytecode manipulation here
        return bytes;
    }
}
```

---

## 1.1 Custom Libraries Shimming
When writing a custom Chainlink for a shared mod library (such as Architectury or Balm), the goal is to handle platform-specific registrations and events transparently. 

By shimming library APIs inside the Chainlink module, mod developers can write and compile their library code against a single, loader-agnostic API. The custom Chainlink then translates all calls to the target environment's native registries and event structures at runtime. This isolates mod developers from having to maintain modloader-specific code for every version update of their libraries.

---

## 2. Registering the Module via SPI
To make your custom Chainlink discoverable during the bootstrap phase, you must register it as an SPI service provider:

1. Create a directory named `META-INF/services/` in your mod's resources folder.
2. Inside it, create a file named exactly:
   ```
   net.chainloader.loader.compat.Chainlink
   ```
3. Open this file and write the fully-qualified name of your class (one per line):
   ```
   net.chainloader.loader.compat.CustomExampleModChainlink
   ```

When the `ChainLauncher` boots, it scans mod JARs, finds your service file, instantiates your class, and invokes it if a mod matches your supported loader and version range rules.

---

## 3. Designing a Dedicated Remapping Layer
For larger mods (e.g., JEI, Waystones, Balm), a dedicated Chainlink acts as a compiler pass:
* **Constant Pool Inspection**: In `getRemapTargetMarkers()`, register your mod's package namespace (e.g., `com/waystones/`). This triggers remapping only on classes belonging to that mod, avoiding runtime overhead on unrelated classes.
* **Visibility Adjustments**: If a legacy class accesses package-private fields on vanilla classes (such as `Entity.level` or `CreativeModeTabs.j`), use your custom Chainlink's `transform` or access wideners to strip `final` modifiers or widen access limits dynamically at load-time.
* **Descriptor Redirections**: Widened parameter return types (such as `getLevel` widening `ServerLevel` to `Level`) can be caught in `mapMethod` and re-routed to static event bridge cast routines to prevent JVM `LinkageError` crashes.
