# NBT & CompoundTag Operations Remapping

Minecraft 1.20.5+ completely overhauled item and block data storage, replacing the legacy unstructured NBT tag (`Tag`/`CompoundTag`) with typed, immutable **Data Components** (`DataComponentMap`). 

ChainLoader bridges legacy mods (which expect direct read/write access to `CompoundTag` objects via `ItemStack.getOrCreateTag()`, `ItemStack.getTag()`, etc.) to modern NeoForge 1.21.1 components. This is achieved via class remapping, ASM bytecode injection, and a custom tracked NBT implementation.

---

## 1. The Component vs. Legacy NBT Challenge

In Minecraft 1.20.1 and prior, `ItemStack` held an internal `CompoundTag` (`tag`) containing arbitrary nested data. In 1.21.1, all unstructured mod data is grouped inside the `net.minecraft.core.component.DataComponents.CUSTOM_DATA` component, which stores a `CustomData` wrapper containing a `CompoundTag`. 

Directly modifying the NBT returned by a component does not propagate changes back to the `ItemStack` because components are designed to be immutable or explicitly set. To solve this, ChainLoader intercepts NBT requests and returns a **tracked** NBT implementation.

---

## 2. TrackedCompoundTag Implementation

ChainLoader defines `net.chainloader.loader.compat.bridge.EventBridgeHelper$TrackedCompoundTag` which extends `net.minecraft.nbt.CompoundTag`. It wraps the parent `ItemStack` and delegates all modifications (puts, removes) to update the item's component map on the fly.

### Member Signature
```java
public static class TrackedCompoundTag extends net.minecraft.nbt.CompoundTag {
    private final net.minecraft.world.item.ItemStack stack;

    public TrackedCompoundTag(net.minecraft.world.item.ItemStack stack, net.minecraft.nbt.CompoundTag src) {
        this.stack = stack;
        if (src != null) {
            for (String key : src.getAllKeys()) {
                super.put(key, src.get(key).copy());
            }
        }
    }

    private void update() {
        stack.set(
            net.minecraft.core.component.DataComponents.CUSTOM_DATA, 
            net.minecraft.world.item.component.CustomData.of(this)
        );
    }

    @Override
    public net.minecraft.nbt.Tag put(String key, net.minecraft.nbt.Tag value) {
        net.minecraft.nbt.Tag res = super.put(key, value);
        update();
        return res;
    }

    @Override
    public void putInt(String key, int value) {
        super.putInt(key, value);
        update();
    }

    // Overridden for all putXXX types (putByte, putShort, putLong, putFloat, putDouble, putString, putByteArray, putIntArray, putLongArray, putBoolean, putUUID) and remove()
}
```

---

## 3. ItemStack NBT Access Redirects

Bytecode transformers detect calls to legacy NBT methods on `ItemStack` and redirect them to `EventBridgeHelper`:

*   **`getTag()`** redirect:
    *   *Legacy Target:* `Lnet/minecraft/world/item/ItemStack;getTag()Lnet/minecraft/nbt/CompoundTag;`
    *   *Redirect:* `INVOKESTATIC net/chainloader/loader/compat/bridge/EventBridgeHelper.getItemStackNbt (Ljava/lang/Object;)Ljava/lang/Object;`
*   **`getOrCreateTag()`** redirect:
    *   *Legacy Target:* `Lnet/minecraft/world/item/ItemStack;getOrCreateTag()Lnet/minecraft/nbt/CompoundTag;`
    *   *Redirect:* `INVOKESTATIC net/chainloader/loader/compat/bridge/EventBridgeHelper.getOrCreateSubNbt (Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/Object;` with `"custom_data"` or returns a `TrackedCompoundTag` created from empty NBT.
*   **`setTag(CompoundTag)`** redirect:
    *   *Legacy Target:* `Lnet/minecraft/world/item/ItemStack;setTag(Lnet/minecraft/nbt/CompoundTag;)V`
    *   *Redirect:* `INVOKESTATIC net/chainloader/loader/compat/bridge/EventBridgeHelper.setItemStackNbt (Ljava/lang/Object;Ljava/lang/Object;)V`
*   **`hasTag()`** redirect:
    *   *Legacy Target:* `Lnet/minecraft/world/item/ItemStack;hasTag()Z`
    *   *Redirect:* `INVOKESTATIC net/chainloader/loader/compat/bridge/EventBridgeHelper.hasNbt (Ljava/lang/Object;)Z`

### Bridge Helper Logic
```java
public static Object getItemStackNbt(Object stackObj) {
    if (stackObj instanceof net.minecraft.world.item.ItemStack stack) {
        net.minecraft.world.item.component.CustomData customData = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        net.minecraft.nbt.CompoundTag tag = null;
        if (customData != null) {
            tag = customData.copyTag();
            if (tag.contains("chainloader_nbt_compat")) {
                tag.remove("chainloader_nbt_compat");
            }
        } else {
            return null;
        }
        return new TrackedCompoundTag(stack, tag);
    }
    return null;
}

public static void setItemStackNbt(Object stackObj, Object nbtObj) {
    if (stackObj instanceof net.minecraft.world.item.ItemStack stack) {
        if (nbtObj == null) {
            stack.remove(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        } else if (nbtObj instanceof net.minecraft.nbt.CompoundTag tag) {
            if (tag.isEmpty()) {
                tag = tag.copy();
                tag.putBoolean("chainloader_nbt_compat", true);
            }
            stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(tag));
        }
    }
}
```

---

## 4. BlockPos & NbtUtils Serialization Remapping

In modern Minecraft versions, `NbtUtils.readBlockPos` and `NbtUtils.writeBlockPos` have been refactored or require registry provider context. To support legacy callers, ChainLoader redirects these static utility invocations to self-contained NBT parsers.

### Redirect Triggers (BytecodeTransformer.java)
```java
boolean isNbtUtils = "net/minecraft/nbt/NbtUtils".equals(owner) || "uq".equals(owner);
boolean isReadBlockPos = "readBlockPos".equals(name) || "a".equals(name);
boolean hasOldNbtSig = desc != null && (desc.contains("CompoundTag") || desc.contains("Lub;")) && (desc.endsWith("BlockPos;") || desc.endsWith("Ljd;"));

if (opcode == Opcodes.INVOKESTATIC && isNbtUtils && isReadBlockPos && hasOldNbtSig) {
    super.visitMethodInsn(
        Opcodes.INVOKESTATIC,
        "net/chainloader/loader/compat/bridge/EventBridgeHelper",
        "readBlockPosBridge",
        remapper.mapDesc("(Lnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/core/BlockPos;"),
        false
    );
    return;
}
```

### Bridge Serializers
```java
public static net.minecraft.core.BlockPos readBlockPosBridge(net.minecraft.nbt.CompoundTag tag) {
    if (tag == null) {
        return net.minecraft.core.BlockPos.ZERO;
    }
    int x = tag.getInt("X");
    int y = tag.getInt("Y");
    int z = tag.getInt("Z");
    return new net.minecraft.core.BlockPos(x, y, z);
}

public static net.minecraft.nbt.CompoundTag writeBlockPosBridge(net.minecraft.core.BlockPos pos) {
    if (pos == null) {
        return new net.minecraft.nbt.CompoundTag();
    }
    net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
    tag.putInt("X", pos.getX());
    tag.putInt("Y", pos.getY());
    tag.putInt("Z", pos.getZ());
    return tag;
}
```
