package app.morphe.extension.relay;

import android.net.Uri;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rewrites single-item Imgur links so Relay classifies them by probing rather than by extension.
 * <br>
 * Relay opens any ".jpg" link as a still image without asking Imgur what it is. Imgur serves a
 * still frame at "&lt;id&gt;.jpg" for videos too, so a video linked as ".jpg" or ".jpeg" showed
 * as a static image. Dropping the extension sends the link through Relay's type probe instead,
 * where {@link ImgurMediaTypeResolver} decides between image and video.
 * <br>
 * The link also moves from imgur.com to i.imgur.com, because Relay builds the media and thumbnail
 * urls on the link's own host, and imgur.com is rate limited (429) on many VPN exit IPs and
 * geo-blocked (403) in the UK while i.imgur.com serves the same media.
 *
 * @noinspection unused
 */
public final class ImgurUrlNormalizer {
    private static final String IMGUR_DOMAIN = "imgur.com";
    private static final String IMGUR_MEDIA_HOST = "i.imgur.com";

    // An Imgur media id, optionally with an extension. Excludes site paths such as /upload.
    private static final Pattern MEDIA_SEGMENT = Pattern.compile("([A-Za-z0-9]{5,10})(\\.[A-Za-z0-9]+)?");

    public static String normalize(String url) {
        if (url == null) return null;

        Uri uri = Uri.parse(url);
        String host = uri.getHost();
        List<String> segments = uri.getPathSegments();
        if (host == null || segments.size() != 1) return url;
        if (!host.equals(IMGUR_DOMAIN) && !host.endsWith("." + IMGUR_DOMAIN)) return url;

        Matcher matcher = MEDIA_SEGMENT.matcher(segments.get(0));
        if (!matcher.matches()) return url;

        String segment = matcher.group(0);
        String extension = matcher.group(2);
        if (extension != null && isStillImageExtension(extension)) {
            segment = matcher.group(1);
        }

        return uri.buildUpon().authority(IMGUR_MEDIA_HOST).path("/" + segment).build().toString();
    }

    private static boolean isStillImageExtension(String extension) {
        String lower = extension.toLowerCase(Locale.ROOT);
        return lower.equals(".jpg") || lower.equals(".jpeg");
    }
}
