package net.chainloader.api.environment;

import java.util.function.Supplier;

/**
 * Platform-agnostic environment information and side-safe execution utilities.
 * <p>
 * Using this class helps prevent {@link NoClassDefFoundError} and {@link ClassNotFoundException}
 * when running code on dedicated servers that refers to client-only classes (e.g., rendering, models, hud).
 * </p>
 *
 * <h3>Under-the-Hood Mappings:</h3>
 * <ul>
 *   <li><b>Fabric environment check:</b> Delegates to {@code FabricLoader.getInstance().getEnvironmentType()}.</li>
 *   <li><b>NeoForge environment check:</b> Delegates to {@code FMLEnvironment.dist} or {@code FMLLoader.getDist()}.</li>
 * </ul>
 *
 * <h3>Best Practice for Side-Safe Execution:</h3>
 * <p>
 * Always pass a {@code Supplier<Runnable>} instead of a direct {@code Runnable} or lambda.
 * A lambda closure like {@code () -> ClientCode.init()} will cause the JVM to attempt to load {@code ClientCode}
 * during class verification, even if the branch is never executed. Using a nested supplier:
 * {@code () -> () -> ClientCode.init()} delays classloading until the outer supplier is actually evaluated on the correct side.
 * </p>
 */
public final class ChainLoaderEnv {

    private static IEnvBackend backend = new MockEnvBackend();

    private ChainLoaderEnv() {}

    /**
     * Registers the platform-specific environment backend provider.
     * Called internally by ChainLoader's platform bootloaders.
     *
     * @param envBackend The platform-specific environment backend.
     */
    public static void registerBackend(IEnvBackend envBackend) {
        if (envBackend != null) {
            backend = envBackend;
        }
    }

    /**
     * Retrieves the current environment type.
     *
     * @return The active {@link EnvType}.
     */
    public static EnvType getEnvType() {
        return backend.getEnvType();
    }

    /**
     * Checks if the mod is currently running in a client environment.
     *
     * @return True if running on client (singleplayer/multiplayer), false otherwise.
     */
    public static boolean isClient() {
        return getEnvType() == EnvType.CLIENT;
    }

    /**
     * Checks if the mod is currently running in a dedicated server environment.
     *
     * @return True if running on a dedicated server, false otherwise.
     */
    public static boolean isServer() {
        return getEnvType() == EnvType.SERVER;
    }

    /**
     * Safely executes a block of client-only code if the environment is a Client.
     * <p>
     * <b>Example:</b>
     * <pre>{@code
     * ChainLoaderEnv.runIfClient(() -> () -> MyClientModInitializer.initClient());
     * }</pre>
     * </p>
     *
     * @param clientRunnableSupplier A supplier containing the client-only runnable to execute.
     */
    public static void runIfClient(Supplier<Runnable> clientRunnableSupplier) {
        if (isClient()) {
            clientRunnableSupplier.get().run();
        }
    }

    /**
     * Safely executes a block of server-only code if the environment is a Dedicated Server.
     *
     * @param serverRunnableSupplier A supplier containing the server-only runnable to execute.
     */
    public static void runIfServer(Supplier<Runnable> serverRunnableSupplier) {
        if (isServer()) {
            serverRunnableSupplier.get().run();
        }
    }

    /**
     * Safely retrieves a side-specific value (e.g. fetching client-side options).
     *
     * @param clientValueSupplier Outer supplier returning the inner supplier of the client value.
     * @param <T>                  The type of value retrieved.
     * @return The retrieved value on the client, or null if on server.
     */
    public static <T> T getIfClient(Supplier<Supplier<T>> clientValueSupplier) {
        if (isClient()) {
            return clientValueSupplier.get().get();
        }
        return null;
    }

    /**
     * Interface representing the platform-specific environment information provider.
     */
    public interface IEnvBackend {
        EnvType getEnvType();
    }

    /**
     * Default mock implementation used when no platform bootloader is active (e.g., compile-time/unit tests).
     */
    private static class MockEnvBackend implements IEnvBackend {
        @Override
        public EnvType getEnvType() {
            // Default to client environment in mockup / test context
            return EnvType.CLIENT;
        }
    }
}
