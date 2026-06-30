package net.chainloader.loader.compat.bridge.render;

import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.chainloader.loader.core.render.ChainItemRenderer;

public class NeoForgeClientItemExtensions implements IClientItemExtensions {
    private final BlockEntityWithoutLevelRenderer renderer;

    public NeoForgeClientItemExtensions(ChainItemRenderer coreRenderer) {
        this.renderer = new NeoForgeItemRendererAdapter(coreRenderer);
    }

    @Override
    public BlockEntityWithoutLevelRenderer getCustomRenderer() {
        return renderer;
    }
}
