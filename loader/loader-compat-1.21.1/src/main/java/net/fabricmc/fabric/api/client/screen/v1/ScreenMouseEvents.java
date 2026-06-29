package net.fabricmc.fabric.api.client.screen.v1;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.client.gui.screens.Screen;
import java.util.Map;
import java.util.WeakHashMap;

public final class ScreenMouseEvents {
    private static final Map<Screen, Event<AllowMouseClick>> ALLOW_MOUSE_CLICK = new WeakHashMap<>();
    private static final Map<Screen, Event<AfterMouseClick>> AFTER_MOUSE_CLICK = new WeakHashMap<>();
    private static final Map<Screen, Event<AllowMouseRelease>> ALLOW_MOUSE_RELEASE = new WeakHashMap<>();
    private static final Map<Screen, Event<AfterMouseRelease>> AFTER_MOUSE_RELEASE = new WeakHashMap<>();

    public static Event<AllowMouseClick> allowMouseClick(Screen screen) {
        synchronized (ALLOW_MOUSE_CLICK) {
            return ALLOW_MOUSE_CLICK.computeIfAbsent(screen, k -> new Event<>(AllowMouseClick.class));
        }
    }

    public static Event<AfterMouseClick> afterMouseClick(Screen screen) {
        synchronized (AFTER_MOUSE_CLICK) {
            return AFTER_MOUSE_CLICK.computeIfAbsent(screen, k -> new Event<>(AfterMouseClick.class));
        }
    }

    public static Event<AllowMouseRelease> allowMouseRelease(Screen screen) {
        synchronized (ALLOW_MOUSE_RELEASE) {
            return ALLOW_MOUSE_RELEASE.computeIfAbsent(screen, k -> new Event<>(AllowMouseRelease.class));
        }
    }

    public static Event<AfterMouseRelease> afterMouseRelease(Screen screen) {
        synchronized (AFTER_MOUSE_RELEASE) {
            return AFTER_MOUSE_RELEASE.computeIfAbsent(screen, k -> new Event<>(AfterMouseRelease.class));
        }
    }

    @FunctionalInterface
    public interface AllowMouseClick {
        boolean allowMouseClick(Screen screen, double mouseX, double mouseY, int button);
    }

    @FunctionalInterface
    public interface AfterMouseClick {
        void afterMouseClick(Screen screen, double mouseX, double mouseY, int button);
    }

    @FunctionalInterface
    public interface AllowMouseRelease {
        boolean allowMouseRelease(Screen screen, double mouseX, double mouseY, int button);
    }

    @FunctionalInterface
    public interface AfterMouseRelease {
        void afterMouseRelease(Screen screen, double mouseX, double mouseY, int button);
    }
}
