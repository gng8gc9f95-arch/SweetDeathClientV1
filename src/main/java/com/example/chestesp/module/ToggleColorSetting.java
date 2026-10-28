package com.example.chestesp.module;

/** Eine Farbe mit eigenem An/Aus-Schalter (z. B. "Kisten" in Chest ESP). */
public final class ToggleColorSetting extends ColorSetting {
    private final boolean defOn;
    private boolean on;

    public ToggleColorSetting(String id, String name, String description, boolean on, int argb, boolean alpha) {
        super(id, name, description, argb, alpha);
        this.defOn = on;
        this.on = on;
    }

    public boolean on() { return on; }

    public void setOn(boolean v) { on = v; }

    public void toggle() { on = !on; }

    @Override public void reset() {
        super.reset();
        on = defOn;
    }

    @Override public String serialize() {
        return on + "|" + super.serialize();
    }

    @Override public void deserialize(String text) {
        int bar = text.indexOf('|');
        if (bar < 0) {
            super.deserialize(text);
            return;
        }
        String flag = text.substring(0, bar).trim();
        if ("true".equalsIgnoreCase(flag)) on = true;
        else if ("false".equalsIgnoreCase(flag)) on = false;
        super.deserialize(text.substring(bar + 1));
    }
}
