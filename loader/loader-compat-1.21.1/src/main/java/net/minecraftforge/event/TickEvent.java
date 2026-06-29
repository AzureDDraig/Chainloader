package net.minecraftforge.event;

import net.minecraftforge.eventbus.api.Event;

public class TickEvent extends Event {
    public enum Phase {
        START,
        END
    }

    public enum Type {
        CLIENT,
        SERVER,
        RENDER,
        WORLD,
        PLAYER
    }

    public final Type type;
    public final Phase phase;

    public TickEvent(Type type, Phase phase) {
        this.type = type;
        this.phase = phase;
    }

    public static class ClientTickEvent extends TickEvent {
        public ClientTickEvent(Phase phase) {
            super(Type.CLIENT, phase);
        }
    }

    public static class ServerTickEvent extends TickEvent {
        public ServerTickEvent(Phase phase) {
            super(Type.SERVER, phase);
        }
    }

    public static class PlayerTickEvent extends TickEvent {
        public final net.minecraft.world.entity.player.Player player;
        public PlayerTickEvent(Phase phase, net.minecraft.world.entity.player.Player player) {
            super(Type.PLAYER, phase);
            this.player = player;
        }
        public net.minecraft.world.entity.player.Player getPlayer() { return player; }
    }

    public static class WorldTickEvent extends TickEvent {
        public final net.minecraft.world.level.Level world;
        public WorldTickEvent(Phase phase, net.minecraft.world.level.Level world) {
            super(Type.WORLD, phase);
            this.world = world;
        }
        public net.minecraft.world.level.Level getLevel() { return world; }
    }

    public static class LevelTickEvent extends TickEvent {
        public final net.minecraft.world.level.Level level;
        public LevelTickEvent(Phase phase, net.minecraft.world.level.Level level) {
            super(Type.WORLD, phase);
            this.level = level;
        }
        public net.minecraft.world.level.Level getLevel() { return level; }
    }

    public static class RenderTickEvent extends TickEvent {
        public RenderTickEvent(Phase phase) {
            super(Type.RENDER, phase);
        }
    }
}
