package net.minecraftforge.event;

import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.registries.IForgeRegistry;

public class RegistryEvent<T> extends Event {
    private final IForgeRegistry<T> registry;

    public RegistryEvent(IForgeRegistry<T> registry) {
        this.registry = registry;
    }

    public IForgeRegistry<T> getRegistry() {
        return registry;
    }

    public static class Register<T> extends RegistryEvent<T> {
        public Register(IForgeRegistry<T> registry) {
            super(registry);
        }
    }
}
