package com.example.chestesp.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

/**
 * Zeichenhelfer, die direkt in echten Bildschirmpixeln arbeiten. Dadurch sind
 * Haarlinien (1 Pixel) und weich geglaettete runde Ecken moeglich - unabhaengig
 * von der GUI-Skalierung.
 */
public final class Draw {
    /** Globale Deckkraft (fuer Ein-/Ausblend-Animationen). */
    public static float alpha = 1f;

    private Draw() {}

    public static int scale() {
        double s = Minecraft.getInstance().getWindow().getGuiScale();
        return Math.max(1, (int) Math.round(s));
    }

    private static int px(float units, int s) {
        return Math.round(units * s);
    }

    private static int fade(int color) {
        if (alpha >= 0.999f) return color;
        return Theme.mulAlpha(color, alpha);
    }

    /** true = in GUI-Einheiten statt Bildschirmpixeln zeichnen (fuer weiche Schatten/Flecken, spart viele Rechtecke). */
    private static boolean coarse;
    /** false = Zeilen ohne Kantenglaettung (eine Fuellung pro Zeile). */
    private static boolean aa = true;

    /** Rasterweite: echte Pixel bis GUI-Skalierung 3, darueber 2-3 Pixel pro Schritt (spart Rechtecke). */
    private static int unit() {
        if (coarse) return 1;
        int s = scale();
        return s <= 3 ? s : (s + 1) / 2;
    }

    private static void begin(GuiGraphics g, int s) {
        g.pose().pushMatrix();
        g.pose().scale(1f / s, 1f / s);
    }

    private static void end(GuiGraphics g) {
        g.pose().popMatrix();
    }

    // ------------------------------------------------------------------ Rechtecke

    public static void rect(GuiGraphics g, float x, float y, float w, float h, int color) {
        if (w <= 0 || h <= 0 || ((color >>> 24) == 0)) return;
        int s = unit();
        begin(g, s);
        g.fill(px(x, s), px(y, s), px(x + w, s), px(y + h, s), fade(color));
        end(g);
    }

    /** 1 Pixel hohe/breite Linie (echte Haarlinie). */
    public static void hairlineH(GuiGraphics g, float x, float y, float w, int color) {
        int s = unit();
        begin(g, s);
        int py = px(y, s);
        g.fill(px(x, s), py, px(x + w, s), py + 1, fade(color));
        end(g);
    }

    public static void hairlineV(GuiGraphics g, float x, float y, float h, int color) {
        int s = unit();
        begin(g, s);
        int pxX = px(x, s);
        g.fill(pxX, px(y, s), pxX + 1, px(y + h, s), fade(color));
        end(g);
    }

    public static void vGradient(GuiGraphics g, float x, float y, float w, float h, int top, int bottom) {
        int s = unit();
        begin(g, s);
        g.fillGradient(px(x, s), px(y, s), px(x + w, s), px(y + h, s), fade(top), fade(bottom));
        end(g);
    }

    /** Horizontaler Verlauf (in schmalen Spalten gezeichnet). */
    public static void hGradient(GuiGraphics g, float x, float y, float w, float h, int left, int right) {
        int s = unit();
        begin(g, s);
        int x1 = px(x, s), x2 = px(x + w, s), y1 = px(y, s), y2 = px(y + h, s);
        int width = Math.max(1, x2 - x1);
        int step = Math.max(1, width / 24);
        for (int cx = x1; cx < x2; cx += step) {
            float t = (cx - x1 + step * 0.5f) / width;
            g.fill(cx, y1, Math.min(x2, cx + step), y2, fade(Theme.lerp(left, right, t)));
        }
        end(g);
    }

    // ------------------------------------------------------------------ Abgerundet

    public static void roundRect(GuiGraphics g, float x, float y, float w, float h, float r, int color) {
        roundRect(g, x, y, w, h, r, r, r, r, color);
    }

    /** Abgerundetes Rechteck mit einzelnen Eckradien (oben-links, oben-rechts, unten-rechts, unten-links). */
    public static void roundRect(GuiGraphics g, float x, float y, float w, float h,
                                 float tl, float tr, float br, float bl, int color) {
        if (w <= 0 || h <= 0 || ((color >>> 24) == 0)) return;
        int s = unit();
        begin(g, s);
        roundRectPx(g, px(x, s), px(y, s), px(x + w, s), px(y + h, s), tl * s, tr * s, br * s, bl * s, fade(color));
        end(g);
    }

    /** Karte: 1-Pixel-Rand (echter Ring) plus Fuellung; beide Farben duerfen transparent sein. */
    public static void card(GuiGraphics g, float x, float y, float w, float h, float r, int fill, int border) {
        if (w <= 0 || h <= 0) return;
        int s = unit();
        begin(g, s);
        int x1 = px(x, s), y1 = px(y, s), x2 = px(x + w, s), y2 = px(y + h, s);
        float rp = r * s;
        if ((border >>> 24) != 0) {
            if ((fill >>> 24) != 0) {
                // Fuellung ohne Kantenglaettung - die Kante liegt unter dem Rand
                float ri = Math.max(0, rp - 1);
                aa = false;
                try {
                    roundRectPx(g, x1 + 1, y1 + 1, x2 - 1, y2 - 1, ri, ri, ri, ri, fade(fill));
                } finally {
                    aa = true;
                }
            }
            outlinePx(g, x1, y1, x2, y2, rp, fade(border));
        } else if ((fill >>> 24) != 0) {
            roundRectPx(g, x1, y1, x2, y2, rp, rp, rp, rp, fade(fill));
        }
        end(g);
    }

    /** Nur der Rand eines abgerundeten Rechtecks. */
    public static void outline(GuiGraphics g, float x, float y, float w, float h, float r, int color) {
        card(g, x, y, w, h, r, 0, color);
    }

    private static void outlinePx(GuiGraphics g, int x1, int y1, int x2, int y2, float r, int color) {
        int w = x2 - x1, h = y2 - y1;
        if (w <= 2 || h <= 2) {
            g.fill(x1, y1, x2, y2, color);
            return;
        }
        r = Math.min(r, Math.min(w, h) / 2f);
        float ri = Math.max(0, r - 1);
        int band = (int) Math.ceil(r);
        if (band * 2 > h) band = h / 2;
        for (int i = 0; i < band; i++) {
            float lo = inset(r, i);
            int top = y1 + i, bottom = y2 - 1 - i;
            if (i == 0) {
                span(g, x1 + lo, x2 - lo, top, color);
                span(g, x1 + lo, x2 - lo, bottom, color);
                continue;
            }
            float li = 1 + inset(ri, i - 1);
            if (li >= w / 2f) {
                span(g, x1 + lo, x2 - lo, top, color);
                span(g, x1 + lo, x2 - lo, bottom, color);
                continue;
            }
            thin(g, x1 + lo, x1 + li, top, color);
            thin(g, x2 - li, x2 - lo, top, color);
            thin(g, x1 + lo, x1 + li, bottom, color);
            thin(g, x2 - li, x2 - lo, bottom, color);
        }
        if (h - 2 * band > 0) {
            g.fill(x1, y1 + band, x1 + 1, y2 - band, color);
            g.fill(x2 - 1, y1 + band, x2, y2 - band, color);
        }
    }

    /** Weicher Schatten um ein Rechteck (grob gerastert - er ist ohnehin unscharf). */
    public static void shadow(GuiGraphics g, float x, float y, float w, float h, float r, float size, int color) {
        int steps = 5;
        int baseA = (color >>> 24) & 0xFF;
        coarse = true;
        aa = false;
        try {
            for (int i = steps; i >= 1; i--) {
                float grow = size * i / steps;
                float t = 1f - (float) i / (steps + 1);
                int a = Math.round(baseA * t * t / steps * 2.4f);
                roundRect(g, x - grow, y - grow * 0.6f, w + grow * 2, h + grow * 2, r + grow, Theme.alpha(color, a));
            }
        } finally {
            coarse = false;
            aa = true;
        }
    }

    /** Grosser, weicher Farbfleck (Deko) - grob gerastert. */
    public static void blob(GuiGraphics g, float cx, float cy, float radius, int color) {
        coarse = true;
        try {
            circle(g, cx, cy, radius, color);
        } finally {
            coarse = false;
        }
    }

    private static void roundRectPx(GuiGraphics g, int x1, int y1, int x2, int y2,
                                    float tl, float tr, float br, float bl, int color) {
        int w = x2 - x1, h = y2 - y1;
        if (w <= 0 || h <= 0) return;
        float max = Math.min(w, h) / 2f;
        tl = Math.min(tl, max);
        tr = Math.min(tr, max);
        br = Math.min(br, max);
        bl = Math.min(bl, max);
        int top = (int) Math.ceil(Math.max(tl, tr));
        int bottom = (int) Math.ceil(Math.max(bl, br));
        if (top + bottom > h) bottom = h - top;

        if (h - top - bottom > 0) g.fill(x1, y1 + top, x2, y2 - bottom, color);
        for (int i = 0; i < top; i++) {
            row(g, x1, x2, y1 + i, inset(tl, i), inset(tr, i), color);
        }
        for (int i = 0; i < bottom; i++) {
            row(g, x1, x2, y2 - 1 - i, inset(bl, i), inset(br, i), color);
        }
    }

    /** Wie weit Zeile i (von der Kante gezaehlt) bei Radius r eingerueckt ist. */
    private static float inset(float r, int i) {
        if (r <= 0) return 0;
        float dy = r - (i + 0.5f);
        if (dy <= 0) return 0;
        return r - (float) Math.sqrt(Math.max(0, r * r - dy * dy));
    }

    /** Eine Pixelzeile mit geglaetteten Enden. */
    private static void row(GuiGraphics g, int x1, int x2, int y, float insetL, float insetR, int color) {
        if (!aa) {
            int l = (int) Math.floor(insetL), r = (int) Math.floor(insetR);
            if (x2 - r > x1 + l) g.fill(x1 + l, y, x2 - r, y + 1, color);
            return;
        }
        int fl = (int) Math.ceil(insetL), fr = (int) Math.ceil(insetR);
        if (x2 - fr > x1 + fl) g.fill(x1 + fl, y, x2 - fr, y + 1, color);
        float cl = fl - insetL, cr = fr - insetR;
        if (fl >= 1 && cl > 0.03f) g.fill(x1 + fl - 1, y, x1 + fl, y + 1, Theme.mulAlpha(color, cl));
        if (fr >= 1 && cr > 0.03f) g.fill(x2 - fr, y, x2 - fr + 1, y + 1, Theme.mulAlpha(color, cr));
    }

    // ------------------------------------------------------------------ Kreise

    public static void circle(GuiGraphics g, float cx, float cy, float radius, int color) {
        if (radius <= 0 || ((color >>> 24) == 0)) return;
        int s = unit();
        begin(g, s);
        circlePx(g, cx * s, cy * s, radius * s, fade(color));
        end(g);
    }

    /** Kreisring (Fuellung zwischen zwei Radien). */
    public static void ring(GuiGraphics g, float cx, float cy, float radius, float thickness, int color) {
        int s = unit();
        begin(g, s);
        float ro = radius * s, ri = Math.max(0, (radius - thickness) * s);
        float pcx = cx * s, pcy = cy * s;
        int c = fade(color);
        int y0 = (int) Math.floor(pcy - ro), y1 = (int) Math.ceil(pcy + ro);
        for (int y = y0; y < y1; y++) {
            float dy = (y + 0.5f) - pcy;
            if (Math.abs(dy) >= ro) continue;
            float ho = (float) Math.sqrt(ro * ro - dy * dy);
            if (Math.abs(dy) >= ri) {
                span(g, pcx - ho, pcx + ho, y, c);
            } else {
                float hi = (float) Math.sqrt(ri * ri - dy * dy);
                span(g, pcx - ho, pcx - hi, y, c);
                span(g, pcx + hi, pcx + ho, y, c);
            }
        }
        end(g);
    }

    private static void circlePx(GuiGraphics g, float cx, float cy, float r, int color) {
        int y0 = (int) Math.floor(cy - r), y1 = (int) Math.ceil(cy + r);
        for (int y = y0; y < y1; y++) {
            float dy = (y + 0.5f) - cy;
            float adx = Math.abs(dy);
            if (adx >= r) {
                continue;
            }
            float half = (float) Math.sqrt(r * r - dy * dy);
            // oberste/unterste Zeile etwas schwaecher (vertikale Glaettung)
            float rowCov = Math.min(1f, r - adx + 0.5f);
            span(g, cx - half, cx + half, y, rowCov < 0.999f ? Theme.mulAlpha(color, rowCov) : color);
        }
    }

    /** Schmale Spanne als ein einziges Rechteck; Teilabdeckung wird ueber die Deckkraft angenaehert. */
    private static void thin(GuiGraphics g, float l, float r, int y, int color) {
        if (r - l <= 0.01f) return;
        int a = (int) Math.floor(l), b = (int) Math.ceil(r);
        if (b - a > 3) {
            span(g, l, r, y, color);
            return;
        }
        float cov = (r - l) / (b - a);
        g.fill(a, y, b, y + 1, cov >= 0.97f ? color : Theme.mulAlpha(color, cov));
    }

    /** Horizontale Spanne [l, r) mit Teilpixel-Abdeckung an beiden Enden. */
    private static void span(GuiGraphics g, float l, float r, int y, int color) {
        if (r - l <= 0.01f) return;
        int fl = (int) Math.ceil(l), fr = (int) Math.floor(r);
        if (fr < fl) {
            g.fill(fl - 1, y, fl, y + 1, Theme.mulAlpha(color, r - l));
            return;
        }
        if (fr > fl) g.fill(fl, y, fr, y + 1, color);
        float cl = fl - l, cr = r - fr;
        if (cl > 0.03f) g.fill(fl - 1, y, fl, y + 1, Theme.mulAlpha(color, cl));
        if (cr > 0.03f) g.fill(fr, y, fr + 1, y + 1, Theme.mulAlpha(color, cr));
    }

    // ------------------------------------------------------------------ Sonstiges

    /** Schachbrettmuster (Hintergrund fuer transparente Farben). */
    public static void checker(GuiGraphics g, float x, float y, float w, float h, float cell) {
        rect(g, x, y, w, h, 0xFF2B2E37);
        for (float cy = 0; cy < h; cy += cell) {
            for (float cx = ((int) (cy / cell) % 2 == 0) ? 0 : cell; cx < w; cx += cell * 2) {
                rect(g, x + cx, y + cy, Math.min(cell, w - cx), Math.min(cell, h - cy), 0xFF4A4E5A);
            }
        }
    }

    public static void item(GuiGraphics g, ItemStack stack, float x, float y, float size) {
        if (stack == null || stack.isEmpty()) return;
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        float k = size / 16f;
        g.pose().scale(k, k);
        g.renderItem(stack, 0, 0);
        g.pose().popMatrix();
    }
}
