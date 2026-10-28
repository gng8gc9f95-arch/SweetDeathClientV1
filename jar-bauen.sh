#!/usr/bin/env sh
# Baut die Mod-JAR (macOS/Linux). Voraussetzung: JDK 21.
cd "$(dirname "$0")" || exit 1
if ! command -v java >/dev/null 2>&1; then
  echo "Java wurde nicht gefunden. Bitte JDK 21 installieren: https://adoptium.net/temurin/releases/?version=21"
  exit 1
fi
echo "Baue die Mod ... (beim ersten Mal 5-10 Minuten, Minecraft wird heruntergeladen)"
chmod +x gradlew
./gradlew build --no-daemon || { echo "Build fehlgeschlagen - bitte die Fehlermeldung oben schicken."; exit 1; }
cp build/libs/chestesp-2.0.0.jar ./chestesp-2.0.0.jar
echo "Fertig: $(pwd)/chestesp-2.0.0.jar  -> in den mods-Ordner kopieren (alte chestesp-1.1.0.jar entfernen)."
