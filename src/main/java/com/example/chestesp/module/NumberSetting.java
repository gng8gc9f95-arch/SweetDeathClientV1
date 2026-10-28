package com.example.chestesp.module;

import java.util.Locale;

/** Schieberegler mit Minimum, Maximum und Schrittweite. */
public final class NumberSetting extends Setting<Double> {
    public final double min, max, step;
    public final String unit;
    private final double def;
    private double value;

    public NumberSetting(String id, String name, String description,
                         double def, double min, double max, double step, String unit) {
        super(id, name, description);
        this.min = min;
        this.max = max;
        this.step = step;
        this.unit = unit == null ? "" : unit;
        this.def = def;
        this.value = def;
    }

    public double value() { return value; }

    public int intValue() { return (int) Math.round(value); }

    public float floatValue() { return (float) value; }

    @Override public Double get() { return value; }

    @Override public void set(Double v) {
        if (v == null || v.isNaN()) return;
        double clamped = Math.max(min, Math.min(max, v));
        if (step > 0) clamped = min + Math.round((clamped - min) / step) * step;
        value = Math.max(min, Math.min(max, clamped));
    }

    /** 0..1 Position auf dem Schieberegler. */
    public double normalized() {
        return max <= min ? 0 : (value - min) / (max - min);
    }

    public void setNormalized(double t) {
        set(min + Math.max(0, Math.min(1, t)) * (max - min));
    }

    public String display() {
        boolean whole = step >= 1 && Math.abs(step - Math.rint(step)) < 1e-9;
        String num;
        if (whole) num = Integer.toString((int) Math.round(value));
        else if (step >= 0.1) num = String.format(Locale.ROOT, "%.1f", value);
        else num = String.format(Locale.ROOT, "%.2f", value);
        return unit.isEmpty() ? num : num + " " + unit;
    }

    @Override public void reset() { value = def; }

    @Override public String serialize() { return Double.toString(value); }

    @Override public void deserialize(String text) {
        try {
            set(Double.parseDouble(text.trim()));
        } catch (NumberFormatException ignored) {
        }
    }
}
