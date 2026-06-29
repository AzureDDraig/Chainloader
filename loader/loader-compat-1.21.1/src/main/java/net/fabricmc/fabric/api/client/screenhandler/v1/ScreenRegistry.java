package net.fabricmc.fabric.api.client.screenhandler.v1;

import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.screens.Screen;

/**
 * Compile-time stub for net.fabricmc.fabric.api.client.screenhandler.v1.ScreenRegistry.
 */
public final class ScreenRegistry {
    public interface Factory<H extends AbstractContainerMenu, S extends Screen> {
        S create(H handler, Inventory inventory, Component title);
    }

    public static <H extends AbstractContainerMenu, S extends Screen> void register(
        MenuType<? extends H> type,
        Factory<H, S> screenFactory
    ) {
    }
}
