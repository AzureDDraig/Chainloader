package net.minecraftforge.event.entity.player;

import net.minecraftforge.eventbus.api.Event;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;

public class PlayerEvent extends Event {
    private final Player player;

    public PlayerEvent(Player player) {
        this.player = player;
    }

    public Player getEntity() {
        return player;
    }

    public static class PlayerLoggedInEvent extends PlayerEvent {
        public PlayerLoggedInEvent(Player player) {
            super(player);
        }
    }

    public static class PlayerLoggedOutEvent extends PlayerEvent {
        public PlayerLoggedOutEvent(Player player) {
            super(player);
        }
    }

    public static class StartTracking extends PlayerEvent {
        public StartTracking(Player player) {
            super(player);
        }
    }

    public static class BreakSpeed extends PlayerEvent {
        private final BlockState state;
        private final float originalSpeed;
        private float newSpeed;
        private final BlockPos pos;

        public BreakSpeed(Player player, BlockState state, float originalSpeed, BlockPos pos) {
            super(player);
            this.state = state;
            this.originalSpeed = originalSpeed;
            this.newSpeed = originalSpeed;
            this.pos = pos;
        }

        public BlockState getState() {
            return state;
        }

        public float getOriginalSpeed() {
            return originalSpeed;
        }

        public float getNewSpeed() {
            return newSpeed;
        }

        public void setNewSpeed(float newSpeed) {
            this.newSpeed = newSpeed;
        }

        public BlockPos getPos() {
            return pos;
        }
    }

    public static class HarvestCheck extends PlayerEvent {
        private final BlockState state;
        private boolean success;

        public HarvestCheck(Player player, BlockState state, boolean success) {
            super(player);
            this.state = state;
            this.success = success;
        }

        public BlockState getState() {
            return state;
        }

        public boolean canHarvest() {
            return success;
        }

        public void setCanHarvest(boolean success) {
            this.success = success;
        }
    }

    public static class Clone extends PlayerEvent {
        private final Player original;
        private final boolean wasDeath;

        public Clone(Player player, Player original, boolean wasDeath) {
            super(player);
            this.original = original;
            this.wasDeath = wasDeath;
        }

        public Player getOriginal() {
            return original;
        }

        public boolean isWasDeath() {
            return wasDeath;
        }
    }
}
