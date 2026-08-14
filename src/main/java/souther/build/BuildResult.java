package souther.build;

import java.util.List;

/**
 * What came of a compile.
 *
 * <p>The classes are not here: the driver has already written them where the request said. What
 * crosses back is what a build has to report and what it has to decide by.
 *
 * @param succeeded whether the build may go on. False and no diagnostic is not a result a driver may
 *                  return — a build that fails has something to say about it.
 * @param diagnostics everything the compile had to say, in the order it said it. A successful
 *                    compile still carries its warnings.
 */
public record BuildResult(boolean succeeded, List<BuildDiagnostic> diagnostics) {

    /**
     * Copies the diagnostics and holds the driver to the one thing said above: a build that stops
     * has a reason to show for it. Left unchecked, that reason goes missing at the one moment a
     * build log is read.
     *
     * @throws IllegalArgumentException if the compile failed and said nothing about it
     * @throws NullPointerException if there are no diagnostics to copy
     */
    public BuildResult {
        diagnostics = List.copyOf(diagnostics);
        if (!succeeded && diagnostics.isEmpty()) {
            throw new IllegalArgumentException(
                    "a failed compile has to say why: no diagnostics with succeeded=false");
        }
    }
}
