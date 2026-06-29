package net.fabricmc.fabric.api.entity.event.v1;

import net.fabricmc.fabric.api.event.Event;

public class ServerPlayerEvents {
    public interface CopyFrom {
        void copyFromPlayer(Object oldPlayer, Object newPlayer, boolean alive);
    }
    public interface AfterRespawn {
        void afterRespawn(Object oldPlayer, Object newPlayer, boolean alive);
    }

    public static final Event<CopyFrom> COPY_FROM = new Event<>(CopyFrom.class);
    public static final Event<AfterRespawn> AFTER_RESPAWN = new Event<>(AfterRespawn.class);
}
