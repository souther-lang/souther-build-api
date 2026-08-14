package souther.build;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How a build plugin gets from a resolved toolchain to a driver. The toolchain here is this
 * project's own output — a stand-in driver, and a copy of this API beside it, which is the
 * arrangement a resolved one has and the one the loader has to be right about.
 */
class DriverLoaderTest {

    /** As a build resolves it: the driver, and a copy of the API it was compiled against. */
    private static final List<Path> TOOLCHAIN =
            List.of(Path.of("target", "classes"), Path.of("target", "test-classes"));

    @Test
    void theDriverComesFromTheToolchainRatherThanFromTheCaller() {
        SoutherBuildDriver driver = DriverLoader.over(TOOLCHAIN);

        assertNotSame(DriverLoaderTest.class.getClassLoader(), driver.getClass().getClassLoader(),
                "the same class is reachable both ways here, and the toolchain's is the one that "
                        + "carries the Souther the build asked for");
    }

    @Test
    void theInterfaceComesFromTheCallerEvenThoughTheToolchainHasOneToo() {
        SoutherBuildDriver driver = DriverLoader.over(TOOLCHAIN);

        assertSame(SoutherBuildDriver.class, driver.getClass().getInterfaces()[0],
                "two of this interface and nothing implements the one being asked for");
    }

    /**
     * Some build tools isolate the work themselves — Gradle runs it in a worker under a loader it
     * composed — and there is nothing left for this to build. What is left is the protocol check and
     * the lookup, which are the parts that must not be written twice.
     */
    @Test
    void aDriverIsFoundInALoaderTheCallerComposedItself() {
        SoutherBuildDriver driver = DriverLoader.foundIn(DriverLoaderTest.class.getClassLoader());

        assertNotNull(driver);
    }

    @Test
    void aLoaderStatingNoProtocolIsRefusedTheSameWay() throws IOException {
        try (URLClassLoader empty = new URLClassLoader(new URL[0], null)) {
            IllegalStateException refused =
                    assertThrows(IllegalStateException.class, () -> DriverLoader.foundIn(empty));

            assertTrue(refused.getMessage().contains("souther-build-driver"), refused.getMessage());
        }
    }

    @Test
    void aToolchainThatStatesNoProtocolIsNotOne(@TempDir Path dir) {
        IllegalStateException refused =
                assertThrows(IllegalStateException.class, () -> DriverLoader.over(List.of(dir)));

        assertTrue(refused.getMessage().contains("souther-build-driver"), refused.getMessage());
    }

    @Test
    void aDriverSpeakingAnotherProtocolIsRefusedByNumberRatherThanAtItsFirstCall(@TempDir Path dir)
            throws IOException {
        Path declaration = dir.resolve(BuildProtocol.RESOURCE);
        Files.createDirectories(declaration.getParent());
        Files.writeString(declaration, String.valueOf(BuildProtocol.VERSION + 1));

        IllegalStateException refused =
                assertThrows(IllegalStateException.class, () -> DriverLoader.over(List.of(dir)));

        assertTrue(refused.getMessage().contains(String.valueOf(BuildProtocol.VERSION + 1))
                        && refused.getMessage().contains(String.valueOf(BuildProtocol.VERSION)),
                "both numbers, or the reader cannot tell which side to move: " + refused.getMessage());
    }

    /** The protocol this project's own output states is the one it is, or the tests above are set
     *  up against a toolchain that could not be loaded by anything. */
    @Test
    void theStandInToolchainStatesTheProtocolThisApiIs() {
        assertEquals(BuildProtocol.VERSION,
                BuildProtocol.declaredBy(DriverLoaderTest.class.getClassLoader()).orElseThrow());
    }
}
