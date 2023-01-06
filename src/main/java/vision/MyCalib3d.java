package vision;

import java.util.function.BinaryOperator;
import java.util.function.Function;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;

/**
 * Include estimateAffine3d from OpenCV 4.6.
 */
public class MyCalib3d {

    /**
     * Computes an optimal affine transformation between two 3D point sets.
     * It computes \f$R,s,t\f$ minimizing \f$\sum{i} dst_i - c \cdot R \cdot
     * src_i \f$ where \f$R\f$ is a 3x3 rotation matrix, \f$t\f$ is a 3x1
     * translation vector and \f$s\f$ is a scalar size value. This is an
     * implementation of the algorithm by Umeyama \cite umeyama1991least .
     * The estimated affine transform has a homogeneous scale which is a
     * subclass of affine transformations with 7 degrees of freedom. The paired
     * point sets need comprise at least 3 points each.
     * 
     * @param src            First input 3D point set.
     * @param dst            Second input 3D point set.
     * @param scale          If null is passed, the scale parameter c will be
     *                       assumed to be 1.0. Else the pointed-to variable will be
     *                       set to the optimal scale. [NOTE the value is obviously
     *                       not returned in java]
     * @param force_rotation If true, the returned rotation will never be a
     *                       reflection. This might be unwanted, e.g. when
     *                       optimizing a transform between a right- and a
     *                       left-handed coordinate system.
     * @return 3D affine transformation matrix \f$3 \times 4\f$ of the form
     *         \f[T =
     *         \begin{bmatrix}
     *         R & t\\
     *         \end{bmatrix}
     *         \f]
     */

    public static Mat estimateAffine3D(Mat _from, Mat _to, Double _scale, boolean force_rotation) {
        // CV_INSTRUMENT_REGION();
        // Mat from = _from.getMat(), to = _to.getMat();

        Mat from = _from;
        Mat to = _to;
        final int count = from.checkVector(3); // 3 columns, columns == points

        // CV_CheckGE(count, 3, "Umeyama algorithm needs at least 3 points for affine
        // transformation estimation.");
        if (count < 3)
            throw new IllegalArgumentException(
                    "Umeyama algorithm needs at least 3 points for affine transformation estimation.");
        // CV_CheckEQ(to.checkVector(3), count, "Point sets need to have the same
        // size");
        if (to.checkVector(3) != count)
            throw new IllegalArgumentException("Point sets need to have the same size");

        from = from.reshape(1, count);
        to = to.reshape(1, count);
        if (from.type() != CvType.CV_64F)
            from.convertTo(from, CvType.CV_64F);
        if (to.type() != CvType.CV_64F)
            to.convertTo(to, CvType.CV_64F);

        final double one_over_n = 1. / count;

        // yields a 3x1 vector (row) containing the means of each column
        final Function<Mat, Mat> colwise_mean = /* [one_over_n] */(final Mat m) -> {
            Mat my = new Mat();
            Core.reduce(m, my, 0, Core.REDUCE_SUM, CvType.CV_64F);
            // return my * one_over_n;
            return my.mul(Mat.ones(my.size(), my.type()), one_over_n);
        };

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

        Mat from_mean = colwise_mean.apply(from); // 3x1 vector of col means

        Mat to_mean = colwise_mean.apply(to); // 3x1 vector of col means

        Mat from_centered = demean.apply(from, from_mean);
        Mat to_centered = demean.apply(to, to_mean);

        // Mat cov = to_centered.t() * from_centered * one_over_n;
        Mat cov = new Mat();
        Core.gemm(to_centered.t(), from_centered, one_over_n, new Mat(), 0.0, cov);

        Mat u = new Mat();
        Mat d = new Mat();
        Mat vt = new Mat();
        Core.SVDecomp(cov, d, u, vt, Core.SVD_MODIFY_A | Core.SVD_FULL_UV);

        // CV_CheckGE(countNonZero(d), 2, "Points cannot be colinear");
        if (Core.countNonZero(d) < 2)
            throw new IllegalArgumentException("Points cannot be colinear");

        Mat S = Mat.eye(3, 3, CvType.CV_64F);
        // det(d) can only ever be >=0, so we can always use this here (compared to the
        // original formula by Umeyama)
        if (force_rotation && (Core.determinant(u) * Core.determinant(vt) < 0)) {
            // S.at<double>(2, 2) = -1;
            S.put(2, 2, -1);
        }
        // Mat rmat = u*S*vt;
        Mat rmat = new Mat();
        Core.gemm(S, vt, 1.0, new Mat(), 0.0, rmat);
        Core.gemm(u, rmat, 1.0, new Mat(), 0.0, rmat);

        double scale = 1.0;
        if (_scale != null) {
            double var_from = 0.;
            scale = 0.;
            for (int i = 0; i < 3; i++) {
                var_from += Core.norm(from_centered.col(i), Core.NORM_L2SQR);
                scale += d.get(i, 0)[0] * S.get(i, i)[0];
            }
            double inverse_var = count / var_from;
            scale *= inverse_var;
            // LATER: return the scale somehow

            // *_scale = scale;
        }

        // Mat new_to = scale * rmat * from_mean.t();
        Mat new_to = new Mat(); // 3x1 vector
        Core.gemm(rmat, from_mean.t(), scale, new Mat(), 0.0, new_to);

        Mat transform = Mat.zeros(3, 4, CvType.CV_64F);
        Mat r_part = transform.submat(0, 3, 0, 3);
        rmat.copyTo(r_part);
        // transform.col(3) = to_mean.t() - new_to;
        Mat t_part = transform.col(3);
        Mat tmat = new Mat();
        Core.subtract(to_mean.t(), new_to, tmat);
        tmat.copyTo(t_part);
        return transform;
    }

}
