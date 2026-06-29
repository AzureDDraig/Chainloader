package net.fabricmc.fabric.api.client.model;

import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.resources.ResourceLocation;
import java.util.function.Function;
import java.util.ArrayList;
import java.util.List;

public interface ModelLoadingRegistry {

    ModelLoadingRegistry INSTANCE = new ModelLoadingRegistryImpl();

    void registerModelProvider(ExtraModelProvider provider);
    
    @Deprecated
    void registerAppender(ModelAppender appender);

    void registerResourceProvider(Function<ResourceManager, ModelResourceProvider> providerFactory);

    void registerVariantProvider(Function<ResourceManager, ModelVariantProvider> providerFactory);

    class ModelLoadingRegistryImpl implements ModelLoadingRegistry {
        private final List<ExtraModelProvider> modelProviders = new ArrayList<>();

        @Override
        public void registerModelProvider(ExtraModelProvider provider) {
            System.out.println("[ModelLoadingRegistry] Registered legacy model provider: " + provider.getClass().getName());
            modelProviders.add(provider);
        }

        @Override
        public void registerAppender(ModelAppender appender) {
            registerModelProvider(appender);
        }

        @Override
        public void registerResourceProvider(Function<ResourceManager, ModelResourceProvider> providerFactory) {
            System.out.println("[ModelLoadingRegistry] WARNING: registerResourceProvider is stubbed as a no-op");
        }

        @Override
        public void registerVariantProvider(Function<ResourceManager, ModelVariantProvider> providerFactory) {
            System.out.println("[ModelLoadingRegistry] WARNING: registerVariantProvider is stubbed as a no-op");
        }

        public List<ExtraModelProvider> getModelProviders() {
            return modelProviders;
        }
    }
}
