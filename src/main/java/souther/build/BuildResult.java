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

    public BuildResult {
        diagnostics = List.copyOf(diagnostics);
    }
}
