package souther.build;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

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
     * and the builds that reuse it run at once. Each gets a driver of its own, so what one compile
     * does to its driver is not something another is holding.
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

    /** Refused where {@link DriverLoader#over(List)} refuses it, so a caller reads one message. */
    @Test
    void aToolchainThatIsNotOneIsRefusedBeforeItIsOpened(@TempDir Path dir) {
        IllegalStateException refused =
                assertThrows(IllegalStateException.class, () -> DriverLoader.open(List.of(dir)));

        assertTrue(refused.getMessage().contains("souther-build-driver"), refused.getMessage());
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
