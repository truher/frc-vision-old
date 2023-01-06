package vision;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDouble;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.MatOfPoint3f;
import org.opencv.core.Size;

/**
 * Always returns the origin; a baseline for awfulness.
 */
public class ConstantPoseEstimator extends BasePoseEstimator {
    final double f = 985; // 2.8mm lens
    final int height = 800;
    final int width = 1280;
    final int cx = width / 2;
    final int cy = height / 2;
    final Size dsize = new Size(width, height);

    @Override
    public String getName() {
        return "ConstantPoseEstimator";
    }

    @Override
    public String getDescription() {
        return "Always returns the origin.";
    }

    @Override
    public MatOfDouble[] getDistortionMatrices() {
        MatOfDouble dMat = new MatOfDouble(Mat.zeros(4, 1, CvType.CV_64F));
        return new MatOfDouble[] { dMat };
    }

    @Override
    public Mat[] getIntrinsicMatrices() {
        return new Mat[] { VisionUtil.makeIntrinsicMatrix(f, dsize) };
    }

    @Override
    public Mat getPose(double heading, MatOfPoint3f targetPoints, MatOfPoint2f[] imagePoints) {
        Mat pose = Mat.zeros(3, 4, CvType.CV_64F);
        pose.put(0, 0,
                1, 0, 0, 0,
                0, 1, 0, 0,
                0, 0, 1, 0);
        return pose;
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
    public double[] getF() {
        return new double[] { f };
    }

    @Override
    public double[] getTilt() {
        return new double[] { 0 };
    }
}
