package net.minecraftforge.event.level;

import net.minecraftforge.eventbus.api.Cancelable;
import net.minecraftforge.eventbus.api.Event;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.player.Player;

/**
 * Mockup of the Forge BlockEvent hierarchy.
 */
public class BlockEvent extends Event {
    private final LevelAccessor level;
    private final BlockPos pos;
    private final BlockState state;

    public BlockEvent(LevelAccessor level, BlockPos pos, BlockState state) {
        this.level = level;
        this.pos = pos;
        this.state = state;
    }

    public LevelAccessor getLevel() {
        return level;
    }

    public BlockPos getPos() {
        return pos;
    }

    public BlockState getState() {
        return state;
    }

    // getWorld() bridge method for 1.16/1.17 compatibility (where getLevel() was getWorld())
    public LevelAccessor getWorld() {
        return level;
    }

    @Cancelable
    public static class BreakEvent extends BlockEvent {
        private final Player player;
        private int expToDrop;

        public BreakEvent(LevelAccessor level, BlockPos pos, BlockState state, Player player) {
            super(level, pos, state);
            this.player = player;
        }

        public Player getPlayer() {
            return player;
        }

        public int getExpToDrop() {
            return expToDrop;
        }

        public void setExpToDrop(int expToDrop) {
            this.expToDrop = expToDrop;
        }
    }
}
