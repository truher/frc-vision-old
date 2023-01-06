package vision;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDouble;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.MatOfPoint3f;
import org.opencv.core.Size;

public class Binocular2dSVDPoseEstimator extends BasePoseEstimator {
    final static Log log = new Log(3, Binocular2dSVDPoseEstimator.class.getName());
    final double f = 914;
    final int height = 800;
    final int width = 1280;
    final int cx = width / 2;
    final Size dsize = new Size(width, height);
    final double b = 0.8;
    final boolean useIMU;

    public Binocular2dSVDPoseEstimator(boolean useIMU) {
        this.useIMU = useIMU;
    }

    @Override
    public String getName() {
        return String.format("Binocular2dSVDPoseEstimator%s", useIMU ? "IMU" : "");
    }

    @Override
    public String getDescription() {
        return "SVD solve 2d only";
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
        log.debug(2, "leftPts", leftPts);
        log.debug(2, "rightPts", rightPts);

        // To solve Ax=b triangulation, first make b:
        Mat bMat = VisionUtil.makeBMat2d(leftPts, rightPts, b, f, cx);
        log.debug(2, "bMat (b data)", bMat);

        // ... and x: (X, Z, 1):
        Mat XMat = VisionUtil.makeXMat2d(targetPoints);
        log.debug(2, "XMat (x data)", XMat);

        // so now Ax=b where X is the world geometry and b is as prepared.
        Mat AA = VisionUtil.solve(XMat, bMat);

        Mat rmat = AA.submat(0, 2, 0, 2);
        log.debug(1, "rmat", rmat);
        double euler = VisionUtil.rotm2euler2d(rmat);
        log.debug(1, "euler", euler);

        // "repairing" the rotation is an essential step, which seems crazy.
        rmat.put(0, 0,
                Math.cos(euler), -Math.sin(euler),
                Math.sin(euler), Math.cos(euler));
        log.debug(1, "rmat repaired", rmat);

        if (useIMU) {
            rmat = Mat.zeros(2, 2, CvType.CV_64F);
            double c = Math.cos(heading);
            double s = Math.sin(heading);
            rmat.put(0, 0,
                    c, -s,
                    s, c);
        }

        Mat cameraTVec = Mat.zeros(2, 1, CvType.CV_64F);
        cameraTVec.put(0, 0, AA.get(0, 2)[0], AA.get(1, 2)[0]);
        log.debug(1, "cameraTVec", cameraTVec);

        Mat transform = Mat.zeros(3, 4, CvType.CV_64F);
        transform.put(0, 0,
                rmat.get(0, 0)[0], 0, rmat.get(0, 1)[0], AA.get(0, 2)[0],
                0, 1, 0, 0,
                rmat.get(1, 0)[0], 0, rmat.get(1, 1)[0], AA.get(1, 2)[0]);

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
}
