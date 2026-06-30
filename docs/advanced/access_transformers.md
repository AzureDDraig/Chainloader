# Access Transformers: AccessWidener Compilation & Wildcard Mappings

To enable cross-compilation and runtime linkage between legacy APIs (which expect public fields/methods) and modern Minecraft (where these same members are protected, private, or final), ChainLoader features an on-the-fly **Access Widener** engine.

This engine parses Fabric-format access widener rules, compiles them into optimized O(1) lookup tables, and rewrites class definitions at load time using ASM.

---

## 1. AccessWidener Format & Syntax

ChainLoader supports the standard Fabric access-widener syntax. The configuration declares target classes, fields, and methods along with their modification directives:

```text
accessWidener v1 named
accessible class net/minecraft/world/entity/Entity
mutable field net/minecraft/world/entity/Entity level Lnet/minecraft/world/level/Level;
extendable method net/minecraft/client/gui/screens/Screen init ()V
```

### Directives Map
*   **`accessible`**: Strips `private`/`protected` modifiers and applies `public`.
*   **`extendable`**: Strips `final` from classes/methods, making methods `protected`/`public` to allow inheritance.
*   **`mutable`**: Strips `final` from fields to allow modification.

---

## 2. Compilation & O(1) Lookup Cache

At game startup, `AccessWidenerCompiler` reads all registered access widener configurations. To ensure that classloading remains extremely fast, it compiles these text rules into high-performance maps:

```java
// Compiled lookup structures (AccessWidenerCompiler.java)
private final Set<String> compiledClasses = ConcurrentHashMap.newKeySet();
private final Map<String, Map<String, AccessType>> compiledFields = new ConcurrentHashMap<>();
private final Map<String, Map<String, AccessType>> compiledMethods = new ConcurrentHashMap<>();
```

During the classload cycle, `ChainClassLoader` queries `AccessWidenerCompiler.isClassTargeted(className)`. If a class lacks access-widening rules, the classloader skips class visitor generation entirely (a fast-path optimization).

---

## 3. Wildcard Descriptor Matcher (*)

In standard access wideners, fields and methods are matched using their exact type descriptor (e.g. `Lnet/minecraft/world/level/Level;` or `()V`). 

However, when bytecode is obfuscated or intermediate mappings differ, compiling exact descriptors can cause target mismatches. ChainLoader solves this by supporting the wildcard `*` descriptor.

### Wildcard Match Resolution (AccessWidener.java)
```java
// Match Field:
Map<String, AccessType> fields = compiledFields.get(classInternalName);
if (fields != null) {
    AccessType type = fields.get(name + ":" + descriptor);
    if (type == null) {
        // Fallback: match using wildcard descriptor
        type = fields.get(name + ":*");
    }
    if (type != null) {
        // Apply widening (accessible / mutable)
    }
}
```
This wildcard registry allows rules like `ctb.j` (matching `CreativeModeTabs.TABS`) to successfully widen fields without needing to know the exact runtime obfuscated descriptor of the array/collection.

---

## 4. Single-Pass Transformation Pipeline

To maximize classloading speed, the compiled visitor (`CompiledAccessWidenerVisitor`) performs multiple bytecode operations in a single ASM traversal, coordinating with two subagent components:

1.  **Mixin Redirector**: Rewrites callsites if access-widened private methods are invoked virtually.
2.  **Annotation Stripper**: Discards environment-specific annotations (`@Environment`, `@OnlyIn`) to prevent side crashes.

### Member Widening Specifics
```java
// Widen Method access (AccessWidenerClassVisitor)
private int widenMethodAccess(String methodName, int access, Set<AccessType> rules) {
    if (rules.contains(AccessType.EXTENDABLE)) {
        access &= ~Opcodes.ACC_FINAL;
        if ((access & Opcodes.ACC_PUBLIC) == 0) {
            access &= ~(Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED);
            access |= Opcodes.ACC_PROTECTED;
        }
    } else if (rules.contains(AccessType.ACCESSIBLE)) {
        boolean wasPrivate = (access & Opcodes.ACC_PRIVATE) != 0;
        access &= ~(Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED);
        access |= Opcodes.ACC_PUBLIC;
        // PRIVATE virtual methods made public must have ACC_FINAL added
        // to prevent JVM verification errors on subclasses that do not override them
        if (wasPrivate && !"<init>".equals(methodName) && !"<clinit>".equals(methodName)) {
            access |= Opcodes.ACC_FINAL;
        }
    }
    return access;
}
```

---

## 5. Built-in Access Rules

To ensure essential Minecraft features remain compatible with legacy mods, ChainLoader registers several programmatic rules by default:

| Legacy Target | Obfuscated Class | Obfuscated Field | Applied Access |
| :--- | :--- | :--- | :--- |
| `Entity.level` | `bsr` | `r` | `ACCESSIBLE` & `MUTABLE` |
| `BlockEntity.level` | `cmu` | `d` | `ACCESSIBLE` & `MUTABLE` |
| `CreativeModeTabs.TABS` | `ctb` | `j` | `ACCESSIBLE` & `MUTABLE` |
