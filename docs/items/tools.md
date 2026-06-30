# Tools & Mining Speed

Minecraft 1.20.5+ migrated the tool tier system (mining levels, destroy speed, and durability) from code-based enums (`Tiers`, `Tier` interfaces) to registry-driven systems and item components. In 1.21.1, tools declare their mining properties inside the `DataComponents.TOOL` component.

This document describes how ChainLoader shims legacy tool tiers and maps mining speed calculations at runtime.

---

## 1. Tool Component Shimming

Legacy mods define tools using custom implementations of `Tier` and register them via classes like `PickaxeItem`, `AxeItem`, or `ShovelItem`:
```java
// Legacy Mod Tool Definition
public class RubyPickaxe extends PickaxeItem {
    public RubyPickaxe(Tier tier, int attackDamage, float attackSpeed, Properties properties) {
        super(tier, attackDamage, attackSpeed, properties);
    }
}
```

### 1.1 Automated Component Compilation
During the item registration and binding phase:
1. **Tier Extraction**: ChainLoader reads the legacy `Tier` properties:
   - `getUses()` (durability)
   - `getSpeed()` (efficiency multiplier)
   - `getAttackDamageBonus()` (combat bonus)
   - `getLevel()` (mining level, e.g. Diamond = 3, Netherite = 4)
   - `getIngredient()` (repair ingredient)
2. **Tool Component Assembly**: Compiles these values into a modern `Tool` component record (`DataComponents.TOOL`). This component specifies:
   - Mining rules: blocks the tool is effective against (e.g. pickaxe matches `#minecraft:mineable/pickaxe`).
   - Mining speed: the baseline destroy speed multiplier (from `getSpeed()`).
   - Correct tool rules: if the mining level satisfies block requirements (from `getLevel()`).
3. **Default Components Injection**: Inject the compiled `Tool` and durability components into the item's default component map.

---

## 2. Mining Speed & Destroy Speed Redirects

When the game calculates how fast a block is mined, it queries `ItemStack.getDestroySpeed(BlockState)`:

### 2.1 Destroy Speed Queries
Legacy calls to `Item.getDestroySpeed` or `ItemStack.getDestroySpeed` are transformed:
* **Redirect target**: `BytecodeTransformer` redirects these calls to query the stack's data components.
* **Component read**: Returns the destroy speed defined in the `DataComponents.TOOL` component.

### 2.2 Tool Action Compatibility (Forge/NeoForge)
In legacy Forge, tools override `canPerformAction(ItemStack, ToolAction)` to support block interactions (like stripping logs or tilling soil).
* **Bridging**: ChainLoader maps the legacy `ToolAction` requests to modern NeoForge tool action parameters, ensuring custom multi-tools or specialty tools trigger interactions correctly.
