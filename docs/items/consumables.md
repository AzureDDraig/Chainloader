# Consumables & Food Properties

In Minecraft 1.20.5+, item eating and drinking properties were converted into components. Instead of referencing static `FoodProperties` fields on `Item` instances, the game engine reads the `DataComponents.FOOD` component attached to the `ItemStack`. 

This document explains how ChainLoader bridges legacy food properties and eating/drinking interactions to modern components.

---

## 1. Food Properties Bridging

Legacy mods initialize food items by attaching properties to the item constructor:
```java
// Legacy Mod Initialization
new Item(new Item.Properties().food(new FoodProperties.Builder().nutrition(4).saturationMod(0.3f).build()));
```

To support this:
1. **Dynamic Codec Translation**: When a legacy mod constructs an item with food settings, ChainLoader intercepts the property builder. It instantiates the legacy parameters and compiles them into a modern `FoodProperties` component record.
2. **Component Mapping**: The compiled record is registered in the item's default component map under `DataComponents.FOOD`.

### 1.2 Query Interception (`getFoodProperties`)
If a legacy mod queries an item's food properties via `Item.getFoodProperties()`:
* **Bytecode Route**: The call is intercepted by `BytecodeTransformer`.
* **Bridge Resolution**: It fetches the `DataComponents.FOOD` component from the item's default component map at runtime. If the component is present, it returns it; otherwise, it returns null.

---

## 2. Eating and Drinking Interactions

When an entity finishes consuming an item, the game calls `finishUsingItem`.

### 2.1 Event Translation
During consumption, critical tick and finish events are posted. ChainLoader bridges these events bi-directionally:
* **Forge/NeoForge**: `LivingEntityUseItemEvent.Start`, `LivingEntityUseItemEvent.Tick`, and `LivingEntityUseItemEvent.Finish` events.
* **Fabric Callback**: Mapped to Fabric's client/server item usage callbacks.

### 2.2 Loop Prevention and Hand Context
To prevent duplicate execution of eating effects (since both Fabric and Forge mods might listen to the same finishing ticks):
1. **ThreadLocal Token**: `EventTranslatorBus` tracks active consumption events on the thread.
2. **Context Preservation**: The active hand (`InteractionHand`) and item stack are stored during event execution and verified before applying legacy status effects or item modifications.
