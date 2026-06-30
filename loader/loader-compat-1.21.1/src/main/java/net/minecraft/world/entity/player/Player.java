package net.minecraft.world.entity.player;

public class Player {
    public net.minecraft.world.level.Level level;
    public int containerCounter;
    public java.util.OptionalInt openMenu(net.minecraft.world.MenuProvider provider) {
        return java.util.OptionalInt.empty();
    }
    public com.mojang.authlib.GameProfile getGameProfile() {
        return null;
    }
}
