package app.morphe.extension.relay;

import android.net.Uri;
import android.os.Looper;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Locale;

import app.morphe.extension.shared.Logger;

/**
 * Decides whether an Imgur link is an image or a video, in place of Relay's own probe.
 * <br>
 * Relay probes HEAD https://imgur.com/&lt;id&gt;.jpg and treats image/jpeg and image/png as
 * images, anything else as a video. That probe cannot tell the two apart: for a video Imgur
 * answers with a still frame as image/jpeg. It also fails outright with a 429 on many VPN exit
 * IPs and a 403 in the UK, which Relay reads as a video. Instead this checks
 * i.imgur.com/&lt;id&gt;.mp4, which exists only for videos.
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
        try {
            String id = imgurId(url.toString());
            if (id == null) return contentType;

            // Relay runs this on its network scheduler; never block the main thread regardless.
            if (Looper.myLooper() == Looper.getMainLooper()) return contentType;

            String resolved = isVideo(id) ? VIDEO_TYPE : IMAGE_TYPE;
            Logger.printDebug(() -> "Imgur probe returned " + contentType + ", resolved " + id + " as " + resolved);
            return resolved;
        } catch (Exception ex) {
            Logger.printException(() -> "Could not resolve the Imgur media type", ex);
            return contentType;
        }
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
