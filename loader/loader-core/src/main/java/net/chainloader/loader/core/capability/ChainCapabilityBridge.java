package net.chainloader.loader.core.capability;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public class ChainCapabilityBridge {
    public interface Provider<T> {
        T getCapability(Object level, Object pos, Object state, Object blockEntity, Object side);
    }

    private static final Map<String, Provider<ChainEnergyStorage>> energyProviders = new ConcurrentHashMap<>();
    private static final Map<String, Provider<ChainItemHandler>> itemProviders = new ConcurrentHashMap<>();
    private static final Map<String, Provider<ChainFluidHandler>> fluidProviders = new ConcurrentHashMap<>();

    public static void registerEnergyProvider(String modId, Provider<ChainEnergyStorage> provider) {
        energyProviders.put(modId, provider);
    }

    public static void registerItemProvider(String modId, Provider<ChainItemHandler> provider) {
        itemProviders.put(modId, provider);
    }

    public static void registerFluidProvider(String modId, Provider<ChainFluidHandler> provider) {
        fluidProviders.put(modId, provider);
    }

    public static ChainEnergyStorage queryEnergy(Object level, Object pos, Object state, Object blockEntity, Object side) {
        for (Provider<ChainEnergyStorage> p : energyProviders.values()) {
            ChainEnergyStorage s = p.getCapability(level, pos, state, blockEntity, side);
            if (s != null) return s;
        }
        return null;
    }

    public static ChainItemHandler queryItems(Object level, Object pos, Object state, Object blockEntity, Object side) {
        for (Provider<ChainItemHandler> p : itemProviders.values()) {
            ChainItemHandler s = p.getCapability(level, pos, state, blockEntity, side);
            if (s != null) return s;
        }
        return null;
    }

    public static ChainFluidHandler queryFluids(Object level, Object pos, Object state, Object blockEntity, Object side) {
        for (Provider<ChainFluidHandler> p : fluidProviders.values()) {
            ChainFluidHandler s = p.getCapability(level, pos, state, blockEntity, side);
            if (s != null) return s;
        }
        return null;
    }
}
