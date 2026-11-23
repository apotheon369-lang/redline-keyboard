package dev.redline.keyboard;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Clean, responsive layouts for QWERTY, SYM1, SYM2, NUMPAD, and the Dev/Terminal bar.
 */
public final class KeyboardLayouts {

    public enum Layer {
        ALPHA,
        SYM1,
        SYM2,
        NUMPAD
    }

    public static List<List<KeySpec>> buildAlpha(boolean includeNumberRow, boolean isUrlOrEmail) {
        List<List<KeySpec>> rows = new ArrayList<>();
        if (includeNumberRow) {
            rows.add(Arrays.asList(
                    KeySpec.sym("1", "!", "¹", "½"),
                    KeySpec.sym("2", "@", "²", "⅔"),
                    KeySpec.sym("3", "#", "³", "¾"),
                    KeySpec.sym("4", "$", "€", "£", "¥"),
                    KeySpec.sym("5", "%", "‰"),
                    KeySpec.sym("6", "^"),
                    KeySpec.sym("7", "&"),
                    KeySpec.sym("8", "*", "•", "×"),
                    KeySpec.sym("9", "(", "[", "{"),
                    KeySpec.sym("0", ")", "]", "}", "°")
            ));
        }

        // Top letter row: if number row is hidden, top-row alts are 1..0; otherwise dev symbols!
        if (!includeNumberRow) {
            rows.add(Arrays.asList(
                    KeySpec.letter('q', "1", "!"),
                    KeySpec.letter('w', "2", "@"),
                    KeySpec.letter('e', "3", "é", "è", "ê", "ë"),
                    KeySpec.letter('r', "4", "$"),
                    KeySpec.letter('t', "5", "%", "™"),
                    KeySpec.letter('y', "6", "^", "ÿ"),
                    KeySpec.letter('u', "7", "&", "ú", "ù", "û", "ü"),
                    KeySpec.letter('i', "8", "*", "í", "ì", "î", "ï"),
                    KeySpec.letter('o', "9", "(", "ó", "ò", "ô", "ö", "ø"),
                    KeySpec.letter('p', "0", ")")
            ));
        } else {
            rows.add(Arrays.asList(
                    KeySpec.letter('q', "~", "`"),
                    KeySpec.letter('w', "\\", "|"),
                    KeySpec.letter('e', "=", "é", "è", "ê", "ë"),
                    KeySpec.letter('r', "{", "["),
                    KeySpec.letter('t', "}", "]"),
                    KeySpec.letter('y', "|", "\\"),
                    KeySpec.letter('u', "_", "ú", "ù", "û", "ü"),
                    KeySpec.letter('i', "<", "í", "ì", "î", "ï"),
                    KeySpec.letter('o', ">", "ó", "ò", "ô", "ö", "ø"),
                    KeySpec.letter('p', "/", "?")
            ));
        }

        // Home row (classic quick-symbol mapping + accents)
        rows.add(Arrays.asList(
                KeySpec.letter('a', "@", "á", "à", "â", "ä", "ã", "æ"),
                KeySpec.letter('s', "#", "ß", "§", "$"),
                KeySpec.letter('d', "$", "€", "£"),
                KeySpec.letter('f', "%", "_"),
                KeySpec.letter('g', "&", "^"),
                KeySpec.letter('h', "-", "—", "–"),
                KeySpec.letter('j', "+", "±"),
                KeySpec.letter('k', "(", "[", "{"),
                KeySpec.letter('l', ")", "]", "}")
        ));

        // Bottom letter row
        rows.add(Arrays.asList(
                KeySpec.special(KeySpec.CODE_SHIFT, "⇧", null, 1.48f, false),
                KeySpec.letter('z', "*", "•"),
                KeySpec.letter('x', "\"", "«", "»"),
                KeySpec.letter('c', "'", "`", "ç", "©"),
                KeySpec.letter('v', ":", ";"),
                KeySpec.letter('b', ";", ":"),
                KeySpec.letter('n', "!", "ñ", "¡"),
                KeySpec.letter('m', "?", "¿"),
                KeySpec.special(KeySpec.CODE_DELETE, "⌫", null, 1.48f, true)
        ));

        // Spacebar row
        KeySpec leftPunct = isUrlOrEmail
                ? KeySpec.symWeighted("/", "@", 1.0f, true, ".", "-", "_")
                : KeySpec.symWeighted(",", "/", 1.0f, true, ";", ":", "-", "_", "(", ")");
        KeySpec rightPunct = isUrlOrEmail
                ? KeySpec.symWeighted(".", ".com", 1.0f, true, ".org", ".io", ".dev", ".net", "/")
                : KeySpec.symWeighted(".", "?", 1.0f, true, "!", "?", ",", ":", ";", "...", "'", "\"", "/");

        rows.add(Arrays.asList(
                KeySpec.special(KeySpec.CODE_MODE_SYM1, "?123", "123", 1.45f, false),
                leftPunct,
                KeySpec.special(KeySpec.CODE_SPACE, "REDLINE", null, 4.7f, false),
                rightPunct,
                KeySpec.special(KeySpec.CODE_ENTER, "↵", null, 1.65f, false)
        ));
        return rows;
    }

    public static List<List<KeySpec>> buildSym1() {
        List<List<KeySpec>> rows = new ArrayList<>();
        rows.add(Arrays.asList(
                KeySpec.sym("1", "¹"), KeySpec.sym("2", "²"), KeySpec.sym("3", "³"),
                KeySpec.sym("4", "¼"), KeySpec.sym("5", "½"), KeySpec.sym("6", "¾"),
                KeySpec.sym("7", null), KeySpec.sym("8", "∞"), KeySpec.sym("9", null),
                KeySpec.sym("0", "°")
        ));
        rows.add(Arrays.asList(
                KeySpec.sym("@", null), KeySpec.sym("#", "№"), KeySpec.sym("$", "€", "£", "¥", "₿"),
                KeySpec.sym("_", "—"), KeySpec.sym("&", "§"), KeySpec.sym("-", "–", "—"),
                KeySpec.sym("+", "±"), KeySpec.sym("(", "<", "{", "["), KeySpec.sym(")", ">", "}", "]"),
                KeySpec.sym("/", "\\")
        ));
        rows.add(Arrays.asList(
                KeySpec.special(KeySpec.CODE_MODE_SYM2, "=\\<", null, 1.48f, false),
                KeySpec.sym("*", "×", "•"), KeySpec.sym("\"", "«", "»"), KeySpec.sym("'", "`"),
                KeySpec.sym(":", "::"), KeySpec.sym(";", null), KeySpec.sym("!", "¡"),
                KeySpec.sym("?", "¿"),
                KeySpec.special(KeySpec.CODE_DELETE, "⌫", null, 1.48f, true)
        ));
        rows.add(Arrays.asList(
                KeySpec.special(KeySpec.CODE_MODE_ALPHA, "ABC", null, 1.45f, false),
                KeySpec.symWeighted(",", ";", 1.0f, true),
                KeySpec.special(KeySpec.CODE_SPACE, "SPACE", null, 4.7f, false),
                KeySpec.symWeighted(".", "...", 1.0f, true),
                KeySpec.special(KeySpec.CODE_ENTER, "↵", null, 1.65f, false)
        ));
        return rows;
    }

    public static List<List<KeySpec>> buildSym2() {
        List<List<KeySpec>> rows = new ArrayList<>();
        rows.add(Arrays.asList(
                KeySpec.sym("~", "≈"), KeySpec.sym("`", "☠", "☢", "☣"), KeySpec.sym("|", "¦", "║"),
                KeySpec.sym("•", "◦", "◆", "★"), KeySpec.sym("√", "∞", "∑"), KeySpec.sym("π", "λ", "Ω", "Δ"),
                KeySpec.sym("÷", "±"), KeySpec.sym("×", "✕", "†", "‡"), KeySpec.sym("§", "¶", "©", "®", "™"),
                KeySpec.sym("Δ", "Ω", "Σ", "λ")
        ));
        rows.add(Arrays.asList(
                KeySpec.sym("£", "¢"), KeySpec.sym("€", "₿"), KeySpec.sym("¥", "₹", "₩"),
                KeySpec.sym("^", "⌃"), KeySpec.sym("°", "℃", "℉"), KeySpec.sym("=", "≠", "≈", "≡", "=="),
                KeySpec.sym("{", "⟨", "「", "【"), KeySpec.sym("}", "⟩", "」", "】"), KeySpec.sym("\\", "╱", "╲"),
                KeySpec.sym("%", "‰")
        ));
        rows.add(Arrays.asList(
                KeySpec.special(KeySpec.CODE_MODE_SYM1, "?123", null, 1.48f, false),
                KeySpec.sym("[", "«", "‹"), KeySpec.sym("]", "»", "›"), KeySpec.sym("<", "≤", "≪"),
                KeySpec.sym(">", "≥", "≫"), KeySpec.sym("←", "↔", "⇐"), KeySpec.sym("→", "⇒", "➜", "➤"),
                KeySpec.sym("⚡", "☠", "☢", "☣", "⚔", "★", "✓", "✗", "⌘"),
                KeySpec.special(KeySpec.CODE_DELETE, "⌫", null, 1.48f, true)
        ));
        rows.add(Arrays.asList(
                KeySpec.special(KeySpec.CODE_MODE_ALPHA, "ABC", null, 1.45f, false),
                KeySpec.symWeighted("<", "≤", 1.0f, true),
                KeySpec.special(KeySpec.CODE_SPACE, "SPACE", null, 4.7f, false),
                KeySpec.symWeighted(">", "≥", 1.0f, true),
                KeySpec.special(KeySpec.CODE_ENTER, "↵", null, 1.65f, false)
        ));
        return rows;
    }

    public static List<List<KeySpec>> buildNumpad() {
        List<List<KeySpec>> rows = new ArrayList<>();
        rows.add(Arrays.asList(
                KeySpec.sym("1", null), KeySpec.sym("2", null), KeySpec.sym("3", null),
                KeySpec.symWeighted("-", "+", 0.9f, true)
        ));
        rows.add(Arrays.asList(
                KeySpec.sym("4", null), KeySpec.sym("5", null), KeySpec.sym("6", null),
                KeySpec.symWeighted("(", ")", 0.9f, true)
        ));
        rows.add(Arrays.asList(
                KeySpec.sym("7", null), KeySpec.sym("8", null), KeySpec.sym("9", null),
                KeySpec.special(KeySpec.CODE_DELETE, "⌫", null, 0.9f, true)
        ));
        rows.add(Arrays.asList(
                KeySpec.special(KeySpec.CODE_MODE_ALPHA, "ABC", null, 0.9f, false),
                KeySpec.sym("0", "+"),
                KeySpec.sym(".", ","),
                KeySpec.special(KeySpec.CODE_ENTER, "↵", null, 1.0f, false)
        ));
        return rows;
    }

    public static List<KeySpec> buildDevBar() {
        return Arrays.asList(
                KeySpec.special(KeySpec.CODE_ESC, "ESC", null, 1.0f, false),
                KeySpec.special(KeySpec.CODE_TAB, "TAB", null, 1.0f, false),
                KeySpec.special(KeySpec.CODE_CTRL, "CTRL", null, 1.15f, false),
                KeySpec.special(KeySpec.CODE_ALT, "ALT", null, 0.95f, false),
                KeySpec.special(KeySpec.CODE_LEFT, "◀", null, 0.85f, true),
                KeySpec.special(KeySpec.CODE_DOWN, "▼", null, 0.85f, true),
                KeySpec.special(KeySpec.CODE_UP, "▲", null, 0.85f, true),
                KeySpec.special(KeySpec.CODE_RIGHT, "▶", null, 0.85f, true),
                KeySpec.special(KeySpec.CODE_HOME, "HM", null, 0.85f, false),
                KeySpec.special(KeySpec.CODE_END, "END", null, 0.95f, false),
                KeySpec.special(KeySpec.CODE_PASTE, "PST", null, 0.95f, false)
        );
    }

    public static final String[] GLYPH_TAB_LABELS = new String[]{
            "☠ SYM", "∑ MTH", "→ BOX", "☺ EMJ", "ツ KAO"
    };

    // Tab 0: Skulls, Hazard, Gothic/Occult, Stars, Suits, Chess, Legal, Keyboard/System, Zodiac, Music
    public static final String[] GLYPH_SYMBOLS = new String[]{
            "☠", "☢", "☣", "⚡", "⚔", "⛧", "☥", "☦",
            "☧", "☨", "☩", "☪", "☯", "☮", "☸", "⚜",
            "⚚", "⚠", "⛔", "✕", "✗", "✘", "✓", "✔",
            "★", "☆", "✦", "✧", "✩", "✪", "✫", "✬",
            "✭", "✮", "✯", "✰", "✴", "✵", "✶", "✷",
            "✸", "✹", "✺", "✻", "❄", "❅", "❆", "☄",
            "♠", "♣", "♥", "♦", "♤", "♧", "♡", "♢",
            "♔", "♕", "♖", "♗", "♘", "♙", "♚", "♛",
            "♜", "♝", "♞", "♟", "†", "‡", "§", "¶",
            "©", "®", "™", "℠", "℗", "№", "℃", "℉",
            "°", "′", "″", "⌘", "⌥", "⌃", "⇧", "⇪",
            "⏎", "⌫", "⌦", "⎋", "⇥", "⏻", "⏼", "⌖",
            "⌚", "⌛", "☎", "✉", "✂", "✎", "✒", "♾",
            "♪", "♫", "♬", "♭", "♮", "♯", "⛨", "⚓",
            "⚙", "⚛", "⚖", "⛓", "⚰", "⚱", "☽", "☾",
            "☉", "♀", "♂", "♈", "♉", "♊", "♋", "♌",
            "♍", "♎", "♏", "♐", "♑", "♒", "♓", "⚐"
    };

    // Tab 1: Math, Logic, Sets, Greek, Superscripts, Subscripts, Fractions, Currencies
    public static final String[] GLYPH_MATH = new String[]{
            "±", "×", "÷", "≠", "≈", "≡", "≢", "≤",
            "≥", "≪", "≫", "∞", "√", "∛", "∜", "∑",
            "∏", "∫", "∮", "∂", "∇", "∆", "∈", "∉",
            "∋", "∅", "∩", "∪", "⊂", "⊃", "⊆", "⊇",
            "∀", "∃", "∄", "¬", "∧", "∨", "⊕", "⊗",
            "⊥", "⊢", "⊨", "∴", "∵", "∝", "∠", "∥",
            "‰", "‱", "α", "β", "γ", "δ", "ε", "ζ",
            "η", "θ", "ι", "κ", "λ", "μ", "ν", "ξ",
            "π", "ρ", "σ", "ς", "τ", "υ", "φ", "χ",
            "ψ", "ω", "Γ", "Δ", "Θ", "Λ", "Ξ", "Π",
            "Σ", "Φ", "Ψ", "Ω", "⁰", "¹", "²", "³",
            "⁴", "⁵", "⁶", "⁷", "⁸", "⁹", "⁺", "⁻",
            "ⁿ", "₀", "₁", "₂", "₃", "₄", "₅", "₆",
            "₇", "₈", "₉", "½", "⅓", "⅔", "¼", "¾",
            "⅕", "⅛", "⅜", "⅝", "⅞", "$", "€", "£",
            "¥", "¢", "₹", "₽", "₩", "₿", "₪", "₫",
            "฿", "₺", "₴", "₦", "₱", "₡", "₮", "¤"
    };

    // Tab 2: Arrows, Geometric Shapes, Bullets, Shading Blocks, Box Drawing, Fancy Brackets & Quotes
    public static final String[] GLYPH_ARROWS_BOX = new String[]{
            "←", "→", "↑", "↓", "↔", "↕", "↖", "↗",
            "↘", "↙", "⇐", "⇒", "⇑", "⇓", "⇔", "⇕",
            "➔", "➜", "➝", "➞", "➤", "➥", "➦", "↩",
            "↪", "↺", "↻", "⇄", "⇅", "⇆", "⇋", "⇌",
            "⇤", "⇥", "⤒", "⤓", "•", "◦", "‣", "⁃",
            "∙", "●", "○", "◉", "◎", "◌", "◍", "◐",
            "◑", "◒", "◓", "■", "□", "▪", "▫", "▬",
            "▭", "▮", "▯", "◆", "◇", "◈", "❖", "▲",
            "△", "▴", "▵", "▶", "▷", "▸", "▹", "▼",
            "▽", "▾", "▿", "◀", "◁", "◂", "◃", "⬡",
            "⬢", "░", "▒", "▓", "█", "▀", "▄", "▌",
            "▐", "─", "│", "┌", "┐", "└", "┘", "├",
            "┤", "┬", "┴", "┼", "═", "║", "╔", "╗",
            "╚", "╝", "╠", "╣", "╦", "╩", "╬", "╱",
            "╲", "╳", "«", "»", "‹", "›", "“", "”",
            "‘", "’", "—", "–", "…", "¡", "¿", "‽",
            "⟨", "⟩", "⟪", "⟫", "「", "」", "『", "』",
            "【", "】", "〔", "〕", "〖", "〗", "⦅", "⦆"
    };

    // Tab 3: Expanded Emojis
    public static final String[] GLYPH_EMOJI = new String[]{
            "💀", "☠️", "🔥", "⚡", "😈", "👹", "👺", "👻",
            "👽", "🤖", "👾", "👀", "🧠", "🫀", "🩸", "🖤",
            "❤️‍🔥", "❤️", "💔", "💯", "😂", "🤣", "😭", "😎",
            "🫡", "🤔", "😤", "🤯", "🥶", "🥵", "🤡", "🤫",
            "🫠", "🤝", "👍", "👎", "👊", "✌️", "🤘", "🤙",
            "🖕", "🙏", "💪", "🚀", "🎯", "⚔️", "🗡️", "🛡️",
            "🔫", "💣", "🧨", "🔪", "⛓️", "🔐", "🔑", "💻",
            "🖥️", "📱", "📡", "🛰️", "🔧", "⚙️", "🧪", "🧬",
            "⚠️", "🚨", "☢️", "☣️", "✅", "❌", "⭕", "⭐",
            "🌟", "💫", "✨", "💎", "👑", "🏴", "🏴‍☠️", "🕶️",
            "🎧", "🎮", "🕹️", "🎲", "🃏", "🎵", "🔊", "🔋"
    };

    // Tab 4: Kaomojis & Text Banners (rendered wide)
    public static final String[] GLYPH_KAOMOJI = new String[]{
            "¯\\_(ツ)_/¯", "(╯°□°)╯︵ ┻━┻",
            "┬─┬ノ( º _ ºノ)", "(⌐■_■)",
            "ಠ_ಠ", "(¬_¬)",
            "(•_•)", "ʕ•ᴥ•ʔ",
            "(づ｡◕‿‿◕｡)づ", "ヽ(´▽`)/",
            "(☞ﾟヮﾟ)☞", "☜(ﾟヮﾟ☜)",
            "(×_×)", "(⊙_☉)",
            "(ง'̀-'́)ง", "╰(*°▽°*)╯",
            "╚(ಠ_ಠ)=┐", "[̲̅$̲̅(̲̅ιοο̲̅)̲̅$̲̅]",
            "☠──⚡──☠", "═══❖═══"
    };

    public static final String[][] GLYPH_PAGES = new String[][]{
            GLYPH_SYMBOLS,
            GLYPH_MATH,
            GLYPH_ARROWS_BOX,
            GLYPH_EMOJI,
            GLYPH_KAOMOJI
    };

    public static final String[] EMOJI_GRID = GLYPH_EMOJI;
}
