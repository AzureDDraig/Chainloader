package net.minecraft.resources;

/**
 * Compile-time stub for net.minecraft.resources.ResourceLocation.
 */
public class ResourceLocation {
    private final String namespace;
    private final String path;

    public ResourceLocation(String id) {
        int index = id.indexOf(':');
        if (index >= 0) {
            this.namespace = id.substring(0, index);
            this.path = id.substring(index + 1);
        } else {
            this.namespace = "minecraft";
            this.path = id;
        }
    }

    public ResourceLocation(String namespace, String path) {
        this.namespace = namespace;
        this.path = path;
    }

    public static ResourceLocation fromNamespaceAndPath(String namespace, String path) {
        return new ResourceLocation(namespace, path);
    }

    public String getNamespace() {
        return namespace;
    }

    public String getPath() {
        return path;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ResourceLocation that = (ResourceLocation) o;
        return namespace.equals(that.namespace) && path.equals(that.path);
    }

    @Override
    public int hashCode() {
        return 31 * namespace.hashCode() + path.hashCode();
    }

    @Override
    public String toString() {
        return namespace + ":" + path;
    }
}
