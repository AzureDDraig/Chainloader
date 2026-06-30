package net.minecraftforge.event.world;

import net.minecraftforge.eventbus.api.Event;

public class ChunkEvent extends Event {
    public static class Load extends ChunkEvent {
    }
    public static class Unload extends ChunkEvent {
    }
}
