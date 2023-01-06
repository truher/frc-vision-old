package vision;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

import org.opencv.calib3d.Calib3d;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDouble;
import org.opencv.core.MatOfInt;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.MatOfPoint3f;
import org.opencv.core.Point;
import org.opencv.core.Point3;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

public abstract class VisionUtil {
    static {
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
    }
    final static Log log = new Log(4, VisionUtil.class.getName());

    public static double averageAngularError(Mat a, Mat b) {
        // a and b are centered around the origin and the same size
        if (a.rows() != b.rows())
            throw new IllegalArgumentException();
        if (a.cols() != b.cols())
            throw new IllegalArgumentException();
        log.debug(1, "a", a);
        log.debug(1, "b", b);
        for (int i = 0; i < a.rows(); ++i) {
            Mat avec = a.row(i);
            Mat bvec = b.row(i);
            log.debug(1, "avec", avec);
            log.debug(1, "bvec", bvec);
        }
        Mat ax = a.col(0);
        Mat az = a.col(2);
        log.debug(1, "ax", ax);
        log.debug(1, "az", az);

        Mat amag = new Mat();
        Mat aang = new Mat();
        Core.cartToPolar(ax, az, amag, aang);
        log.debug(1, "a magnitude", amag);
        log.debug(1, "a angle", aang);

        Mat bx = b.col(0);
        Mat bz = b.col(2);
        log.debug(1, "bx", bx);
        log.debug(1, "bz", bz);

        Mat bmag = new Mat();
        Mat bang = new Mat();
        Core.cartToPolar(bx, bz, bmag, bang);
        log.debug(1, "b magnitude", bmag);
        log.debug(1, "b angle", bang);

        Mat diff = new Mat();
        Core.subtract(aang, bang, diff);
        log.debug(1, "diff", diff);
        for (int i = 0; i < diff.rows(); ++i) {
            double val = diff.get(i, 0)[0];
            if (val > Math.PI) {
                val -= 2 * Math.PI;
            } else if (val < -Math.PI) {
                val += 2 * Math.PI;
            }
            diff.put(i, 0, val);
        }
        log.debug(1, "diff", diff);
        return Core.mean(diff).val[0];
    }

    public static Mat makeBMat3d(MatOfPoint2f leftPts, MatOfPoint2f rightPts, double f, double cx, double cy,
            double b) {
        // To solve Ax=b triangulation (Ax=M-1T-1u), first make u: (u,u',v,1):
        Mat uMat = makeUMat3d(leftPts, rightPts);

        // and the inverse transforms we're going to apply:
        Mat Minv = makeMInv3d(f, cx, cy);
        Mat Tinv = makeTInv3d(b);

        // apply the inverses to the observations (the "u") in the correct order:
        Mat MinvU = new Mat();
        Core.gemm(Minv, uMat, 1.0, new Mat(), 0.0, MinvU);
        Mat bMat = new Mat();
        Core.gemm(Tinv, MinvU, 1.0, new Mat(), 0.0, bMat);
        normalize3d(bMat);
        log.debug(0, "bMat normalized", bMat);
        return bMat;
    }

    /**
     * this just returns a homogeneous version of the argument, note points are in
     * rows in both input and output.
     */
    public static Mat makeXMat3d(MatOfPoint3f targetPointsMultiplied) {
        Mat dst = new Mat();
        Calib3d.convertPointsToHomogeneous(targetPointsMultiplied, dst);
        dst = dst.reshape(1);
        dst.convertTo(dst, CvType.CV_64F);
        return dst;
    }

    /**
     * for horizontal binocular, v is the same in both eyes, so use (u, u',
     * (v+v')/2, 1).
     * Note inputs (leftPts) have points in rows; this method returns points in
     * columns.
     */
    static Mat makeUMat3d(MatOfPoint2f leftPts, MatOfPoint2f rightPts) {
        log.debug(1, "left", leftPts);
        log.debug(1, "right", rightPts);
        // System.out.println(leftPts.size());
        Mat uMat = Mat.zeros(4, leftPts.toList().size(), CvType.CV_64F);
        for (int i = 0; i < leftPts.toList().size(); ++i) {
            double u = leftPts.get(i, 0)[0];
            double u1 = rightPts.get(i, 0)[0];
            double v = leftPts.get(i, 0)[1];
            double v1 = rightPts.get(i, 0)[1];

            uMat.put(0, i, u);
            uMat.put(1, i, u1);
            uMat.put(2, i, (v + v1) / 2);
            uMat.put(3, i, 1.0);
        }
        log.debug(0, "uMat", uMat);
        return uMat;
    }

    /**
     * inverse camera matrix with two u rows for two eyes
     */
    static Mat makeMInv3d(double f, double cx, double cy) {
        Mat M = Mat.zeros(4, 4, CvType.CV_64F);
        M.put(0, 0,
                f, 0, 0, cx,
                0, f, 0, cx,
                0, 0, f, cy,
                0, 0, 0, 1);
        log.debug(0, "M", M);
        Mat Minv = M.inv();
        log.debug(0, "Minv", Minv);
        return Minv;
    }

    /**
     * inverse translation and projection for two eyes (horizontal)
     */
    public static Mat makeTInv3d(double b) {
        Mat T = Mat.zeros(4, 4, CvType.CV_64F);
        T.put(0, 0,
                1, 0, 0, b / 2,
                1, 0, 0, -b / 2,
                0, 1, 0, 0,
                0, 0, 1, 0);
        log.debug(0, "T", T);
        Mat Tinv = T.inv();
        log.debug(0, "Tinv", Tinv);
        return Tinv;
    }

    /**
     * Scale so that each column looks like a homogeneous 3d vector, i.e. with 1 in
     * the last place.
     */
    public static void normalize3d(Mat m) {
        for (int col = 0; col < m.cols(); ++col) {
            double xval = m.get(0, col)[0];
            double yval = m.get(1, col)[0];
            double zval = m.get(2, col)[0];
            double scaleVal = m.get(3, col)[0];
            m.put(0, col, xval / scaleVal);
            m.put(1, col, yval / scaleVal);
            m.put(2, col, zval / scaleVal);
            m.put(3, col, 1.0);
        }
    }

    /**
     * return the Tait-Bryan (ZYX) "Euler" angles for the supplied rotation matrix.
     * 
     * @param r rotation matrix
     * @return Tait-Bryan ZYX angles in a 1x3 matrix
     */
    public static Mat rotm2euler(Mat r) {
        double sy = Math.sqrt(r.get(0, 0)[0] * r.get(0, 0)[0] + r.get(1, 0)[0] * r.get(1, 0)[0]);
        double x, y, z;
        if (sy > 1e-6) { // singular
            x = Math.atan2(r.get(2, 1)[0], r.get(2, 2)[0]);
            y = Math.atan2(-r.get(2, 0)[0], sy);
            z = Math.atan2(r.get(1, 0)[0], r.get(0, 0)[0]);
        } else {
            x = Math.atan2(-r.get(1, 2)[0], r.get(1, 1)[0]);
            y = Math.atan2(-r.get(2, 0)[0], sy);
            z = 0;
        }
        Mat euler = Mat.zeros(3, 1, CvType.CV_64F);
        euler.put(0, 0, x, y, z);
        return euler;
    }

    public static double rotm2euler2d(Mat r) {
        return Math.atan2(r.get(1, 0)[0], r.get(0, 0)[0]);
    }

    /**
     * apply the first Rodrigues rotation vector and then the second, return
     * resulting rotation vector
     * 
     * @param first  Rodrigues rotation vector to apply first
     * @param second Rodrigues rotation vector to apply second
     * @return resulting Rodrigues rotation vector
     */
    public static Mat combineRotations(Mat first, Mat second) {
        Mat firstM = new Mat();
        Calib3d.Rodrigues(first, firstM);
        Mat secondM = new Mat();
        Calib3d.Rodrigues(second, secondM);
        // "first" means on the right side since the resulting matrix appears on the
        // left in actual computation (i think?)
        Mat productM = new Mat();
        Core.gemm(secondM, firstM, 1.0, new Mat(), 0.0, productM);
        Mat productV = new Mat();
        Calib3d.Rodrigues(productM, productV);
        return productV;
    }

    public static Mat removeTilt(Mat undistortedCameraView, double tilt, double f, Mat kMat, Size newSize) {
        Mat invKMat = kMat.inv();
        Mat unTiltV = Mat.zeros(3, 1, CvType.CV_64F);
        unTiltV.put(0, 0, tilt, 0.0, 0.0);
        Mat unTiltM = new Mat();
        Calib3d.Rodrigues(unTiltV, unTiltM);

        Mat transform = new Mat();
        Core.gemm(unTiltM, invKMat, 1.0, new Mat(), 0.0, transform);
        Mat tallKMat = VisionUtil.makeIntrinsicMatrix(f, newSize);
        Core.gemm(tallKMat, transform, 1.0, new Mat(), 0.0, transform);
        log.debug(0, "result", transform);

        Mat untiltedCameraView = Mat.zeros(newSize, CvType.CV_8UC3);
        Imgproc.warpPerspective(undistortedCameraView, untiltedCameraView, transform, newSize);
        return untiltedCameraView;
    }

    /**
     * first pan (about y) and then tilt (about x), return resulting rotation vector
     * 
     * @param pan  radians, up is positive
     * @param tilt radians, right is positive
     * @return Rodrigues rotation vector
     */
    public static Mat panTilt(double pan, double tilt) {
        Mat panV = Mat.zeros(3, 1, CvType.CV_64F);
        panV.put(0, 0, 0.0, pan, 0.0);
        Mat tiltV = Mat.zeros(3, 1, CvType.CV_64F);
        tiltV.put(0, 0, tilt, 0.0, 0.0);
        return combineRotations(panV, tiltV);
    }

    /**
     * make camera intrinsic matrix
     * 
     * @param f     focal length
     * @param dsize
     *              image size; camera axis goes in the center
     * @return camera intrinsic matrix
     */
    public static Mat makeIntrinsicMatrix(double f, Size dsize) {
        Mat kMat = Mat.zeros(3, 3, CvType.CV_64F);
        kMat.put(0, 0,
                f, 0.0, dsize.width / 2,
                0.0, f, dsize.height / 2,
                0.0, 0.0, 1.0);
        return kMat;
    }

    /**
     * return x and y components of 3d points
     * 
     * @param geometry 3d points
     * @return 2d points
     */
    public static MatOfPoint2f slice(MatOfPoint3f geometry) {
        List<Point> pointList = new ArrayList<Point>();
        for (Point3 p : geometry.toList()) {
            pointList.add(new Point(p.x, p.y));
        }
        return new MatOfPoint2f(pointList.toArray(new Point[0]));
    }

    /**
     * return size of bounding box
     * 
     * @param geometry 2d points
     * @return size of box containing the points
     */
    public static Size boundingBox(MatOfPoint2f geometry) {
        double minX = Collections.min(geometry.toList(), Comparator.comparingDouble((s) -> s.x)).x;
        double minY = Collections.min(geometry.toList(), Comparator.comparingDouble((s) -> s.y)).y;
        double maxX = Collections.max(geometry.toList(), Comparator.comparingDouble((s) -> s.x)).x;
        double maxY = Collections.max(geometry.toList(), Comparator.comparingDouble((s) -> s.y)).y;
        return new Size(maxX - minX, maxY - minY);
    }

    /**
     * Make geometry for a rectangular target centered at the origin with specified
     * x width and y height and zero z thickness, upper-left first, then counter
     * clockwise viewed from the usual camera position with negative z (note this
     * actually means clockwise using the usual z axis/angle notion).
     * 
     * @param width
     * @param height
     * @return four 3d points at the corners
     */
    public static MatOfPoint3f makeTargetGeometry3f(double width, double height) {
        return new MatOfPoint3f(
                new Point3(-width / 2, -height / 2, 0.0),
                new Point3(-width / 2, height / 2, 0.0),
                new Point3(width / 2, height / 2, 0.0),
                new Point3(width / 2, -height / 2, 0.0));
    }

    /**
     * Transform the world-coordinates 3d-but-planar target into pixels at the
     * specified scale.
     * 
     * @param targetGeometryMeters 3d points representing the target in world
     *                             coordinates
     * @param scalePixelsPerMeter  how many pixels per world unit
     * @return 2d points
     */
    public static MatOfPoint2f makeTargetImageGeometryPixels(MatOfPoint3f targetGeometryMeters,
            double scalePixelsPerMeter) {
        MatOfPoint2f slice = VisionUtil.slice(targetGeometryMeters);
        double minX = Collections.min(slice.toList(), Comparator.comparingDouble((s) -> s.x)).x;
        double minY = Collections.min(slice.toList(), Comparator.comparingDouble((s) -> s.y)).y;
        List<Point> pointList = new ArrayList<Point>();
        for (Point3 p : targetGeometryMeters.toList()) {
            pointList.add(new Point((p.x - minX) * scalePixelsPerMeter,
                    (p.y - minY) * scalePixelsPerMeter));
        }
        return new MatOfPoint2f(pointList.toArray(new Point[0]));
    }

    static Mat homogeneousRigidTransform(Mat R, Mat t) {
        Mat worldToCamera = Mat.zeros(4, 4, CvType.CV_32F);
        worldToCamera.put(0, 0,
                R.get(0, 0)[0], R.get(0, 1)[0], R.get(0, 2)[0], t.get(0, 0)[0],
                R.get(1, 0)[0], R.get(1, 1)[0], R.get(1, 2)[0], t.get(1, 0)[0],
                R.get(2, 0)[0], R.get(2, 1)[0], R.get(2, 2)[0], t.get(2, 0)[0],
                0, 0, 0, 1);
        return worldToCamera;
    }

    public static MatOfPoint3f duplicatePoints(MatOfPoint3f targetGeometryMeters, int pointMultiplier) {
        MatOfPoint3f targetPointsMultiplied = new MatOfPoint3f();
        List<Point3> targetpointlist = new ArrayList<Point3>();
        for (int reps = 0; reps < pointMultiplier; reps++) {
            for (Point3 p : targetGeometryMeters.toList()) {
                targetpointlist.add(p);
            }
        }
        targetPointsMultiplied = new MatOfPoint3f(targetpointlist.toArray(new Point3[0]));
        return targetPointsMultiplied;
    }

    public static Mat makeWorldToCameraHomogeneous(double pan, double xPos, double yPos, double zPos) {
        // these are camera-to-world transforms
        Mat cameraToWorldTVec = Mat.zeros(3, 1, CvType.CV_32F);
        cameraToWorldTVec.put(0, 0, xPos, yPos, zPos);
        log.debug(0, "worldTVec", cameraToWorldTVec);

        Mat cameraToWorldRV = Mat.zeros(3, 1, CvType.CV_32F);
        cameraToWorldRV.put(0, 0, 0.0, pan, 0.0);
        log.debug(0, "worldRV", cameraToWorldRV);

        Mat cameraToWorldRMat = new Mat();
        Calib3d.Rodrigues(cameraToWorldRV, cameraToWorldRMat);

        Mat cameraToWorldHomogeneous = homogeneousRigidTransform(cameraToWorldRMat, cameraToWorldTVec);
        log.debug(0, "cameraToWorld (just to see)", cameraToWorldHomogeneous);

        // this is inverse(worldT*worldR)
        // inverse of multiplication is order-reversed multipication of inverses, so
        // which is worldR.t * -worldT or camR*-worldT
        Mat worldToCameraRMat = cameraToWorldRMat.t();
        Mat worldToCameraTVec = new Mat();
        Core.gemm(worldToCameraRMat, cameraToWorldTVec, -1.0, new Mat(), 0, worldToCameraTVec);
        log.debug(0, "camTVec", worldToCameraTVec);

        Mat worldToCameraHomogeneous = homogeneousRigidTransform(worldToCameraRMat, worldToCameraTVec);
        log.debug(0, "worldToCamera", worldToCameraHomogeneous);
        return worldToCameraHomogeneous;
    }

    /**
     * Filter specificaly for shot noise, e.g. 'open' and 'close' and median blur.
     */
    public static void removeSaltAndPepperInPlace(Mat src) {
        // Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(2,
        // 2));
        // Imgproc.morphologyEx(src, src, Imgproc.MORPH_OPEN, kernel);
        // Imgproc.morphologyEx(src, src, Imgproc.MORPH_CLOSE, kernel);
        Imgproc.medianBlur(src, src, 3);
    }

    /**
     * find the target corners, in the supplied image, upper-left first, then
     * clockwise.
     * 
     * 
     * @param picIdx        for debugging
     * @param rawCameraView unprocessed image
     * @return 2d geometry of the corners, in the image
     */
    public static MatOfPoint2f findTargetCornersInImage(int picIdx, boolean writeFiles, Mat rawCameraView,
            int threshold) {

        // first "binarize" to remove blur
        Mat cameraView = new Mat();
        Imgproc.threshold(rawCameraView, cameraView, threshold, 255, Imgproc.THRESH_BINARY);
        if (writeFiles)
            Imgcodecs.imwrite(String.format("C:\\Users\\joelt\\Desktop\\pics\\target-%d-thresholded.png", picIdx),
                    cameraView);

        Mat singleChannelCameraView = new Mat();
        if (rawCameraView.channels() == 1)
            singleChannelCameraView = cameraView;
        else {
            Imgproc.cvtColor(cameraView, singleChannelCameraView, Imgproc.COLOR_BGR2GRAY);

        }
        if (writeFiles)
            Imgcodecs.imwrite(String.format("C:\\Users\\joelt\\Desktop\\pics\\target-%d-bw.png", picIdx),
                    singleChannelCameraView);
        /*
         * Mat edges = new Mat();
         * Imgproc.Canny(singleChannelCameraView, edges, 250, 255);
         * MatOfPoint approxCurve = new MatOfPoint();
         * Imgproc.goodFeaturesToTrack(edges, approxCurve, 4, 0.5, 2);
         * System.out.println("approxcurve");
         * System.out.println(approxCurve.dump());
         */

        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();
        Imgproc.findContours(singleChannelCameraView,
                contours,
                hierarchy,
                Imgproc.RETR_EXTERNAL,
                Imgproc.CHAIN_APPROX_SIMPLE);

        log.debug(0, "hierarchy", hierarchy);
        log.debug(2, "contours size", contours.size());

        List<MatOfPoint> bigContours = new ArrayList<>();
        for (MatOfPoint c : contours) {
            if (Imgproc.contourArea(c) > 10) {
                bigContours.add(c);
            }
        }
        log.debug(2, "big contours size", bigContours.size());

        contours = bigContours;
        for (MatOfPoint c : contours) {
            log.debug(2, "contour", c);
        }
        if (contours.size() != 1) {
            log.debugmsg(2, "no contours!");
            log.debug(2, "contours size", contours.size());

            return null;
        }

        if (writeFiles) {
            Mat contourView2 = Mat.zeros(cameraView.size(), CvType.CV_8U);
            Imgproc.drawContours(contourView2, contours, 0, new Scalar(255, 0, 0));
            Imgcodecs.imwrite(String.format("C:\\Users\\joelt\\Desktop\\pics\\target-%d-contours.png", picIdx),
                    contourView2);
        }

        MatOfPoint2f curve = new MatOfPoint2f(contours.get(0).toArray());
        MatOfPoint2f approxCurve = new MatOfPoint2f();
        double epsilon = 0.04 * Imgproc.arcLength(curve, true);
        Imgproc.approxPolyDP(curve, approxCurve, epsilon, true);
        MatOfPoint points = new MatOfPoint(approxCurve.toArray());
        // System.out.println("points");
        // System.out.println(points.dump());

        if (writeFiles) {
            Mat contourView = Mat.zeros(cameraView.size(), CvType.CV_8U);
            Imgproc.drawContours(contourView, List.of(points), 0, new Scalar(255, 0, 0));
            Imgcodecs.imwrite(String.format("C:\\Users\\joelt\\Desktop\\pics\\target-%d-poly.png", picIdx),
                    contourView);
        }
        // System.out.println("approxcurve");
        // System.out.println(approxCurve.dump());
        if (approxCurve.toList().size() != 4) {
            log.debugmsg(2, "wrong size");
            log.debug(2, "approxcurve", approxCurve);
            return null;
        }

        MatOfInt hull = new MatOfInt();

        Imgproc.convexHull(points, hull, true);

        Point upperLeftPoint = new Point(Double.MAX_VALUE, Double.MAX_VALUE);
        int idx = 0;
        List<Point> approxCurveList = approxCurve.toList();
        for (int i = 0; i < approxCurveList.size(); ++i) {
            Point p = approxCurveList.get(i);
            if (p.x + p.y < upperLeftPoint.x + upperLeftPoint.y) {
                upperLeftPoint = p;
                idx = i;
            }
        }

        Collections.rotate(approxCurveList, -idx);

        MatOfPoint2f imagePoints = new MatOfPoint2f(approxCurveList.toArray(new Point[0]));

        Mat pointView = rawCameraView.clone();
        for (Point pt : imagePoints.toList()) {
            Imgproc.circle(pointView,
                    new Point(pt.x, pt.y),
                    3,
                    new Scalar(0, 255, 0),
                    Imgproc.FILLED);
        }
        if (writeFiles)
            Imgcodecs.imwrite(String.format("C:\\Users\\joelt\\Desktop\\pics\\target-%d-points.png", picIdx),
                    pointView);
        return imagePoints;
    }

    /**
     * given camera rotation in camera frame and camera position in *world* frame,
     * *in* the world, derive camera position in camera frame, for warping.
     * 
     * @param camera rotation vector
     * @param world  translation vector
     * @return camera translation vector
     */
    public static Mat world2Cam(Mat camRV, Mat worldTVec) {
        // make camera coords from world coords
        Mat camRMat = new Mat();
        Calib3d.Rodrigues(camRV, camRMat);

        Mat worldRMat = camRMat.t();
        Mat worldRVec = new Mat();
        Calib3d.Rodrigues(worldRMat, worldRVec);

        Mat camTVec = new Mat();
        Core.gemm(worldRMat.t(), worldTVec, -1.0, new Mat(), 0.0, camTVec);
        return camTVec;
    }

    /**
     * apply an dx-translation transform to the given transform (i.e. multiply it)
     */
    public static Mat translateX(Mat worldToCamera, double dx) {
        Mat translation = Mat.zeros(4, 4, CvType.CV_32F);
        translation.put(0, 0,
                1, 0, 0, dx,
                0, 1, 0, 0,
                0, 0, 1, 0,
                0, 0, 0, 1);
        log.debug(0, "translation", translation);
        Mat result = new Mat();
        Core.gemm(translation, worldToCamera, 1.0, new Mat(), 0.0, result);
        log.debug(0, "result", result);
        return result;
    }

    public static boolean inViewport(MatOfPoint2f pts, Rect viewport) {
        for (Point pt : pts.toList()) {
            if (!viewport.contains(pt))
                return false;
        }
        return true;
    }

    /**
     * project target geometry into the camera
     */
    public static MatOfPoint2f imagePoints(Mat kMat, MatOfDouble dMat, MatOfPoint3f geometry, Mat worldToCamera) {
        Mat Rvec = Mat.zeros(3, 1, CvType.CV_32F);
        Calib3d.Rodrigues(worldToCamera.rowRange(0, 3).colRange(0, 3), Rvec);
        log.debug(0, "Rvec", Rvec);
        Mat t = worldToCamera.colRange(3, 4).rowRange(0, 3);
        log.debug(0, "t", t);

        MatOfPoint2f pts = new MatOfPoint2f();
        Mat jacobian = new Mat();
        Calib3d.projectPoints(geometry, Rvec, t, kMat, dMat, pts, jacobian);
        log.debug(0, "pts", pts);
        return pts;
    }

    /**
     * Add noise.
     */
    public static MatOfPoint2f perturbPoints(MatOfPoint2f pts, int pointMultiplier, double noisePixels, Random rand) {
        List<Point> ptsList = new ArrayList<Point>();
        for (int reps = 0; reps < pointMultiplier; reps++) {
            for (Point p : pts.toList()) {
                p.x = p.x + rand.nextGaussian() * noisePixels;
                p.y = p.y + rand.nextGaussian() * noisePixels;
                ptsList.add(p);
            }
        }
        return new MatOfPoint2f(ptsList.toArray(new Point[0]));
    }

    public static MatOfPoint2f projectGeometryToImagePoints(
            double xPos,
            double yPos,
            double zPos,
            double tilt,
            double pan,
            Mat kMat,
            MatOfDouble dMat,
            MatOfPoint3f targetGeometryMeters) {
        // System.out.println(targetGeometryMeters.dump());
        Mat worldTVec = Mat.zeros(3, 1, CvType.CV_64F);
        worldTVec.put(0, 0, xPos, yPos, zPos);

        // camera up/right means world down/left, so both negative
        Mat camRV = VisionUtil.panTilt(-pan, -tilt);
        Mat camRMat = new Mat();
        Calib3d.Rodrigues(camRV, camRMat);

        Mat worldRMat = camRMat.t();
        Mat worldRV = new Mat();
        Calib3d.Rodrigues(worldRMat, worldRV);

        Mat camTVec = VisionUtil.world2Cam(camRV, worldTVec);
        // System.out.println(dMat.dump());

        MatOfPoint2f skewedImagePts2f = new MatOfPoint2f();
        Mat jacobian = new Mat();
        // Calib3d.projectPoints(targetGeometryMeters, camRV, camTVec, kMat, dMat,
        // skewedImagePts2f, jacobian);
        Calib3d.projectPoints(targetGeometryMeters, camRV, camTVec, kMat, dMat, skewedImagePts2f, jacobian);

        return skewedImagePts2f;
    }

    /**
     * synthesize an image of a vision target using the supplied location and
     * camera.
     * using a known target geometry, generate an image of the target viewed
     * from the specified location, which is the position of the camera in the
     * world, and the rotations are rotations *of the camera*.
     * 
     * @param xPos                 camera location in world coords
     * @param yPos                 camera location in world coords
     * @param zPos                 camera location in world coords
     * @param tilt                 camera upwards tilt (around x axis)
     * @param pan                  camera rightwards pan (around y axis)
     * @param targetGeometryMeters target geometry in world coords
     */
    public static Mat makeImage(
            double xPos,
            double yPos,
            double zPos,
            double tilt,
            double pan,
            Mat kMat,
            MatOfDouble dMat,
            MatOfPoint3f targetGeometryMeters,
            Size dsize) {

        // // System.out.println(targetGeometryMeters.dump());
        // Mat worldTVec = Mat.zeros(3, 1, CvType.CV_64F);
        // worldTVec.put(0, 0, xPos, yPos, zPos);
        // MatOfPoint2f targetImageGeometry =
        // VisionUtil.makeTargetImageGeometryPixels(targetGeometryMeters, 1000);
        // // System.out.println(targetImageGeometry.dump());

        // // make an image corresponding to the pixel geometry, for warping
        // Mat visionTarget = new Mat(VisionUtil.boundingBox(targetImageGeometry),
        // CvType.CV_8UC3,
        // new Scalar(255, 255, 255));
        // // Imgcodecs.imwrite("C:\\Users\\joelt\\Desktop\\projection.jpg",
        // visionTarget);

        // // camera up/right means world down/left, so both negative
        // Mat camRV = VisionUtil.panTilt(-pan, -tilt);

        // Mat camTVec = VisionUtil.world2Cam(camRV, worldTVec);
        // // System.out.println(dMat.dump());

        // MatOfPoint2f skewedImagePts2f = new MatOfPoint2f();
        // Mat jacobian = new Mat();
        // Calib3d.projectPoints(targetGeometryMeters, camRV, camTVec, kMat, dMat,
        // skewedImagePts2f, jacobian);

        MatOfPoint2f targetImageGeometry = VisionUtil.makeTargetImageGeometryPixels(targetGeometryMeters, 1000);
        // System.out.println(targetImageGeometry.dump());

        MatOfPoint2f skewedImagePts2f = projectGeometryToImagePoints(xPos,
                yPos,
                zPos,
                tilt,
                pan,
                kMat,
                dMat,
                targetGeometryMeters);

        // System.out.println("jacobian");
        // System.out.println(jacobian.dump());
        // System.out.println(skewedImagePts2f.dump());
        // MatOfPoint2f undistortedPts = new MatOfPoint2f();
        // Calib3d.projectPoints(targetGeometryMeters, camRV, camTVec, kMat, new
        // MatOfDouble(), undistortedPts);
        // System.out.println("undistorted?");
        // System.out.println(undistortedPts.dump());

        // MatOfPoint2f maybeGood = new MatOfPoint2f();
        // System.out.println(kMat.dump());
        // Calib3d.undistortPoints(skewedImagePts2f, maybeGood, kMat, dMat, new Mat(),
        // kMat);
        // System.out.println("maybe good");
        // System.out.println(maybeGood.dump());
        // System.out.println(kMat.dump());

        // if clipping, this isn't going to work, so bail
        // actually it also doesn't work if the area is too close to the edge
        final int border = 5;
        Rect r = new Rect(border, border, (int) (dsize.width - border), (int) (dsize.height - border));
        for (Point p : skewedImagePts2f.toList()) {
            if (!r.contains(p)) {
                // System.out.println("out of frame");
                // System.out.println(r.toString());
                // System.out.println(skewedImagePts2f.dump());
                return null;
            }
        }

        Mat transformMat = Imgproc.getPerspectiveTransform(targetImageGeometry, skewedImagePts2f);

        // make an image corresponding to the pixel geometry, for warping
        Mat visionTarget = new Mat(VisionUtil.boundingBox(targetImageGeometry), CvType.CV_8UC3,
                new Scalar(255, 255, 255));
        Mat cameraView = Mat.zeros(dsize, CvType.CV_8UC3);
        Imgproc.warpPerspective(visionTarget, cameraView, transformMat, dsize);

        return cameraView;
    }

    public static Mat renderImage(int brightness, Size dsize, MatOfPoint3f targetGeometryMeters,
            MatOfPoint2f skewedImagePts2f) {
        MatOfPoint2f targetImageGeometry = VisionUtil.makeTargetImageGeometryPixels(targetGeometryMeters, 1000);

        Mat transformMat = Imgproc.getPerspectiveTransform(targetImageGeometry, skewedImagePts2f);

        // make an image corresponding to the pixel geometry, for warping
        Mat visionTarget = new Mat(VisionUtil.boundingBox(targetImageGeometry), CvType.CV_8UC1,
                new Scalar(brightness));
        Mat cameraView = Mat.zeros(dsize, CvType.CV_8UC1);
        Imgproc.warpPerspective(visionTarget, cameraView, transformMat, dsize);
        visionTarget.release();
        System.gc();
        return cameraView;
    }

    /**
     * Add speckle in place
     */
    public static void addSaltAndPepper(Mat img) {
        Mat noise = img.clone();
        Core.randu(noise, 0, 255);
        Mat black = noise.clone();
        Imgproc.threshold(noise, black, 30, 255, Imgproc.THRESH_BINARY_INV);
        Mat white = noise.clone();
        Imgproc.threshold(noise, white, 225, 255, Imgproc.THRESH_BINARY);
        img.setTo(new Scalar(255), white);
        img.setTo(new Scalar(0), black);
        noise.release();
        black.release();
        white.release();
        System.gc();
    }

    /**
     * Add noise in place
     */
    public static void addGaussianNoise(Mat img) {
        Mat noise = img.clone();
        Core.randn(noise, 128, 30);
        Core.add(img, noise, img);
        noise.release();
        System.gc();
    }

    public static void writePng(MatOfPoint2f pts, int width, int height, String filename) {
        final Scalar green = new Scalar(0, 255, 0);
        Mat img = Mat.zeros(height, width, CvType.CV_32FC3);
        for (Point pt : pts.toList()) {
            Imgproc.circle(img, pt, 6, green, 1);
        }
        Imgcodecs.imwrite(filename, img);
    }

    /**
     * project target geometry into the image plane. this method also looks at the
     * jacobian of the projection transformed to world coordinates but it isn't
     * returned.
     */
    public static MatOfPoint2f makeSkewedImagePts2f(MatOfPoint3f expandedTargetGeometryMeters, Mat newCamRVec,
            Mat newCamTVec,
            Mat tallKMat, Mat newWorldRMat) {
        MatOfPoint2f skewedImagePts2f = new MatOfPoint2f();
        Mat jacobian = new Mat();
        Calib3d.projectPoints(expandedTargetGeometryMeters, newCamRVec,
                newCamTVec, tallKMat, new MatOfDouble(), skewedImagePts2f, jacobian);
        // Mat dpdrot = jacobian.colRange(0, 3); // someday: deal with dpdrot.
        Mat dpdt = jacobian.colRange(3, 6);
        // calculate the stdev dtdp for each dimension:
        double pdxCamDp = 0;
        double pdyCamDp = 0;
        double pdzCamDp = 0;
        double pdxWorldDp = 0;
        double pdyWorldDp = 0;
        double pdzWorldDp = 0;
        // guess stdev in pixels :-)
        final Mat dp = Mat.zeros(2, 1, CvType.CV_64F);
        dp.put(0, 0, 3, 3);
        log.debug(0, "dp", dp);
        for (int i = 0; i < dpdt.rows(); i += 2) {
            Mat pointDpdt = dpdt.rowRange(i, i + 2);
            log.debug(0, "dpdt", pointDpdt);
            Mat dtdp = new Mat();
            Core.invert(pointDpdt, dtdp, Core.DECOMP_SVD);

            log.debug(0, "dtdp", dtdp);
            Mat dt = new Mat();
            Core.gemm(dtdp, dp, 1.0, new Mat(), 0.0, dt);
            pdxCamDp += (dt.get(0, 0)[0] * dt.get(0, 0)[0]);
            pdyCamDp += (dt.get(1, 0)[0] * dt.get(1, 0)[0]);
            pdzCamDp += (dt.get(2, 0)[0] * dt.get(2, 0)[0]);
            // ok now find the world-transformed dt.
            // this is the jacobian of the transform (which is just the
            // transform itself), evaluated at the predicted camt.
            // ... this should be a 3x3 not a 3x1, grrr
            Mat Jworld = new Mat();
            Core.gemm(newWorldRMat, newCamTVec, -1.0, new Mat(), 0.0, Jworld);
            log.debug(0, "Jworld", Jworld);
            Mat dtWorld = new Mat();
            Core.gemm(Jworld.t(), dt, -1.0, new Mat(), 0.0, dtWorld);
            log.debug(0, "dtWorld", dtWorld);
        }
        pdxCamDp /= dpdt.rows() / 2;
        pdyCamDp /= dpdt.rows() / 2;
        pdzCamDp /= dpdt.rows() / 2;
        pdxCamDp = Math.sqrt(pdxCamDp);
        pdyCamDp = Math.sqrt(pdyCamDp);
        pdzCamDp = Math.sqrt(pdzCamDp);
        pdxWorldDp /= dpdt.rows() / 2;
        pdyWorldDp /= dpdt.rows() / 2;
        pdzWorldDp /= dpdt.rows() / 2;
        pdxWorldDp = Math.sqrt(pdxWorldDp);
        pdyWorldDp = Math.sqrt(pdyWorldDp);
        pdzWorldDp = Math.sqrt(pdzWorldDp);

        // System.out.printf(" %f, %f, %f, %f, %f, %f\n",
        // pdxCamDp, pdyCamDp, pdzCamDp, pdxWorldDp, pdyWorldDp, pdzWorldDp);
        return skewedImagePts2f;
    }

    /**
     * for the solution that ignores the Y/v dimension, use (u, u', 1)
     */
    static Mat makeUMat2d(MatOfPoint2f leftPts, MatOfPoint2f rightPts) {
        Mat uMat = Mat.zeros(leftPts.toList().size(), 3, CvType.CV_64F);
        for (int i = 0; i < leftPts.toList().size(); ++i) {
            uMat.put(i, 0, leftPts.get(i, 0)[0], rightPts.get(i, 0)[0], 1.0);
        }
        log.debug(0, "uMat", uMat);
        return uMat;
    }

    public static Mat makeXMat2d(MatOfPoint3f targetPointsMultiplied) {
        List<Point3> targetPointsMultipliedList = targetPointsMultiplied.toList();

        List<Point3> listOfXZ = new ArrayList<Point3>();
        for (Point3 p3 : targetPointsMultipliedList) {
            listOfXZ.add(new Point3(p3.x, p3.z, 1));
        }
        MatOfPoint3f targetPointsMultipliedXZHomogeneous = new MatOfPoint3f(
                listOfXZ.toArray(new Point3[0]));

        Mat targetPointsMultipliedXZHomogeneousMat = targetPointsMultipliedXZHomogeneous.reshape(1).t();

        targetPointsMultipliedXZHomogeneousMat.convertTo(targetPointsMultipliedXZHomogeneousMat,
                CvType.CV_64F);
        return targetPointsMultipliedXZHomogeneousMat;
    }

    static Mat makeMInv2d(double f, double cx) {
        Mat M = Mat.zeros(3, 3, CvType.CV_64F);
        M.put(0, 0,
                f, 0, cx,
                0, f, cx,
                0, 0, 1);
        log.debug(0, "M", M);
        Mat Minv = M.inv();
        log.debug(0, "Minv", Minv);
        return Minv;
    }

    static Mat makeTInv2d(double b) {
        Mat T = Mat.zeros(3, 3, CvType.CV_64F);
        T.put(0, 0,
                1, 0, b / 2,
                1, 0, -b / 2,
                0, 1, 0);
        log.debug(0, "T", T);
        Mat Tinv = T.inv();
        log.debug(0, "Tinv", Tinv);
        return Tinv;
    }

    public static Mat makeBMat2d(MatOfPoint2f leftPts, MatOfPoint2f rightPts, double b, double f, double cx) {
        // To solve Ax=b triangulation (Ax=M-1T-1u), first make u: (u,u',1):
        Mat uMat = makeUMat2d(leftPts, rightPts);

        // and the inverse transforms we're going to apply:
        Mat Minv = makeMInv2d(f, cx);
        Mat Tinv = makeTInv2d(b);

        // apply the inverses to the observations (the "u") in the correct order:
        Mat MinvUmat = new Mat();
        Core.gemm(Minv, uMat.t(), 1.0, new Mat(), 0.0, MinvUmat);
        log.debug(0, "MinvUmat", MinvUmat);

        Mat bMat = new Mat();
        Core.gemm(Tinv, MinvUmat, 1.0, new Mat(), 0.0, bMat);
        log.debug(0, "bMat", bMat);

        // Make the result look homogeneous
        normalize2d(bMat);
        log.debug(0, "bMat normalized", bMat);
        return bMat;
    }

    static void normalize2d(Mat TinvMinvBmat) {
        for (int col = 0; col < TinvMinvBmat.cols(); ++col) {
            double xval = TinvMinvBmat.get(0, col)[0];
            double zval = TinvMinvBmat.get(1, col)[0];
            double scaleVal = TinvMinvBmat.get(2, col)[0];
            TinvMinvBmat.put(0, col, xval / scaleVal);
            TinvMinvBmat.put(1, col, zval / scaleVal);
            TinvMinvBmat.put(2, col, 1.0);
        }
        log.debug(0, "TinvMinvBmat (scaled)", TinvMinvBmat);
    }

    /**
     * use OpenCV Core.solve(X, b, A, DECOMP_SVD), return A.
     */
    public static Mat solve(Mat XMat, Mat bMat) {
        // so now Ax=b where X is the world geometry and b is above.
        Mat AA = new Mat();

        // remember the solver likes transposes
        Core.solve(XMat.t(), bMat.t(), AA, Core.DECOMP_SVD);
        // ...and produces a transpose.
        AA = AA.t();
        log.debug(0, "AA", AA);

        double Ascale = AA.get(2, 2)[0];
        Core.gemm(AA, Mat.eye(3, 3, CvType.CV_64F), 1 / Ascale, new Mat(), 0.0, AA);
        log.debug(1, "AA scaled", AA);
        return AA;
    }

}
