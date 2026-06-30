package net.minecraft.client.gui;

public class Font {
    public enum DisplayMode {
        NORMAL,
        SEE_THROUGH,
        POLYGON_OFFSET
    }

    public int width(String text) {
        return text != null ? text.length() * 6 : 0;
    }

    public int drawInBatch(net.minecraft.util.FormattedCharSequence text, float x, float y, int color, boolean dropShadow, org.joml.Matrix4f matrix, net.minecraft.client.renderer.MultiBufferSource buffer, DisplayMode mode, int backgroundColor, int packedLight) {
        return 0;
    }

    public int drawInBatch(net.minecraft.network.chat.Component text, float x, float y, int color, boolean dropShadow, org.joml.Matrix4f matrix, net.minecraft.client.renderer.MultiBufferSource buffer, DisplayMode mode, int backgroundColor, int packedLight) {
        return 0;
    }
}
