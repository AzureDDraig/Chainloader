package net.fabricmc.fabric.api.client.item.v1;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Item.TooltipContext;
import java.util.List;

@FunctionalInterface
public interface ItemTooltipCallback {
    Event<ItemTooltipCallback> EVENT = new Event<>(ItemTooltipCallback.class);

    void getTooltip(ItemStack stack, TooltipContext context, TooltipFlag type, List<Component> lines);
}
