# GameTest Simulation & Headless Testing

Minecraft includes an automated integration testing framework called **GameTest** (`net.minecraft.gametest.framework`). It is used to write test methods that spawn structures, simulate block behaviors, check pathfinding, and assert block-state changes.

To verify that legacy mods load, register, and link correctly under the modern 1.21.1 environment, ChainLoader includes a headless test harness (`HeadlessSimulator.java`) that simulates the Minecraft bootstrap sequence without requiring a graphic display (GLFW) or user interaction.

---

## 1. Headless Simulator Architecture

Running integration tests or verifying mod initialization in build pipelines requires a headless environment. A standard Minecraft client launch will crash if graphics drivers or window contexts are missing.

ChainLoader's `HeadlessSimulator` handles this by:
1.  Initializing a custom `ChainClassLoader` containing the game dependencies.
2.  Mocking client-side settings (such as Option instance arrays).
3.  Stubbing graphic layers to run in a virtual headless profile.

---

## 2. Registry Bootstrapping & Unfreezing

Minecraft's registration phase is strict. Any attempt to register custom blocks, items, or biomes after the registry is frozen throws an `IllegalStateException`.

To support testing, `HeadlessSimulator` manually drives the bootstrap lifecycle and unfreezes registries so that mods can inject their configurations:

```java
// HeadlessSimulator.java - Bootstrap Sequence
System.out.println("[SIMULATOR] Bootstrapping Minecraft registries...");

// 1. Invoke Minecraft's main registry bootstrap
Class<?> bootstrapClass = classLoader.loadClass("net.minecraft.server.Bootstrap");
java.lang.reflect.Method bootStrapMethod = bootstrapClass.getMethod("a"); // "a" is obfuscated bootstrap method
bootStrapMethod.invoke(null);

// 2. Unfreeze registries to allow mod registration
unfreezeRegistries(classLoader);
```

### Unfreezing Mapped Registries
The unfreeze helper accesses the internal `frozen` fields of the game's registry instances and sets them to `false`:

```java
private static void unfreezeRegistries(ClassLoader classLoader) {
    try {
        Class<?> registryClass = classLoader.loadClass("net.minecraft.core.Registry");
        Class<?> mappedRegistryClass = classLoader.loadClass("net.minecraft.core.MappedRegistry");
        
        // Scan for the built-in registries instance map
        Class<?> registriesClass = classLoader.loadClass("net.minecraft.core.registries.BuiltInRegistries");
        java.lang.reflect.Field registryField = registriesClass.getDeclaredField("REGISTRY");
        registryField.setAccessible(true);
        Object rootRegistry = registryField.get(null);
        
        // Unfreeze the root registry and all sub-registries
        java.lang.reflect.Field frozenField = mappedRegistryClass.getDeclaredField("l"); // Obfuscated frozen state field
        frozenField.setAccessible(true);
        
        frozenField.setBoolean(rootRegistry, false);
        
        java.lang.Iterable<?> registryIterable = (java.lang.Iterable<?>) rootRegistry;
        for (Object subRegistry : registryIterable) {
            if (mappedRegistryClass.isInstance(subRegistry)) {
                frozenField.setBoolean(subRegistry, false);
            }
        }
        System.out.println("[SIMULATOR] Registries unfrozen successfully.");
    } catch (Exception e) {
        System.err.println("[SIMULATOR] Failed to unfreeze registries: " + e.getMessage());
    }
}
```

---

## 3. Simulating Lifecycle Setup Tasks

Once registries are unfrozen, `HeadlessSimulator` initializes the scanned mods and triggers setup tasks:

```java
// Mod initialization is called, which registers entries
ModScanner.initializeMods();

// Execute MinecraftInit lifecycle callbacks
java.lang.reflect.Method onInit = eventBridgeHelperClass.getMethod("onMinecraftInit", Object.class);
onInit.invoke(null, mockMinecraftInstance);

// Post FMLCommonSetupEvent and FMLClientSetupEvent to mod event buses
for (Object bus : ModScanner.getModEventBuses()) {
    postToBus(bus, new FMLCommonSetupEvent());
    postToBus(bus, new FMLClientSetupEvent());
}
```

This sequence replicates the game's initialization state, enabling GameTest runners to execute testing assertions (e.g. constructing world structures, executing block ticks, ticking entities) within a CLI terminal interface.
