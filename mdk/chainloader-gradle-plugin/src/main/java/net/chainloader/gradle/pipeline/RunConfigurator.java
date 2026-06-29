package net.chainloader.gradle.pipeline;

import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.tasks.TaskAction;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * RunConfigurator manages the generation of IDE run configurations
 * for Fabric and NeoForge client/server environments.
 * It integrates with decompiler and refmap tasks to configure proper classpaths,
 * JVM arguments, and environment options.
 */
public class RunConfigurator {

    /**
     * Configures the Run Configuration tasks for the specified Gradle project.
     *
     * @param project the Gradle project to configure
     */
    public static void configure(Project project) {
        // Register individual generator tasks
        project.getTasks().register("generateIntellijRuns", GenerateIntellijRunsTask.class);
        project.getTasks().register("generateVsCodeRuns", GenerateVsCodeRunsTask.class);
        project.getTasks().register("generateEclipseRuns", GenerateEclipseRunsTask.class);

        // Register a lifecycle task to generate all run configurations
        project.getTasks().register("generateRunConfigs", task -> {
            task.setGroup("chainloader");
            task.setDescription("Generates run configurations for all supported IDEs (IntelliJ IDEA, VS Code, Eclipse).");
            task.dependsOn("generateIntellijRuns", "generateVsCodeRuns", "generateEclipseRuns");
        });

        // Ensure run configurations are generated before or during setup
        project.afterEvaluate(p -> {
            // Coordinate with Gradle decompiler task if present
            Task decompileTask = p.getTasks().findByName("decompileMinecraft");
            if (decompileTask != null) {
                p.getLogger().lifecycle("ChainLoader: Found Gradle Decompiler task. Coordinating run configurations to depend on decompile.");
                p.getTasks().getByName("generateRunConfigs").dependsOn(decompileTask);
            }

            // Coordinate with Refmap processor task if present
            Task refmapTask = p.getTasks().findByName("processRefmaps");
            if (refmapTask != null) {
                p.getLogger().lifecycle("ChainLoader: Found Refmap Processor task. Coordinating run configurations to depend on refmap processing.");
                p.getTasks().getByName("generateRunConfigs").dependsOn(refmapTask);
            }
        });
    }

    /**
     * Helper to resolve the Minecraft decompiler output source JAR.
     * Attempts to read project properties or defaults to a standardized build path.
     */
    private static String resolveDecompilerSources(Project project) {
        if (project.hasProperty("chainloader.decompiler.sources")) {
            return String.valueOf(project.property("chainloader.decompiler.sources"));
        }
        // Fallback to build directory target of the decompiler subagent
        return new File(project.getBuildDir(), "decompiler/minecraft-sources.jar").getAbsolutePath();
    }

    /**
     * Helper to resolve the Mixin Refmap output path.
     * Attempts to read project properties or defaults to the resources build directory.
     */
    private static String resolveRefmapPath(Project project) {
        if (project.hasProperty("chainloader.refmap.output")) {
            return String.valueOf(project.property("chainloader.refmap.output"));
        }
        return new File(project.getBuildDir(), "resources/main/" + project.getName() + "-refmap.json").getAbsolutePath();
    }

    /**
     * Task to generate IntelliJ IDEA XML run configuration files.
     */
    public static class GenerateIntellijRunsTask extends DefaultTask {
        public GenerateIntellijRunsTask() {
            setGroup("chainloader");
            setDescription("Generates IntelliJ IDEA .run XML configuration files.");
        }

        @TaskAction
        public void execute() {
            Project project = getProject();
            File ideaRunConfigDir = new File(project.getRootDir(), ".idea/runConfigurations");
            if (!ideaRunConfigDir.exists()) {
                ideaRunConfigDir.mkdirs();
            }

            String decompilerSources = resolveDecompilerSources(project);
            String refmapPath = resolveRefmapPath(project);
            String projectName = project.getName();

            writeIdeaConfig(ideaRunConfigDir, "Run_Fabric_Client",
                    "net.fabricmc.loader.impl.launch.knot.KnotClient",
                    "-Dfabric.development=true -Dchainloader.decompiler.sources=\"" + decompilerSources + "\" -Dmixin.config=\"" + projectName + ".mixins.json\" -Dmixin.refmap=\"" + refmapPath + "\"",
                    "--username Player",
                    "run-fabric",
                    projectName);

            writeIdeaConfig(ideaRunConfigDir, "Run_Fabric_Server",
                    "net.fabricmc.loader.impl.launch.knot.KnotServer",
                    "-Dfabric.development=true -Dchainloader.decompiler.sources=\"" + decompilerSources + "\" -Dmixin.config=\"" + projectName + ".mixins.json\" -Dmixin.refmap=\"" + refmapPath + "\"",
                    "--nogui",
                    "run-fabric-server",
                    projectName);

            writeIdeaConfig(ideaRunConfigDir, "Run_NeoForge_Client",
                    "net.neoforged.bootstrap.CommandLineHost",
                    "-Dnet.neoforged.development=true -Dchainloader.decompiler.sources=\"" + decompilerSources + "\"",
                    "--gameDir .",
                    "run-neoforge",
                    projectName);

            writeIdeaConfig(ideaRunConfigDir, "Run_NeoForge_Server",
                    "net.neoforged.bootstrap.CommandLineHost",
                    "-Dnet.neoforged.development=true -Dchainloader.decompiler.sources=\"" + decompilerSources + "\"",
                    "--nogui",
                    "run-neoforge-server",
                    projectName);

            project.getLogger().lifecycle("ChainLoader: IntelliJ IDEA run configurations written to " + ideaRunConfigDir.getAbsolutePath());
        }

        private void writeIdeaConfig(File dir, String configName, String mainClass, String vmArgs, String programArgs, String workDir, String moduleName) {
            String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                    "<component name=\"ProjectRunConfigurationManager\">\n" +
                    "  <configuration default=\"false\" name=\"" + configName.replace("_", " ") + "\" type=\"Application\" factoryName=\"Application\">\n" +
                    "    <option name=\"MAIN_CLASS_NAME\" value=\"" + mainClass + "\" />\n" +
                    "    <module name=\"" + moduleName + ".main\" />\n" +
                    "    <option name=\"VM_PARAMETERS\" value=\"" + vmArgs + "\" />\n" +
                    "    <option name=\"PROGRAM_PARAMETERS\" value=\"" + programArgs + "\" />\n" +
                    "    <option name=\"WORKING_DIRECTORY\" value=\"$PROJECT_DIR$/" + workDir + "\" />\n" +
                    "    <method v=\"2\">\n" +
                    "      <option name=\"Gradle.BeforeRunTask\" enabled=\"true\" tasks=\"classes\" />\n" +
                    "    </method>\n" +
                    "  </configuration>\n" +
                    "</component>\n";
            try {
                Files.write(new File(dir, configName + ".xml").toPath(), xml.getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                getProject().getLogger().error("Failed to write IntelliJ configuration for " + configName, e);
            }
        }
    }

    /**
     * Task to generate/update VS Code launch.json configuration.
     */
    public static class GenerateVsCodeRunsTask extends DefaultTask {
        public GenerateVsCodeRunsTask() {
            setGroup("chainloader");
            setDescription("Generates VS Code launch.json configuration file.");
        }

        @TaskAction
        public void execute() {
            Project project = getProject();
            File vscodeDir = new File(project.getRootDir(), ".vscode");
            if (!vscodeDir.exists()) {
                vscodeDir.mkdirs();
            }

            String decompilerSources = resolveDecompilerSources(project).replace("\\", "/");
            String refmapPath = resolveRefmapPath(project).replace("\\", "/");
            String projectName = project.getName();

            File launchFile = new File(vscodeDir, "launch.json");
            String jsonContent = "{\n" +
                    "    \"version\": \"0.2.0\",\n" +
                    "    \"configurations\": [\n" +
                    "        {\n" +
                    "            \"type\": \"java\",\n" +
                    "            \"name\": \"Run Fabric Client\",\n" +
                    "            \"request\": \"launch\",\n" +
                    "            \"mainClass\": \"net.fabricmc.loader.impl.launch.knot.KnotClient\",\n" +
                    "            \"vmArgs\": \"-Dfabric.development=true -Dchainloader.decompiler.sources=\\\"" + decompilerSources + "\\\" -Dmixin.config=\\\"" + projectName + ".mixins.json\\\" -Dmixin.refmap=\\\"" + refmapPath + "\\\"\",\n" +
                    "            \"args\": \"--username Player\",\n" +
                    "            \"cwd\": \"${workspaceFolder}/run-fabric\",\n" +
                    "            \"projectName\": \"" + projectName + "\"\n" +
                    "        },\n" +
                    "        {\n" +
                    "            \"type\": \"java\",\n" +
                    "            \"name\": \"Run Fabric Server\",\n" +
                    "            \"request\": \"launch\",\n" +
                    "            \"mainClass\": \"net.fabricmc.loader.impl.launch.knot.KnotServer\",\n" +
                    "            \"vmArgs\": \"-Dfabric.development=true -Dchainloader.decompiler.sources=\\\"" + decompilerSources + "\\\" -Dmixin.config=\\\"" + projectName + ".mixins.json\\\" -Dmixin.refmap=\\\"" + refmapPath + "\\\"\",\n" +
                    "            \"args\": \"--nogui\",\n" +
                    "            \"cwd\": \"${workspaceFolder}/run-fabric-server\",\n" +
                    "            \"projectName\": \"" + projectName + "\"\n" +
                    "        },\n" +
                    "        {\n" +
                    "            \"type\": \"java\",\n" +
                    "            \"name\": \"Run NeoForge Client\",\n" +
                    "            \"request\": \"launch\",\n" +
                    "            \"mainClass\": \"net.neoforged.bootstrap.CommandLineHost\",\n" +
                    "            \"vmArgs\": \"-Dnet.neoforged.development=true -Dchainloader.decompiler.sources=\\\"" + decompilerSources + "\\\"\",\n" +
                    "            \"args\": \"--gameDir .\",\n" +
                    "            \"cwd\": \"${workspaceFolder}/run-neoforge\",\n" +
                    "            \"projectName\": \"" + projectName + "\"\n" +
                    "        },\n" +
                    "        {\n" +
                    "            \"type\": \"java\",\n" +
                    "            \"name\": \"Run NeoForge Server\",\n" +
                    "            \"request\": \"launch\",\n" +
                    "            \"mainClass\": \"net.neoforged.bootstrap.CommandLineHost\",\n" +
                    "            \"vmArgs\": \"-Dnet.neoforged.development=true -Dchainloader.decompiler.sources=\\\"" + decompilerSources + "\\\"\",\n" +
                    "            \"args\": \"--nogui\",\n" +
                    "            \"cwd\": \"${workspaceFolder}/run-neoforge-server\",\n" +
                    "            \"projectName\": \"" + projectName + "\"\n" +
                    "        }\n" +
                    "    ]\n" +
                    "}\n";

            try {
                Files.write(launchFile.toPath(), jsonContent.getBytes(StandardCharsets.UTF_8));
                project.getLogger().lifecycle("ChainLoader: VS Code launch.json written to " + launchFile.getAbsolutePath());
            } catch (IOException e) {
                project.getLogger().error("Failed to write VS Code launch.json", e);
            }
        }
    }

    /**
     * Task to generate Eclipse launch configuration files.
     */
    public static class GenerateEclipseRunsTask extends DefaultTask {
        public GenerateEclipseRunsTask() {
            setGroup("chainloader");
            setDescription("Generates Eclipse .launch configuration files.");
        }

        @TaskAction
        public void execute() {
            Project project = getProject();
            File eclipseDir = project.getRootDir();

            String decompilerSources = resolveDecompilerSources(project);
            String refmapPath = resolveRefmapPath(project);
            String projectName = project.getName();

            writeEclipseConfig(eclipseDir, "Run_Fabric_Client",
                    "net.fabricmc.loader.impl.launch.knot.KnotClient",
                    "-Dfabric.development=true -Dchainloader.decompiler.sources=\"" + decompilerSources + "\" -Dmixin.config=\"" + projectName + ".mixins.json\" -Dmixin.refmap=\"" + refmapPath + "\"",
                    "--username Player",
                    "run-fabric",
                    projectName);

            writeEclipseConfig(eclipseDir, "Run_NeoForge_Client",
                    "net.neoforged.bootstrap.CommandLineHost",
                    "-Dnet.neoforged.development=true -Dchainloader.decompiler.sources=\"" + decompilerSources + "\"",
                    "--gameDir .",
                    "run-neoforge",
                    projectName);

            project.getLogger().lifecycle("ChainLoader: Eclipse launch configurations written to " + eclipseDir.getAbsolutePath());
        }

        private void writeEclipseConfig(File dir, String configName, String mainClass, String vmArgs, String programArgs, String workDir, String projectName) {
            String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n" +
                    "<launchConfiguration type=\"org.eclipse.jdt.launching.localJavaApplication\">\n" +
                    "    <listAttribute key=\"org.eclipse.debug.core.MAPPED_RESOURCE_PATHS\">\n" +
                    "        <listEntry value=\"/" + projectName + "\"/>\n" +
                    "    </listAttribute>\n" +
                    "    <listAttribute key=\"org.eclipse.debug.core.MAPPED_RESOURCE_TYPES\">\n" +
                    "        <listEntry value=\"4\"/>\n" +
                    "    </listAttribute>\n" +
                    "    <booleanAttribute key=\"org.eclipse.jdt.launching.ATTR_ATTR_USE_ARGFILE\" value=\"false\"/>\n" +
                    "    <booleanAttribute key=\"org.eclipse.jdt.launching.ATTR_SHOW_CODETEMPLATE_ONLY_CONFIRMATION\" value=\"true\"/>\n" +
                    "    <booleanAttribute key=\"org.eclipse.jdt.launching.ATTR_USE_CLASSPATH_ONLY_JAR\" value=\"false\"/>\n" +
                    "    <booleanAttribute key=\"org.eclipse.jdt.launching.ATTR_USE_START_ON_FIRST_THREAD\" value=\"true\"/>\n" +
                    "    <stringAttribute key=\"org.eclipse.jdt.launching.MAIN_TYPE\" value=\"" + mainClass + "\"/>\n" +
                    "    <stringAttribute key=\"org.eclipse.jdt.launching.MODULE_NAME\" value=\"" + projectName + "\"/>\n" +
                    "    <stringAttribute key=\"org.eclipse.jdt.launching.PROGRAM_ARGUMENTS\" value=\"" + programArgs + "\"/>\n" +
                    "    <stringAttribute key=\"org.eclipse.jdt.launching.PROJECT_ATTR\" value=\"" + projectName + "\"/>\n" +
                    "    <stringAttribute key=\"org.eclipse.jdt.launching.VM_ARGUMENTS\" value=\"" + vmArgs + "\"/>\n" +
                    "    <stringAttribute key=\"org.eclipse.jdt.launching.WORKING_DIRECTORY\" value=\"${workspace_loc:" + projectName + "}/" + workDir + "\"/>\n" +
                    "</launchConfiguration>\n";

            try {
                Files.write(new File(dir, configName + ".launch").toPath(), xml.getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                getProject().getLogger().error("Failed to write Eclipse configuration for " + configName, e);
            }
        }
    }
}
