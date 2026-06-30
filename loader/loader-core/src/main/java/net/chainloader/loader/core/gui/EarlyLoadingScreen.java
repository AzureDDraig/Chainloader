package net.chainloader.loader.core.gui;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Controller for the early-startup graphics loading screen.
 * Lauches net.chainloader.loader.core.gui.EarlyLoadingScreenProcess in a separate JVM process
 * to isolate Swing/AWT from GLFW, preventing resizing conflicts and crashes.
 */
public class EarlyLoadingScreen {
    private static EarlyLoadingScreen instance;
    private Process guiProcess;
    private PrintWriter writer;
    private boolean headless = false;

    public static synchronized EarlyLoadingScreen getInstance() {
        if (instance == null) {
            instance = new EarlyLoadingScreen();
        }
        return instance;
    }

    private EarlyLoadingScreen() {
        if (java.awt.GraphicsEnvironment.isHeadless()) {
            System.out.println("[EarlyLoadingScreen] Headless environment detected. Falling back to stdout logging.");
            headless = true;
            return;
        }

        try {
            List<String> command = new ArrayList<>();
            command.add(System.getProperty("java.home") + "/bin/java");
            command.add("-cp");
            command.add(System.getProperty("java.class.path"));
            command.add("net.chainloader.loader.core.gui.EarlyLoadingScreenProcess");
            command.add("--keep-open");

            java.lang.ProcessBuilder pb = new java.lang.ProcessBuilder(command);
            pb.redirectErrorStream(true);
            guiProcess = pb.start();
            writer = new PrintWriter(guiProcess.getOutputStream(), true);

            // Consume GUI process stdout/stderr to prevent buffer blockages
            new Thread(() -> {
                try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(guiProcess.getInputStream()))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        System.out.println("[EarlyLoadingScreen GUI] " + line);
                    }
                } catch (Exception ignored) {}
            }).start();

        } catch (Throwable t) {
            System.err.println("[EarlyLoadingScreen] Failed to launch separate GUI process, falling back to stdout logging: " + t.getMessage());
            headless = true;
        }
    }

    public void show() {
        if (headless) {
            System.out.println("[EarlyLoadingScreen] Booting ChainLoader Engine...");
            return;
        }
        log("Booting ChainLoader Engine...");
    }

    public void updateProgress(int percent, String statusMessage) {
        if (headless || writer == null) {
            System.out.println("[EarlyLoadingScreen] Progress: " + percent + "% - " + statusMessage);
            return;
        }
        try {
            writer.println("progress:" + percent + ":" + statusMessage);
        } catch (Throwable ignored) {}
    }

    public boolean isModId(String category) {
        if (category == null || category.trim().isEmpty()) return false;
        try {
            for (net.chainloader.loader.core.ModScanner.DiscoveredMod mod : net.chainloader.loader.core.ModScanner.getDiscoveredMods()) {
                if (category.equalsIgnoreCase(mod.metadata.getId())) {
                    return true;
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    public String detectModIdFromThrowable(Throwable t) {
        if (t == null) return null;
        try {
            Throwable cause = t;
            while (cause != null) {
                for (StackTraceElement element : cause.getStackTrace()) {
                    String className = element.getClassName();
                    for (net.chainloader.loader.core.ModScanner.DiscoveredMod mod : net.chainloader.loader.core.ModScanner.getDiscoveredMods()) {
                        String modId = mod.metadata.getId();
                        if (className.toLowerCase().contains("." + modId.toLowerCase() + ".") || 
                            className.toLowerCase().contains("." + modId.toLowerCase() + "$") ||
                            className.toLowerCase().endsWith("." + modId.toLowerCase())) {
                            return modId;
                        }
                    }
                }
                cause = cause.getCause();
            }
        } catch (Throwable ignored) {}
        return null;
    }

    public void log(String message) {
        log("ChainLoader", message);
    }

    public void log(String category, String message) {
        String targetCategory = "ChainLoader";
        if (isModId(category)) {
            targetCategory = category;
        }
        if (headless || writer == null) {
            System.out.println("[EarlyLoadingScreen Log][" + targetCategory + "] " + message);
            return;
        }
        try {
            writer.println("log:" + targetCategory + ":" + message);
        } catch (Throwable ignored) {}
    }

    public void close() {
        if (headless || writer == null) {
            System.out.println("[EarlyLoadingScreen] Disposing bootloader GUI context.");
            return;
        }
        try {
            writer.println("close");
            writer.flush();
        } catch (Throwable ignored) {}
    }

    public synchronized void logError(String modId, String message, Throwable t) {
        String targetModId = modId;
        if (t != null) {
            String detected = detectModIdFromThrowable(t);
            if (detected != null) {
                targetModId = detected;
            }
        }
        
        String displayMsg = (message != null ? message : "");
        if (t != null) {
            if (!displayMsg.isEmpty()) {
                displayMsg += "\n";
            }
            java.io.StringWriter sw = new java.io.StringWriter();
            java.io.PrintWriter pw = new java.io.PrintWriter(sw);
            t.printStackTrace(pw);
            displayMsg += sw.toString();
        }

        log(targetModId, "[ERROR] " + displayMsg);

        // Write to logs/early-loader-errors.log
        try {
            java.io.File logDir = new java.io.File("logs");
            if (!logDir.exists()) {
                logDir.mkdirs();
            }
            java.io.File logFile = new java.io.File(logDir, "early-loader-errors.log");
            try (java.io.FileWriter fw = new java.io.FileWriter(logFile, true);
                 java.io.PrintWriter pw = new java.io.PrintWriter(fw)) {
                pw.println("=========================================");
                pw.println("Timestamp: " + java.time.LocalDateTime.now());
                pw.println("Mod ID: " + targetModId);
                pw.println("Message: " + message);
                if (t != null) {
                    t.printStackTrace(pw);
                }
                pw.println("=========================================");
                pw.println();
            }
        } catch (Exception e) {
            System.err.println("[EarlyLoadingScreen] Failed to write to early-loader-errors.log: " + e.getMessage());
        }
    }
}
