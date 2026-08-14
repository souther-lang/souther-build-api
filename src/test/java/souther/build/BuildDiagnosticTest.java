package souther.build;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildDiagnosticTest {

    @Test
    void aDiagnosticWithNoSeverityIsRefusedByTheNameOfTheComponent() {
        NullPointerException missing = assertThrows(NullPointerException.class,
                () -> new BuildDiagnostic(null, "src/A.sou:1: something"));

        assertTrue(missing.getMessage().contains("severity"), missing.getMessage());
    }

    @Test
    void aDiagnosticWithNothingRenderedIsRefusedTheSameWay() {
        NullPointerException missing = assertThrows(NullPointerException.class,
                () -> new BuildDiagnostic(BuildDiagnostic.Severity.WARNING, null));

        assertTrue(missing.getMessage().contains("rendered"), missing.getMessage());
    }
}
