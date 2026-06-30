# Logical Sides & Side Stripping

Minecraft divides execution into two environments: the **Client** (physical display, input, sound, client rendering) and the **Server** (game logic, physics, server-side ticks). Mods declare code that should only load on one of these sides using annotations. 

Because legacy Fabric and Forge use different side annotations, ChainLoader uses `SideAnnotationStripper` to dynamically strip incompatible code at classload-time.

---

## 1. Architectural Role

Legacy mods declare environment-specific code using:
* **Fabric**: `@Environment(EnvType.CLIENT)` or `@Environment(EnvType.SERVER)`
* **Forge/NeoForge**: `@OnlyIn(Dist.CLIENT)` or `@OnlyIn(Dist.DEDICATED_SERVER)`

When a mod is loaded on a dedicated server, any reference to client-only classes, fields, or methods will result in a `NoClassDefFoundError` or `VerifyError` if they are not stripped. 

`net.chainloader.loader.core.transform.SideAnnotationStripper` acts as a class transformer that parses these annotations and strips members that mismatch the currently running side.

---

## 2. Side Annotation Stripper (`SideAnnotationStripper`)

The stripper parses class bytecode before it is defined by the JVM. It uses a fast-path preview check (inspecting the constant pool bytes for annotation descriptors) to avoid ASM overhead on classes that do not declare side restrictions.

### 2.1 Default Translation Rules
`SideAnnotationStripper` contains pre-defined translation mapping rules for all supported modloaders:

| Modloader | Annotation Descriptor | Enum Descriptor | Side Mappings |
| :--- | :--- | :--- | :--- |
| **Fabric** | `Lnet/fabricmc/api/Environment;` | `Lnet/fabricmc/api/EnvType;` | `CLIENT` -> `EnvType.CLIENT`<br>`SERVER` -> `EnvType.SERVER` |
| **Forge** | `Lnet/minecraftforge/api/distmarker/OnlyIn;` | `Lnet/minecraftforge/api/distmarker/Dist;` | `CLIENT` -> `EnvType.CLIENT`<br>`DEDICATED_SERVER` -> `EnvType.SERVER` |
| **NeoForge** | `Lnet/neoforged/api/distmarker/OnlyIn;` | `Lnet/neoforged/api/distmarker/Dist;` | `CLIENT` -> `EnvType.CLIENT`<br>`DEDICATED_SERVER` -> `EnvType.SERVER` |

Custom annotation rules can be registered using:
```java
stripper.registerRule("Lmy/mod/ClientOnly;", "Lmy/mod/Side;", Map.of("CLIENT", EnvType.CLIENT));
```

### 2.2 Stripping Behavior
1. **Class-Level Mismatch**: If the class itself is annotated for a side that does not match the active environment, the stripper throws a `SideStrippedException`. The `ChainClassLoader` catches this exception and throws a standard `ClassNotFoundException`.
2. **Field-Level Mismatch**: Removes the field from the class definition entirely.
3. **Method-Level Mismatch**: Removes the method from the class definition entirely.

---

## 3. Subagent Coordination (Mixin & Access Transformers)

When fields or methods are stripped, they disappear from the class. If other components (such as Access Transformers or Mixins) try to access or inject into these deleted members, the game will crash during startup with linkage errors.

To prevent this, `SideAnnotationStripper` exposes global queryable registries of all stripped members:
```java
// Globally check if a class was stripped
SideAnnotationStripper.isClassStripped("net/minecraft/client/renderer/entity/EntityRenderer");

// Globally check if a method was stripped
SideAnnotationStripper.isMethodStripped("net/minecraft/world/entity/player/Player", "m_21111_", "()V");

// Globally check if a field was stripped
SideAnnotationStripper.isFieldStripped("net/minecraft/world/entity/Entity", "f_19853_", "Lnet/minecraft/world/level/Level;");
```

Other runtime subagents (like `MixinPatcher` and `AccessWidenerCompiler`) query these registries. If a target method or field has been stripped, they skip applying their transformations, ensuring runtime safety.
