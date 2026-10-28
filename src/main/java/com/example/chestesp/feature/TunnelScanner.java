package com.example.chestesp.feature;

import java.util.ArrayList;
import java.util.List;

/**
 * Erkennt von Spielern gegrabene Gaenge in einem 16x16-Chunk-Ausschnitt:
 * <ul>
 *   <li>1x1 Tunnel: 1 breit, 1 hoch, Boden + Decke + Waende links/rechts</li>
 *   <li>1x2 Tunnel: 1 breit, 2 hoch (der typische Spielertunnel)</li>
 *   <li>Schaechte: senkrechte 1x1 Loecher, auf allen 4 Seiten Wand</li>
 * </ul>
 * Ein Stueck zaehlt erst ab einer Mindestlaenge am Stueck (natuerliche Hoehlen sind
 * fast nie ueber mehrere Bloecke exakt 1 breit). Gaenge, die ueber die Chunkgrenze
 * gehen, werden ueber die Grenze hinaus nachgemessen.
 */
public final class TunnelScanner {
    public static final int TYPE_1X1 = 0, TYPE_1X2 = 1, TYPE_SHAFT = 2;

    /** Achsparallele Box in Blockkoordinaten; x2/y2/z2 sind exklusiv. */
    public record Box(int x1, int y1, int z1, int x2, int y2, int z2, int type) {}

    /** Zugriff auf die Welt (damit der Algorithmus auch ohne Minecraft testbar ist). */
    public interface Probe {
        /** Begehbar: Luft oder nicht-blockierender Block ohne Fluessigkeit (Fackel, Schiene ...). */
        boolean open(int x, int y, int z);

        /** Wand: Block, der Bewegung blockiert. */
        boolean solid(int x, int y, int z);
    }

    private static final int X1 = 1, X2 = 2, Z1 = 4, Z2 = 8, SH = 16;

    private TunnelScanner() {}

    /**
     * @param x0   kleinste Block-X des Chunks
     * @param z0   kleinste Block-Z des Chunks
     * @param yMin unterste zu pruefende Hoehe (inklusive)
     * @param yMax oberste zu pruefende Hoehe (inklusive)
     * @param worldMinY / worldMaxY  Weltgrenzen (fuer das Nachmessen von Schaechten)
     */
    public static List<Box> scan(Probe p, int x0, int z0, int yMin, int yMax,
                                 int worldMinY, int worldMaxY, int minLen) {
        List<Box> out = new ArrayList<>();
        if (yMax < yMin) return out;
        int h = yMax - yMin + 1;
        byte[] f = new byte[16 * 16 * h];

        boolean any = false;
        for (int y = 0; y < h; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    int v = cell(p, x0 + x, yMin + y, z0 + z);
                    if (v != 0) {
                        f[idx(x, y, z)] = (byte) v;
                        any = true;
                    }
                }
            }
        }
        if (!any) return out;

        // ---- Tunnel entlang X
        for (int y = 0; y < h; y++) {
            for (int z = 0; z < 16; z++) {
                runsX(p, f, out, x0, z0, yMin, y, z, X1, TYPE_1X1, 1, minLen);
                runsX(p, f, out, x0, z0, yMin, y, z, X2, TYPE_1X2, 2, minLen);
            }
        }
        // ---- Tunnel entlang Z
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < 16; x++) {
                runsZ(p, f, out, x0, z0, yMin, y, x, Z1, TYPE_1X1, 1, minLen);
                runsZ(p, f, out, x0, z0, yMin, y, x, Z2, TYPE_1X2, 2, minLen);
            }
        }
        // ---- Senkrechte Schaechte
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int y = 0;
                while (y < h) {
                    if ((f[idx(x, y, z)] & SH) == 0) { y++; continue; }
                    int a = y;
                    while (y + 1 < h && (f[idx(x, y + 1, z)] & SH) != 0) y++;
                    int b = y;
                    int total = b - a + 1;
                    int wx = x0 + x, wz = z0 + z;
                    if (a == 0) total += extend(p, wx, yMin + a, wz, 0, -1, 0, SH, minLen - total, worldMinY, worldMaxY);
                    if (b == h - 1 && total < minLen) total += extend(p, wx, yMin + b, wz, 0, 1, 0, SH, minLen - total, worldMinY, worldMaxY);
                    if (total >= minLen) out.add(new Box(wx, yMin + a, wz, wx + 1, yMin + b + 1, wz + 1, TYPE_SHAFT));
                    y++;
                }
            }
        }
        return out;
    }

    private static void runsX(Probe p, byte[] f, List<Box> out, int x0, int z0, int yMin,
                              int y, int z, int bit, int type, int height, int minLen) {
        int x = 0;
        while (x < 16) {
            if ((f[idx(x, y, z)] & bit) == 0) { x++; continue; }
            int a = x;
            while (x + 1 < 16 && (f[idx(x + 1, y, z)] & bit) != 0) x++;
            int b = x;
            int total = b - a + 1;
            int wy = yMin + y, wz = z0 + z;
            if (a == 0 && total < minLen) total += extend(p, x0, wy, wz, -1, 0, 0, bit, minLen - total, Integer.MIN_VALUE, Integer.MAX_VALUE);
            if (b == 15 && total < minLen) total += extend(p, x0 + 15, wy, wz, 1, 0, 0, bit, minLen - total, Integer.MIN_VALUE, Integer.MAX_VALUE);
            if (total >= minLen) out.add(new Box(x0 + a, wy, wz, x0 + b + 1, wy + height, wz + 1, type));
            x++;
        }
    }

    private static void runsZ(Probe p, byte[] f, List<Box> out, int x0, int z0, int yMin,
                              int y, int x, int bit, int type, int height, int minLen) {
        int z = 0;
        while (z < 16) {
            if ((f[idx(x, y, z)] & bit) == 0) { z++; continue; }
            int a = z;
            while (z + 1 < 16 && (f[idx(x, y, z + 1)] & bit) != 0) z++;
            int b = z;
            int total = b - a + 1;
            int wy = yMin + y, wx = x0 + x;
            if (a == 0 && total < minLen) total += extend(p, wx, wy, z0, 0, 0, -1, bit, minLen - total, Integer.MIN_VALUE, Integer.MAX_VALUE);
            if (b == 15 && total < minLen) total += extend(p, wx, wy, z0 + 15, 0, 0, 1, bit, minLen - total, Integer.MIN_VALUE, Integer.MAX_VALUE);
            if (total >= minLen) out.add(new Box(wx, wy, z0 + a, wx + 1, wy + height, z0 + b + 1, type));
            z++;
        }
    }

    /** Zaehlt, wie viele passende Zellen hinter (sx,sy,sz) in Richtung d noch folgen (hoechstens max). */
    private static int extend(Probe p, int sx, int sy, int sz, int dx, int dy, int dz, int bit,
                              int max, int minY, int maxY) {
        int n = 0;
        for (int k = 1; k <= max; k++) {
            int y = sy + dy * k;
            if (y < minY || y > maxY) break;
            if ((cell(p, sx + dx * k, y, sz + dz * k) & bit) == 0) break;
            n++;
        }
        return n;
    }

    /** Bitmaske, welche Gang-Arten an dieser Position moeglich sind. */
    static int cell(Probe p, int x, int y, int z) {
        if (!p.open(x, y, z)) return 0;
        boolean n = p.solid(x, y, z - 1), s = p.solid(x, y, z + 1);
        boolean w = p.solid(x - 1, y, z), e = p.solid(x + 1, y, z);
        int v = 0;
        if (n && s && w && e) v |= SH;
        boolean alongX = n && s, alongZ = w && e;
        if ((alongX || alongZ) && p.solid(x, y - 1, z)) {
            if (p.solid(x, y + 1, z)) {
                if (alongX) v |= X1;
                if (alongZ) v |= Z1;
            } else if (p.open(x, y + 1, z) && p.solid(x, y + 2, z)) {
                if (alongX && p.solid(x, y + 1, z - 1) && p.solid(x, y + 1, z + 1)) v |= X2;
                if (alongZ && p.solid(x - 1, y + 1, z) && p.solid(x + 1, y + 1, z)) v |= Z2;
            }
        }
        return v;
    }

    private static int idx(int x, int y, int z) {
        return (y * 16 + z) * 16 + x;
    }
}
