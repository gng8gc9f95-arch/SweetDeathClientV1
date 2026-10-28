package com.example.chestesp.gui;

import com.example.chestesp.module.Module;
import com.example.chestesp.module.Modules;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Wasserzeichen, Liste der aktiven Module und Popups. */
public final class Hud {
    private static final Map<Module, Float> fade = new HashMap<>();
    private static long lastFrame;

    private Hud() {}

    public static void render(GuiGraphics g, DeltaTracker tick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null) return;

        long now = System.currentTimeMillis();
        float dt = lastFrame == 0 ? 0.016f : Math.min(0.1f, (now - lastFrame) / 1000f);
        lastFrame = now;
        Draw.alpha = 1f;

        if (Modules.HUD.isEnabled()) {
            float y = 6;
            if (Modules.HUD.watermark.on()) {
                watermark(g, 6, y, mc);
                y += 24;
            }
            if (Modules.HUD.moduleList.on()) moduleList(g, 6, y, dt);
        }
        Notifications.render(g);
        Draw.alpha = 1f;
    }

    private static void watermark(GuiGraphics g, float x, float y, Minecraft mc) {
        int accent = Modules.HUD.color.opaque();
        String fps = mc.getFps() + " fps";
        float w = 26 + Text.width("ChestESP", Text.Kind.BOLD) + 14 + Text.width(fps, Text.Kind.SMALL);
        float h = 18;
        Draw.card(g, x, y, w, h, 9, 0xE00E1016, 0xFF242833);
        logo(g, x + 4, y + 3, 12, accent);
        float tx = x + 21;
        Text.draw(g, "ChestESP", tx, y + 9, Theme.TEXT, Text.Kind.BOLD);
        tx += Text.width("ChestESP", Text.Kind.BOLD) + 6;
        Draw.circle(g, tx + 1, y + 9, 1.2f, Theme.TEXT_MUTED);
        Text.draw(g, fps, tx + 6, y + 9, Theme.TEXT_DIM, Text.Kind.SMALL);
    }

    /** Kleines Logo: Verlaufskachel mit Truhen-Symbol. */
    public static void logo(GuiGraphics g, float x, float y, float size, int accent) {
        float r = size * 0.3f;
        Draw.roundRect(g, x, y, size, size, r, accent);
        Draw.roundRect(g, x, y, size, size * 0.5f, r, r, 0, 0, Theme.alpha(0xFFFFFF, 0x2A));
        // Truhe: Deckel-Linie + Schloss
        float m = size * 0.24f;
        Draw.roundRect(g, x + m, y + m, size - 2 * m, size - 2 * m, size * 0.08f, Theme.alpha(0xFFFFFF, 0xF0));
        Draw.rect(g, x + m, y + size * 0.47f, size - 2 * m, Math.max(0.5f, size * 0.07f), accent);
        Draw.rect(g, x + size * 0.44f, y + size * 0.40f, size * 0.12f, size * 0.2f, accent);
    }

    private static void moduleList(GuiGraphics g, float x, float y, float dt) {
        List<Module> active = new ArrayList<>();
        for (Module m : Modules.all()) {
            if (!m.toggleable || m == Modules.HUD) continue;
            float target = m.isEnabled() ? 1f : 0f;
            float cur = fade.getOrDefault(m, 0f);
            cur += (target - cur) * Math.min(1f, dt * 14f);
            if (Math.abs(cur - target) < 0.01f) cur = target;
            fade.put(m, cur);
            if (cur > 0.01f) active.add(m);
        }
        active.sort((a, b) -> Integer.compare(Text.width(b.name, Text.Kind.REGULAR), Text.width(a.name, Text.Kind.REGULAR)));

        float saved = Draw.alpha;
        for (Module m : active) {
            float f = fade.get(m);
            float h = 13 * f;
            float w = Text.width(m.name, Text.Kind.REGULAR) + 14;
            Draw.alpha = saved * f;
            float ox = (1f - f) * -10;
            Draw.roundRect(g, x + ox, y, w, 12, 3, 0xC80E1016);
            Draw.roundRect(g, x + ox, y + 2, 2, 8, 1, m.accentColor());
            Text.draw(g, m.name, x + ox + 7, y + 6, Theme.TEXT, Text.Kind.REGULAR);
            y += h;
        }
        Draw.alpha = saved;
    }
}
