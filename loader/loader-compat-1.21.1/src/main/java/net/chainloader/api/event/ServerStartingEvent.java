package net.chainloader.api.event;

/**
 * Host event fired when the server is starting.
 */
public class ServerStartingEvent extends ChainEvent {
    private final Object server;

    public ServerStartingEvent(Object server) {
        this.server = server;
    }

    public Object getServer() {
        return server;
    }
}
