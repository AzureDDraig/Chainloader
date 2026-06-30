# Damage Types & Damage Sources

In modern Minecraft (1.20+), damage types are data-driven registry objects loaded from data packs at `data/<namespace>/damage_type/*.json`. In older Minecraft versions (1.19 and below), damage sources and types were instantiated directly in Java code (e.g. using static constants in `DamageSource` or custom subclass constructors).

ChainLoader bridges legacy code-based damage source references and custom damage types onto Minecraft 1.21.1's registry-bound damage system using bytecode redirection and registry helper lookups.

---

## The 1.21.1 Damage Source Model

In 1.21.1, you cannot instantiate a `DamageSource` without a `Holder<DamageType>`, which must be retrieved from the level's registry access:

```java
// Modern 1.21.1 DamageSource lookup
Holder<DamageType> typeHolder = level.registryAccess()
    .registryOrThrow(Registries.DAMAGE_TYPE)
    .getHolderOrThrow(ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation("mymod:custom_damage")));
DamageSource source = new DamageSource(typeHolder);
```

---

## Bytecode Redirection of Legacy Damage Sources

To keep legacy code compiling and running, ChainLoader's bytecode transformer intercepts two kinds of damage source invocations:

### 1. Static DamageSource Fields
Legacy mods often reference standard damage sources via static fields (e.g. `DamageSource.MAGIC`, `DamageSource.WITHER`, or `DamageSource.IN_FIRE`).
* In 1.21.1, these fields no longer exist or are structural keys.
* The bytecode rewriter redirects these field reads to static helper lookups in `EventBridgeHelper`:

```java
// Bytecode redirection
// Old: GETSTATIC net/minecraft/world/damagesource/DamageSource.MAGIC : Lnet/minecraft/world/damagesource/DamageSource;
// New: INVOKESTATIC net/chainloader/loader/compat/bridge/EventBridgeHelper.getMagicDamageSource ()Lnet/minecraft/world/damagesource/DamageSource;
```

Inside `EventBridgeHelper`, the lookup dynamically resolves the `Holder<DamageType>` from the server's global `RegistryAccess` or the current level context and returns the cached, registry-bound `DamageSource`.

### 2. Custom DamageSource Constructors
Legacy mods often created custom damage sources using subclassing or directly invoking constructors:
* **Legacy constructor**: `new DamageSource("custom_name")`
* **Redirection**: The bytecode transformer intercepts the `NEW` and `INVOKESPECIAL` instructions of the `DamageSource` constructor and redirects them to:

```java
public static DamageSource createCustomDamageSource(Object level, String name) {
    // 1. Resolve Holder<DamageType> using 'name' as a key
    // 2. Query registry net.minecraft.core.registries.Registries.DAMAGE_TYPE
    // 3. Return new DamageSource(holder)
}
```

If a level context is not on the stack, the helper falls back to the server's registry access or uses a default fallback holder (like `DamageTypes.GENERIC`).

---

## Data Pack File Relocations

If a legacy Fabric/Forge mod includes custom data-driven damage type JSON files, they are placed under `data/<namespace>/damage_types/` in older versions.

`AssetPathRelocator` singularizes this path on-the-fly during mod scanning:
* **Path Remapping**: `data/fabric/damage_types/acid.json` -> `data/chainloader/damage_type/acid.json`
* This allows Minecraft's vanilla data pack manager to load custom damage types into the `Registries.DAMAGE_TYPE` registry seamlessly, where the bytecode redirects can query them at runtime.
