#!/bin/bash
# stati.sh <serial> <etichetta> <locale>: i quattro stati delle bozze, screenshot e gerarchia in dp
export MSYS_NO_PATHCONV=1; ADB="$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe"; E="$ADB -s $1"; P=it.vantaggi.scoreboardessential
D="$(dirname "$0")"; O="${OUT:-$D/out}"; mkdir -p "$O"; T="$2_$3"
rep(){ printf "$1%.0s," $(seq 1 $2); }
NOW=$(($(date +%s)*1000)); DPI=$($E shell wm density | grep -o '[0-9]*$' | tr -d '\r')
cattura(){ sleep 2; $E exec-out screencap -p > "$O/$T-$1.png"; $E shell uiautomator dump /sdcard/u.xml >/dev/null 2>&1; $E pull -q /sdcard/u.xml "$O/$T-$1.xml"; python "$D/ui.py" "$O/$T-$1.xml" $DPI > "$O/$T-$1.txt"; }
locale(){ $E shell cmd locale set-app-locales $P --locales $1 >/dev/null 2>&1; }
tocca(){ # tocca il centro della vista con quel resource-id
  b=$(grep -o "resource-id=\"$P:id/$1\"[^>]*bounds=\"[^\"]*\"" "$O/$T-$2.xml" | grep -o 'bounds="[^"]*"' | grep -o '[0-9]*' | tr '\n' ' ')
  set -- $b; $E shell input tap $(( ($1+$3)/2 )) $(( ($2+$4)/2 )); }
# A padel 4-3 nei game, 40-15 (ultimo punto in coda)
L="1|$(rep 1 4)$(rep 2 4)$(rep 1 4)$(rep 2 4)$(rep 1 4)$(rep 2 4)$(rep 1 4)1,1,2"
bash "$D/seed.sh" $1 padel "$L" false "point,1,$NOW" >/dev/null; locale $3; $E shell am force-stop $P; $E shell am start -n $P/.wear.MainActivity >/dev/null; sleep 6; cattura padel
# B calcio 3-2 con 2 in coda, cronometro e portiere avviati
bash "$D/seed.sh" $1 football "1|1,2,2" false "point,1,$NOW;point,1,$((NOW+1000))" >/dev/null; locale $3; $E shell am force-stop $P; $E shell am start -n $P/.wear.MainActivity >/dev/null; sleep 6; cattura calcio0
tocca touchTimer calcio0; sleep 1; tocca touchKeeper calcio0; sleep 5; cattura calcio
# D ambient sullo stesso calcio
$E shell input keyevent KEYCODE_SLEEP; sleep 4; $E exec-out screencap -p > "$O/$T-ambient.png"; $E shell input keyevent KEYCODE_WAKEUP; sleep 1
# C tennis 2-1 nei set, ultimo punto in coda
L="1|$(rep 1 24)$(rep 2 24)$(rep 1 23)"; L="${L%,}"
bash "$D/seed.sh" $1 tennis "$L" false "point,1,$NOW" >/dev/null; locale $3; $E shell am force-stop $P; $E shell am start -n $P/.wear.MainActivity >/dev/null; sleep 6; cattura tennis
echo fatto $T
