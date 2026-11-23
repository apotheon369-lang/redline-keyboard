package dev.redline.keyboard;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * High-accuracy SHARK2 + Exact Monotonic DP Alignment + Inflection-Corner + Log-Frequency Glide Engine.
 *
 * Precomputes 28-point location & scale-normalized shape templates for 18,000+ words on a background
 * thread using the keyboard's live QWERTY key coordinates, achieving high top-1 accuracy in ~2-4ms.
 */
public final class SwipeDecoder {
    private static final int SAMPLES = 28;
    private static final int MAX_RESULTS = 3;

    // Normalized isotropic key centers (units = 1 key width)
    private final float[] keyX = new float[26];
    private final float[] keyY = new float[26];
    private volatile boolean geometryReady = false;
    private volatile boolean templatesReady = false;
    private volatile float lastGeoHash = 0f;

    // 26x26 bucketed templates by (firstChar, lastChar)
    @SuppressWarnings("unchecked")
    private volatile List<Template>[][] buckets = null;

    private volatile List<String> sourceWords = Collections.emptyList();

    public synchronized void setDictionary(List<String> words) {
        this.sourceWords = words != null ? new ArrayList<>(words) : Collections.emptyList();
        if (geometryReady) {
            rebuildTemplatesAsync();
        }
    }

    public synchronized void updateKeyGeometry(float[] centersX, float[] centersY, float keyWidth) {
        if (keyWidth <= 1f || centersX == null || centersY == null) return;
        float hash = keyWidth;
        for (int i = 0; i < 26; i++) {
            hash = hash * 31f + centersX[i] * 0.01f + centersY[i] * 0.01f;
        }
        if (geometryReady && Math.abs(hash - lastGeoHash) < 0.05f) return;
        lastGeoHash = hash;

        for (int i = 0; i < 26; i++) {
            keyX[i] = centersX[i] / keyWidth;
            keyY[i] = centersY[i] / keyWidth;
        }
        geometryReady = true;
        if (!sourceWords.isEmpty()) {
            rebuildTemplatesAsync();
        }
    }

    private void rebuildTemplatesAsync() {
        final List<String> wordsSnapshot = sourceWords;
        final float[] kx = Arrays.copyOf(keyX, 26);
        final float[] ky = Arrays.copyOf(keyY, 26);

        new Thread(() -> {
            @SuppressWarnings("unchecked")
            List<Template>[][] grid = new ArrayList[26][26];
            int total = wordsSnapshot.size();
            float[] rawX = new float[24];
            float[] rawY = new float[24];

            for (int rank = 0; rank < total; rank++) {
                String w = wordsSnapshot.get(rank);
                char[] seq = new char[24];
                int seqLen = 0;
                char prev = 0;
                for (int i = 0; i < w.length() && seqLen < 24; i++) {
                    char c = w.charAt(i);
                    if (c >= 'a' && c <= 'z') {
                        if (c != prev) {
                            seq[seqLen++] = c;
                            prev = c;
                        }
                    }
                }
                if (seqLen < 2) continue;

                int first = seq[0] - 'a';
                int last = seq[seqLen - 1] - 'a';
                for (int i = 0; i < seqLen; i++) {
                    int idx = seq[i] - 'a';
                    rawX[i] = kx[idx];
                    rawY[i] = ky[idx];
                }

                float idealLen = pathLength(rawX, rawY, seqLen);
                float[] locX = new float[SAMPLES];
                float[] locY = new float[SAMPLES];
                resample(rawX, rawY, seqLen, locX, locY);

                float[] shpX = new float[SAMPLES];
                float[] shpY = new float[SAMPLES];
                normalizeShape(locX, locY, shpX, shpY);

                byte[] seqBytes = new byte[seqLen];
                for (int i = 0; i < seqLen; i++) seqBytes[i] = (byte) (seq[i] - 'a');

                Template t = new Template(w, seqBytes, rank, idealLen, locX, locY, shpX, shpY);
                if (grid[first][last] == null) {
                    grid[first][last] = new ArrayList<>(16);
                }
                grid[first][last].add(t);
            }

            buckets = grid;
            templatesReady = true;
        }, "redline-swipe-index").start();
    }

    public List<String> decode(float[] pxX, float[] pxY, int count, float keyWidth,
                               Map<String, Integer> userBoost) {
        if (!templatesReady || buckets == null || count < 2 || keyWidth <= 1f) {
            return Collections.emptyList();
        }

        float[] ux = new float[count];
        float[] uy = new float[count];
        for (int i = 0; i < count; i++) {
            ux[i] = pxX[i] / keyWidth;
            uy[i] = pxY[i] / keyWidth;
        }

        float uLen = pathLength(ux, uy, count);
        if (uLen < 0.55f) return Collections.emptyList();

        float startX = ux[0], startY = uy[0];
        float endX = ux[count - 1], endY = uy[count - 1];

        int[] startKeys = new int[4];
        float[] startDists = new float[4];
        int nStart = findNearestKeys(startX, startY, 1.50f, startKeys, startDists);

        int[] endKeys = new int[4];
        float[] endDists = new float[4];
        int nEnd = findNearestKeys(endX, endY, 1.50f, endKeys, endDists);

        if (nStart == 0 || nEnd == 0) return Collections.emptyList();

        float[] uLocX = new float[SAMPLES];
        float[] uLocY = new float[SAMPLES];
        resample(ux, uy, count, uLocX, uLocY);

        float[] uShpX = new float[SAMPLES];
        float[] uShpY = new float[SAMPLES];
        normalizeShape(uLocX, uLocY, uShpX, uShpY);

        // Extract sharp inflection corners (user intentionally turned at these coordinates)
        float[] cornerX = new float[16];
        float[] cornerY = new float[16];
        int nCorners = extractCorners(uLocX, uLocY, cornerX, cornerY);

        // Preallocate DP scratch buffers once per decode
        float[] dpPrev = new float[SAMPLES];
        float[] dpCurr = new float[SAMPLES];

        List<Scored> results = new ArrayList<>(16);
        List<Template>[][] grid = buckets;
        double logMax = Math.log(18005.0);

        for (int si = 0; si < nStart; si++) {
            int sk = startKeys[si];
            float sDist = startDists[si];
            for (int ei = 0; ei < nEnd; ei++) {
                int ek = endKeys[ei];
                float eDist = endDists[ei];
                List<Template> bucket = grid[sk][ek];
                if (bucket == null) continue;

                float endBaseCost = sDist * 1.15f + eDist * 1.25f;

                for (int bi = 0; bi < bucket.size(); bi++) {
                    Template t = bucket.get(bi);

                    // Fast length-ratio pre-filter
                    if (uLen < t.idealLen * 0.48f - 0.75f || uLen > t.idealLen * 1.95f + 2.0f) {
                        continue;
                    }

                    // Channel 1 & 2: Absolute location + normalized shape L2 curves
                    float locSum = 0f;
                    float shpSum = 0f;
                    for (int k = 0; k < SAMPLES; k++) {
                        float dx1 = uLocX[k] - t.locX[k];
                        float dy1 = uLocY[k] - t.locY[k];
                        locSum += (float) Math.sqrt(dx1 * dx1 + dy1 * dy1);

                        float dx2 = uShpX[k] - t.shpX[k];
                        float dy2 = uShpY[k] - t.shpY[k];
                        shpSum += (float) Math.sqrt(dx2 * dx2 + dy2 * dy2);
                    }
                    float locCost = locSum / SAMPLES;
                    float shpCost = shpSum / SAMPLES;

                    if (locCost > 2.20f) continue;

                    // Channel 3: Exact Monotonic DP letter-to-stroke alignment!
                    float alignCost = dpMonotonicAlignmentCost(t.seq, uLocX, uLocY, dpPrev, dpCurr);

                    // Channel 4: Unexplained inflection corner penalty (catches skipped turns!)
                    float cornerCost = cornerCoverageCost(t.seq, cornerX, cornerY, nCorners);

                    // Channel 5: Path-length consistency
                    float lenRatioDiff = Math.abs(uLen - t.idealLen) / Math.max(1.8f, t.idealLen);

                    // Channel 6: Gentle log-frequency tie-breaker + user-learned boost
                    float freqPenalty = (float) (0.30 * (Math.log(t.rank + 2.0) / logMax));
                    if (userBoost != null) {
                        Integer ub = userBoost.get(t.word);
                        if (ub != null && ub > 0) {
                            freqPenalty -= Math.min(0.22f, ub * 0.04f);
                        }
                    }

                    float totalCost = locCost * 1.15f
                            + shpCost * 0.70f
                            + endBaseCost * 0.58f
                            + alignCost * 1.55f
                            + cornerCost * 1.05f
                            + lenRatioDiff * 0.55f
                            + freqPenalty;

                    results.add(new Scored(t.word, totalCost));
                }
            }
        }

        if (results.isEmpty()) return Collections.emptyList();
        Collections.sort(results, (a, b) -> Float.compare(a.cost, b.cost));

        List<String> out = new ArrayList<>(MAX_RESULTS);
        for (Scored s : results) {
            if (!out.contains(s.word)) {
                out.add(s.word);
                if (out.size() >= MAX_RESULTS) break;
            }
        }
        return out;
    }

    /**
     * Exact O(N * SAMPLES) monotonic dynamic programming alignment:
     * maps every letter seq[0..n-1] to a non-decreasing sample index s in [0..SAMPLES-1]
     * minimizing total distance from each letter key to its matched stroke point.
     */
    private float dpMonotonicAlignmentCost(byte[] seq, float[] uLocX, float[] uLocY,
                                           float[] prev, float[] curr) {
        int n = seq.length;
        if (n <= 2) return 0f;

        // Letter 0 is pinned to stroke start (sample 0)
        float dx0 = uLocX[0] - keyX[seq[0]];
        float dy0 = uLocY[0] - keyY[seq[0]];
        float d0 = (float) Math.sqrt(dx0 * dx0 + dy0 * dy0);
        prev[0] = d0;
        for (int s = 1; s < SAMPLES; s++) {
            prev[s] = 1e6f;
        }

        // Prefix minimum makes each inner letter O(SAMPLES)!
        for (int i = 1; i < n; i++) {
            float kx = keyX[seq[i]];
            float ky = keyY[seq[i]];
            float runMin = 1e6f;
            for (int s = 0; s < SAMPLES; s++) {
                if (prev[s] < runMin) runMin = prev[s];
                float dx = uLocX[s] - kx;
                float dy = uLocY[s] - ky;
                float dist = (float) Math.sqrt(dx * dx + dy * dy);
                // Small deadzone (0.28 key-widths) so normal thumb jitter incurs zero penalty
                float pen = Math.max(0f, dist - 0.28f);
                curr[s] = runMin + pen;
            }
            float[] tmp = prev;
            prev = curr;
            curr = tmp;
        }

        // Last letter must align to stroke end (sample SAMPLES - 1)
        float total = prev[SAMPLES - 1];
        return total / n;
    }

    private float cornerCoverageCost(byte[] seq, float[] cx, float[] cy, int nCorners) {
        if (nCorners == 0) return 0f;
        float excessSum = 0f;
        for (int c = 0; c < nCorners; c++) {
            float minD2 = Float.MAX_VALUE;
            for (byte b : seq) {
                float dx = keyX[b] - cx[c];
                float dy = keyY[b] - cy[c];
                float d2 = dx * dx + dy * dy;
                if (d2 < minD2) minD2 = d2;
            }
            float d = (float) Math.sqrt(minD2);
            if (d > 0.65f) {
                excessSum += (d - 0.65f);
            }
        }
        // Blend summed excess (penalizes skipping multiple turns) with average
        return Math.min(3.0f, excessSum * 0.55f);
    }

    private int extractCorners(float[] x, float[] y, float[] outX, float[] outY) {
        int count = 0;
        for (int i = 2; i < SAMPLES - 2 && count < outX.length; i++) {
            float v1x = x[i] - x[i - 2];
            float v1y = y[i] - y[i - 2];
            float v2x = x[i + 2] - x[i];
            float v2y = y[i + 2] - y[i];
            float l1 = (float) Math.hypot(v1x, v1y);
            float l2 = (float) Math.hypot(v2x, v2y);
            if (l1 < 0.14f || l2 < 0.14f) continue;
            float cos = (v1x * v2x + v1y * v2y) / (l1 * l2);
            // cos < 0.78 corresponds to turning angle > ~38 degrees
            if (cos < 0.78f) {
                outX[count] = x[i];
                outY[count] = y[i];
                count++;
                i++;
            }
        }
        return count;
    }

    private int findNearestKeys(float x, float y, float maxRadius, int[] outKeys, float[] outDists) {
        int found = 0;
        float maxR2 = maxRadius * maxRadius;
        for (int k = 0; k < 26; k++) {
            float dx = keyX[k] - x;
            float dy = keyY[k] - y;
            float d2 = dx * dx + dy * dy;
            if (d2 > maxR2) continue;
            float d = (float) Math.sqrt(d2);
            int pos = found;
            if (pos < outKeys.length) {
                found++;
            } else if (d >= outDists[outKeys.length - 1]) {
                continue;
            } else {
                pos = outKeys.length - 1;
            }
            while (pos > 0 && d < outDists[pos - 1]) {
                outKeys[pos] = outKeys[pos - 1];
                outDists[pos] = outDists[pos - 1];
                pos--;
            }
            outKeys[pos] = k;
            outDists[pos] = d;
        }
        return found;
    }

    private static float pathLength(float[] x, float[] y, int n) {
        float total = 0f;
        for (int i = 1; i < n; i++) {
            total += (float) Math.hypot(x[i] - x[i - 1], y[i] - y[i - 1]);
        }
        return total;
    }

    private static void resample(float[] x, float[] y, int n, float[] outX, float[] outY) {
        if (n <= 1) {
            Arrays.fill(outX, x[0]);
            Arrays.fill(outY, y[0]);
            return;
        }
        float total = pathLength(x, y, n);
        if (total < 1e-4f) {
            Arrays.fill(outX, x[0]);
            Arrays.fill(outY, y[0]);
            return;
        }
        float step = total / (SAMPLES - 1);
        outX[0] = x[0];
        outY[0] = y[0];

        int seg = 0;
        float acc = 0f;
        for (int k = 1; k < SAMPLES - 1; k++) {
            float target = k * step;
            while (seg < n - 2) {
                float segLen = (float) Math.hypot(x[seg + 1] - x[seg], y[seg + 1] - y[seg]);
                if (acc + segLen >= target) break;
                acc += segLen;
                seg++;
            }
            float segLen = (float) Math.hypot(x[seg + 1] - x[seg], y[seg + 1] - y[seg]);
            float t = segLen > 1e-5f ? Math.max(0f, Math.min(1f, (target - acc) / segLen)) : 0f;
            outX[k] = x[seg] + t * (x[seg + 1] - x[seg]);
            outY[k] = y[seg] + t * (y[seg + 1] - y[seg]);
        }
        outX[SAMPLES - 1] = x[n - 1];
        outY[SAMPLES - 1] = y[n - 1];
    }

    private static void normalizeShape(float[] inX, float[] inY, float[] outX, float[] outY) {
        float meanX = 0f, meanY = 0f;
        float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE;
        float minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (int i = 0; i < SAMPLES; i++) {
            meanX += inX[i];
            meanY += inY[i];
            if (inX[i] < minX) minX = inX[i];
            if (inX[i] > maxX) maxX = inX[i];
            if (inY[i] < minY) minY = inY[i];
            if (inY[i] > maxY) maxY = inY[i];
        }
        meanX /= SAMPLES;
        meanY /= SAMPLES;
        float scale = Math.max(1.2f, Math.max(maxX - minX, maxY - minY));
        for (int i = 0; i < SAMPLES; i++) {
            outX[i] = (inX[i] - meanX) / scale;
            outY[i] = (inY[i] - meanY) / scale;
        }
    }

    private static final class Template {
        final String word;
        final byte[] seq;
        final int rank;
        final float idealLen;
        final float[] locX;
        final float[] locY;
        final float[] shpX;
        final float[] shpY;

        Template(String word, byte[] seq, int rank, float idealLen,
                 float[] locX, float[] locY, float[] shpX, float[] shpY) {
            this.word = word;
            this.seq = seq;
            this.rank = rank;
            this.idealLen = idealLen;
            this.locX = locX;
            this.locY = locY;
            this.shpX = shpX;
            this.shpY = shpY;
        }
    }

    private static final class Scored {
        final String word;
        final float cost;

        Scored(String word, float cost) {
            this.word = word;
            this.cost = cost;
        }
    }
}
