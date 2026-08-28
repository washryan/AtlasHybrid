package dev.atlashybrid.loader;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Pattern;

public final class PluginMetadataParser {
    private static final int MAX_METADATA_BYTES = 256 * 1024;
    private static final Pattern PLUGIN_NAME = Pattern.compile("[A-Za-z0-9_.-]{1,64}");
    private static final Pattern MAIN_CLASS = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)+");

    public PluginMetadata parse(Path jarPath) throws InvalidPluginMetadataException {
        try (JarFile jar = new JarFile(jarPath.toFile(), true)) {
            rejectProtectedNamespaces(jar);
            JarEntry entry = jar.getJarEntry("plugin.yml");
            if (entry == null || entry.isDirectory()) {
                throw new InvalidPluginMetadataException("Missing root plugin.yml in " + jarPath.getFileName());
            }
            if (entry.getSize() > MAX_METADATA_BYTES) {
                throw new InvalidPluginMetadataException("plugin.yml exceeds " + MAX_METADATA_BYTES + " bytes");
            }
            try (InputStream input = jar.getInputStream(entry)) {
                return parse(readBounded(input));
            }
        } catch (InvalidPluginMetadataException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new InvalidPluginMetadataException("Invalid plugin JAR " + jarPath.getFileName(), exception);
        }
    }

    PluginMetadata parse(String yaml) throws InvalidPluginMetadataException {
        Map<String, String> scalars = new LinkedHashMap<>();
        Map<String, List<String>> lists = new LinkedHashMap<>();
        Set<String> commands = new LinkedHashSet<>();
        Map<String, Set<String>> commandAliases = new LinkedHashMap<>();
        String activeList = null;
        boolean inCommands = false;
        String activeCommand = null;
        boolean inCommandAliases = false;

        for (String raw : yaml.replace("\r", "").split("\n", -1)) {
            String withoutComment = removeComment(raw);
            if (withoutComment.isBlank()) continue;
            int indent = leadingSpaces(withoutComment);
            String line = withoutComment.strip();

            if (indent == 0) {
                activeList = null;
                inCommands = false;
                activeCommand = null;
                inCommandAliases = false;
                int colon = line.indexOf(':');
                if (colon < 1) throw new InvalidPluginMetadataException("Malformed plugin.yml line: " + line);
                String key = line.substring(0, colon).strip().toLowerCase(Locale.ROOT);
                String value = line.substring(colon + 1).strip();
                if ("commands".equals(key) && value.isEmpty()) {
                    inCommands = true;
                } else if (isListKey(key) && value.isEmpty()) {
                    activeList = key;
                    lists.putIfAbsent(key, new ArrayList<>());
                } else if (isListKey(key) && value.startsWith("[") && value.endsWith("]")) {
                    lists.put(key, parseInlineList(value));
                } else {
                    scalars.put(key, stripQuotes(value));
                }
            } else if (inCommands && indent == 2 && line.endsWith(":")) {
                String command = line.substring(0, line.length() - 1).strip().toLowerCase(Locale.ROOT);
                if (!PLUGIN_NAME.matcher(command).matches()) throw new InvalidPluginMetadataException("Invalid command name: " + command);
                commands.add(command);
                commandAliases.putIfAbsent(command, new LinkedHashSet<>());
                activeCommand = command;
                inCommandAliases = false;
            } else if (inCommands && indent == 4 && activeCommand != null) {
                int colon = line.indexOf(':');
                if (colon < 1) continue;
                String key = line.substring(0, colon).strip().toLowerCase(Locale.ROOT);
                String value = line.substring(colon + 1).strip();
                inCommandAliases = "aliases".equals(key) && value.isEmpty();
                if ("aliases".equals(key) && value.startsWith("[") && value.endsWith("]")) {
                    for (String alias : parseInlineList(value)) addAlias(commandAliases.get(activeCommand), alias);
                }
            } else if (inCommands && indent >= 6 && activeCommand != null && inCommandAliases && line.startsWith("-")) {
                addAlias(commandAliases.get(activeCommand), stripQuotes(line.substring(1).strip()));
            } else if (activeList != null && line.startsWith("-")) {
                String value = stripQuotes(line.substring(1).strip());
                if (!value.isBlank()) lists.get(activeList).add(value);
            }
        }

        String name = required(scalars, "name");
        String version = required(scalars, "version");
        String main = required(scalars, "main");
        if (!PLUGIN_NAME.matcher(name).matches()) throw new InvalidPluginMetadataException("Invalid plugin name: " + name);
        if (!MAIN_CLASS.matcher(main).matches()) throw new InvalidPluginMetadataException("Invalid main class: " + main);
        return new PluginMetadata(
            name,
            version,
            main,
            emptyToNull(scalars.get("api-version")),
            emptyToNull(scalars.get("description")),
            lists.getOrDefault("authors", List.of()),
            lists.getOrDefault("depend", List.of()),
            lists.getOrDefault("softdepend", List.of()),
            commands,
            commandAliases
        );
    }

    private static void rejectProtectedNamespaces(JarFile jar) throws InvalidPluginMetadataException {
        for (JarEntry entry : java.util.Collections.list(jar.entries())) {
            String name = entry.getName();
            if (!name.endsWith(".class")) continue;
            if (name.startsWith("org/bukkit/") || name.startsWith("net/minecraft/")
                || name.startsWith("net/minecraftforge/")
                || name.startsWith("dev/atlashybrid/loader/")
                || name.startsWith("dev/atlashybrid/runtime/")
                || name.startsWith("dev/atlashybrid/diagnostics/")
                || name.startsWith("dev/atlashybrid/forge/")) {
                throw new InvalidPluginMetadataException("Plugin contains protected class namespace: " + name);
            }
        }
    }

    private static String readBounded(InputStream input) throws IOException, InvalidPluginMetadataException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int total = 0;
        for (int read; (read = input.read(buffer)) >= 0;) {
            total += read;
            if (total > MAX_METADATA_BYTES) throw new InvalidPluginMetadataException("plugin.yml exceeds " + MAX_METADATA_BYTES + " bytes");
            output.write(buffer, 0, read);
        }
        return output.toString(StandardCharsets.UTF_8);
    }

    private static String required(Map<String, String> values, String key) throws InvalidPluginMetadataException {
        String value = emptyToNull(values.get(key));
        if (value == null) throw new InvalidPluginMetadataException("Missing required plugin.yml key: " + key);
        return value;
    }

    private static boolean isListKey(String key) { return "authors".equals(key) || "depend".equals(key) || "softdepend".equals(key); }
    private static void addAlias(Set<String> aliases, String alias) throws InvalidPluginMetadataException { String value = alias.toLowerCase(Locale.ROOT); if (!PLUGIN_NAME.matcher(value).matches()) throw new InvalidPluginMetadataException("Invalid command alias: " + alias); aliases.add(value); }
    private static List<String> parseInlineList(String value) { List<String> result = new ArrayList<>(); String body = value.substring(1, value.length() - 1); for (String part : body.split(",")) { String item = stripQuotes(part.strip()); if (!item.isBlank()) result.add(item); } return result; }
    private static String stripQuotes(String value) { if (value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'")))) return value.substring(1, value.length() - 1); return value; }
    private static String emptyToNull(String value) { return value == null || value.isBlank() ? null : value; }
    private static int leadingSpaces(String value) { int count = 0; while (count < value.length() && value.charAt(count) == ' ') count++; return count; }
    private static String removeComment(String line) { boolean single = false, dbl = false; for (int i = 0; i < line.length(); i++) { char c = line.charAt(i); if (c == '\'' && !dbl) single = !single; else if (c == '"' && !single) dbl = !dbl; else if (c == '#' && !single && !dbl && (i == 0 || Character.isWhitespace(line.charAt(i - 1)))) return line.substring(0, i); } return line; }
}
