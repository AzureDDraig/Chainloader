package net.chainloader.loader.compat.bridge;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

public class SoftwareRenderHelper {
    private static BufferedImage image;
    private static Graphics2D g2d;

    public static void init(int width, int height) {
        image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        g2d = image.createGraphics();
        g2d.setColor(new Color(16, 16, 16, 255));
        g2d.fillRect(0, 0, width, height);
    }

    public static void fill(int minX, int minY, int maxX, int maxY, int color) {
        if (g2d == null) return;
        g2d.setColor(new Color((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, (color >> 24) & 0xFF));
        g2d.fillRect(minX, minY, maxX - minX, maxY - minY);
    }

    public static void drawString(String text, int x, int y, int color) {
        if (g2d == null) return;
        g2d.setColor(new Color((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, (color >> 24) & 0xFF));
        g2d.drawString(text, x, y + 10);
    }

    public static void blit(String texturePath, int x, int y, int width, int height) {
        if (g2d == null) return;
        g2d.setColor(new Color(80, 80, 200, 255));
        g2d.drawRect(x, y, width, height);
        g2d.drawString(texturePath, x + 2, y + 12);
    }

    public static void save(String path) {
        if (image == null) return;
        try {
            File file = new File(path);
            file.getParentFile().mkdirs();
            ImageIO.write(image, "PNG", file);
            System.out.println("[SoftwareRenderHelper] Saved screen render to: " + file.getAbsolutePath());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
