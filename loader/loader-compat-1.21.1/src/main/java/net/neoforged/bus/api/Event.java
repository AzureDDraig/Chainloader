package net.neoforged.bus.api;

public class Event implements ICancellableEvent {
    private Result result = Result.DEFAULT;

    public enum Result {
        DENY,
        DEFAULT,
        ALLOW
    }

    public boolean isCancellable() {
        return false;
    }

    public boolean isCancelable() {
        return false;
    }

    private boolean canceled = false;

    @Override
    public boolean isCanceled() {
        return canceled;
    }

    @Override
    public void setCanceled(boolean cancel) {
        this.canceled = cancel;
    }

    public Result getResult() {
        return result;
    }

    public void setResult(Result result) {
        this.result = result;
    }

    public boolean hasResult() {
        return false;
    }
}
