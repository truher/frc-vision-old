import org.junit.jupiter.api.Test;
import org.opencv.core.Core;

import vision.PoseEstimatorHarness;

public class TestPoseEstimatorHarness {
    public TestPoseEstimatorHarness() {
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
    }

    @Test
    public void testPoseEstimatorHarness() {
        PoseEstimatorHarness harness = new PoseEstimatorHarness();
        harness.run();
    }
}
