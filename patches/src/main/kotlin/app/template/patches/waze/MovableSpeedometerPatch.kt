package app.template.patches.waze

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.patch

private object SpeedometerView : Fingerprint(
    definingClass = "Lcom/waze/view/map/SpeedometerColoredView;",
    name = "<init>",
    parameters = listOf("Landroid/content/Context;", "Landroid/util/AttributeSet;"),
)
private object MainActivityResume : Fingerprint(
    definingClass = "Lcom/waze/MainActivity;", name = "onResume", parameters = emptyList(),
)

@Suppress("unused")
val movableSpeedometerPatch = patch(
    name = "Advanced Speedometer",
    description = "Move and resize the in-app speedometer. Double-tap to swap the speedometer and speed limit.",
    default = false,
) {
    compatibleWith(
        Compatibility(name = "Waze", packageName = "com.waze", apkFileType = ApkFileType.APK, appIconColor = 0x33CCFF, targets = listOf(AppTarget(version = null, isExperimental = true))),
        Compatibility(name = "Waze Mod", packageName = "com.chisadin.wazemod", apkFileType = ApkFileType.APK, appIconColor = 0x33CCFF, targets = listOf(AppTarget(version = null, isExperimental = true))),
    )
    extendWith("extensions/extension.mpe")
    execute {
        // Check the named widget exists, then install on the map activity itself.
        // The widget can be constructed with an application context, which has no window.
        SpeedometerView.method
        val method = MainActivityResume.method
        // At entry, p0 is guaranteed to be the Activity; Waze reuses that register later.
        method.addInstructions(0, "invoke-static {p0}, Lapp/template/extension/extension/MovableSpeedometer;->install(Landroid/app/Activity;)V")
    }
}
