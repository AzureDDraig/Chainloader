package net.chainloader.api.client.render;

import net.chainloader.api.event.ChainEvents;

public final class ChainRenderEvents {
    public static final ChainEvents.Event<HudRender> HUD_RENDER = new ChainEvents.Event<>(HudRender.class, null);
    public static final ChainEvents.Event<TooltipModify> TOOLTIP_MODIFY = new ChainEvents.Event<>(TooltipModify.class, null);

    public interface HudRender {
        void onHudRender(Object drawContext, float tickDelta);
    }

    public interface TooltipModify {
        void onTooltipModify(Object stack, java.util.List<?> tooltipLines, Object context);
    }
}
