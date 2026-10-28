package com.example.chestesp.module;

public final class BoolSetting extends Setting<Boolean> {
    private final boolean def;
    private boolean value;

    public BoolSetting(String id, String name, String description, boolean def) {
        super(id, name, description);
        this.def = def;
        this.value = def;
    }

    public boolean on() {
        return value;
    }

    public void toggle() {
        value = !value;
    }

    @Override public Boolean get() { return value; }

    @Override public void set(Boolean v) { value = v != null && v; }

    @Override public void reset() { value = def; }

    @Override public String serialize() { return Boolean.toString(value); }

    @Override public void deserialize(String text) {
        if ("true".equalsIgnoreCase(text)) value = true;
        else if ("false".equalsIgnoreCase(text)) value = false;
    }
}
