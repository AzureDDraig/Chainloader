package team.reborn.energy.api;

public interface EnergyStorage {
    long insert(long maxAmount, Object transaction);
    long extract(long maxAmount, Object transaction);
    long getAmount();
    long getCapacity();
    boolean supportsInsertion();
    boolean supportsExtraction();
}
