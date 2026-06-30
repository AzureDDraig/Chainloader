# ResourceLocation Redirects & Fabric ID Shims

Minecraft uses identifiers (namespaces and paths, e.g. `minecraft:stone`) to reference registry entries. In Mojang's code, this is represented by `ResourceLocation`. In Fabric's yarn mappings, it is called `Identifier`.

In Minecraft 1.21.1, Mojang removed the legacy public constructor `new ResourceLocation(String)` (e.g., `new ResourceLocation("mymod:custom_block")`), replacing it with static factory parsing methods: `ResourceLocation.parse(String)`. Any legacy mod attempting to instantiate an identifier via the single-string constructor crashes with a `NoSuchMethodError`.

ChainLoader resolves this by dynamically injecting the single-string constructor at runtime using ASM.

---

## 1. ResourceLocation Constructor Injection

The class transformer `Chainlink1_21_1_Base.java` monitors the loading of the `ResourceLocation` class (obfuscated name: `akr`). If it detects that the single-string constructor is absent, it injects it:

```java
// Chainlink1_21_1_Base.java - transformResourceLocation
@Override
public void visitEnd() {
    if (!hasSingleStringConstructor) {
        // Inject public ResourceLocation(String)
        MethodVisitor mv = super.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "(Ljava/lang/String;)V", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0); // Load 'this'
        
        // Pass string to EventBridgeHelper to get Namespace
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "net/chainloader/loader/compat/bridge/EventBridgeHelper", "getResourceLocationNamespace", "(Ljava/lang/String;)Ljava/lang/String;", false);
        
        // Pass string to EventBridgeHelper to get Path
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "net/chainloader/loader/compat/bridge/EventBridgeHelper", "getResourceLocationPath", "(Ljava/lang/String;)Ljava/lang/String;", false);
        
        // Invoke modern two-argument constructor: akr(String namespace, String path)
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "akr", "<init>", "(Ljava/lang/String;Ljava/lang/String;)V", false);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(3, 2);
        mv.visitEnd();
    }
    super.visitEnd();
}
```

---

## 2. String Splitting Logic

The injected constructor delegates string parsing to `EventBridgeHelper`:

```java
// EventBridgeHelper.java - String Parsers
public static String getResourceLocationNamespace(String s) {
    int colon = s.indexOf(':');
    return colon >= 0 ? s.substring(0, colon) : "minecraft";
}

public static String getResourceLocationPath(String s) {
    int colon = s.indexOf(':');
    return colon >= 0 ? s.substring(colon + 1) : s;
}
```

If the string contains a colon (e.g. `mymod:item`), it is split into namespace `mymod` and path `item`. If there is no colon (e.g. `item`), the namespace defaults to `minecraft`.

---

## 3. Fabric Identifier Shims

Fabric mods import the class `net.fabricmc.api.Identifier` or `net.minecraft.util.Identifier` to reference resources.

To support this, ChainLoader:
1.  **Class Mapping**: Maps references targeting `net/fabricmc/api/Identifier` or similar Fabric paths directly to `net/minecraft/resources/ResourceLocation` in the bytecode remapper.
2.  **Constructor Mapping**: Because `Identifier(String)` constructor mappings compile to `<init>(Ljava/lang/String;)V`, the injected constructor on `ResourceLocation` ensures that these instantiations resolve correctly at runtime without verification or link errors.
