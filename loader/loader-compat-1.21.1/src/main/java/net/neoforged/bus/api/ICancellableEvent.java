package net.neoforged.bus.api;

public interface ICancellableEvent {
    default boolean isCancellable() {
        return false;
    }

    default boolean isCancelable() {
        return false;
    }

    boolean isCanceled();

    void setCanceled(boolean cancel);
}
