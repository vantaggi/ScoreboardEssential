# Catture della revisione home (28 ottobre 2026)

Fotogrammi chiave della home del gioco, per la pagina web della revisione. JPEG qualita' 80: telefono largo 300 px,
orologio largo 240 px. Originali a tutta risoluzione e video: `D:\padel-wt\revisione-home\` (non nel repository).

## Come leggere i nomi

- `telefono/{padel|tennis}-{scuro|chiaro}-{100|200}-{inizio|15-0|dopo-il-game}.jpg`: Pixel_9a emulato, 360dp
  (densita' 480). `100` e' il carattere a 1,0 (da `turno2`), `200` e' il carattere a 2,0 dopo la #214 (da `turno2b`).
- `telefono/calcio-{scuro|chiaro}-200-{inizio|15-0|dopo-il-game}.jpg`: stesso emulatore, calcio, carattere a 2,0 dopo la #214.
- `telefono/calcio-*-{100|200}-*-telefono-vero.jpg`: telefono vero SM-S948B (partita di calcio con il portiere avviato:
  in corsa, a meta', scaduto, dopo un gol), prima della #214. A 100% e' la densita' di fabbrica, a 200% e' 360dp con
  carattere a 2,0.
- `orologio/tondo-*`: Wear_OS_Small_Round (tondo 384 px). `orologio/quadrato-*`: Wear_OS_Square (360 px). Padel e
  tennis sul quadrato sono veri; sul tondo il padel e' quello sincronizzato dal telefono accoppiato.
- `animazioni/<soggetto>-{1,2,3}.jpg`: tre fotogrammi di ogni animazione (prima, durante, poco dopo), scelti dove il
  video cambia di piu'. Soggetti: punto e cambio game sul telefono (calcio, padel, tennis), avvio del portiere
  (calcio, telefono vero), punto e cambio game sull'orologio (tondo e quadrato).

## Cosa mostrano

Tutti i fotogrammi sono di `main` dopo la #206 (tema chiaro); quelli etichettati `200` dell'emulatore anche dopo la #214.
Contesto del tema chiaro: `DESIGN.md`, sezione "Tema chiaro".
