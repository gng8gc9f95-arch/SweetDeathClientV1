package com.example.chestesp;

import com.example.chestesp.module.Module;
import com.example.chestesp.module.Modules;
import com.example.chestesp.module.Setting;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Speichert An/Aus-Zustaende und alle Einstellungen in config/chestesp.properties.
 * Tastenbelegungen landen wie bei Vanilla in options.txt.
 */
public final class Config {
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("chestesp.properties");

    private Config() {}

    public static void load() {
        if (!Files.exists(FILE)) return;
        Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
            p.load(r);
        } catch (IOException e) {
            ChestEspClient.LOGGER.warn("Konnte {} nicht lesen", FILE, e);
            return;
        }
        for (Module m : Modules.all()) {
            String enabled = p.getProperty(m.id + ".enabled");
            if (enabled != null && m.toggleable && m.persistent) {
                m.forceState(Boolean.parseBoolean(enabled.trim()));
            }
            for (Setting<?> s : m.settings()) {
                String v = p.getProperty(m.id + "." + s.id);
                if (v != null) s.deserialize(v);
            }
        }
    }

    public static void save() {
        Properties p = new Properties();
        for (Module m : Modules.all()) {
            if (m.toggleable && m.persistent) p.setProperty(m.id + ".enabled", Boolean.toString(m.isEnabled()));
            for (Setting<?> s : m.settings()) p.setProperty(m.id + "." + s.id, s.serialize());
        }
        try {
            Files.createDirectories(FILE.getParent());
            try (Writer w = Files.newBufferedWriter(FILE, StandardCharsets.UTF_8)) {
                p.store(w, "Chest ESP Client");
            }
        } catch (IOException e) {
            ChestEspClient.LOGGER.warn("Konnte {} nicht speichern", FILE, e);
        }
    }
}
