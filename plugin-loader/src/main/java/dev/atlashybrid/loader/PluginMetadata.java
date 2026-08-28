package dev.atlashybrid.loader;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.plugin.PluginDescriptionFile;

public record PluginMetadata(
    String name,
    String version,
    String main,
    String apiVersion,
    String description,
    List<String> authors,
    List<String> depend,
    List<String> softDepend,
    Set<String> commands,
    Map<String, Set<String>> commandAliases
) {
    public PluginMetadata {
        authors = List.copyOf(authors);
        depend = List.copyOf(depend);
        softDepend = List.copyOf(softDepend);
        commands = Set.copyOf(commands);
        commandAliases = commandAliases.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
            Map.Entry::getKey, entry -> Set.copyOf(entry.getValue())));
    }

    public PluginDescriptionFile toDescription() {
        return new PluginDescriptionFile(name, version, main, apiVersion, description, authors, depend, softDepend, commands);
    }
}
