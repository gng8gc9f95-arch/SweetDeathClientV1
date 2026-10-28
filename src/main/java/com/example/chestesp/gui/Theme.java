package com.example.chestesp.gui;

import com.example.chestesp.module.ColorSetting;
import com.example.chestesp.module.Modules;

/** Farbpalette der Oberflaeche (dunkles "Web-App"-Design). */
public final class Theme {
    public static final int OVERLAY_TOP = 0x8C05060A;
    public static final int OVERLAY_BOTTOM = 0xC005060A;

    public static final int WINDOW = 0xFF0C0D12;
    public static final int SIDEBAR = 0xFF0F1117;
    public static final int CONTENT = 0xFF0A0B10;
    public static final int CARD = 0xFF12141B;
    public static final int CARD_HOVER = 0xFF171A22;
    public static final int INPUT = 0xFF171920;
    public static final int INPUT_HOVER = 0xFF1D2029;
    public static final int BORDER = 0xFF232631;
    public static final int BORDER_SOFT = 0xFF1A1D25;
    public static final int TRACK = 0xFF262A35;

    public static final int TEXT = 0xFFF2F4F8;
    public static final int TEXT_DIM = 0xFFA3A9B8;
    public static final int TEXT_MUTED = 0xFF666D7C;

    public static final int GREEN = 0xFF22C55E;
    public static final int RED = 0xFFEF4444;
    public static final int WHITE = 0xFFFFFFFF;

    private Theme() {}

    public static int accent() {
        return Modules.GUI != null ? Modules.GUI.accentColor.opaque() : 0xFF8B5CF6;
    }

    /** Zweite Verlaufsfarbe: Akzent mit leicht verschobenem Farbton. */
    public static int accent2() {
        float[] hsv = ColorSetting.toHsv(accent());
        return 0xFF000000 | ColorSetting.fromHsv(hsv[0] + 0.07f, Math.min(1f, hsv[1] * 0.9f), Math.min(1f, hsv[2] * 1.05f + 0.05f));
    }

    public static boolean animations() {
        return Modules.GUI == null || Modules.GUI.animations.on();
    }

    // ------------------------------------------------------------------ Farbhelfer

    public static int alpha(int color, int a) {
        return (Math.max(0, Math.min(255, a)) << 24) | (color & 0xFFFFFF);
    }

    public static int mulAlpha(int color, float f) {
        int a = Math.round(((color >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, f)));
        return (a << 24) | (color & 0xFFFFFF);
    }

    public static int lerp(int c1, int c2, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int a = (int) (((c1 >>> 24) & 0xFF) + ((((c2 >>> 24) & 0xFF) - ((c1 >>> 24) & 0xFF)) * t));
        int r = (int) (((c1 >> 16) & 0xFF) + ((((c2 >> 16) & 0xFF) - ((c1 >> 16) & 0xFF)) * t));
        int g = (int) (((c1 >> 8) & 0xFF) + ((((c2 >> 8) & 0xFF) - ((c1 >> 8) & 0xFF)) * t));
        int b = (int) ((c1 & 0xFF) + (((c2 & 0xFF) - (c1 & 0xFF)) * t));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /** Helligkeit 0..1 (fuer lesbaren Text auf farbigen Flaechen). */
    public static float luminance(int c) {
        return (0.299f * ((c >> 16) & 0xFF) + 0.587f * ((c >> 8) & 0xFF) + 0.114f * (c & 0xFF)) / 255f;
    }
}
