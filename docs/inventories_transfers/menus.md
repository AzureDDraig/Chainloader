# Menus

In Minecraft, GUI menus (historically known as "screen handlers" in Fabric and "containers" in Forge) bridge server-side inventory states to client-side screens. Registering menus and mapping them to their corresponding client screens requires coordinating registration APIs and handling screen constructors.

ChainLoader shims both the server-side menu registrations and client-side screen associations, translating them to Minecraft 1.21.1 (NeoForge) equivalents.

---

## Server-Side Menu Registration

Vanilla Minecraft registers menu types in the `BuiltInRegistries.MENU` registry. Fabric mods use `ScreenHandlerRegistry` to register menus that expect extra client buffer data.

ChainLoader shims `ScreenHandlerRegistry.registerExtended` to register custom `ExtendedMenuType` objects and reflectively register them into vanilla registries:

```java
// From ScreenHandlerRegistry.java
public static <T extends AbstractContainerMenu> MenuType<T> registerExtended(
        ResourceLocation id, ExtendedClientHandlerFactory<T> factory) {
    
    MenuType<T> type = new ExtendedMenuType<>(factory);
    try {
        Class<?> registryClass = Class.forName("net.minecraft.core.Registry");
        Class<?> registriesClass = Class.forName("net.minecraft.core.registries.BuiltInRegistries");
        
        // Resolve field for BuiltInRegistries.MENU (obfuscated as "p" in 1.21.1)
        java.lang.reflect.Field menuField;
        try {
            menuField = registriesClass.getField("MENU");
        } catch (NoSuchFieldException e) {
            menuField = registriesClass.getField("p");
        }
        Object menuRegistry = menuField.get(null);
        
        // Resolve method for Registry.register (obfuscated as "a" in 1.21.1)
        java.lang.reflect.Method registerMethod = null;
        try {
            registerMethod = registryClass.getMethod("register", registryClass, ResourceLocation.class, Object.class);
        } catch (NoSuchMethodException e) {
            registerMethod = registryClass.getMethod("a", registryClass, ResourceLocation.class, Object.class);
        }
        
        // Register the MenuType
        registerMethod.invoke(null, menuRegistry, id, type);
    } catch (Exception e) {
        e.printStackTrace();
    }
    return type;
}
```

This dynamically binds the custom menu type to the vanilla registry under the provided `ResourceLocation`, making it available for client/server networking.

---

## Client-Side Screen Association

On the client, every custom `MenuType` must be associated with a `Screen` constructor. Fabric mods do this by calling `ScreenRegistry.register(MenuType, ScreenFactory)`.

In Minecraft 1.21.1, screens are registered to menu types via `MenuScreens.register(MenuType, ScreenConstructor)`. The `ScreenConstructor` is a functional interface (represented obfuscated as `MenuScreens$ScreenConstructor` or `MenuScreens$a` in 1.21.1).

ChainLoader's client-side `ScreenRegistry` shim implements this mapping using a dynamic proxy class:

```java
// From ScreenRegistry.java
public static <H extends AbstractContainerMenu, S extends Screen> void register(
    MenuType<? extends H> type,
    Factory<H, S> screenFactory
) {
    try {
        Class<?> menuScreensClass = Class.forName("net.minecraft.client.gui.screens.MenuScreens");
        Class<?> screenConstructorClass = Class.forName("net.minecraft.client.gui.screens.MenuScreens$ScreenConstructor");
        
        // 1. Create a dynamic JDK proxy representing the MenuScreens.ScreenConstructor interface
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
                        // 2. Delegate to Fabric's screenFactory
                        return screenFactory.create((H) menu, inventory, title);
                    }
                    return null;
                }
            }
        );
        
        // 3. Resolve MenuScreens.register method (obfuscated as "a")
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
        
        // 4. Invoke registration
        if (registerMethod != null) {
            registerMethod.setAccessible(true);
            registerMethod.invoke(null, type, screenConstructorProxy);
        }
    } catch (Throwable t) {
        t.printStackTrace();
    }
}
```

This registers the screen factory on the client-side, allowing Minecraft's client GUI engine to correctly instantiate screen views when the server opens a menu.
