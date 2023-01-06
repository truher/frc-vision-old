package vision;

import java.util.function.BinaryOperator;
import java.util.function.Function;

import org.opencv.calib3d.Calib3d;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDouble;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.MatOfPoint3f;
import org.opencv.core.Size;

/**
 * Solves the 3d binocular pose estimation problem constrained to rotation and
 * translation in the XZ plane.  The 2d solution is simple: find the centroid of the
 * projected points; that's the displacement, then rotate, either using the projection
 * angle or (better) the IMU.
 * 
 * camera/image arrays are {left, right}
 * 
 */
public class BinocularConstrainedPoseEstimator extends BasePoseEstimator {
    static final boolean DEBUG = false;
    static final int LEVEL = 1;
    final double f = 985; // 2.8mm lens
    final int height = 800;
    final int width = 1280;
    final int cx = width / 2;
    final int cy = height / 2;
    final Size dsize = new Size(width, height);
    final double b = 0.8; // camera width meters

    final boolean useIMU;

    public BinocularConstrainedPoseEstimator(boolean useIMU) {
        this.useIMU = useIMU;
    }

    @Override
    public String getName() {
        return String.format("BinocularConstrainedPoseEstimator%s", useIMU ? "IMU" : "");
    }

    @Override
    public String getDescription() {
        return "2.8mm lenses";
    }

    @Override
    public Mat[] getIntrinsicMatrices() {
        Mat kMat = VisionUtil.makeIntrinsicMatrix(f, dsize);
        return new Mat[] { kMat, kMat };
    }

    @Override
    public MatOfDouble[] getDistortionMatrices() {
        MatOfDouble dMat = new MatOfDouble(Mat.zeros(4, 1, CvType.CV_64F));
        return new MatOfDouble[] { dMat, dMat };
    }

    @Override
    public Mat getPose(double heading, MatOfPoint3f targetPoints, MatOfPoint2f[] imagePoints) {
        if (imagePoints.length != 2)
            throw new IllegalArgumentException();
        MatOfPoint2f leftPts = imagePoints[0];
        MatOfPoint2f rightPts = imagePoints[1];
        debug(1, "leftPts", leftPts);
        debug(1, "rightPts", rightPts);
        //
        // To solve Ax=b triangulation, first make b (the points in each eye,
        // transformed back into XYZ):
        Mat bMat = VisionUtil.makeBMat3d(leftPts, rightPts, f, cx, cy, b);
        debug(1, "bMat", bMat);

        // ... and the target x: (X, Y, Z, 1):
        Mat XMat = VisionUtil.makeXMat3d(targetPoints);
        // XMat = XMat.t();
        debug(1, "XMat", XMat);

        // Ax=b : A(from) = (to)
        // now cribbing from the Umeyama thing:
        // first discard the homogeneous part
        Mat from = new Mat();
        Calib3d.convertPointsFromHomogeneous(XMat, from);
        // points in rows
        from = from.reshape(1);
        debug(1, "from", from);
        // System.out.println(from.type());
        // System.out.println(from.size());

        final int count = from.checkVector(3); // 4 points
        // System.out.println(count);

        Mat to = new Mat();
        Calib3d.convertPointsFromHomogeneous(bMat.t(), to);
        // points in rows
        to = to.reshape(1);
        debug(1, "to", to);
        // System.out.println(to.type());
        // System.out.println(to.size());
        // System.out.println(to.checkVector(3));

        final double one_over_n = 1. / count;
        debug(1, "one over n", one_over_n);

        // yields a 3x1 vector (row) containing the means of each column
        final Function<Mat, Mat> colwise_mean = /* [one_over_n] */(final Mat m) -> {
            Mat my = new Mat();
            Core.reduce(m, my, 0, Core.REDUCE_SUM, CvType.CV_64F);
            // return my * one_over_n;
            return my.mul(Mat.ones(my.size(), my.type()), one_over_n);
        };

        // just subtracts the "mean" from the rows in A.
        final BinaryOperator<Mat> demean = /* [count] */(final Mat A, final Mat mean) -> {
            Mat A_centered = Mat.zeros(count, 3, CvType.CV_64F);
            for (int i = 0; i < count; i++) { // i = columns == points
                Mat foo = new Mat();
                Core.subtract(A.row(i), mean, foo);
                for (int j = 0; j < 3; j++) {
                    // A_centered.row(i) = A.row(i) - mean;
                    double d = foo.get(0, j)[0];
                    A_centered.put(i, j, d);
                }
            }
            return A_centered;
        };

        // these are the centroids of the actual and projected points
        Mat from_mean = colwise_mean.apply(from); // 3x1 vector of col means
        debug(1, "from mean", from_mean);
        Mat to_mean = colwise_mean.apply(to); // 3x1 vector of col means
        debug(1, "to mean", to_mean);

        // translate the from and to vectors so mean is zero
        Mat from_centered = demean.apply(from, from_mean);
        debug(1, "from centered", from_centered);
        Mat to_centered = demean.apply(to, to_mean);
        debug(1, "to centered", to_centered);

        Mat cov = new Mat();
        Core.gemm(to_centered.t(), from_centered, one_over_n, new Mat(), 0.0, cov);
        debug(1, "cov", cov);

        // try reprojecting
        Mat reproj = new Mat();
        Mat eye = Mat.eye(3, 3, CvType.CV_64F);
        Core.gemm(eye, from_centered.t(), 1.0, new Mat(), 0.0, reproj);
        debug(1, "reproj without rotation", reproj.t());
        debug(1, "to centered", to_centered);

        double rot = 0.0;
        if (useIMU) {
            rot = heading;
        } else {
            double averageAngleDiff = VisionUtil.averageAngularError(to_centered, reproj.t());
            debug(1, "averageAngleDiff", averageAngleDiff);
            rot = averageAngleDiff;
        }

        // fix the transform

        double c = Math.cos(rot);
        double s = Math.sin(rot);

        Mat newR = Mat.zeros(3, 3, CvType.CV_64F);
        newR.put(0, 0,
                c, 0, -s,
                0, 1, 0,
                s, 0, c);

        // try reprojecting again
        // Core.gemm(newR, from_centered.t(), 1.0, new Mat(), 0.0, reproj);
        // debug(1, "reproj with rotation", reproj.t());
        // debug(1, "to centered", to_centered);

        ////////////

        Mat rmat = newR;
        Mat new_to = new Mat();
        Core.gemm(rmat, from_mean.t(), 1.0, new Mat(), 0.0, new_to);

        Mat transform = Mat.zeros(3, 4, CvType.CV_64F);
        Mat r_part = transform.submat(0, 3, 0, 3);
        rmat.copyTo(r_part);
        // transform.col(3) = to_mean.t() - new_to;
        Mat t_part = transform.col(3);
        Mat tmat = new Mat();
        Core.subtract(to_mean.t(), new_to, tmat);
        tmat.copyTo(t_part);
        debug(1, "transform", transform);
        return transform;
    }

    @Override
    public double[] getXOffsets() {
        return new double[] { b / 2, -b / 2 };
    }

    @Override
    public Size[] getSizes() {
        return new Size[] { dsize, dsize };
    }

    @Override
    public double[] getF() {
        return new double[] { f,f };
    }

    @Override
    public double[] getTilt() {
        return new double[] { 0,0 };
    }

    public static void debug(int level, String msg, Mat m) {
        if (!DEBUG)
            return;
        if (level < LEVEL)
            return;
        System.out.println(msg);
        System.out.println(m.dump());
    }

    public static void debug(int level, String msg, double d) {
        if (!DEBUG)
            return;
        if (level < LEVEL)
            return;
        System.out.println(msg);
        System.out.println(d);
    }
}
