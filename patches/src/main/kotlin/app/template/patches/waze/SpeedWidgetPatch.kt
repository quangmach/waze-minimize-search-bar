package app.template.patches.waze

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.document
import app.morphe.patcher.patch.mutableClassDefBy
import app.morphe.patcher.patch.patch
import app.morphe.patcher.patch.resourceFile
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import org.w3c.dom.Element

private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
private const val LOCATION_LISTENER = "Lcom/waze/location/LocationSensorListener;"
private const val WIDGET_PACKAGE = "app.template.extension.extension"
private const val WIDGET_SERVICE = "Lapp/template/extension/extension/SpeedWidgetService;"

private object WazeSpeedUpdate : Fingerprint(
    definingClass = LOCATION_LISTENER,
    name = "updateSpeedometer",
    parameters = listOf("I", "Ljava/lang/String;", "I", "D"),
    returnType = "V",
)

private object WazeLastLocation : Fingerprint(
    definingClass = LOCATION_LISTENER,
    name = "getLastLocation",
    parameters = emptyList(),
    returnType = "Landroid/location/Location;",
)

private object WazeEngineInitializer : Fingerprint(
    definingClass = "Lcom/waze/ls;",
    name = "d",
    parameters = listOf("Lg/d/e;"),
    returnType = "Ljava/lang/Object;",
)

private object WazeEngineStarted : Fingerprint(
    definingClass = "Lcom/waze/NativeManager;",
    name = "isAppStarted",
    parameters = emptyList(),
    returnType = "Z",
)

private fun Element.androidAttribute(name: String, value: String) =
    setAttributeNS(ANDROID_NS, "android:$name", value)

private val speedWidgetResources = patch {
    execute {
        listOf(
            "layout/qm_speed_widget.xml",
            "xml/qm_speed_widget_info.xml",
        ).forEach { name ->
            val file = resourceFile("res/$name", false)
            file.parentFile.mkdirs()
            checkNotNull(WazeSpeedUpdate.javaClass.getResourceAsStream("/speed-widget/$name")).use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
        }

        document("AndroidManifest.xml").use { manifest ->
            val application = manifest.getElementsByTagName("application").item(0) as Element
            val existingPermissions = manifest.getElementsByTagName("uses-permission")
            listOf(
                "ACCESS_COARSE_LOCATION",
                "ACCESS_FINE_LOCATION",
                "FOREGROUND_SERVICE",
                "FOREGROUND_SERVICE_LOCATION",
                "POST_NOTIFICATIONS",
            ).forEach { name ->
                val permission = "android.permission.$name"
                if ((0 until existingPermissions.length).none {
                        (existingPermissions.item(it) as Element).getAttributeNS(ANDROID_NS, "name") == permission
                    }) {
                    manifest.documentElement.insertBefore(manifest.createElement("uses-permission").apply {
                        androidAttribute("name", permission)
                    }, application)
                }
            }

            application.appendChild(manifest.createElement("receiver").apply {
                androidAttribute("name", "$WIDGET_PACKAGE.SpeedWidget")
                androidAttribute("exported", "false")
                androidAttribute("label", "Waze Speedometer")
                appendChild(manifest.createElement("intent-filter").apply {
                    appendChild(manifest.createElement("action").apply {
                        androidAttribute("name", "android.appwidget.action.APPWIDGET_UPDATE")
                    })
                })
                appendChild(manifest.createElement("meta-data").apply {
                    androidAttribute("name", "android.appwidget.provider")
                    androidAttribute("resource", "@xml/qm_speed_widget_info")
                })
            })
            application.appendChild(manifest.createElement("service").apply {
                androidAttribute("name", "$WIDGET_PACKAGE.SpeedWidgetService")
                androidAttribute("exported", "false")
                androidAttribute("foregroundServiceType", "location")
            })
            application.appendChild(manifest.createElement("activity").apply {
                androidAttribute("name", "$WIDGET_PACKAGE.SpeedWidgetControls")
                androidAttribute("exported", "false")
                androidAttribute("excludeFromRecents", "true")
                androidAttribute("taskAffinity", "")
                androidAttribute("theme", "@android:style/Theme.Material.Dialog.Alert")
            })
        }
    }
}

@Suppress("unused")
val speedWidgetPatch = patch(
    name = "Add Speedometer/Speed Limit Widget",
    description = "Adds a home-screen speed dial with live GPS speed and a Waze road-limit badge.",
    default = false,
) {
    compatibleWith(
        Compatibility(name = "Waze", packageName = "com.waze", targets = listOf(AppTarget("5.27.5.0"))),
    )
    extendWith("extensions/extension.mpe")
    dependsOn(speedWidgetResources)
    execute {
        val location = WazeLastLocation.method
        val update = WazeSpeedUpdate.method
        val initializer = WazeEngineInitializer.method
        val started = WazeEngineStarted.method
        check(update.implementation!!.registerCount > 6)
        update.addInstructions(0, """
            invoke-virtual/range {p0 .. p0}, ${location.definingClass}->${location.name}()Landroid/location/Location;
            move-result-object v0
            invoke-static {p2, p3, v0}, $WIDGET_SERVICE->onWazeLimit(Ljava/lang/String;ILandroid/location/Location;)V
        """)

        val startup = initializer.implementation!!.instructions
        val gate = startup.indexOfFirst { instruction ->
            val call = (instruction as? ReferenceInstruction)?.reference as? MethodReference
            call?.definingClass == "Ljava/util/concurrent/atomic/AtomicBoolean;" && call.name == "getAndSet"
        }
        check(gate >= 0 && startup[gate + 1].opcode == Opcode.MOVE_RESULT && startup[gate + 2].opcode == Opcode.IF_NEZ)
        initializer.addInstructions(gate + 3, """
            invoke-static/range {p1 .. p1}, $WIDGET_SERVICE->onWazeEngineInitializing(Ljava/lang/Object;)V
        """)

        // Reuse the active engine. For overlapping cold starts, Waze's initializer
        // uses its own atomic gate and shared completion to run the startup only once.
        val service = mutableClassDefBy(WIDGET_SERVICE)
        val stub = service.methods.single { it.name == "startWazeEngine" }
        val launch = ImmutableMethod(
            stub.definingClass,
            stub.name,
            stub.parameters,
            stub.returnType,
            stub.accessFlags,
            stub.annotations,
            stub.hiddenApiRestrictions,
            MutableMethodImplementation(3),
        ).toMutable()
        launch.addInstructionsWithLabels(0, """
            invoke-static {}, ${started.definingClass}->${started.name}()Z
            move-result v0
            if-nez v0, :ready
            check-cast p0, ${initializer.definingClass}
            const-class v0, Lg/d/e;
            sget-object v1, Lg/d/k;->a:Lg/d/k;
            invoke-static {v0, v1, p0}, $WIDGET_SERVICE->newContinuation(Ljava/lang/Class;Ljava/lang/Object;Landroid/app/Application;)Ljava/lang/Object;
            move-result-object v0
            check-cast v0, Lg/d/e;
            invoke-virtual {p0, v0}, ${initializer.definingClass}->${initializer.name}(Lg/d/e;)Ljava/lang/Object;
            :ready
            return-void
        """)
        service.methods.remove(stub)
        service.methods.add(launch)
    }
}
