package vision;

import java.util.function.BinaryOperator;
import java.util.function.Function;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDouble;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.MatOfPoint3f;
import org.opencv.core.Size;

/**
 * use the umeyama method in 2d. works pretty well within a few meters.
 */
public class Binocular2dUmeyamaPoseEstimator extends BasePoseEstimator {
    static final boolean DEBUG = false;
    static final int LEVEL = 1;
    final double f = 914;
    final int height = 800;
    final int width = 1280;
    final int cx = width / 2;
    final Size dsize = new Size(width, height);
    final double b = 0.8;

    final boolean useIMU;

    public Binocular2dUmeyamaPoseEstimator(boolean useIMU) {
        this.useIMU = useIMU;
    }

    @Override
    public String getName() {
        return String.format("Binocular2dUmeyamaPoseEstimator%s", useIMU ? "IMU" : "");
    }

    @Override
    public String getDescription() {
        return "Umeyama solve adapted to 2d";
    }

    @Override
    public Mat[] getIntrinsicMatrices() {
        final Mat kMat = VisionUtil.makeIntrinsicMatrix(f, dsize);
        return new Mat[] { kMat, kMat };
    }

    @Override
    public MatOfDouble[] getDistortionMatrices() {
        final MatOfDouble dMat = new MatOfDouble(Mat.zeros(4, 1, CvType.CV_64F));
        return new MatOfDouble[] { dMat, dMat };
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
    public Mat getPose(double heading, MatOfPoint3f targetPoints, MatOfPoint2f[] imagePoints) {
        MatOfPoint2f leftPts = imagePoints[0];
        MatOfPoint2f rightPts = imagePoints[1];

        // To solve Ax=b triangulation, first make b:
        Mat bMat = VisionUtil.makeBMat2d(leftPts, rightPts, b, f, cx);
        debug(1, "bMat (b data)", bMat);

        // ... and x: (X, Z, 1):
        Mat XMat = VisionUtil.makeXMat2d(targetPoints);
        debug(1, "XMat (x data)", XMat);

        Mat from = Mat.zeros(2, XMat.cols(), CvType.CV_64F);
        for (int col = 0; col < XMat.cols(); ++col) {
            from.put(0, col, XMat.get(0, col)[0]);
            from.put(1, col, XMat.get(1, col)[0]);
        }
        from = from.t();
        debug(0, "from", from);
        // Mat from = targetPointsMultipliedXZHomogeneousMat.t();

        Mat to = Mat.zeros(2, bMat.cols(), CvType.CV_64F);
        for (int col = 0; col < bMat.cols(); ++col) {
            to.put(0, col, bMat.get(0, col)[0]);
            to.put(1, col, bMat.get(1, col)[0]);
        }
        to = to.t();
        debug(0, "to", to);

        // Mat to = TinvMinvBmat.t();

        final int count = from.checkVector(2);
        if (count < 3)
            throw new IllegalArgumentException(
                    "Umeyama algorithm needs at least 3 points for affine transformation estimation.");
        if (to.checkVector(2) != count)
            throw new IllegalArgumentException("Point sets need to have the same size");
        final double one_over_n = 1. / count;

        // yields a 3x1 vector (row) containing the means of each column
        final Function<Mat, Mat> colwise_mean = /* [one_over_n] */(final Mat m) -> {
            Mat my = new Mat();
            Core.reduce(m, my, 0, Core.REDUCE_SUM, CvType.CV_64F);
            // return my * one_over_n;
            return my.mul(Mat.ones(my.size(), my.type()), one_over_n);
        };

        final BinaryOperator<Mat> demean = /* [count] */(final Mat A, final Mat mean) -> {
            Mat A_centered = Mat.zeros(count, 2, CvType.CV_64F);
            for (int i = 0; i < count; i++) { // i = columns == points
                Mat foo = new Mat();

                Core.subtract(A.row(i), mean, foo);

                for (int j = 0; j < 2; j++) {
                    // A_centered.row(i) = A.row(i) - mean;

                    double d = foo.get(0, j)[0];
                    A_centered.put(i, j, d);
                }
            }
            return A_centered;
        };

        Mat from_mean = colwise_mean.apply(from); // 3x1 vector of col means

        Mat to_mean = colwise_mean.apply(to); // 3x1 vector of col means

        Mat from_centered = demean.apply(from, from_mean);
        Mat to_centered = demean.apply(to, to_mean);

        debug(0, "from_centered", from_centered);
        debug(0, "to_centered", to_centered);

        // Mat cov = to_centered.t() * from_centered * one_over_n;
        Mat cov = new Mat();
        Core.gemm(to_centered.t(), from_centered, one_over_n, new Mat(), 0.0, cov);
        debug(0, "cov", cov);

        Mat u = new Mat();
        Mat d = new Mat();
        Mat vt = new Mat();
        Core.SVDecomp(cov, d, u, vt, Core.SVD_MODIFY_A | Core.SVD_FULL_UV);
        debug(0, "u", u);
        debug(0, "d", d);
        debug(0, "vt", vt);

        // if (Core.countNonZero(d) < 2)
        // throw new IllegalArgumentException("Points cannot be colinear");

        Mat S = Mat.eye(2, 2, CvType.CV_64F);
        if (Core.determinant(u) * Core.determinant(vt) < 0) {
            // S.at<double>(2, 2) = -1;
            S.put(1, 1, -1);
        }

        Mat rmat = new Mat();
        Core.gemm(S, vt, 1.0, new Mat(), 0.0, rmat);
        Core.gemm(u, rmat, 1.0, new Mat(), 0.0, rmat);
        double scale = 1.0;

        if (useIMU) {
            rmat = Mat.zeros(2, 2, CvType.CV_64F);
            double c = Math.cos(heading);
            double s = Math.sin(heading);
            rmat.put(0, 0,
                    c, -s,
                    s, c);
        }

        // Mat new_to = scale * rmat * from_mean.t();
        Mat new_to = new Mat(); // 3x1 vector
        Core.gemm(rmat, from_mean.t(), scale, new Mat(), 0.0, new_to);

        // Mat transform = Mat.zeros(2, 3, CvType.CV_64F);
        // Mat r_part = transform.submat(0, 2, 0, 2);
        // rmat.copyTo(r_part);
        // transform.col(3) = to_mean.t() - new_to;
        // double euler = VisionUtil.rotm2euler2d(rmat);
        // debug(0, "euler", euler);

        // Mat t_part = transform.col(2);
        Mat tmat = new Mat();
        Core.subtract(to_mean.t(), new_to, tmat);
        // tmat.copyTo(t_part);
        // debug(0, "transform", transform);

        /////////////////////////
        /////////////////////////

        // so now Ax=b where X is the world geometry and b is as prepared.
        // Mat AA = VisionUtil.solve(XMat, bMat);

        // Mat rmat = AA.submat(0, 2, 0, 2);
        // debug(1, "rmat", rmat);

        // double euler = VisionUtil.rotm2euler2d(rmat);
        // debug(1, "euler", euler);

        // "repairing" the rotation is an essential step, which seems crazy.
        // rmat.put(0, 0,
        // Math.cos(euler), -Math.sin(euler),
        // Math.sin(euler), Math.cos(euler));
        // debug(1, "rmat repaired", rmat);
        //
        // Mat cameraTVec = Mat.zeros(2, 1, CvType.CV_64F);
        // cameraTVec.put(0, 0, AA.get(0, 2)[0], AA.get(1, 2)[0]);
        // debug(1, "cameraTVec", cameraTVec);

        Mat transform = Mat.zeros(3, 4, CvType.CV_64F);
        transform.put(0, 0,
                rmat.get(0, 0)[0], 0, rmat.get(0, 1)[0], tmat.get(0, 0)[0],
                0, 1, 0, 0,
                rmat.get(1, 0)[0], 0, rmat.get(1, 1)[0], tmat.get(1, 0)[0]);

        return transform;
    }

    @Override
    public double[] getF() {
        return new double[] { f, f };
    }

    @Override
    public double[] getTilt() {
        return new double[] { 0, 0 };
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
