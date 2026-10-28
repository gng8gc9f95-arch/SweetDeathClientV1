package com.example.chestesp.gui;

import com.example.chestesp.feature.SpawnerAlert;
import com.example.chestesp.module.Module;
import com.example.chestesp.module.Modules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Popups unten rechts: Spawner-Funde (mit Bild) und kurze An/Aus-Hinweise. */
public final class Notifications {
    private static final int MAX = 5;
    private static final long ENTER_MS = 260, EXIT_MS = 280;
    private static final float SPAWNER_W = 184, SPAWNER_H = 44;

    private static final class Toast {
        final SpawnerAlert.Find find;      // null = An/Aus-Hinweis
        final Module module;
        final boolean on;
        final long start;
        final long duration;
        float y = Float.NaN;

        Toast(SpawnerAlert.Find find, Module module, boolean on, long duration) {
            this.find = find;
            this.module = module;
            this.on = on;
            this.start = System.currentTimeMillis();
            this.duration = duration;
        }

        float height() {
            return find != null ? SPAWNER_H : 20;
        }
    }

    private static final List<Toast> toasts = new ArrayList<>();
    private static long lastFrame;

    private Notifications() {}

    public static void spawner(SpawnerAlert.Find find) {
        long ms = Math.round(Modules.SPAWNER.duration.value() * 1000);
        push(new Toast(find, null, true, ms));
    }

    public static void toggled(Module module) {
        if (Modules.HUD == null || !Modules.HUD.toasts.on()) return;
        toasts.removeIf(t -> t.module == module);
        push(new Toast(null, module, module.isEnabled(), 1500));
    }

    private static void push(Toast t) {
        toasts.add(0, t);
        while (toasts.size() > MAX) toasts.remove(toasts.size() - 1);
    }

    public static void render(GuiGraphics g) {
        if (toasts.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        long now = System.currentTimeMillis();
        float dt = lastFrame == 0 ? 0.016f : Math.min(0.1f, (now - lastFrame) / 1000f);
        lastFrame = now;

        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        float bottom = sh - 10;

        Iterator<Toast> it = toasts.iterator();
        while (it.hasNext()) {
            Toast t = it.next();
            if (now - t.start > t.duration + EXIT_MS) it.remove();
        }

        float targetY = bottom;
        float saved = Draw.alpha;
        for (Toast t : toasts) {
            float h = t.height();
            targetY -= h;
            if (Float.isNaN(t.y)) t.y = targetY;
            t.y += (targetY - t.y) * Math.min(1f, dt * 12f);

            long age = now - t.start;
            float enter = ease(Math.min(1f, age / (float) ENTER_MS));
            float exit = age > t.duration ? ease(Math.min(1f, (age - t.duration) / (float) EXIT_MS)) : 0f;
            float w = t.find != null ? SPAWNER_W : toggleWidth(t);
            float x = sw - 10 - w + (1f - enter) * (w + 14) + exit * 24;
            Draw.alpha = saved * enter * (1f - exit);

            if (t.find != null) drawSpawner(g, t, x, t.y, mc);
            else drawToggle(g, t, x, t.y, w);
            targetY -= 6;
        }
        Draw.alpha = saved;
    }

    private static float toggleWidth(Toast t) {
        return 38 + Text.width(t.module.name, Text.Kind.BOLD) + Text.width(t.on ? "AN" : "AUS", Text.Kind.SMALL);
    }

    private static void drawToggle(GuiGraphics g, Toast t, float x, float y, float w) {
        int accent = t.module.accentColor();
        Draw.card(g, x, y, w, 20, 10, 0xF0101218, 0xFF262A35);
        Draw.circle(g, x + 11, y + 10, 3.2f, t.on ? Theme.GREEN : Theme.RED);
        Draw.circle(g, x + 11, y + 10, 5.5f, Theme.alpha(t.on ? Theme.GREEN : Theme.RED, 0x30));
        Text.draw(g, t.module.name, x + 20, y + 10, Theme.TEXT, Text.Kind.BOLD);
        Text.drawRight(g, t.on ? "AN" : "AUS", x + w - 9, y + 10, t.on ? Theme.alpha(accent, 0xFF) : Theme.TEXT_MUTED, Text.Kind.SMALL);
    }

    private static void drawSpawner(GuiGraphics g, Toast t, float x, float y, Minecraft mc) {
        SpawnerAlert.Find f = t.find;
        int accent = Modules.SPAWNER.color.opaque();
        float w = SPAWNER_W, h = SPAWNER_H;

        Draw.shadow(g, x, y, w, h, 9, 10, 0x80000000);
        Draw.card(g, x, y, w, h, 9, 0xF20E1016, Theme.alpha(accent, 0x66));
        // leichter Farbschimmer links
        int tint = Theme.alpha(accent, 0x18);
        Draw.roundRect(g, x + 1, y + 1, 10, h - 2, 8, 0, 0, 8, tint);
        Draw.hGradient(g, x + 11, y + 1, 70, h - 2, tint, Theme.alpha(accent, 0));

        // Bild: Spawner mit Spawn-Ei
        float bx = x + 7, by = y + 7, bs = 30;
        Draw.roundRect(g, bx, by, bs, bs, 7, Theme.alpha(accent, 0x2E));
        Draw.card(g, bx, by, bs, bs, 7, 0x00000000, Theme.alpha(accent, 0x55));
        Draw.item(g, f.icon(), bx + 5, by + 4, 20);
        if (!f.egg().isEmpty()) {
            Draw.circle(g, bx + bs - 5, by + bs - 5, 7, 0xFF0E1016);
            Draw.item(g, f.egg(), bx + bs - 11, by + bs - 11, 12);
        }

        float tx = bx + bs + 9;
        float maxW = x + w - 8 - tx;
        Text.draw(g, "SPAWNER GEFUNDEN", tx, y + 11, accent, Text.Kind.SMALL);
        Text.draw(g, Text.fit(f.title(), maxW, Text.Kind.BOLD), tx, y + 21.5f, Theme.TEXT, Text.Kind.BOLD);

        BlockPos p = f.pos();
        String dist = "";
        if (mc.player != null) {
            double dx = p.getX() + 0.5 - mc.player.getX();
            double dy = p.getY() + 0.5 - mc.player.getY();
            double dz = p.getZ() + 0.5 - mc.player.getZ();
            double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
            dist = String.format("  ·  %.0f m", d);
        }
        Text.draw(g, Text.fit(String.format("%d, %d, %d", p.getX(), p.getY(), p.getZ()) + dist, maxW, Text.Kind.SMALL),
                tx, y + 32, Theme.TEXT_DIM, Text.Kind.SMALL);

        // Restzeit
        long age = System.currentTimeMillis() - t.start;
        float left = 1f - Math.min(1f, age / (float) t.duration);
        float barW = w - 18;
        Draw.roundRect(g, x + 9, y + h - 4, barW, 1.5f, 0.75f, Theme.alpha(accent, 0x26));
        Draw.roundRect(g, x + 9, y + h - 4, barW * left, 1.5f, 0.75f, accent);
    }

    private static float ease(float t) {
        float u = 1f - t;
        return 1f - u * u * u;
    }
}
