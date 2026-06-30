package net.minecraftforge.client.event;

import net.minecraftforge.eventbus.api.Event;

public class EntityRenderersEvent extends Event {
    public static class RegisterRenderers extends EntityRenderersEvent {
    }
    public static class AddLayers extends EntityRenderersEvent {
    }
}
