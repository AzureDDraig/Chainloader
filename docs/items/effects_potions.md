# Mob Effects & Potions

Minecraft 1.21.1 updated the potion-brewing registration and recipe serialization pipelines. Legacy mob effects and custom recipes declare serializing methods or JSON formats that cause validation failures on modern servers. 

This document describes how custom mob effects, brewing recipes, and recipe serializers are bridged.

---

## 1. Custom Mob Effects Registration

Legacy mods register custom mob status effects by extending `MobEffect` (or Yarn `StatusEffect`):
```java
// Legacy Mod Status Effect
public class RubyStrengthEffect extends MobEffect {
    public RubyStrengthEffect(MobEffectCategory category, int color) {
        super(category, color);
    }
}
```

To support this:
1. **Staged Registration**: Custom mob effect initializations are intercepted and staged in the `RegistryStager` under `minecraft:mob_effect`.
2. **Dynamic Binding**: During the NeoForge registry lifecycle, the effects are registered with `BuiltInRegistries.MOB_EFFECT`.
3. **Bytecode Remapping**: References to old status effect categories and attributes are mapped to modern counterparts.

---

## 2. Brewing & Potion Recipe Shims

In Minecraft 1.20.1, brewing recipes were registered by invoking `PotionBrewing.addMix(Potion, Item, Potion)`. In 1.21.1, this static method was restructured.

### 2.1 Brewing Recipe Translation
* **Interception**: Calls to `PotionBrewing.addMix` (or equivalent Forge/Fabric API methods) are intercepted.
* **Modern Registration**: ChainLoader converts these parameters into a modern brewing recipe record and registers it with the local player's/server's active brewing registry at runtime.

---

## 3. Recipe Manager & Codec Patching

Legacy mods loading custom recipes often lack JSON fields required by the modern `RecipeManager`, or they implement outdated codec serializers.

### 3.1 Recipe JSON Patching (`patchRecipes`)
During datapack and recipe loading, `EventBridgeHelper.patchRecipes` intercepts and cleans up recipe JSON data:
* **Validation**: Iterates over loaded recipe JSON files.
* **Field Addition**: Checks if the recipe has a `"result"` object. If `"result"` has `"item"` but lacks the `"id"` property (required in 1.21.1), it adds it:
  ```json
  "result": {
      "item": "legacy_mod:ruby",
      "id": "legacy_mod:ruby"
  }
  ```
This patch prevents the server's `RecipeManager` from throwing json parsing errors and discarding mod recipes.

### 3.2 Legacy Recipe Serializer Bridging
Modern recipes are read and written using `MapCodec` and `StreamCodec`. Legacy mods implemented custom serializers that read recipes from GSON `JsonObject` and `FriendlyByteBuf` directly:
1. **LegacyRecipeMapCodec**: Wraps legacy serializers. Converts modern `DynamicOps` fields back into GSON `JsonObject` structures, and invokes the legacy `read(ResourceLocation, JsonObject)` method via reflection.
2. **LegacyRecipeStreamCodec**: Wraps legacy stream decoders. Intercepts `FriendlyByteBuf` and routes it to the legacy `read(FriendlyByteBuf)` or `read(ResourceLocation, FriendlyByteBuf)` method.

This bridging allows legacy custom recipe types to load and synchronize across the network without requiring rewriting.
