package net.neoforged.neoforge.client.gui.overlay;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.DeltaTracker;

@FunctionalInterface
public interface IGuiOverlay {
    void render(GuiGraphics guiGraphics, DeltaTracker deltaTracker);
}
