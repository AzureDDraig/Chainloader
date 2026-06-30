package net.minecraft.world.inventory;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.flag.FeatureFlagSet;

public class MenuType<T extends AbstractContainerMenu> {
    public interface MenuSupplier<T extends AbstractContainerMenu> {
        T create(int syncId, Inventory inventory);
    }

    public MenuType(MenuSupplier<T> supplier, FeatureFlagSet features) {}

    public T create(int syncId, Inventory inventory) {
        return null;
    }
}
