package com.example.chestesp.module;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * Ein Modul des Clients: An/Aus-Zustand, eigene Tastenbelegung (frei aenderbar,
 * auch im Menue) und eine Liste von Einstellungen.
 */
public class Module {
    public final String id;
    public final String name;
    public final String description;
    public final Category category;
    public final KeyMapping key;
    /** false = Modul hat keinen An/Aus-Zustand (z. B. das Menue selbst). */
    public final boolean toggleable;
    /** false = Zustand wird nicht gespeichert (z. B. Freecam startet immer aus). */
    public final boolean persistent;

    private final List<Setting<?>> settings = new ArrayList<>();
    private final Supplier<ItemLike> icon;
    private ItemStack iconStack;
    private ColorSetting accent;
    private boolean enabled;

    public Module(String id, String name, String description, Category category,
                  KeyMapping.Category keyCategory, String keyTranslation, int defaultKey,
                  boolean defaultEnabled, boolean toggleable, boolean persistent,
                  Supplier<ItemLike> icon) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.category = category;
        this.toggleable = toggleable;
        this.persistent = persistent;
        this.enabled = defaultEnabled;
        this.icon = icon;
        this.key = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                keyTranslation, InputConstants.Type.KEYSYM, defaultKey, keyCategory));
    }

    protected <S extends Setting<?>> S add(S setting) {
        settings.add(setting);
        return setting;
    }

    /** Legt fest, welche Farbe das Modul in HUD und Menue repraesentiert. */
    protected <S extends ColorSetting> S accent(S color) {
        this.accent = color;
        return color;
    }

    public List<Setting<?>> settings() {
        return Collections.unmodifiableList(settings);
    }

    public int accentColor() {
        return accent != null ? accent.opaque() : 0xFF8B5CF6;
    }

    public ItemStack iconStack() {
        if (iconStack == null) iconStack = new ItemStack(icon.get());
        return iconStack;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean value) {
        if (!toggleable || value == enabled) return;
        enabled = value;
        if (value) onEnable();
        else onDisable();
    }

    public void toggle() {
        setEnabled(!enabled);
    }

    /** Zustand setzen, ohne onEnable/onDisable auszuloesen (z. B. beim Laden). */
    public void forceState(boolean value) {
        enabled = value;
    }

    /** Wird ausgeloest, wenn die Taste des Moduls gedrueckt wird. */
    public void onKey() {
        toggle();
    }

    protected void onEnable() {}

    protected void onDisable() {}
}
