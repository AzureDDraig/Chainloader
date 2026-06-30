package net.fabricmc.fabric.api.client.screenhandler.v1;

import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.screens.Screen;

/**
 * Compatibility implementation for ScreenRegistry using reflection.
 */
public final class ScreenRegistry {
    public interface Factory<H extends AbstractContainerMenu, S extends Screen> {
        S create(H handler, Inventory inventory, Component title);
    }

    @SuppressWarnings("unchecked")
    public static <H extends AbstractContainerMenu, S extends Screen> void register(
        MenuType<? extends H> type,
        Factory<H, S> screenFactory
    ) {
        try {
            Class<?> menuScreensClass = Class.forName("net.minecraft.client.gui.screens.MenuScreens");
            Class<?> screenConstructorClass = Class.forName("net.minecraft.client.gui.screens.MenuScreens$ScreenConstructor");
            
            Object screenConstructorProxy = java.lang.reflect.Proxy.newProxyInstance(
                screenConstructorClass.getClassLoader(),
                new Class<?>[] { screenConstructorClass },
                new java.lang.reflect.InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args) throws Throwable {
                        if (args != null && args.length == 3) {
                            AbstractContainerMenu menu = (AbstractContainerMenu) args[0];
                            Inventory inventory = (Inventory) args[1];
                            Component title = (Component) args[2];
                            return screenFactory.create((H) menu, inventory, title);
                        }
                        return null;
                    }
                }
            );
            
            java.lang.reflect.Method registerMethod = null;
            for (java.lang.reflect.Method m : menuScreensClass.getDeclaredMethods()) {
                if (m.getName().equals("register") || m.getName().equals("a")) {
                    Class<?>[] params = m.getParameterTypes();
                    if (params.length == 2 && params[0].isAssignableFrom(MenuType.class) && params[1].equals(screenConstructorClass)) {
                        registerMethod = m;
                        break;
                    }
                }
            }
            
            if (registerMethod != null) {
                registerMethod.setAccessible(true);
                registerMethod.invoke(null, type, screenConstructorProxy);
                System.out.println("[ScreenRegistry] Successfully registered screen factory for MenuType: " + type);
            } else {
                System.err.println("[ScreenRegistry] Failed to find MenuScreens.register method!");
            }
        } catch (Throwable t) {
            System.err.println("[ScreenRegistry] Failed to register screen factory:");
            t.printStackTrace();
        }
    }
}
