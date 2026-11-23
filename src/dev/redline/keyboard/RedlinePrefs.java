package dev.redline.keyboard;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Local-only SharedPreferences store for Redline Keyboard v2.
 */
public final class RedlinePrefs {
    private static final String PREFS_NAME = "redline_keyboard_prefs";

    public static final String KEY_THEME = "theme_id";
    public static final String KEY_FONT = "font_id";
    public static final String KEY_CUSTOM_FONT_NAME = "custom_font_name";
    public static final String KEY_FONT_SCALE_PCT = "font_scale_pct";     // 80..135 (default 105)
    public static final String KEY_BOLD_KEYS = "bold_keys";               // default true
    public static final String KEY_NEON_INTENSITY = "neon_intensity";     // 0..100 (default 80)
    public static final String KEY_SWIPE_TYPING = "swipe_typing";         // default true
    public static final String KEY_AUTO_CORRECT = "auto_correct";         // default true
    public static final String KEY_NEXT_WORD = "next_word_predict";       // default true
    public static final String KEY_VOICE_OFFLINE = "voice_offline";       // default false
    public static final String KEY_VOICE_SMART_PUNCT = "voice_smart_punct"; // default true
    public static final String KEY_VOICE_FG_MODE = "voice_fg_mode";       // default false (auto-fallback)
    public static final String KEY_HEIGHT_PCT = "height_pct";             // 85..130 (default 104)
    public static final String KEY_NUMBER_ROW = "number_row";             // default true
    public static final String KEY_DEV_BAR_DEFAULT = "dev_bar_default";   // default false
    public static final String KEY_AUTO_DEV_TERMUX = "auto_dev_termux";   // default true
    public static final String KEY_SWIPE_UP_SYM = "swipe_up_sym";         // default true
    public static final String KEY_SPACE_CURSOR = "space_cursor";         // default true
    public static final String KEY_DOUBLE_SPACE_DOT = "double_space_dot"; // default true
    public static final String KEY_AUTO_CAPS = "auto_caps";               // default true
    public static final String KEY_SUGGESTIONS = "suggestions";           // default true
    public static final String KEY_KEY_POPUP = "key_popup";               // default true
    public static final String KEY_HAPTIC_MS = "haptic_ms";               // 0..35 (default 12)
    public static final String KEY_KEY_RADIUS_DP = "key_radius_dp";       // 2..14 (default 6)
    public static final String KEY_SNIPPETS = "custom_snippets_v1";
    public static final String KEY_PINNED_CLIPS = "pinned_clips_v1";

    private static final String SEP = "\u001F";

    private static final String[] DEFAULT_SNIPPETS = new String[]{
            "sudo ",
            "git status",
            "git diff --stat",
            "ls -la ",
            "| grep -i ",
            "() => {}",
            "chmod +x ",
            "curl -sL ",
            "#!/usr/bin/env bash\n",
            "https://",
            "127.0.0.1:",
            "¯\\_(ツ)_/¯"
    };

    private final SharedPreferences sp;

    public RedlinePrefs(Context ctx) {
        this.sp = ctx.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public RedlineTheme getTheme() {
        return RedlineTheme.byId(sp.getString(KEY_THEME, RedlineTheme.REDLINE_CRIMSON.id));
    }

    public void setThemeId(String id) {
        sp.edit().putString(KEY_THEME, id).apply();
    }

    public String getFontId() {
        return sp.getString(KEY_FONT, RedlineFonts.ID_RAJDHANI);
    }

    public void setFontId(String id) {
        sp.edit().putString(KEY_FONT, id).apply();
    }

    public String getCustomFontName() {
        return sp.getString(KEY_CUSTOM_FONT_NAME, "Custom Font");
    }

    public void setCustomFontName(String name) {
        sp.edit().putString(KEY_CUSTOM_FONT_NAME, name).apply();
    }

    public int getFontScalePct() {
        return clamp(sp.getInt(KEY_FONT_SCALE_PCT, 105), 80, 135);
    }

    public void setFontScalePct(int pct) {
        sp.edit().putInt(KEY_FONT_SCALE_PCT, clamp(pct, 80, 135)).apply();
    }

    public boolean isBoldKeys() {
        return sp.getBoolean(KEY_BOLD_KEYS, true);
    }

    public void setBoldKeys(boolean v) {
        sp.edit().putBoolean(KEY_BOLD_KEYS, v).apply();
    }

    public int getNeonIntensity() {
        return clamp(sp.getInt(KEY_NEON_INTENSITY, 82), 0, 100);
    }

    public void setNeonIntensity(int v) {
        sp.edit().putInt(KEY_NEON_INTENSITY, clamp(v, 0, 100)).apply();
    }

    public boolean isSwipeTypingEnabled() {
        return sp.getBoolean(KEY_SWIPE_TYPING, true);
    }

    public void setSwipeTypingEnabled(boolean v) {
        sp.edit().putBoolean(KEY_SWIPE_TYPING, v).apply();
    }

    public boolean isAutoCorrectEnabled() {
        return sp.getBoolean(KEY_AUTO_CORRECT, true);
    }

    public void setAutoCorrectEnabled(boolean v) {
        sp.edit().putBoolean(KEY_AUTO_CORRECT, v).apply();
    }

    public boolean isNextWordEnabled() {
        return sp.getBoolean(KEY_NEXT_WORD, true);
    }

    public void setNextWordEnabled(boolean v) {
        sp.edit().putBoolean(KEY_NEXT_WORD, v).apply();
    }

    public boolean isVoiceOfflineOnly() {
        return sp.getBoolean(KEY_VOICE_OFFLINE, false);
    }

    public void setVoiceOfflineOnly(boolean v) {
        sp.edit().putBoolean(KEY_VOICE_OFFLINE, v).apply();
    }

    public boolean isVoiceSmartPunct() {
        return sp.getBoolean(KEY_VOICE_SMART_PUNCT, true);
    }

    public void setVoiceSmartPunct(boolean v) {
        sp.edit().putBoolean(KEY_VOICE_SMART_PUNCT, v).apply();
    }

    public boolean isVoiceForegroundMode() {
        return sp.getBoolean(KEY_VOICE_FG_MODE, false);
    }

    public void setVoiceForegroundMode(boolean v) {
        sp.edit().putBoolean(KEY_VOICE_FG_MODE, v).apply();
    }

    public int getHeightPct() {
        return clamp(sp.getInt(KEY_HEIGHT_PCT, 104), 85, 130);
    }

    public void setHeightPct(int pct) {
        sp.edit().putInt(KEY_HEIGHT_PCT, clamp(pct, 85, 130)).apply();
    }

    public boolean isNumberRowEnabled() {
        return sp.getBoolean(KEY_NUMBER_ROW, true);
    }

    public void setNumberRowEnabled(boolean v) {
        sp.edit().putBoolean(KEY_NUMBER_ROW, v).apply();
    }

    public boolean isDevBarDefault() {
        return sp.getBoolean(KEY_DEV_BAR_DEFAULT, false);
    }

    public void setDevBarDefault(boolean v) {
        sp.edit().putBoolean(KEY_DEV_BAR_DEFAULT, v).apply();
    }

    public boolean isAutoDevTermux() {
        return sp.getBoolean(KEY_AUTO_DEV_TERMUX, true);
    }

    public void setAutoDevTermux(boolean v) {
        sp.edit().putBoolean(KEY_AUTO_DEV_TERMUX, v).apply();
    }

    public boolean isSwipeUpSymEnabled() {
        return sp.getBoolean(KEY_SWIPE_UP_SYM, true);
    }

    public void setSwipeUpSymEnabled(boolean v) {
        sp.edit().putBoolean(KEY_SWIPE_UP_SYM, v).apply();
    }

    public boolean isSpaceCursorEnabled() {
        return sp.getBoolean(KEY_SPACE_CURSOR, true);
    }

    public void setSpaceCursorEnabled(boolean v) {
        sp.edit().putBoolean(KEY_SPACE_CURSOR, v).apply();
    }

    public boolean isDoubleSpaceDotEnabled() {
        return sp.getBoolean(KEY_DOUBLE_SPACE_DOT, true);
    }

    public void setDoubleSpaceDotEnabled(boolean v) {
        sp.edit().putBoolean(KEY_DOUBLE_SPACE_DOT, v).apply();
    }

    public boolean isAutoCapsEnabled() {
        return sp.getBoolean(KEY_AUTO_CAPS, true);
    }

    public void setAutoCapsEnabled(boolean v) {
        sp.edit().putBoolean(KEY_AUTO_CAPS, v).apply();
    }

    public boolean isSuggestionsEnabled() {
        return sp.getBoolean(KEY_SUGGESTIONS, true);
    }

    public void setSuggestionsEnabled(boolean v) {
        sp.edit().putBoolean(KEY_SUGGESTIONS, v).apply();
    }

    public boolean isKeyPopupEnabled() {
        return sp.getBoolean(KEY_KEY_POPUP, true);
    }

    public void setKeyPopupEnabled(boolean v) {
        sp.edit().putBoolean(KEY_KEY_POPUP, v).apply();
    }

    public int getHapticMs() {
        return clamp(sp.getInt(KEY_HAPTIC_MS, 12), 0, 35);
    }

    public void setHapticMs(int ms) {
        sp.edit().putInt(KEY_HAPTIC_MS, clamp(ms, 0, 35)).apply();
    }

    public int getKeyRadiusDp() {
        return clamp(sp.getInt(KEY_KEY_RADIUS_DP, 6), 2, 14);
    }

    public void setKeyRadiusDp(int dp) {
        sp.edit().putInt(KEY_KEY_RADIUS_DP, clamp(dp, 2, 14)).apply();
    }

    public List<String> getSnippets() {
        String raw = sp.getString(KEY_SNIPPETS, null);
        if (raw == null || raw.isEmpty()) {
            return new ArrayList<>(Arrays.asList(DEFAULT_SNIPPETS));
        }
        List<String> out = new ArrayList<>();
        for (String part : raw.split(SEP)) {
            if (!part.isEmpty()) out.add(part);
        }
        return out;
    }

    public void setSnippets(List<String> snippets) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < snippets.size(); i++) {
            if (i > 0) sb.append(SEP);
            sb.append(snippets.get(i).replace(SEP, ""));
        }
        sp.edit().putString(KEY_SNIPPETS, sb.toString()).apply();
    }

    public List<String> getPinnedClips() {
        String raw = sp.getString(KEY_PINNED_CLIPS, "");
        List<String> out = new ArrayList<>();
        if (raw != null && !raw.isEmpty()) {
            for (String part : raw.split(SEP)) {
                if (!part.isEmpty()) out.add(part);
            }
        }
        return out;
    }

    public void setPinnedClips(List<String> clips) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < clips.size(); i++) {
            if (i > 0) sb.append(SEP);
            sb.append(clips.get(i).replace(SEP, ""));
        }
        sp.edit().putString(KEY_PINNED_CLIPS, sb.toString()).apply();
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
