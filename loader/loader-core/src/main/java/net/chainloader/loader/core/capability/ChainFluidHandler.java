package net.chainloader.loader.core.capability;

public interface ChainFluidHandler {
    int getTanks();
    Object getFluidInTank(int tank);
    int getTankCapacity(int tank);
    boolean isFluidValid(int tank, Object stack);
    int fill(Object resource, boolean simulate);
    Object drain(Object resource, boolean simulate);
    Object drain(int maxDrain, boolean simulate);
}
