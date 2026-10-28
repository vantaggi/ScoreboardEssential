#!/bin/bash
# Prepara il container cloud per ./gradlew: JDK 17 e Android SDK.
#
# Solo nelle sessioni cloud (CLAUDE_CODE_REMOTE=true): sul PC di Cioti c'e' gia'
# Android Studio. Idempotente: ogni passo salta se cio' che installa c'e' gia', e lo
# stato del container resta in cache dopo il primo giro.
# Serve dl.google.com aperto nella rete dell'ambiente (28 ottobre 2026): senza, il
# passo dell'SDK fallisce, lo script lo dice e la sessione parte lo stesso.
set -euo pipefail

if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
    exit 0
fi

cd "${CLAUDE_PROJECT_DIR:-$(dirname "$0")/../..}"
SDK="${HOME}/android-sdk"

# Il daemon di Gradle vuole Java 17 (gradle/gradle-daemon-jvm.properties): Gradle lo
# trova in /usr/lib/jvm. foojay, che lo scaricherebbe, dal container non si raggiunge.
if ! ls /usr/lib/jvm 2>/dev/null | grep -q -- '-17-'; then
    (apt-get update -qq && apt-get install -y -qq openjdk-17-jdk-headless) >/dev/null
fi

if [ ! -x "$SDK/cmdline-tools/latest/bin/sdkmanager" ]; then
    # L'ultima versione degli strumenti la dice il catalogo di Google.
    zip="$(curl -fsSL https://dl.google.com/android/repository/repository2-3.xml |
        grep -o 'commandlinetools-linux-[0-9]*_latest\.zip' | sort -t- -k3 -n | tail -1)" || {
        echo "Android SDK: dl.google.com non raggiungibile, ./gradlew non partira'." >&2
        exit 0
    }
    tmp="$(mktemp -d)"
    curl -fsSL -o "$tmp/tools.zip" "https://dl.google.com/android/repository/$zip"
    unzip -q "$tmp/tools.zip" -d "$tmp"
    mkdir -p "$SDK/cmdline-tools"
    rm -rf "$SDK/cmdline-tools/latest"
    mv "$tmp/cmdline-tools" "$SDK/cmdline-tools/latest"
    rm -rf "$tmp"
fi

# Licenze accettate: AGP scarica da solo piattaforma e build-tools alla prima build.
# La 36 la prendiamo subito, cosi' la prima build non si ferma a scaricarla.
yes | "$SDK/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$SDK" --licenses >/dev/null 2>&1 || true
if [ ! -d "$SDK/platforms/android-36" ]; then
    "$SDK/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$SDK" "platforms;android-36" "platform-tools" >/dev/null
fi

# local.properties e' ignorato da git: qui dice a Gradle dov'e' l'SDK.
echo "sdk.dir=$SDK" > local.properties
if [ -n "${CLAUDE_ENV_FILE:-}" ]; then
    echo "export ANDROID_HOME=$SDK" >> "$CLAUDE_ENV_FILE"
fi
