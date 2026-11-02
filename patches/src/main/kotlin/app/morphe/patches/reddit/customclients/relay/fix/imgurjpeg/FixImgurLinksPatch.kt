package app.morphe.patches.reddit.customclients.relay.fix.imgurjpeg

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ExtensionPatches
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

private const val NORMALIZER_CLASS_DESCRIPTOR = "Lapp/morphe/extension/relay/ImgurUrlNormalizer;"
private const val RESOLVER_CLASS_DESCRIPTOR = "Lapp/morphe/extension/relay/ImgurMediaTypeResolver;"

/**
 * Relay decides whether an Imgur link is an image or a video from its extension, or by probing
 * imgur.com when there is no ".jpg" or ".png". Both go wrong:
 * - A video linked as ".jpg" or ".jpeg" opens as a still frame, which Imgur serves at that url.
 * - The probe is rate limited on many VPN exit IPs and geo-blocked in the UK, and Relay reads a
 *   failed probe as a video, so images open as broken videos.
 * - Media urls are built on the link's own host, so bare imgur.com links fail to load.
 *
 * Links are moved to i.imgur.com with any ".jpg"/".jpeg" dropped so they are always probed, and
 * the probe's answer is replaced by a check for the video at i.imgur.com.
 */
@Suppress("unused")
val fixImgurLinksPatch = bytecodePatch(
    name = "Fix Imgur links",
    description = "Fixes Imgur videos showing as still images or failing to load, and Imgur " +
        "images opening as broken videos.",
    default = true,
) {
    dependsOn(ExtensionPatches.Relay)
    compatibleWith(*AppCompatibility.Relay)

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
