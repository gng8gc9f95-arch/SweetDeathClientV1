package com.example.chestesp.module;

/** Auswahl aus festen Optionen (im Menue als Segment-Schalter). */
public final class ModeSetting extends Setting<Integer> {
    public final String[] modes;
    private final int def;
    private int index;

    public ModeSetting(String id, String name, String description, int def, String... modes) {
        super(id, name, description);
        this.modes = modes;
        this.def = def;
        this.index = def;
    }

    public int index() { return index; }

    public String mode() { return modes[index]; }

    public boolean is(int i) { return index == i; }

    public void cycle() { index = (index + 1) % modes.length; }

    @Override public Integer get() { return index; }

    @Override public void set(Integer v) {
        if (v != null && v >= 0 && v < modes.length) index = v;
    }

    @Override public void reset() { index = def; }

    @Override public String serialize() { return modes[index]; }

    @Override public void deserialize(String text) {
        for (int i = 0; i < modes.length; i++) {
            if (modes[i].equalsIgnoreCase(text.trim())) {
                index = i;
                return;
            }
        }
    }
}
