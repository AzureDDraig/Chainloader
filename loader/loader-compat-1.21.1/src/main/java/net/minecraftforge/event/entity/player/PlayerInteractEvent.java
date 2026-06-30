package net.minecraftforge.event.entity.player;

import net.minecraftforge.eventbus.api.Event;

public class PlayerInteractEvent extends Event {
    public static class RightClickBlock extends PlayerInteractEvent {
    }
    public static class LeftClickBlock extends PlayerInteractEvent {
    }
    public static class RightClickItem extends PlayerInteractEvent {
    }
    public static class EntityInteractSpecific extends PlayerInteractEvent {
    }
    public static class LeftClickEmpty extends PlayerInteractEvent {
    }
}
