package app.morphe.extension.relay;

import android.content.Context;
import android.content.res.ColorStateList;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.appcompat.widget.PopupMenu;

import com.dbrady.snudown.Snudown;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;
import reddit.news.data.DataComment;
import reddit.news.data.DataStory;
import reddit.news.utils.PopupMenuUtils;
import reddit.news.utils.RedditUtils;

/**
 * Adds a "Recover" item to the menus of deleted and removed comments and posts, which fetches the
 * original from the Arctic Shift archive and renders it in place.
 * <br>
 * The archive captures content shortly after it is posted, so something its author deleted later
 * is usually recoverable. Something removed before the archive saw it, such as a comment removed
 * instantly by AutoModerator, is archived as "[removed]" and cannot be recovered.
 *
 * @noinspection unused
 */
public final class RecoverDeletedPatch {
    private static final String ARCHIVE_URL = "https://arctic-shift.photon-reddit.com/api/";
    private static final String COMMENTS_PATH = "comments/ids?fields=author,body&ids=";
    private static final String POSTS_PATH = "posts/ids?fields=author,selftext&ids=";
    private static final String DELETED = "[deleted]";
    private static final String REMOVED = "[removed]";
    private static final String MENU_TITLE = "Recover from archive";
    private static final String MENU_ICON = "icon_svg_restore";
    private static final String RECOVERED_BANNER_HTML = "<p><em>Recovered from Arctic Shift</em></p>";
    private static final int TIMEOUT_MS = 10_000;

    // Both are package-private or protected in WebAndCommentsFragment, so they are reached by name.
    private static final String CURRENT_STORY_FIELD = "currentStory";
    private static final String SET_SELF_TEXT_METHOD = "v4";

    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    private static WeakReference<Object> postFragment = new WeakReference<>(null);

    private interface ArchiveCallback {
        void onResult(JSONObject archived);
    }

    /**
     * Called from {@code CommentsAdapter#onClick} just before the comment overflow menu is shown.
     * Parameters are untyped so that a hook landing on a different menu does nothing rather than
     * failing verification.
     */
    public static void addRecoverItem(PopupMenu popup, Object adapter, Object anchor) {
        try {
            if (!(adapter instanceof ArrayAdapter) || !(anchor instanceof View)) return;
            View anchorView = (View) anchor;
            if (!(anchorView.getTag() instanceof Integer)) return;

            ArrayAdapter<?> arrayAdapter = (ArrayAdapter<?>) adapter;
            Object item = arrayAdapter.getItem((Integer) anchorView.getTag());
            if (!(item instanceof DataComment)) return;

            DataComment comment = (DataComment) item;
            if (!isDeletedMarker(comment.N) && !DELETED.equals(comment.p)) return;

            Context context = anchorView.getContext().getApplicationContext();
            addMenuItem(context, popup, () -> fetchArchived(context, COMMENTS_PATH, comment.i, archived ->
                    applyComment(context, arrayAdapter, comment, archived)));
        } catch (Exception ex) {
            Logger.printException(() -> "Could not add the recover item to a comment", ex);
        }
    }

    /**
     * Called from {@code WebAndCommentsFragment#onClick} before {@link #addRecoverPostItem}. The
     * fragment's register is out of reach of a call that also takes the menu, hence two calls.
     */
    public static void setPostFragment(Object fragment) {
        postFragment = new WeakReference<>(fragment);
    }

    /** Called from {@code WebAndCommentsFragment#onClick} just before the post overflow menu is shown. */
    public static void addRecoverPostItem(PopupMenu popup) {
        try {
            Object fragment = postFragment.get();
            if (fragment == null) return;

            Object story = findField(fragment.getClass(), CURRENT_STORY_FIELD).get(fragment);
            if (!(story instanceof DataStory)) return;

            DataStory post = (DataStory) story;
            if (!isDeletedMarker(post.U) && !DELETED.equals(post.p)) return;

            Context context = Utils.getContext();
            addMenuItem(context, popup, () -> fetchArchived(context, POSTS_PATH, post.i, archived ->
                    applyPost(context, fragment, post, archived)));
        } catch (Exception ex) {
            Logger.printException(() -> "Could not add the recover item to a post", ex);
        }
    }

    private static void addMenuItem(Context context, PopupMenu popup, Runnable onClick) {
        MenuItem item = popup.getMenu().add(Menu.NONE, Menu.NONE, Menu.NONE, MENU_TITLE);
        item.setOnMenuItemClickListener(menuItem -> {
            onClick.run();
            return true;
        });

        if (context == null) return;
        int icon = context.getResources().getIdentifier(MENU_ICON, "drawable", context.getPackageName());
        if (icon == 0) return;
        item.setIcon(icon);
        // Relay's icons are plain black; each menu item tints them from the theme in its XML.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            item.setIconTintList(siblingIconTint(popup.getMenu()));
        }
        // The tint argument only applies to submenus.
        PopupMenuUtils.e(item, 0);
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private static ColorStateList siblingIconTint(Menu menu) {
        for (int i = 0; i < menu.size(); i++) {
            ColorStateList tint = menu.getItem(i).getIconTintList();
            if (tint != null) return tint;
        }
        return null;
    }

    private static boolean isDeletedMarker(String text) {
        return DELETED.equals(text) || REMOVED.equals(text);
    }

    private static void fetchArchived(Context context, String path, String id, ArchiveCallback callback) {
        new Thread(() -> {
            try {
                JSONObject archived = fetch(ARCHIVE_URL + path + id);
                mainHandler.post(() -> callback.onResult(archived));
            } catch (Exception ex) {
                Logger.printException(() -> "Could not fetch " + id + " from the archive", ex);
                mainHandler.post(() -> toast(context, "Could not reach the archive"));
            }
        }).start();
    }

    private static JSONObject fetch(String url) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        try {
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);

            int code = connection.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) throw new IOException("HTTP " + code);

            StringBuilder body = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) body.append(line).append('\n');
            }

            // Ids the archive does not hold are left out of "data" rather than reported.
            JSONArray data = new JSONObject(body.toString()).optJSONArray("data");
            return data == null || data.length() == 0 ? null : data.getJSONObject(0);
        } finally {
            connection.disconnect();
        }
    }

    /** Returns the recovered markdown, or null after telling the user why there is none. */
    private static String recoveredText(Context context, JSONObject archived, String textField, String kind) {
        if (archived == null) {
            toast(context, "This " + kind + " is not in the archive");
            return null;
        }

        String text = archived.optString(textField);
        if (isDeletedMarker(text)) {
            toast(context, "This " + kind + " was removed before it could be archived");
            return null;
        }
        return text;
    }

    private static String recoveredAuthor(JSONObject archived, String current) {
        String author = archived.optString("author");
        return author.isEmpty() || DELETED.equals(author) ? current : author;
    }

    private static String recoveredHtml(String markdown) {
        return RECOVERED_BANNER_HTML + new Snudown().markdownToHtml(markdown);
    }

    private static void applyComment(Context context, ArrayAdapter<?> adapter, DataComment comment, JSONObject archived) {
        String body = recoveredText(context, archived, "body", "comment");
        if (body == null || body.isEmpty()) return;

        comment.p = recoveredAuthor(archived, comment.p);
        comment.N = body;
        comment.O = recoveredHtml(body);
        comment.c();
        adapter.notifyDataSetChanged();
        Logger.printDebug(() -> "Recovered comment " + comment.i);
    }

    private static void applyPost(Context context, Object fragment, DataStory post, JSONObject archived) {
        String selfText = recoveredText(context, archived, "selftext", "post");
        if (selfText == null) return;

        post.p = recoveredAuthor(archived, post.p);
        if (selfText.isEmpty()) {
            // Link posts have no self text; the author is all there is to recover.
            toast(context, "Recovered the author: u/" + post.p);
            return;
        }

        post.U = selfText;
        post.T = recoveredHtml(selfText);
        try {
            Method setSelfText = findMethod(fragment.getClass(), SET_SELF_TEXT_METHOD, CharSequence.class);
            setSelfText.invoke(fragment, RedditUtils.j(post.T, true, post.h));
            Logger.printDebug(() -> "Recovered post " + post.i);
        } catch (Exception ex) {
            Logger.printException(() -> "Could not show the recovered post", ex);
        }
    }

    private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            try {
                Field field = c.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static Method findMethod(Class<?> type, String name, Class<?>... parameterTypes) throws NoSuchMethodException {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            try {
                Method method = c.getDeclaredMethod(name, parameterTypes);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
            }
        }
        throw new NoSuchMethodException(name);
    }

    private static void toast(Context context, String message) {
        if (context == null) return;
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
    }
}
