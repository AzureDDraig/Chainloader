package net.fabricmc.fabric.api.screenhandler.v1;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;

public final class ScreenHandlerRegistry {

    @FunctionalInterface
    public interface ExtendedClientHandlerFactory<T extends AbstractContainerMenu> {
        T create(int syncId, net.minecraft.world.entity.player.Inventory inventory, net.minecraft.network.FriendlyByteBuf buf);
    }

    public static class ExtendedMenuType<T extends AbstractContainerMenu> extends MenuType<T> {
        private final ExtendedClientHandlerFactory<T> factory;

        public ExtendedMenuType(ExtendedClientHandlerFactory<T> factory) {
            super((syncId, inventory) -> null, null);
            this.factory = factory;
        }

        @Override
        public T create(int syncId, net.minecraft.world.entity.player.Inventory inventory) {
            net.minecraft.network.FriendlyByteBuf buf = net.chainloader.loader.compat.bridge.EventBridgeHelper.getAndRemoveClientBuffer(syncId);
            if (buf != null) {
                return factory.create(syncId, inventory, buf);
            }
            return null;
        }
    }

    public static <T extends AbstractContainerMenu> MenuType<T> registerExtended(
            ResourceLocation id, ExtendedClientHandlerFactory<T> factory) {
        
        MenuType<T> type = new ExtendedMenuType<>(factory);
        try {
            Class<?> registryClass = Class.forName("net.minecraft.core.Registry");
            Class<?> registriesClass = Class.forName("net.minecraft.core.registries.BuiltInRegistries");
            
            java.lang.reflect.Field menuField;
            try {
                menuField = registriesClass.getField("MENU");
            } catch (NoSuchFieldException e) {
                menuField = registriesClass.getField("p"); // Obfuscated field for MENU (jz<crc<?>>)
            }
            Object menuRegistry = menuField.get(null);
            
            java.lang.reflect.Method registerMethod = null;
            try {
                registerMethod = registryClass.getMethod("register", registryClass, ResourceLocation.class, Object.class);
            } catch (NoSuchMethodException e) {
                registerMethod = registryClass.getMethod("a", registryClass, ResourceLocation.class, Object.class); // Obfuscated method for register
            }
            registerMethod.invoke(null, menuRegistry, id, type);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return type;
    }
}
