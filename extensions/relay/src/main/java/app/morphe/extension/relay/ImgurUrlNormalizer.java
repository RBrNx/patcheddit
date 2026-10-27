package app.morphe.extension.relay;

import android.net.Uri;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Rewrites single-item Imgur links into a form Relay classifies and loads correctly.
 * <br>
 * Relay only recognises ".jpg" and ".png" as direct images. Anything else on Imgur is probed
 * over imgur.com, and the media url is then built on whatever host the link used. imgur.com is
 * the host that gets rate limited (429) on many VPN exit IPs and geo-blocked (403) in the UK,
 * while i.imgur.com serves the same media. So:
 * <ul>
 *   <li>".jpeg" becomes ".jpg", which Relay loads as an image directly without probing.</li>
 *   <li>A bare imgur.com/&lt;id&gt; link moves to i.imgur.com, so the media and thumbnail urls
 *   Relay builds for it point at the host that works.</li>
 * </ul>
 *
 * @noinspection unused
 */
public final class ImgurUrlNormalizer {
    private static final String IMGUR_DOMAIN = "imgur.com";
    private static final String IMGUR_MEDIA_HOST = "i.imgur.com";
    private static final String JPEG_EXTENSION = ".jpeg";
    private static final String JPG_EXTENSION = ".jpg";

    // Imgur media ids, optionally with an extension. Excludes site paths such as /upload.
    private static final Pattern MEDIA_SEGMENT = Pattern.compile("[A-Za-z0-9]{5,10}(\\.[A-Za-z0-9]+)?");

    public static String normalize(String url) {
        if (url == null) return null;

        Uri uri = Uri.parse(url);
        String host = uri.getHost();
        List<String> segments = uri.getPathSegments();
        if (host == null || segments.size() != 1) return url;
        if (!host.equals(IMGUR_DOMAIN) && !host.endsWith("." + IMGUR_DOMAIN)) return url;

        String segment = segments.get(0);
        if (!MEDIA_SEGMENT.matcher(segment).matches()) return url;

        if (segment.toLowerCase(Locale.ROOT).endsWith(JPEG_EXTENSION)) {
            segment = segment.substring(0, segment.length() - JPEG_EXTENSION.length()) + JPG_EXTENSION;
        }

        return uri.buildUpon().authority(IMGUR_MEDIA_HOST).path("/" + segment).build().toString();
    }
}
