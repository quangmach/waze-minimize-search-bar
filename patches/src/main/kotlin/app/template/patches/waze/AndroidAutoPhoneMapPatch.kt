package app.template.patches.waze

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.patch
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction35c
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val PHONE_MODEL = "Lcom/waze/es;"
private const val FLOW = "Lkotlinx/coroutines/b/j;"
private const val CONNECTION = "Lcom/b/b/a/u;"
private const val EXTENSION = "Lapp/template/extension/extension/AndroidAutoPhoneMap;"

private object PhoneViewModel : Fingerprint(
    definingClass = PHONE_MODEL,
    name = "<init>",
    parameters = listOf(
        "Lcom/waze/notifications/a/a;",
        "Lcom/waze/app_nav/w;",
        "Lcom/waze/planned_drive/PlannedDriveNativeManager;",
        "Lcom/waze/install/t;",
        "Lcom/waze/authentication/az;",
        "Lkotlinx/coroutines/b/cc;",
    ),
)

private object CarSessionEvent : Fingerprint(
    definingClass = "Landroidx/car/app/Session;",
    name = "handleLifecycleEvent",
    parameters = listOf("Landroidx/lifecycle/Lifecycle\$Event;"),
    returnType = "V",
)

@Suppress("unused")
val androidAutoPhoneMapPatch = patch(
    name = "Remove Car mode when Waze inactive on Android Auto",
    description = "Use Waze’s phone map while Android Auto is connected and Waze is off the car screen.",
    default = false,
) {
    compatibleWith(Compatibility(name = "Waze", packageName = "com.waze", targets = listOf(
        AppTarget(version = "5.27.5.0", isExperimental = true),
    )))
    extendWith("extensions/extension.mpe")

    execute {
        val session = CarSessionEvent.method
        val viewModel = PhoneViewModel.method
        val calls = viewModel.implementation!!.instructions.mapIndexedNotNull { index, instruction ->
            val reference = (instruction as? ReferenceInstruction)?.reference as? MethodReference
            val call = instruction as? Instruction35c
            if (reference == null || call == null) null else Triple(index, call, reference)
        }
        val (stateIndex, initialCall) = calls.single { (_, _, reference) ->
            reference.definingClass == PHONE_MODEL && reference.name == "s" &&
                reference.parameterTypes.map { it.toString() } == listOf("Lcom/waze/install/s;", "Z", CONNECTION) &&
                reference.returnType == "Lcom/waze/el;"
        }
        val (flowIndex, combineCall) = calls.single { (_, _, reference) ->
            reference.definingClass == "Lkotlinx/coroutines/b/af;" && reference.name == "t" &&
                reference.parameterTypes.map { it.toString() } == listOf(FLOW, FLOW, FLOW, "Lg/h/a/r;") &&
                reference.returnType == FLOW
        }

        // Update later instructions first to preserve the indices above.
        check(stateIndex > flowIndex)
        val state = "v${initialCall.registerF}"
        viewModel.addInstructions(stateIndex, """
            invoke-static {$state}, $EXTENSION->initialConnection(Ljava/lang/Object;)Ljava/lang/Object;
            move-result-object $state
            check-cast $state, $CONNECTION
        """)
        val flow = "v${combineCall.registerE}"
        viewModel.addInstructions(flowIndex, """
            invoke-static {$flow}, $EXTENSION->phoneConnectionFlow(Ljava/lang/Object;)Ljava/lang/Object;
            move-result-object $flow
            check-cast $flow, $FLOW
        """)
        session.addInstructions(0, "invoke-static {p0, p1}, $EXTENSION->onSessionEvent(Ljava/lang/Object;Ljava/lang/Enum;)V")
    }
}
