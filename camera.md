# Camera

## Criteria

There are several criteria involved in the camera choice.

1. __Number of views and eyes.__  I simulated single-view monocular and binocular options (see [comparison](comparison.md)),
2. and found the binocular option
provides about 4X the accuracy, and since the cost, complexity, and compute load per eye is reasonable, it seems worth doing two;
we should explore both options.  Note that
the eyes in a binocular setup need to be synchronized at the frame level, to avoid inaccuracy derived from timing differences.  Another
option would be multi-view, e.g. 360-degree coverage, which might involve three or four monocular views, and might enable
higher accuracy without IMU fusion if multiple targets are visible.  Full-circle binocular coverage would require six to eight cameras,
which seems like a lot, and unnecessary if multiple targets are visible.  Almost everything here can be easily adapted to 360 arrangements.
2. __Spectral response.__   Most cameras capture full-color using a [mosaic of tiny color filters](https://en.wikipedia.org/wiki/Bayer_filter),
which is useful to differentiate objects by color, for example.  But the problem to solve here is simpler: detect retroreflective targets,
illuminated by a source of our choice.  We don't need color, we just need enough contrast between the target and the background to
be able to measure the target geometry in the camera image.  There are two options, we should explore both
(There's a fuller discussion of [radiometry issues here](radiometry.md)):
    1. To maximize contrast of the target while minimizing illuminator power, we could use an illuminator wavelength outside the background
    gym-lighting spectrum, and filter out the background, in front of a monochrome camera.  The best spectrum for this purpose is near-infrared,
    which means we need an IR-sensitive camera.  
    2. To maximize contrast without a dedicated camera, we could use an illuminator wavelength visible to a full-color (Bayer mosaic)
    camera, and just make the illuminator bright enough to overpower the background.  The common green LED illuminator uses this strategy.
3. __Shutter type.__  Most cameras use a [rolling shutter](https://en.wikipedia.org/wiki/Rolling_shutter) which means that a single frame
is not captured all at once but rather one pixel-row at a time, slicing horizontally.  They work this way to simplify in-camera data
processing and to increase sensitivity, at the expense of blur, jello, and other artifacts.  A rolling shutter is also much harder to
synchronize with a strobe: the strobe needs to illuminate the entire capture event, from top to bottom, with the same intensity.
To maximize sharpness without artifacts, in order to accurately extract the target geometry from the image, we use the less-common "global shutter."
4. __Lenses.__  Lens choice is a tradeoff between accuracy at distance (more magnification = more accuracy) and field of view: you can't
localize if you can't see the target.  The best tradeoff depends on the specifics of the targets and the game, so we should choose
interchangeable lenses. The relevant standards for interchangeable lenses are
[12mm or "S-mount"](https://en.wikipedia.org/wiki/Draft:S-mount_(CCTV_lens)) and ["C-mount"](https://en.wikipedia.org/wiki/C_mount).  There
are many [differences](https://www.optowiki.info/blog/comparison-c-mount-lenses-vs-s-mount-lenses-m12x0-5/); one important
difference is cost, S-mount tend to be much less expensive.
5. __Strobe output.__  Not all camera modules expose the shutter signal, so the strobe can be synchronized with it.

## Selection

[ArduCam](https://www.arducam.com/) provides several
[monocular, binocular, and quadocular kits](https://www.arducam.com/raspberry-pi-multiple-cameras/) that seem
appropriate, using the Omnivision OV9281 sensor, which has 1280×800 pixels and a global shutter, about $40 for a single camera.
A binocular [kit is available](https://www.arducam.com/product/arducam-1mp2-stereoscopic-camera-bundle-kit-for-raspberry-pi-nvidia-jetson-nano-xavier-nx-two-ov9281-global-shutter-monochrome-camera-modules-and-camarray-stereo-camera-hat/),

<img src="https://www.uctronics.com/media/catalog/product/cache/f16269e76514986618cdfcfa729ea40d/a/r/arducam-1mp-0v9281-mipi-stereoscopic_camerabundle_kit-b0266_3_.jpg" height=300/><img src="https://www.arducam.com/wp-content/uploads/2020/10/B0266-2-600x600.jpg" height=300/>

It includes two separate OV9281 modules and a multiplexer board, which works by capturing two frames at the same (hardware-synchronized)
time, and stitching them together into one very wide image for the Raspberry Pi.  Separate modules are better than a single two-camera board
because they can be placed further apart, increasing accuracy.  The same module is also available in a
[four-way configuration.](https://www.arducam.com/product/arducam-1mp4-quadrascopic-camera-bundle-kit-for-raspberry-pi-nvidia-jetson-nano-xavier-nx-four-ov9281-global-shutter-monochrome-camera-modules-and-camarray-camera-hat/)

Another option is the OV2311 sensor, for example [here](https://www.e-consystems.com/industrial-cameras/ov2311-monochrome-global-shutter-camera.asp).
or [here](https://www.arducam.com/product/arducam-2mp-ov2311-global-shutter-noir-mono-camera-modules-pivariety/), about $110.  It
is similar to the OV9281 (mono global shutter), but with double the resolution, 1600 x 1300 pixels, using the same pitch, 3 &micro;m. I'm not sure
the extra resolution is required.

This is the quantum efficiency of the detector in these cameras:

<p align=center><img src="https://www.e-consystems.com/images/See3CAM/See3CAM_20CUG/quantum-efficiency-graph-large.jpg" width=640/></p>

The best QE is around 600nm, but the FWHM covers the entire spectrum.  There may be some tradeoff in illuminator and filter efficiency here.


## Alternatives

1. __The old Raspberry Pi sensor, OV5647,__ [might be](https://www.chiefdelphi.com/t/ever-wondered-what-makes-a-limelight-2-tick/380418/29)
used by the Limelight device, and it provides a global shutter, though, strangely, not in the
[Arducam package.](https://www.arducam.com/docs/cameras-for-raspberry-pi/native-raspberry-pi-cameras/5mp-ov5647-standard-camera-modules/)
2. __USB cameras.__  It's common to use simple color USB cameras, but these don't satisfy any of the above requirements.
