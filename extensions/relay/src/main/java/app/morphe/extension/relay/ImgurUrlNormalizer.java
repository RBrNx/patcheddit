package app.morphe.extension.relay;

import android.net.Uri;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rewrites Imgur links into forms Relay classifies and loads correctly.
 * <br>
 * Album and gallery links lose the title slug Imgur now puts before the id, which Relay would
 * otherwise send to the album API as part of the id.
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
    private static final Pattern ALBUM_ID = Pattern.compile("[A-Za-z0-9]{5,10}");
    private static final Set<String> ALBUM_PATHS = new HashSet<>(Arrays.asList("a", "gallery"));

    public static String normalize(String url) {
        if (url == null) return null;

        Uri uri = Uri.parse(url);
        String host = uri.getHost();
        List<String> segments = uri.getPathSegments();
        if (host == null) return url;
        if (!host.equals(IMGUR_DOMAIN) && !host.endsWith("." + IMGUR_DOMAIN)) return url;

        if (segments.size() == 2 && ALBUM_PATHS.contains(segments.get(0))) {
            return stripAlbumSlug(uri, segments);
        }
        if (segments.size() != 1) return url;

        Matcher matcher = MEDIA_SEGMENT.matcher(segments.get(0));
        if (!matcher.matches()) return url;

        String segment = matcher.group(0);
        String extension = matcher.group(2);
        if (extension != null && isStillImageExtension(extension)) {
            segment = matcher.group(1);
        }

        return uri.buildUpon().authority(IMGUR_MEDIA_HOST).path("/" + segment).build().toString();
    }

    // Imgur's album and gallery urls now read /gallery/<title-slug>-<id>, but Relay passes the
    // whole segment to the album API as the id, which 404s.
    private static String stripAlbumSlug(Uri uri, List<String> segments) {
        String segment = segments.get(1);
        int slugEnd = segment.lastIndexOf('-');
        if (slugEnd < 0) return uri.toString();

        String id = segment.substring(slugEnd + 1);
        if (!ALBUM_ID.matcher(id).matches()) return uri.toString();

        return uri.buildUpon().path("/" + segments.get(0) + "/" + id).build().toString();
    }

    private static boolean isStillImageExtension(String extension) {
        String lower = extension.toLowerCase(Locale.ROOT);
        return lower.equals(".jpg") || lower.equals(".jpeg");
    }
}
