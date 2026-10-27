package app.morphe.patches.reddit.customclients.relay.fix.imgur

import app.morphe.patcher.Fingerprint

/**
 * `OkHttpStreamFetcher#onResponse`, Relay's own Glide fetcher. It hands the body to Glide when
 * `Response#isSuccessful` is true and fails the load otherwise, and Glide only caches loads that
 * succeed.
 *
 * Matched on its class and parameters rather than its name, which is minified.
 */
internal val glideFetcherOnResponseFingerprint = Fingerprint(
    definingClass = "Lreddit/news/oauth/glide/okhttp3/OkHttpStreamFetcher;",
    returnType = "V",
    parameters = listOf("Lokhttp3/Call;", "Lokhttp3/Response;"),
)

/**
 * `OkHttpDataSource#open`, Relay's own ExoPlayer data source. When `Response#isSuccessful` is
 * false it throws before returning any data, so nothing reaches ExoPlayer's media cache.
 *
 * Matched on its class and signature rather than its name, which is minified.
 */
internal val videoDataSourceOpenFingerprint = Fingerprint(
    definingClass = "Lreddit/news/oauth/exoplayer/OkHttpDataSource;",
    returnType = "J",
    parameters = listOf("Lcom/google/android/exoplayer2/upstream/DataSpec;"),
)
