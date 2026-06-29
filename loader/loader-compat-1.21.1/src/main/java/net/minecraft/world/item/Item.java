package net.minecraft.world.item;

import net.minecraft.world.level.ItemLike;

public class Item implements ItemLike {
    @Override
    public Item asItem() { return this; }

    public void appendHoverText(ItemStack stack, TooltipContext context, java.util.List<net.minecraft.network.chat.Component> tooltip, TooltipFlag flag) {
    }

    public interface TooltipContext {
        static TooltipContext of(net.minecraft.world.level.Level level) { return null; }
        TooltipContext EMPTY = null;
    }
}
