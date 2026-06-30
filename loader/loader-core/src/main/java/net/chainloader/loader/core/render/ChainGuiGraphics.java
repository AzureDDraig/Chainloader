package net.chainloader.loader.core.render;

public interface ChainGuiGraphics {
    void drawString(String text, float x, float y, int color, boolean shadow);
    void fill(int minX, int minY, int maxX, int maxY, int color);
    void drawTexture(String namespace, String path, int x, int y, int u, int v, int width, int height);
}
