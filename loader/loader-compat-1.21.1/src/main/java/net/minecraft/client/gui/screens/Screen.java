package net.minecraft.client.gui.screens;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

public class Screen {
    protected final net.minecraft.network.chat.Component title;
    public Font font = new Font();
    public int width;
    public int height;
    public java.util.List renderables = new java.util.ArrayList();
    public java.util.List children = new java.util.ArrayList();
    public net.minecraft.client.Minecraft minecraft;

    public net.minecraft.client.Minecraft getMinecraft() {
        return minecraft;
    }

    public Screen(net.minecraft.network.chat.Component title) {
        this.title = title;
    }

    public Object getTitle() {
        return title;
    }

    public void init() {
    }

    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
    }

    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
    }

    public <T extends net.minecraft.client.gui.components.events.GuiEventListener> T addRenderableWidget(T widget) {
        return widget;
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }
}
