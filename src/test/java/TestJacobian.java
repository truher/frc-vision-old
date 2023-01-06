import org.opencv.calib3d.Calib3d;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDouble;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.MatOfPoint3f;
import org.opencv.core.Point;
import org.opencv.core.Point3;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import vision.VisionUtil;

/**
 * OpenCV makes available Jacobians for some transforms, which might
 * be useful for error analysis. This class looks at some of them.
 * 
 * Since I wrote this, I focused more on *reducing* error rather than
 * *measuring* error, so it's not that useful.
 */
public class TestJacobian {
    public static final double DELTA = 0.00001;
    public static final boolean DEBUG = false;

    public TestJacobian() {
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME); 
    }

    /**
     * Look at the output Jacobian for the {@link Calib3d#matMulDeriv()}.
     */
   // @Test
    public void testMatMulJacobian() {
        Mat A = Mat.zeros(3, 3, CvType.CV_64F);
        A.put(0, 0,
                0.707, 0.707, 0,
                -0.707, 0.707, 0,
                0, 0, 1);
        Mat B = Mat.zeros(3, 1, CvType.CV_64F);
        B.put(0, 0, 1, -10, 100);
        Mat dABdA = new Mat();
        Mat dABdB = new Mat();

        Calib3d.matMulDeriv(A, B, dABdA, dABdB);
        debug("A", A);
        debug("B", B);
        debug("dABdA", dABdA);
        debug("dABdB", dABdB);

    }

    /**
     * Look at the output Jacobian for the {@link Calib3d#Rodrigues()}.
     */
    //@Test
    public void testRodriguesJacobian() {
        Mat RV = Mat.zeros(3, 1, CvType.CV_64F);
        RV.put(0, 0, 0, 0.0001, 0.0);
        Mat RM = new Mat();
        Mat J = new Mat();
        Calib3d.Rodrigues(RV, RM, J);
        debug("RV", RV);
        debug("RM", RM);
        debug("J", J);
    }

    // project a single pixel into world space to show the shape of
    // the projection error
    public void projectPixel(Mat field, double scale, int fieldZ, Mat actualWorldTV, Mat camRV) {

        // 1/4 of 1080, so it doesn't take up the whole screen and doesn't take forever
        Size dsize = new Size(960, 540);

        // f=512 camera matrix
        Mat kMat = VisionUtil.makeIntrinsicMatrix(256.0, dsize);

        // no distortion for now
        MatOfDouble dMat = new MatOfDouble(Mat.zeros(4, 1, CvType.CV_64F));
        debug("dMat", dMat);

        // just one target point for now
        // keep it away from the origin to prevent singularity
        MatOfPoint3f targetGeometryMeters = new MatOfPoint3f(new Point3(2.0, 2.0, 0.0));
        MatOfPoint2f targetImageGeometry = VisionUtil.makeTargetImageGeometryPixels(targetGeometryMeters, 1000);
        debug("targetImageGeometry", targetImageGeometry);

        Mat camTV = VisionUtil.world2Cam(camRV, actualWorldTV);

        MatOfPoint2f skewedImagePts2f = new MatOfPoint2f();
        Mat jacobian = new Mat();
        Calib3d.projectPoints(targetGeometryMeters, camRV, camTV, kMat, dMat, skewedImagePts2f, jacobian);

        debug("jacobian", jacobian);

        // from calibration.cpp.
        // these are many 2x3 stacked vertically if there are multiple target points
        // but right now they are just 2x3.
        Mat dpdrot = jacobian.colRange(0, 3);
        Mat dpdt = jacobian.colRange(3, 6);

        // Mat dpdf = jacobian.colRange(6, 8);
        // Mat dpdc = jacobian.colRange(8, 10);
        // Mat dpddist = jacobian.colRange(10, jacobian.cols());

        // double dp = 3.0;
        for (int i = 0; i < targetGeometryMeters.rows(); ++i) {
            // dpixel/drot
            Mat pointdpdrot = dpdrot.rowRange(i * 2, i * 2 + 2);
            debug("dpdrot", pointdpdrot);

            // dpixel/dtranslation
            Mat pointDpdt = dpdt.rowRange(i * 2, i * 2 + 2);
            debug("dpdt", pointDpdt);

            // find the inverse, so drotation/dpixel
            // if condition number is too low, the matrix is "nearly singular"
            Mat drotdp = new Mat(); // 3x2
            double rotInvConditionNumber = Core.invert(pointdpdrot, drotdp, Core.DECOMP_SVD);
            debug("rotation inverse condition number", rotInvConditionNumber);
            debug("drotdp  ", drotdp);

            // find the inverse, so dtranslation/dpixel
            Mat dtdp = new Mat(); // 3x2
            double tInvConditionNumber = Core.invert(pointDpdt, dtdp, Core.DECOMP_SVD);
            debug("translation inverse condition number", tInvConditionNumber);
            debug("dtdp", dtdp);

            // make the center of the world translation estimate; in reality these would
            // come from solvePNP.
            // first the world rotation
            Mat camRM = new Mat();
            // this is the jacobian of the rodrigues transform; it yields 3x9 partials.
            Mat rodJ = new Mat();
            Calib3d.Rodrigues(camRV, camRM, rodJ);
            debug("rodJ", rodJ);
            Mat worldRM = camRM.t();
            // jacobian of the transpose is the transpose of the jacobian
            // which will be useful below
            Mat rodJT = rodJ.t(); // 9x3
            debug("rodJT", rodJT);

            // then the world translation
            Mat worldTV = new Mat();
            Core.gemm(worldRM, camTV, -1.0, new Mat(), 0.0, worldTV);

            debug("world translation", worldTV);

            // now the error in the translation estimate.
            //
            // the function is multiply(transposedrodrigues(rotation),-translation)
            // so the chain rule says we need
            // dmultiply/dtransposedrodrigues * dtransposedrodrigues/drot
            // * drot/dp - dmultiply/dtranslation * dtrans/dp
            // note the -1

            // first the multiplication derivatives
            Mat dmultdrot = new Mat(); // 3x9
            Mat dmultdt = new Mat(); // 3x3
            Calib3d.matMulDeriv(worldRM, camTV, dmultdrot, dmultdt);
            debug("dmultdrot", dmultdrot);
            debug("dmultdt", dmultdt);

            // the rodrigues jacobian is above

            // rotation term:
            Mat drotTdp = new Mat(); // 9x2
            Core.gemm(rodJT, drotdp, 1.0, new Mat(), 0.0, drotTdp);
            Mat dworldtdpR = new Mat();
            Core.gemm(dmultdrot, drotTdp, 1.0, new Mat(), 0.0, dworldtdpR);
            // translation term:
            Mat dworldtdpT = new Mat();
            Core.gemm(dmultdt, dtdp, -1.0, new Mat(), 0.0, dworldtdpT);
            Mat dworldtdp = new Mat();
            Core.add(dworldtdpR, dworldtdpT, dworldtdp);
            // resulting jacobian, (u,v) -> (xw,yw,zw):
            debug("dworldtdp", dworldtdp);

            Scalar color = new Scalar(255, 255, 255);
            Size axes = new Size(1 + dworldtdp.get(0, 0)[0] * scale, 1 + dworldtdp.get(2, 0)[0] * scale);
            debugmsg(axes.toString());
            Point center = new Point(fieldZ / 2 + worldTV.get(0, 0)[0] * scale, -1 * worldTV.get(2, 0)[0] * scale);
            debugmsg(center.toString());
            debugmsg(field.size().toString());

            Point pt1 = new Point(center.x - axes.width, center.y - axes.height);
            Point pt2 = new Point(center.x + axes.width, center.y + axes.height);
            debugmsg(pt1.toString());
            debugmsg(pt2.toString());
            Imgproc.rectangle(field, pt1, pt2, color);

        }
    }

    /**
     * try one projection with all the jacobians hooked up.
     */
   // @Test
    public void testJacobian() {

        double scale = 90; // pixels per meter in the xy projection
        int fieldX = (int) (12 * scale);
        int fieldZ = (int) (12 * scale);
        Mat field = Mat.zeros(fieldX, fieldZ, CvType.CV_64FC3);

        // just z translation, no rotation
        // double xPos = 0.0;
        double yPos = 0.0;
        // double zPos = -10.0;
        double tilt = 0.0;
        double pan = -0.1;

        for (double zPos = -10; zPos <= -1; zPos += 0.5) {
            for (double xPos = -4; xPos <= 4; xPos += 0.5) {

                Mat actualWorldTV = Mat.zeros(3, 1, CvType.CV_64F);
                actualWorldTV.put(0, 0, xPos, yPos, zPos);
                // camera up/right means world down/left, so both negative
                Mat camRV = VisionUtil.panTilt(-pan, -tilt);
                projectPixel(field, scale, fieldZ, actualWorldTV, camRV);

            }
        }

        Imgcodecs.imwrite("C:\\Users\\joelt\\Desktop\\pics\\field.jpg", field);

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
