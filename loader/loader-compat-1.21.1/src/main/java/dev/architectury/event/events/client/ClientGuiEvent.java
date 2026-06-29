package dev.architectury.event.events.client;

import dev.architectury.event.Event;

public class ClientGuiEvent {
    public interface RenderHud {
        void renderHud(net.minecraft.client.gui.GuiGraphics graphics, float tickDelta);
    }

    public static Event<RenderHud> RENDER_HUD = new Event<>() {
        @Override
        public void register(RenderHud listener) {}
    };
}
