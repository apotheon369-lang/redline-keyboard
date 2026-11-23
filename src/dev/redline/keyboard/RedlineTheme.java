package dev.redline.keyboard;

/**
 * High-contrast AMOLED Dark Sith / Redline Neon palettes.
 * Designed for GPU-accelerated neon glow layers with zero frame drops.
 */
public final class RedlineTheme {
    public final String id;
    public final String name;
    public final String subtitle;

    public final int deckBg;          // Outer keyboard chassis background
    public final int toolbarBg;       // Top command / suggestion strip background
    public final int keyBg;           // Standard letter key fill
    public final int keyPressedBg;    // Pressed key fill
    public final int keyBorder;       // Hairline keycap bevel
    public final int specialKeyBg;    // Shift, Del, Sym, Tab, Ctrl key fill
    public final int actionKeyBg;     // Enter / Go / Send key fill
    public final int actionKeyText;   // Text color on action key
    public final int primaryText;     // Main key label
    public final int hintText;        // Top-right alt-symbol hint
    public final int accentPrimary;   // Primary neon laser (horizon, press glow, caps, swipe outer)
    public final int accentSecondary; // Secondary neon plasma (Ctrl/Alt, corner hints, voice wave)
    public final int neonCore;        // Hot inner core for swipe trail & laser highlights
    public final int popupBg;         // Floating key preview / long-press popup fill
    public final int dividerColor;    // Hairline separator lines
    public final int chipBg;          // Suggestion / clipboard pill background
    public final int chipText;        // Suggestion / clipboard pill text

    private RedlineTheme(
            String id, String name, String subtitle,
            int deckBg, int toolbarBg, int keyBg, int keyPressedBg, int keyBorder,
            int specialKeyBg, int actionKeyBg, int actionKeyText,
            int primaryText, int hintText, int accentPrimary, int accentSecondary,
            int neonCore, int popupBg, int dividerColor, int chipBg, int chipText) {
        this.id = id;
        this.name = name;
        this.subtitle = subtitle;
        this.deckBg = deckBg;
        this.toolbarBg = toolbarBg;
        this.keyBg = keyBg;
        this.keyPressedBg = keyPressedBg;
        this.keyBorder = keyBorder;
        this.specialKeyBg = specialKeyBg;
        this.actionKeyBg = actionKeyBg;
        this.actionKeyText = actionKeyText;
        this.primaryText = primaryText;
        this.hintText = hintText;
        this.accentPrimary = accentPrimary;
        this.accentSecondary = accentSecondary;
        this.neonCore = neonCore;
        this.popupBg = popupBg;
        this.dividerColor = dividerColor;
        this.chipBg = chipBg;
        this.chipText = chipText;
    }

    public static final RedlineTheme REDLINE_CRIMSON = new RedlineTheme(
            "redline_crimson", "REDLINE NEON", "Obsidian deck · Hyper crimson · Amber/Cyan pulse",
            0xFF040407, 0xFF07070D, 0xFF101018, 0xFF330C16, 0xFF282032,
            0xFF171522, 0xFFFF173A, 0xFFFFFFFF,
            0xFFF6F6FC, 0xFFFFB000, 0xFFFF1E42, 0xFF00F0FF,
            0xFFFFD6DF, 0xFF1A0910, 0xFF2A1822, 0xFF13111C, 0xFFF2F2FA
    );

    public static final RedlineTheme SITH_LORD = new RedlineTheme(
            "sith_lord", "SITH PLASMA", "Pure AMOLED #000 · Saber scarlet · Molten core",
            0xFF000000, 0xFF060102, 0xFF0D0709, 0xFF3D060D, 0xFF331118,
            0xFF180A0E, 0xFFE50920, 0xFFFFFFFF,
            0xFFFFF2F4, 0xFFFF5E2B, 0xFFFF0A26, 0xFFFF7A00,
            0xFFFFCDD2, 0xFF1E0509, 0xFF2E0D14, 0xFF14080B, 0xFFFFE5E8
    );

    public static final RedlineTheme CYBER_PULSE = new RedlineTheme(
            "cyber_pulse", "CYBER PULSE", "Deep void · Hot neon magenta · Electric cyan",
            0xFF03040A, 0xFF070814, 0xFF0F1020, 0xFF2D0D2E, 0xFF242444,
            0xFF16162E, 0xFFFF1E8E, 0xFFFFFFFF,
            0xFFF4F6FF, 0xFF00F0FF, 0xFFFF2A9D, 0xFF00F5FF,
            0xFFE0FCFF, 0xFF180A22, 0xFF251A3B, 0xFF121226, 0xFFEDF0FF
    );

    public static final RedlineTheme AMBER_TERMINAL = new RedlineTheme(
            "amber_terminal", "AMBER CRT", "Pitch black · Phosphor amber · Overdrive red",
            0xFF040402, 0xFF0A0804, 0xFF13100A, 0xFF362407, 0xFF362B14,
            0xFF1B160D, 0xFFFFA800, 0xFF080500,
            0xFFFFF3D6, 0xFFFFB82E, 0xFFFFB000, 0xFFFF3B30,
            0xFFFFF0BF, 0xFF221806, 0xFF33260F, 0xFF17130B, 0xFFFFE8B5
    );

    public static final RedlineTheme VENOM_MATRIX = new RedlineTheme(
            "venom_matrix", "MATRIX NEON", "Pure void · Laser emerald · Hyper cyan",
            0xFF020504, 0xFF050B08, 0xFF0B1410, 0xFF0B3020, 0xFF183629,
            0xFF101F18, 0xFF00E676, 0xFF02140A,
            0xFFE8FFF2, 0xFF00E5FF, 0xFF00FF88, 0xFF00E5FF,
            0xFFD4FFEC, 0xFF071E14, 0xFF143325, 0xFF0D1A14, 0xFFDFFFEA
    );

    public static final RedlineTheme STEALTH_OBSIDIAN = new RedlineTheme(
            "stealth_obsidian", "STEALTH OPS", "Pitch black · Tactical steel · Surgical crimson",
            0xFF000000, 0xFF040406, 0xFF0E0E12, 0xFF221419, 0xFF1F1F2B,
            0xFF15151D, 0xFF22222E, 0xFFFF2A3C,
            0xFFEAEAF2, 0xFF7D8299, 0xFFFF2A3C, 0xFFFFB000,
            0xFFFFD0D6, 0xFF15151E, 0xFF1C1C26, 0xFF111118, 0xFFE0E0EC
    );

    public static final RedlineTheme[] ALL = new RedlineTheme[]{
            REDLINE_CRIMSON,
            SITH_LORD,
            CYBER_PULSE,
            AMBER_TERMINAL,
            VENOM_MATRIX,
            STEALTH_OBSIDIAN
    };

    public static RedlineTheme byId(String id) {
        if (id != null) {
            for (RedlineTheme t : ALL) {
                if (t.id.equals(id)) return t;
            }
        }
        return REDLINE_CRIMSON;
    }

    public static int withAlpha(int color, int alpha) {
        int a = Math.max(0, Math.min(255, alpha));
        return (a << 24) | (color & 0x00FFFFFF);
    }
}
