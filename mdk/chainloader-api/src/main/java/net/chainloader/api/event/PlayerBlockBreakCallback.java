package net.chainloader.api.event;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Callback for block breaking by a player.
 * Called before a block is broken by a player.
 */
@FunctionalInterface
public interface PlayerBlockBreakCallback {

    /**
     * Event instance to register and invoke listeners.
     */
    ChainEvents.Event<PlayerBlockBreakCallback> EVENT = new ChainEvents.Event<>(PlayerBlockBreakCallback.class, (listeners) -> (player, world, pos, state, blockEntity) -> {
        for (PlayerBlockBreakCallback listener : listeners) {
            ActionResult result = listener.beforeBlockBreak(player, world, pos, state, blockEntity);
            if (result != ActionResult.PASS) {
                return result;
            }
        }
        return ActionResult.PASS;
    });

    /**
     * Helper hook to trigger the event.
     *
     * @param player      the player breaking the block
     * @param world       the world where the block is broken
     * @param pos         the position of the block
     * @param state       the state of the block
     * @param blockEntity the block entity associated with the block, or null
     * @return the action result of the callback execution
     */
    static ActionResult before(PlayerEntity player, World world, BlockPos pos, BlockState state, BlockEntity blockEntity) {
        return EVENT.invoker().beforeBlockBreak(player, world, pos, state, blockEntity);
    }

    /**
     * Called before a block is broken.
     *
     * @param player      the player breaking the block
     * @param world       the world where the block is broken
     * @param pos         the position of the block
     * @param state       the state of the block
     * @param blockEntity the block entity associated with the block, or null
     * @return the action result. SUCCESS/CONSUME/FAIL can cancel the block breaking or modify default behavior.
     */
    ActionResult beforeBlockBreak(PlayerEntity player, World world, BlockPos pos, BlockState state, BlockEntity blockEntity);
}
