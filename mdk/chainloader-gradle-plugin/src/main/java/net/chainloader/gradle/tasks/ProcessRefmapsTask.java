package net.chainloader.gradle.tasks;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * ProcessRefmapsTask simulates the Mixin Refmap Processor subagent.
 * It coordinates with the DecompilerPipeline by consuming the mappings file
 * generated during the de-obfuscation/mapping task.
 */
public class ProcessRefmapsTask extends DefaultTask {

    @InputFile
    public final RegularFileProperty mappingsFile = getProject().getObjects().fileProperty();

    @OutputFile
    public final RegularFileProperty outputRefmap = getProject().getObjects().fileProperty();

    public ProcessRefmapsTask() {
        outputRefmap.convention(getProject().getLayout().getBuildDirectory().file("chainloader/mixin-refmap.json"));
    }

    public RegularFileProperty getMappingsFile() {
        return mappingsFile;
    }

    public RegularFileProperty getOutputRefmap() {
        return outputRefmap;
    }

    @TaskAction
    public void process() throws IOException {
        File mappings = mappingsFile.get().getAsFile();
        File refmap = outputRefmap.get().getAsFile();

        getLogger().lifecycle("==================================================");
        getLogger().lifecycle("ChainLoader Refmap Processor: Processing Mixin refmaps...");
        getLogger().lifecycle("Input Mappings File: " + mappings.getAbsolutePath());
        getLogger().lifecycle("Output Refmap File: " + refmap.getAbsolutePath());

        if (!refmap.getParentFile().exists()) {
            refmap.getParentFile().mkdirs();
        }

        // Mock remapping mixin reference maps using the tiny mappings file
        String mockRefmapContent = "{\n" +
                "  \"mappings\": {\n" +
                "    \"net/chainloader/mixin/ExampleMixin\": {\n" +
                "      \"tick\": \"Lnet/minecraft/client/Minecraft;tick()V\"\n" +
                "    }\n" +
                "  },\n" +
                "  \"data\": {\n" +
                "    \"Official\": {\n" +
                "      \"net/chainloader/mixin/ExampleMixin\": {\n" +
                "        \"tick\": \"La;tick()V\"\n" +
                "      }\n" +
                "    }\n" +
                "  }\n" +
                "}\n";

        Files.writeString(refmap.toPath(), mockRefmapContent, StandardCharsets.UTF_8);
        getLogger().lifecycle("Successfully processed and mapped refmaps (mocked).");
        getLogger().lifecycle("==================================================");
    }
}
