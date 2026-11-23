package dev.redline.keyboard;

import android.graphics.RectF;

/**
 * Represents a single rendered key cap on the keyboard canvas.
 */
public final class KeySpec {
    public static final int CODE_SHIFT = -1;
    public static final int CODE_DELETE = -2;
    public static final int CODE_MODE_SYM1 = -3;
    public static final int CODE_MODE_SYM2 = -4;
    public static final int CODE_MODE_ALPHA = -5;
    public static final int CODE_ENTER = -6;
    public static final int CODE_SPACE = 32;
    public static final int CODE_EMOJI = -7;
    public static final int CODE_SWITCH_IME = -8;
    public static final int CODE_MODE_NUMPAD = -9;

    // Dev / Terminal toolbar codes
    public static final int CODE_ESC = -20;
    public static final int CODE_TAB = -21;
    public static final int CODE_CTRL = -22;
    public static final int CODE_ALT = -23;
    public static final int CODE_LEFT = -24;
    public static final int CODE_DOWN = -25;
    public static final int CODE_UP = -26;
    public static final int CODE_RIGHT = -27;
    public static final int CODE_HOME = -28;
    public static final int CODE_END = -29;
    public static final int CODE_PASTE = -30;
    public static final int CODE_COPY = -31;
    public static final int CODE_SELECT_ALL = -32;

    public final int code;          // Unicode char code (>0) or special negative code
    public final String label;      // Display text
    public final String altText;    // Swipe-up / primary long-press text (shown in corner)
    public final String[] popups;   // Sliding long-press popup choices (first is altText)
    public final float weight;      // Relative horizontal width weight within row
    public final boolean special;   // Styled as special/modifier keycap
    public final boolean repeatable;// Supports hold-to-repeat (Delete, Arrows)

    // Computed layout rect in view coordinates
    public final RectF bounds = new RectF();
    public final RectF drawRect = new RectF();

    public KeySpec(int code, String label, String altText, String[] popups,
                   float weight, boolean special, boolean repeatable) {
        this.code = code;
        this.label = label;
        this.altText = altText;
        this.popups = popups;
        this.weight = weight;
        this.special = special;
        this.repeatable = repeatable;
    }

    public static KeySpec letter(char ch, String alt, String... extraPopups) {
        String[] pop = null;
        if (alt != null || (extraPopups != null && extraPopups.length > 0)) {
            int n = (alt != null ? 1 : 0) + (extraPopups != null ? extraPopups.length : 0);
            pop = new String[n];
            int idx = 0;
            if (alt != null) pop[idx++] = alt;
            if (extraPopups != null) {
                for (String s : extraPopups) pop[idx++] = s;
            }
        }
        return new KeySpec(ch, String.valueOf(ch), alt, pop, 1.0f, false, false);
    }

    public static KeySpec sym(String text, String alt, String... extraPopups) {
        int code = text.length() == 1 ? text.charAt(0) : 0;
        String[] pop = null;
        if (alt != null || (extraPopups != null && extraPopups.length > 0)) {
            int n = (alt != null ? 1 : 0) + (extraPopups != null ? extraPopups.length : 0);
            pop = new String[n];
            int idx = 0;
            if (alt != null) pop[idx++] = alt;
            if (extraPopups != null) {
                for (String s : extraPopups) pop[idx++] = s;
            }
        }
        return new KeySpec(code, text, alt, pop, 1.0f, false, false);
    }

    public static KeySpec symWeighted(String text, String alt, float weight, boolean special, String... extraPopups) {
        int code = text.length() == 1 ? text.charAt(0) : 0;
        String[] pop = null;
        if (alt != null || (extraPopups != null && extraPopups.length > 0)) {
            int n = (alt != null ? 1 : 0) + (extraPopups != null ? extraPopups.length : 0);
            pop = new String[n];
            int idx = 0;
            if (alt != null) pop[idx++] = alt;
            if (extraPopups != null) {
                for (String s : extraPopups) pop[idx++] = s;
            }
        }
        return new KeySpec(code, text, alt, pop, weight, special, false);
    }

    public static KeySpec special(int code, String label, String alt, float weight, boolean repeatable) {
        String[] pop = alt != null ? new String[]{alt} : null;
        return new KeySpec(code, label, alt, pop, weight, true, repeatable);
    }

    public boolean isLetter() {
        return code >= 'a' && code <= 'z';
    }
}
