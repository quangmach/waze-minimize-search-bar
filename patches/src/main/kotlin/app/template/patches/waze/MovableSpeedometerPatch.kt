package app.template.patches.waze

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.patch
import com.android.tools.smali.dexlib2.Opcode

private object SpeedometerView : Fingerprint(
    definingClass = "Lcom/waze/view/map/SpeedometerColoredView;",
    name = "<init>",
    parameters = listOf("Landroid/content/Context;", "Landroid/util/AttributeSet;"),
)

@Suppress("unused")
val movableSpeedometerPatch = patch(
    name = "Move & Resize Speedometer",
    description = "Long-press and drag the speedometer; pinch to resize it.",
    default = false,
) {
    compatibleWith(
        Compatibility(name = "Waze", packageName = "com.waze", apkFileType = ApkFileType.APK, appIconColor = 0x33CCFF, targets = listOf(AppTarget(version = null, isExperimental = true))),
        Compatibility(name = "Waze Mod", packageName = "com.chisadin.wazemod", apkFileType = ApkFileType.APK, appIconColor = 0x33CCFF, targets = listOf(AppTarget(version = null, isExperimental = true))),
    )
    extendWith("extensions/extension.mpe")
    execute {
        val method = SpeedometerView.method
        val returns = method.implementation!!.instructions.withIndex().filter { it.value.opcode == Opcode.RETURN_VOID }
        check(returns.size == 1) { "Speedometer constructor changed; refusing to patch." }
        method.addInstructions(returns.single().index, "invoke-static {p0}, Lapp/template/extension/extension/MovableSpeedometer;->register(Landroid/view/View;)V")
    }
}
