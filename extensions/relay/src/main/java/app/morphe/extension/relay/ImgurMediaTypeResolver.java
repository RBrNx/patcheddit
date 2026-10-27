package app.morphe.extension.relay;

import android.net.Uri;
import android.os.Looper;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Locale;

import app.morphe.extension.shared.Logger;

/**
 * Recovers the media type of an Imgur link when Relay's own probe fails.
 * <br>
 * Relay decides whether an Imgur link without a usable extension is an image or a video with
 * HEAD https://imgur.com/&lt;id&gt;.jpg, treating image/jpeg and image/png as images and anything
 * else as a video. imgur.com answers with a 429 on many VPN exit IPs and a 403 in the UK, so
 * those images were opened as broken videos. When the probe did not return media, this asks
 * i.imgur.com instead: its ".mp4" url exists only for videos.
 *
 * @noinspection unused
 */
public final class ImgurMediaTypeResolver {
    private static final String IMGUR_DOMAIN = "imgur.com";
    private static final String MP4_URL_FORMAT = "https://i.imgur.com/%s.mp4";
    private static final String IMAGE_TYPE = "image/jpeg";
    private static final String VIDEO_TYPE = "video/mp4";
    private static final int TIMEOUT_MS = 5_000;

    public static String resolveContentType(String contentType, Object url) {
        if (isMediaType(contentType)) return contentType;

        try {
            String id = imgurId(url.toString());
            if (id == null) return contentType;

            // Relay runs this on its network scheduler; never block the main thread regardless.
            if (Looper.myLooper() == Looper.getMainLooper()) return IMAGE_TYPE;

            String resolved = isVideo(id) ? VIDEO_TYPE : IMAGE_TYPE;
            Logger.printDebug(() -> "Imgur probe returned " + contentType + ", resolved " + id + " as " + resolved);
            return resolved;
        } catch (Exception ex) {
            Logger.printException(() -> "Could not resolve the Imgur media type", ex);
            return contentType;
        }
    }

    private static boolean isMediaType(String contentType) {
        if (contentType == null) return false;

        String type = contentType.toLowerCase(Locale.ROOT);
        return type.startsWith("image/") || type.startsWith("video/");
    }

    private static String imgurId(String url) {
        Uri uri = Uri.parse(url);
        String host = uri.getHost();
        List<String> segments = uri.getPathSegments();
        if (host == null || segments.isEmpty()) return null;
        if (!host.equals(IMGUR_DOMAIN) && !host.endsWith("." + IMGUR_DOMAIN)) return null;

        String segment = segments.get(segments.size() - 1);
        int extension = segment.indexOf('.');
        return extension < 0 ? segment : segment.substring(0, extension);
    }

    private static boolean isVideo(String id) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(String.format(MP4_URL_FORMAT, id)).openConnection();
        try {
            connection.setRequestMethod("HEAD");
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);

            String type = connection.getContentType();
            return connection.getResponseCode() == HttpURLConnection.HTTP_OK
                    && type != null
                    && type.toLowerCase(Locale.ROOT).startsWith("video/");
        } finally {
            connection.disconnect();
        }
    }
}
