package net.chainloader.api.event;

import net.minecraft.util.ActionResult;

public interface PlayerBlockBreakCallback {
    ChainEvents.Event<PlayerBlockBreakCallback> EVENT = new ChainEvents.Event<>(PlayerBlockBreakCallback.class, null);

    ActionResult beforeBlockBreak(Object player, Object world, Object pos, Object state, Object blockEntity);
}
