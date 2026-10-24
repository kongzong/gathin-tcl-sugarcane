package com.gathin.tvhome;

import android.content.Context;
import android.content.res.AssetManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Ships the wallpaper collection and theme config inside the APK. On first run it copies them into
 * {@link Config#dir}, which the app owns and can read; afterwards the normal external-dir loading
 * takes over, so an owner can still override by pushing files there. Skips when the directory already
 * holds a collection (a previous seed or an owner override), so it runs at most once.
 */
final class ThemeBootstrap {

    private ThemeBootstrap() {}

    static void seedFromAssets(Context ctx) {
        File dir = Config.dir(ctx);
        if (dir == null) return;
        File wallpapers = new File(dir, "wallpapers");

        AssetManager am = ctx.getAssets();
        try {
            if (!wallpapers.exists() && !wallpapers.mkdirs()) return;
            String[] images = am.list("wallpapers");
            if (images != null) {
                for (String name : images) {
                    if (!ThemeFiles.collectionImage(name)) continue;
                    File out = new File(wallpapers, name);
                    if (!out.isFile()) copy(am, "wallpapers/" + name, out); // wallpapers only when missing
                }
            }
            // Bundled config.json (rotation / dim / true colour): always refresh so theme changes apply on update.
            copy(am, Config.FILE_NAME, new File(dir, Config.FILE_NAME));
        } catch (Exception ignored) {
            // storage unavailable: the launcher still runs, just without the bundled wallpaper
        }
    }

    private static boolean hasCollection(File wallpapers) {
        if (!wallpapers.isDirectory()) return false;
        File[] found = wallpapers.listFiles((d, n) -> {
            String s = n.toLowerCase();
            return s.endsWith(".jpg") || s.endsWith(".jpeg") || s.endsWith(".png") || s.endsWith(".webp");
        });
        return found != null && found.length > 0;
    }

    private static void copy(AssetManager am, String asset, File out) {
        try (InputStream in = am.open(asset); OutputStream os = new FileOutputStream(out)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) os.write(buf, 0, n);
        } catch (Exception ignored) {
        }
    }
}
