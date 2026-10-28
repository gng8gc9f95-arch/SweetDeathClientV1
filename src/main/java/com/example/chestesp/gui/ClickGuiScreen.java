package com.example.chestesp.gui;

import com.example.chestesp.Config;
import com.example.chestesp.feature.SpawnerAlert;
import com.example.chestesp.feature.SusChunks;
import com.example.chestesp.feature.Tunnels;
import com.example.chestesp.module.BoolSetting;
import com.example.chestesp.module.Category;
import com.example.chestesp.module.ColorSetting;
import com.example.chestesp.module.ModeSetting;
import com.example.chestesp.module.Module;
import com.example.chestesp.module.Modules;
import com.example.chestesp.module.NumberSetting;
import com.example.chestesp.module.Setting;
import com.example.chestesp.module.ToggleColorSetting;
import com.example.chestesp.render.EspRenderer;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Das Client-Menue im Stil einer Web-App: Seitenleiste, Suchfeld, Modul-Karten,
 * Detailseiten mit Schaltern, Schiebereglern, Farbwaehlern und freier Tastenbelegung.
 * Alles wird selbst gezeichnet (keine Minecraft-Buttons).
 */
public final class ClickGuiScreen extends Screen {
    private enum Page { ALL, RENDER, WORLD, CLIENT, FINDS, MODULE }

    private static final float TOP = 32, SIDE = 116, RADIUS = 12, PAD = 14;
    private static final float PICKER_H = 96;
    private static final int[] PRESETS = {
            0xFFFF4646, 0xFFFF8A1A, 0xFFFFD23F, 0xFF3DFF6E, 0xFF2FE6E0, 0xFF3AA0FF,
            0xFF8B5CF6, 0xFFFF2DB4, 0xFFFFFFFF, 0xFFA3A9B8, 0xFFC08A4E, 0xFF15161A
    };

    /** Merkt sich die letzte Seite zwischen zwei Oeffnungen (wie eine Website). */
    private static Page lastPage = Page.ALL;
    private static Module lastModule;
    private static Page lastParent = Page.ALL;

    private Page page = Page.ALL;
    private Page parent = Page.ALL;
    private Module module;

    private String search = "";
    private boolean searchFocused;
    private Module listening;
    private ColorSetting openPicker;

    private record Hotspot(float x1, float y1, float x2, float y2, boolean clipped, Runnable click, Drag drag, Object key) {}

    private interface Drag {
        void drag(double mx, double my);
    }

    private final List<Hotspot> hotspots = new ArrayList<>();
    private Drag dragging;
    private Object dragKey;
    private final Map<Object, Float> anims = new HashMap<>();

    private float scroll, scrollTarget, maxScroll;
    private long openedAt, pageAt, lastFrame;
    private float dt = 0.016f;
    private int mouseX, mouseY;
    private boolean inContent;

    // Layout dieses Frames
    private float wx, wy, ww, wh;
    private float vx1, vy1, vx2, vy2;

    public ClickGuiScreen() {
        super(Component.literal("ChestESP Client"));
    }

    @Override
    protected void init() {
        long now = System.currentTimeMillis();
        if (openedAt == 0) {
            openedAt = now;
            page = lastPage;
            parent = lastParent;
            module = lastModule;
            if (page == Page.MODULE && module == null) page = Page.ALL;
            pageAt = now;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        lastPage = page;
        lastParent = parent;
        lastModule = module;
        Config.save();
    }

    // ================================================================== Rendern

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float partialTick) {
        if (Modules.GUI.blur.on()) this.renderBlurredBackground(g);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        long now = System.currentTimeMillis();
        dt = lastFrame == 0 ? 0.016f : Math.max(0.001f, Math.min(0.1f, (now - lastFrame) / 1000f));
        lastFrame = now;
        mouseX = mx;
        mouseY = my;
        hotspots.clear();

        boolean anim = Theme.animations();
        float open = anim ? ease(Math.min(1f, (now - openedAt) / 240f)) : 1f;

        Draw.alpha = open;
        Draw.vGradient(g, 0, 0, this.width, this.height, Theme.OVERLAY_TOP, Theme.OVERLAY_BOTTOM);

        ww = Math.min(640, this.width - 24);
        wh = Math.min(380, this.height - 24);
        wx = (float) Math.floor((this.width - ww) / 2f);
        wy = (float) Math.floor((this.height - wh) / 2f) + (1f - open) * 14f;

        Draw.shadow(g, wx, wy, ww, wh, RADIUS, 22, 0xC0000000);
        Draw.card(g, wx, wy, ww, wh, RADIUS, Theme.WINDOW, Theme.BORDER);
        // dezenter Lichtschein oben
        Draw.roundRect(g, wx + 1, wy + 1, ww - 2, TOP, RADIUS - 1, RADIUS - 1, 0, 0, 0xFF0F1117);

        sidebar(g);
        topbar(g);
        content(g, open, anim, now);

        Draw.alpha = 1f;
    }

    // ------------------------------------------------------------------ Kopfzeile

    private void topbar(GuiGraphics g) {
        float cy = wy + TOP / 2f;
        Draw.hairlineH(g, wx + 1, wy + TOP, ww - 2, Theme.BORDER_SOFT);

        Hud.logo(g, wx + 13, cy - 8, 16, Theme.accent());
        Text.draw(g, "ChestESP", wx + 36, cy, Theme.TEXT, Text.Kind.BOLD);
        float bx = wx + 36 + Text.width("ChestESP", Text.Kind.BOLD) + 6;
        String tag = "CLIENT";
        float tw = Text.width(tag, Text.Kind.SMALL) + 8;
        Draw.roundRect(g, bx, cy - 5, tw, 10, 5, Theme.alpha(Theme.accent(), 0x2A));
        Text.draw(g, tag, bx + 4, cy, Theme.accent(), Text.Kind.SMALL);

        // Suchfeld (bzw. Hinweis beim Tastenbelegen)
        float sx = wx + SIDE + PAD;
        float sw = Math.max(90, Math.min(190, ww - SIDE - PAD * 2 - 120));
        float sh = 18;
        if (listening != null) {
            listeningHint(g, sx, cy, ww - SIDE - PAD - 46);
        } else {
            searchBox(g, sx, cy, sw, sh);
        }

        // Schliessen
        float cx = wx + ww - 18;
        boolean ch = hover(cx - 8, cy - 8, 16, 16);
        if (ch) Draw.circle(g, cx, cy, 8, Theme.INPUT_HOVER);
        Text.drawCentered(g, "\u00d7", cx, cy, ch ? Theme.TEXT : Theme.TEXT_DIM, Text.Kind.TITLE);
        hot(cx - 8, cy - 8, 16, 16, this::onClose);

        // Status
        int active = 0;
        for (Module m : Modules.all()) if (m.toggleable && m.isEnabled()) active++;
        String st = active + " aktiv";
        float stw = Text.width(st, Text.Kind.SMALL) + 18;
        float stx = cx - 14 - stw;
        if (listening == null && stx > sx + sw + 8) {
            Draw.card(g, stx, cy - 7, stw, 14, 7, Theme.INPUT, Theme.BORDER_SOFT);
            Draw.circle(g, stx + 7.5f, cy, 2.4f, Theme.GREEN);
            Draw.circle(g, stx + 7.5f, cy, 4.2f, Theme.alpha(Theme.GREEN, 0x33));
            Text.draw(g, st, stx + 13, cy, Theme.TEXT_DIM, Text.Kind.SMALL);
        }
    }

    private void searchBox(GuiGraphics g, float sx, float cy, float sw, float sh) {
        boolean hov = hover(sx, cy - sh / 2, sw, sh);
        int border = searchFocused ? Theme.accent() : (hov ? Theme.BORDER : Theme.BORDER_SOFT);
        Draw.card(g, sx, cy - sh / 2, sw, sh, sh / 2, hov || searchFocused ? Theme.INPUT_HOVER : Theme.INPUT, border);
        if (searchFocused) Draw.roundRect(g, sx - 2, cy - sh / 2 - 2, sw + 4, sh + 4, sh / 2 + 2, Theme.alpha(Theme.accent(), 0x1C));
        // Lupe
        Draw.ring(g, sx + 11, cy - 0.8f, 3.4f, 1.1f, Theme.TEXT_MUTED);
        Draw.roundRect(g, sx + 13.2f, cy + 1.6f, 2.6f, 1.2f, 0.6f, Theme.TEXT_MUTED);
        float textX = sx + 20;
        if (search.isEmpty() && !searchFocused) {
            Text.draw(g, "Module suchen…", textX, cy, Theme.TEXT_MUTED, Text.Kind.REGULAR);
        } else {
            String shown = search;
            while (!shown.isEmpty() && Text.width(shown, Text.Kind.REGULAR) > sw - 32) shown = shown.substring(1);
            Text.draw(g, shown, textX, cy, Theme.TEXT, Text.Kind.REGULAR);
            if (searchFocused && (System.currentTimeMillis() / 500) % 2 == 0) {
                Draw.rect(g, textX + Text.width(shown, Text.Kind.REGULAR) + 1, cy - 4, 0.75f, 8, Theme.accent());
            }
        }
        if (!search.isEmpty()) {
            float xx = sx + sw - 11;
            boolean xh = hover(xx - 5, cy - 5, 10, 10);
            Text.drawCentered(g, "×", xx, cy, xh ? Theme.TEXT : Theme.TEXT_MUTED, Text.Kind.BOLD);
            hot(xx - 5, cy - 5, 10, 10, () -> {
                search = "";
                click(0.8f);
            });
        }
        hot(sx, cy - sh / 2, sw - (search.isEmpty() ? 0 : 14), sh, () -> searchFocused = true);
    }

    // ------------------------------------------------------------------ Seitenleiste

    private void sidebar(GuiGraphics g) {
        Draw.roundRect(g, wx + 1, wy + TOP + 1, SIDE - 1, wh - TOP - 2, 0, 0, 0, RADIUS - 1, Theme.SIDEBAR);
        Draw.hairlineV(g, wx + SIDE, wy + TOP + 1, wh - TOP - 2, Theme.BORDER_SOFT);

        float x = wx + 8, w = SIDE - 16;
        float y = wy + TOP + 14;
        Text.draw(g, "NAVIGATION", x + 6, y, Theme.TEXT_MUTED, Text.Kind.SMALL);
        y += 9;
        y = navItem(g, x, y, w, "Übersicht", Page.ALL, 0, Modules.all().size());
        y = navItem(g, x, y, w, Category.RENDER.title, Page.RENDER, 1, count(Category.RENDER));
        y = navItem(g, x, y, w, Category.WORLD.title, Page.WORLD, 2, count(Category.WORLD));
        y = navItem(g, x, y, w, Category.CLIENT.title, Page.CLIENT, 3, count(Category.CLIENT));
        y += 10;
        Text.draw(g, "DATEN", x + 6, y, Theme.TEXT_MUTED, Text.Kind.SMALL);
        y += 9;
        navItem(g, x, y, w, "Funde", Page.FINDS, 4, SpawnerAlert.finds().size());

        // Profil unten
        float py = wy + wh - 40;
        Draw.hairlineH(g, wx + 10, py - 6, SIDE - 20, Theme.BORDER_SOFT);
        String name = this.minecraft.getUser().getName();
        float ax = x + 14, ay = py + 11;
        Draw.circle(g, ax, ay, 9, Theme.accent());
        Draw.circle(g, ax - 2, ay - 2, 6, Theme.alpha(0xFFFFFF, 0x22));
        Text.drawCentered(g, name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase(Locale.ROOT), ax, ay, Theme.WHITE, Text.Kind.BOLD);
        Text.draw(g, Text.fit(name, w - 30, Text.Kind.BOLD), ax + 14, ay - 4, Theme.TEXT, Text.Kind.BOLD);
        boolean sp = this.minecraft.isSingleplayer();
        Draw.circle(g, ax + 16, ay + 5.5f, 1.8f, Theme.GREEN);
        Text.draw(g, sp ? "Einzelspieler" : "Mehrspieler", ax + 21, ay + 5.5f, Theme.TEXT_MUTED, Text.Kind.SMALL);
    }

    private int count(Category c) {
        int n = 0;
        for (Module m : Modules.all()) if (m.category == c) n++;
        return n;
    }

    private float navItem(GuiGraphics g, float x, float y, float w, String label, Page target, int icon, int badge) {
        float h = 21;
        boolean active = (page == target && search.isEmpty()) || (page == Page.MODULE && parent == target && search.isEmpty());
        boolean hov = hover(x, y, w, h);
        float t = anim("nav" + target, active ? 1 : (hov ? 0.45f : 0), 16);
        if (t > 0.01f) {
            Draw.roundRect(g, x, y, w, h, 6, Theme.alpha(active ? Theme.accent() : 0xFFFFFF, Math.round((active ? 0x24 : 0x10) * t / (active ? 1f : 0.45f))));
        }
        if (active) Draw.roundRect(g, x, y + 5, 2, h - 10, 1, Theme.accent());
        int col = active ? Theme.TEXT : (hov ? Theme.TEXT : Theme.TEXT_DIM);
        int iconCol = active ? Theme.accent() : (hov ? Theme.TEXT_DIM : Theme.TEXT_MUTED);
        icon(g, icon, x + 13, y + h / 2, iconCol, active ? Theme.lerp(Theme.SIDEBAR, Theme.accent(), 0.14f) : (hov ? 0xFF16181F : Theme.SIDEBAR));
        Text.draw(g, label, x + 24, y + h / 2, col, Text.Kind.REGULAR);
        if (badge > 0) {
            String b = Integer.toString(badge);
            float bw = Math.max(13, Text.width(b, Text.Kind.SMALL) + 7);
            Draw.roundRect(g, x + w - 5 - bw, y + h / 2 - 5, bw, 10, 5, active ? Theme.alpha(Theme.accent(), 0x40) : Theme.INPUT);
            Text.drawCentered(g, b, x + w - 5 - bw / 2, y + h / 2, active ? Theme.TEXT : Theme.TEXT_MUTED, Text.Kind.SMALL);
        }
        hot(x, y, w, h, () -> {
            search = "";
            searchFocused = false;
            if (page != target) {
                openPage(target);
                click(1f);
            }
        });
        return y + h + 2;
    }

    /** Kleine selbstgezeichnete Symbole fuer die Navigation. */
    private void icon(GuiGraphics g, int type, float cx, float cy, int col, int bg) {
        switch (type) {
            case 0 -> { // Raster
                float s = 2.7f, gap = 1.3f;
                for (int i = 0; i < 2; i++)
                    for (int j = 0; j < 2; j++)
                        Draw.roundRect(g, cx - s - gap / 2 + i * (s + gap), cy - s - gap / 2 + j * (s + gap), s, s, 0.8f, col);
            }
            case 1 -> { // Auge
                Draw.ring(g, cx, cy, 4.2f, 1.1f, col);
                Draw.circle(g, cx, cy, 1.7f, col);
            }
            case 2 -> { // Globus
                Draw.ring(g, cx, cy, 4.3f, 1.1f, col);
                Draw.rect(g, cx - 4, cy - 0.5f, 8, 1, col);
                Draw.rect(g, cx - 0.5f, cy - 4, 1, 8, col);
            }
            case 3 -> { // Regler
                for (int i = 0; i < 3; i++) {
                    float yy = cy - 3 + i * 3;
                    Draw.roundRect(g, cx - 4.5f, yy - 0.5f, 9, 1, 0.5f, col);
                    float kx = cx + (i == 0 ? 1.5f : i == 1 ? -2f : 0.5f);
                    Draw.circle(g, kx, yy, 1.5f, col);
                }
            }
            default -> { // Liste
                for (int i = 0; i < 3; i++) {
                    float yy = cy - 3 + i * 3;
                    Draw.circle(g, cx - 3.5f, yy, 0.9f, col);
                    Draw.roundRect(g, cx - 1.5f, yy - 0.5f, 6, 1, 0.5f, col);
                }
            }
        }
    }

    // ------------------------------------------------------------------ Inhalt

    private void content(GuiGraphics g, float open, boolean anim, long now) {
        vx1 = wx + SIDE + 1;
        vy1 = wy + TOP + 1;
        vx2 = wx + ww - 1;
        vy2 = wy + wh - 1;
        Draw.roundRect(g, vx1, vy1, vx2 - vx1, vy2 - vy1, 0, 0, RADIUS - 1, 0, Theme.CONTENT);

        scroll += (scrollTarget - scroll) * Math.min(1f, dt * 16f);
        if (Math.abs(scrollTarget - scroll) < 0.05f) scroll = scrollTarget;

        float pageT = anim ? ease(Math.min(1f, (now - pageAt) / 200f)) : 1f;
        float savedAlpha = Draw.alpha;
        Draw.alpha = open * pageT;

        g.enableScissor((int) Math.ceil(vx1), (int) Math.ceil(vy1), (int) Math.floor(vx2), (int) Math.floor(vy2));
        inContent = true;
        float x = vx1 + PAD, w = vx2 - vx1 - PAD * 2;
        float y0 = vy1 + PAD - scroll + (1f - pageT) * 8f;
        float end;
        if (!search.isEmpty()) end = searchPage(g, x, y0, w);
        else end = switch (page) {
            case ALL -> overviewPage(g, x, y0, w);
            case RENDER -> categoryPage(g, x, y0, w, Category.RENDER);
            case WORLD -> categoryPage(g, x, y0, w, Category.WORLD);
            case CLIENT -> categoryPage(g, x, y0, w, Category.CLIENT);
            case FINDS -> findsPage(g, x, y0, w);
            case MODULE -> modulePage(g, x, y0, w);
        };
        inContent = false;
        g.disableScissor();
        Draw.alpha = savedAlpha;

        float total = end + scroll - (vy1 + PAD) + PAD;
        float view = vy2 - vy1;
        maxScroll = Math.max(0, total - view);
        if (scrollTarget > maxScroll) scrollTarget = maxScroll;
        if (scrollTarget < 0) scrollTarget = 0;

        if (maxScroll > 0) {
            float trackH = view - 12;
            float thumbH = Math.max(18, trackH * view / total);
            float ty = vy1 + 6 + (trackH - thumbH) * (scroll / maxScroll);
            Draw.roundRect(g, vx2 - 5, ty, 2.5f, thumbH, 1.25f, Theme.alpha(0xFFFFFF, 0x30));
        }

    }

    private float header(GuiGraphics g, float x, float y, float w, String title, String subtitle) {
        Text.draw(g, title, x, y + 7, Theme.TEXT, Text.Kind.TITLE);
        if (subtitle != null) Text.draw(g, subtitle, x, y + 21, Theme.TEXT_MUTED, Text.Kind.REGULAR);
        return y + (subtitle != null ? 34 : 22);
    }

    private float section(GuiGraphics g, float x, float y, String label, String extra) {
        Text.draw(g, label, x + 1, y + 4, Theme.TEXT_MUTED, Text.Kind.SMALL);
        if (extra != null) {
            float lw = Text.width(label, Text.Kind.SMALL);
            Text.draw(g, extra, x + lw + 7, y + 4, Theme.alpha(Theme.TEXT_MUTED, 0x99), Text.Kind.SMALL);
        }
        return y + 12;
    }

    private float overviewPage(GuiGraphics g, float x, float y, float w) {
        // Hero-Karte
        float h = 58;
        int accent = Theme.accent();
        Draw.card(g, x, y, w, h, 10, Theme.CARD, Theme.BORDER_SOFT);
        Draw.roundRect(g, x + 1, y + 1, w - 2, h - 2, 9, Theme.alpha(accent, 0x12));
        g.enableScissor((int) Math.ceil(x + 1), (int) Math.ceil(Math.max(y + 1, vy1)), (int) (x + w - 1), (int) Math.min(y + h - 1, vy2));
        Draw.blob(g, x + w - 52, y + h * 0.5f, 34, Theme.alpha(accent, 0x22));
        Draw.blob(g, x + w - 26, y + h * 0.2f, 20, Theme.alpha(Theme.accent2(), 0x1E));
        Draw.blob(g, x + w - 84, y + h * 1.05f, 18, Theme.alpha(Theme.accent2(), 0x14));
        g.disableScissor();
        Draw.outline(g, x, y, w, h, 10, Theme.BORDER_SOFT);

        int active = 0, toggleable = 0;
        for (Module m : Modules.all()) {
            if (!m.toggleable) continue;
            toggleable++;
            if (m.isEnabled()) active++;
        }
        String name = this.minecraft.getUser().getName();
        Text.draw(g, Text.fit("Willkommen zurück, " + name, w - 100, Text.Kind.TITLE), x + 14, y + 20, Theme.TEXT, Text.Kind.TITLE);
        Text.draw(g, active + " von " + toggleable + " Modulen aktiv  ·  " + keyName(Modules.GUI) + " öffnet das Menü",
                x + 14, y + 38, Theme.TEXT_DIM, Text.Kind.REGULAR);
        y += h + 16;

        for (Category c : Category.values()) {
            List<Module> list = new ArrayList<>();
            for (Module m : Modules.all()) if (m.category == c) list.add(m);
            y = section(g, x, y, c.title.toUpperCase(Locale.ROOT), Integer.toString(list.size()));
            y = grid(g, x, y, w, list) + 12;
        }
        return y - 12;
    }

    private float categoryPage(GuiGraphics g, float x, float y, float w, Category c) {
        List<Module> list = new ArrayList<>();
        int on = 0;
        for (Module m : Modules.all()) {
            if (m.category != c) continue;
            list.add(m);
            if (m.toggleable && m.isEnabled()) on++;
        }
        y = header(g, x, y, w, c.title, c.subtitle + "  ·  " + on + " aktiv");
        return grid(g, x, y, w, list);
    }

    private float searchPage(GuiGraphics g, float x, float y, float w) {
        String q = search.toLowerCase(Locale.ROOT);
        List<Module> list = new ArrayList<>();
        for (Module m : Modules.all()) {
            if (m.name.toLowerCase(Locale.ROOT).contains(q) || m.description.toLowerCase(Locale.ROOT).contains(q)
                    || m.category.title.toLowerCase(Locale.ROOT).contains(q)) list.add(m);
        }
        y = header(g, x, y, w, "Suche", list.size() + " Treffer für „" + search + "“");
        if (list.isEmpty()) {
            Draw.card(g, x, y, w, 50, 10, Theme.CARD, Theme.BORDER_SOFT);
            Text.drawCentered(g, "Kein Modul gefunden", x + w / 2, y + 25, Theme.TEXT_MUTED, Text.Kind.REGULAR);
            return y + 50;
        }
        return grid(g, x, y, w, list);
    }

    // ------------------------------------------------------------------ Modulkarten

    private float grid(GuiGraphics g, float x, float y, float w, List<Module> list) {
        int cols = w >= 340 ? 2 : 1;
        float gap = 10;
        float cw = (w - gap * (cols - 1)) / cols, ch = 72;
        for (int i = 0; i < list.size(); i++) {
            int col = i % cols, row = i / cols;
            moduleCard(g, list.get(i), x + col * (cw + gap), y + row * (ch + gap), cw, ch);
        }
        int rows = (list.size() + cols - 1) / cols;
        return y + rows * (ch + gap) - gap;
    }

    private void moduleCard(GuiGraphics g, Module m, float x, float y, float w, float h) {
        if (y > vy2 || y + h < vy1) return;
        hot(x, y, w, h, () -> openModule(m));
        int accent = m.accentColor();
        boolean hov = hover(x, y, w, h);
        float ht = anim(m.id + "card", hov ? 1 : 0, 14);
        Draw.card(g, x, y, w, h, 9, Theme.lerp(Theme.CARD, Theme.CARD_HOVER, ht),
                Theme.lerp(Theme.BORDER_SOFT, Theme.alpha(accent, 0x70), ht));

        float on = m.toggleable ? anim(m.id + "on", m.isEnabled() ? 1 : 0, 12) : 0;
        if (on > 0.01f) {
            Draw.hGradient(g, x + 10, y + 1, w * 0.55f, 1, Theme.alpha(accent, Math.round(0xC0 * on)), Theme.alpha(accent, 0));
        }

        // Symbol
        Draw.roundRect(g, x + 10, y + 10, 24, 24, 6, Theme.alpha(accent, 0x24));
        Draw.item(g, m.iconStack(), x + 14, y + 14, 16);

        Text.draw(g, Text.fit(m.name, w - 90, Text.Kind.BOLD), x + 42, y + 17, Theme.TEXT, Text.Kind.BOLD);
        Text.draw(g, m.category.title.toUpperCase(Locale.ROOT), x + 42, y + 28, Theme.TEXT_MUTED, Text.Kind.SMALL);
        Text.draw(g, Text.fit(m.description, w - 20, Text.Kind.REGULAR), x + 10, y + 46, Theme.TEXT_DIM, Text.Kind.REGULAR);

        // Schalter
        if (m.toggleable) {
            float sx = x + w - 10 - 24, sy = y + 12;
            switchWidget(g, m.id + "sw", sx, sy, 24, 13, m.isEnabled(), accent);
            hot(sx - 3, sy - 3, 30, 19, () -> toggleModule(m));
        } else {
            String open = "Öffnen";
            Text.drawRight(g, open, x + w - 10, y + 18, Theme.TEXT_MUTED, Text.Kind.SMALL);
        }

        // Taste
        float kx = x + 10, ky = y + h - 13;
        float kw = keyChip(g, m, kx, ky);
        hot(kx, ky - 6.5f, kw, 13, () -> startListening(m));

        String more = m.settings().isEmpty() ? "" : "Einstellungen ›";
        if (!more.isEmpty()) {
            Text.drawRight(g, more, x + w - 10, ky, hov ? accent : Theme.TEXT_MUTED, Text.Kind.SMALL);
        }
    }

    // ------------------------------------------------------------------ Modulseite

    private float modulePage(GuiGraphics g, float x, float y, float w) {
        Module m = module;
        if (m == null) return overviewPage(g, x, y, w);
        int accent = m.accentColor();

        // Zurueck + Pfad
        String back = "‹  Zurück";
        float bw = Text.width(back, Text.Kind.SMALL) + 14;
        boolean bh = hover(x, y, bw, 14);
        Draw.card(g, x, y, bw, 14, 7, bh ? Theme.INPUT_HOVER : Theme.INPUT, Theme.BORDER_SOFT);
        Text.draw(g, back, x + 7, y + 7, bh ? Theme.TEXT : Theme.TEXT_DIM, Text.Kind.SMALL);
        hot(x, y, bw, 14, () -> {
            openPage(parent == Page.MODULE ? Page.ALL : parent);
            click(0.9f);
        });
        String crumb = m.category.title + "  /  " + m.name;
        Text.draw(g, crumb, x + bw + 8, y + 7, Theme.TEXT_MUTED, Text.Kind.SMALL);
        y += 22;

        // Kopf-Karte
        float h = 64;
        Draw.card(g, x, y, w, h, 10, Theme.CARD, Theme.BORDER_SOFT);
        Draw.roundRect(g, x + 1, y + 1, w - 2, h - 2, 9, Theme.alpha(accent, 0x0E));
        Draw.roundRect(g, x + 12, y + 12, 40, 40, 10, Theme.alpha(accent, 0x2A));
        Draw.item(g, m.iconStack(), x + 20, y + 20, 24);
        Text.draw(g, Text.fit(m.name, w - 170, Text.Kind.TITLE), x + 62, y + 24, Theme.TEXT, Text.Kind.TITLE);
        Text.draw(g, Text.fit(m.description, w - 170, Text.Kind.REGULAR), x + 62, y + 41, Theme.TEXT_DIM, Text.Kind.REGULAR);

        float rx = x + w - 12;
        if (m.toggleable) {
            float sx = rx - 30, sy = y + 14;
            switchWidget(g, m.id + "sw", sx, sy, 30, 16, m.isEnabled(), accent);
            hot(sx - 3, sy - 3, 36, 22, () -> toggleModule(m));
            Text.drawRight(g, m.isEnabled() ? "Aktiv" : "Inaktiv", sx - 7, sy + 8,
                    m.isEnabled() ? Theme.TEXT : Theme.TEXT_MUTED, Text.Kind.SMALL);
        }
        float kw = keyChipWidth(m);
        float kx = rx - kw, ky = y + h - 17;
        keyChip(g, m, kx, ky);
        hot(kx, ky - 6.5f, kw, 13, () -> startListening(m));
        Text.drawRight(g, "Taste", kx - 7, ky, Theme.TEXT_MUTED, Text.Kind.SMALL);
        y += h + 14;

        // Einstellungen
        List<Setting<?>> visible = new ArrayList<>();
        for (Setting<?> s : m.settings()) if (s.isVisible()) visible.add(s);
        if (visible.isEmpty()) return y - 14;

        y = section(g, x, y, "EINSTELLUNGEN", Integer.toString(visible.size()));
        float total = 0;
        for (Setting<?> s : visible) total += rowHeight(s);
        Draw.card(g, x, y, w, total, 10, Theme.CARD, Theme.BORDER_SOFT);
        float ry = y;
        boolean first = true;
        for (Setting<?> s : visible) {
            float rh = rowHeight(s);
            if (!first) Draw.hairlineH(g, x + 12, ry, w - 24, Theme.BORDER_SOFT);
            first = false;
            if (ry + rh >= vy1 && ry <= vy2) settingRow(g, m, s, x, ry, w, accent);
            ry += rh;
        }
        y = ry + 10;

        String reset = "Alle Einstellungen zurücksetzen";
        float rw = Text.width(reset, Text.Kind.SMALL);
        boolean rh = hover(x + w - rw - 2, y - 2, rw + 4, 12);
        Text.drawRight(g, reset, x + w, y + 4, rh ? Theme.RED : Theme.TEXT_MUTED, Text.Kind.SMALL);
        hot(x + w - rw - 2, y - 2, rw + 4, 12, () -> {
            for (Setting<?> s : m.settings()) s.reset();
            click(0.7f);
        });
        return y + 10;
    }

    private float rowHeight(Setting<?> s) {
        boolean d = !s.description.isEmpty();
        if (s instanceof NumberSetting) return d ? 44 : 36;
        float h = d ? 34 : 28;
        if (s instanceof ColorSetting && openPicker == s) h += PICKER_H;
        return h;
    }

    private void settingRow(GuiGraphics g, Module m, Setting<?> s, float x, float y, float w, int accent) {
        boolean d = !s.description.isEmpty();
        float baseH = s instanceof NumberSetting ? (d ? 44 : 36) : (d ? 34 : 28);
        float labelCy = y + (d ? 12 : (s instanceof NumberSetting ? 12 : 14));
        float rightCy = s instanceof NumberSetting ? labelCy : y + baseH / 2;
        float lx = x + 14, rx = x + w - 14;

        Text.draw(g, s.name, lx, labelCy, Theme.TEXT, Text.Kind.REGULAR);
        if (d) Text.draw(g, Text.fit(s.description, w * 0.55f, Text.Kind.SMALL), lx, labelCy + 10, Theme.TEXT_MUTED, Text.Kind.SMALL);

        if (s instanceof ToggleColorSetting tc) {
            float sx = rx - 24;
            switchWidget(g, tc, sx, rightCy - 6.5f, 24, 13, tc.on(), tc.opaque());
            float swx = sx - 10 - 26;
            hot(x, y, w, baseH, () -> {
                tc.toggle();
                click(tc.on() ? 1.2f : 0.9f);
            });
            swatch(g, tc, swx, rightCy - 7, 26, 14);
            hot(swx - 2, rightCy - 9, 30, 18, () -> togglePicker(tc));
            if (openPicker == tc) picker(g, tc, x, y + baseH, w);
        } else if (s instanceof ColorSetting c) {
            float swx = rx - 26;
            swatch(g, c, swx, rightCy - 7, 26, 14);
            Text.drawRight(g, c.hex(), swx - 8, rightCy, Theme.TEXT_MUTED, Text.Kind.SMALL);
            hot(x, y, w, baseH, () -> togglePicker(c));
            if (openPicker == c) picker(g, c, x, y + baseH, w);
        } else if (s instanceof BoolSetting b) {
            switchWidget(g, b, rx - 24, rightCy - 6.5f, 24, 13, b.on(), accent);
            hot(x, y, w, baseH, () -> {
                b.toggle();
                click(b.on() ? 1.2f : 0.9f);
            });
        } else if (s instanceof NumberSetting n) {
            Text.drawRight(g, n.display(), rx, labelCy, Theme.TEXT, Text.Kind.BOLD);
            float ty = y + baseH - 11;
            float tx1 = lx, tw = rx - lx;
            float t = (float) n.normalized();
            float shown = dragKey == n ? t : anim(n, t, 22);
            if (dragKey == n) anims.put(n, t);
            Draw.roundRect(g, tx1, ty - 2, tw, 4, 2, Theme.TRACK);
            Draw.roundRect(g, tx1, ty - 2, Math.max(4, tw * shown), 4, 2, accent);
            boolean active = dragKey == n;
            boolean hov = hover(tx1 - 6, ty - 8, tw + 12, 16);
            float kx = tx1 + tw * shown;
            if (hov || active) Draw.circle(g, kx, ty, 8, Theme.alpha(accent, 0x30));
            Draw.circle(g, kx, ty, 5, Theme.WHITE);
            Draw.circle(g, kx, ty, 2.2f, accent);
            hotDrag(n, tx1 - 6, ty - 8, tw + 12, 16, (mx, my) -> n.setNormalized((mx - tx1) / tw));
        } else if (s instanceof ModeSetting mode) {
            modeControl(g, mode, rx, rightCy, w * 0.5f, accent);
        }
    }

    private void modeControl(GuiGraphics g, ModeSetting mode, float right, float cy, float maxW, int accent) {
        float pad = 8, h = 17;
        float[] widths = new float[mode.modes.length];
        float total = 4;
        for (int i = 0; i < widths.length; i++) {
            widths[i] = Text.width(mode.modes[i], Text.Kind.SMALL) + pad * 2;
            total += widths[i];
        }
        if (total > maxW) {
            // zu breit: einfacher Umschalter
            String label = "‹  " + mode.mode() + "  ›";
            float lw = Text.width(label, Text.Kind.SMALL) + 16;
            boolean hov = hover(right - lw, cy - h / 2, lw, h);
            Draw.card(g, right - lw, cy - h / 2, lw, h, h / 2, hov ? Theme.INPUT_HOVER : Theme.INPUT, Theme.BORDER_SOFT);
            Text.drawCentered(g, label, right - lw / 2, cy, Theme.TEXT, Text.Kind.SMALL);
            hot(right - lw, cy - h / 2, lw, h, () -> {
                mode.cycle();
                click(1f);
            });
            return;
        }
        float x = right - total;
        Draw.card(g, x, cy - h / 2, total, h, h / 2, Theme.INPUT, Theme.BORDER_SOFT);
        float sel = 2;
        for (int i = 0; i < mode.index(); i++) sel += widths[i];
        float animX = anim(mode, sel, 18);
        float animW = anim(mode.id + "w" + System.identityHashCode(mode), widths[mode.index()], 18);
        Draw.roundRect(g, x + animX, cy - h / 2 + 2, animW, h - 4, (h - 4) / 2, accent);
        float cx = x + 2;
        for (int i = 0; i < widths.length; i++) {
            boolean selected = mode.index() == i;
            boolean hov = hover(cx, cy - h / 2, widths[i], h);
            int col = selected ? (Theme.luminance(accent) > 0.7f ? 0xFF101114 : Theme.WHITE) : (hov ? Theme.TEXT : Theme.TEXT_DIM);
            Text.drawCentered(g, mode.modes[i], cx + widths[i] / 2, cy, col, Text.Kind.SMALL);
            final int idx = i;
            hot(cx, cy - h / 2, widths[i], h, () -> {
                if (mode.index() != idx) click(1f);
                mode.set(idx);
            });
            cx += widths[i];
        }
    }

    // ------------------------------------------------------------------ Farbwaehler

    private void swatch(GuiGraphics g, ColorSetting c, float x, float y, float w, float h) {
        boolean hov = hover(x, y, w, h);
        Draw.card(g, x - 1, y - 1, w + 2, h + 2, 5, 0x00000000, openPicker == c ? Theme.accent() : (hov ? 0xFF3A3F4C : Theme.BORDER));
        if (c.alpha && c.a() < 0.99f) {
            g.enableScissor((int) x, (int) Math.max(y, vy1), (int) Math.ceil(x + w), (int) Math.min(Math.ceil(y + h), vy2));
            Draw.checker(g, x, y, w, h, 3.5f);
            g.disableScissor();
            // linke Haelfte deckend, rechte mit Transparenz
            Draw.roundRect(g, x, y, w / 2, h, 4, 0, 0, 4, c.opaque());
            Draw.roundRect(g, x + w / 2, y, w / 2, h, 0, 4, 4, 0, c.argb());
        } else {
            Draw.roundRect(g, x, y, w, h, 4, c.opaque());
        }
    }

    private void togglePicker(ColorSetting c) {
        openPicker = openPicker == c ? null : c;
        click(1f);
    }

    private void picker(GuiGraphics g, ColorSetting c, float x, float y, float w) {
        float px = x + 14, py = y + 4;
        float h = PICKER_H - 16;
        Draw.card(g, x + 8, y - 2, w - 16, PICKER_H - 6, 8, 0xFF0E1015, Theme.BORDER_SOFT);
        px += 4;
        py += 4;
        h -= 4;

        // Saettigung/Helligkeit
        float svW = Math.min(160, (w - 40) * 0.45f);
        int hueRgb = 0xFF000000 | ColorSetting.fromHsv(c.hue(), 1f, 1f);
        int cols = (int) Math.max(8, svW);
        float colW = svW / cols;
        for (int i = 0; i < cols; i++) {
            float t = (i + 0.5f) / cols;
            int top = Theme.lerp(0xFFFFFFFF, hueRgb, t);
            Draw.vGradient(g, px + i * colW, py, colW + 0.05f, h, top, 0xFF000000);
        }
        float mx = px + c.sat() * svW, my = py + (1 - c.val()) * h;
        Draw.ring(g, mx, my, 4.5f, 1.5f, Theme.WHITE);
        Draw.ring(g, mx, my, 5.5f, 1f, 0x80000000);
        final float fpx = px, fpy = py, fh = h, fsv = svW;
        hotDrag(c, px, py, svW, h, (ax, ay) -> c.setHsv(c.hue(), (float) ((ax - fpx) / fsv), 1f - (float) ((ay - fpy) / fh)));

        // Farbton
        float hx = px + svW + 10, barW = 10;
        int steps = (int) Math.max(12, h);
        for (int i = 0; i < steps; i++) {
            float t = (i + 0.5f) / steps;
            Draw.rect(g, hx, py + i * h / steps, barW, h / steps + 0.05f, 0xFF000000 | ColorSetting.fromHsv(t, 1f, 1f));
        }
        float hy = py + c.hue() * h;
        Draw.roundRect(g, hx - 1.5f, hy - 2, barW + 3, 4, 1.5f, Theme.WHITE);
        Draw.roundRect(g, hx - 0.5f, hy - 1, barW + 1, 2, 1, 0xFF000000 | ColorSetting.fromHsv(c.hue(), 1f, 1f));
        hotDrag(c, hx - 2, py, barW + 4, h, (ax, ay) -> c.setHsv((float) Math.max(0, Math.min(0.9999, (ay - fpy) / fh)), c.sat(), c.val()));

        float nx = hx + barW + 10;
        // Deckkraft
        if (c.alpha) {
            g.enableScissor((int) nx, (int) Math.max(py, vy1), (int) Math.ceil(nx + barW), (int) Math.min(Math.ceil(py + h), vy2));
            Draw.checker(g, nx, py, barW, h, 2.5f);
            g.disableScissor();
            Draw.vGradient(g, nx, py, barW, h, c.opaque(), c.opaque() & 0x00FFFFFF);
            float ay0 = py + (1 - c.a()) * h;
            Draw.roundRect(g, nx - 1.5f, ay0 - 2, barW + 3, 4, 1.5f, Theme.WHITE);
            hotDrag(c, nx - 2, py, barW + 4, h, (ax, ay) -> c.setAlpha(1f - (float) ((ay - fpy) / fh)));
            nx += barW + 10;
        }

        // Vorlagen + Werte
        float rest = x + w - 22 - nx;
        int perRow = Math.max(3, Math.min(6, (int) (rest / 17)));
        float sw = 13;
        for (int i = 0; i < PRESETS.length; i++) {
            float sx = nx + (i % perRow) * 17, sy = py + (i / perRow) * 17;
            if (i / perRow > 2) break;
            boolean hov = hover(sx, sy, sw, sw);
            boolean same = (c.argb() & 0xFFFFFF) == (PRESETS[i] & 0xFFFFFF);
            if (same || hov) Draw.roundRect(g, sx - 1.5f, sy - 1.5f, sw + 3, sw + 3, 5, same ? Theme.WHITE : 0xFF3A3F4C);
            Draw.roundRect(g, sx, sy, sw, sw, 4, PRESETS[i]);
            final int preset = PRESETS[i];
            hot(sx, sy, sw, sw, () -> {
                c.set((c.argb() & 0xFF000000) | (preset & 0xFFFFFF));
                click(1.1f);
            });
        }
        float infoY = py + h - 4;
        Text.draw(g, c.hex(), nx, infoY - 11, Theme.TEXT, Text.Kind.BOLD);
        String extra = c.alpha ? "Deckkraft " + Math.round(c.a() * 100) + " %" : "";
        String reset = "Standard";
        Text.draw(g, extra, nx, infoY, Theme.TEXT_MUTED, Text.Kind.SMALL);
        float rw = Text.width(reset, Text.Kind.SMALL);
        float rsx = x + w - 22 - rw;
        boolean rh = hover(rsx - 2, infoY - 5, rw + 4, 10);
        Text.draw(g, reset, rsx, infoY, rh ? Theme.accent() : Theme.TEXT_MUTED, Text.Kind.SMALL);
        hot(rsx - 2, infoY - 5, rw + 4, 10, () -> {
            c.reset();
            click(0.8f);
        });
    }

    // ------------------------------------------------------------------ Funde

    private float findsPage(GuiGraphics g, float x, float y, float w) {
        y = header(g, x, y, w, "Funde", "Live aus deiner Umgebung");

        // Kennzahlen
        int cols = w >= 360 ? 4 : 2;
        float gap = 10, tw = (w - gap * (cols - 1)) / cols, th = 50;
        String[] labels = {"Spawner", "Sus Chunks", "Tunnel", "ESP-Blöcke"};
        int[] values = {SpawnerAlert.finds().size(), SusChunks.entries().size(), Tunnels.boxes().size(), EspRenderer.lastEspCount};
        int[] colors = {Modules.SPAWNER.accentColor(), Modules.SUS.accentColor(), Modules.TUNNEL.accentColor(), Modules.ESP.accentColor()};
        boolean[] on = {Modules.SPAWNER.isEnabled(), Modules.SUS.isEnabled(), Modules.TUNNEL.isEnabled(), Modules.ESP.isEnabled()};
        for (int i = 0; i < 4; i++) {
            float tx = x + (i % cols) * (tw + gap), ty = y + (i / cols) * (th + gap);
            Draw.card(g, tx, ty, tw, th, 10, Theme.CARD, Theme.BORDER_SOFT);
            Draw.circle(g, tx + 14, ty + 14, 3, on[i] ? colors[i] : Theme.TEXT_MUTED);
            Text.draw(g, labels[i], tx + 22, ty + 14, Theme.TEXT_DIM, Text.Kind.SMALL);
            Text.draw(g, Integer.toString(values[i]), tx + 12, ty + 34, Theme.TEXT, Text.Kind.TITLE);
            if (!on[i]) Text.drawRight(g, "aus", tx + tw - 12, ty + 34, Theme.TEXT_MUTED, Text.Kind.SMALL);
        }
        y += ((4 + cols - 1) / cols) * (th + gap) + 6;

        // Spawner-Liste
        List<SpawnerAlert.Find> finds = SpawnerAlert.finds();
        y = section(g, x, y, "SPAWNER", finds.isEmpty() ? null : Integer.toString(finds.size()));
        if (!finds.isEmpty()) {
            String clear = "Liste leeren";
            float cw = Text.width(clear, Text.Kind.SMALL);
            boolean ch = hover(x + w - cw - 2, y - 14, cw + 4, 12);
            Text.drawRight(g, clear, x + w, y - 8, ch ? Theme.RED : Theme.TEXT_MUTED, Text.Kind.SMALL);
            hot(x + w - cw - 2, y - 14, cw + 4, 12, () -> {
                SpawnerAlert.clearFinds();
                click(0.8f);
            });
        }
        float rowH = 30;
        if (finds.isEmpty()) {
            y = emptyCard(g, x, y, w, "Noch keine Spawner gefunden", Modules.SPAWNER.isEnabled()
                    ? "Sobald einer in " + Modules.SPAWNER.radius.intValue() + " Blöcken ist, erscheint er hier."
                    : "Spawner-Alarm ist ausgeschaltet.");
        } else {
            Draw.card(g, x, y, w, rowH * finds.size(), 10, Theme.CARD, Theme.BORDER_SOFT);
            float ry = y;
            for (int i = 0; i < finds.size(); i++) {
                if (i > 0) Draw.hairlineH(g, x + 12, ry, w - 24, Theme.BORDER_SOFT);
                if (ry + rowH >= vy1 && ry <= vy2) findRow(g, finds.get(i), x, ry, w, rowH);
                ry += rowH;
            }
            y = ry;
        }
        y += 14;

        // Sus Chunks
        List<SusChunks.Entry> sus = SusChunks.entries();
        y = section(g, x, y, "SUS CHUNKS", sus.isEmpty() ? null : Integer.toString(sus.size()));
        if (sus.isEmpty()) {
            y = emptyCard(g, x, y, w, "Keine Sus Chunks in Reichweite",
                    Modules.SUS.isEnabled() ? "Chunks mit vielen Spieler-Blöcken tauchen hier auf." : "Sus Chunks ist ausgeschaltet.");
        } else {
            float sh = 26;
            Draw.card(g, x, y, w, sh * sus.size(), 10, Theme.CARD, Theme.BORDER_SOFT);
            float ry = y;
            int accent = Modules.SUS.accentColor();
            for (int i = 0; i < sus.size(); i++) {
                SusChunks.Entry e = sus.get(i);
                if (i > 0) Draw.hairlineH(g, x + 12, ry, w - 24, Theme.BORDER_SOFT);
                if (ry + sh >= vy1 && ry <= vy2) {
                    float cy = ry + sh / 2;
                    Draw.roundRect(g, x + 12, cy - 5, 10, 10, 2.5f, Theme.alpha(accent, 0x55));
                    Draw.card(g, x + 12, cy - 5, 10, 10, 2.5f, 0x00000000, accent);
                    Text.draw(g, "Chunk " + e.cx() + " / " + e.cz(), x + 30, cy, Theme.TEXT, Text.Kind.BOLD);
                    String pos = "X " + (e.cx() * 16 + 8) + "   Z " + (e.cz() * 16 + 8) + "   Y " + e.minY() + "–" + e.maxY();
                    Text.draw(g, pos, x + 30 + Math.max(80, Text.width("Chunk " + e.cx() + " / " + e.cz(), Text.Kind.BOLD) + 12), cy,
                            Theme.TEXT_MUTED, Text.Kind.SMALL);
                    String score = "Score " + Math.round(e.score());
                    float sw = Text.width(score, Text.Kind.SMALL) + 12;
                    Draw.roundRect(g, x + w - 12 - sw, cy - 6, sw, 12, 6, Theme.alpha(accent, 0x2A));
                    Text.drawCentered(g, score, x + w - 12 - sw / 2, cy, accent, Text.Kind.SMALL);
                }
                ry += sh;
            }
            y = ry;
        }
        return y;
    }

    private float emptyCard(GuiGraphics g, float x, float y, float w, String title, String sub) {
        float h = 46;
        Draw.card(g, x, y, w, h, 10, Theme.CARD, Theme.BORDER_SOFT);
        Text.drawCentered(g, title, x + w / 2, y + 17, Theme.TEXT_DIM, Text.Kind.BOLD);
        Text.drawCentered(g, sub, x + w / 2, y + 30, Theme.TEXT_MUTED, Text.Kind.SMALL);
        return y + h;
    }

    private void findRow(GuiGraphics g, SpawnerAlert.Find f, float x, float y, float w, float h) {
        float cy = y + h / 2;
        int accent = Modules.SPAWNER.accentColor();
        Draw.roundRect(g, x + 10, cy - 10, 20, 20, 6, Theme.alpha(accent, 0x22));
        Draw.item(g, f.icon(), x + 12, cy - 8, 16);
        if (!f.egg().isEmpty()) Draw.item(g, f.egg(), x + 22, cy + 1, 9);
        Text.draw(g, Text.fit(f.title(), w * 0.4f, Text.Kind.BOLD), x + 38, cy - 4.5f, Theme.TEXT, Text.Kind.BOLD);
        BlockPos p = f.pos();
        Text.draw(g, "X " + p.getX() + "   Y " + p.getY() + "   Z " + p.getZ(), x + 38, cy + 5.5f, Theme.TEXT_MUTED, Text.Kind.SMALL);

        String ago = ago(System.currentTimeMillis() - f.time());
        Text.drawRight(g, ago, x + w - 12, cy + 5.5f, Theme.TEXT_MUTED, Text.Kind.SMALL);
        if (this.minecraft.player != null) {
            double dx = p.getX() + 0.5 - this.minecraft.player.getX();
            double dy = p.getY() + 0.5 - this.minecraft.player.getY();
            double dz = p.getZ() + 0.5 - this.minecraft.player.getZ();
            String dist = Math.round(Math.sqrt(dx * dx + dy * dy + dz * dz)) + " m";
            Text.drawRight(g, dist, x + w - 12, cy - 4.5f, Theme.TEXT, Text.Kind.BOLD);
        }
    }

    private static String ago(long ms) {
        long s = ms / 1000;
        if (s < 5) return "gerade eben";
        if (s < 60) return "vor " + s + " s";
        long m = s / 60;
        if (m < 60) return "vor " + m + " min";
        return "vor " + (m / 60) + " h";
    }

    // ------------------------------------------------------------------ Bausteine

    private void switchWidget(GuiGraphics g, Object key, float x, float y, float w, float h, boolean on, int accent) {
        float t = anim(key, on ? 1 : 0, 14);
        boolean hov = hover(x, y, w, h);
        int track = Theme.lerp(hov ? 0xFF30343F : Theme.TRACK, accent, t);
        Draw.roundRect(g, x, y, w, h, h / 2, track);
        float r = h / 2 - 2;
        float kx = x + h / 2 + (w - h) * t;
        // bei sehr hellen Akzentfarben dunkler Knopf, damit er sichtbar bleibt
        int knob = Theme.lerp(Theme.WHITE, 0xFF15161B, t * Math.max(0f, (Theme.luminance(accent) - 0.72f) / 0.28f));
        Draw.circle(g, kx, y + h / 2, r, knob);
    }

    private float keyChipWidth(Module m) {
        String text = listening == m ? "Taste drücken…" : keyName(m);
        return Math.max(22, Text.width(text, Text.Kind.SMALL) + 14);
    }

    /** Zeichnet die Tastenanzeige eines Moduls und gibt ihre Breite zurueck (cy = Mitte). */
    private float keyChip(GuiGraphics g, Module m, float x, float cy) {
        boolean l = listening == m;
        String text = l ? "Taste drücken…" : keyName(m);
        float w = keyChipWidth(m), h = 13;
        boolean hov = hover(x, cy - h / 2, w, h);
        int border;
        if (l) {
            float pulse = 0.55f + 0.45f * (float) Math.sin(System.currentTimeMillis() / 140.0);
            border = Theme.mulAlpha(Theme.accent(), pulse);
            Draw.roundRect(g, x - 2, cy - h / 2 - 2, w + 4, h + 4, 6.5f, Theme.alpha(Theme.accent(), 0x22));
        } else {
            border = hov ? 0xFF3A3F4C : Theme.BORDER;
        }
        Draw.card(g, x, cy - h / 2, w, h, 4.5f, hov || l ? Theme.INPUT_HOVER : Theme.INPUT, border);
        // kleine "Tasten-Kante" unten
        Draw.roundRect(g, x + 1, cy + h / 2 - 2, w - 2, 1, 0.5f, Theme.alpha(0x000000, 0x50));
        boolean unbound = !l && m.key.isUnbound();
        Text.drawCentered(g, text, x + w / 2, cy - 0.5f, l ? Theme.accent() : (unbound ? Theme.TEXT_MUTED : Theme.TEXT), Text.Kind.SMALL);
        return w;
    }

    /** Kurzer, gut lesbarer Tastenname. */
    static String keyName(Module m) {
        KeyMapping k = m.key;
        if (k.isUnbound()) return "Keine";
        String save = k.saveString();
        if (save.startsWith("key.keyboard.")) {
            String rest = save.substring("key.keyboard.".length());
            switch (rest) {
                case "right.shift": return "RShift";
                case "left.shift": return "LShift";
                case "right.control": return "RStrg";
                case "left.control": return "LStrg";
                case "right.alt": return "AltGr";
                case "left.alt": return "Alt";
                case "space": return "Leertaste";
                case "caps.lock": return "Feststell";
                case "tab": return "Tab";
                case "enter": return "Enter";
                default:
                    if (rest.length() == 1) return rest.toUpperCase(Locale.ROOT);
                    if (rest.startsWith("keypad.")) return "Num " + rest.substring(7).toUpperCase(Locale.ROOT);
                    if (rest.matches("f\\d+")) return rest.toUpperCase(Locale.ROOT);
            }
        } else if (save.startsWith("key.mouse.")) {
            String rest = save.substring("key.mouse.".length());
            return switch (rest) {
                case "left" -> "Linksklick";
                case "right" -> "Rechtsklick";
                case "middle" -> "Mausrad";
                default -> "Maus " + rest;
            };
        }
        String t = k.getTranslatedKeyMessage().getString();
        return t.length() > 14 ? t.substring(0, 13) + "…" : t;
    }

    /** Hinweis in der Kopfzeile, waehrend auf eine Taste gewartet wird. */
    private void listeningHint(GuiGraphics g, float x, float cy, float maxW) {
        String a = "Taste für " + listening.name + " drücken";
        String b = "ESC abbrechen  ·  Entf entfernt die Taste";
        float aw = Text.width(a, Text.Kind.BOLD), bw = Text.width(b, Text.Kind.SMALL);
        boolean both = aw + bw + 40 <= maxW;
        float w = Math.min(maxW, (both ? aw + bw + 16 : aw) + 26);
        float h = 18;
        float pulse = 0.6f + 0.4f * (float) Math.sin(System.currentTimeMillis() / 160.0);
        Draw.roundRect(g, x - 2, cy - h / 2 - 2, w + 4, h + 4, h / 2 + 2, Theme.alpha(Theme.accent(), Math.round(0x26 * pulse)));
        Draw.card(g, x, cy - h / 2, w, h, h / 2, Theme.INPUT_HOVER, Theme.mulAlpha(Theme.accent(), 0.5f + 0.5f * pulse));
        Draw.circle(g, x + 10, cy, 2.6f, Theme.accent());
        Text.draw(g, Text.fit(a, w - 26, Text.Kind.BOLD), x + 18, cy, Theme.TEXT, Text.Kind.BOLD);
        if (both) Text.draw(g, b, x + 18 + aw + 12, cy, Theme.TEXT_MUTED, Text.Kind.SMALL);
    }

    // ------------------------------------------------------------------ Aktionen

    private void openPage(Page p) {
        if (p == Page.MODULE) return;
        page = p;
        parent = p;
        module = null;
        openPicker = null;
        scroll = scrollTarget = 0;
        pageAt = System.currentTimeMillis();
    }

    private void openModule(Module m) {
        if (page != Page.MODULE) parent = search.isEmpty() ? page : Page.ALL;
        page = Page.MODULE;
        module = m;
        openPicker = null;
        search = "";
        searchFocused = false;
        scroll = scrollTarget = 0;
        pageAt = System.currentTimeMillis();
        click(1f);
    }

    private void toggleModule(Module m) {
        m.toggle();
        click(m.isEnabled() ? 1.2f : 0.9f);
    }

    private void startListening(Module m) {
        listening = listening == m ? null : m;
        searchFocused = false;
        click(1f);
    }

    private void bind(InputConstants.Key key) {
        if (listening == null) return;
        listening.key.setKey(key);
        KeyMapping.resetMapping();
        this.minecraft.options.save();
        listening = null;
        click(1.2f);
    }

    private void click(float pitch) {
        if (Modules.GUI.sounds.on()) {
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, pitch));
        }
    }

    // ------------------------------------------------------------------ Eingabe

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x(), my = event.y();
        if (listening != null) {
            if (event.button() >= 2) {
                bind(InputConstants.Type.MOUSE.getOrCreate(event.button()));
            } else {
                listening = null;
            }
            return true;
        }
        if (event.button() != 0) return true;
        searchFocused = false;
        for (int i = hotspots.size() - 1; i >= 0; i--) {
            Hotspot h = hotspots.get(i);
            if (mx < h.x1 || mx >= h.x2 || my < h.y1 || my >= h.y2) continue;
            if (h.clipped && (mx < vx1 || mx >= vx2 || my < vy1 || my >= vy2)) continue;
            if (h.drag != null) {
                dragging = h.drag;
                dragKey = h.key;
                dragging.drag(mx, my);
            } else if (h.click != null) {
                h.click.run();
            }
            return true;
        }
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (dragging != null) {
            dragging.drag(event.x(), event.y());
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        dragging = null;
        dragKey = null;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (mx >= vx1 && mx < vx2 && my >= vy1 && my < vy2) {
            scrollTarget = (float) Math.max(0, Math.min(maxScroll, scrollTarget - sy * 28));
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (listening != null) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                listening = null;
            } else if (key == GLFW.GLFW_KEY_BACKSPACE || key == GLFW.GLFW_KEY_DELETE) {
                // Die Menue-Taste darf nicht leer sein, sonst kommt man nicht mehr ins Menue
                bind(listening == Modules.GUI ? listening.key.getDefaultKey() : InputConstants.UNKNOWN);
            } else {
                bind(InputConstants.getKey(event));
            }
            return true;
        }
        if (searchFocused) {
            if (key == GLFW.GLFW_KEY_BACKSPACE) {
                if (!search.isEmpty()) search = search.substring(0, search.length() - 1);
                return true;
            }
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                if (!search.isEmpty()) search = "";
                else searchFocused = false;
                return true;
            }
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                searchFocused = false;
                return true;
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE && openPicker != null) {
            openPicker = null;
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE && page == Page.MODULE) {
            openPage(parent == Page.MODULE ? Page.ALL : parent);
            return true;
        }
        if (Modules.GUI.key.matches(event)) {
            this.onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (listening != null) return true;
        int cp = codepoint(event);
        if (cp < 32 || cp == 127 || !Character.isValidCodePoint(cp)) return false;
        String ch = new String(Character.toChars(cp));
        if (!searchFocused) {
            if (!Character.isLetterOrDigit(cp)) return false;
            searchFocused = true;
            search = "";
        }
        if (search.length() < 32) search += ch;
        return true;
    }

    /** Liest das Zeichen aus dem Event (erste Record-Komponente, unabhaengig vom Namen). */
    private static int codepoint(CharacterEvent event) {
        try {
            Object v = event.getClass().getRecordComponents()[0].getAccessor().invoke(event);
            if (v instanceof Integer i) return i;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
        return -1;
    }

    // ------------------------------------------------------------------ Hilfen

    private boolean hover(float x, float y, float w, float h) {
        if (dragging != null) return false;
        if (mouseX < x || mouseX >= x + w || mouseY < y || mouseY >= y + h) return false;
        return !inContent || (mouseX >= vx1 && mouseX < vx2 && mouseY >= vy1 && mouseY < vy2);
    }

    private void hot(float x, float y, float w, float h, Runnable click) {
        hotspots.add(new Hotspot(x, y, x + w, y + h, inContent, click, null, null));
    }

    private void hotDrag(Object key, float x, float y, float w, float h, Drag drag) {
        hotspots.add(new Hotspot(x, y, x + w, y + h, inContent, null, drag, key));
    }

    /** Weicher Uebergang eines Wertes (pro Schluessel), unabhaengig von der Bildrate. */
    private float anim(Object key, float target, float speed) {
        Float cur = anims.get(key);
        if (cur == null || !Theme.animations()) {
            anims.put(key, target);
            return target;
        }
        float v = cur + (target - cur) * Math.min(1f, dt * speed);
        if (Math.abs(v - target) < 0.002f) v = target;
        anims.put(key, v);
        return v;
    }

    private static float ease(float t) {
        float u = 1f - t;
        return 1f - u * u * u;
    }
}
