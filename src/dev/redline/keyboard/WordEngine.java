package dev.redline.keyboard;

import android.content.Context;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Redline v2.1 Offline AI Autocorrect + Next-Word Assumption (Bigram) + Prefix Completion Engine.
 *
 * - 18,000+ frequency-ranked dictionary
 * - Keyboard-proximity weighted Damerau-Levenshtein typo autocorrect
 * - Built-in contraction & common-typo fast fixes
 * - Conversational + adaptive user-learned next-word prediction ("word assumption")
 */
public final class WordEngine {
    private static final int MAX_SUGGESTIONS = 3;

    public interface LoadListener {
        void onDictionaryLoaded(List<String> words);
    }

    // Standard QWERTY 2D key coordinates for proximity-weighted typo scoring
    private static final float[] QWERTY_X = new float[26];
    private static final float[] QWERTY_Y = new float[26];
    static {
        String[] rows = {"qwertyuiop", "asdfghjkl", "zxcvbnm"};
        float[] off = {0f, 0.45f, 1.45f};
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < rows[r].length(); c++) {
                int idx = rows[r].charAt(c) - 'a';
                QWERTY_X[idx] = off[r] + c;
                QWERTY_Y[idx] = r;
            }
        }
    }

    // Fast-path contractions & notorious typos
    private static final Map<String, String> QUICK_FIX = new HashMap<>();
    static {
        QUICK_FIX.put("i", "I");
        QUICK_FIX.put("im", "I'm");
        QUICK_FIX.put("ive", "I've");
        QUICK_FIX.put("ill", "I'll");
        QUICK_FIX.put("id", "I'd");
        QUICK_FIX.put("dont", "don't");
        QUICK_FIX.put("cant", "can't");
        QUICK_FIX.put("wont", "won't");
        QUICK_FIX.put("didnt", "didn't");
        QUICK_FIX.put("doesnt", "doesn't");
        QUICK_FIX.put("isnt", "isn't");
        QUICK_FIX.put("arent", "aren't");
        QUICK_FIX.put("wasnt", "wasn't");
        QUICK_FIX.put("werent", "weren't");
        QUICK_FIX.put("havent", "haven't");
        QUICK_FIX.put("hasnt", "hasn't");
        QUICK_FIX.put("hadnt", "hadn't");
        QUICK_FIX.put("wouldnt", "wouldn't");
        QUICK_FIX.put("couldnt", "couldn't");
        QUICK_FIX.put("shouldnt", "shouldn't");
        QUICK_FIX.put("youre", "you're");
        QUICK_FIX.put("youve", "you've");
        QUICK_FIX.put("youll", "you'll");
        QUICK_FIX.put("theyre", "they're");
        QUICK_FIX.put("theyve", "they've");
        QUICK_FIX.put("theyll", "they'll");
        QUICK_FIX.put("weve", "we've");
        QUICK_FIX.put("thats", "that's");
        QUICK_FIX.put("whats", "what's");
        QUICK_FIX.put("whos", "who's");
        QUICK_FIX.put("heres", "here's");
        QUICK_FIX.put("theres", "there's");
        QUICK_FIX.put("lets", "let's");
        QUICK_FIX.put("aint", "ain't");
        QUICK_FIX.put("teh", "the");
        QUICK_FIX.put("tge", "the");
        QUICK_FIX.put("hte", "the");
        QUICK_FIX.put("adn", "and");
        QUICK_FIX.put("nad", "and");
        QUICK_FIX.put("taht", "that");
        QUICK_FIX.put("wiht", "with");
        QUICK_FIX.put("becuase", "because");
        QUICK_FIX.put("bcuz", "because");
        QUICK_FIX.put("recieve", "receive");
        QUICK_FIX.put("definately", "definitely");
        QUICK_FIX.put("definitly", "definitely");
        QUICK_FIX.put("seperate", "separate");
        QUICK_FIX.put("untill", "until");
        QUICK_FIX.put("tommorow", "tomorrow");
        QUICK_FIX.put("tomorow", "tomorrow");
        QUICK_FIX.put("conplexity", "complexity");
        QUICK_FIX.put("keybaord", "keyboard");
        QUICK_FIX.put("keybord", "keyboard");
        QUICK_FIX.put("acurate", "accurate");
        QUICK_FIX.put("awesoem", "awesome");
        QUICK_FIX.put("woudl", "would");
        QUICK_FIX.put("coudl", "could");
        QUICK_FIX.put("shoudl", "should");
        QUICK_FIX.put("realy", "really");
        QUICK_FIX.put("prolly", "probably");
        QUICK_FIX.put("probly", "probably");
        QUICK_FIX.put("thier", "their");
        QUICK_FIX.put("freind", "friend");
        QUICK_FIX.put("beleive", "believe");
        QUICK_FIX.put("wierd", "weird");
        QUICK_FIX.put("alright", "alright");
        QUICK_FIX.put("alot", "a lot");
    }

    // Conversational & tech next-word prediction ("word assumption") bigrams
    private static final Map<String, String[]> STATIC_BIGRAMS = new HashMap<>();
    static {
        putBg("i", "am", "have", "want");
        putBg("i'm", "not", "going", "just");
        putBg("i've", "been", "got", "never");
        putBg("i'll", "be", "send", "do");
        putBg("i'd", "like", "love", "rather");
        putBg("you", "are", "can", "want");
        putBg("you're", "welcome", "right", "not");
        putBg("we", "are", "can", "need");
        putBg("they", "are", "were", "have");
        putBg("it", "is", "was", "looks");
        putBg("it's", "a", "not", "so");
        putBg("that", "is", "was", "would");
        putBg("that's", "so", "why", "awesome");
        putBg("this", "is", "one", "looks");
        putBg("what", "is", "are", "do");
        putBg("what's", "up", "going", "the");
        putBg("how", "are", "do", "to");
        putBg("where", "are", "is", "do");
        putBg("when", "are", "do", "you");
        putBg("why", "are", "do", "not");
        putBg("who", "is", "are", "was");
        putBg("are", "you", "we", "they");
        putBg("is", "it", "that", "this");
        putBg("was", "it", "just", "that");
        putBg("do", "you", "not", "it");
        putBg("does", "it", "not", "that");
        putBg("did", "you", "not", "it");
        putBg("don't", "know", "think", "worry");
        putBg("can", "you", "we", "i");
        putBg("can't", "wait", "believe", "even");
        putBg("could", "you", "be", "have");
        putBg("would", "you", "be", "like");
        putBg("should", "be", "we", "i");
        putBg("will", "be", "you", "do");
        putBg("won't", "be", "work", "let");
        putBg("want", "to", "a", "some");
        putBg("need", "to", "a", "more");
        putBg("going", "to", "on", "home");
        putBg("gonna", "be", "do", "go");
        putBg("wanna", "go", "do", "see");
        putBg("have", "to", "a", "you");
        putBg("has", "been", "to", "a");
        putBg("had", "to", "a", "been");
        putBg("let", "me", "us", "him");
        putBg("let's", "go", "do", "make");
        putBg("make", "it", "a", "sure");
        putBg("send", "me", "it", "the");
        putBg("give", "me", "it", "you");
        putBg("show", "me", "you", "the");
        putBg("tell", "me", "you", "him");
        putBg("see", "you", "if", "what");
        putBg("thank", "you", "god", "so");
        putBg("thanks", "for", "so", "a");
        putBg("please", "send", "let", "check");
        putBg("sorry", "about", "for", "i");
        putBg("good", "morning", "night", "idea");
        putBg("hey", "man", "bro", "there");
        putBg("in", "the", "a", "my");
        putBg("on", "the", "my", "your");
        putBg("at", "the", "all", "least");
        putBg("to", "be", "the", "do");
        putBg("for", "the", "you", "a");
        putBg("with", "the", "you", "me");
        putBg("from", "the", "my", "here");
        putBg("about", "the", "it", "that");
        putBg("of", "the", "course", "my");
        putBg("and", "the", "i", "then");
        putBg("but", "i", "it", "the");
        putBg("if", "you", "it", "we");
        putBg("so", "i", "we", "good");
        putBg("just", "a", "wanted", "like");
        putBg("right", "now", "here", "there");
        putBg("a", "lot", "new", "good");
        putBg("the", "new", "best", "same");
        putBg("my", "phone", "keyboard", "voice");
        putBg("your", "phone", "voice", "own");
        putBg("new", "keyboard", "apk", "update");
        putBg("looks", "incredible", "awesome", "great");
        putBg("sounds", "good", "great", "like");
        putBg("all", "good", "the", "right");
        putBg("no", "problem", "worries", "way");
        putBg("not", "sure", "yet", "really");
        putBg("pretty", "good", "cool", "much");
        putBg("too", "much", "bad", "fast");
        putBg("very", "fast", "good", "clean");
        putBg("more", "neon", "than", "accurate");
    }

    private static final String[] SENTENCE_STARTERS = new String[]{"I", "The", "Hey"};

    private static void putBg(String prev, String... nexts) {
        STATIC_BIGRAMS.put(prev, nexts);
    }

    private final List<String> dictionary = new ArrayList<>(18500);
    private final Set<String> dictSet = new HashSet<>(24000);
    private final Map<String, Integer> dictRank = new HashMap<>(24000);
    private final Map<String, Integer> userBoost = new HashMap<>();
    private final Map<String, Map<String, Integer>> userBigrams = new HashMap<>();
    private boolean loaded = false;

    public void loadAsync(final Context ctx, final LoadListener onLoaded) {
        if (loaded) {
            if (onLoaded != null) {
                synchronized (dictionary) {
                    if (!dictionary.isEmpty()) onLoaded.onDictionaryLoaded(new ArrayList<>(dictionary));
                }
            }
            return;
        }
        loaded = true;
        new Thread(() -> {
            try {
                int resId = ctx.getResources().getIdentifier("words", "raw", ctx.getPackageName());
                if (resId == 0) return;
                try (InputStream is = ctx.getResources().openRawResource(resId);
                     BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
                    List<String> temp = new ArrayList<>(18500);
                    Set<String> set = new HashSet<>(24000);
                    Map<String, Integer> ranks = new HashMap<>(24000);
                    String line;
                    int r = 0;
                    while ((line = br.readLine()) != null) {
                        String w = line.trim();
                        if (!w.isEmpty() && set.add(w)) {
                            temp.add(w);
                            ranks.put(w, r++);
                        }
                    }
                    synchronized (dictionary) {
                        dictionary.clear();
                        dictionary.addAll(temp);
                        dictSet.clear();
                        dictSet.addAll(set);
                        dictRank.clear();
                        dictRank.putAll(ranks);
                    }
                    if (onLoaded != null) {
                        onLoaded.onDictionaryLoaded(temp);
                    }
                }
            } catch (Throwable ignored) {}
        }, "redline-dict-load").start();
    }

    public void learnWord(String word) {
        if (word == null) return;
        String clean = word.trim();
        if (clean.length() < 2 || clean.length() > 32) return;
        for (int i = 0; i < clean.length(); i++) {
            char c = clean.charAt(i);
            if (!Character.isLetter(c) && c != '\'' && c != '-') return;
        }
        String low = clean.toLowerCase(Locale.US);
        synchronized (userBoost) {
            Integer cur = userBoost.get(low);
            userBoost.put(low, cur == null ? 1 : Math.min(cur + 1, 50));
        }
    }

    public void learnBigram(String prevWord, String nextWord) {
        if (prevWord == null || nextWord == null) return;
        String p = prevWord.trim().toLowerCase(Locale.US);
        String n = nextWord.trim().toLowerCase(Locale.US);
        if (p.isEmpty() || n.length() < 2 || p.length() > 24 || n.length() > 24) return;
        synchronized (userBigrams) {
            Map<String, Integer> inner = userBigrams.get(p);
            if (inner == null) {
                inner = new HashMap<>();
                userBigrams.put(p, inner);
            }
            Integer cur = inner.get(n);
            inner.put(n, cur == null ? 1 : Math.min(cur + 1, 30));
        }
    }

    public Map<String, Integer> getUserBoostSnapshot() {
        synchronized (userBoost) {
            return new HashMap<>(userBoost);
        }
    }

    /**
     * Next-Word Prediction ("Word Assumption") based on previous word + adaptive user bigrams.
     */
    public List<String> predictNextWords(String prevWord, boolean capitalize) {
        List<String> out = new ArrayList<>(MAX_SUGGESTIONS);
        if (prevWord == null || prevWord.trim().isEmpty()) {
            for (String s : SENTENCE_STARTERS) out.add(s);
            return out;
        }
        String low = prevWord.trim().toLowerCase(Locale.US);

        // 1. User-learned personal bigrams first!
        synchronized (userBigrams) {
            Map<String, Integer> learned = userBigrams.get(low);
            if (learned != null && !learned.isEmpty()) {
                List<Map.Entry<String, Integer>> sorted = new ArrayList<>(learned.entrySet());
                Collections.sort(sorted, (a, b) -> Integer.compare(b.getValue(), a.getValue()));
                for (Map.Entry<String, Integer> e : sorted) {
                    String cand = formatNext(e.getKey(), capitalize);
                    if (!out.contains(cand)) {
                        out.add(cand);
                        if (out.size() >= MAX_SUGGESTIONS) return out;
                    }
                }
            }
        }

        // 2. Built-in conversational & tech bigrams
        String[] builtIn = STATIC_BIGRAMS.get(low);
        if (builtIn != null) {
            for (String w : builtIn) {
                String cand = formatNext(w, capitalize);
                if (!out.contains(cand)) {
                    out.add(cand);
                    if (out.size() >= MAX_SUGGESTIONS) return out;
                }
            }
        }

        // 3. Fallback common continuations
        String[] fallback = capitalize
                ? new String[]{"I", "The", "And"}
                : new String[]{"the", "to", "and"};
        for (String w : fallback) {
            if (!out.contains(w)) {
                out.add(w);
                if (out.size() >= MAX_SUGGESTIONS) break;
            }
        }
        return out;
    }

    private static String formatNext(String w, boolean capitalize) {
        if ("i".equals(w)) return "I";
        if (w.startsWith("i'")) return "I" + w.substring(1);
        if (capitalize && !w.isEmpty()) {
            return Character.toUpperCase(w.charAt(0)) + w.substring(1);
        }
        return w;
    }

    /**
     * AI Autocorrect: returns corrected word if `rawWord` is a clear typo or contraction,
     * or null if `rawWord` is already valid / intentionally learned.
     */
    public String autoCorrect(String rawWord) {
        if (rawWord == null || rawWord.isEmpty()) return null;
        String low = rawWord.toLowerCase(Locale.US);
        boolean allUpper = rawWord.length() > 1 && rawWord.equals(rawWord.toUpperCase(Locale.US));
        boolean firstUpper = Character.isUpperCase(rawWord.charAt(0));

        // 1. Instant fast-path contractions & notorious typos (always wins, even for "i" -> "I")
        String quick = QUICK_FIX.get(low);
        if (quick != null) {
            if (quick.equals(rawWord)) return null;
            if (allUpper) return quick.toUpperCase(Locale.US);
            if (firstUpper && !quick.isEmpty()) {
                return Character.toUpperCase(quick.charAt(0)) + quick.substring(1);
            }
            return quick;
        }

        if (low.length() < 2 || low.length() > 20) return null;

        // 2. If word is already in dictionary or user explicitly typed it >= 2 times, keep as-is!
        synchronized (dictionary) {
            if (dictSet.contains(low)) return null;
        }
        synchronized (userBoost) {
            Integer ub = userBoost.get(low);
            if (ub != null && ub >= 2) return null;
        }

        // 3. Find best proximity-weighted typo correction from dictionary
        String best = findBestCorrection(low);
        if (best == null || best.equals(low)) return null;
        return applyCase(best, firstUpper, allUpper);
    }

    private String findBestCorrection(String low) {
        int len = low.length();
        float maxAllowedCost = (len <= 3) ? 1.05f : (len <= 5 ? 1.45f : 2.15f);
        String bestWord = null;
        float bestScore = Float.MAX_VALUE;

        synchronized (dictionary) {
            int limit = Math.min(dictionary.size(), 9000);
            for (int i = 0; i < limit; i++) {
                String cand = dictionary.get(i);
                int clen = cand.length();
                if (Math.abs(clen - len) > 2) continue;
                // Quick first-char proximity filter for 5+ letter words (huge speedup!)
                if (len >= 5) {
                    char a0 = low.charAt(0), b0 = cand.charAt(0);
                    if (a0 != b0 && keyDist(a0, b0) > 1.55f) {
                        // Still allow transposition of first 2 letters (e.g. "hte" -> "the")
                        if (!(clen >= 2 && low.charAt(0) == cand.charAt(1) && low.charAt(1) == cand.charAt(0))) {
                            continue;
                        }
                    }
                }
                float ed = proximityEditDistance(low, cand, maxAllowedCost);
                if (ed <= maxAllowedCost) {
                    float freqBias = (float) (0.28 * (Math.log(i + 2.0) / Math.log(limit + 2.0)));
                    float score = ed + freqBias;
                    if (score < bestScore) {
                        bestScore = score;
                        bestWord = cand;
                    }
                }
            }
        }
        return bestWord;
    }

    /**
     * Suggests top 3 completions / typo corrections while actively typing a word.
     * Slot #0 is the primary autocorrect candidate when the typed word has a typo!
     */
    public List<String> suggest(String prefix, boolean autoCorrectEnabled) {
        if (prefix == null || prefix.isEmpty()) return Collections.emptyList();
        String low = prefix.toLowerCase(Locale.US);
        boolean allUpper = prefix.length() > 1 && prefix.equals(prefix.toUpperCase(Locale.US));
        boolean firstUpper = Character.isUpperCase(prefix.charAt(0));

        List<String> out = new ArrayList<>(MAX_SUGGESTIONS);

        // If AI autocorrect has a strong fix for this exact typo, put it in slot #0!
        if (autoCorrectEnabled) {
            String fix = autoCorrect(prefix);
            if (fix != null && !fix.equalsIgnoreCase(prefix)) {
                out.add(fix);
            }
        }

        List<Scored> matches = new ArrayList<>(12);

        synchronized (userBoost) {
            for (Map.Entry<String, Integer> e : userBoost.entrySet()) {
                String w = e.getKey();
                if (w.startsWith(low) && !w.equals(low)) {
                    matches.add(new Scored(w, 60000 + e.getValue() * 200 - (w.length() - low.length()) * 10));
                }
            }
        }

        synchronized (dictionary) {
            int size = dictionary.size();
            for (int i = 0; i < size; i++) {
                String w = dictionary.get(i);
                if (w.startsWith(low)) {
                    if (w.equals(low)) continue;
                    int score = (size - i) * 2 - (w.length() - low.length()) * 140;
                    matches.add(new Scored(w, score));
                    if (matches.size() >= 24) break;
                }
            }
        }

        Collections.sort(matches, (a, b) -> Integer.compare(b.score, a.score));
        for (Scored s : matches) {
            String formatted = applyCase(s.word, firstUpper, allUpper);
            if (!out.contains(formatted)) {
                out.add(formatted);
                if (out.size() >= MAX_SUGGESTIONS) break;
            }
        }

        // If still fewer than 2 suggestions and word >= 3 chars, add proximity typo matches
        if (out.size() < 2 && low.length() >= 3) {
            String corr = findBestCorrection(low);
            if (corr != null) {
                String formatted = applyCase(corr, firstUpper, allUpper);
                if (!out.contains(formatted)) out.add(formatted);
            }
        }
        return out;
    }

    /**
     * QWERTY keyboard-proximity weighted Damerau-Levenshtein distance with early cutoff.
     */
    private static float proximityEditDistance(String a, String b, float maxCost) {
        int la = a.length(), lb = b.length();
        if (Math.abs(la - lb) > 2) return maxCost + 1f;

        float[][] dp = new float[la + 1][lb + 1];
        for (int i = 0; i <= la; i++) dp[i][0] = i;
        for (int j = 0; j <= lb; j++) dp[0][j] = j;

        for (int i = 1; i <= la; i++) {
            char ca = a.charAt(i - 1);
            float rowMin = Float.MAX_VALUE;
            for (int j = 1; j <= lb; j++) {
                char cb = b.charAt(j - 1);
                float subCost;
                if (ca == cb) {
                    subCost = 0f;
                } else {
                    float kd = keyDist(ca, cb);
                    // Adjacent keys on QWERTY cost only 0.58 instead of 1.0!
                    subCost = (kd <= 1.25f) ? 0.58f : (kd <= 1.75f ? 0.85f : 1.15f);
                }

                float del = dp[i - 1][j] + 1.0f;
                float ins = dp[i][j - 1] + 0.95f;
                float sub = dp[i - 1][j - 1] + subCost;
                float v = Math.min(sub, Math.min(del, ins));

                // Adjacent character transposition (e.g. "teh" -> "the", "keybaord" -> "keyboard")
                if (i > 1 && j > 1 && ca == b.charAt(j - 2) && a.charAt(i - 2) == cb) {
                    v = Math.min(v, dp[i - 2][j - 2] + 0.55f);
                }

                dp[i][j] = v;
                if (v < rowMin) rowMin = v;
            }
            if (rowMin > maxCost) return maxCost + 1f;
        }
        return dp[la][lb];
    }

    private static float keyDist(char a, char b) {
        if (a < 'a' || a > 'z' || b < 'a' || b > 'z') return 3f;
        float dx = QWERTY_X[a - 'a'] - QWERTY_X[b - 'a'];
        float dy = QWERTY_Y[a - 'a'] - QWERTY_Y[b - 'a'];
        return (float) Math.hypot(dx, dy);
    }

    private static String applyCase(String word, boolean firstUpper, boolean allUpper) {
        if (allUpper) return word.toUpperCase(Locale.US);
        if (firstUpper && !word.isEmpty()) {
            return Character.toUpperCase(word.charAt(0)) + word.substring(1);
        }
        return word;
    }

    private static final class Scored {
        final String word;
        final int score;
        Scored(String word, int score) {
            this.word = word;
            this.score = score;
        }
    }
}
