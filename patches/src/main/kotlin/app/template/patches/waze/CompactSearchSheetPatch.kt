package app.template.patches.waze

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.patch
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction

private object SearchSheetHeightFingerprint : Fingerprint(
    definingClass = "Lcom/waze/bw/c/d/cv;",
    name = "a",
    parameters = listOf("Z"),
    returnType = "F",
)

@Suppress("unused")
val compactSearchSheetPatch = patch(
    name = "Waze Minimize Search Bar",
    description = "Reduce the collapsed search sheet to a 24 dp handle; swipe up to expand it.",
    default = true,
) {
    compatibleWith(Compatibility(
        name = "Waze Mod",
        packageName = "com.chisadin.wazemod",
        apkFileType = ApkFileType.APK,
        appIconColor = 0x33CCFF,
        targets = listOf(AppTarget(version = "5.24.5.0")),
    ))

    execute {
        val method = SearchSheetHeightFingerprint.method
        val literals = method.implementation!!.instructions
            .mapNotNull { (it as? NarrowLiteralInstruction)?.narrowLiteral }
        check(0x42a00000 in literals && 0x42dc0000 in literals) {
            "Unexpected search-sheet height calculation; this APK build is not supported"
        }
        // Used by the portrait and landscape start-sheet anchor updates.
        // Waze's separate navigation-bar inset and expanded anchors remain intact.
        method.addInstructions(0, """
            const/high16 p0, 0x41c00000
            return p0
        """.trimIndent())
    }
}
