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
 * <p>{@link #driver()} hands out a driver of its own each time, so what one compile does to its
 * driver is not something another is holding. Whether two compiles may then run at once over one
 * toolchain is not this handle's to answer: they share every class the loader beneath them carries,
 * and what a compiler keeps in a static is settled by the Souther a build resolved rather than here.
 * A build that compiles modules in parallel has to read that from the driver it resolved.
 *
 * <p>What this does answer for is itself. {@link #driver()} and {@link #close()} may be called from
 * different threads: a driver is handed out or refused, and never read out of a loader being closed
 * underneath it.
 */
public final class Toolchain implements AutoCloseable {

    private final URLClassLoader loader;
    /**
     * Read and written under this object's monitor, along with the lookup and the closing themselves.
     * A flag on its own — volatile or not — would let a caller pass the check and then be read out
     * of a loader another thread closed in between, which is the class-shaped failure
     * {@link #driver()} exists to say something else instead of.
     */
    private boolean closed;

    Toolchain(URLClassLoader loader) {
        this.loader = loader;
    }

    /**
     * A driver on this toolchain — a new one each time.
     *
     * @return a driver for one compile
     * @throws IllegalStateException if this toolchain has been closed
     */
    public synchronized SoutherBuildDriver driver() {
        if (closed) {
            throw new IllegalStateException("this toolchain is closed and hands out no more drivers");
        }
        return DriverLoader.driverIn(loader);
    }

    /**
     * Gives the jars back. Drivers taken from here are done with, and asking for another says so.
     *
     * <p>Closing one that is already closed does nothing, which is what a caller closing in a
     * {@code finally} needs. A close that could not give every jar back is closed too, and is
     * reported: the loader marks its class path closed whichever way each jar went, so there is
     * nothing left for a second attempt to release.
     *
     * <p>Waits for a driver being taken right now, and everything after it is refused.
     *
     * @throws UncheckedIOException if the loader could not be closed
     */
    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        // Before the close rather than after it. A close that threw still left the loader unusable,
        // and a toolchain that went on calling itself open would answer the next driver() out of the
        // caller's own realm — the loader reads nothing once its class path is closed, and what is
        // behind it is the plugin that asked.
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
