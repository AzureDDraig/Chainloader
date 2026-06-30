package net.minecraftforge.entity;

public interface IEntityAdditionalSpawnData {
    void writeSpawnData(net.minecraft.network.FriendlyByteBuf buffer);
    void readSpawnData(net.minecraft.network.FriendlyByteBuf buffer);
}
