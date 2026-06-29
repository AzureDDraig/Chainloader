package net.fabricmc.fabric.api.screenhandler.v1;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.MenuProvider;

public interface ExtendedScreenHandlerFactory extends MenuProvider {
    void writeScreenOpeningData(ServerPlayer player, FriendlyByteBuf buf);
}
