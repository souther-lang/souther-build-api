package souther.build;

import java.nio.file.Path;
import java.util.List;

/**
 * One compile, as a build asks for it.
 *
 * @param sourcePaths where the {@code .sou} are: a directory is read through, a file is read. A
 *                    build system whose source set has several directories passes several. Which
 *                    files these come to, and in what order, is the driver's to decide — two plugins
 *                    that walked them separately could disagree about it.
 * @param classPath the compiled classes of the projects this one depends on, as a build already
 *                  resolves them. An import naming no module among the sources is looked for here.
 * @param outputDirectory where the generated classes are written. Created if it is not there.
 * @param languageTag the language to report diagnostics in, or null to let the compiler decide the
 *                    way it does for a command line that named none.
 */
public record BuildRequest(List<Path> sourcePaths, List<Path> classPath, Path outputDirectory,
                           String languageTag) {

    public BuildRequest {
        sourcePaths = List.copyOf(sourcePaths);
        classPath = List.copyOf(classPath);
    }
}
