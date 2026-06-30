package net.minecraft.server.level;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;

public class ServerLevel extends Level implements LevelAccessor {
    public MinecraftServer getServer() {
        return null;
    }
}
