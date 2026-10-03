#!/bin/bash
# seed.sh <serial> <sport> <log> [in_coppia]: scrive LastKnownMatch e riapre il quadrante
export MSYS_NO_PATHCONV=1; ADB="$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe"; E="$ADB -s $1"; P=it.vantaggi.scoreboardessential
F="$(dirname "$0")/lkm.xml"
printf "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n<map>\n<string name=\"sport_id\">%s</string>\n<string name=\"event_log\">%s</string>\n<boolean name=\"in_coppia\" value=\"%s\" />\n</map>\n" "$2" "$3" "${4:-false}" > "$F"
$E shell am force-stop $P; $E shell pm clear $P >/dev/null
$E push -q "$F" /data/local/tmp/lkm.xml; $E shell chmod 644 /data/local/tmp/lkm.xml
$E shell "run-as $P sh -c 'mkdir -p shared_prefs; cp /data/local/tmp/lkm.xml shared_prefs/wear_last_known_match.xml'"
if [ -n "$5" ]; then printf "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
<string name=\"queue\">%s</string>
</map>
" "$5" > "$F"; $E push -q "$F" /data/local/tmp/q.xml; $E shell chmod 644 /data/local/tmp/q.xml; $E shell "run-as $P cp /data/local/tmp/q.xml shared_prefs/wear_pending_intents.xml"; fi
$E shell run-as $P head -c 120 shared_prefs/wear_last_known_match.xml; echo
$E shell am start -n $P/.wear.MainActivity >/dev/null; sleep 7
