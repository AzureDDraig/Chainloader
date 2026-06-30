package net.minecraftforge.common.util;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import com.mojang.authlib.GameProfile;

public class FakePlayer extends ServerPlayer {
    public FakePlayer(ServerLevel level, GameProfile profile) {
    }
}
