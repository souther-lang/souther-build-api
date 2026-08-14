package souther.build;

/**
 * One thing the compile had to say, already written out.
 *
 * <p>Rendered text rather than a structure, because the compiler's diagnostic model is the
 * compiler's and copying it here would make every change to it a change to this interface. What a
 * build log shows is what the CLI shows.
 *
 * @param severity what a build does about it
 * @param rendered the message as it is meant to be read, snippet and hint included, in the language
 *                 the request asked for. Several lines.
 */
public record BuildDiagnostic(Severity severity, String rendered) {

    /** What a build does about a diagnostic. */
    public enum Severity {
        /** The compile did not produce classes. A build stops. */
        ERROR,
        /** The compile produced classes and has something unproven to report. A build goes on. */
        WARNING
    }
}
