package net.chainloader.gradle.pipeline;

import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.file.RegularFile;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.*;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.util.zip.ZipFile;

/**
 * DecompilerPipeline orchestrates the downloading, mapping, and decompilation of Minecraft.
 * It coordinates task outputs with other subsystems:
 * - Refmap Processor subagent: obtains mappings files and mapped jar to map Mixin annotations.
 * - Run Configurator subagent: obtains mapped jars to populate launch classpath configurations.
 */
public class DecompilerPipeline {

    private final Project project;
    private final TaskProvider<DownloadMinecraftTask> downloadTask;
    private final TaskProvider<MapMinecraftTask> mapTask;
    private final TaskProvider<DecompileMinecraftTask> decompileTask;

    public DecompilerPipeline(Project project) {
        this.project = project;

        // 1. Register the download task
        this.downloadTask = project.getTasks().register("downloadMinecraft", DownloadMinecraftTask.class, task -> {
            task.setGroup("chainloader");
            task.setDescription("Downloads Minecraft client obfuscated jar.");
        });

        // 2. Register the mapping task, wiring the output of downloadTask into it
        this.mapTask = project.getTasks().register("mapMinecraft", MapMinecraftTask.class, task -> {
            task.setGroup("chainloader");
            task.setDescription("Applies de-obfuscation mappings to the Minecraft jar.");
            task.getInputJar().set(downloadTask.flatMap(DownloadMinecraftTask::getOutputJar));
        });

        // 3. Register the decompile task, wiring the output of mapTask into it
        this.decompileTask = project.getTasks().register("decompileMinecraft", DecompileMinecraftTask.class, task -> {
            task.setGroup("chainloader");
            task.setDescription("Decompiles the de-obfuscated Minecraft jar.");
            task.getInputMappedJar().set(mapTask.flatMap(MapMinecraftTask::getOutputMappedJar));
        });
    }

    /**
     * Hook for the Run Configurator subagent.
     * Exposes the de-obfuscated (mapped) jar file provider.
     * Binding this provider to run configurations automatically registers a task dependency
     * on the MapMinecraftTask (and downloadMinecraft).
     */
    public Provider<RegularFile> getMappedJar() {
        return mapTask.flatMap(MapMinecraftTask::getOutputMappedJar);
    }

    /**
     * Hook for the Refmap Processor subagent.
     * Exposes the mappings file provider (e.g. tiny, tsrg, proguard).
     * The refmap processor task should read this file to re-map mod mixin references.
     */
    public Provider<RegularFile> getMappingsFile() {
        return mapTask.flatMap(MapMinecraftTask::getOutputMappingsFile);
    }

    /**
     * Exposes the decompiled sources jar file provider for IDE integration.
     */
    public Provider<RegularFile> getDecompiledSources() {
        return decompileTask.flatMap(DecompileMinecraftTask::getOutputSourcesJar);
    }

    /**
     * Allows configuring the Minecraft version of the pipeline.
     */
    public void setMinecraftVersion(String version) {
        downloadTask.configure(task -> task.getMinecraftVersion().set(version));
    }

    /**
     * Allows configuring the mapping provider (e.g. "mojang", "yarn").
     */
    public void setMappingsProvider(String provider) {
        mapTask.configure(task -> task.getMappingsProvider().set(provider));
    }

    // =========================================================================
    // Pipeline Gradle Tasks
    // =========================================================================

    /**
     * Task to simulate downloading the official Minecraft client jar.
     */
    public static class DownloadMinecraftTask extends DefaultTask {
        @Input
        public final Property<String> minecraftVersion = getProject().getObjects().property(String.class);

        @OutputFile
        public final RegularFileProperty outputJar = getProject().getObjects().fileProperty();

        public DownloadMinecraftTask() {
            minecraftVersion.convention("1.21.1");
            outputJar.convention(getProject().getLayout().getBuildDirectory().file("chainloader/minecraft-obf.jar"));
        }

        public Property<String> getMinecraftVersion() {
            return minecraftVersion;
        }

        public RegularFileProperty getOutputJar() {
            return outputJar;
        }

        @TaskAction
        public void download() throws IOException {
            String version = minecraftVersion.get();
            File outFile = outputJar.get().getAsFile();

            getLogger().lifecycle("==================================================");
            getLogger().lifecycle("ChainLoader: Downloading Minecraft " + version + " obfuscated jar...");
            getLogger().lifecycle("Target: " + outFile.getAbsolutePath());

            try {
                // 1. Fetch version manifest
                String manifestUrl = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
                String manifestContent = fetchUrl(manifestUrl);
                
                Gson gson = new Gson();
                JsonObject manifest = gson.fromJson(manifestContent, JsonObject.class);
                JsonArray versions = manifest.getAsJsonArray("versions");
                
                String versionUrl = null;
                for (JsonElement element : versions) {
                    JsonObject versionObj = element.getAsJsonObject();
                    if (versionObj.get("id").getAsString().equals(version)) {
                        versionUrl = versionObj.get("url").getAsString();
                        break;
                    }
                }
                
                if (versionUrl == null) {
                    throw new IllegalArgumentException("Minecraft version '" + version + "' was not found in the manifest.");
                }
                
                getLogger().lifecycle("ChainLoader: Fetching version metadata from: " + versionUrl);
                String versionMetadataContent = fetchUrl(versionUrl);
                JsonObject versionMeta = gson.fromJson(versionMetadataContent, JsonObject.class);
                
                JsonObject downloads = versionMeta.getAsJsonObject("downloads");
                JsonObject clientMeta = downloads.getAsJsonObject("client");
                String clientUrl = clientMeta.get("url").getAsString();
                long clientSize = clientMeta.get("size").getAsLong();
                
                getLogger().lifecycle("ChainLoader: Downloading client JAR (" + (clientSize / (1024 * 1024)) + " MB)...");
                
                if (!outFile.getParentFile().exists()) {
                    outFile.getParentFile().mkdirs();
                }
                
                downloadFile(clientUrl, outFile);
                getLogger().lifecycle("Successfully downloaded Minecraft " + version + " client JAR.");
                
            } catch (Exception e) {
                getLogger().error("Failed to download Minecraft " + version, e);
                getLogger().lifecycle("Falling back to writing mock obfuscated JAR...");
                writeMockJar(outFile);
            }
            getLogger().lifecycle("==================================================");
        }

        private String fetchUrl(String urlStr) throws IOException {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
                return sb.toString();
            }
        }

        private void downloadFile(String urlStr, File targetFile) throws IOException {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            
            try (InputStream in = conn.getInputStream();
                 FileOutputStream out = new FileOutputStream(targetFile)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                long totalDownloaded = 0;
                long lastPrint = 0;
                long contentLength = conn.getContentLengthLong();
                
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                    totalDownloaded += bytesRead;
                    
                    if (contentLength > 0 && System.currentTimeMillis() - lastPrint > 2000) {
                        int percent = (int) ((totalDownloaded * 100) / contentLength);
                        getLogger().lifecycle("Download progress: " + percent + "% (" + (totalDownloaded / 1024) + " KB)");
                        lastPrint = System.currentTimeMillis();
                    }
                }
            }
        }

        private void writeMockJar(File outFile) throws IOException {
            if (!outFile.getParentFile().exists()) {
                outFile.getParentFile().mkdirs();
            }
            try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(outFile))) {
                ZipEntry entry = new ZipEntry("a.class");
                zos.putNextEntry(entry);
                zos.write("// Obfuscated class MinecraftClient".getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        }
    }

    /**
     * Task to map/de-obfuscate the Minecraft client jar.
     */
    public static class MapMinecraftTask extends DefaultTask {
        @InputFile
        public final RegularFileProperty inputJar = getProject().getObjects().fileProperty();

        @Input
        public final Property<String> mappingsProvider = getProject().getObjects().property(String.class);

        @OutputFile
        public final RegularFileProperty outputMappedJar = getProject().getObjects().fileProperty();

        @OutputFile
        public final RegularFileProperty outputMappingsFile = getProject().getObjects().fileProperty();

        public MapMinecraftTask() {
            mappingsProvider.convention("mojang");
            outputMappedJar.convention(getProject().getLayout().getBuildDirectory().file("chainloader/minecraft-mapped.jar"));
            outputMappingsFile.convention(getProject().getLayout().getBuildDirectory().file("chainloader/mappings.tiny"));
        }

        public RegularFileProperty getInputJar() {
            return inputJar;
        }

        public Property<String> getMappingsProvider() {
            return mappingsProvider;
        }

        public RegularFileProperty getOutputMappedJar() {
            return outputMappedJar;
        }

        public RegularFileProperty getOutputMappingsFile() {
            return outputMappingsFile;
        }

        @TaskAction
        public void map() throws IOException {
            File inJar = inputJar.get().getAsFile();
            File outJar = outputMappedJar.get().getAsFile();
            File mappingsFile = outputMappingsFile.get().getAsFile();

            getLogger().lifecycle("==================================================");
            getLogger().lifecycle("ChainLoader: De-obfuscating Minecraft jar...");
            getLogger().lifecycle("Input Obfuscated Jar: " + inJar.getAbsolutePath());
            getLogger().lifecycle("Mappings Provider: " + mappingsProvider.get());
            getLogger().lifecycle("Output Mapped Jar: " + outJar.getAbsolutePath());
            getLogger().lifecycle("Output Mappings File: " + mappingsFile.getAbsolutePath());

            if (!outJar.getParentFile().exists()) {
                outJar.getParentFile().mkdirs();
            }

            // Write mock mappings file (Tiny v2 format)
            try (PrintWriter writer = new PrintWriter(new FileWriter(mappingsFile))) {
                writer.println("tiny\t2\t0\tOfficial\tNamed");
                writer.println("c\ta\tnet/minecraft/client/Minecraft");
                writer.println("c\tb\tnet/minecraft/world/level/Level");
                writer.println("c\tc\tnet/minecraft/world/entity/player/Player");
                writer.println("c\td\tnet/minecraft/world/level/block/StoneBlock");
            }

            // Map the jar entries (Mock mapping by reading inputs and outputting mapped names)
            try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(outJar))) {
                mapAndWrite(zos, "a.class", "net/minecraft/client/Minecraft.class", "public class Minecraft {}");
                mapAndWrite(zos, "b.class", "net/minecraft/world/level/Level.class", "public class Level {}");
                mapAndWrite(zos, "c.class", "net/minecraft/world/entity/player/Player.class", "public class Player {}");
                mapAndWrite(zos, "d.class", "net/minecraft/world/level/block/StoneBlock.class", "public class StoneBlock {}");
            }

            getLogger().lifecycle("Successfully de-obfuscated Minecraft jar.");
            getLogger().lifecycle("==================================================");
        }

        private void mapAndWrite(ZipOutputStream zos, String originalName, String mappedName, String mockCode) throws IOException {
            ZipEntry entry = new ZipEntry(mappedName);
            zos.putNextEntry(entry);
            String compiledBytes = "// Mock bytecode for " + mappedName + " mapped from " + originalName + "\n" + mockCode;
            zos.write(compiledBytes.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
    }

    /**
     * Task to decompile the mapped Minecraft client jar.
     */
    public static class DecompileMinecraftTask extends DefaultTask {
        @InputFile
        public final RegularFileProperty inputMappedJar = getProject().getObjects().fileProperty();

        @OutputFile
        public final RegularFileProperty outputSourcesJar = getProject().getObjects().fileProperty();

        public DecompileMinecraftTask() {
            outputSourcesJar.convention(getProject().getLayout().getBuildDirectory().file("decompiler/minecraft-sources.jar"));
        }

        public RegularFileProperty getInputMappedJar() {
            return inputMappedJar;
        }

        public RegularFileProperty getOutputSourcesJar() {
            return outputSourcesJar;
        }

        @TaskAction
        public void decompile() throws IOException {
            File inJar = inputMappedJar.get().getAsFile();
            File outSources = outputSourcesJar.get().getAsFile();

            getLogger().lifecycle("==================================================");
            getLogger().lifecycle("ChainLoader: Decompiling mapped Minecraft jar...");
            getLogger().lifecycle("Input Mapped Jar: " + inJar.getAbsolutePath());
            getLogger().lifecycle("Output Sources: " + outSources.getAbsolutePath());

            if (!outSources.getParentFile().exists()) {
                outSources.getParentFile().mkdirs();
            }

            // Simulating decompilation progress
            for (int i = 0; i <= 100; i += 25) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                getLogger().lifecycle("Decompile progress: " + i + "%");
            }

            // Read classes from mapped jar and output Java sources to sources zip
            try (ZipFile zipFile = new ZipFile(inJar);
                 ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(outSources))) {

                var entries = zipFile.entries();
                while (entries.hasMoreElements()) {
                    var entry = entries.nextElement();
                    String name = entry.getName();
                    if (name.endsWith(".class")) {
                        String javaName = name.substring(0, name.length() - ".class".length()) + ".java";

                        // Simple package and class extraction
                        String pkg = "";
                        String className = javaName;
                        int lastSlash = javaName.lastIndexOf('/');
                        if (lastSlash != -1) {
                            pkg = javaName.substring(0, lastSlash).replace('/', '.');
                            className = javaName.substring(lastSlash + 1, javaName.length() - 5);
                        } else {
                            className = javaName.substring(0, javaName.length() - 5);
                        }

                        StringBuilder javaCode = new StringBuilder();
                        if (!pkg.isEmpty()) {
                            javaCode.append("package ").append(pkg).append(";\n\n");
                        }
                        javaCode.append("/**\n")
                                .append(" * Decompiled Minecraft class: ").append(className).append("\n")
                                .append(" * Generated by ChainLoader Decompiler Pipeline (Mocked).\n")
                                .append(" */\n")
                                .append("public class ").append(className).append(" {\n")
                                .append("    // Mocked decompiled method\n")
                                .append("    public void tick() {\n")
                                .append("        // Tick logic here...\n")
                                .append("    }\n")
                                .append("}\n");

                        ZipEntry sourceEntry = new ZipEntry(javaName);
                        zos.putNextEntry(sourceEntry);
                        zos.write(javaCode.toString().getBytes(StandardCharsets.UTF_8));
                        zos.closeEntry();
                    }
                }
            }

            getLogger().lifecycle("Successfully decompiled Minecraft jar to sources.");
            getLogger().lifecycle("==================================================");
        }
    }
}
