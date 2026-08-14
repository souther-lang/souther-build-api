package souther.build;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.List;
import java.util.OptionalInt;
import java.util.ServiceLoader;

/**
 * How a build plugin gets from a resolved Souther toolchain to something it can compile with.
 *
 * <p>Here rather than in each plugin, because what it has to be right about is this interface's own
 * identity, and every plugin has to be right about it the same way.
 */
public final class DriverLoader {

    private DriverLoader() {}

    /**
     * The driver on {@code toolchain}, loaded apart from whatever is calling.
     *
     * @throws IllegalStateException if nothing there is a driver, or is one this build API cannot
     *                               speak to. Both name what was found, so a caller can say it
     *                               against the version it asked for.
     */
    public static SoutherBuildDriver over(List<Path> toolchain) {
        URL[] urls = urls(toolchain);
        // Asked of the toolchain alone. Through the loader below it would fall through to the
        // caller, and the question is what the resolved Souther states, not what anything else has.
        try (URLClassLoader toolchainOnly = new URLClassLoader(urls, null)) {
            against(BuildProtocol.declaredBy(toolchainOnly));
        } catch (IOException e) {
            throw new UncheckedIOException("unreadable toolchain", e);
        }
        return found(new ToolchainClassLoader(urls, DriverLoader.class.getClassLoader()));
    }

    /**
     * The driver in {@code toolchain}, for a caller that composed that loader itself.
     *
     * <p>Gradle is one: a worker under {@code classLoaderIsolation} already runs with the toolchain
     * behind it and this API in front, so there is nothing here to build. What is left is the
     * protocol check and the lookup, and those must not be written once per build tool.
     *
     * @throws IllegalStateException as {@link #over(List)} does
     */
    public static SoutherBuildDriver foundIn(ClassLoader toolchain) {
        against(BuildProtocol.declaredBy(toolchain));
        return found(toolchain);
    }

    private static SoutherBuildDriver found(ClassLoader loader) {
        // The two-argument load: the one-argument one reads the context class loader, which is the
        // build tool's and has none of this on it.
        return ServiceLoader.load(SoutherBuildDriver.class, loader).findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "the resolved souther-build-driver states a build protocol but offers no "
                                + SoutherBuildDriver.class.getName()));
    }

    /**
     * Read before a class of the driver's is loaded, so a Souther the caller cannot drive is refused
     * by number. Left to the first call it would be a linkage error naming a method.
     */
    private static void against(OptionalInt declared) {
        if (declared.isEmpty()) {
            throw new IllegalStateException(
                    "no souther-build-driver on the resolved toolchain: nothing there states a "
                            + "Souther build protocol");
        }
        if (declared.getAsInt() != BuildProtocol.VERSION) {
            throw new IllegalStateException(
                    "that Souther needs build protocol " + declared.getAsInt()
                            + ", and this build plugin speaks " + BuildProtocol.VERSION
                            + ". Move whichever of the two you would rather move.");
        }
    }

    private static URL[] urls(List<Path> toolchain) {
        URL[] urls = new URL[toolchain.size()];
        for (int i = 0; i < toolchain.size(); i++) {
            try {
                urls[i] = toolchain.get(i).toUri().toURL();
            } catch (MalformedURLException e) {
                throw new IllegalArgumentException("not a readable toolchain entry: "
                        + toolchain.get(i), e);
            }
        }
        return urls;
    }
}
