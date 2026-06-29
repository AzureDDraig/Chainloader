package net.neoforged.neoforge.client.event;

import net.neoforged.bus.api.Event;
import net.minecraft.server.packs.resources.PreparableReloadListener;

public class RegisterClientReloadListenersEvent extends Event {
    public void registerReloadListener(PreparableReloadListener listener) {}
}
