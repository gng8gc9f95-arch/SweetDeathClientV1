package com.example.chestesp.module;

public enum Category {
    RENDER("Render", "ESP, Tracer und Markierungen in der Welt"),
    WORLD("Welt", "Alarme und Kamera"),
    CLIENT("Client", "Oberfläche, HUD und Menü");

    public final String title;
    public final String subtitle;

    Category(String title, String subtitle) {
        this.title = title;
        this.subtitle = subtitle;
    }
}
