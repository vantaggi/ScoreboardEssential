#!/usr/bin/env bash
# ktlint senza Android SDK: per le sessioni nel container cloud, dove ./gradlew non parte.
#
#   scripts/ktlint-cloud.sh            controlla tutto il codice Kotlin (~12 s)
#   scripts/ktlint-cloud.sh --format   corregge cio' che si corregge da solo
#
# Usa la stessa versione del motore di ./gradlew ktlintCheck (ktlintEngine in
# gradle/libs.versions.toml) e trova gli stessi errori: provato il 28 ottobre 2026 sul
# commit c3e8de8, rosso in CI per "Expected newline before '.'", cinque errori su cinque.
# Lanciarlo prima di ogni push risparmia un giro di CI da 10 minuti per una riga da mandare a capo.
set -euo pipefail

cd "$(dirname "$0")/.."
versione="$(sed -n 's/^ktlintEngine = "\(.*\)"/\1/p' gradle/libs.versions.toml)"
jar="${HOME}/.cache/ktlint/ktlint-cli-${versione}-all.jar"

if [ ! -s "$jar" ]; then
    mkdir -p "$(dirname "$jar")"
    url="https://repo1.maven.org/maven2/com/pinterest/ktlint/ktlint-cli/${versione}/ktlint-cli-${versione}-all.jar"
    # Maven Central a volte risponde 429 al primo tentativo dal proxy del container.
    for attesa in 2 4 8; do
        curl -fsSL -o "$jar.tmp" "$url" && break
        sleep "$attesa"
    done
    mv "$jar.tmp" "$jar"
fi

java -jar "$jar" "$@" "**/src/**/*.kt" "**/*.kts" "!**/build/**"
