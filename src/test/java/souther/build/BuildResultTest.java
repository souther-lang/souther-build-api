package souther.build;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildResultTest {

    private static final BuildDiagnostic SOMETHING =
            new BuildDiagnostic(BuildDiagnostic.Severity.ERROR, "src/A.sou:1: something");

    @Test
    void aFailedCompileHasToSayWhy() {
        IllegalArgumentException silent = assertThrows(IllegalArgumentException.class,
                () -> new BuildResult(false, List.of()));

        assertTrue(silent.getMessage().contains("succeeded=false"), silent.getMessage());
    }

    @Test
    void aSuccessfulCompileNeedSayNothing() {
        BuildResult result = new BuildResult(true, List.of());

        assertEquals(List.of(), result.diagnostics());
    }

    /** Warnings from a compile that went through are still worth a build log. */
    @Test
    void aSuccessfulCompileMayStillCarryDiagnostics() {
        BuildResult result = new BuildResult(true, List.of(SOMETHING));

        assertEquals(List.of(SOMETHING), result.diagnostics());
    }
}
