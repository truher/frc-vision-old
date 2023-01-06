# Safety

Radiation of sufficient intensity of any wavelength can dangerous.  

* [Very long-wave radiation](https://www.fcc.gov/engineering-technology/electromagnetic-compatibility-division/radio-frequency-safety/faq/rf-safety)
(e.g. radio waves) can heat parts of the body not accustomed to being heated.
* [Very short-wave radiation](https://www.osha.gov/laws-regs/regulations/standardnumber/1910/1910.1096)
(e.g. x-rays) can directly damage molecules in the body, which can increase cancer risk.
* Ultraviolet light obviously can cause sunburn.
* [Infrared light](https://en.wikipedia.org/wiki/Glassblower%27s_cataract)
can cause cataracts after tens of thousands of hours of exposure.
* Even [visible blue light](https://www.sciencedirect.com/science/article/pii/S0753332220307708)
in high doses can affect the eye through a number of mechanisms, for example retinal inflammation.

When you're building a device that emits radiation of any kind, you need to understand these hazards,
and make sure that your device won't cause injury to any people nearby.

## Safety Standards

There are several equivalent standards (see appendix); we'll be working through the method specified by the
International Commission on Non-Ionizing Radiation Protection (ICNIRP),
[ICNIRP Guidelines on Limits of Exposure to Incoherent Visible and Infrared Radiation."](https://www.icnirp.org/cms/upload/publications/ICNIRPVisible_Infrared2013.pdf).
I encourage you to read and understand the
guidelines in total (it's only 26 pages).
I'll try to limit discussion here to implications of the guidelines, rather than covering everything.
The math is covered in [this python notebook](https://colab.research.google.com/drive/1T6OjM1fbkWaqULhcl7OmBOcejc7mjztV?usp=sharing).

## Types of harm

There are eight hazards to evaluate (TODO: use the ordering from ICNIRP)

1. [Actinic](https://www.ncbi.nlm.nih.gov/books/NBK401580/) UV, 200-400 nm (irradiance)
2. UVA, 315-400 nm (irradiance)
3. Blue light, 300-700 nm (radiance)
4. Blue light small source, 300-700 nm (irradiance)
5. Thermal, 380-1400 nm (radiance)
6. Thermal invisible, 780-1400 nm (radiance)
7. Infrared 780-3000 nm (irradiance)
8. Thermal skin, 380-3000 nm (irradiance)

Some of the hazards involve harm to the surface of the body (e.g. cornea, skin), and these depend on the
["irradiance"](https://en.wikipedia.org/wiki/Irradiance) of that body surface, which means the total amount of light per area.
Other hazards involve harm to the retina, where the light is focused, and these generally depend on 
["radiance"](https://en.wikipedia.org/wiki/Radiance) (loosely, apparent "brightness") of the source.

## Classification of risk

For each of the hazards above, an emitter may be classified into one of four groups according to the IEC standard:

1. __Exempt__: no hazard
2. __Group 1 (low risk)__: no hazard assuming normal human behavior
3. __Group 2 (moderate risk)__: no hazard due to aversion response
4. __Group 3 (high risk)__: hazardous even for momentary exposure

We will be designing for group 1: no hazard at all, for all hazards, because we can't restrict or police the population of bystanders to be sure
their behavior is "normal."

## The design

For the purposes of this safety analysis, the illuminator design has several parameters:

* wavelength
* strobe duration and duty cycle
* surface brightness per emitter
* total output

## Retinal thermal hazards (380&ndash;1,400 nm)

Some retinal hazards vary with wavelength, as described in the "hazard functions", table 2 and figure 5 in the doc, duplicated here:

<p align=center><img src="https://drive.google.com/uc?export=view&id=1ZXQJZHQRQGgV23JVudl0XwG051RoYHag" width=640/></p>

For thermal hazards, weigh the spectral radiance by the "Thermal" hazard function above and integrate, to get the __effective retinal thermal radiance__,
$L_R$ (W m<sup>-2</sup> sr<sup>-1</sup>).  For any source we might use, the thermal hazard function is 1.0, so the source radiance
can be used directly.

For the [Cree XP-E2 LEDs](https://cree-led.com/media/documents/XLampXPE2.pdf) we have in mind to use,
the radiant flux is specified at 350mA, between 350 and 675 mW, depending on color.  The correction for maximum continuous current (1 amp)
is about 275%, i.e. the output is close to linear with current, the 350&ndash;675 mW output might be 1000&ndash;2000 mW.
The [overdriving guidance](https://cree-led.com/media/documents/XLampPulsedCurrent.pdf)
says that the luminous efficiency at 2.5X maximum current is something like 60% of the 1X maximum; extrapolating, we can estimate that
the efficiency at 3X is half the 1X efficiency.  Increased forward voltage is one of the drivers of the drop in efficiency; extrapolating
slightly from the "typical" table, we find a 24% forward voltage increase from 1X to 3X current, which implies that
the quantum efficiency of 3X overdriving is about 72% of the QE of the 1X case.

In short, triple the current, double the photons, which means that we can double the source radiance for our maximum 3X operating point.

The package of an XP-E2 is a square 3.45 mm on a side, with a hemispherical lens that makes the die appear something like a quarter of the area,
so say the apparent emitter size is about 3 mm<sup>2</sup>.

As described elsewhere (TOOD) about two thirds of the output is focused into about one steradian, so our 1000&ndash;2000 mW becomes a radiant
intensity of about 666-1333 mW/sr, and a __radiance of about 2&ndash;4 MW m<sup>-2</sup> sr<sup>-1</sup>.__  For a 4 ms pulse,
the __radiance dose is 8&ndash;16 kJ m<sup>-2</sup> sr<sup>-1</sup>.__  
We also need to calculate the average radiance, using the duty cycle of 10% or so that we have in mind, so we find 
an __average radiance of 200&ndash;400 KW m<sup>-2</sup> sr<sup>-1</sup>.__

TODO: move this discussion to the illuminator page, just report these results.

1. __radiance = 2&ndash;4 MW m<sup>-2</sup> sr<sup>-1</sup>.__  
2. __radiance dose = 8&ndash;16 kJ m<sup>-2</sup> sr<sup>-1</sup> per pulse__  
3. __average radiance = 200&ndash;400 KW m<sup>-2</sup> sr<sup>-1</sup>.__

To find the exposure limit, we use the angle, $\alpha$ subtended by the emitter, which is just the diameter (about 1.75mm) divided by the distance.
A reasonable worst case would be to look closely at the source, say 200 mm away, so the angle would be something like 0.01 radians.
A pathological case would be for someone to stare directly into the illuminator, at a range of, say, 10 mm, 0.175 radians.  These are larger
than the "point source" limit of 0.0015 radians; the latter is larger than the "large source" limit.

Another input to the exposure limit is the duration of the dose.  For the illuminator we have in mind, the intended duration something like 4 ms,
but it could fail and produce steady light at half the intensity (if it failed at the pulse intensity it would quickly stop working altogether).
For the 4 ms case, the limiting angles are 0.0015-0.012 radians. for the steady case, the limiting angles are 0.0015-0.1 radians.

With these inputs we can calculate the basic radiance limit for intermediate duration, using t = 0.004 and $\alpha$ of 0.01 rad, we obtain

$$
L_R^{EL} = 2.0 \times 10^4 \cdot \alpha^{-1} \cdot t^{-0.25} = 7.95  MW m^{-2} sr^{-1}
$$

Which is above the calculated radiance of 2&ndash;4 MW m<sup>-2</sup> sr<sup>-1</sup>.

The basic radiance dose limit, $D_R^{EL}$ is calculated:

$$
D_R^{EL} = 2.0 \times 10^4 \cdot \alpha^{-1} \cdot t^{0.75} = 31.8  KJ m^{-2} sr^{-1}
$$

which is above the calculated radiance dose of 16 KJ m<sup>-2</sup> sr<sup>-1</sup>._.

We also need to look at the average radiance for long exposures,

$$
L_R^{EL} (W m^{-2} sr^{-1}) = 2.8 \times 10^4 \cdot \alpha^{-1} = 2.8 MW m^{-2} sr^{-1}
$$

which is much more than the calculated average radiance.

For the continuous output (malfunction) case, the output is about half (at most) about 1&ndash;2 MW m<sup>-2</sup> sr<sup>-1</sup>,
and the exposure limits are as follows:

$$
L_R^{EL} (W m^{-2} sr^{-1}) = 2.8 \times 10^4 \cdot \alpha^{-1} = 2.8 MW m^{-2} sr^{-1}
$$

Which is above the calculated radiance of 1&ndash;2 MW m<sup>-2</sup> sr<sup>-1</sup>.

For the pathological pulsed case, we use the large-source dose limit,

$$
D_R^{EL} = 10 \times 10^4 \cdot t^{0.25} = 25.2  KJ m^{-2} sr^{-1}
$$

Which is still above the calculated dose of 16 kJ m<sup>-2</sup> sr<sup>-1</sup>.

For the pathological case, the average large-source radiance limit is 280 KW m<sup>-2</sup> sr<sup>-1</sup>.  Our
calculated average radiance (200 Kw m<sup>-2</sup> sr<sup>-1</sup>) __exceeds this limit__.

For the pathological steady case, we use the large-source radiance limit:

$$
L_R^{EL} = 28 \times 10^4 = 280  KW m^{-2} sr^{-1}
$$

Our calculated radiance of 1&ndash;2 MW m<sup>-2</sup> sr<sup>-1</sup> __exceeds this limit__.

Mitigations of the pathological steady case might include

* restricting the distance from eye to LEDs, with some sort of shroud. it would need to be pretty big, on the order of 4 cm.
* diffusing the illuminator.  a [diffuser](https://www.rpcphotonics.com/pdfs/Optical_Diffuser_Technologies_Final_030215.pdf)
is very common in LED designs, and it can be quite small.  examples:
  * [ground glass diffuser](https://www.edmundoptics.in/f/ground-glass-diffusers/12287/) ($15)
  * [film diffuser](https://www.edmundoptics.com/p/300-x-300mm-Light-Diffusing-Film/45303)  
  * reflective diffuser, i.e. white paper.
  * a frosted optic.  these also reduce the FOV of the illuminator to something like 40 degrees which i think is too narrow.
* a more aggressive current limiter for the "steady light" case.

One of the illuminator options is near-infrared, which requires more a conservative steady limit called the "weak visual stimulus" limit,
which is simply to apply the intermediate-duration limit for exposures under 100s.  For exposures over 100s, the limit is

$$
L_{WVS}^{EL} = 6300 \times \alpha^{-1} = 630 KW m^{-2} sr^{-1} \textrm{(at 200 mm)}
$$

$$
L_{WVS}^{EL} = 6300 \times \alpha^{-1} = 36 KW m^{-2} sr^{-1} \textrm{(at 10 mm, pathological case)}
$$

Our calculated radiance  __exceeds this limit__ in all cases.  In the pathological case, our calculated radiance
is __one hundred times the limit__.

The definition of "weak visual stimulus" is wavelengths over 780 nm, which is higher than the "Far Red" lamp wavelength of
720-740 nm, but only a little.  I think we should be conservative and avoid the 720-740 nm range.The "Photo Red" range
seems acceptable at 650-670 nm.

## Blue-light photochemical retinal hazard (300 - 700 nm)

Similarly to the thermal case above, the hazard weighting function, $B(\lambda)$ is used to calculate an __effective blue-light radiance.__

The hazard function varies by three orders of magnitude over the spectrum we're considering; let's consider four options:

1. blue (435 nm) $B(\lambda)$ = 1.0
1. cyan (500 nm) $B(\lambda)$ = 0.1
1. green (550 nm) $B(\lambda)$ = 0.01  
1. orange and beyond (590+ nm) $B(\lambda)$ = 0.001

Accordingly we have effective radiances ranging from 2&ndash;4 MW m<sup>-2</sup> sr<sup>-1</sup> (blue)
to 2&ndash;4 KW m<sup>-2</sup> sr<sup>-1</sup> (orange), and effective doses ranging from 8&ndash;16 kJ m<sup>-2</sup> sr<sup>-1</sup> (blue)
to 8&ndash;16 J m<sup>-2</sup> sr<sup>-1</sup> (orange).

For short exposures, the blue-light exposure limit is expressed as a radiance dose:

$$
D_B^{EL} = 1 MJ m^{-2} sr^{-1}
$$

which is much higher than our radiance dose per pulse, even for blue light.

For the steady case, the exposure limit can be expressed as a duration:

1. blue: 0.5-1 s
2. cyan: 5-10 s
3. green: 50-100 s
4. orange: 500-1000 s

For very long exposures (>10000 s), the exposure limit is expressed as a radiance:

$$
L_B^{EL} = 100 W m^{-2} sr^{-1}
$$

Our design radiance __exceeds this limit__, even for orange light, which means that very long term
exposure should be curtailed.

## Retinal photochemical hazard to the aphakic eye and the infant eye (300 - 700 nm)

For this case, the hazard function, $A(\lambda)$ is up to 6 times higher than $B(\lambda)$, but the calculation is otherwise the same.

## Cornea and lens (780 nm - 1 mm)

In the normal case of 200 mm observation (400 cm<sup>2</sup> area of one steradian) the irradiance is something like 
0.666-1.333 W/sr / 0.04 <sup>2</sup> or 17 - 33 W m<sup>-2</sup>.
For the pathological case, 0.666-1.333 W/sr / 0.0001 m<sup>2</sup> = 6.6-24.4 KW m<sup>-2</sup>
 
The irradiance limit is 100 W m<sup>-2</sup> for long exposures.  Our calculated irradiance is within the limit for the normal case,
but it  __exceeds the limit__ for the pathological case.

TODO: the pulsed case

## Visible and infrared thermal injury to the skin

TODO: write this case

<hr>

## Appendix

* ICNIRP provided a special [statement on LEDs](https://www.icnirp.org/cms/upload/publications/ICNIRPled2020.pdf)
which reviews some of the hazards specific to LEDs and recommends that LEDs be analyzed using the incoherent broadband standards used above.
* ICNIRP first published guidelines in 1997,
[Guidelines on Limits of Exposure to Broad-band Incoherent Optical Radiation (0.38 to 3 &micro;m)](https://www.icnirp.org/cms/upload/publications/ICNIRPbroadband.pdf)
* The same calculations are also available from IEC/CIE as [IEC 62471, "Photobiological Safety of Lamps and Lamp Systems."](https://cie.co.at/publications/photobiological-safety-lamps-and-lamp-systems-s-curit-photobiologique-des-lampes-et-des),
which is [summarized here](https://smartvisionlights.com/wp-content/uploads/IEC_62471_summary.pdf).
* Another version of the same method is available from ANSI as [ANSI/IESNA RP-27, "Recommended Practice for Photobiological Safety for
Lamps and Lamp Systems-Measurement Techniques"](https://webstore.ansi.org/preview-pages/IESNA/preview_ANSI+IESNA+RP-27.2-00.pdf)
* Blue light hazard
  * Given recent attention to blue light hazard, the CIE clarified that blue light is
  [not a hazard for everyday situations](https://cie.co.at/publications/position-statement-blue-light-hazard-april-23-2019)
  but also pointed out that, for people who might not behave "normally" (i.e. a child who might stare at a blue light for a long 
  time because they find it fascinating), the (non-exempt) exposure limits should be __reduced by a factor of ten.__  
  * Cree provides [eye safety guidance](https://cree-led.com/media/documents/XLamp_EyeSafety.pdf), based on their own testing,
  which focuses on blue light (400-480 nm).  They
  classify the XP-E2 in the "moderate risk" category ("no hazard due to aversion response") when driven with the 1X current maximum.
  They also find that the transition from "low risk" to "moderate risk" happens at around 0.1 amps, and that the maximum safe distance
  is something like 200-500 mm.  
