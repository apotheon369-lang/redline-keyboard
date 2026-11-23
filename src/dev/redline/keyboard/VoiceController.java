package dev.redline.keyboard;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognitionService;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Redline v2.2 Instant AI Voice Dictation Engine.
 *
 * Fixes Android 11-14 IME voice pickup issues via:
 * 1. Proper Service Context + EXTRA_CALLING_PACKAGE (required by Google/OEM Speech Services)
 * 2. Smart recognizer selection (On-Device API 31+ -> Default -> Explicit RecognitionService ComponentName)
 * 3. Instant automatic fallback to foreground VoiceCaptureActivity if Android 13 blocks background IME mic access
 * 4. Real-time streaming partial transcripts & RMS waveform levels + smart AI spoken punctuation
 */
public final class VoiceController {

    public interface Callback {
        void onVoiceStateChanged(boolean listening, String statusText);
        void onVoiceRms(float normalizedLevel); // 0..1
        void onVoicePartial(String partialText);
        void onVoiceFinal(String formattedText);
        void onVoicePermissionNeeded();
        void onVoiceNeedForegroundFallback();
        void onVoiceError(String message);
    }

    private final Context ctx;
    private final RedlinePrefs prefs;
    private final Callback callback;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private SpeechRecognizer recognizer;
    private boolean listening = false;
    private boolean gotAudioOrSpeech = false;
    private String lastPartial = "";
    private long startMs = 0L;

    public VoiceController(Context serviceCtx, RedlinePrefs prefs, Callback callback) {
        // Keep the actual Service/Activity context (NOT getApplicationContext) so Android 13 mic attribution works!
        this.ctx = serviceCtx;
        this.prefs = prefs;
        this.callback = callback;
    }

    public boolean isListening() {
        return listening;
    }

    public boolean hasMicPermission() {
        return ctx.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    public void toggle() {
        mainHandler.post(() -> {
            if (listening) {
                stop(true);
            } else {
                start();
            }
        });
    }

    public void start() {
        mainHandler.post(() -> {
            if (!hasMicPermission()) {
                callback.onVoicePermissionNeeded();
                return;
            }
            if (prefs.isVoiceForegroundMode()) {
                callback.onVoiceNeedForegroundFallback();
                return;
            }
            destroyRecognizer();
            try {
                recognizer = createBestRecognizer(ctx, prefs.isVoiceOfflineOnly());
                if (recognizer == null) {
                    callback.onVoiceNeedForegroundFallback();
                    return;
                }
                recognizer.setRecognitionListener(new ListenerImpl());

                Intent intent = buildRecognizerIntent(ctx, prefs.isVoiceOfflineOnly());
                lastPartial = "";
                gotAudioOrSpeech = false;
                startMs = System.currentTimeMillis();
                listening = true;
                callback.onVoiceStateChanged(true, "MIC LIVE — SPEAK");
                recognizer.startListening(intent);
            } catch (Throwable t) {
                listening = false;
                callback.onVoiceStateChanged(false, null);
                callback.onVoiceNeedForegroundFallback();
            }
        });
    }

    public static SpeechRecognizer createBestRecognizer(Context ctx, boolean preferOffline) {
        try {
            if (preferOffline && Build.VERSION.SDK_INT >= 31
                    && SpeechRecognizer.isOnDeviceRecognitionAvailable(ctx)) {
                return SpeechRecognizer.createOnDeviceSpeechRecognizer(ctx);
            }
        } catch (Throwable ignored) {}

        try {
            if (SpeechRecognizer.isRecognitionAvailable(ctx)) {
                return SpeechRecognizer.createSpeechRecognizer(ctx);
            }
        } catch (Throwable ignored) {}

        // Explicit RecognitionService discovery fallback (handles OEMs where default setting is blank)
        try {
            PackageManager pm = ctx.getPackageManager();
            List<ResolveInfo> services = pm.queryIntentServices(
                    new Intent(RecognitionService.SERVICE_INTERFACE), 0);
            if (services != null && !services.isEmpty()) {
                ResolveInfo pick = services.get(0);
                for (ResolveInfo ri : services) {
                    String pkg = ri.serviceInfo != null ? ri.serviceInfo.packageName : "";
                    if (pkg.contains("google") || pkg.contains("speech")) {
                        pick = ri;
                        break;
                    }
                }
                if (pick.serviceInfo != null) {
                    ComponentName cn = new ComponentName(pick.serviceInfo.packageName, pick.serviceInfo.name);
                    return SpeechRecognizer.createSpeechRecognizer(ctx, cn);
                }
            }
        } catch (Throwable ignored) {}

        try {
            if (Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(ctx)) {
                return SpeechRecognizer.createOnDeviceSpeechRecognizer(ctx);
            }
        } catch (Throwable ignored) {}

        return null;
    }

    public static Intent buildRecognizerIntent(Context ctx, boolean preferOffline) {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, ctx.getPackageName());
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag());
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1200L);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 900L);
        if (preferOffline) {
            intent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);
        }
        return intent;
    }

    public void stop(boolean commitPartialIfNeeded) {
        mainHandler.post(() -> {
            if (!listening) return;
            listening = false;
            if (recognizer != null) {
                try {
                    recognizer.stopListening();
                } catch (Throwable ignored) {}
            }
            if (commitPartialIfNeeded && !lastPartial.isEmpty()) {
                String out = formatSpokenText(lastPartial, prefs.isVoiceSmartPunct());
                lastPartial = "";
                callback.onVoiceFinal(out);
            }
            callback.onVoiceStateChanged(false, null);
        });
    }

    public void release() {
        mainHandler.post(() -> {
            listening = false;
            destroyRecognizer();
        });
    }

    private void destroyRecognizer() {
        if (recognizer != null) {
            try {
                recognizer.cancel();
                recognizer.destroy();
            } catch (Throwable ignored) {}
            recognizer = null;
        }
    }

    public static String formatSpokenText(String raw, boolean smartPunct) {
        if (raw == null) return "";
        String s = raw.trim();
        if (s.isEmpty()) return "";
        if (!smartPunct) return s;

        s = s.replaceAll("(?i)\\b(new line|newline)\\b", "\n");
        s = s.replaceAll("(?i)\\b(question mark)\\b", "?");
        s = s.replaceAll("(?i)\\b(exclamation mark|exclamation point)\\b", "!");
        s = s.replaceAll("(?i)\\b(semicolon|semi colon)\\b", ";");
        s = s.replaceAll("(?i)\\b(colon)\\b", ":");
        s = s.replaceAll("(?i)\\b(comma)\\b", ",");
        s = s.replaceAll("(?i)\\b(period|full stop)\\b", ".");
        s = s.replaceAll("(?i)\\b(at sign)\\b", "@");
        s = s.replaceAll("(?i)\\b(hashtag|hash tag)\\b", "#");
        s = s.replaceAll("(?i)\\b(slash|forward slash)\\b", "/");
        s = s.replaceAll("(?i)\\b(dash|hyphen)\\b", "-");

        s = s.replaceAll("\\s+([.,!?;:])", "$1");
        s = s.replaceAll("([.,!?;:])([A-Za-z])", "$1 $2");
        s = s.replaceAll("\\s*\\n\\s*", "\n");

        s = s.replaceAll("\\bi\\b", "I");
        s = s.replaceAll("\\bi('m|'ve|'ll|'d)\\b", "I$1");

        StringBuilder out = new StringBuilder(s.length());
        boolean capNext = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (capNext && Character.isLetter(c)) {
                out.append(Character.toUpperCase(c));
                capNext = false;
            } else {
                out.append(c);
                if (c == '.' || c == '!' || c == '?' || c == '\n') {
                    capNext = true;
                }
            }
        }
        return out.toString();
    }

    private final class ListenerImpl implements RecognitionListener {
        @Override
        public void onReadyForSpeech(Bundle params) {
            callback.onVoiceStateChanged(true, "SPEAK NOW");
        }

        @Override
        public void onBeginningOfSpeech() {
            gotAudioOrSpeech = true;
            callback.onVoiceStateChanged(true, "PICKING UP...");
        }

        @Override
        public void onRmsChanged(float rmsdB) {
            if (rmsdB > 1.5f) gotAudioOrSpeech = true;
            float norm = Math.max(0f, Math.min(1f, (rmsdB + 2f) / 11f));
            callback.onVoiceRms(norm);
        }

        @Override
        public void onBufferReceived(byte[] buffer) {}

        @Override
        public void onEndOfSpeech() {
            callback.onVoiceStateChanged(true, "DECODING...");
        }

        @Override
        public void onError(int error) {
            long elapsed = System.currentTimeMillis() - startMs;
            listening = false;
            destroyRecognizer();
            callback.onVoiceStateChanged(false, null);

            if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                if (!hasMicPermission()) {
                    callback.onVoicePermissionNeeded();
                } else {
                    // Android 13 blocked background IME service mic access -> instantly open foreground capture!
                    callback.onVoiceNeedForegroundFallback();
                }
                return;
            }

            // If failed immediately (< 1200ms) with audio/client/server/network/language error,
            // auto-fallback to foreground VoiceCaptureActivity so voice ALWAYS works!
            if (error == SpeechRecognizer.ERROR_AUDIO
                    || error == SpeechRecognizer.ERROR_CLIENT
                    || error == SpeechRecognizer.ERROR_SERVER
                    || error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY
                    || (!gotAudioOrSpeech && elapsed < 1200L)) {
                callback.onVoiceNeedForegroundFallback();
                return;
            }

            if (!lastPartial.isEmpty()) {
                String out = formatSpokenText(lastPartial, prefs.isVoiceSmartPunct());
                lastPartial = "";
                callback.onVoiceFinal(out);
                return;
            }

            String msg;
            switch (error) {
                case SpeechRecognizer.ERROR_NETWORK:
                case SpeechRecognizer.ERROR_NETWORK_TIMEOUT: msg = "VOICE NETWORK ERROR"; break;
                case SpeechRecognizer.ERROR_NO_MATCH: msg = "DIDN'T CATCH THAT — TRY AGAIN"; break;
                case SpeechRecognizer.ERROR_SPEECH_TIMEOUT: msg = "NO VOICE HEARD"; break;
                default: msg = "VOICE ERR " + error; break;
            }
            callback.onVoiceError(msg);
        }

        @Override
        public void onResults(Bundle results) {
            if (!listening && lastPartial.isEmpty()) return; // guard against duplicate OEM callbacks
            listening = false;
            destroyRecognizer();
            ArrayList<String> matches = results != null
                    ? results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) : null;
            if (matches != null && !matches.isEmpty() && !matches.get(0).trim().isEmpty()) {
                lastPartial = "";
                callback.onVoiceFinal(formatSpokenText(matches.get(0), prefs.isVoiceSmartPunct()));
            } else if (!lastPartial.isEmpty()) {
                String out = formatSpokenText(lastPartial, prefs.isVoiceSmartPunct());
                lastPartial = "";
                callback.onVoiceFinal(out);
            }
            callback.onVoiceStateChanged(false, null);
        }

        @Override
        public void onPartialResults(Bundle partialResults) {
            ArrayList<String> matches = partialResults != null
                    ? partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) : null;
            if (matches != null && !matches.isEmpty()) {
                String p = matches.get(0);
                if (p != null && !p.trim().isEmpty()) {
                    gotAudioOrSpeech = true;
                    lastPartial = p;
                    callback.onVoicePartial(p);
                }
            }
        }

        @Override
        public void onEvent(int eventType, Bundle params) {}
    }
}
