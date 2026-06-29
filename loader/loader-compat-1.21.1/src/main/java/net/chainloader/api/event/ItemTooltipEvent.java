package net.chainloader.api.event;

import java.util.List;

/**
 * Host event fired when item tooltips are gathered.
 */
public class ItemTooltipEvent extends ChainEvent {
    private final Object itemStack;
    private final Object player;
    private final List<?> toolTip;
    private final Object flags;

    public ItemTooltipEvent(Object itemStack, Object player, List<?> toolTip, Object flags) {
        this.itemStack = itemStack;
        this.player = player;
        this.toolTip = toolTip;
        this.flags = flags;
    }

    public Object getItemStack() {
        return itemStack;
    }

    public Object getPlayer() {
        return player;
    }

    public List<?> getToolTip() {
        return toolTip;
    }

    public Object getFlags() {
        return flags;
    }
}
