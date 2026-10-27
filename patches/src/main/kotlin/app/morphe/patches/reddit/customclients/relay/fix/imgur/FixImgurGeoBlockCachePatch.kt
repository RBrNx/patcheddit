package app.morphe.patches.reddit.customclients.relay.fix.imgur

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.reddit.customclients.relay.RelayCompatible
import app.morphe.patches.reddit.customclients.relay.misc.extension.sharedExtensionPatch
import app.morphe.util.getReference
import app.morphe.util.indexOfFirstInstructionOrThrow
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val EXTENSION_CLASS_DESCRIPTOR = "Lapp/morphe/extension/relay/ImgurBlockDetector;"

/**
 * Imgur is geo-blocked in the UK, and answers every image request from there with 200 OK and a
 * "content blocked" placeholder. Glide caches it against the real url, so the placeholder keeps
 * showing after switching to a VPN.
 *
 * The fetcher's `isSuccessful` check is routed through the extension, which reports the
 * placeholder as a failure so Glide never caches it.
 */
@Suppress("unused")
val fixImgurGeoBlockCachePatch = bytecodePatch(
    name = "Fix Imgur geo-block cache",
    description = "Stops Imgur's UK geo-block placeholder being cached in place of the real image, " +
        "so Imgur images load once a VPN is switched on.",
    default = true,
) {
    dependsOn(sharedExtensionPatch)
    compatibleWith(*RelayCompatible)

    execute {
        glideFetcherOnResponseFingerprint.method.apply {
            // isSuccessful is minified, but it is the only no-argument boolean Response call here.
            val isSuccessfulIndex = indexOfFirstInstructionOrThrow {
                val reference = getReference<MethodReference>() ?: return@indexOfFirstInstructionOrThrow false
                reference.definingClass == "Lokhttp3/Response;" &&
                    reference.returnType == "Z" &&
                    reference.parameterTypes.isEmpty()
            }
            val resultIndex = isSuccessfulIndex + 1
            val resultRegister = getInstruction<OneRegisterInstruction>(resultIndex).registerA

            // p2 is the Response. The result register is overwritten in place, so no spare
            // register is needed.
            addInstructions(
                resultIndex + 1,
                """
                invoke-static { v$resultRegister, p2 }, $EXTENSION_CLASS_DESCRIPTOR->isSuccessful(ZLjava/lang/Object;)Z
                move-result v$resultRegister
                """,
            )
        }
    }
}
