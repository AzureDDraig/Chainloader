package net.fabricmc.fabric.api.client.model.loading.v1;

import java.util.Collection;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.fabricmc.fabric.api.event.Event;

@FunctionalInterface
public interface ModelLoadingPlugin {
    java.util.List<ModelLoadingPlugin> REGISTERED_PLUGINS = new java.util.ArrayList<>();

    void onInitializeModelLoader(Context pluginContext);

    static void register(ModelLoadingPlugin plugin) {
        System.out.println("[ModelLoadingPlugin] Registered plugin: " + plugin);
        REGISTERED_PLUGINS.add(plugin);
    }

    interface Context {
        default void addModels(ResourceLocation... ids) {}

        default void addModels(Collection<? extends ResourceLocation> ids) {}

        default void registerBlockStateResolver(Block block, BlockStateResolver resolver) {}

        default Event<ModelResolver> resolveModel() {
            return new Event<>();
        }

        default Event<ModelModifier.OnLoad> modifyModelOnLoad() {
            return new Event<>();
        }

        default Event<ModelModifier.BeforeBake> modifyModelBeforeBake() {
            return new Event<>();
        }

        default Event<ModelModifier.AfterBake> modifyModelAfterBake() {
            return new Event<>();
        }
    }
}
