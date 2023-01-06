package vision;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDouble;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.MatOfPoint3f;
import org.opencv.core.Rect;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;

/**
 * Evaluate a bunch of pose estimators, do parameter studies, etc.
 */
public class PoseEstimatorHarness {
    final static Log log = new Log(3, PoseEstimatorHarness.class.getName());

    public final List<PoseEstimator> poseEstimators;

    // true = show accuracy for each point in 10x10m grid
    final boolean showGrid = true;

    // true = show stderr for each estimator over all samples
    final boolean showSummary = false;

    // true = add noise to points; false = leave points alone
    // if estimating from points this should be true
    // but if using images this should be false.
    final boolean perturbPoints = false;

    // the physical gyro involves thermal noise so this should be true
    final boolean perturbGyro = true;

    // true = use image; false = use ideal points
    final boolean poseFromImage = true;

    // add (a lot of) gaussian and shot noise.
    final boolean addImageNoise = true;

    // write various image files for debugging.
    final boolean writeFiles = true;

    public PoseEstimatorHarness() {
        // these are ranked worst to best

        poseEstimators = new ArrayList<PoseEstimator>();

        // baseline, constant output
      //  poseEstimators.add(new ConstantPoseEstimator());

        // pretty good close up, not good in x far away
     //   poseEstimators.add(new MonocularPoseEstimator(false));

        // ok close up
      //  poseEstimators.add(new Binocular2dSVDPoseEstimator(false));

        // works pretty well within a few meters.
     //   poseEstimators.add(new Binocular2dUmeyamaPoseEstimator(false));

        // ok within a few meters, much worse than IMU options
     //   poseEstimators.add(new BinocularConstrainedPoseEstimator(false));

        // good, 4x the error, 2X the speed vs binocular ones
    //    poseEstimators.add(new MonocularPoseEstimator(true));

        // pretty awesome
     //   poseEstimators.add(new Binocular2dSVDPoseEstimator(true));

        // awesome
     //   poseEstimators.add(new Binocular2dUmeyamaPoseEstimator(true));

        // awesome
        poseEstimators.add(new BinocularConstrainedPoseEstimator(true));
    }

    public void run() {
        if (showSummary) {
            System.out.println("stderr for each estimator...");
            System.out.printf("%40s %10s %10s %10s %10s %10s %10s %10s %10s\n",
                    "name", "heading", "X", "Z", "position", "bearing", "range", "rate", "failures");
        }
        for (PoseEstimator e : poseEstimators) {
            Random rand = new Random(42);
            final String name = e.getName();
            // final String description = e.getDescription();
            final Mat[] kMat = e.getIntrinsicMatrices();
            final MatOfDouble[] dMat = e.getDistortionMatrices();
            final double[] b = e.getXOffsets();
            final Size[] sizes = e.getSizes();
            {
                Objects.requireNonNull(kMat);
                if (kMat.length < 1)
                    throw new IllegalArgumentException();

                Objects.requireNonNull(dMat);
                if (dMat.length < 1)
                    throw new IllegalArgumentException();
                if (dMat.length != kMat.length)
                    throw new IllegalArgumentException();

                Objects.requireNonNull(b);
                if (b.length < 1)
                    throw new IllegalArgumentException();
                if (b.length != dMat.length)
                    throw new IllegalArgumentException();

            }

            MatOfPoint3f targetGeometryMeters = VisionUtil.makeTargetGeometry3f(0.5, 0.5);

            // pigeon/navx claim 1.5 degrees for fused output
            // final double gyroNoise = 0.025;
            // LIS3MDL claims about 1% thermal noise at 80hz
            // average 8 samples = 1/sqrt(8)
            double gyroNoise = 0.0035; //
            // 50fps video => 10hz output = 5x averaging
            int pointMultiplier = 1;
            double noisePixels = 2;
            MatOfPoint3f targetPointsMultiplied = VisionUtil.duplicatePoints(targetGeometryMeters, pointMultiplier);

            double targetBrightnessMean = 230;
            double targetBrightnessStdev = 15;
            int idx = 0;
            double yPos = 0;
            // double tilt = 0;

            double panErrSquareSum = 0.0;
            double xErrSquareSum = 0.0;
            double zErrSquareSum = 0.0;
            double positionErrSquareSum = 0.0;
            double relativeBearingErrSquareSum = 0.0;
            double rangeErrSquareSum = 0.0;
            int failures = 0;
            long workTime = 0;

            if (showGrid)
                System.out.println(
                        "               name, idx,  pan,  xpos,  ypos,  zpos, rbear, range,  ppan, pxpos, pypos, pzpos, prbear, prange, panErr, xErr, zErr, posErr, relativeBearingErr, rangeErr");
            for (double pan = -3 * Math.PI / 8; pan <= 3 * Math.PI / 8; pan += Math.PI / 8) {
                for (double zPos = -10.0; zPos <= -1.0; zPos += 1.0) {
                    pose: for (double xPos = -5; xPos <= 5; xPos += 1.0) {
                        // for (double pan = 0; pan <= 0; pan += Math.PI / 8) {
                        // for (double zPos = -5.0; zPos <= -5.0; zPos += 1.0) {
                        // pose: for (double xPos = 0; xPos <= 0; xPos += 1.0) {
                        double navBearing = Math.atan2(xPos, -zPos);
                        double relativeBearing = navBearing + pan;
                        double range = Math.sqrt(xPos * xPos + zPos * zPos);

                        // don't bother with oblique angles, the projection is wrong for these cases.
                        if (Math.abs(relativeBearing) > Math.PI / 2) {
                            log.debugmsg(2, "oblique");
                            continue;
                        }

                        // these are the calculated points
                        MatOfPoint2f[] idealImagePoints = new MatOfPoint2f[kMat.length];
                        Mat[] images = new Mat[kMat.length];
                        for (int cameraIdx = 0; cameraIdx < kMat.length; ++cameraIdx) {
                            // make transform from world origin to camera center
                            Mat worldToCameraCenterHomogeneous = VisionUtil.makeWorldToCameraHomogeneous(pan, xPos,
                                    yPos,
                                    zPos);
                            Mat worldToEye = VisionUtil.translateX(worldToCameraCenterHomogeneous, b[cameraIdx]);

                            // make the points the camera sees
                            MatOfPoint2f pts = VisionUtil.imagePoints(kMat[cameraIdx], dMat[cameraIdx],
                                    targetGeometryMeters, worldToEye);

                            if (perturbPoints)
                                pts = VisionUtil.perturbPoints(pts, pointMultiplier, noisePixels, rand);

                            Size size = sizes[cameraIdx];
                            final Rect viewport = new Rect(0, 0, (int) size.width, (int) size.height);
                            if (!VisionUtil.inViewport(pts, viewport)) {
                                log.debugmsg(2, "not in view");
                                continue pose;
                            }
                            if (writeFiles)
                                VisionUtil.writePng(pts, (int) size.width, (int) size.height,
                                        String.format("C:\\Users\\joelt\\Desktop\\pics\\img-%s-%d-%d.png", name, idx,
                                                cameraIdx));
                            idealImagePoints[cameraIdx] = pts;

                            // also make an image
                            // Mat cameraView = VisionUtil.makeImage(xPos, yPos, zPos, tilt, pan,
                            // kMat[cameraIdx],
                            // dMat[cameraIdx], targetGeometryMeters, size);

                            int targetBrightness = (int) Math.min(255, targetBrightnessMean
                                    + rand.nextGaussian() * targetBrightnessStdev);
                            Mat cameraView = VisionUtil.renderImage(targetBrightness, size, targetGeometryMeters, pts);

                            if (cameraView == null) {
                                log.debugmsg(2, "no image");
                                continue pose;
                            }

                            if (addImageNoise) {
                                VisionUtil.addSaltAndPepper(cameraView);
                                VisionUtil.addGaussianNoise(cameraView);
                            }
                            if (writeFiles)
                                Imgcodecs.imwrite(
                                        String.format("C:\\Users\\joelt\\Desktop\\pics\\target-%s-%d-%d-distorted.png",
                                                name, idx, cameraIdx),
                                        cameraView);
                            images[cameraIdx] = cameraView;

                        }

                        // if the target isn't in the viewport, skip

                        

                        double gyro = pan;

                        if (perturbGyro)
                            gyro += (gyroNoise * rand.nextGaussian());

                        long startTime = System.currentTimeMillis();
                        Mat transform;
                        if (poseFromImage)
                            transform = e.getPose(idx, writeFiles, gyro, targetGeometryMeters, images);
                        else
                            transform = e.getPose(gyro, targetPointsMultiplied, idealImagePoints);

                        workTime += (System.currentTimeMillis() - startTime);
                        for (int cameraIdx = 0; cameraIdx < kMat.length; ++cameraIdx) {
                            images[cameraIdx].release();
                        }
                        System.gc();
                        if (transform == null) {
                            failures++;
                            log.debugmsg(2, "no transform");
                            continue pose;
                        }
                        ++idx;
                        log.debug(2, "transform", transform);
                        Mat rmat = transform.submat(0, 3, 0, 3);

                        double euler = Math.atan2(rmat.get(2, 0)[0], rmat.get(0, 0)[0]);
                        log.debug(1, "euler", euler);
                        Mat cameraTVec = Mat.zeros(3, 1, CvType.CV_64F);
                        cameraTVec.put(0, 0,
                                transform.get(0, 3)[0],
                                transform.get(1, 3)[0],
                                transform.get(2, 3)[0]);
                        log.debug(1, "cameraTVec", cameraTVec);
                        Mat pworldTVec = new Mat();
                        log.debug(1, "rmat", rmat);
                        Core.gemm(rmat.t(), cameraTVec, -1.0, new Mat(), 0.0, pworldTVec);
                        log.debug(1, "pWorldTVec", pworldTVec);

                        double pxPos = pworldTVec.get(0, 0)[0];
                        double pyPos = pworldTVec.get(1, 0)[0];
                        double pzPos = pworldTVec.get(2, 0)[0];
                        double ppan = euler;

                        double pNavBearing = Math.atan2(pxPos, -pzPos);
                        double pRelativeBearing = pNavBearing + ppan;
                        double pRange = Math.sqrt(pxPos * pxPos + pzPos * pzPos);

                        double panErr = pan - ppan;
                        double xErr = xPos - pxPos;
                        double zErr = zPos - pzPos;
                        double relativeBearingErr = relativeBearing - pRelativeBearing;
                        double rangeErr = range - pRange;
                        double posErr = Math.sqrt(xErr * xErr + zErr * zErr);

                        panErrSquareSum += panErr * panErr;
                        xErrSquareSum += xErr * xErr;
                        zErrSquareSum += zErr * zErr;
                        positionErrSquareSum += posErr * posErr;
                        relativeBearingErrSquareSum += relativeBearingErr * relativeBearingErr;
                        rangeErrSquareSum += rangeErr * rangeErr;

                        if (showGrid)
                            System.out.printf(
                                    "%40s, %3d, %6.2f, %6.2f, %6.2f, %6.2f, %6.2f, %6.2f, %6.2f, %6.2f, %6.2f, %6.2f, %6.2f, %6.2f, %6.2f, %6.2f, %6.2f, %6.2f, %7.4f, %6.2f\n",
                                    name, idx, pan, xPos, yPos, zPos, relativeBearing, range, ppan, pxPos, pyPos, pzPos,
                                    pRelativeBearing, pRange,
                                    panErr, xErr, zErr, posErr, relativeBearingErr, rangeErr);

                    }
                }
            }

            double panRMSE = Math.sqrt(panErrSquareSum / idx);
            double xRMSE = Math.sqrt(xErrSquareSum / idx);
            double zRMSE = Math.sqrt(zErrSquareSum / idx);
            double posRMSE = Math.sqrt(positionErrSquareSum / idx);
            double relativeBearingRMSE = Math.sqrt(relativeBearingErrSquareSum / idx);
            double rangeRMSE = Math.sqrt(rangeErrSquareSum / idx);

            if (showSummary) {
                if (showGrid)
                    System.out.println("===========================");
                System.out.printf("%40s %10.4f %10.4f %10.4f %10.4f %10.4f %10.4f %10.4f %10d\n",
                        name, panRMSE, xRMSE, zRMSE, posRMSE, relativeBearingRMSE, rangeRMSE, 1000.0 * idx / workTime, failures);
                if (showGrid)
                    System.out.println("===========================");
            }
        }

    }
}
