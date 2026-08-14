package souther.build;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The loader a resolved Souther toolchain is read through: its own classes first, and this build API
 * from whoever is calling.
 *
 * <p>Its own first rather than the caller's, because the caller is a build tool's plugin realm — it
 * carries whatever that tool and that plugin were built with, and a library on both sides would
 * otherwise reach the compiler as the build tool's version rather than the one the compiler was
 * released against. What a Souther release needs is stated by the driver artifact's own
 * dependencies, and this is what lets that statement hold.
 *
 * <p>This API is the exception, and has to be: the plugin holds the interface it looks the driver up
 * by, and answering that name from the toolchain would make two of it, under which no driver
 * implements the interface being asked for.
 */
final class ToolchainClassLoader extends URLClassLoader {

    static {
        // Not inherited: URLClassLoader registers itself, and a subclass that does not register too
        // gets one lock for the whole loader out of getClassLoadingLock rather than one per name.
        // Gradle drives a build from several threads, and they would load classes one at a time.
        registerAsParallelCapable();
    }

    /**
     * The one package both sides name — this one. That package and no other: a driver is written in
     * a package beneath it, and delegating by prefix would hand the driver itself back to the
     * caller, which is the opposite of the point.
     */
    private static final String SHARED_PACKAGE = ToolchainClassLoader.class.getPackageName();

    ToolchainClassLoader(URL[] toolchain, ClassLoader plugin) {
        super("souther-toolchain", toolchain, plugin);
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        if (isTheBuildApi(name)) {
            return super.loadClass(name, resolve);
        }
        synchronized (getClassLoadingLock(name)) {
            Class<?> already = findLoadedClass(name);
            if (already != null) {
                return resolved(already, resolve);
            }
            try {
                return resolved(findClass(name), resolve);
            } catch (ClassNotFoundException notOnTheToolchain) {
                // The platform's, and whatever the caller has that the toolchain does not.
                return super.loadClass(name, resolve);
            }
        }
    }

    /** A resource of the toolchain's before one of the caller's, for the same reason as a class. */
    @Override
    public URL getResource(String name) {
        URL own = findResource(name);
        return own != null ? own : super.getResource(name);
    }

    /**
     * The same order when every one of them is asked for, which is the form that decides which driver
     * is found: {@link java.util.ServiceLoader} reads the service declarations this way and takes the
     * first that names a class. Left to the inherited order the caller's would be read first, and a
     * plugin realm carrying a driver of its own would answer for the toolchain.
     */
    @Override
    public Enumeration<URL> getResources(String name) throws IOException {
        List<URL> ordered = new ArrayList<>(Collections.list(findResources(name)));
        Set<String> already = new HashSet<>();
        for (URL own : ordered) {
            already.add(own.toString());
        }
        // Ours again among these, and it is already above.
        for (URL theirs : Collections.list(super.getResources(name))) {
            if (already.add(theirs.toString())) {
                ordered.add(theirs);
            }
        }
        return Collections.enumeration(ordered);
    }

    /** A class of this API itself, rather than one of something written against it. */
    private static boolean isTheBuildApi(String name) {
        return name.startsWith(SHARED_PACKAGE + ".")
                && name.lastIndexOf('.') == SHARED_PACKAGE.length();
    }

    private Class<?> resolved(Class<?> found, boolean resolve) {
        if (resolve) {
            resolveClass(found);
        }
        return found;
    }
}
