package dev.atlashybrid.loader;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;

public final class AtlasPluginClassLoader extends URLClassLoader {
    static { registerAsParallelCapable(); }

    private static final List<String> PARENT_FIRST = List.of(
        "java.", "javax.", "jdk.", "sun.", "org.bukkit.",
        "dev.atlashybrid.loader.", "dev.atlashybrid.runtime.",
        "dev.atlashybrid.diagnostics.", "dev.atlashybrid.forge.",
        "net.minecraft.", "net.minecraftforge."
    );
    private final List<AtlasPluginClassLoader> dependencies;

    public AtlasPluginClassLoader(URL jar, ClassLoader parent, List<AtlasPluginClassLoader> dependencies) {
        super(new URL[] { jar }, parent);
        this.dependencies = List.copyOf(dependencies);
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            Class<?> loaded = findLoadedClass(name);
            if (loaded == null && isParentFirst(name)) {
                loaded = getParent().loadClass(name);
            }
            if (loaded == null) {
                try { loaded = findClass(name); } catch (ClassNotFoundException ignored) { }
            }
            if (loaded == null) {
                for (AtlasPluginClassLoader dependency : dependencies) {
                    try {
                        loaded = dependency.loadExportedClass(name);
                        break;
                    } catch (ClassNotFoundException ignored) { }
                }
            }
            if (loaded == null) loaded = getParent().loadClass(name);
            if (resolve) resolveClass(loaded);
            return loaded;
        }
    }

    private Class<?> loadExportedClass(String name) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            Class<?> loaded = findLoadedClass(name);
            if (loaded == null) loaded = findClass(name);
            return loaded;
        }
    }

    private static boolean isParentFirst(String name) {
        return PARENT_FIRST.stream().anyMatch(name::startsWith);
    }
}
