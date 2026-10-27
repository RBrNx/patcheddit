package app.morphe.patches.reddit.customclients.relay.fix.imgurjpeg

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall
import app.morphe.patcher.string
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

/**
 * Reads the Content-Type of Relay's imgur.com HEAD probe and builds an image preview for
 * image/jpeg or image/png, or a video preview for anything else.
 */
internal val imgurProbeContentTypeFilter = methodCall(
    definingClass = "Lokhttp3/Headers;",
    returnType = "Ljava/lang/String;",
)

internal val imgurProbeResponseFingerprint = Fingerprint(
    definingClass = "Lreddit/news/previews/MediaUrlFetcher;",
    returnType = "Ljava/util/ArrayList;",
    parameters = listOf("Lokhttp3/HttpUrl;", "Lretrofit2/Response;"),
    filters = listOf(
        string("Content-Type"),
        imgurProbeContentTypeFilter,
        string("image/jpeg"),
    ),
)
