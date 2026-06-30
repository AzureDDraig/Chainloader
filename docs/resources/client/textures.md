# Textures: Texture/Sheet Asset Path Remapping & AssetPathRelocator

Minecraft versions 1.20 and above singularized the asset directory structures for textures, models, and tags (e.g. renaming `textures/blocks/` to `textures/block/` and `textures/items/` to `textures/item/`). Legacy mods referencing plural paths in their code, assets, or models will fail to load or display textures.

ChainLoader solves this via `AssetPathRelocator`, a runtime namespace and path relocation system that applies singularization and namespace remapping in both resources and compiled bytecode.

---

## Directory Singularization Rules

`AssetPathRelocator` defines several standard string replacement rules to normalize resource paths into the modern singular format:

```java
public String singularizePath(String path) {
    if (path == null) return null;
    String result = path;
    result = result.replace("textures/blocks/", "textures/block/");
    result = result.replace("textures/items/", "textures/item/");
    result = result.replace("models/blocks/", "models/block/");
    result = result.replace("models/items/", "models/item/");
    result = result.replace("tags/blocks/", "tags/block/");
    result = result.replace("tags/items/", "tags/item/");
    return result;
}
```

---

## The Relocation Engine Flow

Asset relocation is coordinated between the bytecode loader and the resource loading pipelines:

### 1. Resource File Path Remapping
During mod JAR scanning, `VanillaAssetPatcher.populateModResources()` scans the ZIP entries of mod files.
* Every zip entry name (e.g. `assets/fabric/textures/blocks/my_tile.png`) is passed to `AssetPathRelocator.getInstance().relocateString()`.
* The relocator converts the path to `assets/chainloader/textures/block/my_tile.png`.
* The resource is registered under this singularized, relocated `ResourceLocation` in the client resources of `VirtualAssetPack`.

### 2. Dynamic JSON Content Patching
Texture references inside JSON files (like model JSONs or blockstates) also contain plural paths or legacy namespaces.
* To address this, `VirtualAssetPack` registers an `IndexPatcher` hook with `VanillaAssetPatcher`.
* When a JSON file is read, the patcher intercepts the JSON string and replaces legacy namespaces and plural paths before returning the bytes:

```java
assetPack.registerIndexPatcher((location, rawJson) -> {
    String patchedJson = rawJson;
    // Replace legacy namespaces (e.g. "fabric:" -> "chainloader:")
    for (Map.Entry<String, String> entry : AssetPathRelocator.getInstance().getNamespaceMappings().entrySet()) {
        patchedJson = patchedJson.replace("\"" + entry.getKey() + ":", "\"" + entry.getValue() + ":");
        patchedJson = patchedJson.replace("\"" + entry.getKey() + "/", "\"" + entry.getValue() + "/");
    }
    // Singularize paths in JSON
    patchedJson = patchedJson.replace("textures/blocks/", "textures/block/")
                             .replace("textures/items/", "textures/item/");
    return patchedJson;
});
```

### 3. Bytecode String Constant Patching
If a mod class instantiates a `ResourceLocation` using a hardcoded string path (e.g., `new ResourceLocation("mymod", "textures/blocks/tile.png")`), it would bypass the resource pack.
* `AssetPathRelocator` runs as a class transformer during class-loading.
* It uses ASM's `MethodVisitor` to intercept `LDC` instructions loading string constants.
* It replaces the plural string constant with the singularized/remaped string directly in the JVM memory:

```java
@Override
public void visitLdcInsn(Object value) {
    if (value instanceof String strValue) {
        String relocated = relocateString(strValue);
        if (!strValue.equals(relocated)) {
            super.visitLdcInsn(relocated);
            return;
        }
    }
    super.visitLdcInsn(value);
}
```

This guarantees that texture sheets and sprite maps are compiled, loaded, and displayed correctly by Minecraft's modern texture manager.
