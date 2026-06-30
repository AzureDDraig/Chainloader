# Inventory Transactions: Insertion & Extraction Bridges

Inventory insertion and extraction cover how items are moved programmatically (via automated machinery, item pipes, hoppers, or custom block actions). Because Fabric uses a transaction-based model (`Storage<ItemVariant>`) and Forge/NeoForge use a slot-based capability model (`IItemHandler`), ChainLoader implements a transaction bridge to translate between these paradigms.

---

## Transaction Paradigms

### Fabric Transfer API (`Storage`)
Fabric separates item queries into variants (`ItemVariant`, which combines an `Item` with custom Data Components/NBT) and quantities. Operations require a `TransactionContext` and can be committed or aborted:

```java
// Fabric transaction flow
try (Transaction tx = Transaction.openOuter()) {
    long inserted = storage.insert(ItemVariant.of(Items.IRON_INGOT), 64, tx);
    if (inserted == 64) {
        tx.commit(); // Only changes inventory permanently here
    }
}
```

### Forge & NeoForge API (`IItemHandler`)
Forge and NeoForge query item transfers by slot indices and use a boolean `simulate` flag:

```java
// NeoForge slot-based transfer
ItemStack remaining = itemHandler.insertItem(0, new ItemStack(Items.IRON_INGOT, 64), true); // Simulate
if (remaining.isEmpty()) {
    itemHandler.insertItem(0, new ItemStack(Items.IRON_INGOT, 64), false); // Perform
}
```

---

## Bidirectional Transaction Bridging

ChainLoader bridges these transfer mechanisms bidirectionally at runtime through its core capability providers and class adapters.

### 1. Fabric Mod Accessing a NeoForge Block
When a Fabric machine or pipe attempts to insert items into a NeoForge block entity:
1. **API Lookup**: The Fabric mod queries `ItemStorage.SIDED.find(level, pos, direction)`.
2. **NeoForge Query**: The lookup interceptor queries the NeoForge item capability: `Capabilities.ItemHandler.BLOCK`.
3. **Storage Wrapper**: The returned `IItemHandler` is wrapped in a Fabric `Storage<ItemVariant>` shim.
4. **Method Translation**:
   * When `insert(variant, amount, transaction)` is called on the shim:
     * It converts `ItemVariant` to a vanilla `ItemStack` (merging item ID and Data Components).
     * It maps the insertion across the `IItemHandler` slots by invoking `insertItem(slot, stack, true)` (simulated).
     * If the transaction commits, it calls `insertItem(slot, stack, false)` (non-simulated).
     * If the transaction aborts, it discards the simulated actions.

### 2. NeoForge Mod Accessing a Fabric Block
When a NeoForge hopper or pipe attempts to insert items into a Fabric block entity:
1. **Capability Event**: NeoForge requests the item capability provider for the block.
2. **Bridge Query**: The provider delegates to `ChainCapabilityBridge.queryItems(level, pos, state, be, side)`.
3. **Item Handler Wrapper**: The block's Fabric `Storage<ItemVariant>` is resolved and wrapped in a `NeoForgeItemHandlerAdapter` (implementing NeoForge's `IItemHandler` interface).
4. **Method Translation**:
   * When `insertItem(slot, stack, simulate)` is called on the adapter:
     * If `simulate` is true:
       * It opens a nested Fabric transaction (`Transaction.openNested(parent)`).
       * It calls `storage.insert(ItemVariant.of(stack), stack.getCount(), tx)`.
       * It aborts the transaction to discard changes and returns the remaining count.
     * If `simulate` is false:
       * It opens a transaction.
       * It calls `storage.insert(...)`.
       * It commits the transaction to write the item changes to the Fabric inventory.

This adapter mapping guarantees that hoppers, extraction pipes, and sorting networks can move items in and out of inventories across modloader APIs.
