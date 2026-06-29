package net.minecraft.network.protocol.game;

import net.minecraft.world.level.block.entity.BlockEntity;
import java.util.function.BiFunction;

public class ClientboundBlockEntityDataPacket {
    public static ClientboundBlockEntityDataPacket create(
            BlockEntity blockEntity,
            BiFunction<BlockEntity, ?, net.minecraft.nbt.CompoundTag> function) {
        return null;
    }
}
