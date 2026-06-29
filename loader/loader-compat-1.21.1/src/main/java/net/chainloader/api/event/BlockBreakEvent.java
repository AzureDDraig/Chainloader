package net.chainloader.api.event;

/**
 * Host event fired when a block is broken.
 */
public class BlockBreakEvent extends CancelableEvent {
    private final Object world;
    private final Object pos;
    private final Object state;
    private final Object player;
    private int xpToDrop;

    public BlockBreakEvent(Object world, Object pos, Object state, Object player) {
        this.world = world;
        this.pos = pos;
        this.state = state;
        this.player = player;
    }

    public Object getWorld() {
        return world;
    }

    public Object getPos() {
        return pos;
    }

    public Object getState() {
        return state;
    }

    public Object getPlayer() {
        return player;
    }

    public int getXpToDrop() {
        return xpToDrop;
    }

    public void setXpToDrop(int xpToDrop) {
        this.xpToDrop = xpToDrop;
    }
}
