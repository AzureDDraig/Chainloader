package net.fabricmc.fabric.api.itemgroup.v1;

import net.minecraft.world.item.CreativeModeTab;

public final class FabricItemGroup {
    private static int customTabIndex = 20;

    public static CreativeModeTab.Builder builder() {
        return CreativeModeTab.builder(CreativeModeTab.Row.TOP, customTabIndex++);
    }
}
