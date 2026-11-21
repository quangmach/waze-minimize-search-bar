# QM Patches

A Morphe patch for Waze Mod that hides the search sheet when you tap the map, and brings it back when you tap the map again. The original sheet height is retained; swipe up to use search. The sheet also hides after 10 seconds without interaction.

**Move & Resize Speedometer** is an experimental, optional patch. Long-press the speedometer, keep holding, and drag it. While holding, add a second finger and pinch to resize. Position and size are saved. This targets Waze's named speedometer view rather than an obfuscated version-specific class; Waze updates may still change that view or its layout.

**Add Speedometer/Speed Limit Widget** adds a circular **Waze Speedometer** widget to the Android home screen for original Waze 5.27.5.0. It starts tracking when you add it and allow precise location. Removing the active widget or turning off the screen stops tracking. Tap the dial to start again or stop. Android’s widget provider API reports placement and removal, so switching apps or home-screen pages leaves the current tracking state in place.

The widget shows GPS speed while the Waze map is closed and attaches a badge when Waze returns a posted limit. It shares the active Waze engine and stops an engine it started for itself when tracking ends and there is no independent Waze session. The badge was verified with sample readings. Live posted limits are still unverified on the emulator.

## Add to Morphe

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
