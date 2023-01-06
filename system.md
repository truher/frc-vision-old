# System Overview

<p align=center><img src="https://docs.google.com/drawings/d/e/2PACX-1vTermEQdA50RCGKwogC3vFPD0L5BSxefY743VLYksJp7Z4DL-Zu2sy8-F-xLLjQrFHyy93PnW7f7bCW/pub?w=768" width=768/></p>

The overall goal is to __produce estimates for robot pose and target location accurate enough for semi-automated driving.__
Instead of the usual "remote-controlled car" approach to FRC movement, the approach can be based on higher-level
commands, for example "go to target" or "return to base," and most important, automated targeting for game piece placement or launching.

There are lots of other things we could do with cameras, but those ideas are not addressed here; see "non-goals" below.

There are several virtues, vices, and constraints, that affect the design:

* __Maximize learning.__  FRC is a teaching tool.  The reason to build a thing is to teach about design, not to supply a
"mentor-made" solution in place of a COTS one.
* __Minimize complexity.__  FRC is for high school students; the whole thing should be easy to grasp without advanced knowledge.
* __Minimize cost.__  FRC rules stipulate a $600 limit for any single COTS element, but the system should simply be as simple and cheap as possible,
so we can have several of them to play with.
* __Solve a simple problem.__  Rather than solving a complex modern vision problem, make use of the simple
available __retroreflective targets,__ which have known, useful (i.e. game-piece-receiving) locations.
Focusing on the targets doesn't preclude __also__ doing other things with vision,
e.g. "chase the red ball" kinds of things, see "non-goals" below.
* __Maximize the use of COTS parts.__  FRC rules stipulate that unmodified COTS parts may be freely reused, so a complete system can be assembled and refined
in the off-season, and then trivially re-assembled during the build season.  Avoid custom printed circuits, etc.
* __Maximize accuracy.__ To depend on vision-derived position __all the time,__ it needs to be accurate to within a few centimeters, over the entire
8x16-meter field.
* __Maximize flexibility.__ Neither the game dynamics, nor the field layout, are known in advance, so the objective is to get familiar
with the technology, accumulate some prototypes, and be able to deploy them quickly in January.


The system comprises four parts:

1. __Illumination.__  Instead of the usual "green LED" solution, we'll use an [infrared strobe illuminator](illuminator.md).
To maximize the contrast of the vision targets, and to minimize motion blur, use a bright flash in a wavelength
outside the range of background lighting.  Because the background covers the visible spectrum, I chose a near-IR wavelength of 730nm,
which is also one of the most efficient LED emitters available.
2. __Cameras.__  To start with, we'll use [stereoscopic binocular cameras](camera.md).  A binocular approach provides about 4x
the accuracy of estimated target distance, compared with a monocular approach.  To avoid artifacts, choose a global-shutter camera.
If the game includes multiple targets, a multi-camera non-stereoscopic approach might work as well.
4. __Code.__  There are two phases of computation:
    1. Image analysis uses [OpenCV processing on Raspberry Pi](code.md), because it's simple.  It avoids special hardware and magic
    neural nets nobody understands, just use the OpenCV stuff that WPILib comes with, and use the most straightforward
    "network tables" style interface between the RIO and the Pi.  For comparison, the
    Limelight [appears to use](https://www.chiefdelphi.com/t/ever-wondered-what-makes-a-limelight-2-tick/380418) a Raspberry Pi 
    compute module, coupled with a microcontroller (on its own printed circuit board) and a separate board for the illuminator.
    2. The RoboRIO performs a couple of further processing steps:
        1. __IMU Fusion.__  Because both the target size and the binocular interpupillary distance are known, the distance to the target
        is relatively easy to measure, and the __relative__ bearing (angle from camera to target) can be measured very precisely (easily to a tenth
        of a degree) but much farder to measure the absolute bearing (from target to camera).  In other words, if you're directly
        in front of the target, it will look almost exactly the same if you're a five or ten degrees to the left or to the right.
        To resolve this issue, use the IMU bearing instead of the vision-derived bearing.
        2. __Kalman Filter Pose Estimator.__  The RIO maintains a Kalman Filter (probably the EKF or UKF in WPILib) representing
        the robot pose, and corrects the filter periodically with vision- and IMU-derived data, as well as other sources, e.g. wheel odometry.

# Non-goals

There are other things we could do with the same, or a different, camera setup, and we could pursue some of these ideas too, separately:

1. __Depth mapping.__  With a normal, color binocular camera, a scene can be converted into a [depth map](https://en.wikipedia.org/wiki/Depth_map)
showing near and far regions, or a [point cloud](https://en.wikipedia.org/wiki/Point_cloud) describing the 3d geometry of nearby objects.  This
would be useful for obstacle avoidance and path finding.  The driving characteristic in this arrangement is finding many matching details in each
eye, and measuring the "disparity" (horizontal difference between right and left views) between each detail, so it's important to see a lot
at high resolution, and illumination is only important to find details.  By comparison, goal above is to produce images with as little detail
as possible: blazing white targets on a black background.  It would be possible to combine these two goals into one hardware solution,
using an illuminator matching one of the Bayer filter colors (e.g. the common green illuminator), and using the other two colors for depth mapping.
Keeping the depth mapping and SLAM systems separate for now makes the software simpler; we can combine these projects later if we decide
to get into the sort of path-finding that would benefit from point clouds.  (Note: I haven't found a suitable full-color binocular
global-shutter camera, but maybe I haven't looked hard enough.)
2. __Object detection.__  In order to generate paths towards or around objects, it would be useful to 
identify game pieces, teammates, opponents, etc, using a normal color
monocular camera and fancy software.  Other than the use of a camera, there's not much overlap in the hardware or software relevant to
this problem, so we could do both, separately.
It would even be possible to ignore the retroreflective targets entirely and design a SLAM system around detection of other objects,
but IMHO the software challenge there is inappropriate.


<hr>

1. [the diagram above](https://docs.google.com/drawings/d/1su0P3QJuXBge3MNicDzGQI53iGWPYA5LVvDXvMOyg00/edit)
