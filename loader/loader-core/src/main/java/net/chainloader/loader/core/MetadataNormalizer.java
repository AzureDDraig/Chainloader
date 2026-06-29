package net.chainloader.loader.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MetadataNormalizer parses various mod metadata formats (e.g., Fabric's fabric.mod.json
 * and Forge/NeoForge's mods.toml) and normalizes them into a unified {@link ChainModMetadata} representation.
 * <p>
 * This normalizer uses lightweight, dependency-free regex and state-machine parsers to remain
 * compatible with bootstrapping environments and avoid external library dependencies.
 */
public final class MetadataNormalizer {

    private MetadataNormalizer() {}

    /**
     * Normalizes a metadata file based on its filename.
     *
     * @param filename the name of the file (e.g., "fabric.mod.json" or "mods.toml")
     * @param content  the string content of the file
     * @return the normalized metadata
     * @throws IllegalArgumentException if the format is unsupported or parsing fails
     */
    public static ChainModMetadata normalize(String filename, String content) {
        if (filename.equals("fabric.mod.json")) {
            return parseFabric(content);
        } else if (filename.equals("mods.toml")) {
            return parseForge(content);
        } else {
            throw new IllegalArgumentException("Unsupported mod metadata file format: " + filename);
        }
    }

    /**
     * Reads and normalizes a metadata file from the filesystem.
     *
     * @param path the path to the metadata file
     * @return the normalized metadata
     * @throws IOException if reading the file fails
     */
    public static ChainModMetadata normalize(Path path) throws IOException {
        String filename = path.getFileName().toString();
        byte[] bytes = Files.readAllBytes(path);
        String content = new String(bytes, StandardCharsets.UTF_8);
        return normalize(filename, content);
    }

    /**
     * Parses a Fabric metadata file (fabric.mod.json) and normalizes it.
     */
    public static ChainModMetadata parseFabric(String json) {
        ChainModMetadata.Builder builder = new ChainModMetadata.Builder();
        builder.originalLoaderType("fabric");

        // Extract basic fields
        String id = extractJsonField(json, "id");
        String version = extractJsonField(json, "version");
        String name = extractJsonField(json, "name");
        String description = extractJsonField(json, "description");
        String license = extractJsonField(json, "license");

        builder.id(id);
        if (!version.isEmpty()) builder.version(version);
        if (!name.isEmpty()) builder.name(name);
        if (!description.isEmpty()) builder.description(description);
        if (!license.isEmpty()) builder.license(license);

        // Extract authors (can be a list of strings or list of objects)
        List<String> authors = extractJsonArray(json, "authors");
        for (String author : authors) {
            builder.addAuthor(author);
        }

        // Extract contact links
        Map<String, String> contact = parseFabricDependencyMap(json, "contact");
        for (Map.Entry<String, String> entry : contact.entrySet()) {
            builder.addContactLink(entry.getKey(), entry.getValue());
        }

        // Extract dependencies
        Map<String, String> depends = parseFabricDependencyMap(json, "depends");
        for (Map.Entry<String, String> entry : depends.entrySet()) {
            builder.addDependency(entry.getKey(), entry.getValue(), false);
        }

        Map<String, String> recommends = parseFabricDependencyMap(json, "recommends");
        for (Map.Entry<String, String> entry : recommends.entrySet()) {
            builder.addDependency(entry.getKey(), entry.getValue(), true);
        }

        // Extract entrypoints
        Map<String, List<String>> entrypoints = parseFabricEntrypoints(json);
        for (Map.Entry<String, List<String>> entry : entrypoints.entrySet()) {
            for (String className : entry.getValue()) {
                builder.addEntrypoint(entry.getKey(), className);
            }
        }

        // Extract mixins
        List<String> mixins = extractJsonArray(json, "mixins");
        for (String mixin : mixins) {
            builder.addMixin(mixin);
        }

        return builder.build();
    }

    /**
     * Parses a Forge/NeoForge metadata file (mods.toml) and normalizes it.
     */
    public static ChainModMetadata parseForge(String content) {
        ChainModMetadata.Builder builder = new ChainModMetadata.Builder();
        builder.originalLoaderType("forge");

        String[] lines = content.split("\\r?\\n");
        String currentSection = "";
        boolean inMultilineDescription = false;
        StringBuilder descriptionBuilder = new StringBuilder();

        // Global properties that might be parsed
        String globalLicense = "";
        List<TempDependency> tempDeps = new ArrayList<>();
        TempDependency currentDep = null;

        for (String line : lines) {
            String trimmed = line.trim();

            // Handle multiline description
            if (inMultilineDescription) {
                if (trimmed.endsWith("'''") || trimmed.endsWith("\"\"\"")) {
                    inMultilineDescription = false;
                    String text = trimmed.substring(0, trimmed.length() - 3);
                    descriptionBuilder.append(text);
                    builder.description(descriptionBuilder.toString().trim());
                } else {
                    descriptionBuilder.append(line).append("\n");
                }
                continue;
            }

            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }

            // Section header check
            if (trimmed.startsWith("[[") && trimmed.endsWith("]]")) {
                currentSection = trimmed.substring(2, trimmed.length() - 2);
                if (currentSection.startsWith("dependencies.")) {
                    String target = currentSection.substring(currentSection.lastIndexOf('.') + 1);
                    currentDep = new TempDependency();
                    currentDep.targetModId = target;
                    tempDeps.add(currentDep);
                }
                continue;
            } else if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                currentSection = trimmed.substring(1, trimmed.length() - 1);
                continue;
            }

            // Key-value parsing
            int eqIndex = trimmed.indexOf('=');
            if (eqIndex != -1) {
                String key = trimmed.substring(0, eqIndex).trim();
                String value = trimmed.substring(eqIndex + 1).trim();

                // Strip quotes
                String cleanVal = value;
                if (cleanVal.startsWith("\"\"\"") || cleanVal.startsWith("'''")) {
                    inMultilineDescription = true;
                    descriptionBuilder.setLength(0);
                    String rest = cleanVal.substring(3);
                    if (rest.endsWith("'''") || rest.endsWith("\"\"\"")) {
                        inMultilineDescription = false;
                        cleanVal = rest.substring(0, rest.length() - 3);
                        if (key.equals("description")) {
                            builder.description(cleanVal.trim());
                        }
                    } else {
                        descriptionBuilder.append(rest).append("\n");
                    }
                    continue;
                } else if (cleanVal.startsWith("\"") && cleanVal.endsWith("\"")) {
                    cleanVal = cleanVal.substring(1, cleanVal.length() - 1);
                } else if (cleanVal.startsWith("'") && cleanVal.endsWith("'")) {
                    cleanVal = cleanVal.substring(1, cleanVal.length() - 1);
                }

                if (currentSection.equals("mods") || currentSection.isEmpty()) {
                    switch (key) {
                        case "modLoader":
                            if (cleanVal.trim().equalsIgnoreCase("neoforge")) {
                                builder.originalLoaderType("neoforge");
                            }
                            break;
                        case "modId":
                            builder.id(cleanVal);
                            break;
                        case "version":
                            builder.version(cleanVal);
                            break;
                        case "displayName":
                            builder.name(cleanVal);
                            break;
                        case "description":
                            builder.description(cleanVal);
                            break;
                        case "authors":
                            String[] splitAuthors = cleanVal.split(",");
                            for (String author : splitAuthors) {
                                String a = author.trim();
                                if (!a.isEmpty()) {
                                    builder.addAuthor(a);
                                }
                            }
                            break;
                        case "license":
                            builder.license(cleanVal);
                            break;
                        case "displayURL":
                            builder.addContactLink("homepage", cleanVal);
                            break;
                        case "issueTrackerURL":
                            builder.addContactLink("issues", cleanVal);
                            break;
                    }
                } else if (currentSection.startsWith("dependencies.") && currentDep != null) {
                    switch (key) {
                        case "modId":
                            currentDep.depModId = cleanVal;
                            break;
                        case "versionRange":
                            currentDep.versionRange = cleanVal;
                            break;
                        case "mandatory":
                            currentDep.mandatory = Boolean.parseBoolean(cleanVal);
                            break;
                    }
                }

                // If license is global and not set in mods section, use it
                if (key.equals("license") && currentSection.isEmpty()) {
                    globalLicense = cleanVal;
                }
            }
        }

        // Fallbacks
        if (builder.getLicense().isEmpty() && !globalLicense.isEmpty()) {
            builder.license(globalLicense);
        }

        // Add parsed dependencies
        for (TempDependency dep : tempDeps) {
            if (dep.depModId != null && !dep.depModId.isEmpty()) {
                builder.addDependency(dep.depModId, dep.versionRange, !dep.mandatory);
            }
        }

        return builder.build();
    }

    private static String extractJsonField(String json, String field) {
        Pattern pattern = Pattern.compile("\"" + field + "\"\\s*:\\s*\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    private static List<String> extractJsonArray(String json, String field) {
        List<String> list = new ArrayList<>();
        Pattern pattern = Pattern.compile("\"" + field + "\"\\s*:\\s*\\[([^\\]]*)\\]");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            String content = matcher.group(1);
            if (content.contains("{")) {
                Pattern namePattern = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"");
                Matcher nameMatcher = namePattern.matcher(content);
                while (nameMatcher.find()) {
                    list.add(nameMatcher.group(1));
                }
            } else {
                String[] items = content.split(",");
                for (String item : items) {
                    String clean = item.trim().replace("\"", "");
                    if (!clean.isEmpty()) {
                        list.add(clean);
                    }
                }
            }
        }
        return list;
    }

    private static Map<String, String> parseFabricDependencyMap(String json, String key) {
        Map<String, String> map = new HashMap<>();
        Pattern pattern = Pattern.compile("\"" + key + "\"\\s*:\\s*\\{([^}]+)\\}");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            String content = matcher.group(1);
            Pattern kvPattern = Pattern.compile("\"([^\"]+)\"\\s*:\\s*(\"[^\"]+\"|\\{[^}]*\\})");
            Matcher kvMatcher = kvPattern.matcher(content);
            while (kvMatcher.find()) {
                String k = kvMatcher.group(1);
                String val = kvMatcher.group(2);
                if (val.startsWith("\"")) {
                    map.put(k, val.substring(1, val.length() - 1));
                } else {
                    map.put(k, val);
                }
            }
        }
        return map;
    }

    private static Map<String, List<String>> parseFabricEntrypoints(String json) {
        Map<String, List<String>> entrypoints = new HashMap<>();
        Pattern pattern = Pattern.compile("\"entrypoints\"\\s*:\\s*\\{([^}]+)\\}");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            String content = matcher.group(1);
            Pattern kvPattern = Pattern.compile("\"([^\"]+)\"\\s*:\\s*(\\[[^\\]]*\\]|\"[^\"]+\")");
            Matcher kvMatcher = kvPattern.matcher(content);
            while (kvMatcher.find()) {
                String key = kvMatcher.group(1);
                String val = kvMatcher.group(2);
                List<String> classes = new ArrayList<>();
                if (val.startsWith("[")) {
                    String arrayContent = val.substring(1, val.length() - 1);
                    String[] items = arrayContent.split(",");
                    for (String item : items) {
                        String clean = item.trim().replace("\"", "");
                        if (!clean.isEmpty()) {
                            classes.add(clean);
                        }
                    }
                } else {
                    classes.add(val.replace("\"", "").trim());
                }
                entrypoints.put(key, classes);
            }
        }
        return entrypoints;
    }

    private static class TempDependency {
        String targetModId;
        String depModId;
        String versionRange = "";
        boolean mandatory = true;
    }
}
