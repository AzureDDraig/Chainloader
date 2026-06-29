package net.fabricmc.fabric.api.client.screen.v1;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.client.gui.screens.Screen;
import java.util.Map;
import java.util.WeakHashMap;

public final class ScreenKeyboardEvents {
    private static final Map<Screen, Event<AllowKeyPress>> ALLOW_KEY_PRESS = new WeakHashMap<>();
    private static final Map<Screen, Event<AfterKeyPress>> AFTER_KEY_PRESS = new WeakHashMap<>();
    private static final Map<Screen, Event<AllowKeyRelease>> ALLOW_KEY_RELEASE = new WeakHashMap<>();
    private static final Map<Screen, Event<AfterKeyRelease>> AFTER_KEY_RELEASE = new WeakHashMap<>();

    public static Event<AllowKeyPress> allowKeyPress(Screen screen) {
        synchronized (ALLOW_KEY_PRESS) {
            return ALLOW_KEY_PRESS.computeIfAbsent(screen, k -> new Event<>(AllowKeyPress.class));
        }
    }

    public static Event<AfterKeyPress> afterKeyPress(Screen screen) {
        synchronized (AFTER_KEY_PRESS) {
            return AFTER_KEY_PRESS.computeIfAbsent(screen, k -> new Event<>(AfterKeyPress.class));
        }
    }

    public static Event<AllowKeyRelease> allowKeyRelease(Screen screen) {
        synchronized (ALLOW_KEY_RELEASE) {
            return ALLOW_KEY_RELEASE.computeIfAbsent(screen, k -> new Event<>(AllowKeyRelease.class));
        }
    }

    public static Event<AfterKeyRelease> afterKeyRelease(Screen screen) {
        synchronized (AFTER_KEY_RELEASE) {
            return AFTER_KEY_RELEASE.computeIfAbsent(screen, k -> new Event<>(AfterKeyRelease.class));
        }
    }

    @FunctionalInterface
    public interface AllowKeyPress {
        boolean allowKeyPress(Screen screen, int key, int scancode, int modifiers);
    }

    @FunctionalInterface
    public interface AfterKeyPress {
        void afterKeyPress(Screen screen, int key, int scancode, int modifiers);
    }

    @FunctionalInterface
    public interface AllowKeyRelease {
        boolean allowKeyRelease(Screen screen, int key, int scancode, int modifiers);
    }

    @FunctionalInterface
    public interface AfterKeyRelease {
        void afterKeyRelease(Screen screen, int key, int scancode, int modifiers);
    }
}
