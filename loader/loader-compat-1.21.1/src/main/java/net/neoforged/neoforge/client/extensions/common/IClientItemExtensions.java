package net.neoforged.neoforge.client.extensions.common;

import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;

public interface IClientItemExtensions {
    default BlockEntityWithoutLevelRenderer getCustomRenderer() {
        return null;
    }
}
