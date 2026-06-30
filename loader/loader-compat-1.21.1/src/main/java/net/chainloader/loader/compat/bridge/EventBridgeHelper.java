package net.chainloader.loader.compat.bridge;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.ArrayList;

public class EventBridgeHelper {

    public static net.minecraft.world.level.biome.Biome.Precipitation getPrecipitationBridge(Object biomeObj) {
        if (biomeObj == null) return net.minecraft.world.level.biome.Biome.Precipitation.NONE;
        try {
            net.minecraft.world.level.biome.Biome biome = (net.minecraft.world.level.biome.Biome) biomeObj;
            if (biome.hasPrecipitation()) {
                float temp = biome.getBaseTemperature();
                if (temp < 0.15f) {
                    return net.minecraft.world.level.biome.Biome.Precipitation.SNOW;
                }
                return net.minecraft.world.level.biome.Biome.Precipitation.RAIN;
            }
        } catch (Throwable t) {
            // Fallback for mock or uninitialized Biome instances
        }
        return net.minecraft.world.level.biome.Biome.Precipitation.NONE;
    }

    public static boolean hasHumidityBridge(Object biomeObj) {
        if (biomeObj == null) return false;
        try {
            java.lang.reflect.Field climateSettingsField = null;
            try {
                climateSettingsField = biomeObj.getClass().getDeclaredField("i"); // Obfuscated
            } catch (NoSuchFieldException e) {
                // Deobfuscated
                for (java.lang.reflect.Field f : biomeObj.getClass().getDeclaredFields()) {
                    if (f.getType().getName().contains("ClimateSettings")) {
                        climateSettingsField = f;
                        break;
                    }
                }
            }
            if (climateSettingsField != null) {
                climateSettingsField.setAccessible(true);
                Object climateSettings = climateSettingsField.get(biomeObj);
                if (climateSettings != null) {
                    java.lang.reflect.Field downfallField = null;
                    try {
                        downfallField = climateSettings.getClass().getDeclaredField("e"); // Obfuscated
                    } catch (NoSuchFieldException e) {
                        try {
                            downfallField = climateSettings.getClass().getDeclaredField("downfall"); // Deobfuscated
                        } catch (NoSuchFieldException ex) {
                            // Fallback to second float field
                            int floatCount = 0;
                            for (java.lang.reflect.Field f : climateSettings.getClass().getDeclaredFields()) {
                                if (f.getType() == float.class) {
                                    floatCount++;
                                    if (floatCount == 2) {
                                        downfallField = f;
                                        break;
                                    }
                                }
                            }
                        }
                    }
                    if (downfallField != null) {
                        downfallField.setAccessible(true);
                        float downfall = downfallField.getFloat(climateSettings);
                        return downfall > 0.85f;
                    }
                }
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
        return false;
    }

    public static net.minecraft.server.level.ServerLevel getServerLevelBridge(Object playerObj) {
        if (playerObj instanceof net.minecraft.server.level.ServerPlayer player) {
            return (net.minecraft.server.level.ServerLevel) (Object) player.level;
        }
        return null;
    }

    public static com.mojang.serialization.MapCodec<?> getLegacyCodecBridge(Object instance) {
        try {
            for (java.lang.reflect.Method m : instance.getClass().getMethods()) {
                if (m.getName().equals("codec") && com.mojang.serialization.Codec.class.isAssignableFrom(m.getReturnType()) && !com.mojang.serialization.MapCodec.class.isAssignableFrom(m.getReturnType())) {
                    m.setAccessible(true);
                    com.mojang.serialization.Codec<?> codec = (com.mojang.serialization.Codec<?>) m.invoke(instance);
                    return com.mojang.serialization.MapCodec.assumeMapUnsafe(codec);
                }
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
        throw new AbstractMethodError("Method codec()Lcom/mojang/serialization/MapCodec; is not implemented on " + instance.getClass().getName());
    }
    public static com.mojang.serialization.MapCodec<?> getRecipeSerializerCodec(Object serializer) {
        return new LegacyRecipeMapCodec(serializer);
    }

    public static net.minecraft.network.codec.StreamCodec<?, ?> getRecipeSerializerStreamCodec(Object serializer) {
        return new LegacyRecipeStreamCodec(serializer);
    }

    public static class LegacyRecipeMapCodec extends com.mojang.serialization.MapCodec<Object> {
        private final Object serializer;

        public LegacyRecipeMapCodec(Object serializer) {
            this.serializer = serializer;
        }

        @Override
        public <S> com.mojang.serialization.DataResult<Object> decode(com.mojang.serialization.DynamicOps<S> ops, com.mojang.serialization.MapLike<S> input) {
            try {
                java.util.stream.Stream<com.mojang.datafixers.util.Pair<S, S>> entriesStream = input.entries();
                java.util.Map<S, S> map = new java.util.HashMap<>();
                entriesStream.forEach(pair -> map.put(pair.getFirst(), pair.getSecond()));
                S mapVal = ops.createMap(map);
                com.google.gson.JsonElement jsonElement = ops.convertTo(com.mojang.serialization.JsonOps.INSTANCE, mapVal);
                if (jsonElement.isJsonObject()) {
                    com.google.gson.JsonObject jsonObject = jsonElement.getAsJsonObject();
                    java.lang.reflect.Method readMethod = null;
                    for (java.lang.reflect.Method m : serializer.getClass().getMethods()) {
                        if (m.getName().equals("read") || m.getName().equals("m_6729_")) {
                            Class<?>[] params = m.getParameterTypes();
                            if (params.length == 2 && params[0].getName().contains("ResourceLocation") && params[1].getName().contains("JsonObject")) {
                                readMethod = m;
                                break;
                            }
                        }
                    }
                    if (readMethod != null) {
                        readMethod.setAccessible(true);
                        Object dummyLoc = new net.minecraft.resources.ResourceLocation("minecraft", "dummy_recipe");
                        Object recipe = readMethod.invoke(serializer, dummyLoc, jsonObject);
                        return com.mojang.serialization.DataResult.success(recipe);
                    }
                }
            } catch (Throwable t) {
                t.printStackTrace();
                return com.mojang.serialization.DataResult.error(t::getMessage);
            }
            return com.mojang.serialization.DataResult.error(() -> "Failed to decode legacy recipe using " + serializer.getClass().getName());
        }

        @Override
        public <S> com.mojang.serialization.RecordBuilder<S> encode(Object input, com.mojang.serialization.DynamicOps<S> ops, com.mojang.serialization.RecordBuilder<S> prefix) {
            return prefix;
        }

        @Override
        public <S> java.util.stream.Stream<S> keys(com.mojang.serialization.DynamicOps<S> ops) {
            return java.util.stream.Stream.empty();
        }
    }

    public static class LegacyRecipeStreamCodec implements net.minecraft.network.codec.StreamCodec<net.minecraft.network.FriendlyByteBuf, Object> {
        private final Object serializer;

        public LegacyRecipeStreamCodec(Object serializer) {
            this.serializer = serializer;
        }

        @Override
        public Object decode(net.minecraft.network.FriendlyByteBuf buf) {
            try {
                java.lang.reflect.Method readMethod = null;
                for (java.lang.reflect.Method m : serializer.getClass().getMethods()) {
                    if (m.getName().equals("read") || m.getName().equals("m_6729_")) {
                        Class<?>[] params = m.getParameterTypes();
                        if (params.length == 1 && params[0].getName().contains("FriendlyByteBuf")) {
                            readMethod = m;
                            break;
                        }
                    }
                }
                if (readMethod == null) {
                    for (java.lang.reflect.Method m : serializer.getClass().getMethods()) {
                        if (m.getName().equals("read") || m.getName().equals("m_6729_")) {
                            Class<?>[] params = m.getParameterTypes();
                            if (params.length == 2 && params[0].getName().contains("ResourceLocation") && params[1].getName().contains("FriendlyByteBuf")) {
                                readMethod = m;
                                break;
                            }
                        }
                    }
                    if (readMethod != null) {
                        readMethod.setAccessible(true);
                        Object dummyLoc = new net.minecraft.resources.ResourceLocation("minecraft", "dummy_recipe");
                        return readMethod.invoke(serializer, dummyLoc, buf);
                    }
                } else {
                    readMethod.setAccessible(true);
                    return readMethod.invoke(serializer, buf);
                }
            } catch (Throwable t) {
                t.printStackTrace();
            }
            throw new RuntimeException("Failed to decode legacy recipe packet using " + serializer.getClass().getName());
        }

        @Override
        public void encode(net.minecraft.network.FriendlyByteBuf buf, Object value) {
            try {
                java.lang.reflect.Method writeMethod = null;
                for (java.lang.reflect.Method m : serializer.getClass().getMethods()) {
                    if (m.getName().equals("write") || m.getName().equals("m_6730_")) {
                        Class<?>[] params = m.getParameterTypes();
                        if (params.length == 2 && params[0].getName().contains("FriendlyByteBuf")) {
                            writeMethod = m;
                            break;
                        }
                    }
                }
                if (writeMethod != null) {
                    writeMethod.setAccessible(true);
                    writeMethod.invoke(serializer, buf, value);
                    return;
                }
            } catch (Throwable t) {
                t.printStackTrace();
            }
            throw new RuntimeException("Failed to encode legacy recipe packet using " + serializer.getClass().getName());
        }
    }
    public static String getResourceLocationNamespace(String s) {
        int colon = s.indexOf(':');
        return colon >= 0 ? s.substring(0, colon) : "minecraft";
    }

    public static String getResourceLocationPath(String s) {
        int colon = s.indexOf(':');
        return colon >= 0 ? s.substring(colon + 1) : s;
    }

    public static void registerItemProperty(Object item, Object loc, Object func) {
        try {
            ClassLoader cl = func.getClass().getClassLoader();
            Class<?> clampedClass = Class.forName("net.minecraft.client.renderer.item.ClampedItemPropertyFunction", true, cl);
            Class<?> funcClass = Class.forName("net.minecraft.client.renderer.item.ItemPropertyFunction", true, cl);
            
            Object proxy = java.lang.reflect.Proxy.newProxyInstance(
                cl,
                new Class<?>[] { clampedClass },
                new java.lang.reflect.InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args) throws Throwable {
                        String name = method.getName();
                        if (name.equals("call") || name.equals("unclampedCall") || name.equals("a")) {
                            java.lang.reflect.Method callMethod = funcClass.getMethod("call", 
                                Class.forName("net.minecraft.world.item.ItemStack", true, cl),
                                Class.forName("net.minecraft.client.multiplayer.ClientLevel", true, cl),
                                Class.forName("net.minecraft.world.entity.LivingEntity", true, cl),
                                int.class
                            );
                            return callMethod.invoke(func, args);
                        }
                        // Default implementations
                        if (name.equals("toString")) {
                            return "ClampedItemPropertyFunctionProxy[" + func.toString() + "]";
                        }
                        if (name.equals("hashCode")) {
                            return func.hashCode();
                        }
                        if (name.equals("equals")) {
                            return args[0] != null && java.lang.reflect.Proxy.isProxyClass(args[0].getClass()) && 
                                   java.lang.reflect.Proxy.getInvocationHandler(args[0]) == this;
                        }
                        return null;
                    }
                }
            );
            
            Class<?> itemPropertiesClass = Class.forName("net.minecraft.client.renderer.item.ItemProperties", true, cl);
            Class<?> itemClass = Class.forName("net.minecraft.world.item.Item", true, cl);
            Class<?> resLocClass = Class.forName("net.minecraft.resources.ResourceLocation", true, cl);
            java.lang.reflect.Method registerMethod = null;
            for (java.lang.reflect.Method m : itemPropertiesClass.getDeclaredMethods()) {
                Class<?>[] params = m.getParameterTypes();
                if (params.length == 3 && 
                    itemClass.isAssignableFrom(params[0]) && 
                    resLocClass.isAssignableFrom(params[1]) && 
                    clampedClass.isAssignableFrom(params[2])) {
                    registerMethod = m;
                    break;
                }
            }
            if (registerMethod == null) {
                throw new NoSuchMethodException("Could not find register method in ItemProperties with signature (Item, ResourceLocation, ClampedItemPropertyFunction)");
            }
            registerMethod.setAccessible(true);
            registerMethod.invoke(null, item, loc, proxy);
        } catch (Exception e) {
            System.err.println("[ChainLoader] Failed to register item property via reflection bridge:");
            e.printStackTrace();
        }
    }

    private static final java.util.Map<net.minecraft.client.model.geom.ModelLayerLocation, java.util.function.Supplier<net.minecraft.client.model.geom.builders.LayerDefinition>> customLayerDefinitions = new java.util.HashMap<>();

    public static void onScreenInitPre(Screen screen) {
        // Fire NeoForge ScreenEvent.Init.Pre
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
            new net.neoforged.neoforge.client.event.ScreenEvent.Init.Pre(screen)
        );
        // Fire Forge ScreenEvent.Init.Pre
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(
            new net.minecraftforge.client.event.ScreenEvent.Init.Pre(screen)
        );
        // Fire Fabric ScreenEvents.BEFORE_INIT
        try {
            net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
            net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.BEFORE_INIT.invoker().beforeInit(client, screen, screen.width, screen.height);
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    public static void onScreenInitPost(Screen screen) {
        // Fire NeoForge ScreenEvent.Init.Post
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
            new net.neoforged.neoforge.client.event.ScreenEvent.Init.Post(screen)
        );
        // Fire Forge ScreenEvent.Init.Post
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(
            new net.minecraftforge.client.event.ScreenEvent.Init.Post(screen)
        );
        // Fire Fabric ScreenEvents.AFTER_INIT
        try {
            net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
            net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.AFTER_INIT.invoker().afterInit(client, screen, screen.width, screen.height);
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    public static void onScreenRenderPre(Screen screen, GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Fire Fabric ScreenEvents.beforeRender
        try {
            net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.beforeRender(screen).invoker().beforeRender(screen, graphics, mouseX, mouseY, partialTick);
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    public static void onScreenRenderPost(Screen screen, GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Fire NeoForge ScreenEvent.Render.Post
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
            new net.neoforged.neoforge.client.event.ScreenEvent.Render.Post(screen, graphics, mouseX, mouseY, partialTick)
        );
        // Fire Forge ScreenEvent.Render.Post
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(
            new net.minecraftforge.client.event.ScreenEvent.Render.Post(screen, graphics, mouseX, mouseY, partialTick)
        );
        // Fire Fabric ScreenEvents.afterRender
        try {
            net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.afterRender(screen).invoker().afterRender(screen, graphics, mouseX, mouseY, partialTick);
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    public static boolean onScreenKeyPressedPre(Screen screen, int keyCode, int scanCode, int modifiers) {
        // Fire NeoForge ScreenEvent.KeyPressed.Pre
        boolean cancelled1 = net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
            new net.neoforged.neoforge.client.event.ScreenEvent.KeyPressed.Pre(screen, keyCode, scanCode, modifiers)
        );
        // Fire Forge ScreenEvent.KeyPressed.Pre
        boolean cancelled2 = net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(
            new net.minecraftforge.client.event.ScreenEvent.KeyPressed.Pre(screen, keyCode, scanCode, modifiers)
        );
        boolean cancelled = cancelled1 || cancelled2;
        if (!cancelled) {
            // Fire Fabric ScreenKeyboardEvents.allowKeyPress
            try {
                boolean allowed = net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents.allowKeyPress(screen).invoker().allowKeyPress(screen, keyCode, scanCode, modifiers);
                if (!allowed) {
                    cancelled = true;
                }
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
        if (!cancelled) {
            // Fire Fabric ScreenKeyboardEvents.afterKeyPress
            try {
                net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents.afterKeyPress(screen).invoker().afterKeyPress(screen, keyCode, scanCode, modifiers);
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
        return cancelled;
    }

    public static boolean onScreenKeyReleasedPre(Screen screen, int keyCode, int scanCode, int modifiers) {
        // Fire NeoForge ScreenEvent.KeyReleased.Pre
        boolean cancelled1 = net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
            new net.neoforged.neoforge.client.event.ScreenEvent.KeyReleased.Pre(screen, keyCode, scanCode, modifiers)
        );
        // Fire Forge ScreenEvent.KeyReleased.Pre
        boolean cancelled2 = net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(
            new net.minecraftforge.client.event.ScreenEvent.KeyReleased.Pre(screen, keyCode, scanCode, modifiers)
        );
        boolean cancelled = cancelled1 || cancelled2;
        if (!cancelled) {
            // Fire Fabric ScreenKeyboardEvents.allowKeyRelease
            try {
                boolean allowed = net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents.allowKeyRelease(screen).invoker().allowKeyRelease(screen, keyCode, scanCode, modifiers);
                if (!allowed) {
                    cancelled = true;
                }
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
        if (!cancelled) {
            // Fire Fabric ScreenKeyboardEvents.afterKeyRelease
            try {
                net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents.afterKeyRelease(screen).invoker().afterKeyRelease(screen, keyCode, scanCode, modifiers);
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
        return cancelled;
    }

    public static boolean onScreenMouseClickedPre(Screen screen, double mouseX, double mouseY, int button) {
        // Fire NeoForge ScreenEvent.MouseButtonPressed.Pre
        boolean cancelled1 = net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
            new net.neoforged.neoforge.client.event.ScreenEvent.MouseButtonPressed.Pre(screen, mouseX, mouseY, button)
        );
        // Fire Forge ScreenEvent.MouseButtonPressed.Pre
        boolean cancelled2 = net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(
            new net.minecraftforge.client.event.ScreenEvent.MouseButtonPressed.Pre(screen, mouseX, mouseY, button)
        );
        boolean cancelled = cancelled1 || cancelled2;
        if (!cancelled) {
            // Fire Fabric ScreenMouseEvents.allowMouseClick
            try {
                boolean allowed = net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents.allowMouseClick(screen).invoker().allowMouseClick(screen, mouseX, mouseY, button);
                if (!allowed) {
                    cancelled = true;
                }
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
        if (!cancelled) {
            // Fire Fabric ScreenMouseEvents.afterMouseClick
            try {
                net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents.afterMouseClick(screen).invoker().afterMouseClick(screen, mouseX, mouseY, button);
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
        return cancelled;
    }

    @SuppressWarnings("unchecked")
    public static List<Component> onItemTooltip(ItemStack stack, Player player, List<Component> lines, TooltipContext context, TooltipFlag flag) {
        if (lines == null) {
            lines = new ArrayList<>();
        }
        
        // Post ItemTooltipEvent to the host ChainEventBus
        try {
            net.chainloader.api.event.ChainEventBus hostBus = net.chainloader.loader.compat.bridge.EventTranslatorBus.getInstance().getHostBus();
            if (hostBus != null) {
                hostBus.post(new net.chainloader.api.event.ItemTooltipEvent(stack, player, lines, flag));
            } else {
                // Fallback to direct post if host bus is not initialized yet
                net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
                    new net.neoforged.neoforge.event.entity.player.ItemTooltipEvent(stack, player, lines, flag)
                );
                net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(
                    new net.minecraftforge.event.entity.player.ItemTooltipEvent(stack, player, lines, flag)
                );
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }

        // C. Fire Fabric / ChainLoader API TooltipModify Event
        try {
            net.chainloader.api.client.render.ChainRenderEvents.TOOLTIP_MODIFY.invoker().onTooltipModify(stack, lines, context);
        } catch (Throwable t) {
            t.printStackTrace();
        }

        // D. Fire Fabric ItemTooltipCallback EVENT
        try {
            net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback.EVENT.invoker().getTooltip(stack, context, flag, lines);
        } catch (Throwable t) {
            t.printStackTrace();
        }

        return lines;
    }

    public static java.util.Map<Object, Integer> getEnchantments(ItemStack stack) {
        java.util.Map<Object, Integer> map = new java.util.HashMap<>();
        if (stack == null) return map;
        try {
            net.minecraft.world.item.enchantment.ItemEnchantments enchants = stack.getEnchantments();
            if (enchants != null) {
                for (java.util.Map.Entry<net.minecraft.core.Holder<net.minecraft.world.item.enchantment.Enchantment>, Integer> entry : enchants.entrySet()) {
                    net.minecraft.core.Holder<net.minecraft.world.item.enchantment.Enchantment> holder = entry.getKey();
                    if (holder != null) {
                        net.minecraft.world.item.enchantment.Enchantment value = holder.value();
                        if (value != null) {
                            map.put(value, entry.getValue());
                        }
                    }
                }
            }
            net.minecraft.world.item.enchantment.ItemEnchantments stored = stack.get(net.minecraft.core.component.DataComponents.STORED_ENCHANTMENTS);
            if (stored != null) {
                for (java.util.Map.Entry<net.minecraft.core.Holder<net.minecraft.world.item.enchantment.Enchantment>, Integer> entry : stored.entrySet()) {
                    net.minecraft.core.Holder<net.minecraft.world.item.enchantment.Enchantment> holder = entry.getKey();
                    if (holder != null) {
                        net.minecraft.world.item.enchantment.Enchantment value = holder.value();
                        if (value != null) {
                            map.put(value, entry.getValue());
                        }
                    }
                }
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
        return map;
    }

    public static void enchant(ItemStack stack, Object enchantmentObj, int level) {
        if (stack == null || enchantmentObj == null) {
            return;
        }
        try {
            net.minecraft.world.item.enchantment.Enchantment enchantment = (net.minecraft.world.item.enchantment.Enchantment) enchantmentObj;
            net.minecraft.core.Holder<net.minecraft.world.item.enchantment.Enchantment> holder = net.minecraft.core.Holder.direct(enchantment);
            stack.enchant(holder, level);
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    private static java.util.Map<String, Object> enchantmentCache = new java.util.concurrent.ConcurrentHashMap<>();

    private static Class<?> findClass(String deobfName, String obfName) throws ClassNotFoundException {
        try {
            return Class.forName(deobfName, true, Thread.currentThread().getContextClassLoader());
        } catch (ClassNotFoundException e) {
            return Class.forName(obfName, true, Thread.currentThread().getContextClassLoader());
        }
    }

    public static Object getRegistryAccess() {
        if (currentServer != null) {
            try {
                java.lang.reflect.Method m = null;
                for (String name : new String[]{"registryAccess", "dQ"}) {
                    try {
                        m = currentServer.getClass().getMethod(name);
                        break;
                    } catch (NoSuchMethodException e) {
                        // ignore
                    }
                }
                if (m != null) {
                    m.setAccessible(true);
                    return m.invoke(currentServer);
                }
            } catch (Exception e) {
                System.out.println("[EventBridgeHelper] Failed to get registryAccess from currentServer: " + e.toString());
            }
        }
        try {
            Class<?> mcClass = findClass("net.minecraft.client.Minecraft", "fgo");
            java.lang.reflect.Method mInstance = null;
            for (String name : new String[]{"getInstance", "Q"}) {
                try {
                    mInstance = mcClass.getMethod(name);
                    break;
                } catch (NoSuchMethodException e) {
                    // ignore
                }
            }
            if (mInstance != null) {
                mInstance.setAccessible(true);
                Object mcInstance = mInstance.invoke(null);
                if (mcInstance != null) {
                    java.lang.reflect.Field levelField = null;
                    for (String name : new String[]{"level", "r"}) {
                        try {
                            levelField = mcClass.getField(name);
                            break;
                        } catch (NoSuchFieldException e) {
                            // ignore
                        }
                    }
                    if (levelField != null) {
                        levelField.setAccessible(true);
                        Object levelObj = levelField.get(mcInstance);
                        if (levelObj != null) {
                            java.lang.reflect.Method mReg = null;
                            for (String name : new String[]{"registryAccess", "H_"}) {
                                try {
                                    mReg = levelObj.getClass().getMethod(name);
                                    break;
                                } catch (NoSuchMethodException e) {
                                    // ignore
                                }
                            }
                            if (mReg != null) {
                                mReg.setAccessible(true);
                                return mReg.invoke(levelObj);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            // ignore
        }
        return null;
    }

    public static Object getEnchantment(String fieldName) {
        return enchantmentCache.computeIfAbsent(fieldName, name -> {
            try {
                Class<?> enchantmentsClass = findClass("net.minecraft.world.item.enchantment.Enchantments", "dah");
                java.lang.reflect.Field field = enchantmentsClass.getDeclaredField(name);
                field.setAccessible(true);
                Object resourceKeyObj = field.get(null);
                if (resourceKeyObj == null) {
                    System.out.println("[EventBridgeHelper] Warning: Enchantment ResourceKey is null for field " + name);
                    return null;
                }

                Object registryAccess = getRegistryAccess();
                if (registryAccess == null) {
                    System.out.println("[EventBridgeHelper] Warning: RegistryAccess is null when looking up enchantment " + name);
                    return null;
                }

                Class<?> registriesClass = findClass("net.minecraft.core.registries.Registries", "lu");
                java.lang.reflect.Field enchKeyField = null;
                for (String fieldNameOpt : new String[]{"ENCHANTMENT", "aL"}) {
                    try {
                        enchKeyField = registriesClass.getDeclaredField(fieldNameOpt);
                        break;
                    } catch (NoSuchFieldException e) {
                        // ignore
                    }
                }
                if (enchKeyField == null) {
                    System.out.println("[EventBridgeHelper] Error: Could not find ENCHANTMENT field in Registries class");
                    return null;
                }
                enchKeyField.setAccessible(true);
                Object enchRegistryKey = enchKeyField.get(null);

                java.lang.reflect.Method registryOrThrowMethod = null;
                for (String methodNameOpt : new String[]{"registryOrThrow", "d"}) {
                    try {
                        registryOrThrowMethod = registryAccess.getClass().getMethod(methodNameOpt, enchRegistryKey.getClass());
                        break;
                    } catch (NoSuchMethodException e) {
                        // ignore
                    }
                }
                if (registryOrThrowMethod == null) {
                    for (java.lang.reflect.Method m : registryAccess.getClass().getMethods()) {
                        if ((m.getName().equals("registryOrThrow") || m.getName().equals("d")) && m.getParameterCount() == 1) {
                            registryOrThrowMethod = m;
                            break;
                        }
                    }
                }
                if (registryOrThrowMethod == null) {
                    System.out.println("[EventBridgeHelper] Error: Could not find registryOrThrow method in RegistryAccess");
                    return null;
                }
                registryOrThrowMethod.setAccessible(true);
                Object registry = registryOrThrowMethod.invoke(registryAccess, enchRegistryKey);

                java.lang.reflect.Method getMethod = null;
                for (String getOpt : new String[]{"get", "a"}) {
                    try {
                        getMethod = registry.getClass().getMethod(getOpt, resourceKeyObj.getClass());
                        break;
                    } catch (NoSuchMethodException e) {
                        // ignore
                    }
                }
                if (getMethod == null) {
                    for (java.lang.reflect.Method m : registry.getClass().getMethods()) {
                        if ((m.getName().equals("get") || m.getName().equals("a")) && m.getParameterCount() == 1) {
                            getMethod = m;
                            break;
                        }
                    }
                }
                if (getMethod == null) {
                    System.out.println("[EventBridgeHelper] Error: Could not find get method in Registry");
                    return null;
                }
                getMethod.setAccessible(true);
                Object enchantment = getMethod.invoke(registry, resourceKeyObj);
                if (enchantment == null) {
                    System.out.println("[EventBridgeHelper] Warning: Enchantment " + name + " lookup returned null");
                }
                return enchantment;
            } catch (Exception e) {
                System.out.println("[EventBridgeHelper] Error retrieving enchantment for field " + name + ": " + e.toString());
                return null;
            }
        });
    }


    /**
     * Called when the in-game HUD rendering occurs (e.g. at renderFood/renderHotbar)
     * to draw custom HUD layers registered by NeoForge/Fabric mods.
     */
    public static void onRenderGuiOverlays(GuiGraphics graphics, float partialTick) {
        // Render registered NeoForge GUI overlays
        List<net.neoforged.neoforge.client.event.RegisterGuiLayersEvent.RegisteredOverlay> overlays = 
            net.neoforged.neoforge.client.event.RegisterGuiLayersEvent.getRegisteredOverlays();
        
        if (overlays != null && !overlays.isEmpty()) {
            // Mock DeltaTracker parameter
            net.minecraft.client.DeltaTracker tracker = new net.minecraft.client.DeltaTracker() {
                @Override
                public float getGameTimeDeltaTicks() { return partialTick; }
                @Override
                public float getFrameDelayTicks() { return partialTick; }
                @Override
                public float getRealtimeDeltaTicks() { return partialTick; }
            };
            
            for (net.neoforged.neoforge.client.event.RegisterGuiLayersEvent.RegisteredOverlay entry : overlays) {
                try {
                    entry.overlay.render(graphics, tracker);
                } catch (Exception e) {
                    System.err.println("Error rendering GUI Overlay '" + entry.name + "':");
                    e.printStackTrace();
                }
            }
        }

        // Trigger Fabric HudRenderCallback
        try {
            net.chainloader.api.client.render.ChainRenderEvents.HUD_RENDER.invoker().onHudRender(graphics, partialTick);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Fires RegisterEvent to all mod event buses for key registries.
     * This MUST be called before registry freeze (lt.c()V) to allow mods
     * that register via @EventBusSubscriber / RegisterEvent to succeed.
     * Idempotent — safe to call multiple times; only the first call fires events.
     */
    private static volatile boolean registerEventsFired = false;
    public static void fireRegisterEvents() {
        if (registerEventsFired) {
            System.out.println("[ChainLoader] RegisterEvents already fired, skipping.");
            return;
        }
        registerEventsFired = true;
        System.out.println("[ChainLoader] Firing RegisterEvents to all mod event buses (before registry freeze)...");

        java.util.Set<Object> buses = net.chainloader.loader.core.ModScanner.getModEventBuses();
        
        net.minecraft.resources.ResourceKey<?>[] registriesToFire = {
            net.minecraft.core.registries.Registries.BLOCK,
            net.minecraft.core.registries.Registries.ITEM,
            net.minecraft.core.registries.Registries.BLOCK_ENTITY_TYPE,
            net.minecraft.core.registries.Registries.CREATIVE_MODE_TAB
        };
        for (Object bus : buses) {
            String className = bus.getClass().getName();
            if (className.contains("neoforged")) {
                for (net.minecraft.resources.ResourceKey<?> key : registriesToFire) {
                    try {
                        postToBus(bus, new net.neoforged.neoforge.registries.RegisterEvent((net.minecraft.resources.ResourceKey) key));
                    } catch (Throwable e) {
                        System.err.println("[ChainLoader] Forge RegisterEvent: Failed during registration for registry " + key + ":");
                        e.printStackTrace();
                    }
                }
            } else if (className.contains("minecraftforge")) {
                for (net.minecraft.resources.ResourceKey<?> key : registriesToFire) {
                    // 1. Fire modern RegisterEvent
                    try {
                        postToBus(bus, new net.minecraftforge.registries.RegisterEvent((net.minecraft.resources.ResourceKey) key));
                    } catch (Throwable e) {
                        System.err.println("[ChainLoader] Forge RegisterEvent: Failed during registration for registry " + key + ":");
                        e.printStackTrace();
                    }
                    // 2. Fire legacy RegistryEvent.Register
                    try {
                        net.minecraftforge.registries.IForgeRegistry<?> forgeReg = createForgeRegistry((net.minecraft.resources.ResourceKey) key);
                        if (forgeReg != null) {
                            postToBus(bus, new net.minecraftforge.event.RegistryEvent.Register<>(forgeReg));
                        }
                    } catch (Throwable e) {
                        System.err.println("[ChainLoader] Forge RegistryEvent.Register: Failed during registration for registry " + key + ":");
                        e.printStackTrace();
                    }
                }
            }
        }

        // Process unified core registries from ChainRegistryBridge
        while (!net.chainloader.loader.core.registry.ChainRegistryBridge.getPendingEntries().isEmpty()) {
            net.chainloader.loader.core.registry.ChainRegistryBridge.RegistryEntry entry = 
                net.chainloader.loader.core.registry.ChainRegistryBridge.getPendingEntries().poll();
            if (entry != null) {
                try {
                    System.out.println("[ChainLoader] Processing core registry entry: " + entry.registryName + " -> " + entry.entryId);
                    net.minecraft.resources.ResourceLocation registryId = entry.registryName.contains(":") 
                        ? new net.minecraft.resources.ResourceLocation(entry.registryName) 
                        : new net.minecraft.resources.ResourceLocation("minecraft", entry.registryName);
                    net.minecraft.core.WritableRegistry<?> registry = (net.minecraft.core.WritableRegistry<?>) 
                        net.minecraft.core.registries.BuiltInRegistries.REGISTRY.get(registryId);
                    if (registry != null) {
                        net.minecraft.resources.ResourceLocation entryId = new net.minecraft.resources.ResourceLocation(entry.entryId);
                        net.minecraft.core.Registry.register(
                            (net.minecraft.core.Registry) registry,
                            entryId,
                            entry.value
                        );
                        System.out.println("[ChainLoader] Successfully registered " + entry.entryId + " to registry: " + entry.registryName);
                    } else {
                        System.err.println("[ChainLoader] Core registry not found: " + entry.registryName);
                    }
                } catch (Throwable t) {
                    System.err.println("[ChainLoader] Failed to process core registry entry: " + entry.entryId);
                    t.printStackTrace();
                }
            }
        }

        System.out.println("[ChainLoader] RegisterEvents fired successfully.");
        populateModBlockStates();
    }

    @SuppressWarnings("unchecked")
    private static <T> net.minecraftforge.registries.IForgeRegistry<T> createForgeRegistry(net.minecraft.resources.ResourceKey<net.minecraft.core.Registry<T>> key) {
        net.minecraft.core.Registry<T> registry = getRegistry(key);
        if (registry == null) return null;
        return new net.minecraftforge.registries.IForgeRegistry<T>() {
            @Override
            public void register(T value) {
                net.minecraft.resources.ResourceLocation name = net.chainloader.loader.compat.bridge.RegistryHelper.getRegistryName(value);
                if (name == null) {
                    System.err.println("[ChainLoader] Warning: Tried to register legacy object without registry name: " + value);
                    return;
                }
                registerCleanly(registry, name, value);
                System.out.println("[ChainLoader] Legacy RegistryEvent: Registered " + name + " to registry " + registry.key().location());
            }

            @Override
            public void registerAll(T... values) {
                for (T val : values) {
                    register(val);
                }
            }

            @Override
            public net.minecraft.resources.ResourceLocation getKey(T value) {
                return net.chainloader.loader.compat.bridge.RegistryHelper.getRegistryName(value);
            }

            @Override
            public java.util.Iterator<T> iterator() {
                try {
                    if (registry instanceof Iterable) {
                        return ((Iterable<T>) (Object) registry).iterator();
                    }
                } catch (Throwable t) {}
                return java.util.Collections.emptyIterator();
            }
        };
    }

    @SuppressWarnings("unchecked")
    public static <T> net.minecraft.core.Registry<T> getRegistry(net.minecraft.resources.ResourceKey<? extends net.minecraft.core.Registry<T>> registryKey) {
        try {
            if (net.minecraft.core.registries.BuiltInRegistries.WRITABLE_REGISTRY != null) {
                net.minecraft.core.Registry<T> reg = (net.minecraft.core.Registry<T>) net.minecraft.core.registries.BuiltInRegistries.WRITABLE_REGISTRY.get((net.minecraft.resources.ResourceKey) registryKey);
                if (reg != null) {
                    return reg;
                }
            }
        } catch (Throwable t) {
            // Ignore and fallback
        }
        try {
            Class<?> builtinClass = Class.forName("net.minecraft.core.registries.BuiltInRegistries");
            for (java.lang.reflect.Field field : builtinClass.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                    field.setAccessible(true);
                    Object val = field.get(null);
                    if (val instanceof net.minecraft.core.Registry) {
                        net.minecraft.core.Registry<?> root = (net.minecraft.core.Registry<?>) val;
                        if (root.key().equals(registryKey)) {
                            return (net.minecraft.core.Registry<T>) root;
                        }
                        if ("minecraft:root".equals(root.key().location().toString())) {
                            net.minecraft.core.Registry<T> reg = (net.minecraft.core.Registry<T>) ((net.minecraft.core.Registry) root).get((net.minecraft.resources.ResourceKey) registryKey);
                            if (reg != null) {
                                return reg;
                            }
                        }
                    }
                }
            }
        } catch (Throwable t) {
            // Ignore
        }
        return null;
    }

    public static void unfreezeAndEnableIntrusive(Object registry, String name) {
        try {
            Class<?> clazz = registry.getClass();
            while (clazz != null && clazz != Object.class) {
                // 1. Find and set the frozen field (boolean, or named "frozen" or "l")
                java.lang.reflect.Field frozenField = null;
                try {
                    frozenField = clazz.getDeclaredField("frozen");
                } catch (NoSuchFieldException e1) {
                    try {
                        frozenField = clazz.getDeclaredField("l");
                    } catch (NoSuchFieldException e2) {
                        for (java.lang.reflect.Field f : clazz.getDeclaredFields()) {
                            if (f.getType() == boolean.class) {
                                frozenField = f;
                                break;
                            }
                        }
                    }
                }
                if (frozenField != null) {
                    try {
                        frozenField.setAccessible(true);
                        if (frozenField.getBoolean(registry)) {
                            System.out.println("[ChainLoader] Unfreezing registry " + clazz.getName() + " for " + name);
                            frozenField.setBoolean(registry, false);
                        }
                    } catch (Throwable t) {
                        // Ignore
                    }
                }

                // 2. Find and set the intrusiveHolders field (Map, not final/static, or named "intrusiveHolders" or "m")
                java.lang.reflect.Field intrusiveHoldersField = null;
                try {
                    intrusiveHoldersField = clazz.getDeclaredField("intrusiveHolders");
                } catch (NoSuchFieldException e1) {
                    try {
                        intrusiveHoldersField = clazz.getDeclaredField("m");
                    } catch (NoSuchFieldException e2) {
                        for (java.lang.reflect.Field f : clazz.getDeclaredFields()) {
                            if (java.util.Map.class.isAssignableFrom(f.getType())) {
                                int mod = f.getModifiers();
                                if (!java.lang.reflect.Modifier.isStatic(mod) && !java.lang.reflect.Modifier.isFinal(mod)) {
                                    intrusiveHoldersField = f;
                                    break;
                                }
                            }
                        }
                    }
                }
                if (intrusiveHoldersField != null) {
                    try {
                        intrusiveHoldersField.setAccessible(true);
                        Object existing = intrusiveHoldersField.get(registry);
                        if (existing == null) {
                            boolean needsIntrusive = false;
                            if (registry instanceof net.minecraft.core.Registry) {
                                String regPath = ((net.minecraft.core.Registry<?>) registry).key().location().getPath();
                                if (regPath.equals("block") || regPath.equals("item") || regPath.equals("fluid") || regPath.equals("entity_type") || regPath.equals("game_event")) {
                                    needsIntrusive = true;
                                }
                            } else {
                                String regStr = registry.toString().toLowerCase();
                                if (regStr.contains("block") || regStr.contains("item") || regStr.contains("fluid") || regStr.contains("entity") || regStr.contains("game_event")) {
                                    needsIntrusive = true;
                                }
                            }
                            if (needsIntrusive) {
                                System.out.println("[ChainLoader] Enabling intrusive holders on registry " + clazz.getName() + " (field: " + intrusiveHoldersField.getName() + ") for " + name);
                                intrusiveHoldersField.set(registry, new java.util.IdentityHashMap<>());
                            }
                        }
                    } catch (Throwable t) {
                        // Ignore
                    }
                }
                
                clazz = clazz.getSuperclass();
            }
        } catch (Throwable t) {
            System.err.println("[ChainLoader] Error in unfreezeAndEnableIntrusive for " + name + ":");
            t.printStackTrace();
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> void registerCleanly(net.minecraft.core.Registry<T> registry, net.minecraft.resources.ResourceLocation name, T value) {
        unfreezeAndEnableIntrusive(registry, name.toString());

        net.minecraft.core.Registry.register(registry, name, value);

        try {
            // Also notify Fabric API port that an entry has been added
            net.chainloader.loader.compat.fabric.FabricLoaderShim.getInstance().dispatchEvent("fabric:registry_entry_added", listener -> {
                try {
                    Class<?> callbackClass = Class.forName("net.chainloader.loader.compat.bridge.RegistrySynchronizer$FabricRegistryEntryCallback");
                    java.lang.reflect.Method m = callbackClass.getMethod("onEntryAdded", String.class, String.class, Object.class);
                    m.invoke(listener, registry.key().location().toString(), name.toString(), value);
                } catch (Throwable t) {
                    // Ignore
                }
            });
        } catch (Throwable t) {
            // Ignore
        }
    }

    public static Object[] patchPackRepositorySources(Object[] sources) {
        try {
            System.out.println("[ChainLoader] Intercepting PackRepository construction to inject virtual pack source...");
            return net.chainloader.loader.compat.asset.VanillaAssetPatcher.getInstance().createRepositorySources(sources);
        } catch (Throwable t) {
            System.err.println("[ChainLoader] Failed to patch PackRepository sources:");
            t.printStackTrace();
            return sources;
        }
    }

    public static void onMinecraftInit(Object minecraft) {
        net.chainloader.loader.core.gui.EarlyLoadingScreen.getInstance().updateProgress(90, "Initializing client lifecycle...");
        System.out.println("[ChainLoader] onMinecraftInit: Initializing client lifecycle setup...");

        // Populate and inject virtual resource pack for mod jar resources
        try {
            net.chainloader.loader.compat.asset.VanillaAssetPatcher.getInstance().populateModResources();
            Object packRepository = null;
            try {
                // Try getter: getResourcePackRepository() -> ac()
                java.lang.reflect.Method getRepo = minecraft.getClass().getMethod("ac");
                packRepository = getRepo.invoke(minecraft);
            } catch (NoSuchMethodException e) {
                try {
                    // Try field: resourcePackRepository -> aj
                    java.lang.reflect.Field repoField = minecraft.getClass().getDeclaredField("aj");
                    repoField.setAccessible(true);
                    packRepository = repoField.get(minecraft);
                } catch (NoSuchFieldException ex) {
                    System.err.println("[ChainLoader] Could not find PackRepository on Minecraft class via reflection.");
                }
            }
            if (packRepository != null) {
                net.chainloader.loader.compat.asset.VanillaAssetPatcher.getInstance().injectVirtualPackToRepository(packRepository);
                System.out.println("[ChainLoader] Successfully injected virtual resource packs into PackRepository.");
            }
        } catch (Throwable t) {
            System.err.println("[ChainLoader] Failed to inject virtual resource packs:");
            t.printStackTrace();
        }


        // 1. Fetch mod event buses from ModScanner
        java.util.Set<Object> buses = net.chainloader.loader.core.ModScanner.getModEventBuses();
        System.out.println("[ChainLoader] Posting setup events to " + buses.size() + " mod event buses...");

        // 1a. Fire RegisterEvents (idempotent — may have already been fired from ModScanner.initializeMods before freeze)
        registerPayloadHandlers(buses);
        fireRegisterEvents();

        net.chainloader.loader.core.gui.EarlyLoadingScreen.getInstance().updateProgress(92, "Posting mod lifecycle setup events...");
        Object registerRenderersEvent = instantiateEventReflectively(net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers.class);
        Object registerLayerDefsEvent = instantiateEventReflectively(net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterLayerDefinitions.class);

        for (Object bus : buses) {
            String className = bus.getClass().getName();
            if (className.contains("neoforged")) {
                System.out.println("  Posting NeoForge setup events to: " + className);
                try {
                    postToBus(bus, instantiateEventReflectively(net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent.class));
                    postToBus(bus, instantiateEventReflectively(net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent.class));
                    postToBus(bus, instantiateEventReflectively(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent.class));
                    postToBus(bus, instantiateEventReflectively(net.neoforged.neoforge.client.event.RegisterGuiLayersEvent.class));
                    postToBus(bus, instantiateEventReflectively(net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent.class));
                    postToBus(bus, instantiateEventReflectively(net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent.class));
                    
                    // Fire NeoForge EntityRenderersEvent.RegisterRenderers and RegisterLayerDefinitions
                    if (registerRenderersEvent != null) postToBus(bus, registerRenderersEvent);
                    if (registerLayerDefsEvent != null) postToBus(bus, registerLayerDefsEvent);
                } catch (Throwable e) {
                    System.err.println("  Failed to post NeoForge lifecycle events:");
                    e.printStackTrace();
                }
            } else if (className.contains("minecraftforge")) {
                System.out.println("  Posting Forge setup events to: " + className);
                try {
                    postToBus(bus, instantiateEventReflectively(net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent.class));
                    postToBus(bus, instantiateEventReflectively(net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent.class));
                    postToBus(bus, instantiateEventReflectively(net.minecraftforge.client.event.RegisterKeyMappingsEvent.class));
                } catch (Throwable e) {
                    System.err.println("  Failed to post Forge lifecycle events:");
                    e.printStackTrace();
                }
            }
        }

        // Register Entity Renderers captured in the event using reflection
        for (java.util.Map.Entry<net.minecraft.world.entity.EntityType<?>, net.minecraft.client.renderer.entity.EntityRendererProvider<?>> entry : registerRenderersEvent.getEntityRenderers().entrySet()) {
            try {
                System.out.println("[ChainLoader] Registering NeoForge entity renderer for: " + entry.getKey());
                Class<?> renderersClass = Class.forName("net.minecraft.client.renderer.entity.EntityRenderers");
                java.lang.reflect.Method registerMethod;
                try {
                    registerMethod = renderersClass.getMethod("register", net.minecraft.world.entity.EntityType.class, net.minecraft.client.renderer.entity.EntityRendererProvider.class);
                } catch (NoSuchMethodException e) {
                    registerMethod = renderersClass.getMethod("a", net.minecraft.world.entity.EntityType.class, net.minecraft.client.renderer.entity.EntityRendererProvider.class);
                }
                registerMethod.setAccessible(true);
                registerMethod.invoke(null, entry.getKey(), entry.getValue());
            } catch (Throwable t) {
                System.err.println("[ChainLoader] Failed to register entity renderer for " + entry.getKey() + " via reflection:");
                t.printStackTrace();
            }
        }

        // Register Block Entity Renderers captured in the event using reflection
        for (java.util.Map.Entry<net.minecraft.world.level.block.entity.BlockEntityType<?>, net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider<?>> entry : registerRenderersEvent.getBlockEntityRenderers().entrySet()) {
            try {
                System.out.println("[ChainLoader] Registering NeoForge block entity renderer for: " + entry.getKey());
                Class<?> renderersClass = Class.forName("net.minecraft.client.renderer.blockentity.BlockEntityRenderers");
                java.lang.reflect.Method registerMethod;
                try {
                    registerMethod = renderersClass.getMethod("register", net.minecraft.world.level.block.entity.BlockEntityType.class, net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider.class);
                } catch (NoSuchMethodException e) {
                    registerMethod = renderersClass.getMethod("a", net.minecraft.world.level.block.entity.BlockEntityType.class, net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider.class);
                }
                registerMethod.setAccessible(true);
                registerMethod.invoke(null, entry.getKey(), entry.getValue());
            } catch (Throwable t) {
                System.err.println("[ChainLoader] Failed to register block entity renderer for " + entry.getKey() + " via reflection:");
                t.printStackTrace();
            }
        }

        // Save registered layer definitions for model reloading merging
        customLayerDefinitions.putAll(registerLayerDefsEvent.getLayerDefinitions());

        // 2. Instantiate and call Fabric "client" entrypoints
        net.chainloader.loader.core.gui.EarlyLoadingScreen.getInstance().updateProgress(95, "Initializing Fabric client entrypoints...");
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        for (net.chainloader.loader.core.ModScanner.DiscoveredMod mod : net.chainloader.loader.core.ModScanner.getDiscoveredMods()) {
            if ("fabric".equals(mod.metadata.getOriginalLoaderType())) {
                java.util.List<String> clientEPs = mod.metadata.getEntrypoints().get("client");
                if (clientEPs != null) {
                    for (String epClass : clientEPs) {
                        try {
                            System.out.println("  Initializing Fabric client entrypoint: " + epClass + " for mod: " + mod.metadata.getId());
                            // Log that we are initializing client entrypoint
                            net.chainloader.loader.core.gui.EarlyLoadingScreen.getInstance().log(mod.metadata.getId(), "initializing client entrypoint");
                            Class<?> clazz = Class.forName(epClass, true, cl);
                            Object instance = clazz.getDeclaredConstructor().newInstance();
                            if (instance instanceof net.fabricmc.api.ClientModInitializer) {
                                // Log that we are running onInitializeClient
                                net.chainloader.loader.core.gui.EarlyLoadingScreen.getInstance().log(mod.metadata.getId(), "running onInitializeClient");
                                ((net.fabricmc.api.ClientModInitializer) instance).onInitializeClient();
                                System.out.println("  Successfully initialized client entrypoint " + epClass);
                            }
                        } catch (Throwable e) {
                            System.err.println("  Failed to initialize Fabric client entrypoint " + epClass + ":");
                            e.printStackTrace();
                            // Log error using logError helper
                            net.chainloader.loader.core.gui.EarlyLoadingScreen.getInstance().logError(mod.metadata.getId(), "Failed to initialize client entrypoint " + epClass, e);
                        }
                    }
                }
            }
        }

        // 3. Fire Architectury's CLIENT_SETUP event
        net.chainloader.loader.core.gui.EarlyLoadingScreen.getInstance().updateProgress(98, "Starting client lifecycle...");
        try {
            net.minecraft.client.Minecraft mcInstance = (net.minecraft.client.Minecraft) minecraft;
            net.chainloader.loader.compat.lib.ArchitecturyApiBridge.ClientLifecycleEvent.CLIENT_SETUP.invoker().setup(mcInstance);
            System.out.println("  Successfully fired Architectury's CLIENT_SETUP event");
        } catch (Throwable t) {
            System.err.println("  Failed to fire Architectury's CLIENT_SETUP event:");
            t.printStackTrace();
        }

        // 4. Fire Fabric ClientLifecycleEvents.CLIENT_STARTED event
        try {
            net.minecraft.client.Minecraft mcInstance = (net.minecraft.client.Minecraft) minecraft;
            net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STARTED.invoker().onClientStarted(mcInstance);
            System.out.println("  Successfully fired Fabric ClientLifecycleEvents.CLIENT_STARTED event");
        } catch (Throwable t) {
            System.err.println("  Failed to fire Fabric ClientLifecycleEvents.CLIENT_STARTED event:");
            t.printStackTrace();
        }

        // 5. Inject custom key mappings into Minecraft options
        injectKeyMappings(minecraft);

        // Done with early loading, progress to 100% (Minecraft will close the loading screen when TitleScreen initializes)
        net.chainloader.loader.core.gui.EarlyLoadingScreen.getInstance().updateProgress(100, "Minecraft client initialized.");
    }

    public static final List<net.minecraft.client.KeyMapping> customKeyMappings = new java.util.ArrayList<>();

    public static void registerKeyMapping(net.minecraft.client.KeyMapping keyMapping) {
        if (keyMapping != null && !customKeyMappings.contains(keyMapping)) {
            customKeyMappings.add(keyMapping);
            System.out.println("[ChainLoader] Registered custom key mapping: " + keyMapping.getName());
            try {
                net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                if (mc != null && mc.options != null && mc.options.keyMappings != null) {
                    injectKeyMappings(mc);
                }
            } catch (Throwable t) {
                // Ignore if called too early
            }
        }
    }

    public static void injectKeyMappings(Object minecraft) {
        try {
            net.minecraft.client.Minecraft mc = (net.minecraft.client.Minecraft) minecraft;
            if (mc.options == null) {
                System.out.println("[ChainLoader] Minecraft options are null, cannot inject key mappings.");
                return;
            }
            
            net.minecraft.client.KeyMapping[] original = null;
            java.lang.reflect.Field keyMappingsField = null;
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

            if (original == null) {
                original = new net.minecraft.client.KeyMapping[0];
            }
            
            java.util.List<net.minecraft.client.KeyMapping> toAdd = new java.util.ArrayList<>();
            for (net.minecraft.client.KeyMapping custom : customKeyMappings) {
                boolean exists = false;
                for (net.minecraft.client.KeyMapping orig : original) {
                    if (orig == custom) {
                        exists = true;
                        break;
                    }
                }
                if (!exists) {
                    toAdd.add(custom);
                }
            }
            
            if (toAdd.isEmpty()) {
                System.out.println("[ChainLoader] No new key mappings to inject.");
                return;
            }
            
            net.minecraft.client.KeyMapping[] newArray = new net.minecraft.client.KeyMapping[original.length + toAdd.size()];
            System.arraycopy(original, 0, newArray, 0, original.length);
            for (int i = 0; i < toAdd.size(); i++) {
                newArray[original.length + i] = toAdd.get(i);
            }
            
            if (keyMappingsField != null) {
                java.lang.reflect.Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
                unsafeField.setAccessible(true);
                sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeField.get(null);
                long offset = unsafe.objectFieldOffset(keyMappingsField);
                unsafe.putObject(mc.options, offset, newArray);
            } else {
                throw new NoSuchFieldException("Could not find keyMappings field");
            }
            
            mc.options.load();
            System.out.println("[ChainLoader] Successfully injected " + toAdd.size() + " custom key mappings into Minecraft options.");
        } catch (Throwable t) {
            System.err.println("[ChainLoader] Failed to inject custom key mappings:");
            t.printStackTrace();
        }
    }

    public static int drawInBatchBridge(net.minecraft.client.gui.Font font, com.mojang.blaze3d.vertex.PoseStack poseStack, net.minecraft.util.FormattedCharSequence text, float x, float y, int color) {
        try {
            org.joml.Matrix4f matrix = poseStack != null && poseStack.last() != null ? poseStack.last().pose() : new org.joml.Matrix4f();
            net.minecraft.client.renderer.MultiBufferSource buffer = null;
            try {
                net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                if (mc != null && mc.renderBuffers() != null) {
                    buffer = mc.renderBuffers().bufferSource();
                }
            } catch (Throwable t) {
                // ignore
            }
            if (buffer == null) {
                return 0;
            }
            net.minecraft.client.gui.Font.DisplayMode mode = net.minecraft.client.gui.Font.DisplayMode.NORMAL;
            return font.drawInBatch(text, x, y, color, false, matrix, buffer, mode, 0, 15728880);
        } catch (Throwable t) {
            t.printStackTrace();
            return 0;
        }
    }

    public static int drawInBatchComponentBridge(net.minecraft.client.gui.Font font, com.mojang.blaze3d.vertex.PoseStack poseStack, net.minecraft.network.chat.Component text, float x, float y, int color) {
        try {
            org.joml.Matrix4f matrix = poseStack != null && poseStack.last() != null ? poseStack.last().pose() : new org.joml.Matrix4f();
            net.minecraft.client.renderer.MultiBufferSource buffer = null;
            try {
                net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                if (mc != null && mc.renderBuffers() != null) {
                    buffer = mc.renderBuffers().bufferSource();
                }
            } catch (Throwable t) {
                // ignore
            }
            if (buffer == null) {
                return 0;
            }
            net.minecraft.client.gui.Font.DisplayMode mode = net.minecraft.client.gui.Font.DisplayMode.NORMAL;
            return font.drawInBatch(text, x, y, color, false, matrix, buffer, mode, 0, 15728880);
        } catch (Throwable t) {
            t.printStackTrace();
            return 0;
        }
    }

    public static net.minecraft.core.BlockPos readBlockPosBridge(net.minecraft.nbt.CompoundTag tag) {
        if (tag == null) {
            return net.minecraft.core.BlockPos.ZERO;
        }
        int x = tag.getInt("X");
        int y = tag.getInt("Y");
        int z = tag.getInt("Z");
        return new net.minecraft.core.BlockPos(x, y, z);
    }

    public static net.minecraft.nbt.CompoundTag writeBlockPosBridge(net.minecraft.core.BlockPos pos) {
        if (pos == null) {
            return new net.minecraft.nbt.CompoundTag();
        }
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        tag.putInt("X", pos.getX());
        tag.putInt("Y", pos.getY());
        tag.putInt("Z", pos.getZ());
        return tag;
    }

    @SuppressWarnings("unchecked")
    public static java.util.Map onEntityModelSetReload(java.util.Map originalRoots) {
        System.out.println("[ChainLoader] Intercepting EntityModelSet reload...");
        java.util.Map merged = new java.util.HashMap(originalRoots);

        // 1. Merge Balm registered model layers
        try {
            Class<?> balmModelLayers = Class.forName("net.blay09.mods.balm.fabric.client.rendering.FabricModelLayers");
            java.lang.reflect.Method createRoots = balmModelLayers.getMethod("createRoots");
            java.util.Map balmRoots = (java.util.Map) createRoots.invoke(null);
            if (balmRoots != null) {
                System.out.println("[ChainLoader]   Merging " + balmRoots.size() + " model layers from Balm.");
                merged.putAll(balmRoots);
            }
        } catch (ClassNotFoundException e) {
            // Balm not present, ignore
        } catch (Throwable t) {
            System.err.println("Failed to merge Balm model layers:");
            t.printStackTrace();
        }

        // 2. Merge general Fabric EntityModelLayerRegistry registered model layers
        try {
            Class<?> registry = Class.forName("net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry");
            java.lang.reflect.Method createRoots = registry.getMethod("createRoots");
            java.util.Map fabricRoots = (java.util.Map) createRoots.invoke(null);
            if (fabricRoots != null) {
                System.out.println("[ChainLoader]   Merging " + fabricRoots.size() + " model layers from EntityModelLayerRegistry.");
                merged.putAll(fabricRoots);
            }
        } catch (ClassNotFoundException e) {
            // Registry not present, ignore
        } catch (Throwable t) {
            System.err.println("Failed to merge Fabric model layers:");
            t.printStackTrace();
        }

        // 3. Merge custom NeoForge layer definitions captured in RegisterLayerDefinitions event
        if (!customLayerDefinitions.isEmpty()) {
            System.out.println("[ChainLoader]   Merging " + customLayerDefinitions.size() + " model layers from NeoForge RegisterLayerDefinitions event.");
            for (java.util.Map.Entry<net.minecraft.client.model.geom.ModelLayerLocation, java.util.function.Supplier<net.minecraft.client.model.geom.builders.LayerDefinition>> entry : customLayerDefinitions.entrySet()) {
                try {
                    if (entry.getKey() != null && entry.getValue() != null) {
                        merged.put(entry.getKey(), entry.getValue().get());
                    }
                } catch (Throwable t) {
                    System.err.println("[ChainLoader] Failed to merge NeoForge layer definition for " + entry.getKey() + ":");
                    t.printStackTrace();
                }
            }
        }

        return merged;
    }

    private static Object currentServer = null;

    public static void setCurrentServer(Object server) {
        currentServer = server;
    }

    public static Object getCurrentServer() {
        return currentServer;
    }

    public static net.minecraft.core.HolderLookup.Provider getRegistries() {
        if (currentServer != null) {
            return ((net.minecraft.server.MinecraftServer) currentServer).registryAccess();
        }
        try {
            net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
            if (client != null && client.level != null) {
                return client.level.registryAccess();
            }
        } catch (Throwable t) {
            // Ignore
        }
        return net.minecraft.core.RegistryAccess.EMPTY;
    }

    public static class TrackedCompoundTag extends net.minecraft.nbt.CompoundTag {
        private final net.minecraft.world.item.ItemStack stack;

        public TrackedCompoundTag(net.minecraft.world.item.ItemStack stack, net.minecraft.nbt.CompoundTag src) {
            this.stack = stack;
            if (src != null) {
                for (String key : src.getAllKeys()) {
                    super.put(key, src.get(key).copy());
                }
            }
        }

        private void update() {
            stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(this));
        }

        @Override
        public net.minecraft.nbt.Tag put(String key, net.minecraft.nbt.Tag value) {
            net.minecraft.nbt.Tag res = super.put(key, value);
            update();
            return res;
        }

        @Override
        public void putByte(String key, byte value) {
            super.putByte(key, value);
            update();
        }

        @Override
        public void putShort(String key, short value) {
            super.putShort(key, value);
            update();
        }

        @Override
        public void putInt(String key, int value) {
            super.putInt(key, value);
            update();
        }

        @Override
        public void putLong(String key, long value) {
            super.putLong(key, value);
            update();
        }

        @Override
        public void putFloat(String key, float value) {
            super.putFloat(key, value);
            update();
        }

        @Override
        public void putDouble(String key, double value) {
            super.putDouble(key, value);
            update();
        }

        @Override
        public void putString(String key, String value) {
            super.putString(key, value);
            update();
        }

        @Override
        public void putByteArray(String key, byte[] value) {
            super.putByteArray(key, value);
            update();
        }

        @Override
        public void putIntArray(String key, int[] value) {
            super.putIntArray(key, value);
            update();
        }

        @Override
        public void putLongArray(String key, long[] value) {
            super.putLongArray(key, value);
            update();
        }

        @Override
        public void putBoolean(String key, boolean value) {
            super.putBoolean(key, value);
            update();
        }

        @Override
        public void remove(String key) {
            super.remove(key);
            update();
        }

        @Override
        public void putUUID(String key, java.util.UUID value) {
            super.putUUID(key, value);
            update();
        }
    }

    public static Object getItemStackNbt(Object stackObj) {
        if (stackObj == null) {
            return null;
        }
        if (stackObj instanceof net.minecraft.world.item.ItemStack) {
            net.minecraft.world.item.ItemStack stack = (net.minecraft.world.item.ItemStack) stackObj;
            net.minecraft.world.item.component.CustomData customData = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
            net.minecraft.nbt.CompoundTag tag = null;
            if (customData != null) {
                tag = customData.copyTag();
                if (tag.contains("chainloader_nbt_compat")) {
                    tag.remove("chainloader_nbt_compat");
                }
            } else {
                return null;
            }
            return new TrackedCompoundTag(stack, tag);
        }
        return null;
    }

    public static boolean hasNbt(Object stackObj) {
        if (stackObj == null) return false;
        if (stackObj instanceof net.minecraft.world.item.ItemStack) {
            net.minecraft.world.item.ItemStack stack = (net.minecraft.world.item.ItemStack) stackObj;
            net.minecraft.world.item.component.CustomData customData = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
            return customData != null;
        }
        return false;
    }

    public static Object getOrCreateSubNbt(Object stackObj, String key) {
        if (stackObj == null) {
            return null;
        }
        if (stackObj instanceof net.minecraft.world.item.ItemStack) {
            net.minecraft.world.item.ItemStack stack = (net.minecraft.world.item.ItemStack) stackObj;
            net.minecraft.world.item.component.CustomData customData = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
            net.minecraft.nbt.CompoundTag tag;
            if (customData == null) {
                tag = new net.minecraft.nbt.CompoundTag();
            } else {
                tag = customData.copyTag();
            }
            net.minecraft.nbt.CompoundTag subTag;
            if (tag.contains(key, 10)) {
                subTag = tag.getCompound(key);
            } else {
                subTag = new net.minecraft.nbt.CompoundTag();
                tag.put(key, subTag);
            }
            stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(tag));
            return subTag;
        }
        return null;
    }

    public static Object fromNbt(Object nbtObj) {
        if (nbtObj == null) {
            return null;
        }
        net.minecraft.nbt.CompoundTag nbt = (net.minecraft.nbt.CompoundTag) nbtObj;
        net.minecraft.core.HolderLookup.Provider registries = getRegistries();
        if (registries != null) {
            return net.minecraft.world.item.ItemStack.parseOptional(registries, nbt);
        }
        return null;
    }

    public static Object writeNbt(Object stackObj, Object nbtObj) {
        if (stackObj == null || nbtObj == null) {
            return nbtObj;
        }
        if (stackObj instanceof net.minecraft.world.item.ItemStack) {
            net.minecraft.world.item.ItemStack stack = (net.minecraft.world.item.ItemStack) stackObj;
            net.minecraft.nbt.CompoundTag nbt = (net.minecraft.nbt.CompoundTag) nbtObj;
            net.minecraft.core.HolderLookup.Provider registries = getRegistries();
            if (registries != null) {
                return stack.save(registries, nbt);
            }
        }
        return nbtObj;
    }

    public static void setItemStackNbt(Object stackObj, Object nbtObj) {
        if (stackObj instanceof net.minecraft.world.item.ItemStack) {
            net.minecraft.world.item.ItemStack stack = (net.minecraft.world.item.ItemStack) stackObj;
            System.out.println("[EventBridgeHelper] setItemStackNbt: stack=" + stack + " (hash=" + System.identityHashCode(stack) + "), nbtObj=" + nbtObj);
            if (nbtObj == null) {
                stack.remove(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
            } else if (nbtObj instanceof net.minecraft.nbt.CompoundTag) {
                net.minecraft.nbt.CompoundTag tag = (net.minecraft.nbt.CompoundTag) nbtObj;
                if (tag.isEmpty()) {
                    tag = tag.copy();
                    tag.putBoolean("chainloader_nbt_compat", true);
                }
                stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(tag));
                System.out.println("[EventBridgeHelper]   set CUSTOM_DATA to tag keys: " + tag.getAllKeys());
            }
        }
    }

    private static void postToBus(Object bus, Object event) {
        try {
            java.lang.reflect.Method postMethod = null;
            try {
                postMethod = bus.getClass().getMethod("post", Object.class);
            } catch (NoSuchMethodException e) {
                Class<?> paramType = event.getClass();
                while (paramType != null && !paramType.getName().endsWith(".Event")) {
                    paramType = paramType.getSuperclass();
                }
                if (paramType == null) {
                    if (bus.getClass().getName().contains("neoforged")) {
                        paramType = net.neoforged.bus.api.Event.class;
                    } else {
                        paramType = net.minecraftforge.eventbus.api.Event.class;
                    }
                }
                postMethod = bus.getClass().getMethod("post", paramType);
            }
            
            postMethod.invoke(bus, event);
        } catch (Throwable t) {
            System.err.println("  Failed to post event to bus " + bus.getClass().getName() + " (event: " + event.getClass().getName() + "):");
            t.printStackTrace();
            if (t instanceof java.lang.reflect.InvocationTargetException && t.getCause() != null) {
                System.err.println("  Underlying cause of event listener failure:");
                t.getCause().printStackTrace();
            }
        }
    }

    public static void unfreezeRegistry(Object registry) {
        if (registry == null) return;
        try {
            Class<?> registryClass = registry.getClass();
            Class<?> mappedRegistryClass = null;
            Class<?> curr = registryClass;
            while (curr != null) {
                if (curr.getName().equals("net.minecraft.core.MappedRegistry") || 
                    curr.getName().equals("ju") || 
                    curr.getName().equals("net.minecraft.class_2370")) {
                    mappedRegistryClass = curr;
                    break;
                }
                curr = curr.getSuperclass();
            }
            if (mappedRegistryClass == null) {
                mappedRegistryClass = registryClass;
            }

            // 1. Unfreeze registry (set frozen = false)
            java.lang.reflect.Field frozenField = null;
            for (String name : new String[]{"frozen", "field_36463", "l"}) {
                try {
                    frozenField = mappedRegistryClass.getDeclaredField(name);
                    break;
                } catch (NoSuchFieldException e) {
                    // ignore
                }
            }
            if (frozenField == null) {
                for (java.lang.reflect.Field f : mappedRegistryClass.getDeclaredFields()) {
                    if (f.getType() == boolean.class) {
                        frozenField = f;
                        break;
                    }
                }
            }
            if (frozenField != null) {
                frozenField.setAccessible(true);
                frozenField.setBoolean(registry, false);
            }

            // 2. Enable intrusive holders (initialize intrusiveHolderCache = new IdentityHashMap())
            java.lang.reflect.Field cacheField = null;
            for (String name : new String[]{"intrusiveHolderCache", "intrusiveHolders", "field_36462", "k", "m"}) {
                try {
                    cacheField = mappedRegistryClass.getDeclaredField(name);
                    break;
                } catch (NoSuchFieldException e) {
                    // ignore
                }
            }
            if (cacheField == null) {
                for (java.lang.reflect.Field f : mappedRegistryClass.getDeclaredFields()) {
                    if (f.getType() == java.util.Map.class) {
                        cacheField = f;
                        break;
                    }
                }
            }
            if (cacheField != null) {
                cacheField.setAccessible(true);
                Object cache = cacheField.get(registry);
                if (cache == null) {
                    cacheField.set(registry, new java.util.IdentityHashMap<>());
                }
            }
        } catch (Throwable t) {
            System.err.println("[ChainLoader] Failed to unfreeze registry: " + t.getMessage());
            t.printStackTrace();
        }
    }

    public static void refreezeRegistry(Object registry) {
        if (registry == null) return;
        try {
            Class<?> registryClass = registry.getClass();
            Class<?> mappedRegistryClass = null;
            Class<?> curr = registryClass;
            while (curr != null) {
                if (curr.getName().equals("net.minecraft.core.MappedRegistry") || 
                    curr.getName().equals("ju") || 
                    curr.getName().equals("net.minecraft.class_2370")) {
                    mappedRegistryClass = curr;
                    break;
                }
                curr = curr.getSuperclass();
            }
            if (mappedRegistryClass == null) {
                mappedRegistryClass = registryClass;
            }

            // 1. Freeze registry (set frozen = true)
            java.lang.reflect.Field frozenField = null;
            for (String name : new String[]{"frozen", "field_36463", "l"}) {
                try {
                    frozenField = mappedRegistryClass.getDeclaredField(name);
                    break;
                } catch (NoSuchFieldException e) {
                    // ignore
                }
            }
            if (frozenField == null) {
                for (java.lang.reflect.Field f : mappedRegistryClass.getDeclaredFields()) {
                    if (f.getType() == boolean.class) {
                        frozenField = f;
                        break;
                    }
                }
            }
            if (frozenField != null) {
                frozenField.setAccessible(true);
                frozenField.setBoolean(registry, true);
            }

            // 2. Clear intrusive holder cache (set intrusiveHolderCache = null)
            java.lang.reflect.Field cacheField = null;
            for (String name : new String[]{"intrusiveHolderCache", "intrusiveHolders", "field_36462", "k", "m"}) {
                try {
                    cacheField = mappedRegistryClass.getDeclaredField(name);
                    break;
                } catch (NoSuchFieldException e) {
                    // ignore
                }
            }
            if (cacheField == null) {
                for (java.lang.reflect.Field f : mappedRegistryClass.getDeclaredFields()) {
                    if (f.getType() == java.util.Map.class) {
                        cacheField = f;
                        break;
                    }
                }
            }
            if (cacheField != null) {
                cacheField.setAccessible(true);
                cacheField.set(registry, null);
            }
        } catch (Throwable t) {
            System.err.println("[ChainLoader] Failed to refreeze registry: " + t.getMessage());
            t.printStackTrace();
        }
    }

    public static Object getConnection(Object listener) {
        if (listener == null) return null;
        try {
            // Find field 'connection' or 'c' in the listener class or its parent hierarchy
            Class<?> current = listener.getClass();
            while (current != null && current != Object.class) {
                for (java.lang.reflect.Field f : current.getDeclaredFields()) {
                    if (f.getType().getName().equals("vt") || f.getType().getName().equals("net.minecraft.network.Connection")) {
                        f.setAccessible(true);
                        return f.get(listener);
                    }
                }
                current = current.getSuperclass();
            }
        } catch (Exception e) {
            System.err.println("[EventBridgeHelper] Failed to get connection from listener via reflection:");
            e.printStackTrace();
        }
        return null;
    }

    public static void onClientDisconnect(Object clientPacketListener, Object disconnectionDetails) {
        try {
            System.out.println("[EventBridgeHelper] Client disconnected, firing ClientPlayConnectionEvents.DISCONNECT");
            net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.invoker().onPlayDisconnect(
                (net.minecraft.client.multiplayer.ClientPacketListener) clientPacketListener,
                client
            );
        } catch (Throwable t) {
            System.err.println("Failed to fire ClientPlayConnectionEvents.DISCONNECT:");
            t.printStackTrace();
        }
    }

    public static void onServerDisconnect(Object serverCommonPacketListener, Object disconnectionDetails) {
        try {
            System.out.println("[EventBridgeHelper] Server disconnected, firing ServerPlayConnectionEvents.DISCONNECT");
            Object server = currentServer;
            if (server == null) {
                for (java.lang.reflect.Field f : serverCommonPacketListener.getClass().getFields()) {
                    if (f.getType().getName().contains("MinecraftServer")) {
                        f.setAccessible(true);
                        server = f.get(serverCommonPacketListener);
                        break;
                    }
                }
                if (server == null) {
                    for (java.lang.reflect.Field f : serverCommonPacketListener.getClass().getDeclaredFields()) {
                        if (f.getType().getName().contains("MinecraftServer")) {
                            f.setAccessible(true);
                            server = f.get(serverCommonPacketListener);
                            break;
                        }
                    }
                }
                if (server == null) {
                    Class<?> sc = serverCommonPacketListener.getClass().getSuperclass();
                    while (sc != null && server == null) {
                        for (java.lang.reflect.Field f : sc.getDeclaredFields()) {
                            if (f.getType().getName().contains("MinecraftServer")) {
                                f.setAccessible(true);
                                server = f.get(serverCommonPacketListener);
                                break;
                            }
                        }
                        sc = sc.getSuperclass();
                    }
                }
            }
            if (server != null) {
                net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.invoker().onPlayDisconnect(
                    serverCommonPacketListener,
                    server
                );
            } else {
                System.err.println("Failed to fire ServerPlayConnectionEvents.DISCONNECT: MinecraftServer not found");
            }
        } catch (Throwable t) {
            System.err.println("Failed to fire ServerPlayConnectionEvents.DISCONNECT:");
            t.printStackTrace();
        }
    }

    private static net.minecraft.world.item.CreativeModeTab findCreativeModeTab(Object obj, int depth, java.util.Set<Object> visited) {
        if (obj == null || depth > 3 || visited.contains(obj)) return null;
        visited.add(obj);

        if (obj instanceof net.minecraft.world.item.CreativeModeTab) {
            return (net.minecraft.world.item.CreativeModeTab) obj;
        }

        if (java.lang.reflect.Proxy.isProxyClass(obj.getClass())) {
            try {
                java.lang.reflect.InvocationHandler handler = java.lang.reflect.Proxy.getInvocationHandler(obj);
                net.minecraft.world.item.CreativeModeTab tab = findCreativeModeTab(handler, depth + 1, visited);
                if (tab != null) return tab;
            } catch (Exception ignored) {}
        }

        Class<?> curr = obj.getClass();
        while (curr != null && curr != Object.class) {
            String pkg = curr.getPackageName();
            if (pkg != null && (pkg.startsWith("java.") || pkg.startsWith("javax.") || pkg.startsWith("sun."))) {
                curr = curr.getSuperclass();
                continue;
            }
            for (java.lang.reflect.Field field : curr.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                Class<?> type = field.getType();
                if (type.isPrimitive() || type == String.class || Number.class.isAssignableFrom(type) || type == Boolean.class || type == Character.class) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    Object val = field.get(obj);
                    if (val != null) {
                        net.minecraft.world.item.CreativeModeTab tab = findCreativeModeTab(val, depth + 1, visited);
                        if (tab != null) return tab;
                    }
                } catch (Exception ignored) {}
            }
            curr = curr.getSuperclass();
        }
        return null;
    }

    public static void buildTabContents(Object generator, Object parameters, Object output) {
        if (generator == null || parameters == null || output == null) return;

        // 1. Call original generator
        try {
            ((net.minecraft.world.item.CreativeModeTab.DisplayItemsGenerator) generator).accept(
                (net.minecraft.world.item.CreativeModeTab.ItemDisplayParameters) parameters, 
                (net.minecraft.world.item.CreativeModeTab.Output) output
            );
        } catch (Throwable t) {
            System.err.println("[ChainLoader] Failed to invoke DisplayItemsGenerator.accept:");
            t.printStackTrace();
        }

        // 2. Retrieve outer CreativeModeTab from output
        net.minecraft.world.item.CreativeModeTab tab = findCreativeModeTab(output, 0, new java.util.HashSet<>());

        // 3. Get ResourceKey of this tab
        if (tab != null) {
            net.minecraft.resources.ResourceKey<net.minecraft.world.item.CreativeModeTab> tabKey = null;
            try {
                java.util.Optional<net.minecraft.resources.ResourceKey<net.minecraft.world.item.CreativeModeTab>> optKey = 
                    net.minecraft.core.registries.BuiltInRegistries.CREATIVE_MODE_TAB.getResourceKey(tab);
                if (optKey.isPresent()) {
                    tabKey = optKey.get();
                }
            } catch (Exception e) {
                System.err.println("[ChainLoader] Failed to get ResourceKey for tab " + tab + ":");
                e.printStackTrace();
            }

            if (tabKey != null) {
                System.out.println("[ChainLoader] Intercepted creative tab contents building for: " + tabKey.location());
                java.util.Set<Object> buses = net.chainloader.loader.core.ModScanner.getModEventBuses();

                // Fire Fabric modifyEntriesEvent
                try {
                    net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroupEntriesWrapper fabricWrapper = 
                        new net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroupEntriesWrapper((net.minecraft.world.item.CreativeModeTab.Output) output);
                    net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents.modifyEntriesEvent(tabKey).invoker().modify(fabricWrapper);
                    fabricWrapper.commit();
                } catch (Throwable t) {
                    System.err.println("[ChainLoader] Failed to fire Fabric ItemGroupEvents:");
                    t.printStackTrace();
                }

                // Fire Forge CreativeModeTabEvent.BuildContents
                try {
                    net.minecraftforge.event.CreativeModeTabEvent.BuildContents forgeEvent = 
                        new net.minecraftforge.event.CreativeModeTabEvent.BuildContents(tab, (net.minecraft.world.item.CreativeModeTab.Output) output);
                    for (Object bus : buses) {
                        if (bus.getClass().getName().contains("minecraftforge")) {
                            postToBus(bus, forgeEvent);
                        }
                    }
                } catch (Throwable t) {
                    System.err.println("[ChainLoader] Failed to fire Forge CreativeModeTabEvent:");
                    t.printStackTrace();
                }

                // Fire NeoForge BuildCreativeModeTabContentsEvent
                try {
                    net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent neoforgeEvent = 
                        new net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent(tab, tabKey, 
                            (net.minecraft.world.item.CreativeModeTab.ItemDisplayParameters) parameters, 
                            (net.minecraft.world.item.CreativeModeTab.Output) output);
                    for (Object bus : buses) {
                        if (bus.getClass().getName().contains("neoforged")) {
                            postToBus(bus, neoforgeEvent);
                        }
                    }
                } catch (Throwable t) {
                    System.err.println("[ChainLoader] Failed to fire NeoForge BuildCreativeModeTabContentsEvent:");
                    t.printStackTrace();
                }
            }
        }
    }

    public static void onServerStarting(Object serverObj) {
        if (serverObj instanceof net.minecraft.server.MinecraftServer) {
            net.minecraft.server.MinecraftServer server = (net.minecraft.server.MinecraftServer) serverObj;
            setCurrentServer(server);
            
            // Post to the host event bus
            try {
                net.chainloader.api.event.ChainEventBus hostBus = net.chainloader.loader.compat.bridge.EventTranslatorBus.getInstance().getHostBus();
                if (hostBus != null) {
                    hostBus.post(new net.chainloader.api.event.ServerStartingEvent(server));
                } else {
                    net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
                        new net.neoforged.neoforge.event.server.ServerStartingEvent(server)
                    );
                }
            } catch (Throwable t) {
                t.printStackTrace();
            }
            
            // Fire Fabric ServerLifecycleEvents.SERVER_STARTING
            try {
                net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTING.invoker().onServerStarting(server);
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
    }

    public static void onServerStarted(Object serverObj) {
        if (serverObj instanceof net.minecraft.server.MinecraftServer) {
            net.minecraft.server.MinecraftServer server = (net.minecraft.server.MinecraftServer) serverObj;
            setCurrentServer(server);
            
            // Post to the host event bus
            try {
                net.chainloader.api.event.ChainEventBus hostBus = net.chainloader.loader.compat.bridge.EventTranslatorBus.getInstance().getHostBus();
                if (hostBus != null) {
                    hostBus.post(new net.chainloader.api.event.ServerStartedEvent(server));
                } else {
                    net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
                        new net.neoforged.neoforge.event.server.ServerStartedEvent(server)
                    );
                }
            } catch (Throwable t) {
                t.printStackTrace();
            }
            
            // Fire Fabric ServerLifecycleEvents.SERVER_STARTED
            try {
                net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTED.invoker().onServerStarted(server);
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
    }

    public static void onServerStopping(Object serverObj) {
        if (serverObj instanceof net.minecraft.server.MinecraftServer) {
            net.minecraft.server.MinecraftServer server = (net.minecraft.server.MinecraftServer) serverObj;
            
            // Post to the host event bus
            try {
                net.chainloader.api.event.ChainEventBus hostBus = net.chainloader.loader.compat.bridge.EventTranslatorBus.getInstance().getHostBus();
                if (hostBus != null) {
                    hostBus.post(new net.chainloader.api.event.ServerStoppingEvent(server));
                } else {
                    net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
                        new net.neoforged.neoforge.event.server.ServerStoppingEvent(server)
                    );
                }
            } catch (Throwable t) {
                t.printStackTrace();
            }
            
            // Fire Fabric ServerLifecycleEvents.SERVER_STOPPING
            try {
                net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPING.invoker().onServerStopping(server);
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
    }

    public static void onServerStopped(Object serverObj) {
        if (serverObj instanceof net.minecraft.server.MinecraftServer) {
            net.minecraft.server.MinecraftServer server = (net.minecraft.server.MinecraftServer) serverObj;
            
            // Post to the host event bus
            try {
                net.chainloader.api.event.ChainEventBus hostBus = net.chainloader.loader.compat.bridge.EventTranslatorBus.getInstance().getHostBus();
                if (hostBus != null) {
                    hostBus.post(new net.chainloader.api.event.ServerStoppedEvent(server));
                } else {
                    net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
                        new net.neoforged.neoforge.event.server.ServerStoppedEvent(server)
                    );
                }
            } catch (Throwable t) {
                t.printStackTrace();
            }
            
            // Fire Fabric ServerLifecycleEvents.SERVER_STOPPED
            try {
                net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.invoker().onServerStopped(server);
            } catch (Throwable t) {
                t.printStackTrace();
            }
            
            setCurrentServer(null);
        }
    }

    public static net.minecraft.server.MinecraftServer getServerFromLevel(Object level) {
        if (level == null) return null;
        try {
            for (java.lang.reflect.Method m : level.getClass().getMethods()) {
                if (m.getParameterCount() == 0 && net.minecraft.server.MinecraftServer.class.isAssignableFrom(m.getReturnType())) {
                    return (net.minecraft.server.MinecraftServer) m.invoke(level);
                }
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
        return null;
    }

    public static void onLevelLoad(Object levelObj) {
        if (levelObj instanceof net.minecraft.world.level.LevelAccessor) {
            net.minecraft.world.level.LevelAccessor level = (net.minecraft.world.level.LevelAccessor) levelObj;
            
            // Post to the host event bus
            try {
                net.chainloader.api.event.ChainEventBus hostBus = net.chainloader.loader.compat.bridge.EventTranslatorBus.getInstance().getHostBus();
                if (hostBus != null) {
                    hostBus.post(new net.chainloader.api.event.LevelLoadEvent(level));
                } else {
                    net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
                        new net.neoforged.neoforge.event.level.LevelEvent.Load(level)
                    );
                }
            } catch (Throwable t) {
                t.printStackTrace();
            }
 
            // Fire Fabric ServerLevelEvents.LOAD
            if (level instanceof net.minecraft.server.level.ServerLevel) {
                net.minecraft.server.level.ServerLevel serverLevel = (net.minecraft.server.level.ServerLevel) level;
                try {
                    net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents.LOAD.invoker().onLevelLoad(
                        getServerFromLevel(serverLevel),
                        serverLevel
                    );
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }
        }
    }
 
    public static void onLevelUnload(Object levelObj) {
        if (levelObj instanceof net.minecraft.world.level.LevelAccessor) {
            net.minecraft.world.level.LevelAccessor level = (net.minecraft.world.level.LevelAccessor) levelObj;
            
            // Post to the host event bus
            try {
                net.chainloader.api.event.ChainEventBus hostBus = net.chainloader.loader.compat.bridge.EventTranslatorBus.getInstance().getHostBus();
                if (hostBus != null) {
                    hostBus.post(new net.chainloader.api.event.LevelUnloadEvent(level));
                } else {
                    net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
                        new net.neoforged.neoforge.event.level.LevelEvent.Unload(level)
                    );
                }
            } catch (Throwable t) {
                t.printStackTrace();
            }
 
            // Fire Fabric ServerLevelEvents.UNLOAD
            if (level instanceof net.minecraft.server.level.ServerLevel) {
                net.minecraft.server.level.ServerLevel serverLevel = (net.minecraft.server.level.ServerLevel) level;
                try {
                    net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents.UNLOAD.invoker().onLevelUnload(
                        getServerFromLevel(serverLevel),
                        serverLevel
                    );
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }
        }
    }
 
    public static void onLevelSave(Object levelObj) {
        if (levelObj instanceof net.minecraft.world.level.LevelAccessor) {
            net.minecraft.world.level.LevelAccessor level = (net.minecraft.world.level.LevelAccessor) levelObj;
            
            // Post to the host event bus
            try {
                net.chainloader.api.event.ChainEventBus hostBus = net.chainloader.loader.compat.bridge.EventTranslatorBus.getInstance().getHostBus();
                if (hostBus != null) {
                    hostBus.post(new net.chainloader.api.event.LevelSaveEvent(level));
                } else {
                    net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
                        new net.neoforged.neoforge.event.level.LevelEvent.Save(level)
                    );
                }
            } catch (Throwable t) {
                t.printStackTrace();
            }
 
            // Fire Fabric ServerLevelEvents.SAVE
            if (level instanceof net.minecraft.server.level.ServerLevel) {
                net.minecraft.server.level.ServerLevel serverLevel = (net.minecraft.server.level.ServerLevel) level;
                try {
                    net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents.SAVE.invoker().onLevelSave(
                        getServerFromLevel(serverLevel),
                        serverLevel
                    );
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }
        }
    }

    public static <T extends net.minecraft.world.level.saveddata.SavedData> T computeIfAbsent(
            Object storageObj,
            java.util.function.Function<net.minecraft.nbt.CompoundTag, T> readFunction,
            java.util.function.Supplier<T> constructor,
            String name) {
        System.out.println("[EventBridgeHelper] computeIfAbsent redirect for: " + name);
        
        // Wrap and register in ChainWorldDataBridge
        net.chainloader.loader.core.world.ChainWorldDataBridge.registerWorldData(name, new net.chainloader.loader.core.world.ChainWorldData() {
            @Override
            public void read(Object nbt) {
                readFunction.apply((net.minecraft.nbt.CompoundTag) nbt);
            }
            @Override
            public Object write(Object nbt) {
                return nbt;
            }
        });

        net.minecraft.world.level.storage.DimensionDataStorage storage = (net.minecraft.world.level.storage.DimensionDataStorage) storageObj;
        net.minecraft.world.level.saveddata.SavedData.Factory<T> factory = new net.minecraft.world.level.saveddata.SavedData.Factory<>(
            constructor,
            (nbt, provider) -> readFunction.apply(nbt),
            net.minecraft.util.datafix.DataFixTypes.LEVEL
        );
        return storage.computeIfAbsent(factory, name);
    }

    public static void onContainerScreenRenderForeground(net.minecraft.client.gui.screens.Screen screen, net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY) {
        if (screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen) {
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
                new net.neoforged.neoforge.client.event.ContainerScreenEvent.Render.Foreground(
                    (net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>) screen,
                    graphics,
                    mouseX,
                    mouseY
                )
            );
        }
    }

    public static net.minecraft.client.gui.screens.Screen onScreenOpening(net.minecraft.client.gui.screens.Screen currentScreen, net.minecraft.client.gui.screens.Screen newScreen) {
        net.neoforged.neoforge.client.event.ScreenEvent.Opening event =
            new net.neoforged.neoforge.client.event.ScreenEvent.Opening(currentScreen, newScreen);
        boolean cancelled = net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(event);
        if (cancelled) {
            return null;
        }
        return event.getNewScreen();
    }

    public static void onClientTickPre(Object minecraft) {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
            new net.neoforged.neoforge.client.event.ClientTickEvent.Pre()
        );
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(
            new net.minecraftforge.event.TickEvent.ClientTickEvent(net.minecraftforge.event.TickEvent.Phase.START)
        );
        try {
            net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.START_CLIENT_TICK.invoker().onStartTick((net.minecraft.client.Minecraft) minecraft);
        } catch (Throwable t) {
            // Ignore
        }
    }

    public static void onClientTickPost(Object minecraft) {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
            new net.neoforged.neoforge.client.event.ClientTickEvent.Post()
        );
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(
            new net.minecraftforge.event.TickEvent.ClientTickEvent(net.minecraftforge.event.TickEvent.Phase.END)
        );
        try {
            net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.invoker().onEndTick((net.minecraft.client.Minecraft) minecraft);
        } catch (Throwable t) {
            // Ignore
        }
        
        try {
            for (dev.architectury.event.events.client.ClientTickEvent.Client listener : dev.architectury.event.events.client.ClientTickEvent.getListeners()) {
                listener.post((net.minecraft.client.Minecraft) minecraft);
            }
        } catch (Throwable t) {
            // Ignore
        }
    }

    public static void onServerTickPre(Object server) {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
            new net.neoforged.neoforge.event.tick.ServerTickEvent.Pre((net.minecraft.server.MinecraftServer) server)
        );
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(
            new net.minecraftforge.event.TickEvent.ServerTickEvent(net.minecraftforge.event.TickEvent.Phase.START)
        );
    }

    public static void onServerTickPost(Object server) {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
            new net.neoforged.neoforge.event.tick.ServerTickEvent.Post((net.minecraft.server.MinecraftServer) server)
        );
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(
            new net.minecraftforge.event.TickEvent.ServerTickEvent(net.minecraftforge.event.TickEvent.Phase.END)
        );
    }

    public static void onLocalPlayerInit(Object playerObj) {
        net.minecraft.client.player.LocalPlayer player = (net.minecraft.client.player.LocalPlayer) playerObj;
        System.out.println("[EventBridgeHelper] LocalPlayer initialized: " + (player.getGameProfile() != null ? player.getGameProfile().getName() : "unknown"));
        
        try {
            for (dev.architectury.event.events.client.ClientPlayerEvent.ClientPlayerJoin listener : dev.architectury.event.events.client.ClientPlayerEvent.getJoinListeners()) {
                listener.join(player);
            }
        } catch (Throwable t) {
            // Ignore
        }
    }

    public static void onLocalPlayerQuit() {
        System.out.println("[EventBridgeHelper] LocalPlayer quit");
        try {
            net.minecraft.client.player.LocalPlayer player = net.minecraft.client.Minecraft.getInstance().player;
            if (player != null) {
                for (dev.architectury.event.events.client.ClientPlayerEvent.ClientPlayerQuit listener : dev.architectury.event.events.client.ClientPlayerEvent.getQuitListeners()) {
                    listener.quit(player);
                }
            }
        } catch (Throwable t) {
            // Ignore
        }
    }

    public static void onPlayerLoggedIn(Object playerObj) {
        net.minecraft.server.level.ServerPlayer player = (net.minecraft.server.level.ServerPlayer) playerObj;
        System.out.println("[EventBridgeHelper] ServerPlayer logged in: " + (player.getGameProfile() != null ? player.getGameProfile().getName() : "unknown"));
        
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
            new net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent(player)
        );
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(
            new net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent(player)
        );
        
        try {
            for (dev.architectury.event.events.common.PlayerEvent.PlayerJoin listener : dev.architectury.event.events.common.PlayerEvent.getJoinListeners()) {
                listener.join(player);
            }
        } catch (Throwable t) {
            // Ignore
        }
    }

    public static void onPlayerLoggedOut(Object playerObj) {
        net.minecraft.server.level.ServerPlayer player = (net.minecraft.server.level.ServerPlayer) playerObj;
        System.out.println("[EventBridgeHelper] ServerPlayer logged out: " + (player.getGameProfile() != null ? player.getGameProfile().getName() : "unknown"));
        
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
            new net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent(player)
        );
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(
            new net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent(player)
        );
        
        try {
            for (dev.architectury.event.events.common.PlayerEvent.PlayerQuit listener : dev.architectury.event.events.common.PlayerEvent.getQuitListeners()) {
                listener.quit(player);
            }
        } catch (Throwable t) {
            // Ignore
        }
    }

    public static <T extends net.minecraft.world.level.saveddata.SavedData> T get(
            Object storageObj,
            java.util.function.Function<net.minecraft.nbt.CompoundTag, T> readFunction,
            String name) {
        System.out.println("[EventBridgeHelper] get redirect for: " + name);
        net.minecraft.world.level.storage.DimensionDataStorage storage = (net.minecraft.world.level.storage.DimensionDataStorage) storageObj;
        net.minecraft.world.level.saveddata.SavedData.Factory<T> factory = new net.minecraft.world.level.saveddata.SavedData.Factory<>(
            () -> null,
            (nbt, provider) -> readFunction.apply(nbt),
            net.minecraft.util.datafix.DataFixTypes.LEVEL
        );
        return storage.get(factory, name);
    }

    public static <V, T extends V> T register(net.minecraft.core.Registry<V> registry, net.minecraft.resources.ResourceLocation name, T value) {
        System.out.println("[EventBridgeHelper] Registry.register redirect for: " + name + " in " + registry.key().location());
        registerCleanly(registry, name, value);
        return value;
    }

    public static net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket createBlockEntityDataPacket(
            net.minecraft.world.level.block.entity.BlockEntity blockEntity,
            java.util.function.Function<net.minecraft.world.level.block.entity.BlockEntity, net.minecraft.nbt.CompoundTag> tagFunction) {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(
            blockEntity,
            (be, provider) -> tagFunction.apply(be)
        );
    }

        private static final java.util.Map<Object, net.minecraft.nbt.CompoundTag> entityPersistentData = new java.util.concurrent.ConcurrentHashMap<>();

    public static net.minecraft.nbt.CompoundTag getEntityPersistentData(Object entity) {
        if (entity == null) return new net.minecraft.nbt.CompoundTag();
        return entityPersistentData.computeIfAbsent(entity, k -> new net.minecraft.nbt.CompoundTag());
    }

    public static net.minecraft.nbt.CompoundTag writeBlockPosCompound(net.minecraft.core.BlockPos pos) {
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        tag.putInt("X", pos.getX());
        tag.putInt("Y", pos.getY());
        tag.putInt("Z", pos.getZ());
        return tag;
    }

    public static void patchRecipes(java.util.Map<?, ?> map) {
        if (map == null) return;
        for (java.util.Map.Entry<?, ?> entry : map.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof com.google.gson.JsonObject) {
                com.google.gson.JsonObject recipeObj = (com.google.gson.JsonObject) value;
                if (recipeObj.has("result")) {
                    com.google.gson.JsonElement resultElement = recipeObj.get("result");
                    if (resultElement.isJsonObject()) {
                        com.google.gson.JsonObject resultObj = resultElement.getAsJsonObject();
                        if (resultObj.has("item") && !resultObj.has("id")) {
                            resultObj.addProperty("id", resultObj.get("item").getAsString());
                        }
                    }
                }
            }
        }
    }

    public static net.minecraft.world.level.biome.Biome.Precipitation getPrecipitation(Object biomeObj) {
        if (biomeObj instanceof net.minecraft.world.level.biome.Biome) {
            net.minecraft.world.level.biome.Biome biome = (net.minecraft.world.level.biome.Biome) biomeObj;
            if (!biome.hasPrecipitation()) {
                return net.minecraft.world.level.biome.Biome.Precipitation.NONE;
            }
            return biome.getBaseTemperature() < 0.15F ? 
                net.minecraft.world.level.biome.Biome.Precipitation.SNOW : 
                net.minecraft.world.level.biome.Biome.Precipitation.RAIN;
        }
        return null;
    }

    public static Object getCreativeModeTab(Object resourceKeyObj) {
        if (resourceKeyObj instanceof net.minecraft.resources.ResourceKey) {
            net.minecraft.resources.ResourceKey<net.minecraft.world.item.CreativeModeTab> key = 
                (net.minecraft.resources.ResourceKey<net.minecraft.world.item.CreativeModeTab>) resourceKeyObj;
            return net.minecraft.core.registries.BuiltInRegistries.CREATIVE_MODE_TAB.get(key);
        }
        return null;
    }

    private static java.lang.reflect.Method dyeColorGetColorMethod = null;
    private static java.lang.reflect.Field dyeColorField = null;

    public static float[] getColorComponents(Object dyeColorObj) {
        if (dyeColorObj == null) {
            return new float[]{1.0f, 1.0f, 1.0f};
        }
        try {
            int colorValue = 0;
            if (dyeColorGetColorMethod != null) {
                colorValue = (Integer) dyeColorGetColorMethod.invoke(dyeColorObj);
            } else if (dyeColorField != null) {
                colorValue = dyeColorField.getInt(dyeColorObj);
            } else {
                Class<?> clazz = dyeColorObj.getClass();
                try {
                    dyeColorGetColorMethod = clazz.getMethod("getTextureDiffuseColor");
                    colorValue = (Integer) dyeColorGetColorMethod.invoke(dyeColorObj);
                } catch (NoSuchMethodException e) {
                    try {
                        dyeColorGetColorMethod = clazz.getMethod("d");
                        colorValue = (Integer) dyeColorGetColorMethod.invoke(dyeColorObj);
                    } catch (NoSuchMethodException e2) {
                        try {
                            dyeColorField = clazz.getField("textureDiffuseColor");
                            colorValue = dyeColorField.getInt(dyeColorObj);
                        } catch (NoSuchFieldException e3) {
                            try {
                                dyeColorField = clazz.getField("x");
                                colorValue = dyeColorField.getInt(dyeColorObj);
                            } catch (NoSuchFieldException e4) {
                                // Search all fields or methods
                                for (java.lang.reflect.Method m : clazz.getMethods()) {
                                    if (m.getName().equals("getTextureDiffuseColor") || m.getName().equals("d")) {
                                        dyeColorGetColorMethod = m;
                                        break;
                                    }
                                }
                                if (dyeColorGetColorMethod != null) {
                                    colorValue = (Integer) dyeColorGetColorMethod.invoke(dyeColorObj);
                                } else {
                                    for (java.lang.reflect.Field f : clazz.getFields()) {
                                        if (f.getName().equals("textureDiffuseColor") || f.getName().equals("x")) {
                                            dyeColorField = f;
                                            break;
                                        }
                                    }
                                    if (dyeColorField != null) {
                                        colorValue = dyeColorField.getInt(dyeColorObj);
                                    } else {
                                        throw new RuntimeException("Could not find color value in DyeColor");
                                    }
                                }
                            }
                        }
                    }
                }
            }
            float r = ((colorValue >> 16) & 255) / 255.0f;
            float g = ((colorValue >> 8) & 255) / 255.0f;
            float b = (colorValue & 255) / 255.0f;
            return new float[]{r, g, b};
        } catch (Throwable t) {
            t.printStackTrace();
            return new float[]{1.0f, 1.0f, 1.0f};
        }
    }

    public static java.util.List<net.minecraft.client.KeyMapping> getKeyMappings() {
        return customKeyMappings;
    }

    public static void populateModBlockStates() {
        try {
            System.out.println("[ChainLoader] Populating mod block states in BLOCK_STATE_REGISTRY...");
            
            // 1. Get Block class and the BLOCK_STATE_REGISTRY field (field 'q' on net.minecraft.world.level.block.Block)
            Class<?> blockClass = Class.forName("net.minecraft.world.level.block.Block");
            java.lang.reflect.Field registryField = blockClass.getDeclaredField("q");
            registryField.setAccessible(true);
            Object blockStateRegistry = registryField.get(null);
            
            // Methods on IdMapper/js
            java.lang.reflect.Method getIdMethod = blockStateRegistry.getClass().getMethod("a", Object.class);
            java.lang.reflect.Method addMethod = blockStateRegistry.getClass().getMethod("b", Object.class);
            
            // 2. Get BuiltInRegistries class and BLOCK field (field 'e' on net.minecraft.core.registries.BuiltInRegistries)
            Class<?> registriesClass = Class.forName("net.minecraft.core.registries.BuiltInRegistries");
            java.lang.reflect.Field blockRegistryField = registriesClass.getDeclaredField("e");
            blockRegistryField.setAccessible(true);
            Iterable<?> blockRegistry = (Iterable<?>) blockRegistryField.get(null);
            int addedCount = 0;
            
            for (Object block : blockRegistry) {
                // block.getStateDefinition()
                java.lang.reflect.Method getStateDefinitionMethod = block.getClass().getMethod("l");
                Object stateDefinition = getStateDefinitionMethod.invoke(block);
                
                // stateDefinition.getPossibleStates()
                java.lang.reflect.Method getPossibleStatesMethod = stateDefinition.getClass().getMethod("a");
                java.util.List<?> possibleStates = (java.util.List<?>) getPossibleStatesMethod.invoke(stateDefinition);
                
                for (Object state : possibleStates) {
                    // Check if state is already registered (id != -1)
                    int id = (Integer) getIdMethod.invoke(blockStateRegistry, state);
                    if (id == -1) {
                        addMethod.invoke(blockStateRegistry, state);
                        addedCount++;
                    }
                }
            }
            System.out.println("[ChainLoader] Successfully populated mod block states. Added " + addedCount + " states to BLOCK_STATE_REGISTRY.");
        } catch (Throwable t) {
            System.err.println("[ChainLoader] Failed to populate mod block states:");
            t.printStackTrace();
        }
    }

    public static void tickWidget(Object widget) {
        // No-op to prevent NoSuchMethodError when legacy mods tick modern widgets.
    }

    public static List<Component> getTooltipLines(ItemStack stack, Player player, TooltipFlag flag) {
        TooltipContext context = (player != null && player.level != null)
            ? TooltipContext.of(player.level)
            : TooltipContext.EMPTY;
        return stack.getTooltipLines(context, player, flag);
    }

    public static net.minecraft.world.level.Level getClientLevel() {
        try {
            Class<?> mcClass = Class.forName("net.minecraft.client.Minecraft");
            Object mcInstance = mcClass.getMethod("getInstance").invoke(null);
            return (net.minecraft.world.level.Level) mcClass.getField("level").get(mcInstance);
        } catch (Throwable t) {
            return null;
        }
    }

    private static java.lang.invoke.MethodHandle superUseHandle = null;

    public static net.minecraft.world.InteractionResult superUse(
        net.minecraft.world.level.block.state.BlockBehaviour block,
        net.minecraft.world.level.block.state.BlockState state,
        net.minecraft.world.level.Level level,
        net.minecraft.core.BlockPos pos,
        net.minecraft.world.entity.player.Player player,
        net.minecraft.world.InteractionHand hand,
        net.minecraft.world.phys.BlockHitResult hit
    ) {
        try {
            if (superUseHandle == null) {
                java.lang.invoke.MethodType type = java.lang.invoke.MethodType.methodType(
                    net.minecraft.world.InteractionResult.class,
                    net.minecraft.world.level.block.state.BlockState.class,
                    net.minecraft.world.level.Level.class,
                    net.minecraft.core.BlockPos.class,
                    net.minecraft.world.entity.player.Player.class,
                    net.minecraft.world.phys.BlockHitResult.class
                );
                java.lang.invoke.MethodHandles.Lookup lookup = java.lang.invoke.MethodHandles.privateLookupIn(
                    net.minecraft.world.level.block.state.BlockBehaviour.class,
                    java.lang.invoke.MethodHandles.lookup()
                );
                try {
                    superUseHandle = lookup.findSpecial(
                        net.minecraft.world.level.block.state.BlockBehaviour.class,
                        "useWithoutItem",
                        type,
                        net.minecraft.world.level.block.state.BlockBehaviour.class
                    );
                } catch (NoSuchMethodException e) {
                    superUseHandle = lookup.findSpecial(
                        net.minecraft.world.level.block.state.BlockBehaviour.class,
                        "a",
                        type,
                        net.minecraft.world.level.block.state.BlockBehaviour.class
                    );
                }
            }
            return (net.minecraft.world.InteractionResult) superUseHandle.bindTo(block).invoke(state, level, pos, player, hit);
        } catch (Throwable t) {
            return block.useWithoutItem(state, level, pos, player, hit);
        }
    }

    private static java.lang.invoke.MethodHandle superAppendHoverTextItemHandle = null;

    public static void superAppendHoverText(
        net.minecraft.world.item.Item item,
        net.minecraft.world.item.ItemStack stack,
        net.minecraft.world.level.Level level,
        java.util.List<net.minecraft.network.chat.Component> tooltip,
        net.minecraft.world.item.TooltipFlag flag
    ) {
        net.minecraft.world.item.Item.TooltipContext context = (level != null)
            ? net.minecraft.world.item.Item.TooltipContext.of(level)
            : net.minecraft.world.item.Item.TooltipContext.EMPTY;
        try {
            if (superAppendHoverTextItemHandle == null) {
                java.lang.invoke.MethodType type = java.lang.invoke.MethodType.methodType(
                    void.class,
                    net.minecraft.world.item.ItemStack.class,
                    net.minecraft.world.item.Item.TooltipContext.class,
                    java.util.List.class,
                    net.minecraft.world.item.TooltipFlag.class
                );
                java.lang.invoke.MethodHandles.Lookup lookup = java.lang.invoke.MethodHandles.privateLookupIn(
                    net.minecraft.world.item.Item.class,
                    java.lang.invoke.MethodHandles.lookup()
                );
                try {
                    superAppendHoverTextItemHandle = lookup.findSpecial(
                        net.minecraft.world.item.Item.class,
                        "appendHoverText",
                        type,
                        net.minecraft.world.item.Item.class
                    );
                } catch (NoSuchMethodException e) {
                    superAppendHoverTextItemHandle = lookup.findSpecial(
                        net.minecraft.world.item.Item.class,
                        "a",
                        type,
                        net.minecraft.world.item.Item.class
                    );
                }
            }
            superAppendHoverTextItemHandle.bindTo(item).invoke(stack, context, tooltip, flag);
        } catch (Throwable t) {
            item.appendHoverText(stack, context, tooltip, flag);
        }
    }

    private static java.lang.invoke.MethodHandle superAppendHoverTextBlockHandle = null;

    public static void superAppendHoverText(
        net.minecraft.world.level.block.Block block,
        net.minecraft.world.item.ItemStack stack,
        net.minecraft.world.level.BlockGetter level,
        java.util.List<net.minecraft.network.chat.Component> tooltip,
        net.minecraft.world.item.TooltipFlag flag
    ) {
        net.minecraft.world.item.Item.TooltipContext context = (level instanceof net.minecraft.world.level.Level)
            ? net.minecraft.world.item.Item.TooltipContext.of((net.minecraft.world.level.Level) level)
            : net.minecraft.world.item.Item.TooltipContext.EMPTY;
        try {
            if (superAppendHoverTextBlockHandle == null) {
                java.lang.invoke.MethodType type = java.lang.invoke.MethodType.methodType(
                    void.class,
                    net.minecraft.world.item.ItemStack.class,
                    net.minecraft.world.item.Item.TooltipContext.class,
                    java.util.List.class,
                    net.minecraft.world.item.TooltipFlag.class
                );
                java.lang.invoke.MethodHandles.Lookup lookup = java.lang.invoke.MethodHandles.privateLookupIn(
                    net.minecraft.world.level.block.Block.class,
                    java.lang.invoke.MethodHandles.lookup()
                );
                try {
                    superAppendHoverTextBlockHandle = lookup.findSpecial(
                        net.minecraft.world.level.block.Block.class,
                        "appendHoverText",
                        type,
                        net.minecraft.world.level.block.Block.class
                    );
                } catch (NoSuchMethodException e) {
                    superAppendHoverTextBlockHandle = lookup.findSpecial(
                        net.minecraft.world.level.block.Block.class,
                        "a",
                        type,
                        net.minecraft.world.level.block.Block.class
                    );
                }
            }
            superAppendHoverTextBlockHandle.bindTo(block).invoke(stack, context, tooltip, flag);
        } catch (Throwable t) {
            block.appendHoverText(stack, context, tooltip, flag);
        }
    }

    public static void bridgeFabricModelLoading(Object registerAdditionalEvent) {
        System.out.println("[EventBridgeHelper] Bridging Fabric model loading plugins onto RegisterAdditional event...");
        try {
            // 1. Invoke modern Fabric ModelLoadingPlugins
            java.util.List<?> plugins = net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin.REGISTERED_PLUGINS;
            if (!plugins.isEmpty()) {
                System.out.println("[EventBridgeHelper] Found " + plugins.size() + " registered modern Fabric model loading plugins.");
                // Create a proxy Context
                Class<?> contextClass = Class.forName("net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin$Context");
                Object contextProxy = java.lang.reflect.Proxy.newProxyInstance(
                    registerAdditionalEvent.getClass().getClassLoader(),
                    new Class<?>[] { contextClass },
                    (proxy, method, args) -> {
                        String methodName = method.getName();
                        if ("addModels".equals(methodName)) {
                            if (args[0] instanceof net.minecraft.resources.ResourceLocation[]) {
                                for (net.minecraft.resources.ResourceLocation loc : (net.minecraft.resources.ResourceLocation[]) args[0]) {
                                    registerAdditionalModel(registerAdditionalEvent, loc);
                                }
                            } else if (args[0] instanceof java.util.Collection) {
                                for (Object obj : (java.util.Collection<?>) args[0]) {
                                    if (obj instanceof net.minecraft.resources.ResourceLocation) {
                                        registerAdditionalModel(registerAdditionalEvent, (net.minecraft.resources.ResourceLocation) obj);
                                    }
                                }
                            }
                        }
                        // Default stubs or dummy events
                        if ("resolveModel".equals(methodName) || "modifyModelOnLoad".equals(methodName) || 
                            "modifyModelBeforeBake".equals(methodName) || "modifyModelAfterBake".equals(methodName)) {
                            return new net.fabricmc.fabric.api.event.Event<>();
                        }
                        return null;
                    }
                );

                for (Object plugin : plugins) {
                    try {
                        plugin.getClass().getMethod("onInitializeModelLoader", contextClass).invoke(plugin, contextProxy);
                    } catch (Throwable t) {
                        System.err.println("[EventBridgeHelper] Failed to run Fabric ModelLoadingPlugin:");
                        t.printStackTrace();
                    }
                }
            }

            // 2. Invoke legacy Fabric ModelLoadingRegistry providers
            net.fabricmc.fabric.api.client.model.ModelLoadingRegistry.ModelLoadingRegistryImpl registry = 
                (net.fabricmc.fabric.api.client.model.ModelLoadingRegistry.ModelLoadingRegistryImpl) net.fabricmc.fabric.api.client.model.ModelLoadingRegistry.INSTANCE;
            java.util.List<net.fabricmc.fabric.api.client.model.ExtraModelProvider> legacyProviders = registry.getModelProviders();
            if (!legacyProviders.isEmpty()) {
                System.out.println("[EventBridgeHelper] Found " + legacyProviders.size() + " registered legacy Fabric model providers.");
                net.minecraft.server.packs.resources.ResourceManager resourceManager = null;
                try {
                    Class<?> mcClass = Class.forName("net.minecraft.client.Minecraft");
                    Object mcInstance = mcClass.getMethod("getInstance").invoke(null);
                    resourceManager = (net.minecraft.server.packs.resources.ResourceManager) mcClass.getMethod("getResourceManager").invoke(mcInstance);
                } catch (Throwable t) {
                    System.out.println("[EventBridgeHelper] Real ResourceManager not available yet, using mock ResourceManager.");
                }
                
                for (net.fabricmc.fabric.api.client.model.ExtraModelProvider provider : legacyProviders) {
                    try {
                        provider.provideExtraModels(resourceManager, loc -> {
                            registerAdditionalModel(registerAdditionalEvent, loc);
                        });
                    } catch (Throwable t) {
                        System.err.println("[EventBridgeHelper] Failed to run Fabric legacy ExtraModelProvider:");
                        t.printStackTrace();
                    }
                }
            }
        } catch (Throwable t) {
            System.err.println("[EventBridgeHelper] Error in bridgeFabricModelLoading:");
            t.printStackTrace();
        }
    }

    private static void registerAdditionalModel(Object event, net.minecraft.resources.ResourceLocation modelLoc) {
        try {
            event.getClass().getMethod("register", net.minecraft.resources.ResourceLocation.class).invoke(event, modelLoc);
            System.out.println("[EventBridgeHelper] Registered extra model: " + modelLoc);
        } catch (Throwable t) {
            System.err.println("[EventBridgeHelper] Failed to register extra model " + modelLoc + " on event:");
        }
    }

    private static int nextTabColumn = 0;
    public static net.minecraft.world.item.CreativeModeTab.Builder createCreativeModeTabBuilder() {
        return net.minecraft.world.item.CreativeModeTab.builder(net.minecraft.world.item.CreativeModeTab.Row.TOP, 7 + (nextTabColumn++));
    }

    private static final java.util.Map<Integer, net.minecraft.network.FriendlyByteBuf> clientBuffers = new java.util.concurrent.ConcurrentHashMap<>();

    public static java.util.OptionalInt openMenuBridge(net.minecraft.world.entity.player.Player player, net.minecraft.world.MenuProvider provider) {
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            if (provider instanceof net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory extended) {
                int syncId = serverPlayer.containerCounter + 1;
                net.minecraft.network.FriendlyByteBuf buf = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
                extended.writeScreenOpeningData(serverPlayer, buf);
                clientBuffers.put(syncId, buf);
                return serverPlayer.openMenu(provider);
            }
        }
        return player.openMenu(provider);
    }

    public static net.minecraft.network.FriendlyByteBuf getAndRemoveClientBuffer(int syncId) {
        return clientBuffers.remove(syncId);
    }

    static {
        net.chainloader.loader.core.gui.EarlyLoadingScreen.getInstance().log("Capabilities", "Initializing EventBridgeHelper capability & render registry...");
        
        // Register Forge Energy Storage capability provider to Core Capability Bridge
        net.chainloader.loader.core.gui.EarlyLoadingScreen.getInstance().log("Capabilities", "Registering Forge IEnergyStorage capability adapter...");
        net.chainloader.loader.core.capability.ChainCapabilityBridge.registerEnergyProvider("forge_compat", (level, pos, state, be, side) -> {
            if (be instanceof net.minecraftforge.common.capabilities.ICapabilityProvider) {
                net.minecraftforge.common.util.LazyOptional<net.minecraftforge.energy.IEnergyStorage> cap = 
                    ((net.minecraftforge.common.capabilities.ICapabilityProvider) be).getCapability(
                        net.minecraftforge.energy.CapabilityEnergy.ENERGY,
                        (net.minecraft.core.Direction) side
                    );
                if (cap.isPresent()) {
                    return new net.chainloader.loader.compat.bridge.capability.CoreEnergyStorageAdapter(cap.orElseThrow());
                }
            }
            return null;
        });

        // Register Fabric Energy Storage capability provider to Core Capability Bridge
        net.chainloader.loader.core.gui.EarlyLoadingScreen.getInstance().log("Capabilities", "Registering Fabric Reborn Energy capability adapter...");
        net.chainloader.loader.core.capability.ChainCapabilityBridge.registerEnergyProvider("fabric_compat", (level, pos, state, be, side) -> {
            if (be instanceof team.reborn.energy.api.EnergyStorage) {
                return new net.chainloader.loader.compat.bridge.capability.FabricEnergyStorageCoreAdapter((team.reborn.energy.api.EnergyStorage) be);
            }
            return null;
        });
    }

    private static void registerPayloadHandlers(java.util.Set<Object> buses) {
        for (Object bus : buses) {
            if (bus instanceof net.neoforged.bus.api.IEventBus) {
                net.neoforged.bus.api.IEventBus neoforgeBus = (net.neoforged.bus.api.IEventBus) bus;
                neoforgeBus.addListener(
                    net.neoforged.bus.api.EventPriority.NORMAL,
                    net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent.class,
                    (java.util.function.Consumer<net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent>) event -> {
                        System.out.println("[EventBridgeHelper] Received RegisterPayloadHandlersEvent on mod bus: " + neoforgeBus);
                        
                        // Register all ClientPlayNetworking global receivers
                        for (net.minecraft.resources.ResourceLocation channelId : net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.getGlobalReceivers()) {
                            net.neoforged.neoforge.network.registration.PayloadRegistrar registrar = event.registrar(channelId.getNamespace());
                            registerChannel(registrar, channelId);
                        }
                        
                        // Register all ServerPlayNetworking global receivers
                        for (net.minecraft.resources.ResourceLocation channelId : net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.getGlobalReceivers()) {
                            net.neoforged.neoforge.network.registration.PayloadRegistrar registrar = event.registrar(channelId.getNamespace());
                            registerChannel(registrar, channelId);
                        }

                        // Register all discovered channels from ModScanner
                        for (String channelStr : net.chainloader.loader.core.ModScanner.getDiscoveredPacketChannels()) {
                            int colonIdx = channelStr.indexOf(':');
                            if (colonIdx > 0 && colonIdx < channelStr.length() - 1) {
                                net.minecraft.resources.ResourceLocation channelId = 
                                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                                        channelStr.substring(0, colonIdx), 
                                        channelStr.substring(colonIdx + 1)
                                    );
                                if (channelId != null) {
                                    net.neoforged.neoforge.network.registration.PayloadRegistrar registrar = event.registrar(channelId.getNamespace());
                                    registerChannel(registrar, channelId);
                                }
                            }
                        }
                    }
                );

                // Register Capabilities for native NeoForge compatibility
                neoforgeBus.addListener(
                    net.neoforged.bus.api.EventPriority.NORMAL,
                    net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent.class,
                    (java.util.function.Consumer<net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent>) event -> {
                        System.out.println("[EventBridgeHelper] Received RegisterCapabilitiesEvent on mod bus: " + neoforgeBus);
                        
                        // Expose core energy to native NeoForge
                        event.registerBlock(
                            net.neoforged.neoforge.capabilities.Capabilities.Energy.BLOCK,
                            (level, pos, state, be, side) -> {
                                if (be != null) {
                                    net.chainloader.loader.core.capability.ChainEnergyStorage coreEnergy = 
                                        net.chainloader.loader.core.capability.ChainCapabilityBridge.queryEnergy(level, pos, state, be, side);
                                    if (coreEnergy != null) {
                                        return new net.chainloader.loader.compat.bridge.capability.NeoForgeEnergyStorageAdapter(coreEnergy);
                                    }
                                }
                                return null;
                            }
                        );
                    }
                );

                // Register Client Item Extensions for custom item renderers compatibility
                neoforgeBus.addListener(
                    net.neoforged.bus.api.EventPriority.NORMAL,
                    net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent.class,
                    (java.util.function.Consumer<net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent>) event -> {
                        System.out.println("[EventBridgeHelper] Received RegisterClientExtensionsEvent on mod bus: " + neoforgeBus);
                        
                        // We register custom client extensions for any item that has a registered ChainItemRenderer in Core Render Bridge
                        net.minecraft.core.Registry<net.minecraft.world.item.Item> itemReg = (net.minecraft.core.Registry<net.minecraft.world.item.Item>) 
                            net.minecraft.core.registries.BuiltInRegistries.REGISTRY.get(new net.minecraft.resources.ResourceLocation("minecraft", "item"));
                        if (itemReg != null) {
                            try {
                                java.lang.Iterable<?> iterable = (java.lang.Iterable<?>) itemReg;
                                for (Object itemObj : iterable) {
                                    net.minecraft.world.item.Item item = (net.minecraft.world.item.Item) itemObj;
                                    net.minecraft.resources.ResourceLocation itemId = null;
                                    try {
                                        itemId = (net.minecraft.resources.ResourceLocation) itemReg.getClass().getMethod("getKey", Object.class).invoke(itemReg, item);
                                    } catch (Throwable t) {
                                        try {
                                            java.util.Optional<?> opt = (java.util.Optional<?>) itemReg.getClass().getMethod("getResourceKey", Object.class).invoke(itemReg, item);
                                            if (opt.isPresent()) {
                                                Object key = opt.get();
                                                itemId = (net.minecraft.resources.ResourceLocation) key.getClass().getMethod("location").invoke(key);
                                            }
                                        } catch (Throwable t2) {}
                                    }
                                    if (itemId != null) {
                                        net.chainloader.loader.core.render.ChainItemRenderer renderer = 
                                            net.chainloader.loader.core.render.ChainRenderBridge.getItemRenderer(itemId.toString());
                                        if (renderer != null) {
                                            System.out.println("[EventBridgeHelper] Registering native ClientItemExtension for: " + itemId);
                                            event.registerItem(new net.chainloader.loader.compat.bridge.render.NeoForgeClientItemExtensions(renderer), item);
                                        }
                                    }
                                }
                            } catch (Throwable t) {
                                System.err.println("[EventBridgeHelper] Failed to register client item extensions:");
                                t.printStackTrace();
                            }
                        }
                    }
                );
            }
        }
    }

    public static Object getBlockEntityRenderDispatcher() {
        try {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            for (java.lang.reflect.Method m : mc.getClass().getMethods()) {
                if (m.getReturnType().getName().contains("BlockEntityRenderDispatcher")) {
                    return m.invoke(mc);
                }
            }
        } catch (Throwable t) {}
        return null;
    }

    public static Object getEntityModels() {
        try {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            for (java.lang.reflect.Method m : mc.getClass().getMethods()) {
                if (m.getReturnType().getName().contains("EntityModelSet")) {
                    return m.invoke(mc);
                }
            }
        } catch (Throwable t) {}
        return null;
    }

    private static final java.util.Set<net.minecraft.resources.ResourceLocation> REGISTERED_CHANNELS = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final java.util.Set<net.minecraft.resources.ResourceLocation> REGISTERED_NEOFORGE_CHANNELS = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final java.util.Map<Object, java.util.concurrent.ConcurrentHashMap<net.minecraft.resources.ResourceLocation, Object>> codecInstances = new java.util.concurrent.ConcurrentHashMap<>();

    public static void registerCustomPacketPayloadCodecInstance(Object codecInstance, java.util.Map<?, ?> map) {
        System.out.println("[EventBridgeHelper] Registering CustomPacketPayload$1 codec instance: " + codecInstance);
        java.util.concurrent.ConcurrentHashMap<net.minecraft.resources.ResourceLocation, Object> mutableMap = new java.util.concurrent.ConcurrentHashMap<>();
        for (java.util.Map.Entry<?, ?> entry : map.entrySet()) {
            mutableMap.put((net.minecraft.resources.ResourceLocation) entry.getKey(), entry.getValue());
        }
        
        // Register any already-known custom channels
        for (net.minecraft.resources.ResourceLocation channelId : REGISTERED_CHANNELS) {
            mutableMap.put(channelId, ChainloaderPayload.STREAM_CODEC(channelId));
        }
        
        try {
            java.lang.reflect.Field mapField = null;
            Class<?> clazz = codecInstance.getClass();
            try {
                mapField = clazz.getDeclaredField("a");
            } catch (NoSuchFieldException e) {
                // Try obfuscated or mapped names
                for (java.lang.reflect.Field f : clazz.getDeclaredFields()) {
                    if (java.util.Map.class.isAssignableFrom(f.getType())) {
                        mapField = f;
                        break;
                    }
                }
            }
            if (mapField != null) {
                mapField.setAccessible(true);
                mapField.set(codecInstance, mutableMap);
                codecInstances.put(codecInstance, mutableMap);
                System.out.println("[EventBridgeHelper] Successfully replaced CustomPacketPayload$1 map field with mutable ConcurrentHashMap copy. Size: " + mutableMap.size());
            } else {
                System.err.println("[EventBridgeHelper] Failed to find Map field in CustomPacketPayload$1 class " + clazz.getName());
            }
        } catch (Throwable t) {
            System.err.println("[EventBridgeHelper] Failed to replace CustomPacketPayload$1 map field:");
            t.printStackTrace();
        }
    }

    public static void ensureChannelRegistered(net.minecraft.resources.ResourceLocation channelId) {
        if (REGISTERED_CHANNELS.add(channelId)) {
            System.out.println("[EventBridgeHelper] Dynamically registering custom payload channel on the fly: " + channelId);
            for (java.util.concurrent.ConcurrentHashMap<net.minecraft.resources.ResourceLocation, Object> map : codecInstances.values()) {
                map.put(channelId, ChainloaderPayload.STREAM_CODEC(channelId));
            }
        }
    }

    private static void registerChannel(net.neoforged.neoforge.network.registration.PayloadRegistrar registrar, net.minecraft.resources.ResourceLocation channelId) {
        ensureChannelRegistered(channelId);
        if (!REGISTERED_NEOFORGE_CHANNELS.add(channelId)) {
            return;
        }
        System.out.println("[EventBridgeHelper] Dynamically registering custom payload channel on NeoForge: " + channelId);
        
        net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<ChainloaderPayload> payloadType = 
            new net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<>(channelId);
        
        registrar.playBidirectional(
            payloadType,
            ChainloaderPayload.STREAM_CODEC(channelId),
            (payload, context) -> {
                boolean isClient = context.flow() == net.minecraft.network.protocol.PacketFlow.CLIENTBOUND;
                
                context.enqueueWork(() -> {
                    try {
                        net.minecraft.network.FriendlyByteBuf buf = 
                            new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.wrappedBuffer(payload.data()));
                        
                        if (isClient) {
                            if (net.neoforged.fml.loading.FMLEnvironment.dist.isClient()) {
                                ClientPayloadHandlerHelper.receiveClientPayload(channelId, buf);
                            }
                        } else {
                            net.minecraft.server.level.ServerPlayer player = (net.minecraft.server.level.ServerPlayer) context.player();
                            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.PlayChannelHandler handler = 
                                net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.getGlobalReceiver(channelId);
                            if (handler != null) {
                                handler.receive(player.server, player, player.connection, buf, null);
                            } else {
                                System.err.println("[EventBridgeHelper] No ServerPlayNetworking receiver registered for channel: " + channelId);
                            }
                        }
                    } catch (Throwable t) {
                        System.err.println("[EventBridgeHelper] Failed to process payload for channel " + channelId + ":");
                        t.printStackTrace();
                    }
                });
            }
        );
    }

    private static class ClientPayloadHandlerHelper {
        static void receiveClientPayload(net.minecraft.resources.ResourceLocation channelId, net.minecraft.network.FriendlyByteBuf buf) {
            net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.PlayChannelHandler handler = 
                net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.getGlobalReceiver(channelId);
            if (handler != null) {
                handler.receive(client, client.getConnection(), buf, null);
            } else {
                System.err.println("[EventBridgeHelper] No ClientPlayNetworking receiver registered for channel: " + channelId);
            }
        }
    }

    public static final java.lang.ThreadLocal<Object> CURRENT_NBT_PROVIDER = new java.lang.ThreadLocal<>();

    public static Object getCurrentNbtProvider() {
        return CURRENT_NBT_PROVIDER.get();
    }

    public static void setCurrentNbtProvider(Object provider) {
        if (provider == null) {
            CURRENT_NBT_PROVIDER.remove();
        } else {
            CURRENT_NBT_PROVIDER.set(provider);
        }
    }

    public static java.util.concurrent.CompletableFuture<Void> enqueueWorkBridge(Object event, Runnable runnable) {
        runnable.run();
        return java.util.concurrent.CompletableFuture.completedFuture(null);
    }

    public static Object instantiateEventReflectively(Class<?> clazz) {
        try {
            // Try zero-argument constructor first
            try {
                java.lang.reflect.Constructor<?> ctor = clazz.getDeclaredConstructor();
                ctor.setAccessible(true);
                return ctor.newInstance();
            } catch (NoSuchMethodException e) {
                // Ignore
            }

            // Find constructor with arguments
            java.lang.reflect.Constructor<?>[] ctors = clazz.getDeclaredConstructors();
            if (ctors.length == 0) {
                return null;
            }
            // Sort by parameter count, prefer the one with fewer parameters
            java.util.Arrays.sort(ctors, (c1, c2) -> Integer.compare(c1.getParameterCount(), c2.getParameterCount()));
            java.lang.reflect.Constructor<?> ctor = ctors[0];
            ctor.setAccessible(true);
            Class<?>[] paramTypes = ctor.getParameterTypes();
            Object[] args = new Object[paramTypes.length];
            for (int i = 0; i < paramTypes.length; i++) {
                Class<?> type = paramTypes[i];
                if (type.isPrimitive()) {
                    if (type == boolean.class) args[i] = false;
                    else if (type == int.class) args[i] = 0;
                    else if (type == float.class) args[i] = 0.0f;
                    else if (type == double.class) args[i] = 0.0d;
                    else if (type == long.class) args[i] = 0L;
                    else if (type == char.class) args[i] = '\0';
                    else if (type == short.class) args[i] = (short)0;
                    else if (type == byte.class) args[i] = (byte)0;
                } else {
                    args[i] = null;
                }
            }
            return ctor.newInstance(args);
        } catch (Throwable t) {
            System.err.println("[ChainLoader] Failed to reflectively instantiate event class " + clazz.getName() + ":");
            t.printStackTrace();
            try {
                // Fallback to Unsafe allocation
                java.lang.reflect.Field theUnsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
                theUnsafeField.setAccessible(true);
                sun.misc.Unsafe unsafe = (sun.misc.Unsafe) theUnsafeField.get(null);
                return unsafe.allocateInstance(clazz);
            } catch (Throwable t2) {
                System.err.println("[ChainLoader] Unsafe fallback failed for event class " + clazz.getName() + ":");
                t2.printStackTrace();
            }
        }
        return null;
    }
}
