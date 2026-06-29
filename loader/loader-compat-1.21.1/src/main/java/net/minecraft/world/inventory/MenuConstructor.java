package net.minecraft.world.inventory;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;

public interface MenuConstructor {
    AbstractContainerMenu createMenu(int syncId, Inventory inventory, Player player);
}
