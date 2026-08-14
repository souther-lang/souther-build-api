package souther.build;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import souther.build.tck.SlowStandInDriver;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceConfigurationError;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A toolchain a caller can put down again. {@link DriverLoader#over(List)} opens a loader over the
 * resolved jars and hands back only the driver, so nothing the caller holds can release them; a
 * build that compiles thirty modules keeps thirty toolchains open until its JVM ends.
 */
class ToolchainTest {

    /** As a build resolves it: the driver, and a copy of the API it was compiled against. */
    private static final List<Path> RESOLVED =
            List.of(Path.of("target", "classes"), Path.of("target", "test-classes"));

    @Test
    void aDriverComesFromTheToolchainRatherThanFromTheCaller() throws IOException {
        try (Toolchain toolchain = DriverLoader.open(RESOLVED)) {
            SoutherBuildDriver driver = toolchain.driver();

            assertNotSame(ToolchainTest.class.getClassLoader(), driver.getClass().getClassLoader());
            assertSame(SoutherBuildDriver.class, driver.getClass().getInterfaces()[0]);
        }
    }

    /**
     * A toolchain outlives the compile it was opened for — that is the whole reason it is a handle —
     * so the compiles that reuse it get a driver each rather than one between them.
     *
     * <p>Instance identity and nothing more. Two compiles sharing a loader share every static on it,
     * and whether that is safe is the resolved compiler's answer rather than this API's.
     */
    @Test
    void aDriverIsItsOwnEachTimeOneIsAskedFor() throws IOException {
        try (Toolchain toolchain = DriverLoader.open(RESOLVED)) {
            assertNotSame(toolchain.driver(), toolchain.driver());
        }
    }

    /** What a caller could not do before: give the jars back. */
    @Test
    void closingAToolchainReleasesTheJarsItWasReadFrom(@TempDir Path dir) throws IOException {
        Path jar = jarHolding(dir.resolve("something.jar"), "META-INF/only-in-the-jar", "here");
        List<Path> resolved = new ArrayList<>(RESOLVED);
        resolved.add(jar);
        Toolchain toolchain = DriverLoader.open(resolved);
        assertNotNull(toolchain.loader().getResource("META-INF/only-in-the-jar"));

        toolchain.close();

        assertNull(toolchain.loader().getResource("META-INF/only-in-the-jar"));
    }

    /**
     * Said rather than let through. A driver taken from a toolchain that has been given back reads
     * whatever of it was already loaded and fails at the first thing that was not, which is a
     * complaint about a class rather than about the mistake.
     */
    @Test
    void aClosedToolchainHandsOutNoMoreDrivers() throws IOException {
        Toolchain toolchain = DriverLoader.open(RESOLVED);
        toolchain.close();

        IllegalStateException refused = assertThrows(IllegalStateException.class, toolchain::driver);

        assertTrue(refused.getMessage().contains("closed"), refused.getMessage());
    }

    /** Closing is what a caller does in a finally, and a caller that gets there twice is normal. */
    @Test
    void closingAToolchainTwiceIsNotAFailure() throws IOException {
        Toolchain toolchain = DriverLoader.open(RESOLVED);

        toolchain.close();
        toolchain.close();
    }

    /**
     * A build tool closes from wherever it finished, which need not be the thread that took the
     * driver. Either answer is one a caller can act on; what it may not be handed is a driver read
     * out of a loader that was closed between the check and the lookup, which comes back as the
     * caller's own driver or as a class that is no longer there.
     */
    @Test
    void closingWaitsForADriverThatIsBeingTakenRightNow(@TempDir Path dir) throws Exception {
        Toolchain toolchain = DriverLoader.open(slowToDriveToolchainIn(dir));
        AtomicLong taken = new AtomicLong();
        AtomicReference<Throwable> insteadOfADriver = new AtomicReference<>();
        Thread taking = new Thread(() -> {
            try {
                assertNotNull(toolchain.driver());
                taken.set(System.nanoTime());
            } catch (Throwable notADriver) {
                insteadOfADriver.set(notADriver);
            }
        });

        taking.start();
        waitUntilInsideDriver(taking);
        toolchain.close();
        long closed = System.nanoTime();

        taking.join();
        assertNull(insteadOfADriver.get(), () -> "no driver was ever taken: " + insteadOfADriver.get());
        assertTrue(taken.get() <= closed,
                "the jars went back while a driver was still being read out of them");
    }

    /**
     * Until {@code taking} reaches the wait in {@link SlowStandInDriver}'s static initializer, which
     * is the only thing it waits on and is inside {@code driver()}. Read off the thread rather than
     * slept past: a fixed wait is a guess about how long a loaded machine takes to start one, and
     * guessing short makes this test report the opposite of what happened.
     */
    private static void waitUntilInsideDriver(Thread taking) {
        while (taking.getState() != Thread.State.TIMED_WAITING
                && taking.getState() != Thread.State.TERMINATED) {
            Thread.onSpinWait();
        }
    }

    /**
     * A toolchain declaring the driver that takes its time, ahead of the one the other tests use:
     * {@link java.util.ServiceLoader} takes the first declaration it reads, and this loader reads the
     * toolchain's own before the caller's.
     */
    private static List<Path> slowToDriveToolchainIn(Path dir) throws IOException {
        Files.writeString(within(dir, BuildProtocol.RESOURCE), String.valueOf(BuildProtocol.VERSION));
        Files.writeString(within(dir, "META-INF/services/" + SoutherBuildDriver.class.getName()),
                SlowStandInDriver.class.getName());
        List<Path> resolved = new ArrayList<>();
        resolved.add(dir);
        resolved.addAll(RESOLVED);
        return resolved;
    }

    /** Refused where {@link DriverLoader#over(List)} refuses it, so a caller reads one message. */
    @Test
    void aToolchainThatIsNotOneIsRefusedBeforeItIsOpened(@TempDir Path dir) {
        IllegalStateException refused =
                assertThrows(IllegalStateException.class, () -> DriverLoader.open(List.of(dir)));

        assertTrue(refused.getMessage().contains("souther-build-driver"), refused.getMessage());
    }

    /**
     * The other half of what a toolchain is asked before it is handed over: not that a driver is
     * declared but that the declared one is there. Left to the first compile, a caller would be
     * holding a toolchain it cannot drive and would hear about it a build later.
     *
     * <p>Refused the way its neighbours are refused. A plugin reports an unusable toolchain out of
     * one catch, and {@link ServiceConfigurationError} would go past the catch it wrote.
     */
    @Test
    void aToolchainDeclaringADriverThatIsNotThereIsRefusedBeforeItIsOpened(@TempDir Path dir)
            throws IOException {
        Files.writeString(within(dir, BuildProtocol.RESOURCE), String.valueOf(BuildProtocol.VERSION));
        Files.writeString(within(dir, "META-INF/services/" + SoutherBuildDriver.class.getName()),
                "souther.build.tck.NotShippedWithThisSouther");

        IllegalStateException refused =
                assertThrows(IllegalStateException.class, () -> DriverLoader.open(List.of(dir)));

        assertTrue(refused.getMessage().contains(SoutherBuildDriver.class.getName()),
                refused.getMessage());
        assertInstanceOf(ServiceConfigurationError.class, refused.getCause(),
                "what the lookup said, kept for whoever is reading a build log");
    }

    private static Path within(Path dir, String name) throws IOException {
        Path file = dir.resolve(name);
        Files.createDirectories(file.getParent());
        return file;
    }

    private static Path jarHolding(Path jar, String name, String contents) throws IOException {
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            out.putNextEntry(new JarEntry(name));
            write(out, contents);
            out.closeEntry();
        }
        return jar;
    }

    private static void write(OutputStream out, String contents) throws IOException {
        out.write(contents.getBytes(StandardCharsets.UTF_8));
    }
}
