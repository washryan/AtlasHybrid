package org.bukkit.plugin;

import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class PluginDescriptionFile {
    private final String name;
    private final String version;
    private final String main;
    private final String apiVersion;
    private final String description;
    private final List<String> authors;
    private final List<String> depend;
    private final List<String> softDepend;
    private final Set<String> commands;

    public PluginDescriptionFile(
        String name,
        String version,
        String main,
        String apiVersion,
        String description,
        List<String> authors,
        List<String> depend,
        List<String> softDepend,
        Set<String> commands
    ) {
        this.name = Objects.requireNonNull(name, "name");
        this.version = Objects.requireNonNull(version, "version");
        this.main = Objects.requireNonNull(main, "main");
        this.apiVersion = apiVersion;
        this.description = description;
        this.authors = List.copyOf(authors);
        this.depend = List.copyOf(depend);
        this.softDepend = List.copyOf(softDepend);
        this.commands = Set.copyOf(commands);
    }

    public String getName() { return name; }
    public String getVersion() { return version; }
    public String getMain() { return main; }
    public String getAPIVersion() { return apiVersion; }
    public String getDescription() { return description; }
    public List<String> getAuthors() { return authors; }
    public List<String> getDepend() { return depend; }
    public List<String> getSoftDepend() { return softDepend; }
    public Set<String> getCommands() { return commands; }
    public String getFullName() { return name + " v" + version; }
}
