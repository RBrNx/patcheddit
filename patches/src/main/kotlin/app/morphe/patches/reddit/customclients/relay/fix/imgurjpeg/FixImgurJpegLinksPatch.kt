package app.morphe.patches.reddit.customclients.relay.fix.imgurjpeg

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.reddit.customclients.relay.RelayCompatible
import app.morphe.patches.reddit.customclients.relay.misc.extension.sharedExtensionPatch

private const val EXTENSION_CLASS_DESCRIPTOR = "Lapp/morphe/extension/relay/ImgurJpegUrlNormalizer;"

/**
 * Relay only treats ".jpg" and ".png" as direct images. An Imgur ".jpeg" link is instead probed
 * with a HEAD request, and when that is rate limited or blocked Relay assumes a video and the
 * image fails to play. Rewriting the link to ".jpg" sends it down the direct image path.
 */
@Suppress("unused")
val fixImgurJpegLinksPatch = bytecodePatch(
    name = "Fix Imgur .jpeg links",
    description = "Fixes Imgur images with a .jpeg extension being opened as broken videos.",
    default = true,
) {
    dependsOn(sharedExtensionPatch)
    compatibleWith(*RelayCompatible)

    execute {
        // p1 is the link url; it is replaced in place, so no spare register is needed. The method
        // has enough registers that p1 is above v15, which only the range form can address.
        fetchMediaFingerprint.method.addInstructions(
            0,
            """
            invoke-static/range { p1 .. p1 }, $EXTENSION_CLASS_DESCRIPTOR->normalize(Ljava/lang/String;)Ljava/lang/String;
            move-result-object p1
            """,
        )
    }
}
