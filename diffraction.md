# Diffraction

When choosing an illuminator wavelength, one of the things to think about is diffraction.  Light is a wave-like thing,
and so it diffracts around edges, just like waves in the ocean.  If the wavelength of light coming into the camera is
long enough, then the resolution of the camera won't be limited by the number of pixels, it will be limited by the
size of the "blob" produced by diffraction, called the [Airy disk](https://en.wikipedia.org/wiki/Airy_disk).
This situation is called "diffraction-limited."

The diameter of the Airy disk can be
[estimated](https://www.edmundoptics.in/knowledge-center/application-notes/imaging/limitations-on-resolution-and-contrast-the-airy-disk/)
as follows:

$$
\emptyset_{Airy Disk} = 2.44 \cdot \lambda \cdot fstop
$$

The lens we'll probably use has an f-stop of 2.8, and the longest wavelength we'll use is around 740nm, which yields an Airy disk
diameter of about 5 &micro;m, which is larger than the pixel size of the OV9281 sensor 3 &micro;m, so the optics will definitely
be diffraction-limited, i.e. the actual resolution of the camera won't be 1280 x 800, it will be more like 768 x 480.
To get more "pixels," we'd need a "faster" lens (lower f-stop) or a larger sensor (which is what the OV2311 has).
To stay keep the Airy disk within the 3 &micro;m pixel, we'd need an illuminator below 439 nm, i.e. deep blue.
