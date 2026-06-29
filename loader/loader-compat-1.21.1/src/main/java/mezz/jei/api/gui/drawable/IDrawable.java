package mezz.jei.api.gui.drawable;

/**
 * Mockup of JEI's IDrawable interface.
 */
public interface IDrawable {
    int getWidth();
    int getHeight();
    void draw(Object guiGraphics, int xOffset, int yOffset);
}
