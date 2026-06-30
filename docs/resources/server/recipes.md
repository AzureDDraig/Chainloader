# Recipes: Recipe Patching

Minecraft 1.21.1 changed the JSON structure for crafting and cooking recipe results. In older versions, recipe output definitions specified the result item using the `"item"` key, whereas modern versions expect the `"id"` key. Additionally, recipe serializers now use MapCodecs and StreamCodecs rather than raw JSON and packet buffer readers/writers.

ChainLoader solves these compatibility barriers through load-time bytecode modification of `RecipeManager` and custom codec wrappers.

---

## Bytecode Injection in `RecipeManager`

During data reloading, Minecraft parses all recipe JSON files into a map (`Map<ResourceLocation, JsonElement>`) before passing them to `RecipeManager.apply` (obfuscated class `czd`, method `apply` or `a`).

ChainLoader's bytecode transformer `Chainlink1_21_1_Base` intercepts the `apply` method and injects a call to `EventBridgeHelper.patchRecipes` at the start:

```java
// From Chainlink1_21_1_Base.java
private byte[] transformRecipeManager(byte[] bytes) {
    ClassReader cr = new ClassReader(bytes);
    ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS);
    ClassVisitor cv = new ClassVisitor(Opcodes.ASM9, cw) {
        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
            boolean isApply = ("apply".equals(name) || "a".equals(name)) &&
                    ("(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V".equals(descriptor) ||
                     "(Ljava/util/Map;Laue;Lbnf;)V".equals(descriptor));
            if (isApply) {
                return new MethodVisitor(Opcodes.ASM9, mv) {
                    @Override
                    public void visitCode() {
                        super.visitCode();
                        visitVarInsn(Opcodes.ALOAD, 1); // Load the first argument (the map of recipes)
                        visitMethodInsn(Opcodes.INVOKESTATIC, "net/chainloader/loader/compat/bridge/EventBridgeHelper", 
                            "patchRecipes", "(Ljava/util/Map;)V", false);
                    }
                };
            }
            return mv;
        }
    };
    cr.accept(cv, 0);
    return cw.toByteArray();
}
```

---

## JSON Structure Patching

The `patchRecipes` method iterates through the map of loaded recipe JSON elements at runtime. If it detects a legacy recipe result format, it updates the JSON structure in-place before the game parses it:

```java
public static void patchRecipes(java.util.Map<?, ?> map) {
    if (map == null) return;
    for (java.util.Map.Entry<?, ?> entry : map.entrySet()) {
        Object value = entry.getValue();
        if (value instanceof com.google.gson.JsonObject recipeObj) {
            if (recipeObj.has("result")) {
                com.google.gson.JsonElement resultElement = recipeObj.get("result");
                if (resultElement.isJsonObject()) {
                    com.google.gson.JsonObject resultObj = resultElement.getAsJsonObject();
                    // Copy "item" to "id" if "id" is missing
                    if (resultObj.has("item") && !resultObj.has("id")) {
                        resultObj.addProperty("id", resultObj.get("item").getAsString());
                    }
                }
            }
        }
    }
}
```

This transforms a legacy result structure:
```json
"result": {
  "item": "mymod:magic_potion",
  "count": 1
}
```
Into the format expected by modern serializers:
```json
"result": {
  "item": "mymod:magic_potion",
  "id": "mymod:magic_potion",
  "count": 1
}
```

---

## Legacy Serializer Codec Wrappers

In Minecraft 1.21.1, recipe serializers require a `MapCodec` and a `StreamCodec` to deserialize recipes from JSON and sync them over the network. Legacy serializers implemented `read(ResourceLocation, JsonObject)` and `read(ResourceLocation, FriendlyByteBuf)`.

To bridge this, `EventBridgeHelper` wraps legacy serializers using two compatibility classes:

1. **`LegacyRecipeMapCodec`**: Wraps the legacy serializer's JSON reader and writer into a modern `MapCodec`. During decoding, it feeds the recipe JSON directly to the legacy `read` method:
   ```java
   Object recipe = readMethod.invoke(serializer, dummyLoc, jsonObject);
   return DataResult.success(recipe);
   ```
2. **`LegacyRecipeStreamCodec`**: Wraps the legacy serializer's packet buffer reader and writer into a modern `StreamCodec<FriendlyByteBuf, Object>`, delegating reading/writing to the legacy network methods during client/server synchronization.
