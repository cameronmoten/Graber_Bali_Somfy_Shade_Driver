# Graber, Bali, or Somfy Shade Driver
 Hubitat Driver For Graber, Bali or Somfy Virtual Cord Z-Wave Shades

- 2.1 Author : Evan Callia (@evcallia on Hubitat Community Forums)
- 2.0 Author : Cameron Moten (@CamM on Hubitat Community Forums)
- 1.0 Author: Tim Yuhl (@WindowWasher on the Hubitat Community Forums)

**Features:**

1. Supports Open, Close, On, Off, Set Position, Start Position Change, Stop Position Change, Set Level Change, and Stop Level Change commands 
2. Battery Level reporting
3. Tested with Rule Machine, webCoRE and Hubitat Alexa integration

**New Features Version 2.1:**
1. Add "opening" and "closing" status
2. Handle "Start Level Change" and "Stop Level Change" for full dimmer compatibility

**New Features Version 2.0:**
1. Added Ability for it to act like a Dimmer Switch in HomeKit/Hubitat (Set % Open via HomeKit or Hubitat App Natively)
2. Added Ability to Set MIN & MAX Open Values to not allow it to exceed a certain Up & Down Max per Blind so users don't have to set it per window with a remote.
3. Add Support if WindowShadeLevel is ever added to Hubitat like SmartThings.


**To use this driver:**

1. Install the driver code in the Drivers Code section of Hubitat.
2. If you already have Graber Bali or Somfy shades installed, switch each of their drivers to use the Graber Somfy Shade Driver.
3. If you are pairing new shades, this driver should be automatically selected during the inclusion process if this driver is installed prior to pairing the new shades.

**_Please report any problems or bugs to the developer_**


**Notes:**

1. It has only been tested with the Graber Virtual Cord Shades and Bali Roller Shades, but Somfy/Spring Window Fashions is the Zwave device which is shared across products so it should work for any that match. But no guarantee on all shades.

2. Secure Z-Wave communication is not currently supported. The Graber shades don't pair with security, so this shouldn't be an issue with normal installations.

3. The **Min/Max Software Limits does NOT override the Hardware set limits** you set with the remote controller. It only operates within the hardware/remote set bounds, so **you can only make the bounds SMALLER** but not larger than the hardware/remote set limits.
-- If you use a hardware Z-Wave remote or click manually on the blinds, and don't use hubitat it will also ignore the hubitat set limits.

Forum Link:
https://community.hubitat.com/t/release-graber-bali-somfy-virtual-cord-shade-driver-v2-0-dimmer-max-min-limits-battery/119774

Has Pictures and Instructions in forum.
