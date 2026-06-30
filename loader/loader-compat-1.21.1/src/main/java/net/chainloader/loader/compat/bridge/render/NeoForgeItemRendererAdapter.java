package net.chainloader.loader.compat.bridge.render;

import net.chainloader.loader.core.render.ChainItemRenderer;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;

public class NeoForgeItemRendererAdapter extends BlockEntityWithoutLevelRenderer {
    private final ChainItemRenderer parent;

    public NeoForgeItemRendererAdapter(ChainItemRenderer parent) {
        super(net.chainloader.loader.compat.bridge.EventBridgeHelper.getBlockEntityRenderDispatcher(), 
              net.chainloader.loader.compat.bridge.EventBridgeHelper.getEntityModels());
        this.parent = parent;
    }

    @Override
    public void renderByItem(net.minecraft.world.item.ItemStack stack, net.minecraft.world.item.ItemDisplayContext context, com.mojang.blaze3d.vertex.PoseStack poseStack, net.minecraft.client.renderer.MultiBufferSource bufferSource, int light, int overlay) {
        parent.render(stack, context, poseStack, bufferSource, light, overlay);
    }
}
