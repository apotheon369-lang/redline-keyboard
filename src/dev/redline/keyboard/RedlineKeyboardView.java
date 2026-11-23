package dev.redline.keyboard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Redline Keyboard v2.0 Hardware-Accelerated Neon Canvas View.
 *
 * Features:
 * - Zero-lag GPU-native neon glow (translucent multi-pass strokes & gradients, no software blur)
 * - Fast 150ms neon keycap afterglow pulse on tap
 * - Multi-layer neon plasma ribbon trail for high-accuracy glide/swipe typing
 * - Live AI Voice neon HUD with real-time 14-bar audio equalizer + partial transcript streaming
 * - Custom bundled & user-imported Typeface + scalable font sizing
 * - Swipe-up alt symbols, sliding long-press popup, spacebar cursor trackpad, swipe-left word delete
 * - Inline Clipboard, Snippets, Emoji drawers, and Termux Dev toolbar
 */
public final class RedlineKeyboardView extends View {

    public interface Listener {
        void onKeyPress(KeySpec key, boolean shifted);
        void onCommitText(String text);
        void onDeleteChar();
        void onDeleteWord();
        void onCursorMove(int delta);
        void onSuggestionPicked(String word);
        void onSwipeGesture(float[] xs, float[] ys, int count, float keyWidth, int shiftState);
        void onKeyGeometryChanged(float[] centersX, float[] centersY, float keyWidth);
        void onVoiceToggle();
        void onShiftToggle();
        void onShiftLock();
        void onCtrlToggle();
        void onAltToggle();
        void onLayerSwitch(KeyboardLayouts.Layer layer);
        void onOpenSettings();
        void onSwitchIme();
        void onHaptic(boolean light);
        List<String> getClipboardItems();
        boolean isClipPinned(String item);
        void onToggleClipPin(String item);
        void onClearRecentClips();
        List<String> getSnippets();
    }

    public enum PanelMode {
        NONE,
        CLIPBOARD,
        SNIPPETS,
        EMOJI
    }

    public static final int SHIFT_OFF = 0;
    public static final int SHIFT_ONCE = 1;
    public static final int SHIFT_LOCK = 2;

    private static final int MAX_SWIPE_PTS = 256;
    private static final long AFTERGLOW_MS = 160L;

    private final float dp;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Listener listener;

    // Theme, Font & settings
    private RedlineTheme theme = RedlineTheme.REDLINE_CRIMSON;
    private Typeface keyTypeface = Typeface.SANS_SERIF;
    private Typeface uiTypeface = Typeface.MONOSPACE;
    private float fontScale = 1.05f;
    private boolean boldKeys = true;
    private float neonFactor = 0.82f; // 0..1
    private int heightPct = 104;
    private int keyRadiusDp = 6;
    private boolean numberRow = true;
    private boolean swipeTypingEnabled = true;
    private boolean swipeUpSym = true;
    private boolean spaceCursor = true;
    private boolean showKeyPopup = true;
    private boolean suggestionsEnabled = true;
    private boolean passwordMode = false;
    private boolean isUrlOrEmail = false;

    // Live IME state
    private KeyboardLayouts.Layer currentLayer = KeyboardLayouts.Layer.ALPHA;
    private PanelMode panelMode = PanelMode.NONE;
    private boolean devBarVisible = false;
    private int shiftState = SHIFT_OFF;
    private boolean ctrlActive = false;
    private boolean altActive = false;
    private String actionLabel = "↵";
    private List<String> suggestions = Collections.emptyList();
    private String quickPastePreview = null;

    // Live AI Voice HUD state
    private boolean voiceListening = false;
    private String voiceStatusText = "";
    private String voicePartialText = "";
    private String transientBanner = null;
    private final float[] voiceBars = new float[14];
    private final RectF voiceStopRect = new RectF();
    private float targetRms = 0.15f;

    // Layout geometry
    private List<List<KeySpec>> rows = new ArrayList<>();
    private final List<KeySpec> devBarKeys = KeyboardLayouts.buildDevBar();
    private final RectF toolbarRect = new RectF();
    private final RectF keyAreaRect = new RectF();
    private final List<ToolbarBtn> toolbarButtons = new ArrayList<>();
    private final List<SuggestionChip> suggestionChips = new ArrayList<>();
    private float approxKeyWidth = 36f;
    private LinearGradient horizonBleedShader = null;

    // Panel geometry & scrolling
    private final List<PanelItem> panelItems = new ArrayList<>();
    private final List<ToolbarBtn> panelTabs = new ArrayList<>();
    private int glyphTabIndex = 0;
    private final RectF panelHeaderRect = new RectF();
    private final RectF panelCloseBtn = new RectF();
    private final RectF panelClearBtn = new RectF();
    private float panelScrollY = 0f;
    private float panelMaxScrollY = 0f;
    private float panelTouchDownY = 0f;
    private float panelLastY = 0f;
    private boolean panelDragging = false;
    private PanelItem panelPressedItem = null;
    private boolean panelLongPressTriggered = false;

    // Active touch tracking
    private int activePointerId = -1;
    private KeySpec pressedKey = null;
    private float downX, downY;
    private float lastTrackX;
    private boolean spaceTrackpadActive = false;
    private boolean deleteSwipeActive = false;
    private boolean swipeUpTriggered = false;
    private boolean longPressActive = false;
    private int popupSelectedIndex = 0;
    private final RectF popupStripRect = new RectF();
    private long lastShiftTapMs = 0L;

    // Swipe / Glide typing stroke buffer (zero allocation)
    private boolean swipeGlideActive = false;
    private final float[] swipeX = new float[MAX_SWIPE_PTS];
    private final float[] swipeY = new float[MAX_SWIPE_PTS];
    private int swipeCount = 0;
    private float swipeTotalDist = 0f;
    private int swipeShiftSnapshot = SHIFT_OFF;
    private final Path swipePath = new Path();
    private long swipeFadeEndMs = 0L;

    // Neon afterglow on recently tapped key
    private KeySpec glowKey = null;
    private long glowEndMs = 0L;

    // Repeating key (Backspace, Arrows)
    private boolean repeating = false;
    private int repeatCount = 0;

    // Reusable Paints & RectF
    private final Paint pDeck = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pNeon = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pTrail = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pHint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pSmall = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF tmpRect = new RectF();

    private final Runnable repeatRunnable = new Runnable() {
        @Override
        public void run() {
            if (!repeating || pressedKey == null || deleteSwipeActive) return;
            if (listener != null) {
                if (pressedKey.code == KeySpec.CODE_DELETE) {
                    if (repeatCount > 12) listener.onDeleteWord();
                    else listener.onDeleteChar();
                    listener.onHaptic(true);
                } else {
                    listener.onKeyPress(pressedKey, false);
                    listener.onHaptic(true);
                }
            }
            repeatCount++;
            handler.postDelayed(this, repeatCount > 10 ? 45L : 75L);
        }
    };

    private final Runnable longPressRunnable = () -> {
        if (pressedKey == null || swipeGlideActive) return;
        if (pressedKey.repeatable) {
            repeating = true;
            repeatCount = 0;
            handler.post(repeatRunnable);
            return;
        }
        if (pressedKey.code == KeySpec.CODE_SPACE) {
            if (listener != null) {
                listener.onHaptic(false);
                listener.onSwitchIme();
            }
            pressedKey = null;
            invalidate();
            return;
        }
        if (pressedKey.code == KeySpec.CODE_MODE_SYM1) {
            if (listener != null) {
                listener.onHaptic(false);
                listener.onLayerSwitch(KeyboardLayouts.Layer.NUMPAD);
            }
            pressedKey = null;
            invalidate();
            return;
        }
        if (pressedKey.popups != null && pressedKey.popups.length > 0) {
            longPressActive = true;
            popupSelectedIndex = 0;
            if (listener != null) listener.onHaptic(true);
            invalidate();
        }
    };

    private final Runnable panelLongPressRunnable = () -> {
        if (panelMode == PanelMode.CLIPBOARD && panelPressedItem != null && !panelDragging) {
            panelLongPressTriggered = true;
            if (listener != null) {
                listener.onHaptic(false);
                listener.onToggleClipPin(panelPressedItem.value);
            }
            rebuildPanelItems();
            invalidate();
        }
    };

    private final Runnable clearBannerRunnable = () -> {
        transientBanner = null;
        invalidate();
    };

    private final Runnable voiceAnimRunnable = new Runnable() {
        @Override
        public void run() {
            if (!voiceListening) return;
            long t = SystemClock.uptimeMillis();
            for (int i = 0; i < voiceBars.length; i++) {
                float phase = (float) Math.sin(t * 0.012 + i * 0.65);
                float env = 1f - Math.abs(i - 6.5f) / 8.5f;
                float want = Math.max(0.12f, Math.min(1f, (0.22f + targetRms * 0.85f) * (0.55f + 0.45f * phase) * env + 0.12f));
                voiceBars[i] += (want - voiceBars[i]) * 0.45f;
            }
            invalidate(0, 0, getWidth(), (int) toolbarRect.bottom + 2);
            handler.postDelayed(this, 33L);
        }
    };

    public RedlineKeyboardView(Context context) {
        super(context);
        this.dp = context.getResources().getDisplayMetrics().density;
        setWillNotDraw(false);

        pStroke.setStyle(Paint.Style.STROKE);
        pNeon.setStyle(Paint.Style.STROKE);
        pTrail.setStyle(Paint.Style.STROKE);
        pTrail.setStrokeCap(Paint.Cap.ROUND);
        pTrail.setStrokeJoin(Paint.Join.ROUND);

        pText.setTextAlign(Paint.Align.CENTER);
        pHint.setTextAlign(Paint.Align.RIGHT);
        pHint.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        pSmall.setTextAlign(Paint.Align.CENTER);
        pSmall.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));

        rebuildKeyRows();
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void applyPrefs(RedlinePrefs prefs) {
        this.theme = prefs.getTheme();
        this.heightPct = prefs.getHeightPct();
        this.keyRadiusDp = prefs.getKeyRadiusDp();
        this.numberRow = prefs.isNumberRowEnabled();
        this.swipeTypingEnabled = prefs.isSwipeTypingEnabled();
        this.swipeUpSym = prefs.isSwipeUpSymEnabled();
        this.spaceCursor = prefs.isSpaceCursorEnabled();
        this.showKeyPopup = prefs.isKeyPopupEnabled();
        this.suggestionsEnabled = prefs.isSuggestionsEnabled();
        this.fontScale = prefs.getFontScalePct() / 100f;
        this.boldKeys = prefs.isBoldKeys();
        this.neonFactor = prefs.getNeonIntensity() / 100f;

        this.keyTypeface = RedlineFonts.load(getContext(), prefs.getFontId(), boldKeys);
        this.uiTypeface = RedlineFonts.load(getContext(), prefs.getFontId(), true);
        pSmall.setTypeface(uiTypeface);
        rebuildHorizonShader();

        rebuildKeyRows();
        requestLayout();
        invalidate();
    }

    private void rebuildHorizonShader() {
        int bleedAlpha = Math.round(72 * neonFactor);
        horizonBleedShader = new LinearGradient(
                0f, 0f, 0f, 18f * dp,
                RedlineTheme.withAlpha(theme.accentPrimary, bleedAlpha),
                0x00000000,
                Shader.TileMode.CLAMP);
    }

    public void setEditorContext(boolean passwordMode, boolean isUrlOrEmail,
                                 String actionLabel, boolean devBarVisible,
                                 KeyboardLayouts.Layer initialLayer) {
        this.passwordMode = passwordMode;
        this.isUrlOrEmail = isUrlOrEmail;
        this.actionLabel = actionLabel != null ? actionLabel : "↵";
        this.devBarVisible = devBarVisible;
        this.currentLayer = initialLayer;
        this.panelMode = PanelMode.NONE;
        rebuildKeyRows();
        requestLayout();
        invalidate();
    }

    public void setLayer(KeyboardLayouts.Layer layer) {
        this.currentLayer = layer;
        this.panelMode = PanelMode.NONE;
        rebuildKeyRows();
        computeKeyBounds(getWidth(), getHeight());
        invalidate();
    }

    public KeyboardLayouts.Layer getLayer() {
        return currentLayer;
    }

    public void setShiftState(int state) {
        if (this.shiftState != state) {
            this.shiftState = state;
            invalidate();
        }
    }

    public void setModifiers(boolean ctrl, boolean alt) {
        if (this.ctrlActive != ctrl || this.altActive != alt) {
            this.ctrlActive = ctrl;
            this.altActive = alt;
            invalidate();
        }
    }

    public void setSuggestions(List<String> list, String quickPaste) {
        this.suggestions = list != null ? list : Collections.emptyList();
        this.quickPastePreview = quickPaste;
        layoutToolbar(getWidth());
        invalidate();
    }

    public void setVoiceState(boolean listening, String status) {
        boolean wasListening = this.voiceListening;
        this.voiceListening = listening;
        this.voiceStatusText = status != null ? status : "";
        if (!listening) {
            this.voicePartialText = "";
            handler.removeCallbacks(voiceAnimRunnable);
        } else if (!wasListening) {
            handler.removeCallbacks(voiceAnimRunnable);
            handler.post(voiceAnimRunnable);
        }
        layoutToolbar(getWidth());
        invalidate();
    }

    public void setVoiceRms(float normalized) {
        this.targetRms = Math.max(0.08f, Math.min(1f, normalized));
    }

    public void setVoicePartial(String partial) {
        this.voicePartialText = partial != null ? partial : "";
        invalidate(0, 0, getWidth(), (int) toolbarRect.bottom + 2);
    }

    public void showTransientBanner(String msg) {
        this.transientBanner = msg;
        handler.removeCallbacks(clearBannerRunnable);
        handler.postDelayed(clearBannerRunnable, 2000L);
        invalidate();
    }

    private void rebuildKeyRows() {
        switch (currentLayer) {
            case SYM1: rows = KeyboardLayouts.buildSym1(); break;
            case SYM2: rows = KeyboardLayouts.buildSym2(); break;
            case NUMPAD: rows = KeyboardLayouts.buildNumpad(); break;
            case ALPHA:
            default: rows = KeyboardLayouts.buildAlpha(numberRow, isUrlOrEmail); break;
        }
        if (getWidth() > 0 && getHeight() > 0) {
            computeKeyBounds(getWidth(), getHeight());
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = MeasureSpec.getSize(widthMeasureSpec);
        float scale = heightPct / 100f;
        float toolbarH = 44f * dp;
        int rowCount = (currentLayer == KeyboardLayouts.Layer.ALPHA && numberRow) ? 5 : 4;
        float baseRowH = (rowCount == 5 ? 49f : 55f) * dp * scale;
        float padBottom = 8f * dp;
        setMeasuredDimension(w, Math.round(toolbarH + rowCount * baseRowH + padBottom));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        rebuildHorizonShader();
        computeKeyBounds(w, h);
    }

    private void computeKeyBounds(int w, int h) {
        if (w <= 0 || h <= 0) return;
        float toolbarH = 44f * dp;
        toolbarRect.set(0, 0, w, toolbarH);
        keyAreaRect.set(0, toolbarH, w, h - 6f * dp);

        layoutToolbar(w);

        int numRows = rows.size();
        if (numRows == 0) return;

        float areaH = keyAreaRect.height();
        float rowH = areaH / numRows;
        float hPad = 2.8f * dp;
        float vPad = 3.0f * dp;
        float sideMargin = 3f * dp;

        float[] centersX = new float[26];
        float[] centersY = new float[26];
        boolean hasAllLetters = (currentLayer == KeyboardLayouts.Layer.ALPHA);
        int letterCount = 0;
        float sumLetterW = 0f;

        for (int r = 0; r < numRows; r++) {
            List<KeySpec> row = rows.get(r);
            float totalWeight = 0f;
            for (KeySpec k : row) totalWeight += k.weight;

            float rowInset = sideMargin;
            if (currentLayer == KeyboardLayouts.Layer.ALPHA && row.size() == 9 && !row.get(0).special) {
                rowInset = w * 0.048f;
            }
            float usableW = w - rowInset * 2f;
            float top = keyAreaRect.top + r * rowH;
            float bottom = top + rowH;
            float curX = rowInset;

            for (int i = 0; i < row.size(); i++) {
                KeySpec k = row.get(i);
                float kw = usableW * (k.weight / totalWeight);
                float left = curX;
                float right = curX + kw;

                float hitLeft = (i == 0) ? 0f : left;
                float hitRight = (i == row.size() - 1) ? w : right;
                k.bounds.set(hitLeft, top, hitRight, bottom);
                k.drawRect.set(left + hPad, top + vPad, right - hPad, bottom - vPad);
                curX = right;

                if (k.isLetter()) {
                    int idx = k.code - 'a';
                    centersX[idx] = k.drawRect.centerX();
                    centersY[idx] = k.drawRect.centerY();
                    letterCount++;
                    sumLetterW += kw;
                }
            }
        }

        if (hasAllLetters && letterCount == 26) {
            approxKeyWidth = sumLetterW / 26f;
            if (listener != null) {
                listener.onKeyGeometryChanged(centersX, centersY, approxKeyWidth);
            }
        }

        if (panelMode != PanelMode.NONE) {
            rebuildPanelItems();
        }
    }

    private void layoutToolbar(int w) {
        toolbarButtons.clear();
        suggestionChips.clear();
        if (w <= 0) return;

        float top = 5f * dp;
        float bottom = toolbarRect.bottom - 3.5f * dp;
        float pad = 4f * dp;

        if (voiceListening) {
            voiceStopRect.set(pad, top, pad + 68f * dp, bottom);
            return;
        }

        if (devBarVisible) {
            float closeW = 30f * dp;
            float micW = 30f * dp;
            toolbarButtons.add(new ToolbarBtn("DEV_TOGGLE", "×", new RectF(pad, top, pad + closeW, bottom), true));
            toolbarButtons.add(new ToolbarBtn("VOICE", "🎙", new RectF(w - pad - micW, top, w - pad, bottom), false));

            float startX = pad + closeW + 3f * dp;
            float usableW = (w - pad - micW - 3f * dp) - startX;
            float totalW = 0f;
            for (KeySpec k : devBarKeys) totalW += k.weight;
            float curX = startX;
            for (KeySpec k : devBarKeys) {
                float kw = usableW * (k.weight / totalW);
                k.bounds.set(curX, toolbarRect.top, curX + kw, toolbarRect.bottom);
                k.drawRect.set(curX + 1.2f * dp, top, curX + kw - 1.2f * dp, bottom);
                curX += kw;
            }
            return;
        }

        // Normal strip: Left [>_] [CLP]  |  Center [Suggestions / Paste]  |  Right [🎙] [⚡] [☺] [⚙]
        float btnW = 34f * dp;
        float xL = pad;
        toolbarButtons.add(new ToolbarBtn("DEV_TOGGLE", ">_", new RectF(xL, top, xL + btnW, bottom), false));
        xL += btnW + 3f * dp;
        toolbarButtons.add(new ToolbarBtn("CLIP", "CLP", new RectF(xL, top, xL + btnW, bottom), panelMode == PanelMode.CLIPBOARD));
        xL += btnW + 4f * dp;

        float xR = w - pad;
        float smW = 30f * dp;
        toolbarButtons.add(new ToolbarBtn("SETTINGS", "⚙", new RectF(xR - smW, top, xR, bottom), false));
        xR -= smW + 3f * dp;
        toolbarButtons.add(new ToolbarBtn("EMOJI", "☠", new RectF(xR - smW, top, xR, bottom), panelMode == PanelMode.EMOJI));
        xR -= smW + 3f * dp;
        toolbarButtons.add(new ToolbarBtn("SNIPPETS", "⚡", new RectF(xR - smW, top, xR, bottom), panelMode == PanelMode.SNIPPETS));
        xR -= smW + 3f * dp;
        toolbarButtons.add(new ToolbarBtn("VOICE", "🎙", new RectF(xR - btnW, top, xR, bottom), false));
        xR -= btnW + 4f * dp;

        float midW = xR - xL;
        if (midW < 36f * dp) return;

        if (!passwordMode && suggestionsEnabled && !suggestions.isEmpty()) {
            int n = Math.min(3, suggestions.size());
            float chipW = (midW - (n - 1) * 3.5f * dp) / n;
            for (int i = 0; i < n; i++) {
                float cl = xL + i * (chipW + 3.5f * dp);
                suggestionChips.add(new SuggestionChip(suggestions.get(i), false,
                        new RectF(cl, top, cl + chipW, bottom)));
            }
        } else if (!passwordMode && quickPastePreview != null && !quickPastePreview.isEmpty()) {
            String preview = quickPastePreview.replace('\n', ' ').trim();
            if (preview.length() > 18) preview = preview.substring(0, 17) + "…";
            suggestionChips.add(new SuggestionChip("⎘ " + preview, true,
                    new RectF(xL, top, xR, bottom)));
        }
    }

    private void openPanel(PanelMode mode) {
        if (this.panelMode == mode) {
            this.panelMode = PanelMode.NONE;
        } else {
            this.panelMode = mode;
            this.panelScrollY = 0f;
            rebuildPanelItems();
        }
        layoutToolbar(getWidth());
        invalidate();
    }

    private void rebuildPanelItems() {
        panelItems.clear();
        panelTabs.clear();
        if (panelMode == PanelMode.NONE || keyAreaRect.width() <= 0) return;

        float w = keyAreaRect.width();
        float headerH = 36f * dp;
        panelHeaderRect.set(0, keyAreaRect.top, w, keyAreaRect.top + headerH);
        panelCloseBtn.set(w - 56f * dp, keyAreaRect.top + 3f * dp, w - 5f * dp, keyAreaRect.top + headerH - 3f * dp);
        panelClearBtn.set(w - 122f * dp, keyAreaRect.top + 3f * dp, w - 62f * dp, keyAreaRect.top + headerH - 3f * dp);

        float contentTop = panelHeaderRect.bottom + 4f * dp;
        float pad = 6f * dp;

        if (panelMode == PanelMode.EMOJI) {
            // Layout 5 neon category tabs across panel header left of KEYS button
            int tabCount = KeyboardLayouts.GLYPH_TAB_LABELS.length;
            float tabsLeft = 5f * dp;
            float tabsRight = panelCloseBtn.left - 5f * dp;
            float gap = 3f * dp;
            float tabW = (tabsRight - tabsLeft - (tabCount - 1) * gap) / tabCount;
            for (int ti = 0; ti < tabCount; ti++) {
                float tl = tabsLeft + ti * (tabW + gap);
                panelTabs.add(new ToolbarBtn("TAB_" + ti, KeyboardLayouts.GLYPH_TAB_LABELS[ti],
                        new RectF(tl, keyAreaRect.top + 3f * dp, tl + tabW, keyAreaRect.top + headerH - 3f * dp),
                        ti == glyphTabIndex));
            }

            int safeTab = Math.max(0, Math.min(glyphTabIndex, KeyboardLayouts.GLYPH_PAGES.length - 1));
            String[] page = KeyboardLayouts.GLYPH_PAGES[safeTab];
            boolean isKaomojiTab = (safeTab == KeyboardLayouts.GLYPH_PAGES.length - 1);
            int cols = isKaomojiTab ? 2 : 8;
            float cellW = (w - pad * 2f) / cols;
            float cellH = 42f * dp;
            for (int i = 0; i < page.length; i++) {
                String g = page[i];
                int r = i / cols;
                int c = i % cols;
                float l = pad + c * cellW;
                float t = contentTop + r * cellH;
                panelItems.add(new PanelItem(g, g, false,
                        new RectF(l + 2f * dp, t + 2f * dp, l + cellW - 2f * dp, t + cellH - 2f * dp)));
            }
            int totalRows = (page.length + cols - 1) / cols;
            panelMaxScrollY = Math.max(0f, totalRows * cellH + 8f * dp - (keyAreaRect.bottom - contentTop));
        } else if (panelMode == PanelMode.SNIPPETS) {
            List<String> snips = listener != null ? listener.getSnippets() : Collections.emptyList();
            int cols = 2;
            float cellW = (w - pad * 2f) / cols;
            float cellH = 44f * dp;
            for (int i = 0; i < snips.size(); i++) {
                int r = i / cols;
                int c = i % cols;
                String s = snips.get(i);
                String disp = s.replace("\n", "↵");
                if (disp.length() > 22) disp = disp.substring(0, 21) + "…";
                float l = pad + c * cellW;
                float t = contentTop + r * cellH;
                panelItems.add(new PanelItem(disp, s, false,
                        new RectF(l + 3f * dp, t + 3f * dp, l + cellW - 3f * dp, t + cellH - 3f * dp)));
            }
            int totalRows = (snips.size() + cols - 1) / cols;
            panelMaxScrollY = Math.max(0f, totalRows * cellH + 8f * dp - (keyAreaRect.bottom - contentTop));
        } else if (panelMode == PanelMode.CLIPBOARD) {
            List<String> items = listener != null ? listener.getClipboardItems() : Collections.emptyList();
            float itemH = 44f * dp;
            for (int i = 0; i < items.size(); i++) {
                String raw = items.get(i);
                boolean pinned = listener != null && listener.isClipPinned(raw);
                String disp = raw.replace('\n', ' ').trim();
                if (disp.length() > 44) disp = disp.substring(0, 43) + "…";
                float t = contentTop + i * itemH;
                panelItems.add(new PanelItem((pinned ? "★ " : "") + disp, raw, pinned,
                        new RectF(pad, t + 2.5f * dp, w - pad, t + itemH - 2.5f * dp)));
            }
            panelMaxScrollY = Math.max(0f, items.size() * itemH + 8f * dp - (keyAreaRect.bottom - contentTop));
        }
        panelScrollY = Math.max(0f, Math.min(panelScrollY, panelMaxScrollY));
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        long now = SystemClock.uptimeMillis();
        boolean needsNextFrame = false;

        // 1. Obsidian deck backing
        pDeck.setColor(theme.deckBg);
        c.drawRect(0, 0, w, h, pDeck);

        // 2. Top toolbar + GPU neon horizon bleed
        pFill.setColor(theme.toolbarBg);
        c.drawRect(toolbarRect, pFill);

        if (neonFactor > 0.05f && horizonBleedShader != null) {
            pFill.setShader(horizonBleedShader);
            c.drawRect(0, 0, w, 18f * dp, pFill);
            pFill.setShader(null);
        }

        // Crisp glowing neon laser rail at top edge
        pFill.setColor(voiceListening ? theme.accentSecondary : theme.accentPrimary);
        c.drawRect(0, 0, w, 2f * dp, pFill);

        pFill.setColor(theme.dividerColor);
        c.drawRect(0, toolbarRect.bottom - 1f, w, toolbarRect.bottom, pFill);

        drawToolbar(c);

        // 3. Inline drawer OR keyboard keys
        if (panelMode != PanelMode.NONE) {
            drawPanel(c);
            return;
        }

        // Compute afterglow factor (0..1)
        float glowAlpha = 0f;
        if (glowKey != null && now < glowEndMs && neonFactor > 0.05f) {
            glowAlpha = (glowEndMs - now) / (float) AFTERGLOW_MS;
            needsNextFrame = true;
        } else {
            glowKey = null;
        }

        float radius = keyRadiusDp * dp;
        for (List<KeySpec> row : rows) {
            for (KeySpec key : row) {
                float ag = (key == glowKey) ? glowAlpha : 0f;
                drawKey(c, key, radius, ag);
            }
        }

        // 4. Multi-layer Neon Plasma Ribbon Trail during glide typing (or 140ms fadeout)
        if (swipeGlideActive && swipeCount >= 2) {
            drawNeonSwipeTrail(c, 1.0f);
        } else if (swipeCount >= 2 && now < swipeFadeEndMs) {
            float fade = (swipeFadeEndMs - now) / 140f;
            drawNeonSwipeTrail(c, fade);
            needsNextFrame = true;
        } else if (!swipeGlideActive && pressedKey == null && swipeCount > 0) {
            swipeCount = 0;
        }

        // 5. Floating key preview OR sliding long-press popup
        if (pressedKey != null && !spaceTrackpadActive && !deleteSwipeActive && !swipeGlideActive) {
            if (longPressActive && pressedKey.popups != null && pressedKey.popups.length > 0) {
                drawPopupStrip(c, pressedKey);
            } else if (showKeyPopup && !passwordMode && !pressedKey.special) {
                drawKeyPreview(c, pressedKey);
            }
        }

        if (needsNextFrame) {
            postInvalidateOnAnimation();
        }
    }

    private void drawToolbar(Canvas c) {
        float r = 6f * dp;

        // Live AI Voice HUD mode!
        if (voiceListening) {
            drawNeonOutline(c, voiceStopRect, r, theme.accentPrimary, 0.9f);
            pFill.setColor(theme.actionKeyBg);
            c.drawRoundRect(voiceStopRect, r, r, pFill);
            pSmall.setColor(theme.actionKeyText);
            pSmall.setTextSize(11f * dp);
            drawCenteredText(c, "■ STOP", voiceStopRect, pSmall);

            // 14-bar live neon audio spectrum
            float waveLeft = voiceStopRect.right + 8f * dp;
            float barW = 3.2f * dp;
            float gap = 2.6f * dp;
            float maxBarH = toolbarRect.height() - 14f * dp;
            float cy = toolbarRect.centerY();
            for (int i = 0; i < voiceBars.length; i++) {
                float bh = Math.max(4f * dp, voiceBars[i] * maxBarH);
                float bx = waveLeft + i * (barW + gap);
                pFill.setColor((i % 2 == 0) ? theme.accentPrimary : theme.accentSecondary);
                c.drawRoundRect(bx, cy - bh / 2f, bx + barW, cy + bh / 2f, 2f * dp, 2f * dp, pFill);
            }

            // Streaming partial transcript or status
            float txtLeft = waveLeft + voiceBars.length * (barW + gap) + 10f * dp;
            String msg = !voicePartialText.isEmpty() ? voicePartialText : voiceStatusText;
            if (msg.length() > 26) msg = "…" + msg.substring(msg.length() - 25);
            pSmall.setTextAlign(Paint.Align.LEFT);
            pSmall.setColor(!voicePartialText.isEmpty() ? theme.primaryText : theme.accentSecondary);
            pSmall.setTextSize(12f * dp);
            c.drawText(msg, txtLeft, toolbarRect.centerY() - (pSmall.descent() + pSmall.ascent()) / 2f, pSmall);
            pSmall.setTextAlign(Paint.Align.CENTER);
            return;
        }

        for (ToolbarBtn b : toolbarButtons) {
            boolean isVoiceBtn = "VOICE".equals(b.id);
            pFill.setColor(b.active ? theme.keyPressedBg : theme.chipBg);
            c.drawRoundRect(b.rect, r, r, pFill);

            if ((b.active || isVoiceBtn) && neonFactor > 0.1f) {
                drawNeonOutline(c, b.rect, r, isVoiceBtn ? theme.accentSecondary : theme.accentPrimary,
                        b.active ? 0.85f : 0.40f);
            }
            pStroke.setColor(b.active ? theme.accentPrimary : (isVoiceBtn ? theme.accentSecondary : theme.keyBorder));
            pStroke.setStrokeWidth(1f * dp);
            c.drawRoundRect(b.rect, r, r, pStroke);

            boolean isIconBtn = isVoiceBtn || "EMOJI".equals(b.id) || "SNIPPETS".equals(b.id) || "SETTINGS".equals(b.id);
            pSmall.setColor(b.active ? theme.accentPrimary : (isVoiceBtn ? theme.accentSecondary : theme.primaryText));
            pSmall.setTextSize(isIconBtn ? 14f * dp : 11.5f * dp);
            drawCenteredText(c, b.label, b.rect, pSmall);
        }

        if (devBarVisible) {
            for (KeySpec k : devBarKeys) {
                boolean active = (k.code == KeySpec.CODE_CTRL && ctrlActive)
                        || (k.code == KeySpec.CODE_ALT && altActive)
                        || (k == pressedKey);
                pFill.setColor(active ? theme.keyPressedBg : theme.specialKeyBg);
                c.drawRoundRect(k.drawRect, r, r, pFill);

                if (active && neonFactor > 0.05f) {
                    drawNeonOutline(c, k.drawRect, r, theme.accentSecondary, 0.9f);
                }
                pStroke.setColor(active ? theme.accentSecondary : theme.keyBorder);
                pStroke.setStrokeWidth(active ? 1.4f * dp : 1f * dp);
                c.drawRoundRect(k.drawRect, r, r, pStroke);

                pSmall.setColor(active ? theme.accentSecondary : theme.primaryText);
                pSmall.setTextSize(10f * dp);
                drawCenteredText(c, k.label, k.drawRect, pSmall);
            }
            return;
        }

        if (transientBanner != null) {
            pSmall.setColor(theme.accentSecondary);
            pSmall.setTextSize(11.5f * dp);
            drawCenteredText(c, transientBanner, toolbarRect, pSmall);
            return;
        }

        for (int i = 0; i < suggestionChips.size(); i++) {
            SuggestionChip chip = suggestionChips.get(i);
            pFill.setColor(theme.chipBg);
            c.drawRoundRect(chip.rect, r, r, pFill);

            int rim = chip.isQuickPaste ? theme.accentSecondary : (i == 0 ? theme.accentPrimary : theme.keyBorder);
            if ((i == 0 || chip.isQuickPaste) && neonFactor > 0.1f) {
                drawNeonOutline(c, chip.rect, r, rim, 0.45f);
            }
            pStroke.setColor(rim);
            pStroke.setStrokeWidth(1f * dp);
            c.drawRoundRect(chip.rect, r, r, pStroke);

            pText.setColor(chip.isQuickPaste ? theme.accentSecondary : theme.chipText);
            pText.setTextSize(12.5f * dp);
            pText.setTypeface(keyTypeface);
            String label = chip.label;
            if (label.length() > 20) label = label.substring(0, 19) + "…";
            drawCenteredText(c, label, chip.rect, pText);
        }
    }

    private void drawKey(Canvas c, KeySpec key, float radius, float afterglow) {
        boolean isPressed = (key == pressedKey && !swipeGlideActive);
        boolean isAction = (key.code == KeySpec.CODE_ENTER);
        boolean isShiftActive = (key.code == KeySpec.CODE_SHIFT && shiftState != SHIFT_OFF);
        boolean isCapsLock = (key.code == KeySpec.CODE_SHIFT && shiftState == SHIFT_LOCK);

        int bg;
        int border;
        if (isAction) {
            bg = isPressed ? theme.accentPrimary : theme.actionKeyBg;
            border = theme.accentPrimary;
        } else if (isPressed) {
            bg = theme.keyPressedBg;
            border = theme.accentPrimary;
        } else if (isShiftActive) {
            bg = theme.keyPressedBg;
            border = isCapsLock ? theme.accentSecondary : theme.accentPrimary;
        } else if (key.special) {
            bg = theme.specialKeyBg;
            border = theme.keyBorder;
        } else {
            bg = theme.keyBg;
            border = theme.keyBorder;
        }

        // Fast GPU-native neon outer halo for action key, active modifiers, pressed keys, and afterglow!
        if (neonFactor > 0.05f) {
            if (isPressed) {
                drawNeonOutline(c, key.drawRect, radius, theme.accentPrimary, 1.0f);
            } else if (isAction) {
                drawNeonOutline(c, key.drawRect, radius, theme.accentPrimary, 0.72f);
            } else if (isShiftActive) {
                drawNeonOutline(c, key.drawRect, radius, isCapsLock ? theme.accentSecondary : theme.accentPrimary, 0.85f);
            } else if (afterglow > 0.02f) {
                drawNeonOutline(c, key.drawRect, radius, theme.accentPrimary, afterglow * 0.9f);
            } else if (!key.special && neonFactor > 0.35f) {
                // Subtle ambient neon under-rim tint on standard keycaps
                pFill.setColor(RedlineTheme.withAlpha(theme.accentPrimary, Math.round(28 * neonFactor)));
                c.drawRect(key.drawRect.left + radius, key.drawRect.bottom - 1.2f * dp,
                        key.drawRect.right - radius, key.drawRect.bottom, pFill);
            }
        }

        // Keycap body
        pFill.setColor(bg);
        c.drawRoundRect(key.drawRect, radius, radius, pFill);

        // Top subtle specular bevel inside keycap
        if (!isAction && !isPressed) {
            pFill.setColor(0x16FFFFFF);
            c.drawRect(key.drawRect.left + radius, key.drawRect.top + 0.8f * dp,
                    key.drawRect.right - radius, key.drawRect.top + 1.6f * dp, pFill);
        }

        // Crisp machined keycap border
        int finalBorder = afterglow > 0.1f
                ? RedlineTheme.withAlpha(theme.accentPrimary, Math.min(255, Math.round(120 + 135 * afterglow)))
                : border;
        pStroke.setColor(finalBorder);
        pStroke.setStrokeWidth((isPressed || isShiftActive || isAction) ? 1.5f * dp : 0.95f * dp);
        c.drawRoundRect(key.drawRect, radius, radius, pStroke);

        // Caps lock plasma bar
        if (isCapsLock) {
            pFill.setColor(theme.accentSecondary);
            float bw = key.drawRect.width() * 0.46f;
            float cx = key.drawRect.centerX();
            c.drawRoundRect(cx - bw / 2f, key.drawRect.bottom - 5.5f * dp,
                    cx + bw / 2f, key.drawRect.bottom - 3f * dp, 1.5f * dp, 1.5f * dp, pFill);
        }

        // Label text
        String display = key.label;
        if (isAction) {
            display = actionLabel;
        } else if (key.code == KeySpec.CODE_SPACE) {
            if (spaceTrackpadActive) display = "◀  CURSOR  ▶";
            else if (ctrlActive && altActive) display = "CTRL + ALT";
            else if (ctrlActive) display = "CTRL ACTIVE";
            else if (altActive) display = "ALT ACTIVE";
            else display = "REDLINE";
        } else if (key.isLetter() && shiftState != SHIFT_OFF) {
            display = display.toUpperCase();
        } else if (key.code == KeySpec.CODE_SHIFT) {
            display = isCapsLock ? "⇪" : "⇧";
        }

        int txtColor;
        if (isAction) txtColor = theme.actionKeyText;
        else if (key.code == KeySpec.CODE_SPACE) {
            txtColor = (spaceTrackpadActive || ctrlActive || altActive) ? theme.accentSecondary : theme.hintText;
        } else {
            txtColor = theme.primaryText;
        }

        if (key.code == KeySpec.CODE_SPACE) {
            pSmall.setColor(txtColor);
            pSmall.setTextSize(10.5f * dp * Math.min(1.15f, fontScale));
            drawCenteredText(c, display, key.drawRect, pSmall);

            // Neon tactical flank bars on spacebar
            int flankColor = spaceTrackpadActive ? theme.accentSecondary
                    : RedlineTheme.withAlpha(theme.accentPrimary, Math.round(90 + 130 * neonFactor));
            pFill.setColor(flankColor);
            float barW = 14f * dp;
            c.drawRoundRect(key.drawRect.left + 10f * dp, key.drawRect.centerY() - 1f * dp,
                    key.drawRect.left + 10f * dp + barW, key.drawRect.centerY() + 1f * dp, 1f * dp, 1f * dp, pFill);
            c.drawRoundRect(key.drawRect.right - 10f * dp - barW, key.drawRect.centerY() - 1f * dp,
                    key.drawRect.right - 10f * dp, key.drawRect.centerY() + 1f * dp, 1f * dp, 1f * dp, pFill);
        } else {
            float baseSize = (display.length() > 2) ? 12.5f : (key.special ? 17f : 19.5f);
            pText.setColor(txtColor);
            pText.setTextSize(baseSize * dp * fontScale);
            pText.setTypeface(key.special ? uiTypeface : keyTypeface);
            drawCenteredText(c, display, key.drawRect, pText);
        }

        // Top-right corner alternate symbol hint
        if (key.altText != null && !key.altText.isEmpty() && key.drawRect.width() > 22f * dp) {
            pHint.setColor(isPressed ? theme.accentSecondary : theme.hintText);
            pHint.setTextSize(9.5f * dp);
            c.drawText(key.altText,
                    key.drawRect.right - 4f * dp,
                    key.drawRect.top + 11f * dp,
                    pHint);
        }
    }

    /**
     * Pure GPU-native multi-pass neon outline (no slow software BlurMaskFilter).
     */
    private void drawNeonOutline(Canvas c, RectF rect, float radius, int color, float strength) {
        float s = strength * neonFactor;
        if (s <= 0.02f) return;

        // Outer soft halo
        tmpRect.set(rect.left - 1.5f * dp, rect.top - 1.5f * dp, rect.right + 1.5f * dp, rect.bottom + 1.5f * dp);
        pNeon.setColor(RedlineTheme.withAlpha(color, Math.round(52 * s)));
        pNeon.setStrokeWidth(4.5f * dp);
        c.drawRoundRect(tmpRect, radius + 1.5f * dp, radius + 1.5f * dp, pNeon);

        // Inner bright rim
        pNeon.setColor(RedlineTheme.withAlpha(color, Math.round(135 * s)));
        pNeon.setStrokeWidth(2.1f * dp);
        c.drawRoundRect(rect, radius, radius, pNeon);
    }

    private void drawNeonSwipeTrail(Canvas c, float fade) {
        if (swipeCount < 2) return;
        swipePath.reset();
        swipePath.moveTo(swipeX[0], swipeY[0]);
        for (int i = 1; i < swipeCount - 1; i++) {
            float midX = (swipeX[i] + swipeX[i + 1]) * 0.5f;
            float midY = (swipeY[i] + swipeY[i + 1]) * 0.5f;
            swipePath.quadTo(swipeX[i], swipeY[i], midX, midY);
        }
        swipePath.lineTo(swipeX[swipeCount - 1], swipeY[swipeCount - 1]);

        float nf = Math.max(0.35f, neonFactor) * fade;
        // Pass 1: wide outer plasma aura
        pTrail.setColor(RedlineTheme.withAlpha(theme.accentPrimary, Math.round(70 * nf)));
        pTrail.setStrokeWidth(15f * dp);
        c.drawPath(swipePath, pTrail);

        // Pass 2: vibrant mid neon beam
        pTrail.setColor(RedlineTheme.withAlpha(theme.accentPrimary, Math.round(210 * fade)));
        pTrail.setStrokeWidth(6.2f * dp);
        c.drawPath(swipePath, pTrail);

        // Pass 3: white-hot laser core
        pTrail.setColor(RedlineTheme.withAlpha(theme.neonCore, Math.round(240 * fade)));
        pTrail.setStrokeWidth(2.4f * dp);
        c.drawPath(swipePath, pTrail);

        // Live fingertip spark ring
        if (swipeGlideActive) {
            float tx = swipeX[swipeCount - 1];
            float ty = swipeY[swipeCount - 1];
            pFill.setColor(RedlineTheme.withAlpha(theme.accentSecondary, Math.round(150 * nf)));
            c.drawCircle(tx, ty, 9f * dp, pFill);
            pFill.setColor(theme.neonCore);
            c.drawCircle(tx, ty, 3.8f * dp, pFill);
        }
    }

    private void drawKeyPreview(Canvas c, KeySpec key) {
        String text = (swipeUpTriggered && key.altText != null)
                ? key.altText
                : (key.isLetter() && shiftState != SHIFT_OFF ? key.label.toUpperCase() : key.label);
        float pw = Math.max(key.drawRect.width() * 1.25f, 44f * dp);
        float ph = 52f * dp;
        float cx = key.drawRect.centerX();
        float left = Math.max(4f * dp, Math.min(getWidth() - pw - 4f * dp, cx - pw / 2f));
        float bottom = key.drawRect.top - 4f * dp;
        float top = Math.max(2f * dp, bottom - ph);

        RectF r = new RectF(left, top, left + pw, top + ph);
        float rad = (keyRadiusDp + 2) * dp;
        int rim = swipeUpTriggered ? theme.accentSecondary : theme.accentPrimary;

        drawNeonOutline(c, r, rad, rim, 1.0f);
        pFill.setColor(theme.popupBg);
        c.drawRoundRect(r, rad, rad, pFill);
        pStroke.setColor(rim);
        pStroke.setStrokeWidth(1.8f * dp);
        c.drawRoundRect(r, rad, rad, pStroke);

        pText.setColor(swipeUpTriggered ? theme.accentSecondary : theme.primaryText);
        pText.setTextSize(26f * dp * Math.min(1.15f, fontScale));
        pText.setTypeface(keyTypeface);
        drawCenteredText(c, text, r, pText);
    }

    private void drawPopupStrip(Canvas c, KeySpec key) {
        String[] pop = key.popups;
        if (pop == null || pop.length == 0) return;

        float cellW = 38f * dp;
        float stripW = Math.min(getWidth() - 12f * dp, pop.length * cellW + 8f * dp);
        cellW = (stripW - 8f * dp) / pop.length;
        float stripH = 46f * dp;

        float left = key.drawRect.centerX() - stripW / 2f;
        left = Math.max(6f * dp, Math.min(getWidth() - stripW - 6f * dp, left));
        float bottom = key.drawRect.top - 6f * dp;
        float top = Math.max(4f * dp, bottom - stripH);

        popupStripRect.set(left, top, left + stripW, top + stripH);
        float rad = 8f * dp;

        drawNeonOutline(c, popupStripRect, rad, theme.accentPrimary, 0.9f);
        pFill.setColor(theme.popupBg);
        c.drawRoundRect(popupStripRect, rad, rad, pFill);
        pStroke.setColor(theme.accentPrimary);
        pStroke.setStrokeWidth(1.6f * dp);
        c.drawRoundRect(popupStripRect, rad, rad, pStroke);

        for (int i = 0; i < pop.length; i++) {
            float cl = popupStripRect.left + 4f * dp + i * cellW;
            RectF cell = new RectF(cl + 1f * dp, popupStripRect.top + 4f * dp,
                    cl + cellW - 1f * dp, popupStripRect.bottom - 4f * dp);
            boolean sel = (i == popupSelectedIndex);
            if (sel) {
                pFill.setColor(theme.accentPrimary);
                c.drawRoundRect(cell, 5f * dp, 5f * dp, pFill);
            }
            pText.setColor(sel ? theme.actionKeyText : theme.primaryText);
            pText.setTextSize(pop[i].length() > 2 ? 12f * dp : 18f * dp);
            pText.setTypeface(keyTypeface);
            drawCenteredText(c, pop[i], cell, pText);
        }
    }

    private void drawPanel(Canvas c) {
        pFill.setColor(theme.toolbarBg);
        c.drawRect(panelHeaderRect, pFill);

        float r = 5f * dp;
        if (panelMode == PanelMode.EMOJI && !panelTabs.isEmpty()) {
            for (ToolbarBtn tab : panelTabs) {
                pFill.setColor(tab.active ? theme.keyPressedBg : theme.chipBg);
                c.drawRoundRect(tab.rect, r, r, pFill);
                if (tab.active && neonFactor > 0.1f) {
                    drawNeonOutline(c, tab.rect, r, theme.accentPrimary, 0.75f);
                }
                pStroke.setColor(tab.active ? theme.accentPrimary : theme.keyBorder);
                pStroke.setStrokeWidth(tab.active ? 1.3f * dp : 1f * dp);
                c.drawRoundRect(tab.rect, r, r, pStroke);

                pSmall.setColor(tab.active ? theme.accentPrimary : theme.hintText);
                pSmall.setTextSize(9.8f * dp);
                pSmall.setTypeface(Typeface.SANS_SERIF);
                drawCenteredText(c, tab.label, tab.rect, pSmall);
            }
            pSmall.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        } else {
            String title = (panelMode == PanelMode.CLIPBOARD)
                    ? "CLIPBOARD // HOLD TO PIN ★"
                    : "SNIPPETS // QUICK MACROS";

            pSmall.setColor(theme.accentPrimary);
            pSmall.setTextAlign(Paint.Align.LEFT);
            pSmall.setTextSize(11f * dp);
            c.drawText(title, 10f * dp, panelHeaderRect.centerY() + 4f * dp, pSmall);
            pSmall.setTextAlign(Paint.Align.CENTER);
        }

        if (panelMode == PanelMode.CLIPBOARD) {
            pFill.setColor(theme.specialKeyBg);
            c.drawRoundRect(panelClearBtn, r, r, pFill);
            pStroke.setColor(theme.keyBorder);
            pStroke.setStrokeWidth(1f * dp);
            c.drawRoundRect(panelClearBtn, r, r, pStroke);
            pSmall.setColor(theme.hintText);
            pSmall.setTextSize(10.5f * dp);
            drawCenteredText(c, "CLEAR", panelClearBtn, pSmall);
        }

        pFill.setColor(theme.actionKeyBg);
        c.drawRoundRect(panelCloseBtn, r, r, pFill);
        pSmall.setColor(theme.actionKeyText);
        pSmall.setTextSize(10.5f * dp);
        drawCenteredText(c, "KEYS", panelCloseBtn, pSmall);

        c.save();
        c.clipRect(0, panelHeaderRect.bottom, getWidth(), keyAreaRect.bottom);
        c.translate(0, -panelScrollY);

        if (panelItems.isEmpty()) {
            pText.setColor(theme.hintText);
            pText.setTextSize(13f * dp);
            pText.setTypeface(keyTypeface);
            c.drawText("No items yet — copy any text to see it here.",
                    getWidth() / 2f, panelHeaderRect.bottom + 54f * dp, pText);
        } else {
            for (PanelItem item : panelItems) {
                boolean pressed = (item == panelPressedItem);
                pFill.setColor(pressed ? theme.keyPressedBg : theme.keyBg);
                c.drawRoundRect(item.rect, r, r, pFill);
                pStroke.setColor(pressed ? theme.accentPrimary : (item.pinned ? theme.accentSecondary : theme.keyBorder));
                pStroke.setStrokeWidth(1f * dp);
                c.drawRoundRect(item.rect, r, r, pStroke);

                if (panelMode == PanelMode.CLIPBOARD) {
                    pText.setTextAlign(Paint.Align.LEFT);
                    pText.setColor(item.pinned ? theme.accentSecondary : theme.primaryText);
                    pText.setTextSize(13f * dp);
                    pText.setTypeface(Typeface.MONOSPACE);
                    c.drawText(item.label, item.rect.left + 10f * dp,
                            item.rect.centerY() - (pText.descent() + pText.ascent()) / 2f, pText);
                    pText.setTextAlign(Paint.Align.CENTER);
                } else if (panelMode == PanelMode.EMOJI) {
                    boolean wide = item.label.length() > 3;
                    pText.setColor(theme.primaryText);
                    pText.setTextSize(wide ? 12.5f * dp : 20f * dp);
                    // Use system SANS_SERIF so Android's full Unicode symbol & emoji fallback fonts render every glyph!
                    pText.setTypeface(Typeface.SANS_SERIF);
                    drawCenteredText(c, item.label, item.rect, pText);
                } else {
                    pText.setColor(theme.primaryText);
                    pText.setTextSize(12.5f * dp);
                    pText.setTypeface(Typeface.MONOSPACE);
                    drawCenteredText(c, item.label, item.rect, pText);
                }
            }
        }
        c.restore();
    }

    private static void drawCenteredText(Canvas c, String text, RectF r, Paint p) {
        float y = r.centerY() - (p.descent() + p.ascent()) / 2f;
        c.drawText(text, r.centerX(), y, p);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int action = e.getActionMasked();
        int idx = e.getActionIndex();
        float x = e.getX(idx);
        float y = e.getY(idx);

        if (panelMode != PanelMode.NONE && (panelPressedItem != null || panelDragging || y >= keyAreaRect.top)) {
            return handlePanelTouch(e);
        }

        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                // Multi-touch fast tap: commit prior key immediately if not gliding/trackpadding
                if (pressedKey != null && !longPressActive && !spaceTrackpadActive
                        && !deleteSwipeActive && !swipeGlideActive) {
                    commitKey(pressedKey, swipeUpTriggered);
                    cancelTimers();
                }

                if (y <= toolbarRect.bottom) {
                    if (handleToolbarTap(x, y)) return true;
                }

                KeySpec hit = findKeyAt(x, y);
                if (hit != null) {
                    activePointerId = e.getPointerId(idx);
                    pressedKey = hit;
                    downX = x;
                    downY = y;
                    lastTrackX = x;
                    spaceTrackpadActive = false;
                    deleteSwipeActive = false;
                    swipeUpTriggered = false;
                    longPressActive = false;
                    repeating = false;

                    // Initialize glide stroke buffer if starting on a letter key in ALPHA layer
                    swipeGlideActive = false;
                    swipeCount = 0;
                    swipeTotalDist = 0f;
                    if (swipeTypingEnabled && currentLayer == KeyboardLayouts.Layer.ALPHA && hit.isLetter()) {
                        swipeX[0] = x;
                        swipeY[0] = y;
                        swipeCount = 1;
                        swipeShiftSnapshot = shiftState;
                    }

                    if (listener != null) listener.onHaptic(true);

                    cancelTimers();
                    long lpDelay = hit.repeatable ? 340L : 290L;
                    handler.postDelayed(longPressRunnable, lpDelay);
                    invalidate();
                }
                return true;
            }

            case MotionEvent.ACTION_MOVE: {
                int pIdx = e.findPointerIndex(activePointerId);
                if (pIdx < 0 || pressedKey == null) return true;
                float mx = e.getX(pIdx);
                float my = e.getY(pIdx);

                // Sliding long-press popup selection
                if (longPressActive && pressedKey.popups != null && pressedKey.popups.length > 0) {
                    float rel = (mx - popupStripRect.left) / Math.max(1f, popupStripRect.width());
                    int newIdx = (int) Math.floor(rel * pressedKey.popups.length);
                    newIdx = Math.max(0, Math.min(pressedKey.popups.length - 1, newIdx));
                    if (newIdx != popupSelectedIndex) {
                        popupSelectedIndex = newIdx;
                        if (listener != null) listener.onHaptic(true);
                        invalidate();
                    }
                    return true;
                }

                // Spacebar horizontal cursor trackpad
                if (pressedKey.code == KeySpec.CODE_SPACE && spaceCursor) {
                    float dxTotal = mx - downX;
                    if (!spaceTrackpadActive && Math.abs(dxTotal) > 18f * dp) {
                        spaceTrackpadActive = true;
                        cancelTimers();
                        lastTrackX = mx;
                        invalidate();
                    }
                    if (spaceTrackpadActive) {
                        float step = 14f * dp;
                        float deltaX = mx - lastTrackX;
                        while (deltaX >= step) {
                            if (listener != null) {
                                listener.onCursorMove(1);
                                listener.onHaptic(true);
                            }
                            lastTrackX += step;
                            deltaX = mx - lastTrackX;
                        }
                        while (deltaX <= -step) {
                            if (listener != null) {
                                listener.onCursorMove(-1);
                                listener.onHaptic(true);
                            }
                            lastTrackX -= step;
                            deltaX = mx - lastTrackX;
                        }
                    }
                    return true;
                }

                // Swipe-left on Backspace -> delete whole words
                if (pressedKey.code == KeySpec.CODE_DELETE) {
                    float dxTotal = mx - downX;
                    if (!deleteSwipeActive && dxTotal < -28f * dp) {
                        deleteSwipeActive = true;
                        repeating = false;
                        cancelTimers();
                        lastTrackX = mx;
                        if (listener != null) {
                            listener.onDeleteWord();
                            listener.onHaptic(false);
                        }
                    } else if (deleteSwipeActive) {
                        float step = 28f * dp;
                        if (mx - lastTrackX <= -step) {
                            lastTrackX = mx;
                            if (listener != null) {
                                listener.onDeleteWord();
                                listener.onHaptic(true);
                            }
                        }
                    }
                    return true;
                }

                // Complex Glide / Swipe Typing vs Swipe-Up Alt Symbol disambiguation
                if (swipeTypingEnabled && currentLayer == KeyboardLayouts.Layer.ALPHA
                        && pressedKey.isLetter() && swipeCount > 0) {
                    // Consume batched historical points for ultra-accurate high-speed curves!
                    int hist = e.getHistorySize();
                    for (int h = 0; h < hist; h++) {
                        appendSwipePoint(e.getHistoricalX(pIdx, h), e.getHistoricalY(pIdx, h));
                    }
                    appendSwipePoint(mx, my);

                    if (!swipeGlideActive) {
                        float absDx = Math.abs(mx - downX);
                        float dy = my - downY;
                        // Cancel long-press as soon as thumb starts moving so swipe never gets blocked!
                        if (swipeTotalDist > 11f * dp) {
                            cancelTimers();
                        }
                        KeySpec under = findKeyAt(mx, my);
                        boolean movedToAnotherLetter = (under != null && under.isLetter() && under != pressedKey);
                        boolean straightUpFlick = swipeUpSym && pressedKey.altText != null
                                && dy < -24f * dp && absDx < approxKeyWidth * 0.45f && !movedToAnotherLetter;

                        if (swipeTotalDist > approxKeyWidth * 0.50f
                                && (movedToAnotherLetter || absDx > approxKeyWidth * 0.48f || dy > approxKeyWidth * 0.50f)) {
                            // Engage Neon Glide Typing immediately!
                            swipeGlideActive = true;
                            swipeUpTriggered = false;
                            cancelTimers();
                            invalidate();
                            return true;
                        } else if (straightUpFlick) {
                            if (!swipeUpTriggered) {
                                swipeUpTriggered = true;
                                cancelTimers();
                                if (listener != null) listener.onHaptic(true);
                                invalidate();
                            }
                            return true;
                        }
                    } else {
                        invalidate();
                        return true;
                    }
                }

                // Swipe-up alt symbol for non-letter keys (numbers, punctuation)
                if (!swipeGlideActive && swipeUpSym && pressedKey.altText != null && !pressedKey.special) {
                    boolean nowUp = (downY - my) > 25f * dp && Math.abs(mx - downX) < approxKeyWidth * 0.65f;
                    if (nowUp != swipeUpTriggered) {
                        swipeUpTriggered = nowUp;
                        if (nowUp) {
                            cancelTimers();
                            if (listener != null) listener.onHaptic(true);
                        }
                        invalidate();
                    }
                }
                return true;
            }

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP: {
                int pid = e.getPointerId(idx);
                if (pid != activePointerId) return true;
                cancelTimers();

                if (swipeGlideActive && swipeCount >= 2) {
                    if (listener != null) {
                        listener.onSwipeGesture(swipeX, swipeY, swipeCount, approxKeyWidth, swipeShiftSnapshot);
                        listener.onHaptic(true);
                    }
                    swipeFadeEndMs = SystemClock.uptimeMillis() + 140L;
                    swipeGlideActive = false;
                    resetTouchState();
                    postInvalidateOnAnimation();
                    return true;
                }

                if (pressedKey != null) {
                    // Trigger crisp neon afterglow on keycap
                    glowKey = pressedKey;
                    glowEndMs = SystemClock.uptimeMillis() + AFTERGLOW_MS;

                    if (longPressActive && pressedKey.popups != null && pressedKey.popups.length > 0) {
                        String chosen = pressedKey.popups[popupSelectedIndex];
                        if (listener != null) listener.onCommitText(chosen);
                    } else if (!spaceTrackpadActive && !deleteSwipeActive && !repeating) {
                        commitKey(pressedKey, swipeUpTriggered);
                    }
                }
                swipeCount = 0;
                resetTouchState();
                postInvalidateOnAnimation();
                return true;
            }

            case MotionEvent.ACTION_CANCEL: {
                cancelTimers();
                swipeGlideActive = false;
                swipeCount = 0;
                resetTouchState();
                invalidate();
                return true;
            }
        }
        return true;
    }

    private void appendSwipePoint(float px, float py) {
        if (swipeCount <= 0 || swipeCount >= MAX_SWIPE_PTS) return;
        float dx = px - swipeX[swipeCount - 1];
        float dy = py - swipeY[swipeCount - 1];
        float dist = (float) Math.hypot(dx, dy);
        if (dist >= 3.5f * dp) {
            swipeX[swipeCount] = px;
            swipeY[swipeCount] = py;
            swipeCount++;
            swipeTotalDist += dist;
        }
    }

    private boolean handleToolbarTap(float x, float y) {
        if (voiceListening) {
            if (listener != null) {
                listener.onHaptic(false);
                listener.onVoiceToggle();
            }
            return true;
        }

        for (ToolbarBtn b : toolbarButtons) {
            if (b.rect.contains(x, y)) {
                if (listener != null) listener.onHaptic(true);
                switch (b.id) {
                    case "DEV_TOGGLE":
                        devBarVisible = !devBarVisible;
                        panelMode = PanelMode.NONE;
                        layoutToolbar(getWidth());
                        invalidate();
                        return true;
                    case "CLIP":
                        openPanel(PanelMode.CLIPBOARD);
                        return true;
                    case "SNIPPETS":
                        openPanel(PanelMode.SNIPPETS);
                        return true;
                    case "EMOJI":
                        openPanel(PanelMode.EMOJI);
                        return true;
                    case "VOICE":
                        panelMode = PanelMode.NONE;
                        if (listener != null) listener.onVoiceToggle();
                        return true;
                    case "SETTINGS":
                        if (listener != null) listener.onOpenSettings();
                        return true;
                }
            }
        }

        if (!devBarVisible) {
            for (SuggestionChip chip : suggestionChips) {
                if (chip.rect.contains(x, y)) {
                    if (listener != null) {
                        listener.onHaptic(true);
                        if (chip.isQuickPaste && quickPastePreview != null) {
                            listener.onCommitText(quickPastePreview);
                        } else {
                            listener.onSuggestionPicked(chip.label);
                        }
                    }
                    return true;
                }
            }
        }
        return false;
    }

    private boolean handlePanelTouch(MotionEvent e) {
        float x = e.getX();
        float y = e.getY();

        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (panelCloseBtn.contains(x, y)) {
                    if (listener != null) listener.onHaptic(true);
                    panelMode = PanelMode.NONE;
                    layoutToolbar(getWidth());
                    invalidate();
                    return true;
                }
                if (panelMode == PanelMode.EMOJI && !panelTabs.isEmpty()) {
                    for (int ti = 0; ti < panelTabs.size(); ti++) {
                        if (panelTabs.get(ti).rect.contains(x, y)) {
                            if (listener != null) listener.onHaptic(true);
                            glyphTabIndex = ti;
                            panelScrollY = 0f;
                            rebuildPanelItems();
                            invalidate();
                            return true;
                        }
                    }
                }
                if (panelMode == PanelMode.CLIPBOARD && panelClearBtn.contains(x, y)) {
                    if (listener != null) {
                        listener.onHaptic(false);
                        listener.onClearRecentClips();
                    }
                    rebuildPanelItems();
                    invalidate();
                    return true;
                }
                panelTouchDownY = y;
                panelLastY = y;
                panelDragging = false;
                panelLongPressTriggered = false;
                panelPressedItem = findPanelItem(x, y + panelScrollY);
                if (panelPressedItem != null && panelMode == PanelMode.CLIPBOARD) {
                    handler.postDelayed(panelLongPressRunnable, 420L);
                }
                invalidate();
                return true;

            case MotionEvent.ACTION_MOVE:
                float dy = y - panelLastY;
                if (!panelDragging && Math.abs(y - panelTouchDownY) > 10f * dp) {
                    panelDragging = true;
                    panelPressedItem = null;
                    handler.removeCallbacks(panelLongPressRunnable);
                }
                if (panelDragging) {
                    panelScrollY = Math.max(0f, Math.min(panelMaxScrollY, panelScrollY - dy));
                    panelLastY = y;
                    invalidate();
                }
                return true;

            case MotionEvent.ACTION_UP:
                handler.removeCallbacks(panelLongPressRunnable);
                if (!panelDragging && !panelLongPressTriggered && panelPressedItem != null) {
                    if (listener != null) {
                        listener.onHaptic(true);
                        listener.onCommitText(panelPressedItem.value);
                    }
                    if (panelMode == PanelMode.CLIPBOARD || panelMode == PanelMode.SNIPPETS) {
                        panelMode = PanelMode.NONE;
                        layoutToolbar(getWidth());
                    }
                }
                panelPressedItem = null;
                panelDragging = false;
                invalidate();
                return true;

            case MotionEvent.ACTION_CANCEL:
                handler.removeCallbacks(panelLongPressRunnable);
                panelPressedItem = null;
                panelDragging = false;
                invalidate();
                return true;
        }
        return true;
    }

    private PanelItem findPanelItem(float x, float scrolledY) {
        if (scrolledY - panelScrollY < panelHeaderRect.bottom) return null;
        for (PanelItem item : panelItems) {
            if (item.rect.contains(x, scrolledY)) return item;
        }
        return null;
    }

    private KeySpec findKeyAt(float x, float y) {
        if (devBarVisible && y <= toolbarRect.bottom) {
            for (KeySpec k : devBarKeys) {
                if (k.bounds.contains(x, y)) return k;
            }
        }
        for (List<KeySpec> row : rows) {
            for (KeySpec k : row) {
                if (k.bounds.contains(x, y)) return k;
            }
        }
        return null;
    }

    private void commitKey(KeySpec key, boolean useAlt) {
        if (listener == null || key == null) return;
        if (useAlt && key.altText != null && !key.altText.isEmpty()) {
            listener.onCommitText(key.altText);
            return;
        }

        switch (key.code) {
            case KeySpec.CODE_SHIFT: {
                long now = System.currentTimeMillis();
                if (now - lastShiftTapMs < 320L && shiftState == SHIFT_ONCE) {
                    listener.onShiftLock();
                    lastShiftTapMs = 0L;
                } else {
                    listener.onShiftToggle();
                    lastShiftTapMs = now;
                }
                break;
            }
            case KeySpec.CODE_DELETE: listener.onDeleteChar(); break;
            case KeySpec.CODE_MODE_SYM1: listener.onLayerSwitch(KeyboardLayouts.Layer.SYM1); break;
            case KeySpec.CODE_MODE_SYM2: listener.onLayerSwitch(KeyboardLayouts.Layer.SYM2); break;
            case KeySpec.CODE_MODE_ALPHA: listener.onLayerSwitch(KeyboardLayouts.Layer.ALPHA); break;
            case KeySpec.CODE_MODE_NUMPAD: listener.onLayerSwitch(KeyboardLayouts.Layer.NUMPAD); break;
            case KeySpec.CODE_CTRL: listener.onCtrlToggle(); break;
            case KeySpec.CODE_ALT: listener.onAltToggle(); break;
            default: listener.onKeyPress(key, shiftState != SHIFT_OFF); break;
        }
    }

    private void cancelTimers() {
        handler.removeCallbacks(longPressRunnable);
        handler.removeCallbacks(repeatRunnable);
        repeating = false;
    }

    private void resetTouchState() {
        activePointerId = -1;
        pressedKey = null;
        spaceTrackpadActive = false;
        deleteSwipeActive = false;
        swipeUpTriggered = false;
        longPressActive = false;
        repeating = false;
    }

    private static final class ToolbarBtn {
        final String id;
        final String label;
        final RectF rect;
        final boolean active;

        ToolbarBtn(String id, String label, RectF rect, boolean active) {
            this.id = id;
            this.label = label;
            this.rect = rect;
            this.active = active;
        }
    }

    private static final class SuggestionChip {
        final String label;
        final boolean isQuickPaste;
        final RectF rect;

        SuggestionChip(String label, boolean isQuickPaste, RectF rect) {
            this.label = label;
            this.isQuickPaste = isQuickPaste;
            this.rect = rect;
        }
    }

    private static final class PanelItem {
        final String label;
        final String value;
        final boolean pinned;
        final RectF rect;

        PanelItem(String label, String value, boolean pinned, RectF rect) {
            this.label = label;
            this.value = value;
            this.pinned = pinned;
            this.rect = rect;
        }
    }
}
