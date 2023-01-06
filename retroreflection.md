# Retroreflection

The key features perceived by the vision system are the __retroreflective tape vision targets__ provided in the FRC field.  In order
to understand how to best build a system around them, we should first understand a bit about retroreflection.

First, what is it exactly?  The [game manual](https://firstfrc.blob.core.windows.net/frc2022/Manual/2022FRCGameManual.pdf) tells us
that the tape used on the field is [3M 8830 Scotchlite™ Reflective Material](https://www.3m.com/3M/en_US/p/d/v000312582/), which is more fully
described in the [datasheet](https://multimedia.3m.com/mws/media/662425O/reflective-psa-technical-data-sheet.pdf).  It's a polyester fabric
topped with microscopic glass beads:

<p align=center><img src="https://upload.wikimedia.org/wikipedia/commons/thumb/1/19/Reflective_leg_band_in_Scanning_Electron_Microscope%2C_15x.GIF/600px-Reflective_leg_band_in_Scanning_Electron_Microscope%2C_15x.GIF" width=500/></p>

Each bead is about 75 micrometers (&micro;m, "micron") in diameter.

What do these beads do? They reflect light back the way it came:

<p align=center><img src="https://illumin.usc.edu/wp-content/uploads/2022/02/image9-1-768x768.png" width=500/></p>

When you hold a flashlight near your eye and shine it at the retrorefletor, it appears quite bright.  The flashlight
needs to be pretty close to your eye for this to work.  Try it and see.

## Quantifying retroreflection

How can we assign some quantities to what we're seeing?  How much of the incident light is reflected?  How narrowly is it reflected?
The datasheet mentions a number, "Coefficient of Retroreflection," $R_A$, which is 500.  What does that number mean?

The coefficient of retroreflection represents the ratio of reflected intensity, $I_v$, (flux per angle; intuitively, the
brightness in a particular direction) to total incident flux, $\phi_v$.  Importantly, when 3M say their produce yields an $R_A$ of 500,
they specify 5&deg; entrance angle and 0.2&deg; observation angle.  What does that mean?  It means the result of a specific measurement.

It turns out that retroreflectors have been pretty thoroughly studied, because they're important for automobile safety at night.  Every road
sign includes retroreflectors indended to reflect car headlights into the eyes of the driver.  There are ASTM standards for [describing
retroreflection](https://tajhizkala.ir/doc/ASTM/E808-01%20(Reapproved%202009).pdf), for
[measuring it](https://tajhizkala.ir/doc/ASTM/E809-08%20(Reapproved%202013).pdf) (with more
[detail here](https://tajhizkala.ir/doc/ASTM/E810-03%20(Reapproved%202013).pdf)).

The measurement setup looks like this:

<p align=center><img src="https://docs.google.com/drawings/d/e/2PACX-1vTDrcVYwdwpPgKSEYGIWqTEa0Id0P5eUocKcF76txyRAHRnZBlB6afy_-YV9q4PHE2KUZp3cQmkZPG5/pub?w=960&amp;h=720" width=640/></p>

The sample, of area $A$, is illuminated from a particular angle relative to perpendicular called the "entrance angle", $\beta$.  The reflection
is measured at distance $d$ from an angle relative to the source, called the "observation angle", $\alpha$, using a detector
with aperture $A_o$.

The measurement is conceptually simple, using the "ratio" method described in the ASTM docs above.  The advantage of this method
is that there are no calibration assumptions about the detector, other than it needs to be linear, and it needs to match the photopic
response.  As long as the sample is in the FOV, the detector aperture doesn't matter very much.  There are two measurements:

1. __Reflection.__  Set the sample in place, turn on the illuminator, and measure the reflection.
This represents the total reflected __flux__ received by the detector, through the aperture, $A_o$.  Call this measurement $m_1$.
2. __Incident illuminance.__  Set the detector in place of the sample, pointing at the illuminator,
to measure the incident perpendicular illuminance.  This represents the total incident flux through the detector aperture.
Call this measurement $m_2$.

Now we can calculate the total incident flux, $\phi_v$.  The measured flux divided by the observer area is the illuminance (flux per area),
so multiply that by the actual sample area to get the total flux incident on the sample:

$$
\phi_v = \frac{m_2}{A_o} A
$$

And we can calculate the reflected intensity per unit angle, $I_v$, which is just the measured flux divided by the angle
corresponding to the aperture ($\frac{A_o}{d^2}$):

$$
I_v = m_1 \frac{d^2}{A_o}
$$

So the ratio of reflected intensity to incident flux is:

$$
R_A = \frac{I_v}{\phi_v} = \frac{m_1 \frac{d^2}{A_o}} {\frac{m_2}{A_o} A} = \frac{m_1 d^2}{m_2 A}
$$

So the ratio of 500 means that, for an incident flux of 1 lumen, the tape produces 500 cd at 0.2&deg; from the incident direction.

## Retroreflection example

Take an illuminator, for example the Cree XP-E2 "Far Red" illuminator suggested elsewhere.  It produces a pretty wide beam:

<p align=center><img src="lamp_angle.png" with=500/></p>

Let's simplify this distribution by saying that the output is pretty constant within the middle +/-45-degree cone, which is 1.84 sr, and that
represents 2/3 of the output.

Imagine a target 20cm on a side, ten meters away.  The area of 0.04 m<sup>2</sup>
represents 0.0004 sr, which is a tiny fraction, 0.000217, of the 1.84 cone.

Imagine the illuminator produces 150lm (a reasonable number for a single very bright LED), so that 100lm goes into the 1.84 sr cone.

So the target receives a total flux of 0.0217 lm. Using the $R_A$ specified by 3M, we simply multiply,
and find the reflected intensity at 0.2&deg; is about 10 cd.

Since the target area is 0.04 $m^2$ we can compute the apparent luminance of the target, which corresponds to brightness
in the camera frame: 10 cd / 0.04 m<sup>2</sup> = 250 cd / m<sup>2</sup>.

For comparison, the background reflection might be something like 50 cd / m<sup>2</sup>, and the luminaires in the frame might be 1000.

Another interesting comparison is a diffuse reflector illuminated with the same 100 lm LED.  For an ideal diffuse (lambertian)
reflector, the luminance is just the illuminance divided by pi (sr).  So starting from the flux of 0.0217 and area of 0.04 m<sup>2</sup>,
we find illuminance of 0.542 lux.  Divide
by pi to get 0.173 cd / m<sup>2</sup>.  So the retroreflector is __1500 times__ brighter than a diffuse reflector, for the same illuminance.

# Variation by observer angle

The reflectance varies strongly with observer angle: almost all the light is reflected right back at the source, so
the only way to see the reflection is for the observer to be very close to the source.  There are some old datasets
describing this phenomenon, for various generations of retroreflectors, mostly old ones, but useful to get the
shape of the relationship.  I transcribed 
[here](https://docs.google.com/spreadsheets/d/1KAr34-RtiIM0Fbr4D6O5UhFfduAykVAWz9x5DW8REuw/edit#gid=0), with some
interpolation for missing values, including for the 3M tape used by FRC:

<p align=center><img src="https://docs.google.com/spreadsheets/d/e/2PACX-1vS9LfUmscBJBon-H0nxE4GovaWxXLS5JhTjmkOymTXKV8SPol-0FX14R36codUMJB_g8UcuQVn0SOpA/pubchart?oid=1624612283&format=image" width=640/></p>

Note the $R_A$ of 500 is what is quoted by 3M, it's observed 0.2 degrees from the source. At just 0.33 degrees, $R_A$ falls by half!
For context, if a target were 3 meters away, a 0.2-degree observer would be ten __millimeters__ from the camera.  Move seven millimeters
further away, and you lose half the intensity.  This is a strong effect!  Emitters need to be as close to the camera as possible!

# Variation by entrance angle

Reflectance varies less strongly with entrance angle, covering about a factor of 2 over 40 degrees:

<p align=center><img src="https://docs.google.com/spreadsheets/d/e/2PACX-1vS9LfUmscBJBon-H0nxE4GovaWxXLS5JhTjmkOymTXKV8SPol-0FX14R36codUMJB_g8UcuQVn0SOpA/pubchart?oid=836368983&format=image" width=640/></p>

This is a relationship we should be aware of, when calculating the contrast for obliquely-illuminated targets, but it's not
the sort of show-stopper that the observer dependency above is.
