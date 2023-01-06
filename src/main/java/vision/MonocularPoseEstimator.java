package vision;

import org.opencv.calib3d.Calib3d;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDouble;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.MatOfPoint3f;
import org.opencv.core.Size;

/**
 * Uses {@link Calib3d#solvePnP()}
 */
public class MonocularPoseEstimator extends BasePoseEstimator {
    final double f = 985; // 2.8mm lens
    final int height = 800;
    final int width = 1280;
    final int cx = width / 2;
    final int cy = height / 2;
    final Size dsize = new Size(width, height);
    final Mat kMat = VisionUtil.makeIntrinsicMatrix(f, dsize);
    final boolean useIMU;

    public MonocularPoseEstimator(boolean useIMU) {
        this.useIMU = useIMU;
    }

    @Override
    public String getName() {
        return String.format("MonocularPoseEstimator%s", useIMU ? "IMU" : "");
    }

    @Override
    public String getDescription() {
        return "Uses Calib3d.solvePnP()";
    }

    @Override
    public Mat[] getIntrinsicMatrices() {

        return new Mat[] { kMat };
    }

    @Override
    public MatOfDouble[] getDistortionMatrices() {
        return new MatOfDouble[] { new MatOfDouble(Mat.zeros(4, 1, CvType.CV_64F)) };
    }

    @Override
    public double[] getXOffsets() {
        return new double[] { 0.0 };
    }

    @Override
    public Size[] getSizes() {
        return new Size[] { dsize };
    }

    @Override
    public Mat getPose(double heading, MatOfPoint3f targetPoints, MatOfPoint2f[] imagePoints) {

        // ok the problem here is that the imagePoints are not repeated like the
        // targetPoints are.

        Mat newCamRVec = new Mat();
        Mat newCamTVec = new Mat();
        // System.out.println(targetPoints.dump());
        // System.out.println(imagePoints[0].dump());
        Calib3d.solvePnP(targetPoints, imagePoints[0], kMat,
                new MatOfDouble(), newCamRVec, newCamTVec, false,
                Calib3d.SOLVEPNP_ITERATIVE);
        if (useIMU) {
            newCamRVec = Mat.zeros(3, 1, CvType.CV_64F);
            newCamRVec.put(0, 0,
                    0, -heading, 0);
        }
        Mat newCamRMat = new Mat();
        Calib3d.Rodrigues(newCamRVec, newCamRMat);
        Mat transform = Mat.zeros(3, 4, CvType.CV_64F);
        newCamRMat.copyTo(transform.submat(0, 3, 0, 3));
        newCamTVec.copyTo(transform.submat(0, 3, 3, 4));
        return transform;
    }

    @Override
    public double[] getF() {
        return new double[] { f };
    }

    @Override
    public double[] getTilt() {
        return new double[] { 0 };
    }

}
