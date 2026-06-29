package net.chainloader.api.event;

/**
 * Base class for ChainLoader events that can be canceled.
 */
public abstract class CancelableEvent extends ChainEvent {
    private boolean canceled = false;

    public boolean isCanceled() {
        return canceled;
    }

    public void setCanceled(boolean canceled) {
        this.canceled = canceled;
    }
}
