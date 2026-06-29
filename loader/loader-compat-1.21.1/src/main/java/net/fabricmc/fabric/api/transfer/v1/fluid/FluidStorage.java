package net.fabricmc.fabric.api.transfer.v1.fluid;

import net.fabricmc.fabric.api.lookup.v1.block.BlockApiLookup;
import net.minecraft.core.Direction;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;

public final class FluidStorage {
    @SuppressWarnings("unchecked")
    public static final BlockApiLookup<Storage<FluidVariant>, Direction> SIDED = BlockApiLookup.get(
        new net.minecraft.resources.ResourceLocation("fabric", "sided_fluid_storage"),
        (Class<Storage<FluidVariant>>) (Class<?>) Storage.class,
        Direction.class
    );
}
