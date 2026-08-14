package souther.build;

/**
 * What a build plugin drives a Souther compile through.
 *
 * <p>The implementation ships with the compiler and is resolved for the Souther version the project
 * names, so a plugin and the compile it runs are two releases that never met. This interface is all
 * they share: it is loaded from the plugin's own loader and the implementation from an isolated one,
 * and nothing of the compiler's is named here, so what the compiler does with its own types is not
 * something a plugin release has to follow.
 *
 * <p>Found with {@link java.util.ServiceLoader#load(Class, ClassLoader)} over the loader the driver
 * was resolved into. The one-argument overload reads the context class loader, which is neither
 * build tool's.
 */
public interface SoutherBuildDriver {

    /**
     * Compiles what {@code request} names and writes the classes under its output directory.
     *
     * <p>Writing them is the driver's job rather than the caller's: the classes never cross the
     * loader boundary, so how the compiler hands them back is not part of this interface.
     *
     * <p>A compile that fails returns a result saying so, with the diagnostics rendered. Only
     * something this interface does not describe — an unreadable source directory, an output
     * directory that cannot be written — is raised.
     *
     * @param request what to compile and where to put it
     * @return whether the build may go on, and everything the compile had to say
     */
    BuildResult compile(BuildRequest request);
}
