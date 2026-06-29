package net.fabricmc.fabric.api.transfer.v1.item;

import net.fabricmc.fabric.api.lookup.v1.block.BlockApiLookup;
import net.minecraft.core.Direction;

public final class ItemStorage {
    public static final BlockApiLookup<Object, Direction> SIDED = BlockApiLookup.get(
        new net.minecraft.resources.ResourceLocation("fabric", "sided_item_storage"),
        Object.class,
        Direction.class
    );
}
