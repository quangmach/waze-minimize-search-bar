# Waze Minimize Search Bar

A Morphe patch for Waze Mod that hides the search sheet when you tap the map, and brings it back when you tap the map again. The original sheet height is retained; swipe up to use search. The sheet also hides after 10 seconds without interaction.

## Add to Morphe

In Morphe Manager, open **Sources**, add a source, and enter:

`https://github.com/quangmach/waze-minimize-search-bar`

Select the **Waze Minimize Search Bar** patch when patching Waze Mod. The patch targets package `com.chisadin.wazemod`, version `5.24.5.0` (version code `1030732`). It does not target the official Waze package.

The bundle was applied to the target APK and its resulting bytecode was inspected. The tap, timeout and touch behavior have not yet been tested on a phone. Re-signing with a different key may require uninstalling an existing installation and can remove its data.

The repository contains patch source only; it does not distribute Waze or its APK.

## Build

Use JDK 21, the Android SDK, and Morphe's Gradle dependencies. Run `./gradlew buildAndroid`; the output is under `patches/build/libs/`. GitHub Packages access may require credentials. Optional sibling checkouts of Morphe's patcher and Gradle plugin can be used instead, as configured in `settings.gradle.kts`.

Based on Morphe's patches template. Licensed under GPL-3.0; see LICENSE and NOTICE.
