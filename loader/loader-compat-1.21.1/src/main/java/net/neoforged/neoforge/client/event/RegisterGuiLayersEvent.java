package net.neoforged.neoforge.client.event;

import net.neoforged.bus.api.Event;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.gui.LayeredDraw;
import java.util.ArrayList;
import java.util.List;

public class RegisterGuiLayersEvent extends Event {
    public static class RegisteredOverlay {
        public final ResourceLocation name;
        public final LayeredDraw.Layer overlay;

        public RegisteredOverlay(ResourceLocation name, LayeredDraw.Layer overlay) {
            this.name = name;
            this.overlay = overlay;
        }
    }

    private static final List<RegisteredOverlay> OVERLAYS = new ArrayList<>();

    public static List<RegisteredOverlay> getRegisteredOverlays() {
        return OVERLAYS;
    }

    public void registerAbove(ResourceLocation existing, ResourceLocation name, LayeredDraw.Layer overlay) {
        register(name, overlay);
    }

    public void registerBelow(ResourceLocation existing, ResourceLocation name, LayeredDraw.Layer overlay) {
        register(name, overlay);
    }

    public void register(ResourceLocation name, LayeredDraw.Layer overlay) {
        if (overlay != null) {
            OVERLAYS.add(new RegisteredOverlay(name, overlay));
            System.out.println("[RegisterGuiLayersEvent] Registered GUI overlay layer: " + name);
        }
    }
}
