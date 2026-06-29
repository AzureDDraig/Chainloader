package net.fabricmc.fabric.api.client.screen.v1;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.GuiGraphics;
import java.util.Map;
import java.util.WeakHashMap;

public final class ScreenEvents {
    public static final Event<BeforeInit> BEFORE_INIT = new Event<>(BeforeInit.class);
    public static final Event<AfterInit> AFTER_INIT = new Event<>(AfterInit.class);

    private static final Map<Screen, Event<BeforeRender>> BEFORE_RENDER_EVENTS = new WeakHashMap<>();
    private static final Map<Screen, Event<AfterRender>> AFTER_RENDER_EVENTS = new WeakHashMap<>();

    public static Event<BeforeRender> beforeRender(Screen screen) {
        synchronized (BEFORE_RENDER_EVENTS) {
            return BEFORE_RENDER_EVENTS.computeIfAbsent(screen, k -> new Event<>(BeforeRender.class));
        }
    }

    public static Event<AfterRender> afterRender(Screen screen) {
        synchronized (AFTER_RENDER_EVENTS) {
            return AFTER_RENDER_EVENTS.computeIfAbsent(screen, k -> new Event<>(AfterRender.class));
        }
    }

    @FunctionalInterface
    public interface BeforeInit {
        void beforeInit(Minecraft client, Screen screen, int scaledWidth, int scaledHeight);
    }

    @FunctionalInterface
    public interface AfterInit {
        void afterInit(Minecraft client, Screen screen, int scaledWidth, int scaledHeight);
    }

    @FunctionalInterface
    public interface BeforeRender {
        void beforeRender(Screen screen, GuiGraphics graphics, int mouseX, int mouseY, float tickDelta);
    }

    @FunctionalInterface
    public interface AfterRender {
        void afterRender(Screen screen, GuiGraphics graphics, int mouseX, int mouseY, float tickDelta);
    }
}
