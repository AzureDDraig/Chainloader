package net.chainloader.loader.compat.bridge.capability;

import net.chainloader.loader.core.capability.ChainEnergyStorage;
import net.minecraftforge.energy.IEnergyStorage;

public class CoreEnergyStorageAdapter implements ChainEnergyStorage {
    private final IEnergyStorage parent;

    public CoreEnergyStorageAdapter(IEnergyStorage parent) {
        this.parent = parent;
    }

    @Override
    public long receiveEnergy(long maxReceive, boolean simulate) {
        return parent.receiveEnergy((int) maxReceive, simulate);
    }

    @Override
    public long extractEnergy(long maxExtract, boolean simulate) {
        return parent.extractEnergy((int) maxExtract, simulate);
    }

    @Override
    public long getEnergyStored() {
        return parent.getEnergyStored();
    }

    @Override
    public long getMaxEnergyStored() {
        return parent.getMaxEnergyStored();
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
