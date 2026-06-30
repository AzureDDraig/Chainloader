package net.minecraft.client.gui.screens.inventory;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public abstract class AbstractContainerScreen<T> extends Screen {
    protected AbstractContainerScreen(Component title) {
        super(title);
    }
}
