package dev.redline.keyboard;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.inputmethodservice.InputMethodService;
import android.os.Build;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Redline Keyboard v2.2 Android InputMethodService.
 * Integrates complex SHARK2+DP swipe decoding, AI autocorrect with 1-tap undo,
 * next-word assumption bigrams, instant AI voice dictation with foreground fallback,
 * custom fonts, GPU neon rendering, Termux hardware Ctrl/Alt keys, clipboard & snippets.
 */
public final class RedlineImeService extends InputMethodService
        implements RedlineKeyboardView.Listener, VoiceController.Callback {

    private static final Set<String> TERMINAL_PACKAGES = new HashSet<>();
    static {
        TERMINAL_PACKAGES.add("com.termux");
        TERMINAL_PACKAGES.add("jackpal.androidterm");
        TERMINAL_PACKAGES.add("com.sonelli.juicessh");
        TERMINAL_PACKAGES.add("org.connectbot");
        TERMINAL_PACKAGES.add("com.server.auditor.ssh.client");
        TERMINAL_PACKAGES.add("com.offsec.nhterm");
        TERMINAL_PACKAGES.add("com.foxdebug.acode");
    }

    // Shared bridge for foreground VoiceCaptureActivity fallback delivery
    private static volatile RedlineImeService activeInstance = null;
    private static volatile String pendingExternalVoiceText = null;

    public static void deliverExternalVoiceText(String text) {
        if (text == null || text.isEmpty()) return;
        pendingExternalVoiceText = text;
        RedlineImeService inst = activeInstance;
        if (inst != null) {
            inst.tryFlushPendingVoiceText();
        }
    }

    private RedlinePrefs prefs;
    private WordEngine wordEngine;
    private SwipeDecoder swipeDecoder;
    private VoiceController voiceController;
    private ClipboardStore clipboardStore;
    private RedlineKeyboardView keyboardView;
    private Vibrator vibrator;

    private int shiftState = RedlineKeyboardView.SHIFT_OFF;
    private boolean ctrlActive = false;
    private boolean altActive = false;

    private boolean isPasswordField = false;
    private boolean isTerminalField = false;
    private boolean isUrlOrEmail = false;
    private int imeActionId = EditorInfo.IME_ACTION_NONE;
    private long lastSpaceMs = 0L;

    // Swipe undo / swap state
    private int lastSwipedTotalChars = 0;
    private boolean lastSwipedHadLeadingSpace = false;
    private boolean lastSwipedHasTrailingSpace = false;
    private int lastSwipedShiftState = RedlineKeyboardView.SHIFT_OFF;

    // Duplicate voice guard
    private String lastVoiceFinalText = "";
    private long lastVoiceFinalMs = 0L;

    // AI Autocorrect undo & bigram state
    private String lastAutoFixOriginal = null;
    private int lastAutoFixCommittedLen = 0;
    private String lastCommittedWord = "";

    // Live streaming voice composing state
    private boolean voiceComposingActive = false;
    private boolean voiceComposingNeedSpace = false;

    @Override
    public void onCreate() {
        super.onCreate();
        activeInstance = this;
        prefs = new RedlinePrefs(this);
        wordEngine = new WordEngine();
        swipeDecoder = new SwipeDecoder();
        wordEngine.loadAsync(this, words -> swipeDecoder.setDictionary(words));
        voiceController = new VoiceController(this, prefs, this);
        clipboardStore = new ClipboardStore(this, prefs);
        clipboardStore.start();
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
    }

    @Override
    public void onDestroy() {
        if (activeInstance == this) activeInstance = null;
        if (voiceController != null) voiceController.release();
        if (clipboardStore != null) clipboardStore.stop();
        super.onDestroy();
    }

    @Override
    public boolean onEvaluateFullscreenMode() {
        return false;
    }

    @Override
    public View onCreateInputView() {
        keyboardView = new RedlineKeyboardView(this);
        keyboardView.setListener(this);
        keyboardView.applyPrefs(prefs);
        syncNavBarColor();
        return keyboardView;
    }

    @Override
    public void onFinishInputView(boolean finishingInput) {
        if (voiceController != null && voiceController.isListening()) {
            voiceController.stop(true);
        }
        voiceComposingActive = false;
        lastSwipedTotalChars = 0;
        super.onFinishInputView(finishingInput);
    }

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        if (keyboardView == null) return;

        keyboardView.applyPrefs(prefs);
        syncNavBarColor();
        clipboardStore.captureCurrent();
        lastSwipedTotalChars = 0;
        lastAutoFixOriginal = null;
        lastAutoFixCommittedLen = 0;
        voiceComposingActive = false;

        int inputType = info != null ? info.inputType : 0;
        int cls = inputType & InputType.TYPE_MASK_CLASS;
        int var = inputType & InputType.TYPE_MASK_VARIATION;
        String pkg = info != null && info.packageName != null ? info.packageName : "";

        isPasswordField = (cls == InputType.TYPE_CLASS_TEXT && (
                var == InputType.TYPE_TEXT_VARIATION_PASSWORD
                        || var == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
                        || var == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD))
                || (cls == InputType.TYPE_CLASS_NUMBER && var == InputType.TYPE_NUMBER_VARIATION_PASSWORD);

        isUrlOrEmail = cls == InputType.TYPE_CLASS_TEXT && (
                var == InputType.TYPE_TEXT_VARIATION_URI
                        || var == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                        || var == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS);

        isTerminalField = inputType == InputType.TYPE_NULL
                || TERMINAL_PACKAGES.contains(pkg)
                || pkg.contains("termux")
                || pkg.contains("terminal");

        boolean showDevBar = prefs.isDevBarDefault() || (prefs.isAutoDevTermux() && isTerminalField);

        KeyboardLayouts.Layer initialLayer = KeyboardLayouts.Layer.ALPHA;
        if (cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_PHONE) {
            initialLayer = KeyboardLayouts.Layer.NUMPAD;
        }

        imeActionId = EditorInfo.IME_ACTION_NONE;
        String actionLabel = "↵";
        if (info != null) {
            int action = info.imeOptions & EditorInfo.IME_MASK_ACTION;
            boolean noEnterAction = (info.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0;
            if (!noEnterAction && action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
                imeActionId = action;
                switch (action) {
                    case EditorInfo.IME_ACTION_GO: actionLabel = "GO"; break;
                    case EditorInfo.IME_ACTION_SEARCH: actionLabel = "SRCH"; break;
                    case EditorInfo.IME_ACTION_SEND: actionLabel = "SEND"; break;
                    case EditorInfo.IME_ACTION_NEXT: actionLabel = "NEXT"; break;
                    case EditorInfo.IME_ACTION_DONE: actionLabel = "DONE"; break;
                    default: actionLabel = "↵"; break;
                }
            }
        }

        ctrlActive = false;
        altActive = false;
        keyboardView.setModifiers(false, false);
        keyboardView.setEditorContext(isPasswordField, isUrlOrEmail, actionLabel, showDevBar, initialLayer);

        updateAutoCaps();
        refreshSuggestions();
        tryFlushPendingVoiceText();
    }

    private void tryFlushPendingVoiceText() {
        String txt = pendingExternalVoiceText;
        if (txt == null || txt.isEmpty()) return;
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        pendingExternalVoiceText = null;
        onVoiceFinal(txt);
    }

    @Override
    public void onUpdateSelection(int oldSelStart, int oldSelEnd, int newSelStart, int newSelEnd,
                                  int candidatesStart, int candidatesEnd) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd);
        if (keyboardView != null && lastSwipedTotalChars == 0 && !voiceComposingActive) {
            updateAutoCaps();
            refreshSuggestions();
        }
    }

    private void syncNavBarColor() {
        try {
            Dialog d = getWindow();
            if (d != null) {
                Window w = d.getWindow();
                if (w != null && Build.VERSION.SDK_INT >= 21) {
                    w.setNavigationBarColor(prefs.getTheme().deckBg);
                }
            }
        } catch (Throwable ignored) {}
    }

    // ===================== SWIPE / GLIDE DECODING =====================

    @Override
    public void onKeyGeometryChanged(float[] centersX, float[] centersY, float keyWidth) {
        if (swipeDecoder != null) {
            swipeDecoder.updateKeyGeometry(centersX, centersY, keyWidth);
        }
    }

    @Override
    public void onSwipeGesture(float[] xs, float[] ys, int count, float keyWidth, int shiftSnapshot) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null || swipeDecoder == null) return;

        List<String> rawCandidates = swipeDecoder.decode(xs, ys, count, keyWidth, wordEngine.getUserBoostSnapshot());
        if (rawCandidates.isEmpty()) return;

        List<String> formatted = new ArrayList<>(rawCandidates.size());
        for (String w : rawCandidates) {
            formatted.add(applyShiftCase(w, shiftSnapshot));
        }

        String best = formatted.get(0);
        boolean needLeadSpace = shouldInsertLeadingSpace(ic);
        String toCommit = (needLeadSpace ? " " : "") + best + " ";
        ic.commitText(toCommit, 1);

        lastSwipedTotalChars = toCommit.length();
        lastSwipedHadLeadingSpace = needLeadSpace;
        lastSwipedHasTrailingSpace = true;
        lastSwipedShiftState = shiftSnapshot;
        lastAutoFixOriginal = null;
        lastAutoFixCommittedLen = 0;
        lastSpaceMs = 0L;

        if (!isPasswordField && !isTerminalField) {
            wordEngine.learnWord(rawCandidates.get(0));
            if (!lastCommittedWord.isEmpty()) {
                wordEngine.learnBigram(lastCommittedWord, rawCandidates.get(0));
            }
            lastCommittedWord = rawCandidates.get(0);
        }

        if (shiftState == RedlineKeyboardView.SHIFT_ONCE) {
            shiftState = RedlineKeyboardView.SHIFT_OFF;
            keyboardView.setShiftState(shiftState);
        }

        keyboardView.setSuggestions(formatted, null);
    }

    private boolean shouldInsertLeadingSpace(InputConnection ic) {
        CharSequence before = ic.getTextBeforeCursor(2, 0);
        if (before == null || before.length() == 0) return false;
        char last = before.charAt(before.length() - 1);
        return Character.isLetterOrDigit(last)
                || last == '.' || last == ',' || last == '!' || last == '?' || last == ';' || last == ':';
    }

    private static String applyShiftCase(String word, int shift) {
        if (word == null || word.isEmpty()) return "";
        if (shift == RedlineKeyboardView.SHIFT_LOCK) {
            return word.toUpperCase(Locale.US);
        }
        if (shift == RedlineKeyboardView.SHIFT_ONCE || "i".equals(word) || word.startsWith("i'")) {
            return Character.toUpperCase(word.charAt(0)) + word.substring(1);
        }
        return word;
    }

    // ===================== AI VOICE CALLBACKS =====================

    @Override
    public void onVoiceToggle() {
        if (voiceController != null) {
            voiceController.toggle();
        }
    }

    @Override
    public void onVoiceStateChanged(boolean listening, String statusText) {
        if (!listening && voiceComposingActive) {
            // Cancelled or ended without onVoiceFinal: clear dangling preview instead of committing a duplicate!
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) ic.setComposingText("", 1);
            voiceComposingActive = false;
        }
        if (keyboardView != null) {
            keyboardView.setVoiceState(listening, statusText);
        }
    }

    @Override
    public void onVoiceRms(float normalizedLevel) {
        if (keyboardView != null) {
            keyboardView.setVoiceRms(normalizedLevel);
        }
    }

    @Override
    public void onVoicePartial(String partialText) {
        if (keyboardView != null) {
            keyboardView.setVoicePartial(partialText);
        }
        InputConnection ic = getCurrentInputConnection();
        if (ic != null && partialText != null && !partialText.isEmpty() && !isTerminalField) {
            if (!voiceComposingActive) {
                voiceComposingNeedSpace = shouldInsertLeadingSpace(ic);
                voiceComposingActive = true;
            }
            ic.setComposingText((voiceComposingNeedSpace ? " " : "") + partialText, 1);
        }
    }

    @Override
    public void onVoiceFinal(String formattedText) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null || formattedText == null || formattedText.isEmpty()) return;

        // Dedupe guard against duplicate OEM recognizer callbacks within 700ms
        long now = System.currentTimeMillis();
        if (formattedText.equals(lastVoiceFinalText) && (now - lastVoiceFinalMs) < 700L) {
            if (voiceComposingActive) {
                ic.setComposingText("", 1);
                voiceComposingActive = false;
            }
            return;
        }
        lastVoiceFinalText = formattedText;
        lastVoiceFinalMs = now;

        String out = formattedText;
        if (shiftState != RedlineKeyboardView.SHIFT_OFF && Character.isLetter(out.charAt(0))) {
            out = Character.toUpperCase(out.charAt(0)) + out.substring(1);
        }
        boolean needSpace = voiceComposingActive
                ? voiceComposingNeedSpace
                : (shouldInsertLeadingSpace(ic)
                    && !out.startsWith(".") && !out.startsWith(",")
                    && !out.startsWith("!") && !out.startsWith("?"));

        // commitText cleanly replaces active composing text ONCE with zero duplication!
        voiceComposingActive = false;
        ic.commitText((needSpace ? " " : "") + out + " ", 1);
        lastSwipedTotalChars = 0;
        lastSwipedHasTrailingSpace = false;
        lastAutoFixOriginal = null;
        lastAutoFixCommittedLen = 0;
        updateAutoCaps();
        refreshSuggestions();
    }

    @Override
    public void onVoicePermissionNeeded() {
        if (keyboardView != null) {
            keyboardView.showTransientBanner("GRANT MIC ACCESS...");
        }
        Intent i = new Intent(this, VoiceCaptureActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        startActivity(i);
    }

    @Override
    public void onVoiceNeedForegroundFallback() {
        if (voiceComposingActive) {
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) ic.setComposingText("", 1);
            voiceComposingActive = false;
        }
        Intent i = new Intent(this, VoiceCaptureActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        startActivity(i);
    }

    @Override
    public void onVoiceError(String message) {
        if (voiceComposingActive) {
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) ic.setComposingText("", 1);
            voiceComposingActive = false;
        }
        if (keyboardView != null && message != null) {
            keyboardView.showTransientBanner(message.toUpperCase(Locale.US));
        }
    }

    // ===================== KEY EVENTS =====================

    @Override
    public void onKeyPress(KeySpec key, boolean shifted) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null || key == null) return;
        lastSwipedTotalChars = 0;

        switch (key.code) {
            case KeySpec.CODE_ESC:
                sendHardwareKey(ic, KeyEvent.KEYCODE_ESCAPE, currentMeta());
                clearOneShotMods();
                return;
            case KeySpec.CODE_TAB:
                sendHardwareKey(ic, KeyEvent.KEYCODE_TAB, currentMeta());
                clearOneShotMods();
                return;
            case KeySpec.CODE_LEFT:
                sendHardwareKey(ic, KeyEvent.KEYCODE_DPAD_LEFT, currentMeta());
                return;
            case KeySpec.CODE_RIGHT:
                sendHardwareKey(ic, KeyEvent.KEYCODE_DPAD_RIGHT, currentMeta());
                return;
            case KeySpec.CODE_UP:
                sendHardwareKey(ic, KeyEvent.KEYCODE_DPAD_UP, currentMeta());
                return;
            case KeySpec.CODE_DOWN:
                sendHardwareKey(ic, KeyEvent.KEYCODE_DPAD_DOWN, currentMeta());
                return;
            case KeySpec.CODE_HOME:
                sendHardwareKey(ic, KeyEvent.KEYCODE_MOVE_HOME, currentMeta());
                return;
            case KeySpec.CODE_END:
                sendHardwareKey(ic, KeyEvent.KEYCODE_MOVE_END, currentMeta());
                return;
            case KeySpec.CODE_PASTE: {
                String latest = clipboardStore.getLatestText();
                if (latest != null && !latest.isEmpty()) ic.commitText(latest, 1);
                else ic.performContextMenuAction(android.R.id.paste);
                return;
            }
            case KeySpec.CODE_ENTER:
                handleEnter(ic);
                return;
            case KeySpec.CODE_SPACE:
                handleSpace(ic);
                return;
        }

        if (ctrlActive || altActive) {
            int kc = charToKeyCode(key.code);
            if (kc != KeyEvent.KEYCODE_UNKNOWN) {
                sendHardwareKey(ic, kc, currentMeta());
                clearOneShotMods();
                return;
            }
        }

        String out = key.label;
        if (key.isLetter() && shifted) {
            out = out.toUpperCase();
        }
        if (out.length() == 1 && ".,!?;:".indexOf(out.charAt(0)) >= 0) {
            // Absorb auto-trailing space from a swiped word so "hello " + "." -> "hello. "
            if (lastSwipedHasTrailingSpace && !isTerminalField) {
                ic.deleteSurroundingText(1, 0);
                lastSwipedHasTrailingSpace = false;
                ic.commitText(out + " ", 1);
                lastSpaceMs = 0L;
                updateAutoCaps();
                refreshSuggestions();
                return;
            }
            maybeAutoCorrectCurrentWord(ic, out);
            ic.commitText(out, 1);
            lastSpaceMs = 0L;
            updateAutoCaps();
            refreshSuggestions();
            return;
        }
        lastSwipedHasTrailingSpace = false;
        lastAutoFixOriginal = null;
        lastAutoFixCommittedLen = 0;
        ic.commitText(out, 1);
        lastSpaceMs = 0L;

        if (shiftState == RedlineKeyboardView.SHIFT_ONCE) {
            shiftState = RedlineKeyboardView.SHIFT_OFF;
            keyboardView.setShiftState(shiftState);
        }
        refreshSuggestions();
    }

    @Override
    public void onCommitText(String text) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null || text == null || text.isEmpty()) return;
        lastSwipedTotalChars = 0;
        lastAutoFixOriginal = null;
        lastAutoFixCommittedLen = 0;
        ic.commitText(text, 1);
        lastSpaceMs = 0L;
        if (shiftState == RedlineKeyboardView.SHIFT_ONCE) {
            shiftState = RedlineKeyboardView.SHIFT_OFF;
            keyboardView.setShiftState(shiftState);
        }
        refreshSuggestions();
    }

    @Override
    public void onDeleteChar() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        lastSpaceMs = 0L;

        // 1-tap Backspace undo of last AI auto-correction!
        if (lastAutoFixOriginal != null && lastAutoFixCommittedLen > 0 && !isTerminalField) {
            ic.deleteSurroundingText(lastAutoFixCommittedLen, 0);
            ic.commitText(lastAutoFixOriginal, 1);
            wordEngine.learnWord(lastAutoFixOriginal);
            wordEngine.learnWord(lastAutoFixOriginal);
            lastAutoFixOriginal = null;
            lastAutoFixCommittedLen = 0;
            updateAutoCaps();
            refreshSuggestions();
            return;
        }

        // 1-tap undo of entire last swiped word!
        if (lastSwipedTotalChars > 0 && !isTerminalField) {
            ic.deleteSurroundingText(lastSwipedTotalChars, 0);
            lastSwipedTotalChars = 0;
            updateAutoCaps();
            refreshSuggestions();
            return;
        }

        if (isTerminalField) {
            sendHardwareKey(ic, KeyEvent.KEYCODE_DEL, currentMeta());
            return;
        }
        CharSequence selected = ic.getSelectedText(0);
        if (selected != null && selected.length() > 0) {
            ic.commitText("", 1);
        } else {
            ic.deleteSurroundingText(1, 0);
        }
        updateAutoCaps();
        refreshSuggestions();
    }

    @Override
    public void onDeleteWord() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        lastSpaceMs = 0L;
        lastSwipedTotalChars = 0;
        lastAutoFixOriginal = null;
        lastAutoFixCommittedLen = 0;

        if (isTerminalField) {
            sendHardwareKey(ic, KeyEvent.KEYCODE_W, KeyEvent.META_CTRL_ON | KeyEvent.META_CTRL_LEFT_ON);
            return;
        }
        CharSequence before = ic.getTextBeforeCursor(48, 0);
        if (before == null || before.length() == 0) {
            ic.deleteSurroundingText(1, 0);
            return;
        }
        int i = before.length();
        while (i > 0 && Character.isWhitespace(before.charAt(i - 1))) i--;
        while (i > 0 && !Character.isWhitespace(before.charAt(i - 1))) i--;
        int count = before.length() - i;
        if (count > 0) {
            ic.deleteSurroundingText(count, 0);
        }
        updateAutoCaps();
        refreshSuggestions();
    }

    @Override
    public void onCursorMove(int delta) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null || delta == 0) return;
        lastSwipedTotalChars = 0;
        lastAutoFixOriginal = null;
        lastAutoFixCommittedLen = 0;
        int kc = delta > 0 ? KeyEvent.KEYCODE_DPAD_RIGHT : KeyEvent.KEYCODE_DPAD_LEFT;
        int steps = Math.abs(delta);
        for (int i = 0; i < steps; i++) {
            sendHardwareKey(ic, kc, 0);
        }
    }

    @Override
    public void onSuggestionPicked(String word) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null || word == null) return;

        if (lastSwipedTotalChars > 0) {
            ic.deleteSurroundingText(lastSwipedTotalChars, 0);
            String replacement = (lastSwipedHadLeadingSpace ? " " : "") + applyShiftCase(word, lastSwipedShiftState) + " ";
            ic.commitText(replacement, 1);
            lastSwipedTotalChars = replacement.length();
            lastSwipedHasTrailingSpace = true;
            wordEngine.learnWord(word);
            return;
        }

        String cur = getCurrentWordBeforeCursor(ic);
        if (!cur.isEmpty()) {
            ic.deleteSurroundingText(cur.length(), 0);
        }
        boolean needLeadSpace = cur.isEmpty() && shouldInsertLeadingSpace(ic);
        ic.commitText((needLeadSpace ? " " : "") + word + " ", 1);
        if (!isPasswordField && !isTerminalField) {
            wordEngine.learnWord(word);
            if (!lastCommittedWord.isEmpty()) {
                wordEngine.learnBigram(lastCommittedWord, word);
            }
            lastCommittedWord = word;
        }
        lastAutoFixOriginal = null;
        lastAutoFixCommittedLen = 0;
        updateAutoCaps();
        refreshSuggestions();
    }

    @Override
    public void onShiftToggle() {
        shiftState = (shiftState == RedlineKeyboardView.SHIFT_OFF)
                ? RedlineKeyboardView.SHIFT_ONCE : RedlineKeyboardView.SHIFT_OFF;
        keyboardView.setShiftState(shiftState);
    }

    @Override
    public void onShiftLock() {
        shiftState = RedlineKeyboardView.SHIFT_LOCK;
        keyboardView.setShiftState(shiftState);
    }

    @Override
    public void onCtrlToggle() {
        ctrlActive = !ctrlActive;
        keyboardView.setModifiers(ctrlActive, altActive);
    }

    @Override
    public void onAltToggle() {
        altActive = !altActive;
        keyboardView.setModifiers(ctrlActive, altActive);
    }

    @Override
    public void onLayerSwitch(KeyboardLayouts.Layer layer) {
        keyboardView.setLayer(layer);
    }

    @Override
    public void onOpenSettings() {
        Intent i = new Intent(this, RedlineSettingsActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
    }

    @Override
    public void onSwitchIme() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.showInputMethodPicker();
    }

    @Override
    public void onHaptic(boolean light) {
        int ms = prefs.getHapticMs();
        if (ms <= 0 || vibrator == null) return;
        int dur = light ? Math.max(4, (int) (ms * 0.7f)) : ms;
        int amp = light ? 95 : 175;
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                vibrator.vibrate(VibrationEffect.createOneShot(dur, amp));
            } else {
                vibrator.vibrate(dur);
            }
        } catch (Throwable ignored) {}
    }

    @Override public List<String> getClipboardItems() { return clipboardStore.getAllItems(); }
    @Override public boolean isClipPinned(String item) { return clipboardStore.isPinned(item); }
    @Override public void onToggleClipPin(String item) { clipboardStore.togglePin(item); }
    @Override public void onClearRecentClips() { clipboardStore.clearRecent(); }
    @Override public List<String> getSnippets() { return prefs.getSnippets(); }

    private void maybeAutoCorrectCurrentWord(InputConnection ic, String trailingStr) {
        lastAutoFixOriginal = null;
        lastAutoFixCommittedLen = 0;
        if (!prefs.isAutoCorrectEnabled() || isPasswordField || isTerminalField || isUrlOrEmail) return;
        String typed = getCurrentWordBeforeCursor(ic);
        if (typed.isEmpty()) return;
        String fixed = wordEngine.autoCorrect(typed);
        if (fixed != null && !fixed.equals(typed)) {
            ic.deleteSurroundingText(typed.length(), 0);
            ic.commitText(fixed, 1);
            lastAutoFixOriginal = typed;
            lastAutoFixCommittedLen = fixed.length() + (trailingStr != null ? trailingStr.length() : 0);
            if (!lastCommittedWord.isEmpty()) {
                wordEngine.learnBigram(lastCommittedWord, fixed);
            }
            lastCommittedWord = fixed;
        } else {
            if (typed.length() >= 2) wordEngine.learnWord(typed);
            if (!lastCommittedWord.isEmpty()) {
                wordEngine.learnBigram(lastCommittedWord, typed);
            }
            lastCommittedWord = typed;
        }
    }

    private void handleSpace(InputConnection ic) {
        long now = System.currentTimeMillis();

        if (prefs.isDoubleSpaceDotEnabled() && !isTerminalField && !isUrlOrEmail && !isPasswordField
                && (now - lastSpaceMs) < 380L) {
            CharSequence before = ic.getTextBeforeCursor(3, 0);
            if (before != null && before.length() >= 2
                    && before.charAt(before.length() - 1) == ' '
                    && Character.isLetterOrDigit(before.charAt(before.length() - 2))) {
                ic.deleteSurroundingText(1, 0);
                ic.commitText(". ", 1);
                lastSpaceMs = 0L;
                lastAutoFixOriginal = null;
                lastAutoFixCommittedLen = 0;
                updateAutoCaps();
                refreshSuggestions();
                return;
            }
        }

        maybeAutoCorrectCurrentWord(ic, " ");

        ic.commitText(" ", 1);
        lastSpaceMs = now;
        updateAutoCaps();
        refreshSuggestions();
    }

    private void handleEnter(InputConnection ic) {
        lastSpaceMs = 0L;
        lastAutoFixOriginal = null;
        lastAutoFixCommittedLen = 0;
        if (imeActionId != EditorInfo.IME_ACTION_NONE && !isTerminalField) {
            ic.performEditorAction(imeActionId);
        } else {
            sendHardwareKey(ic, KeyEvent.KEYCODE_ENTER, currentMeta());
        }
        clearOneShotMods();
        updateAutoCaps();
        refreshSuggestions();
    }

    private void updateAutoCaps() {
        if (keyboardView == null) return;
        if (shiftState == RedlineKeyboardView.SHIFT_LOCK) return;
        if (!prefs.isAutoCapsEnabled() || isPasswordField || isTerminalField || isUrlOrEmail) {
            shiftState = RedlineKeyboardView.SHIFT_OFF;
            keyboardView.setShiftState(shiftState);
            return;
        }
        InputConnection ic = getCurrentInputConnection();
        EditorInfo ei = getCurrentInputEditorInfo();
        if (ic == null || ei == null) return;

        int caps = ic.getCursorCapsMode(ei.inputType);
        shiftState = (caps != 0) ? RedlineKeyboardView.SHIFT_ONCE : RedlineKeyboardView.SHIFT_OFF;
        keyboardView.setShiftState(shiftState);
    }

    private void refreshSuggestions() {
        if (keyboardView == null) return;
        if (isPasswordField || !prefs.isSuggestionsEnabled()) {
            keyboardView.setSuggestions(Collections.emptyList(), null);
            return;
        }
        InputConnection ic = getCurrentInputConnection();
        String word = ic != null ? getCurrentWordBeforeCursor(ic) : "";
        if (word.length() >= 1) {
            List<String> list = wordEngine.suggest(word, prefs.isAutoCorrectEnabled() && !isTerminalField && !isUrlOrEmail);
            keyboardView.setSuggestions(list, null);
        } else if (prefs.isNextWordEnabled() && !isTerminalField && !isUrlOrEmail) {
            String prev = getPreviousCompletedWord(ic);
            boolean cap = (shiftState != RedlineKeyboardView.SHIFT_OFF);
            List<String> nexts = wordEngine.predictNextWords(prev, cap);
            keyboardView.setSuggestions(nexts, null);
        } else {
            keyboardView.setSuggestions(Collections.emptyList(), clipboardStore.getLatestText());
        }
    }

    private String getPreviousCompletedWord(InputConnection ic) {
        if (ic == null) return "";
        CharSequence before = ic.getTextBeforeCursor(40, 0);
        if (before == null || before.length() == 0) return "";
        int end = before.length();
        while (end > 0 && Character.isWhitespace(before.charAt(end - 1))) end--;
        if (end == 0) return "";
        char last = before.charAt(end - 1);
        if (last == '.' || last == '!' || last == '?' || last == '\n') return "";
        int start = end;
        while (start > 0) {
            char c = before.charAt(start - 1);
            if (Character.isLetter(c) || c == '\'') start--;
            else break;
        }
        return before.subSequence(start, end).toString();
    }

    private String getCurrentWordBeforeCursor(InputConnection ic) {
        CharSequence before = ic.getTextBeforeCursor(32, 0);
        if (before == null || before.length() == 0) return "";
        int i = before.length();
        while (i > 0) {
            char c = before.charAt(i - 1);
            if (Character.isLetter(c) || c == '\'') i--;
            else break;
        }
        return before.subSequence(i, before.length()).toString();
    }

    private int currentMeta() {
        int meta = 0;
        if (ctrlActive) meta |= KeyEvent.META_CTRL_ON | KeyEvent.META_CTRL_LEFT_ON;
        if (altActive) meta |= KeyEvent.META_ALT_ON | KeyEvent.META_ALT_LEFT_ON;
        if (shiftState != RedlineKeyboardView.SHIFT_OFF) meta |= KeyEvent.META_SHIFT_ON | KeyEvent.META_SHIFT_LEFT_ON;
        return meta;
    }

    private void clearOneShotMods() {
        if (ctrlActive || altActive) {
            ctrlActive = false;
            altActive = false;
            keyboardView.setModifiers(false, false);
        }
    }

    private static void sendHardwareKey(InputConnection ic, int keyCode, int metaState) {
        long now = SystemClock.uptimeMillis();
        ic.sendKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0, metaState));
        ic.sendKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0, metaState));
    }

    private static int charToKeyCode(int code) {
        if (code >= 'a' && code <= 'z') return KeyEvent.KEYCODE_A + (code - 'a');
        if (code >= '0' && code <= '9') return KeyEvent.KEYCODE_0 + (code - '0');
        switch (code) {
            case ' ': return KeyEvent.KEYCODE_SPACE;
            case '-': return KeyEvent.KEYCODE_MINUS;
            case '=': return KeyEvent.KEYCODE_EQUALS;
            case '[': return KeyEvent.KEYCODE_LEFT_BRACKET;
            case ']': return KeyEvent.KEYCODE_RIGHT_BRACKET;
            case '\\': return KeyEvent.KEYCODE_BACKSLASH;
            case ';': return KeyEvent.KEYCODE_SEMICOLON;
            case '\'': return KeyEvent.KEYCODE_APOSTROPHE;
            case ',': return KeyEvent.KEYCODE_COMMA;
            case '.': return KeyEvent.KEYCODE_PERIOD;
            case '/': return KeyEvent.KEYCODE_SLASH;
            case '`': return KeyEvent.KEYCODE_GRAVE;
            default: return KeyEvent.KEYCODE_UNKNOWN;
        }
    }
}
