package net.chainloader.loader.core.render;

public interface ChainItemRenderer {
    void render(Object itemStack, Object displayContext, Object poseStack, Object bufferSource, int light, int overlay);
}
