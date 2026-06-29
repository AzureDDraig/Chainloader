package net.neoforged.neoforge.client.event;

import net.neoforged.bus.api.Event;

public class ContainerScreenEvent extends Event {
    public static class Render extends ContainerScreenEvent {
        public static class Foreground extends Render {
        }
    }
}
