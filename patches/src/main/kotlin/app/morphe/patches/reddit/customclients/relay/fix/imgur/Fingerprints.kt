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
