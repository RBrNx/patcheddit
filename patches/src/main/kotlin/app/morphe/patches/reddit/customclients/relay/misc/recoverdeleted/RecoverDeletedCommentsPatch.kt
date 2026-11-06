package app.morphe.patches.reddit.customclients.relay.misc.recoverdeleted

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.resource.ResourceType
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patches.all.misc.resources.getResourceId
import app.morphe.patches.all.misc.resources.resourceMappingPatch
import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ExtensionPatches
import app.morphe.util.getReference
import app.morphe.util.indexOfFirstInstructionOrThrow
import app.morphe.util.indexOfFirstLiteralInstructionOrThrow
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val EXTENSION_CLASS_DESCRIPTOR = "Lapp/morphe/extension/relay/RecoverDeletedPatch;"

// A non-range invoke can only address v0-v15; anything higher is dropped without an error.
private const val MAX_NON_RANGE_REGISTER = 15

/**
 * Index of the `show()` call for the popup inflated from [menuName]. Both hooked methods also show
 * a moderator menu, and the order of the two branches in the bytecode differs between them.
 */
private fun MutableMethod.popupShowIndex(menuName: String): Int {
    val menuLiteralIndex = indexOfFirstLiteralInstructionOrThrow(getResourceId(ResourceType.MENU, menuName))
    return indexOfFirstInstructionOrThrow(menuLiteralIndex) {
        val reference = getReference<MethodReference>()
        reference?.definingClass == POPUP_MENU_CLASS && reference.name == "show"
    }
}

private fun MutableMethod.popupRegisterAt(showIndex: Int): Int {
    val popupRegister = getInstruction<FiveRegisterInstruction>(showIndex).registerC
    check(popupRegister <= MAX_NON_RANGE_REGISTER) { "$name: popup register v$popupRegister is out of range" }
    return popupRegister
}

@Suppress("unused")
val recoverDeletedCommentsPatch = bytecodePatch(
    name = "Recover deleted posts and comments",
    description = "Adds a \"Recover from archive\" option to the menu of deleted and removed " +
        "posts and comments, which restores the original text from the Arctic Shift archive.",
    default = true,
) {
    dependsOn(ExtensionPatches.Relay, resourceMappingPatch)
    compatibleWith(*AppCompatibility.Relay)

    execute {
        commentsAdapterClickFingerprint.method.apply {
            val showIndex = popupShowIndex("comment_overflow")
            val popupRegister = popupRegisterAt(showIndex)
            val viewRegister = implementation!!.registerCount - 1
            check(viewRegister <= MAX_NON_RANGE_REGISTER) { "CommentsAdapter#onClick p1 is out of range" }

            // p0 is the adapter and p1 the tapped overflow button, whose tag is the comment's
            // position. Neither is reassigned on the overflow menu's path.
            addInstruction(
                showIndex,
                "invoke-static { v$popupRegister, p0, p1 }, $EXTENSION_CLASS_DESCRIPTOR->" +
                    "addRecoverItem(Landroidx/appcompat/widget/PopupMenu;Ljava/lang/Object;Ljava/lang/Object;)V",
            )
        }

        webAndCommentsFragmentClickFingerprint.method.apply {
            val showIndex = popupShowIndex("menu_comments_overflow")
            val popupRegister = popupRegisterAt(showIndex)

            // p0 (the fragment) is above v15 here, so it goes through a separate range invoke.
            addInstructions(
                showIndex,
                """
                invoke-static/range { p0 .. p0 }, $EXTENSION_CLASS_DESCRIPTOR->setPostFragment(Ljava/lang/Object;)V
                invoke-static { v$popupRegister }, $EXTENSION_CLASS_DESCRIPTOR->addRecoverPostItem(Landroidx/appcompat/widget/PopupMenu;)V
                """,
            )
        }
    }
}
