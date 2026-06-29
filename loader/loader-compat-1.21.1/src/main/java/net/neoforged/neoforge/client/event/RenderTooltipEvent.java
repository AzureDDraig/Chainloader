package net.neoforged.neoforge.client.event;

import net.neoforged.bus.api.Event;
import net.minecraft.world.item.ItemStack;
import java.util.List;

public class RenderTooltipEvent extends Event {
    public static class GatherComponents extends RenderTooltipEvent {
        private final ItemStack stack;
        private final List<Object> tooltipElements;

        public GatherComponents(ItemStack stack, List<Object> tooltipElements) {
            this.stack = stack;
            this.tooltipElements = tooltipElements;
        }

        public ItemStack getItemStack() { return stack; }
        public List<Object> getTooltipElements() { return tooltipElements; }
    }
}
