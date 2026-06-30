package dev.architectury.event.events.client;

import dev.architectury.event.Event;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class ClientGuiEvent {
    public interface RenderHud {
        void renderHud(net.minecraft.client.gui.GuiGraphics graphics, float tickDelta);
    }

    private static final List<RenderHud> LISTENERS = new CopyOnWriteArrayList<>();

    public static Event<RenderHud> RENDER_HUD = new Event<>() {
        @Override
        public void register(RenderHud listener) {
            if (listener != null) {
                System.out.println("[Architectury Stub] Registered RenderHud listener: " + listener.getClass().getName());
                LISTENERS.add(listener);
            }
        }
    };

    public static List<RenderHud> getListeners() {
        return LISTENERS;
    }
}

