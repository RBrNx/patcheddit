package app.morphe.patches.reddit.customclients.relay.fix.imgurjpeg

import app.morphe.patcher.Fingerprint
import com.android.tools.smali.dexlib2.AccessFlags

/**
 * `MediaUrlFetcher#fetchMedia`, the single entry point that classifies a link as an image, video
 * or album and builds its previews.
 *
 * Matched on its class and signature rather than its name, which is minified. A private album
 * fetcher shares the signature, so the public access flag is what makes this unique.
 */
internal val fetchMediaFingerprint = Fingerprint(
    definingClass = "Lreddit/news/previews/MediaUrlFetcher;",
    accessFlags = listOf(AccessFlags.PUBLIC),
    returnType = "Lrx/Observable;",
    parameters = listOf("Ljava/lang/String;"),
)
