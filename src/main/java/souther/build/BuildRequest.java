package souther.build;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * One compile, as a build asks for it.
 *
 * @param sourcePaths where the {@code .sou} are: a directory is read through, a file is read. A
 *                    build system whose source set has several directories passes several. Which
 *                    files these come to, and in what order, is the driver's to decide — two plugins
 *                    that walked them separately could disagree about it.
 * @param classPath the compiled classes of the projects this one depends on, as a build already
 *                  resolves them. An import naming no module among the sources is looked for here.
 * @param outputDirectory where the generated classes are written. Created if it is not there. It
 *                        need not hold only generated classes — on Maven it is where javac writes
 *                        too — so a driver removes what it wrote before and no longer writes,
 *                        rather than emptying it.
 * @param stateDirectory somewhere of the build's that a driver may keep what it needs between
 *                       compiles, such as the record of what it last generated. Created if it is
 *                       not there. Nothing else reads it, and losing it costs a stale class rather
 *                       than a wrong compile.
 * @param languageTag the language to report diagnostics in, or null to let the compiler decide the
 *                    way it does for a command line that named none.
 */
public record BuildRequest(List<Path> sourcePaths, List<Path> classPath, Path outputDirectory,
                           Path stateDirectory, String languageTag) {

    /**
     * Copies the lists, and names whichever directory the caller left out — a plugin built against
     * an earlier version of this record passes one fewer, and inside a compile that would surface as
     * a failure naming whatever the driver did with it first.
     *
     * @throws NullPointerException if a directory is missing
     */
    public BuildRequest {
        sourcePaths = List.copyOf(sourcePaths);
        classPath = List.copyOf(classPath);
        Objects.requireNonNull(outputDirectory, "outputDirectory");
        Objects.requireNonNull(stateDirectory, "stateDirectory");
    }
}
