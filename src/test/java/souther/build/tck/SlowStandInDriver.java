package souther.build.tck;

import souther.build.BuildRequest;
import souther.build.BuildResult;
import souther.build.SoutherBuildDriver;

import java.util.List;

/**
 * A driver that takes a while to come into being, for the one test that has to watch a toolchain
 * while a driver is being taken from it.
 *
 * <p>The wait is in the static initializer because that is where a real driver's is: the compiler's
 * classes are read and its tables built the first time one is made. Declared only by the toolchain
 * that test assembles, so nothing else pays for it.
 */
public final class SlowStandInDriver implements SoutherBuildDriver {

    /** Long enough to outlast a close that arrives in the middle of it. */
    private static final long TAKES_MILLIS = 200;

    static {
        try {
            Thread.sleep(TAKES_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public BuildResult compile(BuildRequest request) {
        return new BuildResult(true, List.of());
    }
}
