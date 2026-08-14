package souther.build;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URLClassLoader;

/**
 * A resolved Souther, open. Held by whoever asked for it, and given back when they are done with it.
 *
 * <p>A handle rather than the driver alone, because the jars stay open for as long as the loader
 * over them does: nothing releases them when the driver is collected, and a build tool that opened
 * one per module would hold every one of them until its JVM ended. Which is also why it is worth
 * keeping — a build compiling thirty modules with the same Souther opens this once and reads the
 * compiler's classes once.
 *
 * <p>Reusable across compiles that run at the same time. {@link #driver()} hands out a driver of its
 * own each time, so what one compile does to its driver is not something another is holding, and the
 * loader beneath them is parallel-capable.
 */
public final class Toolchain implements AutoCloseable {

    private final URLClassLoader loader;
    private volatile boolean closed;

    Toolchain(URLClassLoader loader) {
        this.loader = loader;
    }

    /**
     * A driver on this toolchain — a new one each time.
     *
     * @return a driver for one compile
     * @throws IllegalStateException if this toolchain has been closed
     */
    public SoutherBuildDriver driver() {
        if (closed) {
            throw new IllegalStateException("this toolchain is closed and hands out no more drivers");
        }
        return DriverLoader.driverIn(loader);
    }

    /**
     * Gives the jars back. Drivers taken from here are done with, and asking for another says so.
     *
     * <p>Closing one that is already closed does nothing, which is what a caller closing in a
     * {@code finally} needs.
     *
     * @throws UncheckedIOException if the loader could not be closed
     */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            loader.close();
        } catch (IOException e) {
            throw new UncheckedIOException("the Souther toolchain could not be closed", e);
        }
    }

    /** What the toolchain was read through, for the tests that are about closing it. */
    ClassLoader loader() {
        return loader;
    }
}
