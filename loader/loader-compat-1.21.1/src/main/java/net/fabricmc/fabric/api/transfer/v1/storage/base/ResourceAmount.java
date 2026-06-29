package net.fabricmc.fabric.api.transfer.v1.storage.base;

public final class ResourceAmount<T> {
    private final T resource;
    private final long amount;

    public ResourceAmount(T resource, long amount) {
        this.resource = resource;
        this.amount = amount;
    }

    public T resource() { return resource; }
    public long amount() { return amount; }
}
