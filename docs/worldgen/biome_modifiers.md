# Biome Modifiers & Climate Redirects

In Minecraft 1.21.1, the `Biome` climate settings were heavily refactored. Legacy methods to query precipitation type or humidity directly on the `Biome` class were either removed, split into separate records (`ClimateSettings`), or wrapped inside registry lookup helpers.

ChainLoader intercepts legacy mod calls targeting `Biome` precipitation and downfall and redirects them to unified compat layers.

---

## 1. Bytecode Redirect Configuration

The bytecode transformer (`BytecodeTransformer.java`) intercepts calls targeting biome metadata:

*   **`getDownfall()` redirect:** Directly maps legacy downfall queries to the modern obfuscated/deobfuscated name (`m_47554_`).
*   **`getPrecipitation()` redirect:** Intercepts `INVOKEVIRTUAL` calls to `net/minecraft/world/level/biome/Biome` and redirects them to `EventBridgeHelper.getPrecipitationBridge(Object)`.
*   **`hasHumidity()` redirect:** Intercepts `INVOKEVIRTUAL` calls to `hasHumidity` (`m_47533_`) and redirects them to `EventBridgeHelper.hasHumidityBridge(Object)`.

```java
// BytecodeTransformer.java - Biome Redirect Instructions
boolean isBiome = "net/minecraft/world/level/biome/Biome".equals(owner) || "ddw".equals(owner);

boolean isGetDownfall = "getDownfall".equals(name) || "m_47548_".equals(name);
if (opcode == Opcodes.INVOKEVIRTUAL && isBiome && isGetDownfall) {
    String mappedName = remapper.mapMethodName(owner, "m_47554_", "()F");
    super.visitMethodInsn(opcode, owner, mappedName, "()F", isInterface);
    return;
}

boolean isGetPrecipitation = "getPrecipitation".equals(name) || "m_47530_".equals(name);
if (opcode == Opcodes.INVOKEVIRTUAL && isBiome && isGetPrecipitation) {
    super.visitMethodInsn(
        Opcodes.INVOKESTATIC,
        "net/chainloader/loader/compat/bridge/EventBridgeHelper",
        "getPrecipitationBridge",
        remapper.mapDesc("(Ljava/lang/Object;)Lnet/minecraft/world/level/biome/Biome$Precipitation;"),
        false
    );
    return;
}

boolean isHasHumidity = "hasHumidity".equals(name) || "m_47533_".equals(name);
if (opcode == Opcodes.INVOKEVIRTUAL && isBiome && isHasHumidity) {
    super.visitMethodInsn(
        Opcodes.INVOKESTATIC,
        "net/chainloader/loader/compat/bridge/EventBridgeHelper",
        "hasHumidityBridge",
        remapper.mapDesc("(Ljava/lang/Object;)Z"),
        false
    );
    return;
}
```

---

## 2. Precipitation Bridging

In 1.21.1, the biome precipitation type is determined dynamically by checking whether the biome has precipitation enabled, and querying the temperature at the target position. For legacy callers querying precipitation globally, `EventBridgeHelper.getPrecipitationBridge` mimics the 1.20 behavior by querying the base temperature.

```java
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
```

---

## 3. Humidity downfall Calculations

The legacy method `Biome.hasHumidity()` checked whether the biome's downfall was greater than `0.85f`. In modern versions, downfall is stored inside the nested `ClimateSettings` class (obfuscated as field `i` or `downfall`).

`EventBridgeHelper.hasHumidityBridge` extracts this field via reflection and performs the threshold check:

```java
public static boolean hasHumidityBridge(Object biomeObj) {
    if (biomeObj == null) return false;
    try {
        java.lang.reflect.Field climateSettingsField = null;
        try {
            climateSettingsField = biomeObj.getClass().getDeclaredField("i"); // Obfuscated
        } catch (NoSuchFieldException e) {
            // Deobfuscated / type-search
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
                        // Fallback: search for float fields in ClimateSettings
                        int floatCount = 0;
                        for (java.lang.reflect.Field f : climateSettings.getClass().getDeclaredFields()) {
                            if (f.getType() == float.class) {
                                floatCount++;
                                if (floatCount == 2) { // Downfall is typically the second float field
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
```
This dynamic field scanning ensures that even when obfuscation mapping varies, the climate properties can be successfully resolved.
