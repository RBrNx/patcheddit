package app.morphe.patches.reddit.customclients.relay.fix.imgur

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ExtensionPatches
import app.morphe.util.getReference
import app.morphe.util.indexOfFirstInstructionOrThrow
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val EXTENSION_CLASS_DESCRIPTOR = "Lapp/morphe/extension/relay/ImgurBlockDetector;"

/**
 * Imgur is geo-blocked in the UK, and answers every image and video request from there with
 * 200 OK and a "content blocked" placeholder. Glide and ExoPlayer each cache it against the real
 * url, so the placeholder keeps showing - and videos keep failing - after switching to a VPN.
 *
 * Both loaders' `isSuccessful` checks are routed through the extension, which reports the
 * placeholder as a failure so neither cache ever stores it.
 */
@Suppress("unused")
val fixImgurGeoBlockCachePatch = bytecodePatch(
    name = "Fix Imgur geo-block cache",
    description = "Stops Imgur's UK geo-block placeholder being cached in place of the real " +
        "image or video, so Imgur media loads once a VPN is switched on.",
    default = true,
) {
    dependsOn(ExtensionPatches.Relay)
    compatibleWith(*AppCompatibility.Relay)

    execute {
        listOf(glideFetcherOnResponseFingerprint, videoDataSourceOpenFingerprint).forEach { fingerprint ->
            fingerprint.method.apply {
                // isSuccessful is minified, but it is the only no-argument boolean Response call
                // in either method.
                val isSuccessfulIndex = indexOfFirstInstructionOrThrow {
                    val reference = getReference<MethodReference>()
                        ?: return@indexOfFirstInstructionOrThrow false
                    reference.definingClass == "Lokhttp3/Response;" &&
                        reference.returnType == "Z" &&
                        reference.parameterTypes.isEmpty()
                }
                val responseRegister =
                    getInstruction<FiveRegisterInstruction>(isSuccessfulIndex).registerC
                val resultIndex = isSuccessfulIndex + 1
                val resultRegister = getInstruction<OneRegisterInstruction>(resultIndex).registerA

                // The result register is overwritten in place, so no spare register is needed.
                addInstructions(
                    resultIndex + 1,
                    """
                    invoke-static { v$resultRegister, v$responseRegister }, $EXTENSION_CLASS_DESCRIPTOR->isSuccessful(ZLjava/lang/Object;)Z
                    move-result v$resultRegister
                    """,
                )
            }
        }
    }
}
