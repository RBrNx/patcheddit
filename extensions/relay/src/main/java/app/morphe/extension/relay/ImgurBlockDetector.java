package app.morphe.extension.relay;

import android.net.Uri;

import java.lang.reflect.Field;
import java.util.Locale;

import app.morphe.extension.shared.Logger;

/**
 * Stops Imgur's UK geo-block placeholder from being cached in place of the real image.
 * <br>
 * From a UK connection Imgur answers every i.imgur.com request - .jpg, .gif and .mp4 urls
 * included - with 200 OK and the same "content blocked" PNG. Glide caches that against the
 * requested url, so the placeholder keeps showing even after switching to a VPN. Reporting the
 * response as unsuccessful makes Glide fail the load instead, and a failed load is never cached.
 * <br>
 * okhttp is minified in Relay, so none of its accessors can be called by name. The headers are
 * found by field type, and the url is read from Response#toString, which cannot be renamed.
 *
 * @noinspection unused
 */
public final class ImgurBlockDetector {
    private static final String IMGUR_DOMAIN = "imgur.com";
    private static final String HEADERS_CLASS_NAME = "okhttp3.Headers";
    private static final String URL_PREFIX = "url=";

    // The placeholder is byte-for-byte identical for every blocked url, so its exact size
    // identifies it without false positives on genuine PNGs.
    private static final String BLOCKED_CONTENT_TYPE = "image/png";
    private static final String BLOCKED_CONTENT_LENGTH = "34641";

    private static volatile Field headersField;

    public static boolean isSuccessful(boolean successful, Object response) {
        if (!successful) return false;

        try {
            if (!isBlockPlaceholder(response)) return true;

            Logger.printDebug(() -> "Imgur geo-block placeholder, failing the load: " + response);
            return false;
        } catch (Exception ex) {
            Logger.printException(() -> "Could not inspect the Imgur response", ex);
            return true;
        }
    }

    private static boolean isBlockPlaceholder(Object response) throws IllegalAccessException {
        if (!isImgurUrl(response.toString())) return false;

        Object headers = findHeadersField(response.getClass()).get(response);
        if (headers == null) return false;

        String contentType = null;
        String contentLength = null;
        // Headers#toString renders one "name: value" line per header.
        for (String line : headers.toString().split("\n")) {
            int separator = line.indexOf(':');
            if (separator < 0) continue;

            String name = line.substring(0, separator).trim().toLowerCase(Locale.ROOT);
            String value = line.substring(separator + 1).trim();
            if (name.equals("content-type")) contentType = value;
            else if (name.equals("content-length")) contentLength = value;
        }

        return contentType != null
                && contentType.toLowerCase(Locale.ROOT).startsWith(BLOCKED_CONTENT_TYPE)
                && BLOCKED_CONTENT_LENGTH.equals(contentLength);
    }

    // Response#toString is "Response{protocol=…, code=…, message=…, url=…}".
    private static boolean isImgurUrl(String description) {
        int start = description.lastIndexOf(URL_PREFIX);
        if (start < 0) return false;

        int end = description.endsWith("}") ? description.length() - 1 : description.length();
        String host = Uri.parse(description.substring(start + URL_PREFIX.length(), end)).getHost();
        return host != null && (host.equals(IMGUR_DOMAIN) || host.endsWith("." + IMGUR_DOMAIN));
    }

    private static Field findHeadersField(Class<?> responseClass) {
        Field field = headersField;
        if (field != null) return field;

        for (Field candidate : responseClass.getDeclaredFields()) {
            if (candidate.getType().getName().equals(HEADERS_CLASS_NAME)) {
                candidate.setAccessible(true);
                headersField = candidate;
                return candidate;
            }
        }

        throw new IllegalStateException("No " + HEADERS_CLASS_NAME + " field on " + responseClass);
    }
}
