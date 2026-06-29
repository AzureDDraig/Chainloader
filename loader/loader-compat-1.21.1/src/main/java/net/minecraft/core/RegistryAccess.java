package net.minecraft.core;

/**
 * Compile-time stub for net.minecraft.core.RegistryAccess.
 */
public interface RegistryAccess extends HolderLookup.Provider {
    Frozen EMPTY = null;

    interface Frozen extends RegistryAccess {
    }
}
