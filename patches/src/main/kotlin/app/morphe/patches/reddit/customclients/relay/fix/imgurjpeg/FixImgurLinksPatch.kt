package app.morphe.patches.reddit.customclients.relay.fix.imgurjpeg

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.reddit.customclients.relay.RelayCompatible
import app.morphe.patches.reddit.customclients.relay.misc.extension.sharedExtensionPatch
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

private const val NORMALIZER_CLASS_DESCRIPTOR = "Lapp/morphe/extension/relay/ImgurUrlNormalizer;"
private const val RESOLVER_CLASS_DESCRIPTOR = "Lapp/morphe/extension/relay/ImgurMediaTypeResolver;"

/**
 * Relay only treats ".jpg" and ".png" as direct images. Any other Imgur link is probed over
 * imgur.com - which is rate limited on many VPN exit IPs and geo-blocked in the UK - and when the
 * probe fails Relay assumes a video, so images open as broken videos. Media urls are also built
 * on the link's own host, so bare imgur.com links fail even when they really are videos.
 *
 * Links are normalised to i.imgur.com, ".jpeg" to ".jpg", and a failed probe falls back to
 * checking i.imgur.com for a video.
 */
@Suppress("unused")
val fixImgurLinksPatch = bytecodePatch(
    name = "Fix Imgur links",
    description = "Fixes Imgur images opening as broken videos, and Imgur videos failing to " +
        "load, when imgur.com is rate limited or blocked.",
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
            invoke-static/range { p1 .. p1 }, $NORMALIZER_CLASS_DESCRIPTOR->normalize(Ljava/lang/String;)Ljava/lang/String;
            move-result-object p1
            """,
        )

        imgurProbeResponseFingerprint.let {
            it.method.apply {
                val resultIndex = it.instructionMatches[1].index + 1
                val contentTypeRegister = getInstruction<OneRegisterInstruction>(resultIndex).registerA

                // p1 is the link's HttpUrl, still unmodified at this point.
                addInstructions(
                    resultIndex + 1,
                    """
                    invoke-static { v$contentTypeRegister, p1 }, $RESOLVER_CLASS_DESCRIPTOR->resolveContentType(Ljava/lang/String;Ljava/lang/Object;)Ljava/lang/String;
                    move-result-object v$contentTypeRegister
                    """,
                )
            }
        }
    }
}
