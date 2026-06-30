package net.chainloader.loader.compat.bridge.capability;

import net.chainloader.loader.core.capability.ChainEnergyStorage;
import team.reborn.energy.api.EnergyStorage;

public class FabricEnergyStorageCoreAdapter implements ChainEnergyStorage {
    private final EnergyStorage parent;

    public FabricEnergyStorageCoreAdapter(EnergyStorage parent) {
        this.parent = parent;
    }

    @Override
    public long receiveEnergy(long maxReceive, boolean simulate) {
        return parent.insert(maxReceive, null);
    }

    @Override
    public long extractEnergy(long maxExtract, boolean simulate) {
        return parent.extract(maxExtract, null);
    }

    @Override
    public long getEnergyStored() {
        return parent.getAmount();
    }

    @Override
    public long getMaxEnergyStored() {
        return parent.getCapacity();
    }

    @Override
    public boolean canExtract() {
        return parent.supportsExtraction();
    }

    @Override
    public boolean canReceive() {
        return parent.supportsInsertion();
    }
}
