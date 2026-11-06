#!/bin/bash
# Rifa' le immagini del README (docs/media/) dall'app vera, con dati finti.
#
# Le catture le fanno tre test Robolectric che girano solo con VETRINA=1 (la CI li salta):
# VetrinaDelTelefonoTest (tabellone), VetrinaDelleSchermateTest (Cronaca, Storico, Statistiche)
# e VetrinaDelPolsoTest (quadrante tondo e quadrato). Poi scripts/vetrina/componi.py mette le
# catture nelle cornici e scrive copertina, schermate e teaser.gif. Nessun emulatore: gira anche
# nel cloud. Serve Python 3 con Pillow.
set -euo pipefail
cd "$(dirname "$0")/.."

# --rerun: senza, Gradle considera i test gia' fatti e non rifa' le catture.
VETRINA=1 ./gradlew \
    :mobile:testDebugUnitTest --rerun --tests '*VetrinaDelTelefonoTest' --tests '*VetrinaDelleSchermateTest' \
    :wear:testDebugUnitTest --rerun --tests '*VetrinaDelPolsoTest'
python3 scripts/vetrina/componi.py
