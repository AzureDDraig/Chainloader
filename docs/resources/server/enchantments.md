# Enchantments Registry Mapping

In Minecraft 1.21.1, enchantments are data-driven registry objects loaded from data packs at `data/<namespace>/enchantment/*.json`. This represents a structural shift from legacy Minecraft versions where enchantments were compiled code objects (subclasses of `Enchantment`) registered in `BuiltInRegistries`.

ChainLoader handles this transition via bytecode rewriting and dynamic registry lookups, allowing legacy code to interact with modern data-driven enchantments.

---

## Bytecode Field Redirection (`getEnchantment`)

In 1.21.1, the fields in the vanilla `Enchantments` class (represented obfuscated as `dah`) hold `ResourceKey<Enchantment>` references, not `Enchantment` objects. If a legacy mod accesses these fields directly, a `ClassCastException` or compiler mismatch occurs.

ChainLoader's bytecode transformer intercepts `GETSTATIC` field instructions targeting the `Enchantments` class:

```java
// From Chainlink1_21_1_Base.java
@Override
public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
    if (opcode == Opcodes.GETSTATIC && "dah".equals(owner) && "Ldac;".equals(descriptor)) {
        super.visitLdcInsn(name);
        super.visitMethodInsn(Opcodes.INVOKESTATIC, "net/chainloader/loader/compat/bridge/EventBridgeHelper", 
            "getEnchantment", "(Ljava/lang/String;)Ljava/lang/Object;", false);
        super.visitTypeInsn(Opcodes.CHECKCAST, "dac"); // Cast to Enchantment (dac)
    } else {
        super.visitFieldInsn(opcode, owner, name, descriptor);
    }
}
```

### The Lookup Mechanism

When `EventBridgeHelper.getEnchantment(fieldName)` is called at runtime, it performs a dynamic registry lookup:

1. **Resolve ResourceKey**: Reads the `ResourceKey` from the static field in `Enchantments` using reflection.
2. **Resolve RegistryAccess**: Obtains the active `RegistryAccess` (via `getRegistryAccess()`).
3. **Resolve Registry**: Retrieves the enchantment registry from registry access using `Registries.ENCHANTMENT` (obfuscated key `aL`).
4. **Retrieve Enchantment**: Calls `registry.get(resourceKey)` and returns the active data-driven `Enchantment` object.

---

## Enchantment Helper & Item Application Bridges

In 1.21.1, how enchantments are read and applied to items was changed:
* **Legacy**: Enchantments on an `ItemStack` were read and written using raw NBT tags or maps mapping `Enchantment` to `Integer`.
* **Modern**: Minecraft uses `ItemEnchantments` (a component containing holder references).

### 1. Retrieving Enchantments (`getEnchantments`)
The transformer redirects legacy calls to `EnchantmentHelper.getEnchantments(ItemStack)` (or `method_8222`) to `EventBridgeHelper.getEnchantments(ItemStack)`:

```java
public static Map<Object, Integer> getEnchantments(ItemStack stack) {
    Map<Object, Integer> map = new LinkedHashMap<>();
    ItemEnchantments enchants = stack.getEnchantments();
    for (Map.Entry<Holder<Enchantment>, Integer> entry : enchants.entrySet()) {
        map.put(entry.getKey().value(), entry.getValue());
    }
    return map;
}
```

This unpacks the modern `ItemEnchantments` component back into a map of raw `Enchantment` objects, matching the signature expected by legacy mods.

### 2. Applying Enchantments (`enchant`)
Similarly, the transformer redirects calls to `ItemStack.enchant(Enchantment, int)` to `EventBridgeHelper.enchant(ItemStack, Object, int)`:

```java
public static void enchant(ItemStack stack, Object enchantmentObj, int level) {
    Enchantment enchantment = (Enchantment) enchantmentObj;
    Holder<Enchantment> holder = Holder.direct(enchantment);
    stack.enchant(holder, level);
}
```

By wrapping the raw `Enchantment` instance in a direct holder (`Holder.direct(enchantment)`), the bridge complies with the modern API requirements.

---

## Data Pack File Relocations

Legacy data packs define custom enchantments under `data/<namespace>/enchantments/` (plural).

`AssetPathRelocator` normalizes this during mod scanning:
* **Path Remapping**: `data/fabric/enchantments/lifesteal.json` -> `data/chainloader/enchantment/lifesteal.json`
* This ensures that custom enchantment JSON files are loaded by Minecraft's vanilla data pack manager into the correct registries.
