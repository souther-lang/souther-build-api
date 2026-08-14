package souther.build;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.OptionalInt;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A plugin reads the protocol a driver was built against before it loads a class from it, so that a
 * disagreement is a build error naming both numbers rather than a linkage error at the first call.
 */
class BuildProtocolTest {

    @Test
    void theProtocolADriverDeclaresIsReadFromWhereItWasResolved(@TempDir Path dir)
            throws IOException {
        declare(dir, "1");

        assertEquals(OptionalInt.of(1), BuildProtocol.declaredBy(loaderOver(dir)));
    }

    @Test
    void somethingThatDeclaresNoProtocolIsNotADriver(@TempDir Path dir) {
        assertEquals(OptionalInt.empty(), BuildProtocol.declaredBy(loaderOver(dir)));
    }

    /**
     * The constant a driver and a plugin compare, and the major a build resolves this artifact by,
     * are the same number said twice. This is what keeps the second one from drifting.
     */
    @Test
    void theProtocolIsThisArtifactsMajorVersion() throws IOException {
        Properties project = new Properties();
        try (InputStream in = getClass().getResourceAsStream("/project.properties")) {
            project.load(in);
        }
        String version = project.getProperty("version");
        int major = Integer.parseInt(version.split("[.-]")[0]);

        assertEquals(major, BuildProtocol.VERSION,
                () -> "this artifact is " + version + ", so the protocol is " + major);
    }

    private static void declare(Path dir, String text) throws IOException {
        Path resource = dir.resolve(BuildProtocol.RESOURCE);
        Files.createDirectories(resource.getParent());
        Files.writeString(resource, text);
    }

    /** A loader over {@code dir} alone: no parent, so nothing answers but what was written there. */
    private static ClassLoader loaderOver(Path dir) {
        try {
            return new URLClassLoader(new URL[] {dir.toUri().toURL()}, null);
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }
}
