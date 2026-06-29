package net.fabricmc.fabric.api.transfer.v1.item;

import net.minecraft.world.Container;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;

public interface InventoryStorage extends Storage<ItemVariant> {
    static Storage<ItemVariant> of(Container inventory, net.minecraft.core.Direction direction) {
        return new Storage<>() {};
    }
}
