package dev.chill.hillside.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.renderer.block.model.BakedQuad;

/**
 * Cuts block quads to a horizontal band. Each quad is first moved by {@code shift} in Y, then every vertex outside
 * [{@code min}, {@code max}] is pulled onto the edge, with its UV slid along the vertical edge it shares with a
 * neighbouring vertex, so the texture is cropped rather than squashed. Results are cached per source quad
 * (BakedQuad compares by identity) and dropped on resource reload.
 */
final class QuadSlicer {
    private static final int STRIDE = DefaultVertexFormat.BLOCK.getVertexSize() / 4;
    private static final int X = 0, Y = 1, Z = 2, U = 4, V = 5;
    private static final float EPS = 1.0E-4F;
    private static final BakedQuad[] DROPPED = new BakedQuad[0];

    private final float shift, min, max;
    private final Map<BakedQuad, BakedQuad[]> cache = new ConcurrentHashMap<>();

    QuadSlicer(float shift, float min, float max) {
        this.shift = shift;
        this.min = min;
        this.max = max;
    }

    void clear() {
        cache.clear();
    }

    void sliceInto(List<BakedQuad> quads, List<BakedQuad> out) {
        for (BakedQuad q : quads) {
            BakedQuad[] s = cache.computeIfAbsent(q, this::slice);
            if (s.length > 0) out.add(s[0]);
        }
    }

    List<BakedQuad> slice(List<BakedQuad> quads) {
        List<BakedQuad> out = new ArrayList<>(quads.size());
        sliceInto(quads, out);
        return out;
    }

    private BakedQuad[] slice(BakedQuad quad) {
        int[] src = quad.getVertices();
        int n = src.length / STRIDE;
        float[] y = new float[n];
        float lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            y[i] = f(src, i, Y) + shift;
            lo = Math.min(lo, y[i]);
            hi = Math.max(hi, y[i]);
        }
        if (hi - lo < EPS) { // flat face: keep it only if it lies inside the band
            if (lo < min - EPS || lo > max + EPS) return DROPPED;
        } else if (hi <= min + EPS || lo >= max - EPS) {
            return DROPPED;
        }
        int[] out = src.clone();
        for (int i = 0; i < n; i++) {
            float target = y[i] < min ? min : y[i] > max ? max : y[i];
            if (target != y[i]) {
                int j = verticalPartner(src, y, i, n, target > y[i]);
                if (j >= 0) {
                    float t = (target - y[i]) / (y[j] - y[i]);
                    set(out, i, U, lerp(f(src, i, U), f(src, j, U), t));
                    set(out, i, V, lerp(f(src, i, V), f(src, j, V), t));
                }
            }
            set(out, i, Y, target);
        }
        return new BakedQuad[] {new BakedQuad(out, quad.getTintIndex(), quad.getDirection(), quad.getSprite(), quad.isShade())};
    }

    /** The adjacent vertex on the same vertical edge, lying above ({@code up}) or below vertex i; -1 if none. */
    private static int verticalPartner(int[] v, float[] y, int i, int n, boolean up) {
        for (int k : new int[] {(i + 1) % n, (i + n - 1) % n}) {
            if (Math.abs(f(v, k, X) - f(v, i, X)) < EPS && Math.abs(f(v, k, Z) - f(v, i, Z)) < EPS
                    && (up ? y[k] > y[i] : y[k] < y[i])) {
                return k;
            }
        }
        return -1;
    }

    private static float f(int[] v, int vertex, int field) {
        return Float.intBitsToFloat(v[vertex * STRIDE + field]);
    }

    private static void set(int[] v, int vertex, int field, float value) {
        v[vertex * STRIDE + field] = Float.floatToRawIntBits(value);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
