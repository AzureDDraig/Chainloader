package net.minecraft.client.renderer;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import java.util.Map;
import java.util.HashMap;

public class ItemBlockRenderTypes {
    public static final Map<Block, RenderType> TYPE_BY_BLOCK = new HashMap<>();
    public static final Map<Fluid, RenderType> TYPE_BY_FLUID = new HashMap<>();
}
