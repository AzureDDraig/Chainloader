# Model Loading Registry Bridges

ChainLoader bridges legacy and modern Fabric model loading APIs onto Minecraft 1.21.1 (NeoForge) model events by intercepting event postings and using reflection-based proxying.

## Event Interception and Routing

In NeoForge 1.21.1, model loading and registration are handled via specific bus events:
1. `ModelEvent.RegisterAdditional`: Used to register extra/additional model resource locations before baking.
2. `ModelEvent.BakingCompleted`: Posted after models are baked, providing access to the final baked model map.

To bridge legacy Fabric registries (like `ModelLoadingRegistry` and modern `ModelLoadingPlugin`) without modifying mod code, ChainLoader intercepts the posting of these events directly inside its custom EventBus implementations:

```java
// From net.neoforged.bus.EventBus / net.minecraftforge.eventbus.api.EventBus
@Override
public boolean post(Object event) {
    if (event == null) return false;
    Class<?> current = event.getClass();
    
    if (current.getName().contains("ModelEvent$RegisterAdditional")) {
        try {
            Class<?> helper = Class.forName("net.chainloader.loader.compat.bridge.EventBridgeHelper", true, current.getClassLoader());
            helper.getMethod("bridgeFabricModelLoading", Object.class).invoke(null, event);
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
    // ... normal event dispatching logic continues ...
}
```

---

## Bridging Model Loading APIs

When `ModelEvent.RegisterAdditional` is intercepted, `EventBridgeHelper.bridgeFabricModelLoading(registerAdditionalEvent)` handles routing the registrations.

### 1. Modern Fabric `ModelLoadingPlugin` Bridge
Fabric's modern model API uses `ModelLoadingPlugin` classes registered in `ModelLoadingPlugin.REGISTERED_PLUGINS`. To invoke them:
* The bridge creates a dynamic JDK proxy implementing `ModelLoadingPlugin$Context`.
* When the plugin calls `addModels(ResourceLocation...)` or `addModels(Collection)`, the proxy's `InvocationHandler` intercepts the arguments and maps them directly to the NeoForge event's `register` method.
* Other hooks (like `resolveModel`, `modifyModelOnLoad`, `modifyModelBeforeBake`, and `modifyModelAfterBake`) return dummy events or stubs as fallback behavior.

```java
Class<?> contextClass = Class.forName("net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin$Context");
Object contextProxy = java.lang.reflect.Proxy.newProxyInstance(
    registerAdditionalEvent.getClass().getClassLoader(),
    new Class<?>[] { contextClass },
    (proxy, method, args) -> {
        String methodName = method.getName();
        if ("addModels".equals(methodName)) {
            if (args[0] instanceof ResourceLocation[] locs) {
                for (ResourceLocation loc : locs) {
                    registerAdditionalModel(registerAdditionalEvent, loc);
                }
            } else if (args[0] instanceof Collection<?> coll) {
                for (Object obj : coll) {
                    if (obj instanceof ResourceLocation loc) {
                        registerAdditionalModel(registerAdditionalEvent, loc);
                    }
                }
            }
        }
        // Return event stubs for modifiers
        if ("resolveModel".equals(methodName) || "modifyModelOnLoad".equals(methodName) || 
            "modifyModelBeforeBake".equals(methodName) || "modifyModelAfterBake".equals(methodName)) {
            return new net.fabricmc.fabric.api.event.Event<>();
        }
        return null;
    }
);
```

### 2. Legacy Fabric `ModelLoadingRegistry` Bridge
Older Fabric mods register model providers via `ModelLoadingRegistry.INSTANCE.registerModelProvider(ExtraModelProvider)`. The shim stores these in a local `List<ExtraModelProvider>`:

```java
public interface ModelLoadingRegistry {
    ModelLoadingRegistry INSTANCE = new ModelLoadingRegistryImpl();
    void registerModelProvider(ExtraModelProvider provider);
    
    class ModelLoadingRegistryImpl implements ModelLoadingRegistry {
        private final List<ExtraModelProvider> modelProviders = new ArrayList<>();
        
        @Override
        public void registerModelProvider(ExtraModelProvider provider) {
            modelProviders.add(provider);
        }
        // ... stubs for resource/variant providers ...
    }
}
```

During event interception:
* The bridge retrieves the list of legacy `ExtraModelProvider`s.
* It resolves a reference to the active `ResourceManager`.
* It calls `provider.provideExtraModels(resourceManager, consumer)` and routes all consumed `ResourceLocation`s to the NeoForge event's `register` method:

```java
private static void registerAdditionalModel(Object event, ResourceLocation modelLoc) {
    try {
        event.getClass().getMethod("register", ResourceLocation.class).invoke(event, modelLoc);
    } catch (Throwable t) {
        System.err.println("[EventBridgeHelper] Failed to register extra model: " + modelLoc);
    }
}
```

---

## Model Baking and completed events

Once the model registration phase is complete, NeoForge bakes all registered models.
* The baked models are collected into a `Map<ResourceLocation, BakedModel>`.
* NeoForge fires `ModelEvent.BakingCompleted`.
* ChainLoader's event bus relays this event. Any legacy or compat listener subscribing to baking phases receives access to the modern baked models, allowing custom renderer setups to hook final render styles correctly.
