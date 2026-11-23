package dev.redline.keyboard;

import android.content.Context;
import android.graphics.Typeface;
import android.net.Uri;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Bundled pro cyberpunk/terminal Typeface manager + custom user .ttf/.otf loader.
 * Caches Typefaces in memory so onDraw() performs zero allocations.
 */
public final class RedlineFonts {

    public static final class FontOption {
        public final String id;
        public final String name;
        public final String subtitle;
        public final String assetPath; // null for system or custom

        public FontOption(String id, String name, String subtitle, String assetPath) {
            this.id = id;
            this.name = name;
            this.subtitle = subtitle;
            this.assetPath = assetPath;
        }
    }

    public static final String ID_RAJDHANI = "rajdhani";
    public static final String ID_ORBITRON = "orbitron";
    public static final String ID_OXANIUM = "oxanium";
    public static final String ID_JETBRAINS = "jetbrainsmono";
    public static final String ID_SHARETECH = "sharetechmono";
    public static final String ID_SYSTEM = "system_sans";
    public static final String ID_CUSTOM = "custom_file";

    public static final FontOption[] BUILT_IN = new FontOption[]{
            new FontOption(ID_RAJDHANI, "TACTICAL TECH", "Rajdhani · Razor angular cyberpunk", "fonts/rajdhani.ttf"),
            new FontOption(ID_OXANIUM, "PLASMA DISPLAY", "Oxanium · Futuristic neon geometry", "fonts/oxanium.ttf"),
            new FontOption(ID_ORBITRON, "CYBER HUD", "Orbitron · Bold Sith sci-fi display", "fonts/orbitron.ttf"),
            new FontOption(ID_JETBRAINS, "REDLINE MONO", "JetBrains Mono · Crisp dev monospace", "fonts/jetbrainsmono.ttf"),
            new FontOption(ID_SHARETECH, "TERMINAL CRT", "Share Tech Mono · Retro hacker CRT", "fonts/sharetechmono.ttf"),
            new FontOption(ID_SYSTEM, "CLEAN SANS", "Android System Sans · Minimal neutral", null)
    };

    private static final String CUSTOM_FONT_FILENAME = "custom_user_font.ttf";
    private static final Map<String, Typeface> CACHE = new HashMap<>();

    public static File getCustomFontFile(Context ctx) {
        return new File(ctx.getApplicationContext().getFilesDir(), CUSTOM_FONT_FILENAME);
    }

    public static boolean hasCustomFont(Context ctx) {
        File f = getCustomFontFile(ctx);
        return f.exists() && f.length() > 1024;
    }

    public static boolean importCustomFont(Context ctx, Uri uri) {
        if (uri == null) return false;
        File tmp = new File(ctx.getApplicationContext().getFilesDir(), CUSTOM_FONT_FILENAME + ".tmp");
        File dest = getCustomFontFile(ctx);
        try (InputStream in = ctx.getContentResolver().openInputStream(uri);
             FileOutputStream out = new FileOutputStream(tmp)) {
            if (in == null) return false;
            byte[] buf = new byte[8192];
            int total = 0;
            int n;
            while ((n = in.read(buf)) > 0) {
                total += n;
                if (total > 12 * 1024 * 1024) { // 12MB safety cap
                    tmp.delete();
                    return false;
                }
                out.write(buf, 0, n);
            }
            out.flush();
            // Validate font parses cleanly
            Typeface test = Typeface.createFromFile(tmp);
            if (test == null) {
                tmp.delete();
                return false;
            }
            if (dest.exists()) dest.delete();
            if (!tmp.renameTo(dest)) {
                tmp.delete();
                return false;
            }
            synchronized (CACHE) {
                CACHE.remove(ID_CUSTOM + "_0");
                CACHE.remove(ID_CUSTOM + "_1");
            }
            return true;
        } catch (Throwable t) {
            tmp.delete();
            return false;
        }
    }

    public static Typeface load(Context ctx, String fontId, boolean bold) {
        if (fontId == null) fontId = ID_RAJDHANI;
        String cacheKey = fontId + (bold ? "_1" : "_0");
        synchronized (CACHE) {
            Typeface cached = CACHE.get(cacheKey);
            if (cached != null) return cached;
        }

        Typeface base = null;
        if (ID_CUSTOM.equals(fontId)) {
            File f = getCustomFontFile(ctx);
            if (f.exists()) {
                try {
                    base = Typeface.createFromFile(f);
                } catch (Throwable ignored) {}
            }
        } else {
            for (FontOption opt : BUILT_IN) {
                if (opt.id.equals(fontId) && opt.assetPath != null) {
                    try {
                        base = Typeface.createFromAsset(ctx.getApplicationContext().getAssets(), opt.assetPath);
                    } catch (Throwable ignored) {}
                    break;
                }
            }
        }

        if (base == null) {
            base = Typeface.SANS_SERIF;
        }
        Typeface finalTf = bold ? Typeface.create(base, Typeface.BOLD) : base;
        synchronized (CACHE) {
            CACHE.put(cacheKey, finalTf);
        }
        return finalTf;
    }
}
