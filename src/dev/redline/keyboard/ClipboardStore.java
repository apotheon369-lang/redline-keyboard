package dev.redline.keyboard;

import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Build;
import android.os.PersistableBundle;
import java.util.ArrayList;
import java.util.List;

/**
 * Local-only clipboard history tracker (zero network, skips sensitive/password clips).
 */
public final class ClipboardStore {
    private static final int MAX_RECENT = 20;

    private final Context ctx;
    private final RedlinePrefs prefs;
    private final ClipboardManager cm;
    private final List<String> recent = new ArrayList<>();
    private final List<String> pinned = new ArrayList<>();
    private boolean registered = false;

    private final ClipboardManager.OnPrimaryClipChangedListener listener = this::captureCurrent;

    public ClipboardStore(Context ctx, RedlinePrefs prefs) {
        this.ctx = ctx.getApplicationContext();
        this.prefs = prefs;
        this.cm = (ClipboardManager) this.ctx.getSystemService(Context.CLIPBOARD_SERVICE);
        this.pinned.addAll(prefs.getPinnedClips());
    }

    public void start() {
        if (!registered && cm != null) {
            try {
                cm.addPrimaryClipChangedListener(listener);
                registered = true;
                captureCurrent();
            } catch (Throwable ignored) {}
        }
    }

    public void stop() {
        if (registered && cm != null) {
            try {
                cm.removePrimaryClipChangedListener(listener);
            } catch (Throwable ignored) {}
            registered = false;
        }
    }

    public void captureCurrent() {
        if (cm == null) return;
        try {
            if (!cm.hasPrimaryClip()) return;
            ClipData clip = cm.getPrimaryClip();
            if (clip == null || clip.getItemCount() == 0) return;

            // Respect Android 13+ EXTRA_IS_SENSITIVE (do not store passwords/OTPs!)
            ClipDescription desc = clip.getDescription();
            if (desc != null && Build.VERSION.SDK_INT >= 33) {
                PersistableBundle extras = desc.getExtras();
                if (extras != null && extras.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE, false)) {
                    return;
                }
            }

            CharSequence cs = clip.getItemAt(0).coerceToText(ctx);
            if (cs == null) return;
            String text = cs.toString();
            if (text.trim().isEmpty() || text.length() > 4000) return;

            recent.remove(text);
            recent.add(0, text);
            while (recent.size() > MAX_RECENT) {
                recent.remove(recent.size() - 1);
            }
        } catch (Throwable ignored) {}
    }

    public String getLatestText() {
        captureCurrent();
        if (!recent.isEmpty()) return recent.get(0);
        if (!pinned.isEmpty()) return pinned.get(0);
        return null;
    }

    public List<String> getAllItems() {
        captureCurrent();
        List<String> out = new ArrayList<>();
        out.addAll(pinned);
        for (String r : recent) {
            if (!pinned.contains(r)) out.add(r);
        }
        return out;
    }

    public boolean isPinned(String item) {
        return pinned.contains(item);
    }

    public void togglePin(String item) {
        if (pinned.contains(item)) {
            pinned.remove(item);
        } else {
            pinned.add(0, item);
        }
        prefs.setPinnedClips(pinned);
    }

    public void clearRecent() {
        recent.clear();
    }
}
