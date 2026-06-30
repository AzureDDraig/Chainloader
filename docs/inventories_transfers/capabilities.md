# Capabilities Translation

Transferring energy, fluids, and items between blocks is handled differently depending on the modding API:
* **Forge**: Uses the Capability system (`IEnergyStorage`, `IFluidHandler`, `IItemHandler`).
* **Fabric**: Uses the Transfer API (`EnergyStorage`, `FluidStorage`, `ItemStorage`).
* **NeoForge 1.21.1**: Uses modern Block Capabilities (`Capabilities.Energy`, `Capabilities.FluidHandler`, `Capabilities.ItemHandler`).

ChainLoader bridges all three ecosystems using a unified intermediate interface layer (`ChainCapabilityBridge`) and bi-directional adapters.

---

## Unified Capability Architecture

ChainLoader defines three core capability interfaces in `loader-core` that abstract the common operations:
1. `ChainEnergyStorage`: Common operations for energy storage and transfer (receive, extract, capacity).
2. `ChainItemHandler`: Common slot-based inventory operations (slots, insertion, extraction, validity).
3. `ChainFluidHandler`: Common tank-based fluid operations (tanks, capacity, fill, drain).

### `ChainCapabilityBridge`

`ChainCapabilityBridge` acts as the central registry for capability queries:

```java
public class ChainCapabilityBridge {
    private static final Map<String, Provider<ChainEnergyStorage>> energyProviders = new ConcurrentHashMap<>();
    
    public static void registerEnergyProvider(String modId, Provider<ChainEnergyStorage> provider) {
        energyProviders.put(modId, provider);
    }

    public static ChainEnergyStorage queryEnergy(Object level, Object pos, Object state, Object blockEntity, Object side) {
        for (Provider<ChainEnergyStorage> p : energyProviders.values()) {
            ChainEnergyStorage s = p.getCapability(level, pos, state, blockEntity, side);
            if (s != null) return s;
        }
        return null;
    }
    // ... item and fluid equivalents ...
}
```

---

## Bidirectional Energy Adapters

To bridge energy storage across Forge, Fabric, and NeoForge, ChainLoader implements a set of wrapper adapters:

```
[Fabric EnergyStorage] <--> [FabricEnergyStorageCoreAdapter]
                                      |
                                      v
                             [ChainEnergyStorage]
                                      ^
                                      |
[Forge IEnergyStorage]  <--> [CoreEnergyStorageAdapter]
                                      |
                                      v
                       [NeoForgeEnergyStorageAdapter] <--> [NeoForge Energy]
```

### The Registration Pipeline

During mod initialization, the capability providers are registered to the core bridge:

```java
// From EventBridgeHelper.java
static {
    // 1. Register Forge Energy Storage provider to Core Capability Bridge
    ChainCapabilityBridge.registerEnergyProvider("forge_compat", (level, pos, state, be, side) -> {
        if (be instanceof net.minecraftforge.common.capabilities.ICapabilityProvider provider) {
            LazyOptional<IEnergyStorage> cap = provider.getCapability(CapabilityEnergy.ENERGY, (Direction) side);
            if (cap.isPresent()) {
                return new CoreEnergyStorageAdapter(cap.orElseThrow());
            }
        }
        return null;
    });

    // 2. Register Fabric Energy Storage provider to Core Capability Bridge
    ChainCapabilityBridge.registerEnergyProvider("fabric_compat", (level, pos, state, be, side) -> {
        if (be instanceof team.reborn.energy.api.EnergyStorage storage) {
            return new FabricEnergyStorageCoreAdapter(storage);
        }
        return null;
    });
}
```

### Exposing to NeoForge Capabilities

To allow native NeoForge blocks to interact with these bridged capabilities, ChainLoader listens to NeoForge's `RegisterCapabilitiesEvent` and registers a block capability provider that queries the bridge:

```java
// From EventBridgeHelper.java
neoforgeBus.addListener(RegisterCapabilitiesEvent.class, event -> {
    event.registerBlock(
        Capabilities.Energy.BLOCK,
        (level, pos, state, be, side) -> {
            if (be != null) {
                ChainEnergyStorage coreEnergy = ChainCapabilityBridge.queryEnergy(level, pos, state, be, side);
                if (coreEnergy != null) {
                    return new NeoForgeEnergyStorageAdapter(coreEnergy);
                }
            }
            return null;
        }
    );
});
```

---

## Fluid and Item Handler Bridges

Items and fluids are bridged using the same adapter architecture:

### 1. Item Handler Bridge
* **Fabric to Core**: Fabric's `ItemStorage.SIDED` (emulated using a BlockApiLookup stub) retrieves item storage. The returned storage is adapted to `ChainItemHandler`.
* **Forge to Core**: Forge's `IItemHandler` is wrapped and registered in `ChainCapabilityBridge.registerItemProvider`.
* **Exposing to NeoForge**: `Capabilities.ItemHandler.BLOCK` query triggers `ChainCapabilityBridge.queryItems` and wraps it in a NeoForge item handler adapter.

### 2. Fluid Handler Bridge
* **Fabric to Core**: Fabric's `FluidStorage.SIDED` is adapted to `ChainFluidHandler`.
* **Forge to Core**: Forge's `IFluidHandler` is wrapped and registered in `ChainCapabilityBridge.registerFluidProvider`.
* **Exposing to NeoForge**: `Capabilities.FluidHandler.BLOCK` query triggers `ChainCapabilityBridge.queryFluids` and wraps it in a NeoForge fluid handler adapter.

This unified mapping structure enables bidirectional energy, fluid, and item interactions between machinery and pipes regardless of the original API they were compiled against.
