# Chest ESP Client 2.0 (Fabric, Minecraft 1.21.11)

Ein Client-Mod mit eigenem Menü im Web-App-Stil (keine Minecraft-Buttons).
**Rechte Shift** öffnet das Menü. Dort lässt sich alles per Klick einstellen,
auch Farben und Tastenbelegungen.

## Module

| Modul | Taste | Was es macht |
|---|---|---|
| Chest ESP | K | Umrandung + leichte Füllung für Kisten, Redstone-Kisten, Endertruhen, Fässer, Shulker, Spawner (auch Trial Spawner), durch Wände. Jede Art einzeln an/aus und mit eigener Farbe. |
| Tracer | J | Linien vom Fadenkreuz zu jedem Fund. Auf dem Bildschirm überall gleich dünn (Standard 1 Pixel, einstellbar 0,5–3 px). Farbe wie das Ziel oder eigene Farbe. |
| Sus Chunks | G | Chunks mit vielen Spieler-Blöcken werden als **flache** Fläche markiert. Farbe + Transparenz, Höhe (Fund / Spieler / fester Y-Wert), Schwelle, Radius, Chat-Meldung. |
| Tunnel ESP | H | Findet von Spielern gegrabene Gänge: 1x1-Tunnel, 1x2-Tunnel und senkrechte 1x1-Schächte. Jede Art mit eigener Farbe. Mindestlänge, Radius und Höhenbereich einstellbar. Standardmäßig aus. |
| Spawner-Alarm | U | Popup unten rechts mit Bild (Spawner + Spawn-Ei) und Mob-Typ, z. B. „Zombie-Spawner“, plus Koordinaten und Entfernung. Optional Ton und Chat-Meldung. Alle Funde stehen im Menü unter **Funde**. |
| Freecam | V | Kamera löst sich von der Figur (WASD, Leertaste hoch, Shift runter, Sprint schneller). Geschwindigkeit einstellbar. |
| HUD | – | Wasserzeichen oben links, Liste der aktiven Module, kurze An/Aus-Hinweise. |
| Menü | Rechte Shift | Akzentfarbe der Oberfläche, Schrift (Modern/Minecraft), Unschärfe, Animationen, Klick-Töne. |

Jedes Modul hat eine eigene Farbe und eine frei wählbare Taste.

## Tasten ändern

Im Menü auf das kleine Tastenfeld eines Moduls klicken (auf der Karte oder oben auf der
Modulseite), dann die neue Taste drücken. Auch Maustasten 3–5 gehen.
**ESC** bricht ab, **Entf** oder **Rücktaste** entfernt die Belegung.
Die Tasten stehen weiterhin auch unter Optionen → Steuerung → „Chest ESP“.

## Menü

- Seitenleiste: Übersicht, Render, Welt, Client und **Funde** (Spawner-Liste, Sus Chunks, Statistik)
- Einfach lostippen, um Module zu suchen
- Modul-Karte anklicken → Detailseite mit Schaltern, Schiebereglern und Farbwähler
  (Farbfläche, Farbton, Deckkraft, Vorlagen, „Standard“)
- ESC geht eine Ebene zurück bzw. schließt das Menü
- Falls die moderne Schrift bei dir unscharf aussieht: Client → Menü → Schrift → „Minecraft“

Einstellungen werden in `config/chestesp.properties` gespeichert, Tasten in `options.txt`.

## JAR bauen

**Windows (am einfachsten):** JDK 21 installieren (https://adoptium.net/temurin/releases/?version=21,
beim Installieren „Set JAVA_HOME“ und „Add to PATH“ anhaken), dann **JAR-BAUEN.bat** doppelklicken.
Beim ersten Mal dauert es 5–10 Minuten (Minecraft und Fabric werden heruntergeladen).
Danach liegt `chestesp-2.0.0.jar` direkt in diesem Ordner.

**macOS/Linux:** `./jar-bauen.sh`

**Ohne etwas zu installieren (GitHub):** Den Ordnerinhalt in ein neues GitHub-Repository hochladen.
Unter „Actions“ läuft dann automatisch „JAR bauen“; die fertige JAR liegt beim Lauf unter „Artifacts“.

**Manuell:** `gradlew.bat build` bzw. `./gradlew build` → `build/libs/chestesp-2.0.0.jar` (NICHT die -sources.jar).

Die JAR in den mods-Ordner legen und eine alte `chestesp-1.1.0.jar` entfernen.
Benötigt Fabric Loader 0.18+ und Fabric API für 1.21.11.

## Hinweise

- Mappings: Offizielle Mojang-Mappings.
- Die Schrift Inter (© The Inter Project Authors) liegt unter der SIL Open Font License 1.1 bei,
  siehe `assets/chestesp/font/Inter-OFL.txt`.
- Auf vielen Multiplayer-Servern ist so etwas verboten.
