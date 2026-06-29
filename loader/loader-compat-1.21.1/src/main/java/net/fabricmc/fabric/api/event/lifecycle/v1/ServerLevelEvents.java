package net.fabricmc.fabric.api.event.lifecycle.v1;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

public final class ServerLevelEvents {
    public static final Event<Load> LOAD = new Event<>(Load.class);
    public static final Event<Unload> UNLOAD = new Event<>(Unload.class);
    public static final Event<Save> SAVE = new Event<>(Save.class);

    @FunctionalInterface
    public interface Load {
        void onLevelLoad(MinecraftServer server, ServerLevel level);
    }

    @FunctionalInterface
    public interface Unload {
        void onLevelUnload(MinecraftServer server, ServerLevel level);
    }

    @FunctionalInterface
    public interface Save {
        void onLevelSave(MinecraftServer server, ServerLevel level);
    }
}
