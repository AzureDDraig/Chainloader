package net.chainloader.loader.core.boot;

import net.chainloader.loader.core.ChainModMetadata;

import java.util.*;
import java.util.logging.Logger;

/**
 * UnifiedDependencyResolver resolves the mod dependency tree for the ChainLoader environment.
 * It parses and matches SemVer (fabric.mod.json) and Maven-style version range (mods.toml) constraints,
 * performs topological sorting to determine loading order, detects circular dependencies,
 * and exposes hooks for the Knot and ModLauncher adapters to coordinate their loading pipelines.
 */
public final class UnifiedDependencyResolver {
    private static final Logger LOGGER = Logger.getLogger(UnifiedDependencyResolver.class.getName());

    private final Map<String, String> environmentMods = new HashMap<>();
    private final List<KnotAdapterHook> knotHooks = new ArrayList<>();
    private final List<ModLauncherAdapterHook> modLauncherHooks = new ArrayList<>();

    public UnifiedDependencyResolver() {
        // Set default environment / virtual mod versions
        environmentMods.put("minecraft", "1.19.2");
        environmentMods.put("java", System.getProperty("java.version", "17"));
        environmentMods.put("fabricloader", "0.14.21");
        environmentMods.put("forge", "41.0.0");
        environmentMods.put("neoforge", "20.1.0");
        environmentMods.put("chainloader", "1.0.0");
    }

    /**
     * Registers a custom system/environment version override (e.g. for game version or java version).
     */
    public UnifiedDependencyResolver withEnvironmentMod(String modId, String version) {
        environmentMods.put(modId, version);
        return this;
    }

    /**
     * Registers a hook for the Knot (Fabric) adapter.
     */
    public void registerKnotHook(KnotAdapterHook hook) {
        this.knotHooks.add(Objects.requireNonNull(hook));
    }

    /**
     * Registers a hook for the ModLauncher (Forge/NeoForge) adapter.
     */
    public void registerModLauncherHook(ModLauncherAdapterHook hook) {
        this.modLauncherHooks.add(Objects.requireNonNull(hook));
    }

    /**
     * Resolves the dependencies and produces a sorted load order.
     *
     * @param mods a collection of candidate mod metadata objects
     * @return the result of the dependency resolution process
     */
    public ResolutionResult resolve(Collection<ChainModMetadata> mods) {
        Map<String, ChainModMetadata> candidates = new HashMap<>();
        for (ChainModMetadata mod : mods) {
            candidates.put(mod.getId(), mod);
        }

        List<MissingDependencyException> missingDependencies = new ArrayList<>();
        List<VersionMismatchException> versionMismatches = new ArrayList<>();
        List<String> circularDependencies = new ArrayList<>();

        // 1. Validate version constraints and identify missing dependencies
        for (ChainModMetadata mod : mods) {
            for (ChainModMetadata.ModDependency dep : mod.getDependencies()) {
                String depId = dep.getModId();
                String reqRange = dep.getVersionRequirement();

                String actualVersion = null;
                if (environmentMods.containsKey(depId)) {
                    actualVersion = environmentMods.get(depId);
                } else if (candidates.containsKey(depId)) {
                    actualVersion = candidates.get(depId).getVersion();
                }

                if (actualVersion == null) {
                    if (!dep.isOptional()) {
                        missingDependencies.add(new MissingDependencyException(mod.getId(), depId, reqRange));
                    }
                } else {
                    VersionRequirement req = new VersionRequirement(reqRange);
                    if (!req.matches(actualVersion)) {
                        versionMismatches.add(new VersionMismatchException(mod.getId(), depId, reqRange, actualVersion));
                    }
                }
            }
        }

        // 2. Perform Topological Sort using Depth First Search (DFS) with cycle detection
        List<ChainModMetadata> resolvedMods = new ArrayList<>();
        Map<String, Integer> states = new HashMap<>(); // 0 = UNVISITED, 1 = VISITING, 2 = VISITED

        for (String modId : candidates.keySet()) {
            if (states.getOrDefault(modId, 0) == 0) {
                visit(modId, candidates, states, resolvedMods, circularDependencies, new ArrayList<>());
            }
        }

        ResolutionResult result = new ResolutionResult(
                resolvedMods,
                missingDependencies,
                versionMismatches,
                circularDependencies
        );

        // 3. Notify registered adapter hooks
        for (KnotAdapterHook hook : knotHooks) {
            try {
                hook.onResolve(result);
            } catch (Exception e) {
                LOGGER.severe("Exception in Knot adapter hook: " + e.getMessage());
            }
        }

        for (ModLauncherAdapterHook hook : modLauncherHooks) {
            try {
                hook.onResolve(result);
            } catch (Exception e) {
                LOGGER.severe("Exception in ModLauncher adapter hook: " + e.getMessage());
            }
        }

        return result;
    }

    private void visit(String modId, Map<String, ChainModMetadata> candidates, Map<String, Integer> states,
                       List<ChainModMetadata> resolved, List<String> cycles, List<String> currentPath) {
        states.put(modId, 1); // VISITING
        currentPath.add(modId);

        ChainModMetadata mod = candidates.get(modId);
        if (mod != null) {
            for (ChainModMetadata.ModDependency dep : mod.getDependencies()) {
                String depId = dep.getModId();
                if (candidates.containsKey(depId)) {
                    int depState = states.getOrDefault(depId, 0);
                    if (depState == 1) { // Cycle detected!
                        int startIndex = currentPath.indexOf(depId);
                        if (startIndex != -1) {
                            StringBuilder sb = new StringBuilder();
                            for (int i = startIndex; i < currentPath.size(); i++) {
                                sb.append(currentPath.get(i)).append(" -> ");
                            }
                            sb.append(depId);
                            cycles.add(sb.toString());
                        }
                    } else if (depState == 0) {
                        visit(depId, candidates, states, resolved, cycles, currentPath);
                    }
                }
            }
        }

        currentPath.remove(currentPath.size() - 1);
        states.put(modId, 2); // VISITED
        if (mod != null) {
            resolved.add(mod);
        }
    }

    /**
     * Interface for Knot (Fabric loader) adapter to receive dependency resolution output.
     */
    public interface KnotAdapterHook {
        void onResolve(ResolutionResult result);
    }

    /**
     * Interface for ModLauncher (Forge/NeoForge loader) adapter to receive dependency resolution output.
     */
    public interface ModLauncherAdapterHook {
        void onResolve(ResolutionResult result);
    }

    /**
     * Exception thrown or logged when a mandatory dependency is missing.
     */
    public static class MissingDependencyException extends Exception {
        private final String modId;
        private final String dependencyId;
        private final String requiredVersion;

        public MissingDependencyException(String modId, String dependencyId, String requiredVersion) {
            super("Mod '" + modId + "' is missing required dependency '" + dependencyId + "' (" + requiredVersion + ")");
            this.modId = modId;
            this.dependencyId = dependencyId;
            this.requiredVersion = requiredVersion;
        }

        public String getModId() { return modId; }
        public String getDependencyId() { return dependencyId; }
        public String getRequiredVersion() { return requiredVersion; }
    }

    /**
     * Exception thrown or logged when a dependency version requirement is not met.
     */
    public static class VersionMismatchException extends Exception {
        private final String modId;
        private final String dependencyId;
        private final String requiredVersion;
        private final String actualVersion;

        public VersionMismatchException(String modId, String dependencyId, String requiredVersion, String actualVersion) {
            super("Mod '" + modId + "' requires version '" + requiredVersion + "' of dependency '" + dependencyId + "', but version '" + actualVersion + "' is installed.");
            this.modId = modId;
            this.dependencyId = dependencyId;
            this.requiredVersion = requiredVersion;
            this.actualVersion = actualVersion;
        }

        public String getModId() { return modId; }
        public String getDependencyId() { return dependencyId; }
        public String getRequiredVersion() { return requiredVersion; }
        public String getActualVersion() { return actualVersion; }
    }

    /**
     * Contains the output of a dependency resolution pass.
     */
    public static class ResolutionResult {
        private final List<ChainModMetadata> resolvedMods;
        private final List<MissingDependencyException> missingDependencies;
        private final List<VersionMismatchException> versionMismatches;
        private final List<String> circularDependencies;

        public ResolutionResult(List<ChainModMetadata> resolvedMods,
                                List<MissingDependencyException> missingDependencies,
                                List<VersionMismatchException> versionMismatches,
                                List<String> circularDependencies) {
            this.resolvedMods = Collections.unmodifiableList(resolvedMods);
            this.missingDependencies = Collections.unmodifiableList(missingDependencies);
            this.versionMismatches = Collections.unmodifiableList(versionMismatches);
            this.circularDependencies = Collections.unmodifiableList(circularDependencies);
        }

        public List<ChainModMetadata> getResolvedMods() { return resolvedMods; }
        public List<MissingDependencyException> getMissingDependencies() { return missingDependencies; }
        public List<VersionMismatchException> getVersionMismatches() { return versionMismatches; }
        public List<String> getCircularDependencies() { return circularDependencies; }

        public boolean hasErrors() {
            return !missingDependencies.isEmpty() || !versionMismatches.isEmpty() || !circularDependencies.isEmpty();
        }

        @Override
        public String toString() {
            return "ResolutionResult{" +
                    "resolvedModsCount=" + resolvedMods.size() +
                    ", missingDependenciesCount=" + missingDependencies.size() +
                    ", versionMismatchesCount=" + versionMismatches.size() +
                    ", circularDependenciesCount=" + circularDependencies.size() +
                    '}';
        }
    }

    /**
     * Internal representation of a specific version component, compliant with Semantic Versioning.
     */
    public static class Version implements Comparable<Version> {
        public final int major;
        public final int minor;
        public final int patch;
        public final String prerelease;

        public Version(int major, int minor, int patch, String prerelease) {
            this.major = major;
            this.minor = minor;
            this.patch = patch;
            this.prerelease = prerelease != null ? prerelease.trim() : "";
        }

        public static Version parse(String versionStr) {
            if (versionStr == null || versionStr.trim().isEmpty()) {
                return new Version(0, 0, 0, "");
            }
            String clean = versionStr.trim();
            if (clean.startsWith("v") || clean.startsWith("V")) {
                clean = clean.substring(1);
            }

            String mainPart = clean;
            String prereleasePart = "";
            int dashIndex = clean.indexOf('-');
            if (dashIndex != -1) {
                mainPart = clean.substring(0, dashIndex);
                prereleasePart = clean.substring(dashIndex + 1);
            }

            String[] parts = mainPart.split("\\.");
            int major = 0;
            int minor = 0;
            int patch = 0;

            try {
                if (parts.length > 0) major = Integer.parseInt(parts[0]);
                if (parts.length > 1) minor = Integer.parseInt(parts[1]);
                if (parts.length > 2) patch = Integer.parseInt(parts[2]);
            } catch (NumberFormatException ignored) {}

            return new Version(major, minor, patch, prereleasePart);
        }

        @Override
        public int compareTo(Version o) {
            if (this.major != o.major) return Integer.compare(this.major, o.major);
            if (this.minor != o.minor) return Integer.compare(this.minor, o.minor);
            if (this.patch != o.patch) return Integer.compare(this.patch, o.patch);

            if (this.prerelease.isEmpty() && !o.prerelease.isEmpty()) return 1;
            if (!this.prerelease.isEmpty() && o.prerelease.isEmpty()) return -1;
            return this.prerelease.compareTo(o.prerelease);
        }

        @Override
        public String toString() {
            return major + "." + minor + "." + patch + (prerelease.isEmpty() ? "" : "-" + prerelease);
        }
    }

    private interface VersionConstraint {
        boolean matches(Version version);
    }

    /**
     * Parses and evaluates a string requirement which can contain SemVer range operators
     * (~, ^, >=, <=, etc.) or Maven-style boundary constraints ([min,max), [version], etc.).
     */
    public static class VersionRequirement {
        private final List<VersionConstraint> constraints = new ArrayList<>();
        private final String originalText;

        public VersionRequirement(String text) {
            this.originalText = text != null ? text.trim() : "";
            parse(originalText);
        }

        private void parse(String text) {
            if (text.isEmpty() || text.equals("*")) {
                constraints.add(version -> true);
                return;
            }

            // Handle Maven-style ranges
            if (text.startsWith("[") || text.startsWith("(")) {
                parseMavenRange(text);
                return;
            }

            // Handle space/comma-separated SemVer tokens
            String[] tokens = text.split("[\\s,]+");
            for (String token : tokens) {
                parseSemVerToken(token);
            }
        }

        private void parseMavenRange(String range) {
            boolean startInclusive = range.startsWith("[");
            boolean endInclusive = range.endsWith("]");

            String inner = range.substring(1, range.length() - 1).trim();
            int commaIndex = inner.indexOf(',');

            if (commaIndex == -1) {
                // Exact version match: e.g. "[1.19]"
                Version v = Version.parse(inner);
                constraints.add(version -> version.compareTo(v) == 0);
                return;
            }

            String leftStr = inner.substring(0, commaIndex).trim();
            String rightStr = inner.substring(commaIndex + 1).trim();

            if (!leftStr.isEmpty()) {
                Version left = Version.parse(leftStr);
                if (startInclusive) {
                    constraints.add(version -> version.compareTo(left) >= 0);
                } else {
                    constraints.add(version -> version.compareTo(left) > 0);
                }
            }

            if (!rightStr.isEmpty()) {
                Version right = Version.parse(rightStr);
                if (endInclusive) {
                    constraints.add(version -> version.compareTo(right) <= 0);
                } else {
                    constraints.add(version -> version.compareTo(right) < 0);
                }
            }
        }

        private void parseSemVerToken(String token) {
            if (token.isEmpty()) return;

            if (token.startsWith(">=")) {
                Version v = Version.parse(token.substring(2));
                constraints.add(version -> version.compareTo(v) >= 0);
            } else if (token.startsWith("<=")) {
                Version v = Version.parse(token.substring(2));
                constraints.add(version -> version.compareTo(v) <= 0);
            } else if (token.startsWith(">")) {
                Version v = Version.parse(token.substring(1));
                constraints.add(version -> version.compareTo(v) > 0);
            } else if (token.startsWith("<")) {
                Version v = Version.parse(token.substring(1));
                constraints.add(version -> version.compareTo(v) < 0);
            } else if (token.startsWith("=")) {
                Version v = Version.parse(token.substring(1));
                constraints.add(version -> version.compareTo(v) == 0);
            } else if (token.startsWith("~")) {
                Version v = Version.parse(token.substring(1));
                Version limit = new Version(v.major, v.minor + 1, 0, "");
                constraints.add(version -> version.compareTo(v) >= 0 && version.compareTo(limit) < 0);
            } else if (token.startsWith("^")) {
                Version v = Version.parse(token.substring(1));
                Version limit = new Version(v.major + 1, 0, 0, "");
                constraints.add(version -> version.compareTo(v) >= 0 && version.compareTo(limit) < 0);
            } else {
                // Prefix check for plain versions (e.g. "1.19" matches "1.19.2")
                Version v = Version.parse(token);
                constraints.add(version -> {
                    String[] parts = token.split("\\.");
                    if (parts.length == 1) {
                        return version.major == v.major;
                    } else if (parts.length == 2) {
                        return version.major == v.major && version.minor == v.minor;
                    } else {
                        return version.compareTo(v) == 0;
                    }
                });
            }
        }

        public boolean matches(Version version) {
            for (VersionConstraint constraint : constraints) {
                if (!constraint.matches(version)) {
                    return false;
                }
            }
            return true;
        }

        public boolean matches(String versionStr) {
            return matches(Version.parse(versionStr));
        }

        @Override
        public String toString() {
            return originalText;
        }
    }
}
