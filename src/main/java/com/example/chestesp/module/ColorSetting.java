package com.example.chestesp.module;

import java.util.Locale;

/**
 * Farbe als ARGB-Wert. Haelt zusaetzlich Farbton/Saettigung/Helligkeit, damit der
 * Farbwaehler den Farbton nicht verliert, wenn die Farbe grau oder schwarz wird.
 */
public class ColorSetting extends Setting<Integer> {
    public final boolean alpha;
    private final int def;
    private int argb;
    private float hue, sat, val;

    public ColorSetting(String id, String name, String description, int defArgb, boolean alpha) {
        super(id, name, description);
        this.alpha = alpha;
        this.def = defArgb;
        set(defArgb);
    }

    public int argb() { return argb; }

    public float r() { return ((argb >> 16) & 0xFF) / 255f; }
    public float g() { return ((argb >> 8) & 0xFF) / 255f; }
    public float b() { return (argb & 0xFF) / 255f; }
    public float a() { return ((argb >>> 24) & 0xFF) / 255f; }

    /** Farbe ohne Transparenz (fuer Texte und Symbole). */
    public int opaque() { return argb | 0xFF000000; }

    public float hue() { return hue; }
    public float sat() { return sat; }
    public float val() { return val; }

    @Override public Integer get() { return argb; }

    @Override public void set(Integer v) {
        if (v == null) return;
        int c = alpha ? v : (v | 0xFF000000);
        argb = c;
        float[] hsv = toHsv(c);
        // Farbton nur uebernehmen, wenn er aus der Farbe ablesbar ist
        if (hsv[1] > 0.001f && hsv[2] > 0.001f) hue = hsv[0];
        if (hsv[2] > 0.001f) sat = hsv[1];
        val = hsv[2];
    }

    public void setHsv(float h, float s, float v) {
        hue = clamp01(h);
        sat = clamp01(s);
        val = clamp01(v);
        int rgb = fromHsv(hue, sat, val);
        argb = (argb & 0xFF000000) | rgb;
    }

    public void setAlpha(float a) {
        if (!alpha) return;
        int ai = Math.round(clamp01(a) * 255f);
        argb = (ai << 24) | (argb & 0xFFFFFF);
    }

    public String hex() {
        if (alpha) return String.format(Locale.ROOT, "#%08X", argb);
        return String.format(Locale.ROOT, "#%06X", argb & 0xFFFFFF);
    }

    @Override public void reset() { set(def); }

    @Override public String serialize() { return String.format(Locale.ROOT, "#%08X", argb); }

    @Override public void deserialize(String text) {
        String t = text.trim();
        if (t.startsWith("#")) t = t.substring(1);
        try {
            long parsed = Long.parseLong(t, 16);
            if (t.length() <= 6) parsed |= 0xFF000000L;
            set((int) parsed);
        } catch (NumberFormatException ignored) {
        }
    }

    // ------------------------------------------------------------------ Farbmathematik

    public static int fromHsv(float h, float s, float v) {
        float hh = (h - (float) Math.floor(h)) * 6f;
        int i = (int) Math.floor(hh);
        float f = hh - i;
        float p = v * (1 - s), q = v * (1 - s * f), t = v * (1 - s * (1 - f));
        float r, g, b;
        switch (i) {
            case 0 -> { r = v; g = t; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = t; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = t; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }
        return (Math.round(r * 255) << 16) | (Math.round(g * 255) << 8) | Math.round(b * 255);
    }

    public static float[] toHsv(int argb) {
        float r = ((argb >> 16) & 0xFF) / 255f, g = ((argb >> 8) & 0xFF) / 255f, b = (argb & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        float d = max - min;
        float h = 0;
        if (d > 1e-6f) {
            if (max == r) h = ((g - b) / d) % 6f;
            else if (max == g) h = (b - r) / d + 2f;
            else h = (r - g) / d + 4f;
            h /= 6f;
            if (h < 0) h += 1f;
        }
        float s = max <= 1e-6f ? 0 : d / max;
        return new float[]{h, s, max};
    }

    private static float clamp01(float f) {
        return f < 0 ? 0 : (f > 1 ? 1 : f);
    }
}
