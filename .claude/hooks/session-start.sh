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
    "$SDK/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$SDK" "platforms;android-36" "platform-tools" >/dev/null 2>&1
fi

# Maven Central dal mirror di Google, scelta di Cioti del 28 ottobre 2026: dal container
# repo1.maven.org e repo.maven.apache.org rispondono 429 a circa meta' delle richieste, e ne'
# Gradle ne' Robolectric (che scarica android-all-instrumented a runtime) ritentano: la prima
# build ha richiesto 11 lanci. L'init script sta fuori dal repo, quindi la build del repo e
# la CI non cambiano; nel cloud il mirror viene per primo e Maven Central resta di riserva.
mkdir -p "${HOME}/.gradle/init.d"
cat > "${HOME}/.gradle/init.d/maven-central-mirror.gradle" <<'GRADLE'
def mirror = 'https://maven-central.storage-download.googleapis.com/maven2/'
beforeSettings { settings ->
    settings.pluginManagement.repositories { maven { url = mirror } }
    settings.dependencyResolutionManagement.repositories { maven { url = mirror } }
}
allprojects {
    tasks.withType(Test).configureEach {
        systemProperty 'robolectric.dependency.repo.url', mirror
        systemProperty 'robolectric.dependency.repo.id', 'maven-central-mirror'
    }
}
GRADLE

# local.properties e' ignorato da git: qui dice a Gradle dov'e' l'SDK.
echo "sdk.dir=$SDK" > local.properties
if [ -n "${CLAUDE_ENV_FILE:-}" ]; then
    echo "export ANDROID_HOME=$SDK" >> "$CLAUDE_ENV_FILE"
fi
