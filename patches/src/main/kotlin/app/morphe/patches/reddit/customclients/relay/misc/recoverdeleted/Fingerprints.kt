package app.morphe.patches.reddit.customclients.relay.misc.recoverdeleted

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall

internal const val POPUP_MENU_CLASS = "Landroidx/appcompat/widget/PopupMenu;"

/**
 * `CommentsAdapter#onClick`, which handles every button on a comment in the post view, including
 * showing the comment's moderator and overflow ("...") menus.
 */
internal val commentsAdapterClickFingerprint = Fingerprint(
    definingClass = "Lreddit/news/adapters/CommentsAdapter;",
    name = "onClick",
    returnType = "V",
    parameters = listOf("Landroid/view/View;"),
    filters = listOf(
        methodCall(definingClass = POPUP_MENU_CLASS, name = "show"),
    ),
)

/**
 * `WebAndCommentsFragment#onClick`, which handles the post view's toolbar buttons, including
 * showing the post's moderator and overflow menus.
 */
internal val webAndCommentsFragmentClickFingerprint = Fingerprint(
    definingClass = "Lreddit/news/WebAndCommentsFragment;",
    name = "onClick",
    returnType = "V",
    parameters = listOf("Landroid/view/View;"),
    filters = listOf(
        methodCall(definingClass = POPUP_MENU_CLASS, name = "show"),
    ),
)
