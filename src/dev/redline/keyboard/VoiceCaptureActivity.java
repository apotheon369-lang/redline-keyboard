package dev.redline.keyboard;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;

/**
 * Translucent foreground AI Voice Capture overlay.
 * Guarantees instant, unrestricted Android 13+ microphone access even on OEM ROMs
 * that restrict background IME services from opening the mic.
 * Falls back cleanly to Android's system ACTION_RECOGNIZE_SPEECH popup if needed.
 */
public final class VoiceCaptureActivity extends Activity {

    private static final int REQ_MIC = 801;
    private static final int REQ_SYSTEM_SPEECH = 802;

    private RedlinePrefs prefs;
    private SpeechRecognizer recognizer;
    private TextView statusTv;
    private TextView liveTextTv;
    private View pulseDot;
    private String lastPartial = "";
    private boolean launchedSystemFallback = false;
    private float dp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new RedlinePrefs(this);
        dp = getResources().getDisplayMetrics().density;
        buildOverlay();

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
        } else {
            beginListening();
        }
    }

    private void buildOverlay() {
        RedlineTheme t = prefs.getTheme();
        Typeface tf = RedlineFonts.load(this, prefs.getFontId(), true);

        FrameLayout backdrop = new FrameLayout(this);
        backdrop.setBackgroundColor(0xAA000000);
        backdrop.setOnClickListener(v -> finishWithCurrent());

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(px(22), px(20), px(22), px(18));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(t.keyBg);
        bg.setStroke(px(2), t.accentPrimary);
        bg.setCornerRadius(px(14));
        card.setBackground(bg);
        card.setOnClickListener(v -> {}); // consume clicks inside card

        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);

        pulseDot = new View(this);
        GradientDrawable dotBg = new GradientDrawable();
        dotBg.setShape(GradientDrawable.OVAL);
        dotBg.setColor(t.accentPrimary);
        pulseDot.setBackground(dotBg);
        LinearLayout.LayoutParams dpLp = new LinearLayout.LayoutParams(px(12), px(12));
        dpLp.rightMargin = px(10);
        topRow.addView(pulseDot, dpLp);

        statusTv = new TextView(this);
        statusTv.setText("REDLINE AI VOICE // SPEAK NOW");
        statusTv.setTextColor(t.accentSecondary);
        statusTv.setTextSize(13f);
        statusTv.setTypeface(tf);
        topRow.addView(statusTv);
        card.addView(topRow);

        liveTextTv = new TextView(this);
        liveTextTv.setText("Listening...");
        liveTextTv.setTextColor(t.primaryText);
        liveTextTv.setTextSize(18f);
        liveTextTv.setTypeface(tf);
        liveTextTv.setGravity(Gravity.CENTER);
        liveTextTv.setMinLines(2);
        liveTextTv.setPadding(0, px(16), 0, px(18));
        card.addView(liveTextTv);

        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);

        Button cancelBtn = makeBtn("CANCEL", t.specialKeyBg, t.hintText, t.keyBorder);
        cancelBtn.setOnClickListener(v -> {
            lastPartial = "";
            destroyRec();
            finish();
        });
        LinearLayout.LayoutParams b1 = new LinearLayout.LayoutParams(0, px(42), 1f);
        b1.rightMargin = px(8);
        btnRow.addView(cancelBtn, b1);

        Button doneBtn = makeBtn("INSERT", t.actionKeyBg, t.actionKeyText, t.accentPrimary);
        doneBtn.setOnClickListener(v -> finishWithCurrent());
        btnRow.addView(doneBtn, new LinearLayout.LayoutParams(0, px(42), 1f));

        card.addView(btnRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        clp.setMargins(px(16), px(16), px(16), px(28));
        backdrop.addView(card, clp);

        setContentView(backdrop);
    }

    private void beginListening() {
        destroyRec();
        recognizer = VoiceController.createBestRecognizer(this, prefs.isVoiceOfflineOnly());
        if (recognizer == null) {
            launchSystemIntentFallback();
            return;
        }
        recognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) { statusTv.setText("MIC LIVE — SPEAK NOW"); }
            @Override public void onBeginningOfSpeech() { statusTv.setText("PICKING UP VOICE..."); }
            @Override public void onRmsChanged(float rmsdB) {
                float scale = 1.0f + Math.max(0f, Math.min(1.2f, (rmsdB + 2f) / 8f));
                pulseDot.setScaleX(scale);
                pulseDot.setScaleY(scale);
            }
            @Override public void onBufferReceived(byte[] buffer) {}
            @Override public void onEndOfSpeech() { statusTv.setText("DECODING..."); }

            @Override
            public void onError(int error) {
                if (!lastPartial.isEmpty()) {
                    deliverAndClose(lastPartial);
                    return;
                }
                if (!launchedSystemFallback && (error == SpeechRecognizer.ERROR_AUDIO
                        || error == SpeechRecognizer.ERROR_CLIENT
                        || error == SpeechRecognizer.ERROR_SERVER
                        || error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS)) {
                    launchSystemIntentFallback();
                    return;
                }
                finish();
            }

            @Override
            public void onResults(Bundle results) {
                ArrayList<String> list = results != null
                        ? results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) : null;
                if (list != null && !list.isEmpty() && !list.get(0).trim().isEmpty()) {
                    deliverAndClose(list.get(0));
                } else if (!lastPartial.isEmpty()) {
                    deliverAndClose(lastPartial);
                } else {
                    finish();
                }
            }

            @Override
            public void onPartialResults(Bundle partialResults) {
                ArrayList<String> list = partialResults != null
                        ? partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) : null;
                if (list != null && !list.isEmpty() && !list.get(0).trim().isEmpty()) {
                    lastPartial = list.get(0);
                    liveTextTv.setText(lastPartial);
                }
            }

            @Override public void onEvent(int eventType, Bundle params) {}
        });

        try {
            recognizer.startListening(VoiceController.buildRecognizerIntent(this, prefs.isVoiceOfflineOnly()));
        } catch (Throwable t) {
            launchSystemIntentFallback();
        }
    }

    private void launchSystemIntentFallback() {
        if (launchedSystemFallback) {
            finish();
            return;
        }
        launchedSystemFallback = true;
        destroyRec();
        try {
            Intent i = VoiceController.buildRecognizerIntent(this, prefs.isVoiceOfflineOnly());
            i.putExtra(RecognizerIntent.EXTRA_PROMPT, "Redline AI Voice — Speak now");
            startActivityForResult(i, REQ_SYSTEM_SPEECH);
        } catch (Throwable t) {
            finish();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_SYSTEM_SPEECH && resultCode == RESULT_OK && data != null) {
            ArrayList<String> list = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (list != null && !list.isEmpty() && !list.get(0).trim().isEmpty()) {
                deliverAndClose(list.get(0));
                return;
            }
        }
        finish();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_MIC && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            beginListening();
        } else {
            finish();
        }
    }

    private void finishWithCurrent() {
        destroyRec();
        if (!lastPartial.isEmpty()) {
            deliverAndClose(lastPartial);
        } else {
            finish();
        }
    }

    private void deliverAndClose(String rawText) {
        destroyRec();
        String formatted = VoiceController.formatSpokenText(rawText, prefs.isVoiceSmartPunct());
        RedlineImeService.deliverExternalVoiceText(formatted);
        finish();
        overridePendingTransition(0, 0);
    }

    private void destroyRec() {
        if (recognizer != null) {
            try {
                recognizer.cancel();
                recognizer.destroy();
            } catch (Throwable ignored) {}
            recognizer = null;
        }
    }

    @Override
    protected void onDestroy() {
        destroyRec();
        super.onDestroy();
    }

    private Button makeBtn(String label, int fill, int text, int stroke) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextColor(text);
        b.setTextSize(12.5f);
        b.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setStroke(px(1), stroke);
        d.setCornerRadius(px(7));
        b.setBackground(d);
        b.setAllCaps(false);
        return b;
    }

    private int px(int v) {
        return Math.round(v * dp);
    }
}
