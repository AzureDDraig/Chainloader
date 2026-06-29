package net.minecraft.world.item;

import net.minecraft.network.chat.Component;
import java.util.function.Supplier;
import net.minecraft.world.level.ItemLike;

public class CreativeModeTab {
    public enum Row {
        TOP,
        BOTTOM
    }

    public enum Type {
        CATEGORY,
        INVENTORY,
        HOTBAR,
        SEARCH
    }

    public enum TabVisibility {
        PARENT_AND_SEARCH_TABS,
        PARENT_TAB_ONLY,
        SEARCH_TAB_ONLY
    }

    public interface Output {
        void accept(ItemLike item);
        void accept(ItemStack stack);
        void accept(ItemStack stack, TabVisibility visibility);
        void accept(ItemLike item, TabVisibility visibility);
    }

    public interface ItemDisplayParameters {
    }

    public interface DisplayItemsGenerator {
        void accept(ItemDisplayParameters parameters, Output output);
    }

    public static Builder builder(Row row, int index) {
        return null;
    }

    public static class Builder {
        public Builder title(Component title) {
            return this;
        }

        public Builder icon(Supplier<ItemStack> icon) {
            return this;
        }

        public CreativeModeTab build() {
            return null;
        }
    }
}
