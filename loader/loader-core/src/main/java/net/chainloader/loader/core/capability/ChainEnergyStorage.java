package net.chainloader.loader.core.capability;

public interface ChainEnergyStorage {
    long receiveEnergy(long maxReceive, boolean simulate);
    long extractEnergy(long maxExtract, boolean simulate);
    long getEnergyStored();
    long getMaxEnergyStored();
    boolean canExtract();
    boolean canReceive();
}
