package net.chainloader.api.event;

/**
 * Host event fired when registries are synchronizing or staging.
 */
public class RegistrySyncEvent extends ChainEvent {
    private final String registryId;

    public RegistrySyncEvent(String registryId) {
        this.registryId = registryId;
    }

    public String getRegistryId() {
        return registryId;
    }
}
