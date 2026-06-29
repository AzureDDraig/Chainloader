package net.minecraftforge.fml.javafmlmod;

import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.EventBus;

public class FMLJavaModLoadingContext {
    private static final FMLJavaModLoadingContext INSTANCE = new FMLJavaModLoadingContext();
    private final IEventBus eventBus = new EventBus();

    public static FMLJavaModLoadingContext get() {
        return INSTANCE;
    }

    public IEventBus getModEventBus() {
        return eventBus;
    }
}
