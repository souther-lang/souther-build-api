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
     * The toolchain {@code jars} are, open and held by the caller.
     *
     * <p>What {@link #over(List)} could not give back: the jars stay open for as long as the loader
     * over them does, and there the caller is handed the driver alone. A build tool that compiles
     * several projects with the same Souther opens one of these and keeps it — the compiler's
     * classes are read once rather than once per project — and closes it when the build is done.
     *
     * @param jars the jars a build resolved for the Souther version the project names
     * @return that Souther, open
     * @throws IllegalStateException if nothing there is a driver, or is one this build API cannot
     *                               speak to. Both name what was found, so a caller can say it
     *                               against the version it asked for.
     * @throws IllegalArgumentException if an entry is not a path a loader can be opened over
     * @throws UncheckedIOException if the toolchain cannot be read
     */
    public static Toolchain open(List<Path> jars) {
        URL[] urls = urls(jars);
        // Asked of the toolchain alone. Through the loader below it would fall through to the
        // caller, and the question is what the resolved Souther states, not what anything else has.
        try (URLClassLoader toolchainOnly = new URLClassLoader(urls, null)) {
            against(BuildProtocol.declaredBy(toolchainOnly));
        } catch (IOException e) {
            throw new UncheckedIOException("unreadable toolchain", e);
        }
        Toolchain opened =
                new Toolchain(new ToolchainClassLoader(urls, DriverLoader.class.getClassLoader()));
        try {
            declaredIn(opened.loader());
        } catch (RuntimeException | Error notADriver) {
            // Refused, so nothing is going to close what was opened to find that out.
            opened.close();
            throw notADriver;
        }
        return opened;
    }

    /**
     * The driver on {@code toolchain}, loaded apart from whatever is calling.
     *
     * <p>The loader it is read through is not handed back and nothing else can release it, so the
     * jars stay open for the rest of the JVM. A caller that drives more than one compile wants
     * {@link #open(List)} instead.
     *
     * @param toolchain the jars a build resolved for the Souther version the project names
     * @return the driver they carry, loaded apart from the caller
     * @throws IllegalStateException as {@link #open(List)} does
     * @throws IllegalArgumentException as {@link #open(List)} does
     * @throws UncheckedIOException as {@link #open(List)} does
     */
    public static SoutherBuildDriver over(List<Path> toolchain) {
        return open(toolchain).driver();
    }

    /**
     * The driver in {@code toolchain}, for a caller that composed that loader itself.
     *
     * <p>Gradle is one: a worker under {@code classLoaderIsolation} already runs with the toolchain
     * behind it and this API in front, so there is nothing here to build. What is left is the
     * protocol check and the lookup, and those must not be written once per build tool.
     *
     * @param toolchain a loader the caller already composed, with the toolchain behind it and this
     *                  API in front
     * @return the driver it carries
     * @throws IllegalStateException as {@link #over(List)} does
     */
    public static SoutherBuildDriver foundIn(ClassLoader toolchain) {
        against(BuildProtocol.declaredBy(toolchain));
        return driverIn(toolchain);
    }

    static SoutherBuildDriver driverIn(ClassLoader loader) {
        return declaredIn(loader).get();
    }

    /**
     * The driver {@code loader} declares, without making one. Asked when a toolchain is opened: what
     * it answers is that there is a driver there and its class loads, which is the part a caller has
     * to hear before it holds the toolchain rather than at the first compile.
     */
    private static ServiceLoader.Provider<SoutherBuildDriver> declaredIn(ClassLoader loader) {
        // The two-argument load: the one-argument one reads the context class loader, which is the
        // build tool's and has none of this on it.
        return ServiceLoader.load(SoutherBuildDriver.class, loader).stream().findFirst()
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
