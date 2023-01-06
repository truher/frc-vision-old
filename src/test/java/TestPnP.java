import org.opencv.calib3d.Calib3d;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDouble;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.MatOfPoint3f;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import vision.VisionUtil;

/**
 * This uses {@link Calib3d#solvePnP()} to estimate poses using a single camera.
 * 
 * TODO: the remaining stuff to pull out of here is:
 * 
 * * un-tilting
 * * finding contours
 */
public class TestPnP {
    public static final double DELTA = 0.00001;
    public static final boolean DEBUG = false;

    public TestPnP() {
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
    }

    /**
     * Try many world locations; generate an image, extract pose from it.
     */
    // @Test
    public void testSolvePnPGrid() {
        Size dsize = new Size(1920, 1080);
        double f = 600.0;
        Mat kMat = VisionUtil.makeIntrinsicMatrix(f, dsize);

        MatOfDouble dMat = new MatOfDouble(Mat.zeros(5, 1, CvType.CV_64F));
        dMat.put(0, 0, -0.1, 0, 0, 0, 0);
        //
        //
        //
        // target is 0.4m wide, 0.1m high .
        double height = 0.1;
        double width = 0.4;
        MatOfPoint3f targetGeometryMeters = VisionUtil.makeTargetGeometry3f(width, height);

        final double dyWorld = 1.0; // say the camera is 1m below (+y) relative to the target
        final double tilt = 0.45; // camera tilts up
        final double pan = 0.0; // for now, straight ahead
        int idx = 0;
        System.out.println(
                "idx, dxWorld, dyWorld, dzWorld, dxCam, "
                        + "dyCam, dzCam, pan, tilt, 0.0, "
                        + "pdxworld, pdyworld, pdzworld, ppanCam, ptiltCam, "
                        + "pscrewCam, pdxCam, pdyCam, pdzCam, "
                        + "ppanWorld, ptiltWorld, pscrewWorld");
        // FRC field is 8x16m, let's try for half-length and full-width i.e. 8x8
        for (double dzWorld = -10; dzWorld <= -1; dzWorld += 1.0) { // meters, start far, move closer
            for (double dxWorld = -5; dxWorld <= 5; dxWorld += 1.0) { // meters, start left, move right

                idx += 1;

                //
                //
                //
                Mat cameraView = VisionUtil.makeImage(dxWorld, dyWorld, dzWorld, tilt, pan, kMat, dMat,
                        targetGeometryMeters, dsize);
                if (cameraView == null) {
                    debugmsg("no camera view");
                    continue;
                }
                Imgcodecs.imwrite(String.format("C:\\Users\\joelt\\Desktop\\pics\\target-%d-distorted.png", idx),
                        cameraView);

                //
                // manually undistort the camera view.
                Mat undistortedCameraView = new Mat();
                Calib3d.undistort(cameraView, undistortedCameraView, kMat, dMat);
                Imgcodecs.imwrite(String.format("C:\\Users\\joelt\\Desktop\\pics\\target-%d-undistorted.png", idx),
                        undistortedCameraView);

                Size tallSize = new Size(1920, 2160);
                Mat tallKMat = VisionUtil.makeIntrinsicMatrix(f, tallSize);
                Mat untiltedCameraView = VisionUtil.removeTilt(undistortedCameraView, tilt, f, kMat, tallSize);
                Imgcodecs.imwrite(String.format("C:\\Users\\joelt\\Desktop\\pics\\target-%d-raw.png", idx),
                        untiltedCameraView);

                MatOfPoint2f imagePoints = VisionUtil.findTargetCornersInImage(idx, true,
                 untiltedCameraView, 240);
                if (imagePoints == null) {
                    debugmsg("no image points");
                    continue;
                }

                //
                // find the pose using solvepnp. this sucks because the target is small relative
                // to the distances.
                //
                Mat newCamRVec = new Mat();
                Mat newCamTVec = new Mat();
                Calib3d.solvePnP(targetGeometryMeters, imagePoints, tallKMat,
                        new MatOfDouble(), newCamRVec, newCamTVec, false,
                        Calib3d.SOLVEPNP_ITERATIVE);

                // now derive world coords
                // for distant orthogonal targets the R is quite uncertain.
                Mat newCamRMat = new Mat();
                Calib3d.Rodrigues(newCamRVec, newCamRMat);
                Mat newWorldRMat = newCamRMat.t();

                //
                // draw the target points on the camera view to see where we think they are
                //

                MatOfPoint2f skewedImagePts2f = VisionUtil.makeSkewedImagePts2f(targetGeometryMeters,
                        newCamRVec,
                        newCamTVec, tallKMat, newWorldRMat);
                // points projected from pnp
                for (Point pt : skewedImagePts2f.toList()) {
                    Imgproc.circle(untiltedCameraView,
                            new Point(pt.x, pt.y),
                            2,
                            new Scalar(0, 0, 255),
                            Imgproc.FILLED);
                }
                // points found in the image
                for (Point pt : imagePoints.toList()) {
                    Imgproc.circle(untiltedCameraView,
                            new Point(pt.x, pt.y),
                            6,
                            new Scalar(0, 255, 0),
                            1);
                }

                // report on the predictions
                // first derive cam coords, because cam coords errors are easier to understand

                double pdxCam = newCamTVec.get(0, 0)[0];
                double pdyCam = newCamTVec.get(1, 0)[0];
                double pdzCam = newCamTVec.get(2, 0)[0];

                // are world rotations interesting?
                Mat eulerWorld = VisionUtil.rotm2euler(newCamRMat);
                double ptiltWorld = eulerWorld.get(0, 0)[0];
                double ppanWorld = eulerWorld.get(1, 0)[0];
                double pscrewWorld = eulerWorld.get(2, 0)[0];

                // what are the actual camera translations
                // note this ignores camera tilt, because we magically detilt above
                Mat dWorld = Mat.zeros(3, 1, CvType.CV_64F);
                dWorld.put(0, 0, dxWorld, dyWorld, dzWorld);

                Mat rCam = Mat.zeros(3, 1, CvType.CV_64F);
                rCam.put(0, 0, 0.0, pan, 0.0);
                Mat rCamM = new Mat();
                Calib3d.Rodrigues(rCam, rCamM);
                Mat dCam = new Mat();
                Core.gemm(rCamM, dWorld, -1.0, new Mat(), 0.0, dCam);

                double dxCam = dCam.get(0, 0)[0];
                double dyCam = dCam.get(1, 0)[0];
                double dzCam = dCam.get(2, 0)[0];

                Mat newWorldTVec = new Mat();
                Core.gemm(newWorldRMat, newCamTVec, -1.0, new Mat(), 0.0, newWorldTVec);
                // predictions in world coords
                // NOTE: error in R becomes error in X and Y.pnp
                double pdxworld = newWorldTVec.get(0, 0)[0];
                double pdyworld = newWorldTVec.get(1, 0)[0];
                double pdzworld = newWorldTVec.get(2, 0)[0];
                // the interesting aspect of rotation is the *camera* rotation
                Mat eulerCam = VisionUtil.rotm2euler(newCamRMat);
                double ptiltCam = eulerCam.get(0, 0)[0];
                double ppanCam = eulerCam.get(1, 0)[0];
                double pscrewCam = eulerCam.get(2, 0)[0];

                Imgcodecs.imwrite(String.format("C:\\Users\\joelt\\Desktop\\pics\\target-%d-annotated.png", idx),
                        untiltedCameraView);
                System.out.printf(
                        "%d, %f, %f, %f, %f, "
                                + " %f, %f, %f, %f, %f, "
                                + " %f, %f, %f, %f, %f, "
                                + " %f, %f, %f, %f, %f, "
                                + " %f, %f\n",
                        idx, dxWorld, dyWorld, dzWorld, dxCam,
                        dyCam, dzCam, pan, tilt, 0.0,
                        pdxworld, pdyworld, pdzworld, ppanCam, ptiltCam,
                        pscrewCam, pdxCam, pdyCam, pdzCam,
                        ppanWorld, ptiltWorld, pscrewWorld);
            }
        }
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