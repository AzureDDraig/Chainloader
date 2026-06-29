package net.chainloader.loader.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a normalized mod metadata descriptor.
 * This class abstracts the differences between mod metadata formats
 * (such as Fabric's fabric.mod.json and Forge/NeoForge's mods.toml) into a unified structure
 * for the ChainLoader modloader.
 */
public final class ChainModMetadata {
    private final String id;
    private final String version;
    private final String name;
    private final String description;
    private final String license;
    private final List<String> authors;
    private final Map<String, String> contactLinks;
    private final List<ModDependency> dependencies;
    private final Map<String, List<String>> entrypoints;
    private final List<String> mixins;
    private final String originalLoaderType; // "fabric", "forge", "neoforge", "chainloader"

    private ChainModMetadata(Builder builder) {
        this.id = builder.id;
        this.version = builder.version;
        this.name = builder.name;
        this.description = builder.description;
        this.license = builder.license;
        this.authors = List.copyOf(builder.authors);
        this.contactLinks = Map.copyOf(builder.contactLinks);
        this.dependencies = List.copyOf(builder.dependencies);
        this.entrypoints = Map.copyOf(builder.entrypoints);
        this.mixins = List.copyOf(builder.mixins);
        this.originalLoaderType = builder.originalLoaderType;
    }

    public String getId() {
        return id;
    }

    public String getVersion() {
        return version;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getLicense() {
        return license;
    }

    public List<String> getAuthors() {
        return authors;
    }

    public Map<String, String> getContactLinks() {
        return contactLinks;
    }

    public List<ModDependency> getDependencies() {
        return dependencies;
    }

    public Map<String, List<String>> getEntrypoints() {
        return entrypoints;
    }

    public List<String> getMixins() {
        return mixins;
    }

    public String getOriginalLoaderType() {
        return originalLoaderType;
    }

    @Override
    public String toString() {
        return "ChainModMetadata{" +
                "id='" + id + '\'' +
                ", version='" + version + '\'' +
                ", name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", license='" + license + '\'' +
                ", authors=" + authors +
                ", contactLinks=" + contactLinks +
                ", dependencies=" + dependencies +
                ", entrypoints=" + entrypoints +
                ", mixins=" + mixins +
                ", originalLoaderType='" + originalLoaderType + '\'' +
                '}';
    }

    public static class ModDependency {
        private final String modId;
        private final String versionRequirement;
        private final boolean optional;

        public ModDependency(String modId, String versionRequirement, boolean optional) {
            this.modId = modId;
            this.versionRequirement = versionRequirement;
            this.optional = optional;
        }

        public String getModId() {
            return modId;
        }

        public String getVersionRequirement() {
            return versionRequirement;
        }

        public boolean isOptional() {
            return optional;
        }

        @Override
        public String toString() {
            return "ModDependency{" +
                    "modId='" + modId + '\'' +
                    ", versionRequirement='" + versionRequirement + '\'' +
                    ", optional=" + optional +
                    '}';
        }
    }

    public static class Builder {
        private String id = "";
        private String version = "0.0.0";
        private String name = "";
        private String description = "";
        private String license = "";
        private final List<String> authors = new ArrayList<>();
        private final Map<String, String> contactLinks = new HashMap<>();
        private final List<ModDependency> dependencies = new ArrayList<>();
        private final Map<String, List<String>> entrypoints = new HashMap<>();
        private final List<String> mixins = new ArrayList<>();
        private String originalLoaderType = "unknown";

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder version(String version) {
            this.version = version;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder license(String license) {
            this.license = license;
            return this;
        }

        public String getLicense() {
            return license;
        }

        public Builder addAuthor(String author) {
            this.authors.add(author);
            return this;
        }

        public Builder addContactLink(String key, String value) {
            this.contactLinks.put(key, value);
            return this;
        }

        public Builder addDependency(String modId, String versionRequirement, boolean optional) {
            this.dependencies.add(new ModDependency(modId, versionRequirement, optional));
            return this;
        }

        public Builder addEntrypoint(String key, String className) {
            this.entrypoints.computeIfAbsent(key, k -> new ArrayList<>()).add(className);
            return this;
        }

        public Builder addMixin(String mixinFile) {
            this.mixins.add(mixinFile);
            return this;
        }

        public Builder originalLoaderType(String originalLoaderType) {
            this.originalLoaderType = originalLoaderType;
            return this;
        }

        public ChainModMetadata build() {
            if (id == null || id.isEmpty()) {
                throw new IllegalStateException("Mod ID must not be empty");
            }
            return new ChainModMetadata(this);
        }
    }
}
