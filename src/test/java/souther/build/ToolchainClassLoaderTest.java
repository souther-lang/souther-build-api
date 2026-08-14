package souther.build;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * What the loader answers when the toolchain and the caller both have a resource under the same
 * name, which is the arrangement a real build is in: the plugin realm carries whatever that build
 * tool was assembled with, and the toolchain carries what the resolved Souther was released with.
 */
class ToolchainClassLoaderTest {

    private static final String NAME = "META-INF/both-sides-have-one";

    @Test
    void aResourceComesFromTheToolchainRatherThanFromTheCaller(@TempDir Path dir) throws Exception {
        try (URLClassLoader loader = bothSidesHaving(dir)) {
            URL answered = loader.getResource(NAME);

            assertNotNull(answered);
            assertEquals(List.of("toolchain"), contentsOf(List.of(answered)));
        }
    }

    /**
     * The form {@link java.util.ServiceLoader} reads, and the one that decides which driver is found
     * when both sides declare one.
     */
    @Test
    void everyOneOfThemComesBackWithTheToolchainsFirst(@TempDir Path dir) throws Exception {
        try (URLClassLoader loader = bothSidesHaving(dir)) {
            List<URL> answered = Collections.list(loader.getResources(NAME));

            assertEquals(List.of("toolchain", "caller"), contentsOf(answered),
                    "the caller's is still reachable, behind the toolchain's rather than before it");
        }
    }

    /** The toolchain and the caller, each with a resource of that name saying which side it is. */
    private static URLClassLoader bothSidesHaving(Path dir) throws IOException {
        URL toolchain = declaring(dir.resolve("toolchain"));
        URL caller = declaring(dir.resolve("caller"));
        return new ToolchainClassLoader(new URL[] {toolchain},
                new URLClassLoader(new URL[] {caller}, null));
    }

    private static URL declaring(Path side) throws IOException {
        Path resource = side.resolve(NAME);
        Files.createDirectories(resource.getParent());
        Files.writeString(resource, side.getFileName().toString());
        return side.toUri().toURL();
    }

    private static List<String> contentsOf(List<URL> urls) throws IOException, URISyntaxException {
        List<String> read = new ArrayList<>();
        for (URL url : urls) {
            read.add(Files.readString(Path.of(url.toURI())));
        }
        return read;
    }
}
