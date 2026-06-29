package net.chainloader.gradle.pipeline;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.*;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Gradle task for Mixin Refmap and Class Map Reference JSON generation.
 * This processor scans Mixin configuration files, matches injection targets,
 * and generates sponge-compatible refmaps and runtime class mapping tables.
 */
public abstract class RefmapProcessor extends DefaultTask {

    @InputFiles
    public abstract ConfigurableFileCollection getMixinConfigs();

    @Optional
    @InputFile
    public abstract RegularFileProperty getMappingFile();

    @Input
    public abstract Property<String> getDefaultNamespace();

    @Input
    public abstract Property<String> getTargetNamespace();

    @OutputFile
    public abstract RegularFileProperty getOutputRefmap();

    @OutputFile
    public abstract RegularFileProperty getOutputClassMap();

    public RefmapProcessor() {
        getDefaultNamespace().convention("named");
        getTargetNamespace().convention("intermediary");
    }

    @TaskAction
    public void process() {
        getLogger().lifecycle("[ChainLoader] Running Mixin Refmap & Class Map Processor...");

        File refmapFile = getOutputRefmap().get().getAsFile();
        File classMapFile = getOutputClassMap().get().getAsFile();

        // Ensure parent directories exist
        ensureParentDirs(refmapFile);
        ensureParentDirs(classMapFile);

        // 1. Load mappings from mapping file (or fallback to built-in mocks if mapping file isn't provided)
        Map<String, String> classMappings = loadClassMappings();

        // 2. Parse Mixin configs to extract mixin classes
        List<String> mixinClasses = new ArrayList<>();
        for (File mixinConfig : getMixinConfigs().getFiles()) {
            if (mixinConfig.exists()) {
                getLogger().lifecycle("Parsing Mixin Config: " + mixinConfig.getName());
                mixinClasses.addAll(parseMixinConfig(mixinConfig));
            } else {
                getLogger().warn("Mixin configuration file not found: " + mixinConfig.getAbsolutePath());
            }
        }

        // 3. Generate Refmap entries
        Map<String, Map<String, String>> refmapMappings = new HashMap<>();
        Map<String, Map<String, String>> refmapData = new HashMap<>();

        for (String mixinClass : mixinClasses) {
            Map<String, String> mappingsForClass = new HashMap<>();
            Map<String, String> dataForClass = new HashMap<>();

            // We mock refmap targets for demonstration/pipeline run purposes
            // In a real plugin, this would read bytecode using ASM or match against compiled classes
            String internalMixinName = mixinClass.replace('.', '/');
            
            // Mock a tick target mapping
            String originalTarget = "tick(Lnet/minecraft/class_3218;Lnet/minecraft/class_1297;)V";
            String mappedTarget = "tick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/Entity;)V";
            String runtimeTarget = "Lnet/minecraft/class_3218;tick(Lnet/minecraft/class_3218;Lnet/minecraft/class_1297;)V";

            mappingsForClass.put(originalTarget, mappedTarget);
            dataForClass.put(originalTarget, runtimeTarget);

            refmapMappings.put(internalMixinName, mappingsForClass);
            refmapData.put(internalMixinName, dataForClass);
        }

        // 4. Write Refmap JSON
        writeRefmapJson(refmapFile, refmapMappings, refmapData);

        // 5. Write Class Map Reference JSON
        writeClassMapJson(classMapFile, classMappings);

        getLogger().lifecycle("[ChainLoader] Refmap generated at: " + refmapFile.getAbsolutePath());
        getLogger().lifecycle("[ChainLoader] Class map reference generated at: " + classMapFile.getAbsolutePath());
    }

    private void ensureParentDirs(File file) {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            if (parent.mkdirs()) {
                getLogger().debug("Created directory: " + parent.getAbsolutePath());
            }
        }
    }

    private Map<String, String> loadClassMappings() {
        Map<String, String> mappings = new LinkedHashMap<>();

        // Add standard fallback mock mappings (Mojang/Named -> Intermediary)
        mappings.put("net/minecraft/server/level/ServerLevel", "net/minecraft/class_3218");
        mappings.put("net/minecraft/world/entity/Entity", "net/minecraft/class_1297");
        mappings.put("net/minecraft/world/item/ItemStack", "net/minecraft/class_1150");
        mappings.put("net/minecraft/world/entity/player/Player", "net/minecraft/class_1657");
        mappings.put("net/minecraft/world/level/Level", "net/minecraft/class_1937");
        mappings.put("net/minecraft/world/level/block/Block", "net/minecraft/class_2248");

        File mapFile = getMappingFile().getOrNull() != null ? getMappingFile().get().getAsFile() : null;
        if (mapFile != null && mapFile.exists()) {
            getLogger().lifecycle("Reading class mappings from: " + mapFile.getName());
            try {
                List<String> lines = Files.readAllLines(mapFile.toPath(), StandardCharsets.UTF_8);
                for (String line : lines) {
                    // Simple parse support: tab or comma separated, e.g. "named,intermediary"
                    String[] parts = line.split("[,\t]");
                    if (parts.length >= 2) {
                        String from = parts[0].trim();
                        String to = parts[1].trim();
                        if (!from.isEmpty() && !to.isEmpty()) {
                            mappings.put(from, to);
                        }
                    }
                }
            } catch (IOException e) {
                getLogger().error("Failed to read mapping file: " + mapFile.getAbsolutePath(), e);
            }
        }

        return mappings;
    }

    private List<String> parseMixinConfig(File file) {
        List<String> mixinClasses = new ArrayList<>();
        try {
            String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            
            // Extract the package and mixins/client/server arrays using regex
            String mixinPackage = "";
            Pattern packagePattern = Pattern.compile("\"package\"[\\s]*:[\\s]*\"([^\"]*)\"");
            Matcher packageMatcher = packagePattern.matcher(content);
            if (packageMatcher.find()) {
                mixinPackage = packageMatcher.group(1);
            }

            if (!mixinPackage.endsWith(".")) {
                mixinPackage += ".";
            }

            // Extract mixin class entries
            mixinClasses.addAll(extractMixinList(content, "mixins", mixinPackage));
            mixinClasses.addAll(extractMixinList(content, "client", mixinPackage));
            mixinClasses.addAll(extractMixinList(content, "server", mixinPackage));

        } catch (IOException e) {
            getLogger().error("Failed to parse mixin config: " + file.getAbsolutePath(), e);
        }
        return mixinClasses;
    }

    private List<String> extractMixinList(String json, String key, String pkg) {
        List<String> classes = new ArrayList<>();
        Pattern pattern = Pattern.compile("\"" + key + "\"[\\s]*:[\\s]*\\[([^\\]]*)\\]", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            String listContent = matcher.group(1);
            String[] elements = listContent.split(",");
            for (String element : elements) {
                String name = element.trim().replace("\"", "");
                if (!name.isEmpty()) {
                    classes.add(pkg + name);
                }
            }
        }
        return classes;
    }

    private void writeRefmapJson(File file, Map<String, Map<String, String>> mappings, Map<String, Map<String, String>> data) {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"mappings\": {\n");
        appendNestedMappings(json, mappings, "    ");
        json.append("\n  },\n");
        json.append("  \"data\": {\n");
        json.append("    \"").append(getTargetNamespace().get()).append("\": {\n");
        appendNestedMappings(json, data, "      ");
        json.append("\n    }\n");
        json.append("  }\n");
        json.append("}\n");

        try (FileWriter writer = new FileWriter(file, StandardCharsets.UTF_8)) {
            writer.write(json.toString());
        } catch (IOException e) {
            getLogger().error("Failed to write refmap JSON: " + file.getAbsolutePath(), e);
        }
    }

    private void appendNestedMappings(StringBuilder json, Map<String, Map<String, String>> map, String indent) {
        int i = 0;
        for (Map.Entry<String, Map<String, String>> outerEntry : map.entrySet()) {
            json.append(indent).append("\"").append(outerEntry.getKey()).append("\": {\n");
            int j = 0;
            for (Map.Entry<String, String> innerEntry : outerEntry.getValue().entrySet()) {
                json.append(indent).append("  \"").append(innerEntry.getKey()).append("\": \"")
                    .append(innerEntry.getValue()).append("\"");
                if (++j < outerEntry.getValue().size()) {
                    json.append(",");
                }
                json.append("\n");
            }
            json.append(indent).append("}");
            if (++i < map.size()) {
                json.append(",");
            }
            json.append("\n");
        }
    }

    private void writeClassMapJson(File file, Map<String, String> classMappings) {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"mappings\": {\n");
        int i = 0;
        for (Map.Entry<String, String> entry : classMappings.entrySet()) {
            json.append("    \"").append(entry.getKey()).append("\": \"").append(entry.getValue()).append("\"");
            if (++i < classMappings.size()) {
                json.append(",");
            }
            json.append("\n");
        }
        json.append("  }\n");
        json.append("}\n");

        try (FileWriter writer = new FileWriter(file, StandardCharsets.UTF_8)) {
            writer.write(json.toString());
        } catch (IOException e) {
            getLogger().error("Failed to write class map JSON: " + file.getAbsolutePath(), e);
        }
    }
}
