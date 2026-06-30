package net.minecraft.world.item;

import net.minecraft.world.level.ItemLike;

public class Item implements ItemLike {
    public static final java.util.Map<net.minecraft.world.level.block.Block, Item> BY_BLOCK = new java.util.HashMap<>();

    public static class Properties {}

    @Override
    public Item asItem() { return this; }

    public void appendHoverText(ItemStack stack, TooltipContext context, java.util.List<net.minecraft.network.chat.Component> tooltip, TooltipFlag flag) {
    }

    public interface TooltipContext {
        static TooltipContext of(net.minecraft.world.level.Level level) { return null; }
        TooltipContext EMPTY = null;
    }
}
