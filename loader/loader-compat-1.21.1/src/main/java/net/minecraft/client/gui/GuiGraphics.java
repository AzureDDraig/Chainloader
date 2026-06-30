package net.minecraft.client.gui;

import net.minecraft.network.chat.Component;

public class GuiGraphics {
    public int drawString(Font font, String text, int x, int y, int color) {
        return 0;
    }

    public int drawString(Font font, String text, int x, int y, int color, boolean dropShadow) {
        return 0;
    }

    public int drawString(Font font, Component text, int x, int y, int color, boolean dropShadow) {
        return 0;
    }

    public void fill(int minX, int minY, int maxX, int maxY, int color) {
    }

    public void blit(net.minecraft.resources.ResourceLocation texture, int x, int y, int u, int v, int width, int height) {
    }

    public com.mojang.blaze3d.vertex.PoseStack pose() {
        return null;
    }
}
