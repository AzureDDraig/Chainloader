package net.minecraftforge.server.permission.nodes;

import net.minecraft.resources.ResourceLocation;

public class PermissionNode<T> {
    
    public interface PermissionResolver<T> {
        T resolve(Object player, java.util.UUID uuid, PermissionDynamicContextKey... context);
    }

    public PermissionNode(ResourceLocation nodeName, PermissionType<T> type, PermissionResolver<T> defaultResolver, PermissionDynamicContextKey... dynamics) {}
    
    public PermissionNode(String modid, String nodeName, PermissionType<T> type, PermissionResolver<T> defaultResolver, PermissionDynamicContextKey... dynamics) {}

    public PermissionNode<T> setInformation(Object readableName, Object description) {
        return this;
    }
}
