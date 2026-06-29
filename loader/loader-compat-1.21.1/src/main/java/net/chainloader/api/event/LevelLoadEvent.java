package net.chainloader.api.event;

/**
 * Host event fired when a level/world is loaded.
 */
public class LevelLoadEvent extends ChainEvent {
    private final Object level;

    public LevelLoadEvent(Object level) {
        this.level = level;
    }

    public Object getLevel() {
        return level;
    }
}
