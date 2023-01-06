import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;

import vision.MyCalib3d;

/**
 * Verify {@MyCalib3d#estimateAffine3D()} which is the Umeyama method.
 */
public class TestMyCalib3d {

    public TestMyCalib3d() {
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
    }

    /**
     * This is a translation of the C++ test
     * calib3d/test/test_affine3d_estimator.cpp
     */
   // @Test
    public void testCalib3d_EstimateAffine3D() { // umeyama_3_pt

        // columns = points, like, for the multiplication below
        Mat points = Mat.zeros(3, 4, CvType.CV_64F);
        points.put(0, 0,
                0.80549149, 0.28906756, 0.58266182, 0.58266182,
                0.8225781, 0.57158557, 0.65474983, 0.65474983,
                0.79949521, 0.9864789, 0.25078834, 0.25078834);

        Mat pointsH = Mat.zeros(4, 4, CvType.CV_64F);
        pointsH.put(0, 0,
                0.80549149, 0.28906756, 0.58266182, 0.58266182,
                0.8225781, 0.57158557, 0.65474983, 0.65474983,
                0.79949521, 0.9864789, 0.25078834, 0.25078834,
                1, 1, 1, 1);

        Mat R = Mat.zeros(3, 3, CvType.CV_64F);
        R.put(0, 0,
                0.9689135, -0.0232753, 0.2463025,
                0.0236362, 0.9997195, 0.0014915,
                -0.2462682, 0.0043765, 0.9691918);

        Mat t = Mat.zeros(3, 1, CvType.CV_64F);
        t.put(0, 0, 1.0, 2.0, 3.0);

        // Java does not have Affine3d, so it's just Mat.
        // cv::Affine3d transform(R, t);
        Mat transform = Mat.zeros(4, 4, CvType.CV_64F);
        transform.put(0, 0,
                0.9689135, -0.0232753, 0.2463025, 1.0,
                0.0236362, 0.9997195, 0.0014915, 2.0,
                -0.2462682, 0.0043765, 0.9691918, 3.0,
                0, 0, 0, 1);

        // columns = transformed points
        Mat transformed_pointsH = new Mat();
        Core.gemm(transform, pointsH, 1.0, new Mat(), 0.0, transformed_pointsH);

        Mat transformed_points = Mat.zeros(3, transformed_pointsH.cols(), CvType.CV_64F);
        transformed_pointsH.submat(0, 3, 0, transformed_pointsH.cols()).copyTo(transformed_points);

        Double scale = 1.0;
        Mat trafo_est = MyCalib3d.estimateAffine3D(points.t(),
                transformed_points.t(),
                null, true);

        Mat R_est = trafo_est.submat(0, 3, 0, 3);

        assertTrue(Core.norm(R_est, R, Core.NORM_INF) <= 1e-6);
        Mat t_est = trafo_est.submat(0, 3, 3, 4);

        assertTrue(Core.norm(t_est, t, Core.NORM_INF) <= 1e-6);
        assertEquals(scale, 1.0, 1e-6);
    }

}
