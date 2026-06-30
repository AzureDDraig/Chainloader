# Attributes & Modifiers

Minecraft 1.20.5+ updated entity attributes. Attribute modifiers and registrations were converted to registry holders, and legacy fields on `EntityAttribute` were restructured. 

This document describes how ChainLoader registers attributes and shims attribute getter systems.

---

## 1. Attributes Registry Translation

Legacy mods register custom attributes (e.g. adding attributes to player class definitions, or registering armor stats) by calling registration hooks or adding them to registries:
```java
// Legacy Mod Attribute Definition
public static final EntityAttribute CRITICAL_STRIKE = new EntityAttribute("attribute.name.critical_strike", 0.0);
```

To support this:
1. **Lazy Staging**: Custom `EntityAttribute` instantiations are intercepted and staged in `RegistryStager` under `minecraft:attribute`.
2. **Registry Injection**: During the NeoForge attribute registration lifecycle, the staging engine injects these custom attributes into the modern registry (`BuiltInRegistries.ATTRIBUTE`).
3. **Registry Holder Compilation**: Mapped attributes are wrapped into dynamic registry Holders so they can be registered on entities.

---

## 2. Attribute Getter Redirections

In Minecraft 1.21.1, entities query attribute values using registry holders:
`LivingEntity.getAttribute(Holder<Attribute>)`

Legacy mods query attributes using the old `EntityAttribute` object:
`LivingEntity.getAttributeValue(EntityAttribute)` (or `LivingEntity.getAttribute(EntityAttribute)`)

### 2.1 Bytecode Translation
`BytecodeTransformer` redirects attribute getters in legacy bytecode:
* **The Intercept**: Calls to `LivingEntity.getAttribute(EntityAttribute)` are captured.
* **The Redirect**: The call is modified to first lookup the corresponding attribute registry holder inside the modern registry, and then call the updated `getAttribute(Holder<Attribute>)` method.

### 2.2 Attribute Instance Shims
If a legacy mod requests an `AttributeInstance` to add custom modifiers dynamically:
* The getter is redirected to fetch the modern `AttributeInstance`.
* Modifications (e.g., adding an `AttributeModifier` with UUIDs and operation types) are translated:
  - Mappings convert the old constructor `AttributeModifier(UUID, String, double, Operation)` to the updated 1.21.1 structure (which uses resource locations instead of UUID strings for modifier keys).
  - This translation ensures custom attribute boosts (like potion effects or speed modifiers) are calculated correctly.
