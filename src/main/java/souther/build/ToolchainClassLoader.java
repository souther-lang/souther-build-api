package souther.build;

import java.net.URL;
import java.net.URLClassLoader;

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
