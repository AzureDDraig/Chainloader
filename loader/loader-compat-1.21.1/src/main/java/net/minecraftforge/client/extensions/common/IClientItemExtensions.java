package net.minecraftforge.client.extensions.common;

import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;

public interface IClientItemExtensions {
    public static final IClientItemExtensions DEFAULT = new IClientItemExtensions() {};

    default BlockEntityWithoutLevelRenderer getCustomRenderer() {
        return null;
    }
}
