package net.fabricmc.fabric.api.client.model.loading.v1;

import java.util.function.Function;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.ModelResourceLocation;

public final class ModelModifier {
    public static final ResourceLocation OVERRIDE_PHASE = new ResourceLocation("fabric", "override");
    public static final ResourceLocation DEFAULT_PHASE = new ResourceLocation("fabric", "default");
    public static final ResourceLocation WRAP_PHASE = new ResourceLocation("fabric", "wrap");
    public static final ResourceLocation WRAP_LAST_PHASE = new ResourceLocation("fabric", "wrap_last");

    private ModelModifier() {}

    @FunctionalInterface
    public interface OnLoad {
        UnbakedModel modifyModelOnLoad(UnbakedModel model, Context context);

        interface Context {
            ResourceLocation id();

            UnbakedModel getOrLoadModel(ResourceLocation id);

            Object loader();
        }
    }

    @FunctionalInterface
    public interface BeforeBake {
        UnbakedModel modifyModelBeforeBake(UnbakedModel model, Context context);

        interface Context {
            ResourceLocation id();

            Function<?, ?> textureGetter();

            ModelState settings();

            Object baker();

            Object loader();
        }
    }

    @FunctionalInterface
    public interface AfterBake {
        BakedModel modifyModelAfterBake(BakedModel model, Context context);

        interface Context {
            ResourceLocation resourceId();

            ModelResourceLocation topLevelId();

            UnbakedModel sourceModel();

            Function<?, ?> textureGetter();

            ModelState settings();

            Object baker();

            Object loader();
        }
    }
}
