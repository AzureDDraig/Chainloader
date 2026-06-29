package net.chainloader.api.event;

/**
 * Host event fired when the server is stopping.
 */
public class ServerStoppingEvent extends ChainEvent {
    private final Object server;

    public ServerStoppingEvent(Object server) {
        this.server = server;
    }

    public Object getServer() {
        return server;
    }
}
