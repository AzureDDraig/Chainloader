package net.chainloader.gradle;

import net.chainloader.gradle.pipeline.DecompilerPipeline;
import net.chainloader.gradle.pipeline.RefmapProcessor;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.JavaExec;
import java.io.File;

/**
 * Gradle plugin for ChainLoader mod development environments.
 * Registers run configurations for launching Fabric and NeoForge client environments,
 * and sets up the decompiler pipeline and refmap processing tasks.
 */
public class ChainLoaderGradlePlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        // Initialize the Decompiler Pipeline extension
        DecompilerPipeline pipeline = project.getExtensions().create("decompilerPipeline", DecompilerPipeline.class, project);

        // Register the processRefmaps task, coordinating with DecompilerPipeline mappings
        var processRefmapsTask = project.getTasks().register("processRefmaps", RefmapProcessor.class, task -> {
            task.setGroup("chainloader");
            task.setDescription("Generates Mixin refmaps and runtime class map references.");

            // Standard layout search path for mixin configurations
            task.getMixinConfigs().from(
                project.fileTree("src/main/resources").matching(pattern -> {
                    pattern.include("**/*.mixins.json");
                    pattern.include("**/mixins.*.json");
                    pattern.include("**/mixin.*.json");
                })
            );

            // Default conventions for outputs
            task.getOutputRefmap().convention(
                project.getLayout().getBuildDirectory().file("chainloader/refmaps/mixins.refmap.json")
            );
            task.getOutputClassMap().convention(
                project.getLayout().getBuildDirectory().file("chainloader/mappings/classmap-reference.json")
            );

            // Coordinate with DecompilerPipeline: wire the mapping file output to RefmapProcessor input
            task.getMappingFile().set(pipeline.getMappingsFile());
        });

        // Register the legacy generateRefmaps task for backward compatibility / lifecycle simplicity
        project.getTasks().register("generateRefmaps", task -> {
            task.setGroup("chainloader");
            task.setDescription("Generates Mixin refmaps (delegates to processRefmaps).");
            task.dependsOn(processRefmapsTask);
        });

        // Register the runFabricClient task
        project.getTasks().register("runFabricClient", JavaExec.class, task -> {
            task.setGroup("chainloader");
            task.setDescription("Runs the Fabric client with ChainLoader development environment.");
            
            // Mock main class and working directory
            task.getMainClass().set("net.fabricmc.loader.impl.launch.knot.KnotClient");
            task.workingDir(new File(project.getProjectDir(), "run-fabric"));
            
            // Mock run setup: JVM arguments and program arguments
            task.jvmArgs("-Dfabric.development=true");
            task.args("--username", "Player");

            // Wiring mapped jar dependency (coordination with run configurator)
            task.classpath(project.files(pipeline.getMappedJar()));
            
            // Setup directory creation before execution
            task.doFirst(t -> {
                project.getLogger().lifecycle("Starting Fabric Client via ChainLoader...");
                File runDir = task.getWorkingDir();
                if (!runDir.exists()) {
                    if (runDir.mkdirs()) {
                        project.getLogger().lifecycle("Created Fabric run directory: " + runDir.getAbsolutePath());
                    }
                }
            });
        });

        // Register the runNeoForgeClient task
        project.getTasks().register("runNeoForgeClient", JavaExec.class, task -> {
            task.setGroup("chainloader");
            task.setDescription("Runs the NeoForge client with ChainLoader development environment.");
            
            // Mock main class and working directory
            task.getMainClass().set("net.neoforged.bootstrap.CommandLineHost");
            task.workingDir(new File(project.getProjectDir(), "run-neoforge"));
            
            // Mock run setup: JVM arguments and program arguments
            task.jvmArgs("-Dnet.neoforged.development=true");
            task.args("--gameDir", ".");

            // Wiring mapped jar dependency (coordination with run configurator)
            task.classpath(project.files(pipeline.getMappedJar()));
            
            // Setup directory creation before execution
            task.doFirst(t -> {
                project.getLogger().lifecycle("Starting NeoForge Client via ChainLoader...");
                File runDir = task.getWorkingDir();
                if (!runDir.exists()) {
                    if (runDir.mkdirs()) {
                        project.getLogger().lifecycle("Created NeoForge run directory: " + runDir.getAbsolutePath());
                    }
                }
            });
        });

        // Configure run configuration generation pipeline
        net.chainloader.gradle.pipeline.RunConfigurator.configure(project);

        project.getLogger().lifecycle("ChainLoader Gradle Plugin applied successfully!");
    }
}
