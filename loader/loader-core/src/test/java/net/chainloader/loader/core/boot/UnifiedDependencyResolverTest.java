package net.chainloader.loader.core.boot;

import net.chainloader.loader.core.ChainModMetadata;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class UnifiedDependencyResolverTest {

    @Test
    public void testVersionParsingAndComparison() {
        UnifiedDependencyResolver.Version v1 = UnifiedDependencyResolver.Version.parse("1.19.2");
        UnifiedDependencyResolver.Version v2 = UnifiedDependencyResolver.Version.parse("1.20.0");
        UnifiedDependencyResolver.Version v3 = UnifiedDependencyResolver.Version.parse("1.19.2-beta.1");

        assertEquals(1, v1.major);
        assertEquals(19, v1.minor);
        assertEquals(2, v1.patch);
        assertEquals("", v1.prerelease);

        assertTrue(v1.compareTo(v2) < 0);
        assertTrue(v2.compareTo(v1) > 0);
        assertTrue(v1.compareTo(v1) == 0);

        // Pre-release versions should be lower than release versions
        assertTrue(v3.compareTo(v1) < 0);
    }

    @Test
    public void testSemVerRanges() {
        UnifiedDependencyResolver.VersionRequirement req1 = new UnifiedDependencyResolver.VersionRequirement(">=1.14.0");
        assertTrue(req1.matches("1.14.0"));
        assertTrue(req1.matches("1.15.2"));
        assertFalse(req1.matches("1.13.9"));

        UnifiedDependencyResolver.VersionRequirement req2 = new UnifiedDependencyResolver.VersionRequirement("~1.19.0");
        assertTrue(req2.matches("1.19.0"));
        assertTrue(req2.matches("1.19.2"));
        assertFalse(req2.matches("1.20.0"));

        UnifiedDependencyResolver.VersionRequirement req3 = new UnifiedDependencyResolver.VersionRequirement("^1.19.2");
        assertTrue(req3.matches("1.19.2"));
        assertTrue(req3.matches("1.20.1"));
        assertFalse(req3.matches("2.0.0"));

        // Prefix match
        UnifiedDependencyResolver.VersionRequirement req4 = new UnifiedDependencyResolver.VersionRequirement("1.19");
        assertTrue(req4.matches("1.19.0"));
        assertTrue(req4.matches("1.19.2"));
        assertFalse(req4.matches("1.20.0"));
    }

    @Test
    public void testMavenRanges() {
        UnifiedDependencyResolver.VersionRequirement req1 = new UnifiedDependencyResolver.VersionRequirement("[1.19,1.20)");
        assertTrue(req1.matches("1.19"));
        assertTrue(req1.matches("1.19.2"));
        assertFalse(req1.matches("1.20"));
        assertFalse(req1.matches("1.18"));

        UnifiedDependencyResolver.VersionRequirement req2 = new UnifiedDependencyResolver.VersionRequirement("[1.19]");
        assertTrue(req2.matches("1.19.0"));
        assertFalse(req2.matches("1.19.1"));

        UnifiedDependencyResolver.VersionRequirement req3 = new UnifiedDependencyResolver.VersionRequirement("[1.19,)");
        assertTrue(req3.matches("1.19.0"));
        assertTrue(req3.matches("2.0.0"));
        assertFalse(req3.matches("1.18.9"));
    }

    @Test
    public void testSuccessfulTopologicalResolution() {
        ChainModMetadata modC = new ChainModMetadata.Builder()
                .id("mod-c")
                .version("1.0.0")
                .build();

        ChainModMetadata modB = new ChainModMetadata.Builder()
                .id("mod-b")
                .version("1.1.0")
                .addDependency("mod-c", ">=1.0.0", false)
                .build();

        ChainModMetadata modA = new ChainModMetadata.Builder()
                .id("mod-a")
                .version("2.0.0")
                .addDependency("mod-b", "[1.0.0,2.0.0)", false)
                .build();

        UnifiedDependencyResolver resolver = new UnifiedDependencyResolver();
        UnifiedDependencyResolver.ResolutionResult result = resolver.resolve(Arrays.asList(modA, modB, modC));

        assertFalse(result.hasErrors());
        assertEquals(3, result.getResolvedMods().size());

        // Correct topological order: C, then B, then A
        assertEquals("mod-c", result.getResolvedMods().get(0).getId());
        assertEquals("mod-b", result.getResolvedMods().get(1).getId());
        assertEquals("mod-a", result.getResolvedMods().get(2).getId());
    }

    @Test
    public void testResolutionFailureMissingDependency() {
        ChainModMetadata modA = new ChainModMetadata.Builder()
                .id("mod-a")
                .version("1.0.0")
                .addDependency("non-existent", "*", false)
                .build();

        UnifiedDependencyResolver resolver = new UnifiedDependencyResolver();
        UnifiedDependencyResolver.ResolutionResult result = resolver.resolve(List.of(modA));

        assertTrue(result.hasErrors());
        assertEquals(1, result.getMissingDependencies().size());
        assertEquals("non-existent", result.getMissingDependencies().get(0).getDependencyId());
    }

    @Test
    public void testResolutionFailureVersionMismatch() {
        ChainModMetadata modB = new ChainModMetadata.Builder()
                .id("mod-b")
                .version("1.0.0")
                .build();

        ChainModMetadata modA = new ChainModMetadata.Builder()
                .id("mod-a")
                .version("1.0.0")
                .addDependency("mod-b", ">=2.0.0", false)
                .build();

        UnifiedDependencyResolver resolver = new UnifiedDependencyResolver();
        UnifiedDependencyResolver.ResolutionResult result = resolver.resolve(Arrays.asList(modA, modB));

        assertTrue(result.hasErrors());
        assertEquals(1, result.getVersionMismatches().size());
        assertEquals("mod-b", result.getVersionMismatches().get(0).getDependencyId());
        assertEquals(">=2.0.0", result.getVersionMismatches().get(0).getRequiredVersion());
        assertEquals("1.0.0", result.getVersionMismatches().get(0).getActualVersion());
    }

    @Test
    public void testResolutionFailureCircularDependency() {
        ChainModMetadata modA = new ChainModMetadata.Builder()
                .id("mod-a")
                .version("1.0.0")
                .addDependency("mod-b", "*", false)
                .build();

        ChainModMetadata modB = new ChainModMetadata.Builder()
                .id("mod-b")
                .version("1.0.0")
                .addDependency("mod-a", "*", false)
                .build();

        UnifiedDependencyResolver resolver = new UnifiedDependencyResolver();
        UnifiedDependencyResolver.ResolutionResult result = resolver.resolve(Arrays.asList(modA, modB));

        assertTrue(result.hasErrors());
        assertFalse(result.getCircularDependencies().isEmpty());
        // Verify cycle is reported
        assertTrue(result.getCircularDependencies().get(0).contains("mod-a"));
        assertTrue(result.getCircularDependencies().get(0).contains("mod-b"));
    }

    @Test
    public void testAdapterHooksTriggered() {
        ChainModMetadata mod = new ChainModMetadata.Builder()
                .id("test-mod")
                .version("1.0.0")
                .build();

        UnifiedDependencyResolver resolver = new UnifiedDependencyResolver();

        final boolean[] knotCalled = {false};
        final boolean[] modLauncherCalled = {false};

        resolver.registerKnotHook(result -> knotCalled[0] = true);
        resolver.registerModLauncherHook(result -> modLauncherCalled[0] = true);

        resolver.resolve(List.of(mod));

        assertTrue(knotCalled[0]);
        assertTrue(modLauncherCalled[0]);
    }
}
