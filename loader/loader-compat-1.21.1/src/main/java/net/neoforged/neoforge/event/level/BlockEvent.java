package net.neoforged.neoforge.event.level;

import net.neoforged.bus.api.Event;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.player.Player;

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
