# Item Interactions & Tooltips

In Minecraft 1.21.1, item interaction, usage, and tooltip generation methods were refactored. The return type names changed, parameters were wrapped in context objects, and virtual method targets were relocated. 

This document describes how ChainLoader redirects and shims item interactions, use callbacks, and tooltips at runtime.

---

## 1. Interaction Result & Method Remapping

In previous versions, Yarn mapped results to `ActionResult`, while Mojang mapped them to `InteractionResult`. To maintain compatibility for both Fabric and Forge mods, the classloader registers a class mapping redirect:
```text
net/minecraft/util/ActionResult -> net/minecraft/world/InteractionResult
```

### 1.1 Item Use-on-Block Callback Refactoring
In 1.21.1, use-on-block callbacks require modern contexts. If a legacy mod calls or overrides `useOn(UseOnContext)` (which was renamed/refactored), ChainLoader's `BytecodeTransformer` re-routes the invocation:
* Calls targeting the legacy method are intercepted and mapped to the corresponding 1.21.1 equivalents.
* Returns are translated safely, and exception wrappers prevent crashes if custom item use logic throws type-cast exceptions.

---

## 2. Hover Tooltips & Context Bridging

One of the largest discrepancies in the 1.21.1 item API is the `appendHoverText` method signature change on `Item` and `Block`.

* **Legacy Signature**: `appendHoverText(ItemStack, Level, List<Component>, TooltipFlag)`
* **Modern Signature**: `appendHoverText(ItemStack, TooltipContext, List<Component>, TooltipFlag)`
  - Where `TooltipContext` is represented by class `net.minecraft.world.item.Item$TooltipContext` (obfuscated as `cul$b`).

To keep legacy mod tooltips rendering, ChainLoader injects a legacy bridge method into `Item` and `Block` classes:

### 2.1 Injected Tooltip Bridges
1. **Item Tooltip Bridge (`appendHoverText_legacy`)**:
   `BytecodeTransformer` injects the legacy signature `appendHoverText_legacy` on the `Item` class (`cul`). When called, it evaluates the `Level` parameter:
   - If level is null, it retrieves the static context `TooltipContext.EMPTY` (`cul$b.a`).
   - If level is not null, it invokes `TooltipContext.of(level)` (`cul$b.a(Level)`).
   - Finally, it calls the new `appendHoverText` method non-virtually via `INVOKESPECIAL`:
   ```java
   public void appendHoverText_legacy(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
       TooltipContext context = (level != null) ? TooltipContext.of(level) : TooltipContext.EMPTY;
       this.appendHoverText(stack, context, tooltip, flag);
   }
   ```

2. **Block Tooltip Bridge**:
   Similarly, on the `Block` class (`dfy`), the bridge method accepts `BlockGetter`. It performs an `INSTANCEOF` check on the `BlockGetter` to see if it is a `Level` instance, converts it to `TooltipContext`, and delegates.

### 2.2 Super Tooltip Call Redirection (`superAppendHoverText`)
When a custom mod item extends `Item` and calls `super.appendHoverText(...)`, the call fails at runtime. ChainLoader redirects this call to `EventBridgeHelper.superAppendHoverText`:
* **MethodHandle Resolution**: It uses a `MethodHandle` lookup to locate `appendHoverText` (or obfuscated name `a`) in the `Item` class.
* **Invoking Parent**: Binds the custom item instance and safely executes the superclass logic:
  ```java
  superAppendHoverTextItemHandle.bindTo(item).invoke(stack, context, tooltip, flag);
  ```
This redirection prevents JVM linkage errors while preserving item tooltip behavior.
