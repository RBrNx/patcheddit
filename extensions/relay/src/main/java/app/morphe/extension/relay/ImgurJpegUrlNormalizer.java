package app.morphe.extension.relay;

import android.net.Uri;

import java.util.Locale;

/**
 * Rewrites direct Imgur ".jpeg" links to ".jpg" before Relay classifies them.
 * <br>
 * Relay only recognises ".jpg" and ".png" as direct images. Anything else on Imgur is probed with
 * a HEAD request to imgur.com, and any answer other than image/jpeg or image/png - including a
 * 429 rate limit or a geo-block 403 - is taken to mean a video, so the image is handed to the
 * video player as "&lt;id&gt;.jpeg.mp4" and fails. Imgur serves the same image at ".jpg", which
 * Relay handles as an image directly, thumbnails included.
 *
 * @noinspection unused
 */
public final class ImgurJpegUrlNormalizer {
    private static final String IMGUR_DOMAIN = "imgur.com";
    private static final String JPEG_EXTENSION = ".jpeg";
    private static final String JPG_EXTENSION = ".jpg";

    public static String normalize(String url) {
        if (url == null) return null;

        Uri uri = Uri.parse(url);
        String host = uri.getHost();
        String path = uri.getPath();
        if (host == null || path == null) return url;
        if (!host.equals(IMGUR_DOMAIN) && !host.endsWith("." + IMGUR_DOMAIN)) return url;
        if (!path.toLowerCase(Locale.ROOT).endsWith(JPEG_EXTENSION)) return url;

        String jpgPath = path.substring(0, path.length() - JPEG_EXTENSION.length()) + JPG_EXTENSION;
        return uri.buildUpon().path(jpgPath).build().toString();
    }
}
