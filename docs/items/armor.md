# Armor Materials & Properties

In Minecraft 1.21.1, armor materials were restructured. Instead of hardcoded Java enums or class-based `ArmorMaterial` instances, armor materials are registered in a dynamic game registry (`BuiltInRegistries.ARMOR_MATERIAL`) and referenced on items using registry wrappers (`Holder<ArmorMaterial>`).

This document details how ChainLoader translates legacy armor materials and shims armor items at runtime.

---

## 1. Armor Material Translation

Legacy mods register custom armor by implementing the `ArmorMaterial` interface and passing it to the `ArmorItem` constructor:
```java
// Legacy Mod Armor Registration
public static final ArmorMaterial RUBY_ARMOR_MATERIAL = new RubyArmorMaterial();
public static final Item RUBY_HELMET = new ArmorItem(RUBY_ARMOR_MATERIAL, ArmorItem.Type.HELMET, new Item.Properties());
```

To support this constructor chain:
1. **Dynamic Registration**: During classloading, ChainLoader captures the instanced `ArmorMaterial` and extracts its properties:
   - `getDefenseForType(Type)`: Armor protection points.
   - `getToughness()`: Armor toughness.
   - `getKnockbackResistance()`: Resistance to knockback.
   - `getEquipSound()`: Equip sound event.
   - `getRepairIngredient()`: Repair item ingredients.
   - `getName()`: Resource path string (e.g. `"ruby"`).
2. **Registry Injection**: ChainLoader dynamically registers this custom material into the native `BuiltInRegistries.ARMOR_MATERIAL` registry under the namespace of the registering mod (e.g., `legacy_mod:ruby`).
3. **Holder Compilation**: Compiles the material into a modern registry `Holder<ArmorMaterial>`.

---

## 2. Armor Item Bridging & Attribute Mapping

In Minecraft 1.21.1, armor attributes (like protection and toughness) are no longer calculated dynamically from the material at runtime. Instead, they are stored directly as attribute modifiers inside the `DataComponents.ATTRIBUTE_MODIFIERS` component.

### 2.1 Constructor Signature Adaptation
`BytecodeTransformer` rewrites references to the `ArmorItem` constructor:
* **Legacy signature**: `(Lnet/minecraft/world/item/ArmorMaterial;Lnet/minecraft/world/item/ArmorItem$Type;Lnet/minecraft/world/item/Item$Properties;)V`
* **Modern target**: Rewritten to accept the resolved registry `Holder<ArmorMaterial>` as its first argument.

### 2.2 Attribute Modifier Injection
To match the expected stats of legacy armor:
1. The loader reads the defense, toughness, and knockback resistance from the custom material.
2. It compiles these values into default attribute modifiers for the respective slot (e.g. `EquipmentSlot.HEAD` for helmet).
3. The modifiers are injected into the default item component map under `DataComponents.ATTRIBUTE_MODIFIERS`, ensuring correct armor values are visible in tooltips and active during damage calculations.
