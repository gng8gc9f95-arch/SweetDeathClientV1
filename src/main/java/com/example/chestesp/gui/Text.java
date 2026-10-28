package com.example.chestesp.gui;

import com.example.chestesp.ChestEspClient;
import com.example.chestesp.module.Modules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

/**
 * Text in der modernen Schrift (Inter, mitgeliefert als TrueType) oder in der
 * Minecraft-Schrift. Fuer jede GUI-Skalierung gibt es eine eigene Schriftdefinition,
 * deren Aufloesung genau den Bildschirmpixeln entspricht - dadurch bleibt der Text scharf.
 */
public final class Text {
    public enum Kind {
        REGULAR("ui", 8f, false),
        BOLD("ui_bold", 8f, true),
        SMALL("ui_small", 6.5f, false),
        TITLE("ui_title", 12f, true);

        final String file;
        final float size;
        final boolean bold;

        Kind(String file, float size, boolean bold) {
            this.file = file;
            this.size = size;
            this.bold = bold;
        }
    }

    /** Hoechste mitgelieferte Aufloesungsstufe. */
    private static final int MAX_SCALE = 6;
    private static final Style[][] MODERN = new Style[Kind.values().length][MAX_SCALE + 1];
    private static final Style MC_BOLD = Style.EMPTY.withBold(true);

    private Text() {}

    public static boolean modern() {
        return Modules.GUI == null || Modules.GUI.font.is(0);
    }

    private static Font font() {
        return Minecraft.getInstance().font;
    }

    private static Style style(Kind k) {
        if (!modern()) return k.bold ? MC_BOLD : Style.EMPTY;
        int s = Math.min(MAX_SCALE, Draw.scale());
        Style st = MODERN[k.ordinal()][s];
        if (st == null) {
            Identifier id = Identifier.fromNamespaceAndPath(ChestEspClient.MOD_ID, k.file + "_" + s);
            st = Style.EMPTY.withFont(new FontDescription.Resource(id));
            MODERN[k.ordinal()][s] = st;
        }
        return st;
    }

    public static Component of(String text, Kind k) {
        return Component.literal(text).setStyle(style(k));
    }

    public static int width(String text, Kind k) {
        return font().width(of(text, k));
    }

    /** Abstand von der Zeilenoberkante zur optischen Mitte der Schrift. */
    private static float centerOffset(Kind k) {
        if (!modern()) return 4f;
        // Grundlinie liegt bei y+7; Versalhoehe von Inter = 0,727 em
        return 7f - 0.34f * k.size;
    }

    /** Zeichnet Text, vertikal zentriert auf cy. */
    public static void draw(GuiGraphics g, String text, float x, float cy, int color, Kind k) {
        if (text == null || text.isEmpty()) return;
        int c = Draw.alpha >= 0.999f ? color : Theme.mulAlpha(color, Draw.alpha);
        if ((c >>> 24) < 5) return;
        int s = Draw.scale();
        float y = cy - centerOffset(k);
        // auf echte Pixel runden, damit die Glyphen exakt auf dem Raster liegen
        float fx = Math.round(x * s) / (float) s;
        float fy = Math.round(y * s) / (float) s;
        g.pose().pushMatrix();
        g.pose().translate(fx, fy);
        g.drawString(font(), of(text, k), 0, 0, c, false);
        g.pose().popMatrix();
    }

    public static void drawRight(GuiGraphics g, String text, float right, float cy, int color, Kind k) {
        draw(g, text, right - width(text, k), cy, color, k);
    }

    public static void drawCentered(GuiGraphics g, String text, float cx, float cy, int color, Kind k) {
        draw(g, text, cx - width(text, k) / 2f, cy, color, k);
    }

    /** Kuerzt Text mit "..." auf die maximale Breite. */
    public static String fit(String text, float maxWidth, Kind k) {
        if (text == null) return "";
        if (width(text, k) <= maxWidth) return text;
        String dots = "…";
        int lo = 0, hi = text.length();
        while (lo < hi) {
            int mid = (lo + hi + 1) / 2;
            if (width(text.substring(0, mid) + dots, k) <= maxWidth) lo = mid;
            else hi = mid - 1;
        }
        return lo <= 0 ? dots : text.substring(0, lo).trim() + dots;
    }
}
