package com.example.chestesp.module;

import com.example.chestesp.feature.Freecam;
import com.example.chestesp.gui.ClickGuiScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Alle Module des Clients mit ihren Einstellungen und Standardwerten. */
public final class Modules {
    private static final List<Module> ALL = new ArrayList<>();

    public static Esp ESP;
    public static Tracers TRACERS;
    public static Sus SUS;
    public static Tunnel TUNNEL;
    public static Spawner SPAWNER;
    public static FreecamModule FREECAM;
    public static Hud HUD;
    public static Gui GUI;

    private Modules() {}

    public static void init(KeyMapping.Category keys) {
        ESP = register(new Esp(keys));
        TRACERS = register(new Tracers(keys));
        SUS = register(new Sus(keys));
        TUNNEL = register(new Tunnel(keys));
        SPAWNER = register(new Spawner(keys));
        FREECAM = register(new FreecamModule(keys));
        HUD = register(new Hud(keys));
        GUI = register(new Gui(keys));
    }

    private static <M extends Module> M register(M module) {
        ALL.add(module);
        return module;
    }

    public static List<Module> all() {
        return Collections.unmodifiableList(ALL);
    }

    public static Module byId(String id) {
        for (Module m : ALL) if (m.id.equals(id)) return m;
        return null;
    }

    // ================================================================== Render

    public static final class Esp extends Module {
        public final ToggleColorSetting chests, trapped, ender, barrels, shulkers, spawners;
        public final NumberSetting fill, lineWidth;

        Esp(KeyMapping.Category keys) {
            super("esp", "Chest ESP", "Kisten, Shulker, Fässer und Spawner durch Wände",
                    Category.RENDER, keys, "key.chestesp.toggle", InputConstants.KEY_K,
                    true, true, true, () -> Items.CHEST);
            chests = accent(add(new ToggleColorSetting("chest", "Kisten", "Normale Kisten", true, 0xFFFFA31A, true)));
            trapped = add(new ToggleColorSetting("trapped", "Redstone-Kisten", "Trapped Chests", true, 0xFFFF4646, true));
            ender = add(new ToggleColorSetting("ender", "Endertruhen", null, true, 0xFFB15CFF, true));
            barrels = add(new ToggleColorSetting("barrel", "Fässer", null, true, 0xFFC08A4E, true));
            shulkers = add(new ToggleColorSetting("shulker", "Shulkerkisten", null, true, 0xFF2FE6E0, true));
            spawners = add(new ToggleColorSetting("spawner", "Spawner", "Auch Trial Spawner", true, 0xFF3DFF6E, true));
            fill = add(new NumberSetting("fill", "Füllung", "Deckkraft der Flächen", 12, 0, 60, 1, "%"));
            lineWidth = add(new NumberSetting("line", "Linienstärke", "In Bildschirmpixeln", 1.5, 0.5, 4, 0.5, "px"));
        }
    }

    public static final class Tracers extends Module {
        public final NumberSetting thickness, opacity;
        public final ModeSetting colorMode;
        public final ColorSetting color;
        public final BoolSetting toBlocks, toSus, toTunnels;

        Tracers(KeyMapping.Category keys) {
            super("tracers", "Tracer", "Dünne Linien vom Fadenkreuz zu jedem Fund",
                    Category.RENDER, keys, "key.chestesp.tracers", InputConstants.KEY_J,
                    true, true, true, () -> Items.SPECTRAL_ARROW);
            thickness = add(new NumberSetting("thickness", "Dicke", "In Bildschirmpixeln", 1.0, 0.5, 3, 0.25, "px"));
            opacity = add(new NumberSetting("opacity", "Deckkraft", null, 75, 10, 100, 5, "%"));
            colorMode = add(new ModeSetting("colormode", "Farbe", null, 0, "Wie Ziel", "Eigene"));
            color = accent(add(new ColorSetting("color", "Eigene Farbe", null, 0xFFFFFFFF, false)));
            color.visibleIf(() -> colorMode.is(1));
            toBlocks = add(new BoolSetting("blocks", "Zu ESP-Blöcken", null, true));
            toSus = add(new BoolSetting("sus", "Zu Sus Chunks", null, true));
            toTunnels = add(new BoolSetting("tunnels", "Zu Tunneln", "Kann bei vielen Tunneln unübersichtlich werden", false));
        }
    }

    public static final class Sus extends Module {
        public final ColorSetting color;
        public final BoolSetting outline, chat;
        public final ModeSetting height;
        public final NumberSetting fixedY, threshold, radius;

        Sus(KeyMapping.Category keys) {
            super("sus", "Sus Chunks", "Chunks mit vielen Spieler-Blöcken werden flach markiert",
                    Category.RENDER, keys, "key.chestesp.sus", InputConstants.KEY_G,
                    true, true, true, () -> Items.SPYGLASS);
            color = accent(add(new ColorSetting("color", "Farbe", "Transparenz = Deckkraft der Fläche", 0x47FF2DB4, true)));
            outline = add(new BoolSetting("outline", "Umrandung", null, true));
            height = add(new ModeSetting("height", "Höhe", "Auf welcher Höhe die Fläche liegt", 0, "Fund", "Spieler", "Fest"));
            fixedY = add(new NumberSetting("y", "Y-Wert", null, 64, -64, 320, 1, ""));
            fixedY.visibleIf(() -> height.is(2));
            threshold = add(new NumberSetting("threshold", "Schwelle", "Ab diesem Score gilt ein Chunk als sus", 6, 2, 30, 1, ""));
            radius = add(new NumberSetting("radius", "Radius", "Begrenzt durch die Sichtweite", 16, 2, 32, 1, "Chunks"));
            chat = add(new BoolSetting("chat", "Chat-Meldung", null, true));
        }
    }

    public static final class Tunnel extends Module {
        public final ToggleColorSetting oneByOne, oneByTwo, shafts;
        public final NumberSetting minLength, radius, yRange;
        public final BoolSetting outline;

        Tunnel(KeyMapping.Category keys) {
            super("tunnel", "Tunnel ESP", "Findet gegrabene 1x1/1x2 Tunnel und Schächte",
                    Category.RENDER, keys, "key.chestesp.tunnel", GLFW.GLFW_KEY_H,
                    false, true, true, () -> Items.DIAMOND_PICKAXE);
            oneByTwo = accent(add(new ToggleColorSetting("t1x2", "1x2 Tunnel", "1 breit, 2 hoch (normaler Spielertunnel)", true, 0x553AA0FF, true)));
            oneByOne = add(new ToggleColorSetting("t1x1", "1x1 Tunnel", "1 breit, 1 hoch (Kriechtunnel)", true, 0x55FFD23F, true));
            shafts = add(new ToggleColorSetting("shaft", "Schächte", "Senkrechte 1x1 Löcher", true, 0x55FF6A2F, true));
            minLength = add(new NumberSetting("minlen", "Mindestlänge", "Kürzere Stücke werden ignoriert", 5, 3, 16, 1, "Blöcke"));
            radius = add(new NumberSetting("radius", "Radius", null, 4, 1, 8, 1, "Chunks"));
            yRange = add(new NumberSetting("yrange", "Höhenbereich", "± um die eigene Höhe", 32, 8, 96, 4, "Blöcke"));
            outline = add(new BoolSetting("outline", "Umrandung", null, true));
        }
    }

    // ================================================================== Welt

    public static final class Spawner extends Module {
        public final NumberSetting radius, duration;
        public final BoolSetting popup, sound, chat, trial;
        public final ColorSetting color;

        Spawner(KeyMapping.Category keys) {
            super("spawner", "Spawner-Alarm", "Popup mit Mob-Typ, sobald ein Spawner in der Nähe ist",
                    Category.WORLD, keys, "key.chestesp.spawner", InputConstants.KEY_U,
                    true, true, true, () -> Items.SPAWNER);
            radius = add(new NumberSetting("radius", "Radius", null, 48, 16, 128, 4, "Blöcke"));
            popup = add(new BoolSetting("popup", "Popup", "Benachrichtigung mit Bild unten rechts", true));
            duration = add(new NumberSetting("duration", "Popup-Dauer", null, 6, 2, 15, 1, "s"));
            duration.visibleIf(popup::on);
            sound = add(new BoolSetting("sound", "Ton", null, true));
            chat = add(new BoolSetting("chat", "Chat-Meldung", null, true));
            trial = add(new BoolSetting("trial", "Trial Spawner", "Auch Trial Spawner melden", true));
            color = accent(add(new ColorSetting("color", "Akzentfarbe", null, 0xFF3DFF6E, false)));
        }
    }

    public static final class FreecamModule extends Module {
        public final NumberSetting speed;
        public final ColorSetting color;

        FreecamModule(KeyMapping.Category keys) {
            super("freecam", "Freecam", "Kamera löst sich von der Figur und fliegt frei",
                    Category.WORLD, keys, "key.chestesp.freecam", InputConstants.KEY_V,
                    false, true, false, () -> Items.ENDER_EYE);
            speed = add(new NumberSetting("speed", "Geschwindigkeit", "Sprinten = 2,5x", 0.6, 0.1, 3, 0.1, "B/t"));
            color = accent(add(new ColorSetting("color", "Farbe", "Farbe im HUD", 0xFF7C8CFF, false)));
        }

        @Override protected void onEnable() {
            Minecraft mc = Minecraft.getInstance();
            if (!Freecam.enable(mc)) forceState(false);
        }

        @Override protected void onDisable() {
            Freecam.disable(Minecraft.getInstance());
        }
    }

    // ================================================================== Client

    public static final class Hud extends Module {
        public final BoolSetting watermark, moduleList, toasts;
        public final ColorSetting color;

        Hud(KeyMapping.Category keys) {
            super("hud", "HUD", "Wasserzeichen und Liste der aktiven Module",
                    Category.CLIENT, keys, "key.chestesp.hud", GLFW.GLFW_KEY_UNKNOWN,
                    true, true, true, () -> Items.COMPASS);
            watermark = add(new BoolSetting("watermark", "Wasserzeichen", null, true));
            moduleList = add(new BoolSetting("list", "Modulliste", null, true));
            toasts = add(new BoolSetting("toasts", "Umschalt-Hinweise", "Kleiner Hinweis beim An-/Ausschalten", true));
            color = accent(add(new ColorSetting("color", "Farbe", null, 0xFF8B5CF6, false)));
        }
    }

    public static final class Gui extends Module {
        public final ColorSetting accentColor;
        public final ModeSetting font;
        public final BoolSetting blur, animations, sounds;

        Gui(KeyMapping.Category keys) {
            super("gui", "Menü", "Diese Oberfläche – Taste öffnet das Menü",
                    Category.CLIENT, keys, "key.chestesp.menu", InputConstants.KEY_RSHIFT,
                    false, false, false, () -> Items.COMPARATOR);
            accentColor = accent(add(new ColorSetting("accent", "Akzentfarbe", "Farbe der Oberfläche", 0xFF8B5CF6, false)));
            font = add(new ModeSetting("font", "Schrift", null, 0, "Modern", "Minecraft"));
            blur = add(new BoolSetting("blur", "Hintergrund-Unschärfe", null, true));
            animations = add(new BoolSetting("anim", "Animationen", null, true));
            sounds = add(new BoolSetting("sounds", "Klick-Töne", null, true));
        }

        @Override public void onKey() {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen == null) mc.setScreen(new ClickGuiScreen());
        }
    }
}
