package com.example.chestesp.module;

import java.util.function.BooleanSupplier;

/** Basisklasse fuer alle Moduleinstellungen (wird im Menue angezeigt und in der Config gespeichert). */
public abstract class Setting<T> {
    public final String id;
    public final String name;
    public final String description;
    private BooleanSupplier visible = () -> true;

    protected Setting(String id, String name, String description) {
        this.id = id;
        this.name = name;
        this.description = description == null ? "" : description;
    }

    public abstract T get();

    public abstract void set(T value);

    public abstract void reset();

    /** Wert als Text fuer die Config-Datei. */
    public abstract String serialize();

    /** Wert aus der Config-Datei lesen; fehlerhafte Werte werden ignoriert. */
    public abstract void deserialize(String text);

    public boolean isVisible() {
        return visible.getAsBoolean();
    }

    @SuppressWarnings("unchecked")
    public <S extends Setting<T>> S visibleIf(BooleanSupplier condition) {
        this.visible = condition;
        return (S) this;
    }
}
