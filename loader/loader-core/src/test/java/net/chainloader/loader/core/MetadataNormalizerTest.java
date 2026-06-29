package net.chainloader.loader.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class MetadataNormalizerTest {

    @Test
    public void testParseFabric() {
        String fabricJson = "{\n" +
                "  \"schemaVersion\": 1,\n" +
                "  \"id\": \"example-mod\",\n" +
                "  \"version\": \"1.2.3\",\n" +
                "  \"name\": \"Example Mod Name\",\n" +
                "  \"description\": \"A simple fabric mod description\",\n" +
                "  \"license\": \"MIT\",\n" +
                "  \"authors\": [\n" +
                "    \"Author One\",\n" +
                "    \"Author Two\"\n" +
                "  ],\n" +
                "  \"contact\": {\n" +
                "    \"homepage\": \"https://example.com\",\n" +
                "    \"sources\": \"https://example.com/sources\"\n" +
                "  },\n" +
                "  \"depends\": {\n" +
                "    \"fabricloader\": \">=0.14.0\",\n" +
                "    \"minecraft\": \"~1.19\"\n" +
                "  },\n" +
                "  \"entrypoints\": {\n" +
                "    \"main\": [\n" +
                "      \"net.example.ExampleMod\"\n" +
                "    ]\n" +
                "  },\n" +
                "  \"mixins\": [\n" +
                "    \"example.mixins.json\"\n" +
                "  ]\n" +
                "}";

        ChainModMetadata metadata = MetadataNormalizer.parseFabric(fabricJson);

        assertEquals("example-mod", metadata.getId());
        assertEquals("1.2.3", metadata.getVersion());
        assertEquals("Example Mod Name", metadata.getName());
        assertEquals("A simple fabric mod description", metadata.getDescription());
        assertEquals("MIT", metadata.getLicense());
        assertEquals("fabric", metadata.getOriginalLoaderType());

        assertTrue(metadata.getAuthors().contains("Author One"));
        assertTrue(metadata.getAuthors().contains("Author Two"));

        assertEquals("https://example.com", metadata.getContactLinks().get("homepage"));
        assertEquals("https://example.com/sources", metadata.getContactLinks().get("sources"));

        assertEquals(2, metadata.getDependencies().size());
        ChainModMetadata.ModDependency firstDep = metadata.getDependencies().stream()
                .filter(d -> d.getModId().equals("minecraft"))
                .findFirst().orElseThrow();
        assertEquals("~1.19", firstDep.getVersionRequirement());
        assertFalse(firstDep.isOptional());

        assertTrue(metadata.getEntrypoints().containsKey("main"));
        assertTrue(metadata.getEntrypoints().get("main").contains("net.example.ExampleMod"));
        assertTrue(metadata.getMixins().contains("example.mixins.json"));
    }

    @Test
    public void testParseForge() {
        String forgeToml = "modLoader=\"javafml\"\n" +
                "loaderVersion=\"[41,)\"\n" +
                "license=\"MIT\"\n" +
                "\n" +
                "[[mods]]\n" +
                "modId=\"examplemod\"\n" +
                "version=\"1.0.0\"\n" +
                "displayName=\"Example Mod\"\n" +
                "authors=\"Author A, Author B\"\n" +
                "description=\"\"\"\n" +
                "This is a multiline description.\n" +
                "It can span multiple lines.\n" +
                "\"\"\"\n" +
                "displayURL=\"https://example.com\"\n" +
                "\n" +
                "[[dependencies.examplemod]]\n" +
                "    modId=\"minecraft\"\n" +
                "    mandatory=true\n" +
                "    versionRange=\"[1.19,1.20)\"\n";

        ChainModMetadata metadata = MetadataNormalizer.parseForge(forgeToml);

        assertEquals("examplemod", metadata.getId());
        assertEquals("1.0.0", metadata.getVersion());
        assertEquals("Example Mod", metadata.getName());
        assertTrue(metadata.getDescription().contains("This is a multiline description."));
        assertEquals("MIT", metadata.getLicense());
        assertEquals("forge", metadata.getOriginalLoaderType());

        assertTrue(metadata.getAuthors().contains("Author A"));
        assertTrue(metadata.getAuthors().contains("Author B"));

        assertEquals("https://example.com", metadata.getContactLinks().get("homepage"));

        assertEquals(1, metadata.getDependencies().size());
        ChainModMetadata.ModDependency dep = metadata.getDependencies().get(0);
        assertEquals("minecraft", dep.getModId());
        assertEquals("[1.19,1.20)", dep.getVersionRequirement());
        assertFalse(dep.isOptional());
    }
}
