# QM Patches

A Morphe patch for Waze Mod that hides the search sheet when you tap the map, and brings it back when you tap the map again. The original sheet height is retained; swipe up to use search. The sheet also hides after 10 seconds without interaction.

**Move & Resize Speedometer** is an experimental, optional patch. Long-press the speedometer, keep holding, and drag it. While holding, add a second finger and pinch to resize. The phone and secondary displays keep separate portrait and landscape placements. This targets Waze's named speedometer view rather than an obfuscated version-specific class; Waze updates may still change that view or its layout.

**Add Speedometer/Speed Limit Widget** adds a circular **Waze Speedometer** widget to the Android home screen for original Waze 5.27.5.0. In manual mode, tracking starts when you add the widget and allow precise location. Removing the active widget or turning off the screen stops manual tracking. Tap the dial to start again or stop. Android’s widget provider API reports placement and removal, so switching apps or home-screen pages leaves the current tracking state in place.

The widget renders Waze's own speedometer layout and speed-limit sign. It shows GPS speed while the Waze map is closed and attaches the sign when Waze returns a posted limit. It shares the active Waze engine and stops an engine it started for itself when tracking ends and there is no independent Waze session. The badge was verified with sample readings. Live posted limits are still unverified on the emulator.

Automatic trip tracking is on by default. Adding the widget asks for any missing permissions and then waits for a trip. For an existing widget that needs access, tap the dial and choose **Allow permissions**. You can turn off automatic tracking from the widget controls.

An Android Auto projection session or a paired Bluetooth device that reports Car Audio or a Bluetooth LE motorized-vehicle type can start tracking. Android Auto's event is available while Waze's process is running. If Android has stopped that process or blocks a background start, Bluetooth and Google Play services' **In vehicle** detection can still start tracking. A Bluetooth disconnection has a 15-second grace period. Automatic trips continue when the phone is asleep or locked. The screen-off rule for manual tracking follows Android's device-wide sleep state.

Grant **Nearby devices** for Bluetooth detection and **Physical activity** for the motion fallback. In Waze's Location permissions, enable precise location and choose **Allow all the time** so a trip can start while the app is in the background. Waze already declares Bluetooth connection and background location permissions. The patch adds the Physical activity permission.

## Add to Morphe

**Add navigation and alert widget** adds a separate **Waze Navigation & Alerts** home-screen widget for original Waze 5.27.5.0. It shows the next maneuver and distance, current speed, posted maximum speed, and up to four nearby Waze alerts. The widgets share one tracking service and Waze engine. Upcoming warnings can be spoken through Waze’s audio when its guidance mode permits alerts. The widget can display guidance once a route starts in Waze.

In Morphe Manager, open **Sources**, add a source, and enter:

`https://github.com/quangmach/waze-minimize-search-bar`

Select the **Waze Minimize Search Bar** patch. Supported builds are Waze Mod `com.chisadin.wazemod` 5.24.5.0 and original Waze `com.waze` 5.24.5.0 and 5.27.5.0. Original Waze support is experimental. Other versions may require a new profile.

The bundle was applied to all three target APKs and its resulting bytecode was inspected. The tap, timeout and touch behavior have not yet been tested on a phone. Re-signing with a different key may require uninstalling an existing installation and can remove its data.

The speedometer patch applied successfully to original Waze 5.24.5.0 and 5.27.5.0 and Waze Mod 5.24.5.0, but its gestures have not yet been tested on a device. It is listed as experimental for Waze and Waze Mod versions and is disabled by default; enable experimental app versions and select it in Morphe.

The repository contains patch source only; it does not distribute Waze or its APK.

## Build

Use JDK 21, the Android SDK, and Morphe's Gradle dependencies. Run `./gradlew buildAndroid`; the output is under `patches/build/libs/`. GitHub Packages access may require credentials. Optional sibling checkouts of Morphe's patcher and Gradle plugin can be used instead, as configured in `settings.gradle.kts`.

Based on Morphe's patches template. Licensed under GPL-3.0; see LICENSE and NOTICE.

**Remove Car mode when Waze inactive on Android Auto** keeps the phone map and shared destinations on the phone while another app is visible on Android Auto. Shared destinations open on the display where the Waze map is already running. This optional experimental patch targets original Waze 5.27.5.0 and was tested with the Android Auto Desktop Head Unit and an emulator.
