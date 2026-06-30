package net.neoforged.neoforge.client.event;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.neoforged.bus.api.Event;

public class ContainerScreenEvent extends Event {
    private final AbstractContainerScreen<?> containerScreen;

    public ContainerScreenEvent(AbstractContainerScreen<?> containerScreen) {
        this.containerScreen = containerScreen;
    }

    public AbstractContainerScreen<?> getContainerScreen() {
        return this.containerScreen;
    }

    public static class Render extends ContainerScreenEvent {
        private final GuiGraphics guiGraphics;
        private final int mouseX;
        private final int mouseY;

        public Render(AbstractContainerScreen<?> containerScreen, GuiGraphics guiGraphics, int mouseX, int mouseY) {
            super(containerScreen);
            this.guiGraphics = guiGraphics;
            this.mouseX = mouseX;
            this.mouseY = mouseY;
        }

        public GuiGraphics getGuiGraphics() {
            return this.guiGraphics;
        }

        public int getMouseX() {
            return this.mouseX;
        }

        public int getMouseY() {
            return this.mouseY;
        }

        public static class Foreground extends Render {
            public Foreground(AbstractContainerScreen<?> containerScreen, GuiGraphics guiGraphics, int mouseX, int mouseY) {
                super(containerScreen, guiGraphics, mouseX, mouseY);
            }
        }
    }
}

