package net.chainloader.loader.core;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;

/**
 * Dedicated debug logging system for ChainLoader.
 * Supports verbose debug logs for mod scanning, remapping, events posting, and injection stages.
 */
public final class Logging {
    private static final boolean DEBUG = Boolean.getBoolean("chainloader.debug") || "true".equalsIgnoreCase(System.getenv("CHAINLOADER_DEBUG"));

    private static PrintStream originalOut;
    private static PrintStream originalErr;
    private static FileOutputStream stdoutLogStream;
    private static FileOutputStream stderrLogStream;
    private static PrintWriter debugLogWriter;

    private Logging() {} // Prevent instantiation

    public static void initialize() {
        try {
            Path logsDir = Paths.get("logs");
            if (!Files.exists(logsDir)) {
                Files.createDirectories(logsDir);
            }

            originalOut = System.out;
            originalErr = System.err;

            stdoutLogStream = new FileOutputStream(new File("logs/stdout.log"), false);
            stderrLogStream = new FileOutputStream(new File("logs/stderr.log"), false);
            debugLogWriter = new PrintWriter(new FileWriter("logs/debug.log", false), true);

            // Redirect System.out to both original console and stdout.log
            System.setOut(new PrintStream(new OutputStream() {
                @Override
                public void write(int b) throws IOException {
                    originalOut.write(b);
                    stdoutLogStream.write(b);
                }
                @Override
                public void write(byte[] b, int off, int len) throws IOException {
                    originalOut.write(b, off, len);
                    stdoutLogStream.write(b, off, len);
                }
                @Override
                public void flush() throws IOException {
                    originalOut.flush();
                    stdoutLogStream.flush();
                }
            }, true));

            // Redirect System.err to both original console and stderr.log
            System.setErr(new PrintStream(new OutputStream() {
                @Override
                public void write(int b) throws IOException {
                    originalErr.write(b);
                    stderrLogStream.write(b);
                }
                @Override
                public void write(byte[] b, int off, int len) throws IOException {
                    originalErr.write(b, off, len);
                    stderrLogStream.write(b, off, len);
                }
                @Override
                public void flush() throws IOException {
                    originalErr.flush();
                    stderrLogStream.flush();
                }
            }, true));

            // Register shutdown hook to close streams
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    if (stdoutLogStream != null) stdoutLogStream.close();
                    if (stderrLogStream != null) stderrLogStream.close();
                    if (debugLogWriter != null) debugLogWriter.close();
                } catch (Exception ignored) {}
            }));

            debugLogWriter.println("=== ChainLoader Debug Log Initialized ===");
            originalOut.println("[Logging] STDOUT/STDERR log separation and debug log initialized successfully.");

        } catch (Exception e) {
            System.err.println("Failed to initialize log redirection: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static boolean isDebugEnabled() {
        return DEBUG;
    }

    public static void info(String format, Object... args) {
        log("INFO", format, args);
    }

    public static void warn(String format, Object... args) {
        log("WARN", format, args);
    }

    public static void error(String format, Object... args) {
        log("ERROR", format, args);
    }

    public static void error(String message, Throwable t) {
        log("ERROR", "%s: %s", message, getStackTrace(t));
    }

    public static void debug(String format, Object... args) {
        String message = (args == null || args.length == 0) ? format : String.format(format, args);
        writeToDebugLog("DEBUG", message);
        if (DEBUG) {
            logConsole("DEBUG", message);
        }
    }

    public static void debug(String stage, String format, Object... args) {
        String message = (args == null || args.length == 0) ? format : String.format(format, args);
        writeToDebugLog("DEBUG/" + stage, message);
        if (DEBUG) {
            logConsole("DEBUG/" + stage, message);
        }
    }

    private static void log(String level, String format, Object... args) {
        String message = (args == null || args.length == 0) ? format : String.format(format, args);
        writeToDebugLog(level, message);
        logConsole(level, message);
    }

    private static void logConsole(String level, String message) {
        PrintStream out = originalOut != null ? originalOut : System.out;
        out.printf("[ChainLoader/%s] %s%n", level, message);
    }

    private static synchronized void writeToDebugLog(String level, String message) {
        if (debugLogWriter != null) {
            LocalDateTime now = LocalDateTime.now();
            debugLogWriter.printf("[%s] [%s] %s%n", now.toString(), level, message);
        }
    }

    private static String getStackTrace(Throwable t) {
        if (t == null) return "";
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        t.printStackTrace(pw);
        return sw.toString();
    }
}
