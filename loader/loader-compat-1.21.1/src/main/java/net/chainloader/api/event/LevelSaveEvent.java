package net.chainloader.api.event;

/**
 * Host event fired when a level/world is saved.
 */
public class LevelSaveEvent extends ChainEvent {
    private final Object level;

    public LevelSaveEvent(Object level) {
        this.level = level;
    }

    public Object getLevel() {
        return level;
    }
}
