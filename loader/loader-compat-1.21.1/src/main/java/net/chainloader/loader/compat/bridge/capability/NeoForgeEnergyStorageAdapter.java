package net.chainloader.loader.compat.bridge.capability;

import net.chainloader.loader.core.capability.ChainEnergyStorage;

public class NeoForgeEnergyStorageAdapter implements net.neoforged.neoforge.energy.IEnergyStorage {
    private final ChainEnergyStorage parent;

    public NeoForgeEnergyStorageAdapter(ChainEnergyStorage parent) {
        this.parent = parent;
    }

    @Override
    public int receiveEnergy(int maxReceive, boolean simulate) {
        return (int) parent.receiveEnergy(maxReceive, simulate);
    }

    @Override
    public int extractEnergy(int maxExtract, boolean simulate) {
        return (int) parent.extractEnergy(maxExtract, simulate);
    }

    @Override
    public int getEnergyStored() {
        return (int) parent.getEnergyStored();
    }

    @Override
    public int getMaxEnergyStored() {
        return (int) parent.getMaxEnergyStored();
    }

    @Override
    public boolean canExtract() {
        return parent.canExtract();
    }

    @Override
    public boolean canReceive() {
        return parent.canReceive();
    }
}
