package souther.build;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildRequestTest {

    private static final Path SOMEWHERE = Path.of("target", "somewhere");

    @Test
    void theListsAreTheRequestsOwnRatherThanTheCallersToKeepChanging() {
        List<Path> sourcePaths = new ArrayList<>(List.of(Path.of("src")));

        BuildRequest request =
                new BuildRequest(sourcePaths, List.of(), SOMEWHERE, SOMEWHERE, null);
        sourcePaths.clear();

        assertEquals(List.of(Path.of("src")), request.sourcePaths());
    }

    /**
     * A plugin built against an earlier version of this record passes one component fewer, and the
     * one it leaves out has to be named here — inside a compile it would be an NPE naming whatever
     * the driver happened to do with it first.
     */
    @Test
    void aMissingDirectoryIsRefusedByTheNameOfTheComponent() {
        NullPointerException missing = assertThrows(NullPointerException.class,
                () -> new BuildRequest(List.of(), List.of(), SOMEWHERE, null, null));

        assertTrue(missing.getMessage().contains("stateDirectory"), missing.getMessage());
    }

    @Test
    void noLanguageTagIsTheCompilersToDecideRatherThanSomethingToRefuse() {
        BuildRequest request = new BuildRequest(List.of(), List.of(), SOMEWHERE, SOMEWHERE, null);

        assertNull(request.languageTag());
    }
}
