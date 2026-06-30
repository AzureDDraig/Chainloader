package net.chainloader.loader.compat.bridge.capability;

import net.chainloader.loader.core.capability.ChainEnergyStorage;
import team.reborn.energy.api.EnergyStorage;

public class FabricEnergyStorageAdapter implements EnergyStorage {
    private final ChainEnergyStorage parent;

    public FabricEnergyStorageAdapter(ChainEnergyStorage parent) {
        this.parent = parent;
    }

    @Override
    public long insert(long maxAmount, Object transaction) {
        return parent.receiveEnergy(maxAmount, false);
    }

    @Override
    public long extract(long maxAmount, Object transaction) {
        return parent.extractEnergy(maxAmount, false);
    }

    @Override
    public long getAmount() {
        return parent.getEnergyStored();
    }

    @Override
    public long getCapacity() {
        return parent.getMaxEnergyStored();
    }

    @Override
    public boolean supportsInsertion() {
        return parent.canReceive();
    }

    @Override
    public boolean supportsExtraction() {
        return parent.canExtract();
    }
}
