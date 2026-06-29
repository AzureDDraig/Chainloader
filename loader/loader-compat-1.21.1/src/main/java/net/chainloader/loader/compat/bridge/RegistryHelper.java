package net.chainloader.loader.compat.bridge;

import net.minecraft.resources.ResourceLocation;
import java.util.WeakHashMap;

public class RegistryHelper {
    public static final WeakHashMap<Object, ResourceLocation> REGISTRY_NAMES = new WeakHashMap<>();

    public static Object setRegistryName(Object obj, ResourceLocation name) {
        REGISTRY_NAMES.put(obj, name);
        return obj;
    }

    public static Object setRegistryName(Object obj, String name) {
        return setRegistryName(obj, parseLocation(name));
    }

    public static Object setRegistryName(Object obj, String modId, String name) {
        return setRegistryName(obj, ResourceLocation.fromNamespaceAndPath(modId, name));
    }

    public static ResourceLocation getRegistryName(Object obj) {
        return REGISTRY_NAMES.get(obj);
    }

    public static ResourceLocation parseLocation(String locationStr) {
        int colonIdx = locationStr.indexOf(':');
        if (colonIdx == -1) {
            return ResourceLocation.fromNamespaceAndPath("minecraft", locationStr);
        } else {
            return ResourceLocation.fromNamespaceAndPath(locationStr.substring(0, colonIdx), locationStr.substring(colonIdx + 1));
        }
    }
}
