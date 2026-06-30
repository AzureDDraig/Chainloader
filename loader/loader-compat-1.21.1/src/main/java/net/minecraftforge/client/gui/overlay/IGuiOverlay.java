package net.minecraftforge.client.gui.overlay;

import net.minecraft.client.gui.GuiGraphics;

@FunctionalInterface
public interface IGuiOverlay {
    void render(Object gui, GuiGraphics guiGraphics, float partialTick, int width, int height);
}
