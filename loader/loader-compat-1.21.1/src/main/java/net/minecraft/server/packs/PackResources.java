package net.minecraft.server.packs;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

public interface PackResources extends AutoCloseable {
    net.minecraft.server.packs.resources.IoSupplier<InputStream> getRootResource(String... paths);
    net.minecraft.server.packs.resources.IoSupplier<InputStream> getResource(PackType type, net.minecraft.resources.ResourceLocation location);
    void listResources(PackType type, String namespace, String path, ResourceOutput resourceOutput);
    Set<String> getNamespaces(PackType type);
    <T> T getMetadataSection(net.minecraft.server.packs.metadata.MetadataSectionSerializer<T> serializer) throws IOException;
    String packId();
    boolean isBuiltin();
    net.minecraft.server.packs.PackLocationInfo location();
    default void close() {}

    interface ResourceOutput extends java.util.function.BiConsumer<net.minecraft.resources.ResourceLocation, net.minecraft.server.packs.resources.IoSupplier<java.io.InputStream>> {
    }
}
