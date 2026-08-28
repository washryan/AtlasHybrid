package dev.atlashybrid.loader;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;
import java.util.Objects;
import org.bukkit.plugin.java.JavaPluginBootstrap;

public final class AtlasPluginClassLoader extends URLClassLoader implements JavaPluginBootstrap.Provider {
    static { registerAsParallelCapable(); }

    private static final List<String> PARENT_FIRST = List.of(
        "java.", "javax.", "jdk.", "sun.", "org.bukkit.",
        "dev.atlashybrid.loader.", "dev.atlashybrid.runtime.",
        "dev.atlashybrid.diagnostics.", "dev.atlashybrid.forge.",
        "net.minecraft.", "net.minecraftforge."
    );
    private final List<AtlasPluginClassLoader> dependencies;
    private final JavaPluginBootstrap.Context bootstrapContext;
    private final ThreadLocal<Integer> constructionDepth = new ThreadLocal<>();

    public AtlasPluginClassLoader(
        URL jar,
        ClassLoader parent,
        List<AtlasPluginClassLoader> dependencies,
        JavaPluginBootstrap.Context bootstrapContext
    ) {
        super(new URL[] { jar }, parent);
        this.dependencies = List.copyOf(dependencies);
        this.bootstrapContext = Objects.requireNonNull(bootstrapContext, "bootstrapContext");
    }

    ConstructionScope beginConstruction() {
        Integer current = constructionDepth.get();
        constructionDepth.set(current == null ? 1 : current + 1);
        return () -> {
            Integer depth = constructionDepth.get();
            if (depth == null || depth <= 1) constructionDepth.remove();
            else constructionDepth.set(depth - 1);
        };
    }

    @Override
    public JavaPluginBootstrap.Context atlasBootstrapContext(ClassLoader requestingClassLoader) {
        if (constructionDepth.get() == null || !owns(requestingClassLoader)) return null;
        return bootstrapContext;
    }

    boolean hasActiveConstructionContext() {
        return constructionDepth.get() != null;
    }

    private boolean owns(ClassLoader requestingClassLoader) {
        for (ClassLoader current = requestingClassLoader; current != null; current = current.getParent()) {
            if (current == this) return true;
        }
        return false;
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

    @FunctionalInterface
    interface ConstructionScope extends AutoCloseable {
        @Override void close();
    }
}
