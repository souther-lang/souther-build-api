package souther.build.tck;

import souther.build.BuildRequest;
import souther.build.BuildResult;
import souther.build.SoutherBuildDriver;

import java.util.List;

/**
 * A driver that compiles nothing, for the tests that are about how a driver is found rather than
 * what one does.
 *
 * <p>In a package beneath the API's rather than in it, which is where a real driver sits too — that
 * is the arrangement the loader has to get right, so it is the one to test against.
 */
public final class StandInDriver implements SoutherBuildDriver {

    @Override
    public BuildResult compile(BuildRequest request) {
        return new BuildResult(true, List.of());
    }
}
