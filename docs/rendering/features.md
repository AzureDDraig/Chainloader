# Rendering Features: GUI Layers, Key Mappings, and Tooltips

Minecraft 1.21.1 and NeoForge structure client rendering around event buses. Custom overlays, keybind options, and tooltip formatting are handled via specialized event subscriptions. 

ChainLoader bridges legacy GUI, keybind, and tooltip hooks to these modern event registrations, ensuring rendering modifications compile and execute seamlessly.

---

## 1. GUI Overlays & HUD Render Layers

In older modding APIs, drawing to the HUD/in-game GUI was handled by registering a HUD render callback (e.g. Fabric's `HudRenderCallback`). NeoForge 1.21.1 structures this around **GUI Layers** registered in the `RegisterGuiLayersEvent`.

### Capture & Registration Cycle
1.  ChainLoader registers a GUI layer overlay in NeoForge's `RegisterGuiLayersEvent`:
    ```java
    // EventBridgeHelper.java
    postToBus(bus, new RegisterGuiLayersEvent());
    ```
2.  At render time, ChainLoader maps HUD overlays gathered from the event to `ChainRenderEvents.HUD_RENDER` inside the GUI draw cycle:
    ```java
    public static void onRenderGuiOverlays(GuiGraphics graphics, float partialTick) {
        // ... wraps partial ticks and renders
        ChainRenderEvents.HUD_RENDER.invoker().onHudRender(graphics, partialTick);
    }
    ```
3.  The Fabric API Port maps this HUD render event to trigger early/legacy Fabric `HudRenderCallback` listeners.

---

## 2. Key Mapping Injection

Mod keybinds must be registered with the game's options array to prevent the key inputs from being ignored. NeoForge handles this via `RegisterKeyMappingsEvent`.

Because legacy mods register key mappings during early startup, ChainLoader captures them and merges them into the active `Minecraft.options.keyMappings` array using `sun.misc.Unsafe`.

### Dynamic Injection Logic (EventBridgeHelper.java)
```java
public static void injectKeyMappings(Object minecraft) {
    try {
        net.minecraft.client.Minecraft mc = (net.minecraft.client.Minecraft) minecraft;
        if (mc.options == null) return;
        
        net.minecraft.client.KeyMapping[] original = null;
        java.lang.reflect.Field keyMappingsField = null;
        
        // Scan for the KeyMapping[] field inside Options class
        for (java.lang.reflect.Field f : net.minecraft.client.Options.class.getDeclaredFields()) {
            if (f.getType() == net.minecraft.client.KeyMapping[].class) {
                f.setAccessible(true);
                net.minecraft.client.KeyMapping[] currentVal = (net.minecraft.client.KeyMapping[]) f.get(mc.options);
                if (currentVal != null && currentVal.length > 15) {
                    keyMappingsField = f;
                    original = currentVal;
                    break;
                }
            }
        }

        if (original == null) original = new net.minecraft.client.KeyMapping[0];
        
        // Filter out key mappings that are already present
        List<net.minecraft.client.KeyMapping> toAdd = new ArrayList<>();
        for (net.minecraft.client.KeyMapping custom : customKeyMappings) {
            boolean exists = false;
            for (net.minecraft.client.KeyMapping orig : original) {
                if (orig == custom) {
                    exists = true;
                    break;
                }
            }
            if (!exists) toAdd.add(custom);
        }
        
        if (toAdd.isEmpty()) return;
        
        // Create new expanded array
        net.minecraft.client.KeyMapping[] newArray = new net.minecraft.client.KeyMapping[original.length + toAdd.size()];
        System.arraycopy(original, 0, newArray, 0, original.length);
        for (int i = 0; i < toAdd.size(); i++) {
            newArray[original.length + i] = toAdd.get(i);
        }
        
        // Write the array back to the options object using Unsafe to bypass final modifiers
        java.lang.reflect.Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeField.get(null);
        long offset = unsafe.objectFieldOffset(keyMappingsField);
        unsafe.putObject(mc.options, offset, newArray);
        
        mc.options.load(); // Reload options from options.txt
        System.out.println("[ChainLoader] Injected " + toAdd.size() + " key mappings successfully.");
    } catch (Throwable t) {
        t.printStackTrace();
    }
}
```

---

## 3. Tooltip Modifications

Minecraft 1.21.1 tooltips are represented by `Component` lists and parsed by `TooltipComponent` factories. 

ChainLoader bridges legacy tooltip modifications (e.g. adding text lines dynamically when an item is hovered) by listening to ChainLoader's `TOOLTIP_MODIFY` event and routing it to legacy callbacks.

### Tooltip Event Translation (FabricApiPort.java)
```java
ChainRenderEvents.TOOLTIP_MODIFY.register((stack, tooltipLines, context) -> {
    Object eventMarker = new Object();
    if (activeEventTranslations.get().contains(eventMarker)) return;

    activeEventTranslations.get().add(eventMarker);
    try {
        // Dispatch to Fabric ItemTooltipCallback
        FabricLoaderShim.getInstance().dispatchEvent("fabric:tooltip_modify", listener -> {
            try {
                Class<?> itemStackClass = Class.forName("net.minecraft.item.ItemStack");
                Class<?> tooltipContextClass = Class.forName("net.minecraft.client.item.TooltipContext");
                java.lang.reflect.Method method = listener.getClass().getMethod("getTooltip", itemStackClass, tooltipContextClass, List.class);
                method.setAccessible(true);
                method.invoke(listener, stack, context, tooltipLines);
            } catch (Exception e) {
                System.err.println("Error translating Tooltip event: " + e.getMessage());
            }
        });
    } finally {
        activeEventTranslations.get().remove(eventMarker);
    }
});
```
This bidirectional translation enables legacy tooltips to format their hover text lines without needing to interact with the modern tooltip rendering pipeline.
