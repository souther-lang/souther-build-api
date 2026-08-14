package souther.build;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.OptionalInt;

/** The protocol this interface is, and what a driver has to say to be spoken to over it. */
public final class BuildProtocol {

    private BuildProtocol() {}

    /** The protocol this interface is. Held to this artifact's major version by a test. */
    public static final int VERSION = 1;

    /** Where a driver states the protocol it was built against. */
    public static final String RESOURCE = "META-INF/souther-build-protocol";

    /**
     * The protocol a driver resolved into {@code loader} states, or empty if it states none.
     *
     * <p>A resource rather than anything of the driver's own, so this can be asked before a class of
     * the driver's is loaded — which is the point: a driver built against a protocol this one does
     * not speak has to be refused by name, not by whatever linkage error its first call would raise.
     */
    public static OptionalInt declaredBy(ClassLoader loader) {
        URL declaration = loader.getResource(RESOURCE);
        if (declaration == null) {
            return OptionalInt.empty();
        }
        try (InputStream in = declaration.openStream()) {
            String stated = new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
            return OptionalInt.of(Integer.parseInt(stated));
        } catch (IOException | NumberFormatException e) {
            throw new IllegalStateException(
                    "unreadable Souther build protocol declaration at " + declaration, e);
        }
    }
}
