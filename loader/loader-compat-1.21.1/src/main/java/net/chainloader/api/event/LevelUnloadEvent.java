package net.chainloader.api.event;

/**
 * Host event fired when a level/world is unloaded.
 */
public class LevelUnloadEvent extends ChainEvent {
    private final Object level;

    public LevelUnloadEvent(Object level) {
        this.level = level;
    }

    public Object getLevel() {
        return level;
    }
}
