package net.chainloader.api.event;

/**
 * Host event fired when the server has started.
 */
public class ServerStartedEvent extends ChainEvent {
    private final Object server;

    public ServerStartedEvent(Object server) {
        this.server = server;
    }

    public Object getServer() {
        return server;
    }
}
