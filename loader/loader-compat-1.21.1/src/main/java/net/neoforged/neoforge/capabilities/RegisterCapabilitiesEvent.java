package net.neoforged.neoforge.capabilities;

public class RegisterCapabilitiesEvent {
    public interface BlockCapability<T, C> {}

    public <T, C> void registerBlock(BlockCapability<T, C> capability, BlockCapabilityProvider<T, C> provider, Object... blocks) {}

    @FunctionalInterface
    public interface BlockCapabilityProvider<T, C> {
        T getCapability(Object level, Object pos, Object state, Object blockEntity, C context);
    }
}
