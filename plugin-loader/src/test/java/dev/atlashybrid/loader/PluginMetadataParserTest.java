package dev.atlashybrid.loader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PluginMetadataParserTest {
    private final PluginMetadataParser parser = new PluginMetadataParser();

    @Test
    void parsesRequiredOptionalListsAndCommands() throws Exception {
        PluginMetadata metadata = parser.parse("""
            name: AtlasTest
            version: '1.0'
            main: example.atlas.Main
            api-version: 1.19
            description: Example plugin
            authors: [One, Two]
            depend:
              - RequiredPlugin
            softdepend: []
            commands:
              atlas:
                description: status
                aliases:
                  - ah
            """);
        assertEquals("AtlasTest", metadata.name());
        assertEquals(List.of("One", "Two"), metadata.authors());
        assertEquals(List.of("RequiredPlugin"), metadata.depend());
        assertEquals(Set.of("atlas"), metadata.commands());
        assertEquals(Set.of("ah"), metadata.commandAliases().get("atlas"));
    }

    @Test
    void rejectsMissingMainClass() {
        assertThrows(InvalidPluginMetadataException.class, () -> parser.parse("name: Broken\nversion: 1\n"));
    }
}
