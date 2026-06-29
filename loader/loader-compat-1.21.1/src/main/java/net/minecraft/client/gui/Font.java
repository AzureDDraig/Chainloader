package net.minecraft.client.gui;

public class Font {
    public int width(String text) {
        return text != null ? text.length() * 6 : 0;
    }
}
