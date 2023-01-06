import static org.junit.jupiter.api.Assertions.assertEquals;

import org.opencv.calib3d.Calib3d;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint3f;

import vision.VisionUtil;

/**
 * Verifies a few of the functions in {@link VisionUtil}.
 */
public class TestVisionUtil {
    public static final double DELTA = 0.00001;
    public static final boolean DEBUG = false;

    public TestVisionUtil() {
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
    }

    // @Test
    public void testHomogeneous() {
        MatOfPoint3f targetGeometryMeters = VisionUtil.makeTargetGeometry3f(0.5, 0.5);
        debug("target", targetGeometryMeters);
        Mat XMat = VisionUtil.makeXMat3d(targetGeometryMeters);
        debug("homogeneous target", XMat);
        System.out.println(XMat.size());
    }

    /**
     * Verify one case for {@link VisionUtil#combineRotations()}.
     */
    // @Test
    public void testCombineRotations() {
        Mat pan = Mat.zeros(3, 1, CvType.CV_64F);
        pan.put(0, 0, 0.0, -0.5, 0.0); // pan to right, world to left, so negative
        Mat tilt = Mat.zeros(3, 1, CvType.CV_64F);
        tilt.put(0, 0, -0.5, 0.0, 0.0); // tilt up, world down, so negative
        debug("pan vector", pan);
        debug("tilt vector", tilt);
        // pan first to keep horizon horizontal
        Mat productV = VisionUtil.combineRotations(pan, tilt);
        debug("product vector", productV);
        assertEquals(-0.48945, productV.get(0, 0)[0], DELTA);
        assertEquals(-0.48945, productV.get(1, 0)[0], DELTA);
        assertEquals(0.12498, productV.get(2, 0)[0], DELTA);
    }

    /**
     * Verify one case for {@link VisionUtil#rotm2euler()}.
     */
    // @Test
    public void testRotm2euler() {
        Mat pan = Mat.zeros(3, 1, CvType.CV_64F);
        pan.put(0, 0, 0.0, -0.7854, 0.0); // pan 45deg to right, world to left, so
        // negative
        Mat tilt = Mat.zeros(3, 1, CvType.CV_64F);
        tilt.put(0, 0, -0.7854, 0.0, 0.0); // tilt 45deg up, world down, so negative
        debug("pan vector", pan);
        debug("tilt vector", tilt);
        // pan first to keep horizon horizontal
        Mat productV = VisionUtil.combineRotations(pan, tilt);
        Mat productM = new Mat();
        Calib3d.Rodrigues(productV, productM);

        Mat r = productM.t();

        debug("result", r);

        Mat euler = VisionUtil.rotm2euler(r);

        debug("euler radians", euler);

        assertEquals(0.7854, euler.get(0, 0)[0], DELTA); // upward tilt
        assertEquals(0.7854, euler.get(1, 0)[0], DELTA); // rightward pan
        assertEquals(0, euler.get(2, 0)[0], DELTA); // no rotation around the camera axis
    }

    public static void debugmsg(String msg) {
        if (!DEBUG)
            return;
        System.out.println(msg);
    }

    public static void debug(String msg, Mat m) {
        if (!DEBUG)
            return;
        System.out.println(msg);
        System.out.println(m.dump());
    }

    public static void debug(String msg, double d) {
        if (!DEBUG)
            return;
        System.out.println(msg);
        System.out.println(d);
    }
}
