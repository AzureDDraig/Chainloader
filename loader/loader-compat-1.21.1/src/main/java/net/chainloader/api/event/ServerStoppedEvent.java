package net.chainloader.api.event;

/**
 * Host event fired when the server has stopped.
 */
public class ServerStoppedEvent extends ChainEvent {
    private final Object server;

    public ServerStoppedEvent(Object server) {
        this.server = server;
    }

    public Object getServer() {
        return server;
    }
}
