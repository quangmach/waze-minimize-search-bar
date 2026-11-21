package app.template.patches.waze

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.patch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction35c
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

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

private object CarConnectionUpdate : Fingerprint(
    definingClass = "Lcom/waze/car_lib/platform/ai;",
    name = "f",
    parameters = listOf(CONNECTION),
    returnType = "V",
)

private object SharedTextIntent : Fingerprint(
    definingClass = "Lcom/waze/in;",
    name = "a",
    parameters = listOf("Landroid/content/Intent;"),
    returnType = "Z",
)

private object DestinationIntent : Fingerprint(
    definingClass = "Lcom/waze/cn;",
    name = "a",
    parameters = listOf("Landroid/content/Intent;", "Lcom/waze/co;"),
    returnType = "V",
)

private object LocationPreview : Fingerprint(
    definingClass = "Lcom/waze/navigate/gs;",
    name = "f",
    parameters = listOf("Lcom/waze/ay/d/n;", "Lcom/waze/cp;", "Lcom/waze/am;"),
    returnType = "V",
)

private object CarDestinationCollector : Fingerprint(
    definingClass = "Lcom/waze/car_lib/g/o;",
    name = "emit",
    parameters = listOf("Ljava/lang/Object;", "Lg/d/e;"),
    returnType = "Ljava/lang/Object;",
)

private fun openPhoneSearch(query: String, activity: String, search: String, temp: String) = """
    invoke-static {}, Lcom/waze/lb;->d()Lcom/waze/lb;
    move-result-object $activity
    invoke-virtual {$activity}, Lcom/waze/lb;->e()Lcom/waze/sharedui/a/f;
    move-result-object $activity
    instance-of $temp, $activity, Landroid/app/Activity;
    if-eqz $temp, :fallback
    check-cast $activity, Landroid/app/Activity;
    new-instance $search, Lcom/waze/search/w;
    invoke-direct {$search}, Lcom/waze/search/w;-><init>()V
    invoke-virtual {$search, $query}, Lcom/waze/search/w;->h(Ljava/lang/String;)Lcom/waze/search/w;
    const/4 $temp, 0x1
    invoke-virtual {$search, $activity, $temp}, Lcom/waze/search/w;->j(Landroid/app/Activity;I)V
"""

private fun MutableMethod.gateDestinationConnection(expectedCalls: Int) {
    val instructions = implementation!!.instructions.toList()
    val checks = instructions.indices.filter { index ->
        val reference = (instructions[index] as? ReferenceInstruction)?.reference as? MethodReference
        reference?.definingClass == "Lcom/waze/am;" && reference.name == "e" &&
            reference.parameterTypes.isEmpty() && reference.returnType == "Z"
    }
    check(checks.size == expectedCalls)
    for (index in checks.asReversed()) {
        val result = instructions[index + 1]
        check(result.opcode == Opcode.MOVE_RESULT)
        val register = "v${(result as OneRegisterInstruction).registerA}"
        addInstructions(index + 2, """
            invoke-static {$register}, $EXTENSION->routeDestinationToCar(Z)Z
            move-result $register
        """)
    }
}

@Suppress("unused")
val androidAutoPhoneMapPatch = patch(
    name = "Remove Car mode when Waze inactive on Android Auto",
    description = "Keep Waze’s map and shared destinations on the phone while Waze is off the Android Auto screen.",
    default = false,
) {
    compatibleWith(Compatibility(name = "Waze", packageName = "com.waze", targets = listOf(
        AppTarget(version = "5.27.5.0", isExperimental = true),
    )))
    extendWith("extensions/extension.mpe")

    execute {
        val session = CarSessionEvent.method
        val viewModel = PhoneViewModel.method
        val connectionUpdate = CarConnectionUpdate.method
        val sharedText = SharedTextIntent.method
        val textInstructions = sharedText.implementation!!.instructions.toList()
        val textFlagCall = textInstructions.indices.single { index ->
            val reference = (textInstructions[index] as? ReferenceInstruction)?.reference as? MethodReference
            reference?.definingClass == "Lcom/waze/io;" && reference.name == "a" &&
                reference.parameterTypes.isEmpty() && reference.returnType == "Z"
        }
        val textFlagResult = textInstructions[textFlagCall + 1]
        check(textFlagResult.opcode == Opcode.MOVE_RESULT)
        val textFlag = "v${(textFlagResult as OneRegisterInstruction).registerA}"
        val sharedTextDispatch = textInstructions.withIndex().single { (_, instruction) ->
            val reference = (instruction as? ReferenceInstruction)?.reference as? MethodReference
            reference?.definingClass == "Lcom/waze/search/bj;" && reference.name == "b" &&
                reference.parameterTypes.map { it.toString() } == listOf("Ljava/lang/String;", "Z", "Z", "Ljava/lang/String;")
        }
        val textQuery = "v${(sharedTextDispatch.value as Instruction35c).registerD}"
        val destinationIntent = DestinationIntent.method
        val locationPreview = LocationPreview.method
        val collector = CarDestinationCollector.method
        val collectorChecks = collector.implementation!!.instructions.mapIndexedNotNull { index, instruction ->
            val type = (instruction as? ReferenceInstruction)?.reference as? TypeReference
            if (instruction.opcode == Opcode.CHECK_CAST && type?.type in
                setOf("Lcom/waze/search/bi;", "Lcom/waze/bd;")) index else null
        }
        check(collectorChecks.size == 3)
        val nativeDestination = destinationIntent.implementation!!.instructions.withIndex().single { (_, instruction) ->
            val reference = (instruction as? ReferenceInstruction)?.reference as? MethodReference
            reference?.definingClass == "Lcom/waze/NativeManager;" && reference.name == "UrlHandler" &&
                reference.parameterTypes.map { it.toString() } == listOf("Ljava/lang/String;") && reference.returnType == "V"
        }
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
        connectionUpdate.addInstructions(0, "invoke-static {p1}, $EXTENSION->onConnectionChanged(Ljava/lang/Object;)V")
        sharedText.addInstructionsWithLabels(sharedTextDispatch.index, """
            invoke-static {$textQuery}, $EXTENSION->openSharedTextOnPhone(Ljava/lang/String;)Z
            move-result v6
            if-eqz v6, :fallback
            ${openPhoneSearch(textQuery, "v4", "v5", "v6")}
            return v2
        """, ExternalLabel("fallback", sharedTextDispatch.index))
        sharedText.gateDestinationConnection(1)
        sharedText.addInstructions(textFlagCall + 2, """
            invoke-static {$textFlag}, $EXTENSION->acceptSharedText(Z)Z
            move-result $textFlag
        """)
        locationPreview.gateDestinationConnection(2)

        // A search-only URL can go straight to Waze's phone search activity. Keep Waze's
        // parser for coordinates, navigation requests and other deep link actions.
        val url = "v${(nativeDestination.value as Instruction35c).registerD}"
        destinationIntent.addInstructionsWithLabels(nativeDestination.index, """
            invoke-static {$url}, $EXTENSION->phoneSearchQuery(Ljava/lang/String;)Ljava/lang/String;
            move-result-object v0
            if-eqz v0, :fallback
            ${openPhoneSearch("v0", "v1", "v2", "v3")}
            return-void
        """, ExternalLabel("fallback", nativeDestination.index))

        // The phone and car subscribe to the same destination events. Skip the car's delivery
        // when the active projection is displaying a different app.
        for (index in collectorChecks.asReversed()) {
            collector.addInstructionsWithLabels(index + 1, """
                invoke-static {}, $EXTENSION->keepDestinationOnPhone()Z
                move-result p2
                if-eqz p2, :deliver
                sget-object p2, Lg/ab;->a:Lg/ab;
                return-object p2
            """, ExternalLabel("deliver", index + 1))
        }
    }
}
