package dev.redline.keyboard;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.util.List;

/**
 * Redline Keyboard v2.0 Neon Command & Customization Studio.
 * Includes AI Voice mic permission manager, Font Studio (6 bundled + custom .ttf/.otf import),
 * Neon Overdrive intensity & 6 themes, Swipe Typing controls, ergonomics, and custom snippets.
 */
public final class RedlineSettingsActivity extends Activity {

    public static final String EXTRA_REQUEST_MIC = "redline_request_mic";
    private static final int REQ_MIC_PERM = 701;
    private static final int REQ_PICK_FONT = 702;

    private RedlinePrefs prefs;
    private float dp;
    private TextView statusBadge;
    private Button micBtn;
    private LinearLayout themeGrid;
    private LinearLayout fontGrid;
    private LinearLayout snippetContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new RedlinePrefs(this);
        dp = getResources().getDisplayMetrics().density;
        buildUi();

        if (getIntent() != null && getIntent().getBooleanExtra(EXTRA_REQUEST_MIC, false)) {
            requestMicPermission();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateImeStatus();
        updateMicButton();
    }

    private void requestMicPermission() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC_PERM);
        } else {
            Toast.makeText(this, "AI Voice microphone is ready! Tap 🎙 on the keyboard.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_MIC_PERM) {
            updateMicButton();
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Microphone granted — tap 🎙 on Redline to dictate!", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Microphone permission denied — enable it in Android App Info.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void launchFontPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "font/ttf", "font/otf", "application/x-font-ttf", "application/x-font-opentype",
                "application/octet-stream"
        });
        try {
            startActivityForResult(intent, REQ_PICK_FONT);
        } catch (Throwable t) {
            Toast.makeText(this, "Unable to open font picker", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PICK_FONT && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null && RedlineFonts.importCustomFont(this, uri)) {
                String name = queryDisplayName(uri);
                prefs.setCustomFontName(name != null ? name : "Custom Font");
                prefs.setFontId(RedlineFonts.ID_CUSTOM);
                Toast.makeText(this, "Custom font loaded: " + prefs.getCustomFontName(), Toast.LENGTH_SHORT).show();
                buildUi();
            } else {
                Toast.makeText(this, "Invalid font file — pick a .ttf or .otf file.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private String queryDisplayName(Uri uri) {
        try (Cursor c = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                return c.getString(0);
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private void buildUi() {
        RedlineTheme t = prefs.getTheme();
        Typeface activeTf = RedlineFonts.load(this, prefs.getFontId(), true);

        getWindow().setStatusBarColor(t.deckBg);
        getWindow().setNavigationBarColor(t.deckBg);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(t.deckBg);
        scroll.setFillViewport(true);
        scroll.setFitsSystemWindows(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(px(16), px(18), px(16), px(38));
        scroll.addView(root);

        // Top neon laser bar
        View laser = new View(this);
        laser.setBackgroundColor(t.accentPrimary);
        root.addView(laser, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(2)));

        // Header
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, px(14), 0, px(4));

        TextView title = new TextView(this);
        title.setText("REDLINE // v2.4");
        title.setTextColor(t.primaryText);
        title.setTextSize(21f);
        title.setTypeface(activeTf);
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        statusBadge = new TextView(this);
        statusBadge.setTextSize(10.5f);
        statusBadge.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        statusBadge.setPadding(px(8), px(4), px(8), px(4));
        header.addView(statusBadge);
        root.addView(header);

        TextView sub = new TextView(this);
        sub.setText("Swipe Glide · Word Assumption · AI Autocorrect · Voice · Fonts");
        sub.setTextColor(t.hintText);
        sub.setTextSize(12f);
        sub.setPadding(0, 0, 0, px(14));
        root.addView(sub);

        // Quick Setup Buttons
        LinearLayout setupRow = new LinearLayout(this);
        setupRow.setOrientation(LinearLayout.HORIZONTAL);

        Button btnEnable = makeActionButton("1. ENABLE IME", t.actionKeyBg, t.actionKeyText, t.accentPrimary);
        btnEnable.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS));
            } catch (Throwable ex) {
                Toast.makeText(this, "Open Android Settings → System → Languages & Input", Toast.LENGTH_LONG).show();
            }
        });
        LinearLayout.LayoutParams bp1 = new LinearLayout.LayoutParams(0, px(44), 1f);
        bp1.rightMargin = px(8);
        setupRow.addView(btnEnable, bp1);

        Button btnSwitch = makeActionButton("2. SWITCH KEYBOARD", t.specialKeyBg, t.primaryText, t.accentSecondary);
        btnSwitch.setOnClickListener(v -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showInputMethodPicker();
        });
        setupRow.addView(btnSwitch, new LinearLayout.LayoutParams(0, px(44), 1f));
        root.addView(setupRow);

        // Live Typing / Swipe / Voice Sandbox
        addSectionHeader(root, "LIVE TEST DECK (TAP · SWIPE · 🎙 VOICE)", t);
        EditText sandbox = new EditText(this);
        sandbox.setHint("Test glide swipe, 🎙 AI voice dictation, neon glow & custom fonts...");
        sandbox.setHintTextColor(0xFF686C82);
        sandbox.setTextColor(t.primaryText);
        sandbox.setTextSize(15f);
        sandbox.setTypeface(activeTf);
        sandbox.setMinLines(2);
        sandbox.setPadding(px(12), px(12), px(12), px(12));
        sandbox.setBackground(makeCardBg(t.keyBg, t.accentPrimary, 8));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sp.bottomMargin = px(10);
        root.addView(sandbox, sp);

        // Core Smart Typing Controls right at top!
        addSectionHeader(root, "SMART TYPING (SWIPE · ASSUMPTION · AUTOCORRECT)", t);
        LinearLayout smartCard = makeCardContainer(t);
        addToggleRow(smartCard, "Swipe / Glide Typing", "Glide across letters with neon trail (auto-spaces & 1-tap ⌫ undo)",
                prefs.isSwipeTypingEnabled(), t, v -> prefs.setSwipeTypingEnabled(v));
        addToggleRow(smartCard, "AI Autocorrect", "Auto-fix typos & contractions on Space (1-tap ⌫ reverts original)",
                prefs.isAutoCorrectEnabled(), t, v -> prefs.setAutoCorrectEnabled(v));
        addToggleRow(smartCard, "Word Assumption (Next-Word)", "Predict next words after Space & learn your personal phrases",
                prefs.isNextWordEnabled(), t, v -> prefs.setNextWordEnabled(v));
        addToggleRow(smartCard, "Word Suggestion Strip", "Show top 3 live completions, fixes & next-word chips",
                prefs.isSuggestionsEnabled(), t, v -> prefs.setSuggestionsEnabled(v));
        root.addView(smartCard);

        // AI Voice Section
        addSectionHeader(root, "AI VOICE DICTATION (🎙)", t);
        LinearLayout voiceCard = makeCardContainer(t);
        micBtn = makeActionButton("GRANT MICROPHONE ACCESS", t.specialKeyBg, t.accentSecondary, t.accentSecondary);
        micBtn.setOnClickListener(v -> requestMicPermission());
        LinearLayout.LayoutParams mbp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(42));
        mbp.bottomMargin = px(8);
        voiceCard.addView(micBtn, mbp);
        addToggleRow(voiceCard, "Smart Voice Punctuation", "Auto-convert 'period', 'comma', 'question mark', 'new line'",
                prefs.isVoiceSmartPunct(), t, v -> prefs.setVoiceSmartPunct(v));
        addToggleRow(voiceCard, "Always Use Foreground Voice Overlay", "Opens floating neon mic HUD (guaranteed on all Android 13 ROMs)",
                prefs.isVoiceForegroundMode(), t, v -> prefs.setVoiceForegroundMode(v));
        addToggleRow(voiceCard, "Prefer On-Device Offline Voice", "Request strictly local speech recognition when installed",
                prefs.isVoiceOfflineOnly(), t, v -> prefs.setVoiceOfflineOnly(v));
        root.addView(voiceCard);

        // Font Studio Section
        addSectionHeader(root, "FONT STUDIO", t);
        fontGrid = new LinearLayout(this);
        fontGrid.setOrientation(LinearLayout.VERTICAL);
        root.addView(fontGrid);
        renderFontCards();

        Button importFontBtn = makeActionButton("+ IMPORT CUSTOM .TTF / .OTF FONT", t.specialKeyBg, t.accentSecondary, t.accentSecondary);
        importFontBtn.setOnClickListener(v -> launchFontPicker());
        LinearLayout.LayoutParams ifp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(44));
        ifp.bottomMargin = px(8);
        root.addView(importFontBtn, ifp);

        LinearLayout fontTuneCard = makeCardContainer(t);
        addSliderRow(fontTuneCard, "Key Font Size", prefs.getFontScalePct(), 80, 135, "%", t,
                val -> prefs.setFontScalePct(val));
        addToggleRow(fontTuneCard, "Bold Keycap Glyphs", "Crisp high-contrast bold typography on keys",
                prefs.isBoldKeys(), t, v -> { prefs.setBoldKeys(v); renderFontCards(); });
        root.addView(fontTuneCard);

        // Neon & Themes Section
        addSectionHeader(root, "NEON OVERDRIVE & PALETTES", t);
        LinearLayout neonCard = makeCardContainer(t);
        addSliderRow(neonCard, "Neon Glow Intensity", prefs.getNeonIntensity(), 0, 100, "%", t,
                val -> prefs.setNeonIntensity(val));
        LinearLayout.LayoutParams ncp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ncp.bottomMargin = px(8);
        root.addView(neonCard, ncp);

        themeGrid = new LinearLayout(this);
        themeGrid.setOrientation(LinearLayout.VERTICAL);
        root.addView(themeGrid);
        renderThemeCards();

        // Ergonomics & Haptics
        addSectionHeader(root, "ERGONOMICS & HAPTICS", t);
        LinearLayout sliderCard = makeCardContainer(t);
        addSliderRow(sliderCard, "Keyboard Height", prefs.getHeightPct(), 85, 130, "%", t,
                val -> prefs.setHeightPct(val));
        addSliderRow(sliderCard, "Haptic Pulse (0 = off)", prefs.getHapticMs(), 0, 35, "ms", t,
                val -> prefs.setHapticMs(val));
        addSliderRow(sliderCard, "Keycap Corner Radius", prefs.getKeyRadiusDp(), 2, 14, "dp", t,
                val -> prefs.setKeyRadiusDp(val));
        root.addView(sliderCard);

        // Feature Toggles
        addSectionHeader(root, "LAYOUT & POWER FEATURES", t);
        LinearLayout toggleCard = makeCardContainer(t);
        addToggleRow(toggleCard, "Dedicated Number Row", "Always show 1–0 row above QWERTY",
                prefs.isNumberRowEnabled(), t, v -> prefs.setNumberRowEnabled(v));
        addToggleRow(toggleCard, "Auto Dev Bar in Termux", "Auto-show ESC/TAB/CTRL/ALT/Arrows in terminals",
                prefs.isAutoDevTermux(), t, v -> prefs.setAutoDevTermux(v));
        addToggleRow(toggleCard, "Always Show Dev Toolbar", "Keep ESC/TAB/CTRL/ALT bar open in every app",
                prefs.isDevBarDefault(), t, v -> prefs.setDevBarDefault(v));
        addToggleRow(toggleCard, "Swipe-Up for Alt Symbol", "Short upward flick on any key types its corner symbol",
                prefs.isSwipeUpSymEnabled(), t, v -> prefs.setSwipeUpSymEnabled(v));
        addToggleRow(toggleCard, "Spacebar Cursor Trackpad", "Slide thumb on spacebar to scrub cursor left/right",
                prefs.isSpaceCursorEnabled(), t, v -> prefs.setSpaceCursorEnabled(v));
        addToggleRow(toggleCard, "Double-Space Period", "Insert '. ' on quick double-space (off in Termux)",
                prefs.isDoubleSpaceDotEnabled(), t, v -> prefs.setDoubleSpaceDotEnabled(v));
        addToggleRow(toggleCard, "Smart Auto-Capitalization", "Capitalize sentence starts (off in Termux & passwords)",
                prefs.isAutoCapsEnabled(), t, v -> prefs.setAutoCapsEnabled(v));
        addToggleRow(toggleCard, "Floating Key Preview", "Neon-rimmed keycap badge above thumb on tap",
                prefs.isKeyPopupEnabled(), t, v -> prefs.setKeyPopupEnabled(v));
        root.addView(toggleCard);

        // Custom Snippets Manager
        addSectionHeader(root, "QUICK SNIPPETS (⚡ DRAWER)", t);
        LinearLayout snipCard = makeCardContainer(t);

        LinearLayout addRow = new LinearLayout(this);
        addRow.setOrientation(LinearLayout.HORIZONTAL);
        addRow.setGravity(Gravity.CENTER_VERTICAL);
        addRow.setPadding(0, 0, 0, px(10));

        EditText snipInput = new EditText(this);
        snipInput.setHint("Add snippet (e.g. ssh root@...)");
        snipInput.setHintTextColor(0xFF626578);
        snipInput.setTextColor(t.primaryText);
        snipInput.setTextSize(13.5f);
        snipInput.setSingleLine(true);
        snipInput.setTypeface(Typeface.MONOSPACE);
        snipInput.setPadding(px(10), px(8), px(10), px(8));
        snipInput.setBackground(makeCardBg(t.deckBg, t.keyBorder, 6));
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        ip.rightMargin = px(8);
        addRow.addView(snipInput, ip);

        Button addBtn = makeActionButton("+ ADD", t.actionKeyBg, t.actionKeyText, t.accentPrimary);
        addBtn.setOnClickListener(v -> {
            String txt = snipInput.getText().toString();
            if (!txt.isEmpty()) {
                List<String> list = prefs.getSnippets();
                list.add(0, txt);
                prefs.setSnippets(list);
                snipInput.setText("");
                renderSnippets();
            }
        });
        addRow.addView(addBtn, new LinearLayout.LayoutParams(px(74), px(38)));
        snipCard.addView(addRow);

        snippetContainer = new LinearLayout(this);
        snippetContainer.setOrientation(LinearLayout.VERTICAL);
        snipCard.addView(snippetContainer);
        root.addView(snipCard);
        renderSnippets();

        setContentView(scroll);
        updateImeStatus();
        updateMicButton();
    }

    private void renderFontCards() {
        fontGrid.removeAllViews();
        RedlineTheme t = prefs.getTheme();
        String activeId = prefs.getFontId();
        boolean bold = prefs.isBoldKeys();

        for (RedlineFonts.FontOption opt : RedlineFonts.BUILT_IN) {
            addFontCard(opt.id, opt.name, opt.subtitle, activeId.equals(opt.id),
                    RedlineFonts.load(this, opt.id, bold), t);
        }
        if (RedlineFonts.hasCustomFont(this)) {
            addFontCard(RedlineFonts.ID_CUSTOM, "CUSTOM: " + prefs.getCustomFontName(),
                    "Imported user .ttf / .otf font",
                    RedlineFonts.ID_CUSTOM.equals(activeId),
                    RedlineFonts.load(this, RedlineFonts.ID_CUSTOM, bold), t);
        }
    }

    private void addFontCard(String id, String name, String subtitle, boolean selected, Typeface tf, RedlineTheme t) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(px(12), px(10), px(12), px(10));
        row.setBackground(makeCardBg(t.keyBg, selected ? t.accentPrimary : t.keyBorder, 8));

        TextView sample = new TextView(this);
        sample.setText("Aa");
        sample.setTextColor(selected ? t.accentPrimary : t.accentSecondary);
        sample.setTextSize(20f);
        sample.setTypeface(tf);
        sample.setGravity(Gravity.CENTER);
        sample.setBackground(makeCardBg(t.deckBg, selected ? t.accentPrimary : t.keyBorder, 6));
        row.addView(sample, new LinearLayout.LayoutParams(px(46), px(38)));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(px(12), 0, px(8), 0);

        TextView titleTv = new TextView(this);
        titleTv.setText(name);
        titleTv.setTextColor(t.primaryText);
        titleTv.setTextSize(14f);
        titleTv.setTypeface(tf);
        texts.addView(titleTv);

        TextView subTv = new TextView(this);
        subTv.setText(subtitle);
        subTv.setTextColor(0xFF8A8EA6);
        subTv.setTextSize(11f);
        texts.addView(subTv);

        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView badge = new TextView(this);
        badge.setText(selected ? "ACTIVE" : "USE");
        badge.setTextColor(selected ? t.accentPrimary : t.hintText);
        badge.setTextSize(11f);
        badge.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        row.addView(badge);

        row.setOnClickListener(v -> {
            prefs.setFontId(id);
            buildUi();
        });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = px(7);
        fontGrid.addView(row, lp);
    }

    private void renderThemeCards() {
        themeGrid.removeAllViews();
        RedlineTheme active = prefs.getTheme();

        for (RedlineTheme item : RedlineTheme.ALL) {
            boolean selected = item.id.equals(active.id);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(px(12), px(10), px(12), px(10));
            row.setBackground(makeCardBg(item.keyBg, selected ? item.accentPrimary : item.keyBorder, 8));

            LinearLayout swatches = new LinearLayout(this);
            swatches.setOrientation(LinearLayout.HORIZONTAL);
            swatches.addView(makeSwatch(item.deckBg, item.keyBorder));
            swatches.addView(makeSwatch(item.accentPrimary, item.accentPrimary));
            swatches.addView(makeSwatch(item.accentSecondary, item.accentSecondary));
            row.addView(swatches);

            LinearLayout texts = new LinearLayout(this);
            texts.setOrientation(LinearLayout.VERTICAL);
            texts.setPadding(px(12), 0, px(8), 0);

            TextView name = new TextView(this);
            name.setText(item.name);
            name.setTextColor(item.primaryText);
            name.setTextSize(13.5f);
            name.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
            texts.addView(name);

            TextView desc = new TextView(this);
            desc.setText(item.subtitle);
            desc.setTextColor(item.hintText);
            desc.setTextSize(11f);
            texts.addView(desc);

            row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView tag = new TextView(this);
            tag.setText(selected ? "ACTIVE" : "SELECT");
            tag.setTextColor(selected ? item.accentPrimary : item.hintText);
            tag.setTextSize(11f);
            tag.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
            row.addView(tag);

            row.setOnClickListener(v -> {
                prefs.setThemeId(item.id);
                buildUi();
            });

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = px(7);
            themeGrid.addView(row, lp);
        }
    }

    private void renderSnippets() {
        snippetContainer.removeAllViews();
        RedlineTheme t = prefs.getTheme();
        List<String> list = prefs.getSnippets();
        for (int i = 0; i < list.size(); i++) {
            final int idx = i;
            String s = list.get(i);

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(px(10), px(7), px(8), px(7));
            row.setBackground(makeCardBg(t.deckBg, t.keyBorder, 6));

            TextView tv = new TextView(this);
            tv.setText(s.replace("\n", "↵"));
            tv.setTextColor(t.primaryText);
            tv.setTextSize(13f);
            tv.setTypeface(Typeface.MONOSPACE);
            row.addView(tv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView del = new TextView(this);
            del.setText("✕");
            del.setTextColor(t.accentPrimary);
            del.setTextSize(14f);
            del.setPadding(px(10), px(2), px(6), px(2));
            del.setOnClickListener(v -> {
                List<String> updated = prefs.getSnippets();
                if (idx >= 0 && idx < updated.size()) {
                    updated.remove(idx);
                    prefs.setSnippets(updated);
                    renderSnippets();
                }
            });
            row.addView(del);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = px(5);
            snippetContainer.addView(row, lp);
        }
    }

    private void updateMicButton() {
        if (micBtn == null) return;
        RedlineTheme t = prefs.getTheme();
        boolean granted = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
        if (granted) {
            micBtn.setText("● MICROPHONE READY — TAP 🎙 ON KEYBOARD");
            micBtn.setTextColor(0xFF00E676);
            micBtn.setBackground(makeCardBg(0xFF082214, 0xFF00E676, 7));
        } else {
            micBtn.setText("🎙 GRANT MICROPHONE ACCESS FOR AI VOICE");
            micBtn.setTextColor(t.accentSecondary);
            micBtn.setBackground(makeCardBg(t.specialKeyBg, t.accentSecondary, 7));
        }
    }

    private void updateImeStatus() {
        if (statusBadge == null) return;
        RedlineTheme t = prefs.getTheme();
        boolean enabled = false;
        boolean selected = false;
        try {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                for (InputMethodInfo info : imm.getEnabledInputMethodList()) {
                    if (getPackageName().equals(info.getPackageName())) {
                        enabled = true;
                        break;
                    }
                }
            }
            String def = Settings.Secure.getString(getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
            if (def != null && def.startsWith(getPackageName() + "/")) {
                selected = true;
            }
        } catch (Throwable ignored) {}

        if (selected) {
            statusBadge.setText("● ACTIVE");
            statusBadge.setTextColor(0xFF00E676);
            statusBadge.setBackground(makeCardBg(0xFF082214, 0xFF00E676, 6));
        } else if (enabled) {
            statusBadge.setText("◐ ENABLED");
            statusBadge.setTextColor(t.accentSecondary);
            statusBadge.setBackground(makeCardBg(0xFF261C07, t.accentSecondary, 6));
        } else {
            statusBadge.setText("○ SETUP");
            statusBadge.setTextColor(t.accentPrimary);
            statusBadge.setBackground(makeCardBg(0xFF280B10, t.accentPrimary, 6));
        }
    }

    private void addSectionHeader(LinearLayout parent, String text, RedlineTheme t) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(t.accentPrimary);
        tv.setTextSize(11.5f);
        tv.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        tv.setPadding(px(2), px(14), 0, px(7));
        parent.addView(tv);
    }

    private LinearLayout makeCardContainer(RedlineTheme t) {
        LinearLayout ll = new LinearLayout(this);
        ll.setOrientation(LinearLayout.VERTICAL);
        ll.setPadding(px(12), px(10), px(12), px(10));
        ll.setBackground(makeCardBg(t.keyBg, t.keyBorder, 8));
        return ll;
    }

    private interface IntConsumer { void accept(int v); }
    private interface BoolConsumer { void accept(boolean v); }

    private void addSliderRow(LinearLayout parent, String label, int current, int min, int max,
                              String unit, RedlineTheme t, IntConsumer onChange) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, px(4), 0, px(8));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextColor(t.primaryText);
        tv.setTextSize(13.5f);
        top.addView(tv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView valTv = new TextView(this);
        valTv.setText(current + unit);
        valTv.setTextColor(t.accentSecondary);
        valTv.setTextSize(13f);
        valTv.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        top.addView(valTv);
        row.addView(top);

        SeekBar bar = new SeekBar(this);
        bar.setMax(max - min);
        bar.setProgress(current - min);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int v = min + progress;
                valTv.setText(v + unit);
                if (fromUser) onChange.accept(v);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        row.addView(bar);
        parent.addView(row);
    }

    private void addToggleRow(LinearLayout parent, String title, String subtitle, boolean checked,
                              RedlineTheme t, BoolConsumer onChange) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, px(6), 0, px(6));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView tv = new TextView(this);
        tv.setText(title);
        tv.setTextColor(t.primaryText);
        tv.setTextSize(13.5f);
        texts.addView(tv);

        TextView sub = new TextView(this);
        sub.setText(subtitle);
        sub.setTextColor(0xFF84889E);
        sub.setTextSize(11.5f);
        texts.addView(sub);

        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Switch sw = new Switch(this);
        sw.setChecked(checked);
        sw.setOnCheckedChangeListener((btn, isChecked) -> onChange.accept(isChecked));
        row.addView(sw);

        row.setOnClickListener(v -> sw.toggle());
        parent.addView(row);
    }

    private Button makeActionButton(String label, int bg, int fg, int stroke) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextColor(fg);
        b.setTextSize(12f);
        b.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        b.setBackground(makeCardBg(bg, stroke, 7));
        b.setAllCaps(false);
        return b;
    }

    private View makeSwatch(int fill, int stroke) {
        View v = new View(this);
        v.setBackground(makeCardBg(fill, stroke, 4));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(px(16), px(24));
        lp.rightMargin = px(3);
        v.setLayoutParams(lp);
        return v;
    }

    private GradientDrawable makeCardBg(int fill, int stroke, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setStroke(px(1), stroke);
        d.setCornerRadius(px(radiusDp));
        return d;
    }

    private int px(int v) {
        return Math.round(v * dp);
    }
}
