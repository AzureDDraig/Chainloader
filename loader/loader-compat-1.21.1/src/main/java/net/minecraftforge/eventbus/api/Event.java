package net.minecraftforge.eventbus.api;

/**
 * Mockup of the base Forge/NeoForge Event class.
 */
public class Event {
    private Result result = Result.DEFAULT;

    public enum Result {
        DENY,
        DEFAULT,
        ALLOW
    }

    public boolean isCancelable() {
        return getClass().isAnnotationPresent(Cancelable.class);
    }

    private boolean canceled = false;

    public boolean isCanceled() {
        return canceled;
    }

    public void setCanceled(boolean cancel) {
        if (!isCancelable()) {
            throw new UnsupportedOperationException("Attempted to call setCanceled() on a non-cancelable event of type: " + getClass().getName());
        }
        this.canceled = cancel;
    }

    public Result getResult() {
        return result;
    }

    public void setResult(Result result) {
        this.result = result;
    }

    public boolean hasResult() {
        return getClass().isAnnotationPresent(HasResult.class);
    }
}
