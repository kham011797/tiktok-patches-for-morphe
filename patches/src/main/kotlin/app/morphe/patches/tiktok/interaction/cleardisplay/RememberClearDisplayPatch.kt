/*
 * Forked from:
 * https://gitlab.com/ReVanced/revanced-patches/-/blob/main/patches/src/main/kotlin/app/revanced/patches/tiktok/interaction/cleardisplay/RememberClearDisplayPatch.kt
 */
package app.morphe.patches.tiktok.interaction.cleardisplay

import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.returnEarly
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction35c
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction

@Suppress("unused")
val rememberClearDisplayPatch = bytecodePatch(
    name = "Remember clear display",
    description = "Remembers TikTok's clear-display state between videos.",
    default = true,
) {
    compatibleWith(*AppCompatibilities.tiktok4623())

    execute {
        // Prevent excessive logging in high-frequency feed paths.
        ClearModeLogCoreFingerprint.methodOrNull?.returnEarly()
        ClearModeLogStateFingerprint.methodOrNull?.returnEarly()
        ClearModeLogPlaytimeFingerprint.methodOrNull?.returnEarly()


        
        AutoScrollSwitchFingerprint.method.addInstructions(
            0,
            """
                const/16 v0, 0x8
                invoke-virtual {p1, v0}, Landroid/view/View;->setVisibility(I)V
            """,
        )

AutoScrollButtonNoTextFingerprint.method.let { method ->
            val instructions = method.implementation!!.instructions

            val visibilityCallIndex = instructions.indexOfFirst { instruction ->
                val reference =
                    (instruction as? ReferenceInstruction)?.reference as? MethodReference

                reference?.definingClass == "LX/0UUp;" &&
                    reference.name == "LJLZ" &&
                    reference.returnType == "V"
            }

            check(visibilityCallIndex >= 0) {
                "Auto Scroll icon visibility call was not found"
            }

            val visibilityCall =
                instructions.elementAt(visibilityCallIndex) as Instruction35c

            method.addInstruction(
                visibilityCallIndex,
                "const/16 v${visibilityCall.registerC}, 0x8",
            )
        }

        OnClearDisplayEventFingerprint.method.let { method ->
            val eventClass = method.parameters[0].type
            val eventRegister = method.implementation!!.registerCount - method.parameters.size
            method.addInstructions(
                0,
                "invoke-static/range {v$eventRegister .. v$eventRegister}, " +
                    "Lapp/morphe/extension/tiktok/cleardisplay/RememberClearDisplayPatch;->rememberClearDisplayEvent(Ljava/lang/Object;)V",
            )

            OnRenderFirstFrameBodyFingerprint.method.addInstructions(
                0,
                """
                    invoke-static {}, Lapp/morphe/extension/tiktok/cleardisplay/RememberClearDisplayPatch;->getClearDisplayState()Z
                    move-result v1

                    if-eqz v1, :clear_display_disabled

                    const/4 v2, 0x0
                    const-string v3, ""
                    const-string v4, "long_press"

                    new-instance v0, $eventClass
                    invoke-direct {v0, v1, v2, v3, v4}, $eventClass-><init>(ZILjava/lang/String;Ljava/lang/String;)V
                    invoke-virtual {v0}, $eventClass->post()Lcom/ss/android/ugc/governance/eventbus/IEvent;

                    :clear_display_disabled
                    nop
                """,
            )
        }
    }
}

