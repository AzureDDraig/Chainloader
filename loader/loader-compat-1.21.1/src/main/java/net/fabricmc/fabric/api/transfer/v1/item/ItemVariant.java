package net.fabricmc.fabric.api.transfer.v1.item;

import net.minecraft.world.item.Item;

public interface ItemVariant {
    Item getItem();
    static ItemVariant of(Item item) {
        return new ItemVariant() {
            @Override
            public Item getItem() { return item; }
        };
    }
}
