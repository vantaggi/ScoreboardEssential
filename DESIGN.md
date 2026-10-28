# Design - telefono e orologio, 24 settembre 2026

Prodotto da un workflow di agenti su `0337026`: per ciascuna piattaforma tre proposte indipendenti da angoli diversi, un giudice che le ha confrontate (ricalcolando i contrasti WCAG e aprendo i file citati), una sintesi che parte dalla vincente e innesta il meglio delle altre. Tutto in sola lettura: **nessuna riga di codice e' stata cambiata**. Le bozze in `design/` sono SVG alle proporzioni reali (412x923 dp per il Pixel 9a, tondo da 192 dp per il Wear_OS_Small_Round) e servono a giudicare gerarchia e proporzioni, non sono schermate.

Il punto di partenza sono i difetti di `VALIDAZIONE.md`: ogni schermata indica quali risolve o rende visibili.

## Telefono: Bordo campo: numero in alto, pollice in basso, niente che si sposta

| Proposta | Voto |
|---|---|
| Bordo campo: numero in alto, pollice in basso, niente che si sposta | 8.5 |
| Il tabellone parla lo sport: un solo layout, uno slot di contesto e un registro derivato dal motore | 7 |
| Vernice e inchiostro: la street diventata sistema | 6 |

### Direzione

La base è la proposta vincente, «Bordo campo». Rispetta la Fase D (via di mezzo: street nel contorno, riduzione solo sulla schermata di gioco) e dice apertamente dove non è d'accordo: la D4 ha tolto il tabellone dallo scorrimento, ma ha lasciato il + a metà schermo, a circa 470-500dp dall'alto su 923. Ha lasciato anche un'intestazione che cambia altezza a partita in corso: undo_goal_button passa da GONE a VISIBLE, la riga del portiere compare e scompare, il Flow va a capo quando START diventa PAUSA. FabOverlap.kt esiste proprio per tamponare questo sintomo. La schermata di gioco diventa quindi una colonna a slot fissi su fondo #000000. In alto si leggono tempo o set. Al centro ci sono i numeri, bianco puro, dimensionati una volta per sport fino a 150sp. In basso c'è la fascia d'azione: una striscia fissa con l'ultima azione e ANNULLA (spento, mai GONE) e due zone + da circa 182x112dp nel colore della squadra, sotto il pollice. Rose, registro, formazioni, fine partita, cronologia, statistiche, giocatori e impostazioni passano in un foglio PARTITA (BottomSheetBehavior nella stessa Activity: niente secondo o terzo MainViewModel), che tiene lo street (asfalto, cemento, angoli tagliati, pulsante pieno rosa). Una scelta tocca l'identità e la lascio al proprietario: il colore di squadra esce da dietro il numero e va sulle zone + e su una barretta sotto il nome. L'ho ricalcolata: col modello «sole» (1500 nit più 300 di riflesso), bianco su nero vale 6,0. Il numero sul colore di squadra, anche con l'inchiostro ottimo WCAG, scende a 1,90 per un colore appena sopra la soglia di luminanza 0,179, perché lì l'inchiostro scelto è il nero. Innesti dalla terza proposta: servizio mostrato da servingSide e non da periodLabel (nel padel a set unico oggi non compare mai), dialogo di fine partita col punteggio che si vede a schermo, vibrazione diversa per punto, game, fine partita e tocco inerte, minuto del calcio nel formato 66', sport e set nella cronologia, riga delle regole nelle impostazioni, PDF senza TABELLINO nel padel. Più avanti, e solo con i lotti da cui dipendono, vengono la card COPPIE (con L2) e il registro derivato dal motore (dopo L3). Innesti dalla seconda: TeamInk in :core, così vale anche per il quadrante (L7); mappatura completa dei ruoli M3 (niente viola di base); un'unica fonte dei colori predefiniti; token #FF6E6E per il testo distruttivo; TeamInk su avatar, chip e cronologia. Non entra il neon_cyan in gioco (SET POINT) della terza proposta: contraddice la riduzione. Correggo tre errori sfuggiti a proposte e giudice. (1) Il dialogo del portiere con setCancelable(false) non blocca niente: è codice morto, perché nessuno osserva showKeeperTimerExpired (L8). Il difetto vero è un altro: alla scadenza il valore va a 0 e la riga del portiere diventa GONE, quindi l'intestazione si accorcia e card e + saltano proprio in quel momento. Il telefono non ha nessun comando per avviare il portiere: startKeeperTimer è chiamato solo con fromRemote=true. (2) La vibrazione da 500ms (0,100,50,100,50,200) parte a ogni + in tutti gli sport, non solo nel padel. (3) Lo stroke #E0E0E0 su #1A237E vale 10,03, non 13,24 (che è il valore di #FFFFFF); lo stroke conta contro il nero, dove vale 15,91. Il caso al sole «3,15» vale 3,17.

### Bozze

- [Gioco nel calcio (verticale, Pixel 9a 412x923dp)](design/mobile-gioco-nel-calcio-verticale-pixel-9a-412x923dp.svg): In alto il tempo come pulsante (pausa visibile, 32sp bianco) e lo slot PORTIERE fisso (qui in corso), poi l'orologio e ≡ PARTITA. Riga dei nomi con −1 ai bordi esterni e barretta colore (la squadra blu scuro ha il contorno perché sul nero fa 1,59). Numeri a 150sp bianco su nero. In basso la striscia fissa con la scorciatoia del marcatore e ANNULLA, poi le due zone + da 182x112dp: gialla con glifo nero, blu con glifo bianco e stroke #E0E0E0.
- [Gioco nel padel (verticale, stesse coordinate del calcio)](design/mobile-gioco-nel-padel-verticale-stesse-coordinate-del-.svg): La barra dice sport e servizio («PADEL · SERVE ROSSI», da servingSide e non da periodLabel, che a set unico è null). Il pallino bianco sta accanto a chi serve. Badge rosso sull'orologio: c'è un arretrato non entrato. Niente −1 e niente tempo. Il punto 40-30 a 150sp, sotto il dettaglio GAME 5-3 letto da sinistra. Zone +, striscia e ANNULLA esattamente dove stanno nel calcio: il pollice non impara due schermate.
- [Foglio PARTITA aperto (calcio)](design/mobile-foglio-partita-aperto-calcio.svg): Il foglio sale sopra la schermata di gioco, che resta sotto scurita. Fondo asfalto, card in cemento, angoli tagliati: è qui che vive lo street. In cima l'avviso persistente dell'orologio, che descrive senza prescrivere. Un solo pulsante pieno (TERMINA, rosa con testo nero 5,02) e gli altri contornati. Rose con etichette nel colore della squadra e testo TeamInk. Registro col minuto del calcio (35', 22'), testo sempre #E0E0E0 e colore solo sulla barretta.
- [Cronologia (contorno, identità street)](design/mobile-cronologia-contorno-identita-street.svg): Fondo asfalto #121212, card StreetCard in cemento, titolo condensato maiuscolo. Ogni partita dice sport, data e durata. Le squadre sono etichette nel colore con cui si è giocato (Team.color), testo in TeamInk: nero sul giallo, bianco sul blu scuro. Il vincitore è in #E0E0E0 e lo sconfitto in #9E9E9E, quindi chi ha vinto si legge senza il colore. Nella racchetta c'è la riga dei set o delle regole. Cestino da 48dp in rosso come grafica. La partita in corso non compare (rimedio L2).

### Il colore della squadra

TeamInk.on(argb): Int, funzione pura in core/src/main/kotlin/it/vantaggi/scoreboardessential/core/TeamInk.kt. Calcola la luminanza relativa WCAG L del colore (canali sRGB linearizzati, pesi 0,2126 / 0,7152 / 0,0722) e restituisce 0xFF000000 se L >= 0,1791, altrimenti 0xFFFFFFFF.

Perché regge con qualsiasi colore: 0,1791 = sqrt(1,05 x 0,05) - 0,05 è il punto in cui nero e bianco danno lo stesso contrasto, (1+0,05)/(0,1791+0,05) = 4,58. Lontano dalla soglia uno dei due cresce, quindi ogni colore sRGB ha almeno 4,58:1, AA anche per il testo normale. L'ho verificato con una scansione a passo 3 su tutto l'RGB: minimo 4,583 su #5D60FF.

Perché servono nero e bianco puri: con la coppia attuale #E0E0E0/#1E1E1E, anche scegliendo sempre la migliore, il minimo è 3,55 (#66758A). La regola di oggi (luma gamma sotto 0,5) scende a 2,07 su #FF4BFF; sta sotto 3:1 nel 7,1% dei colori e sotto 4,5 nel 26,1%.

Corollari.
(1) In gioco il colore di squadra è solo riempimento: zone + (glifo in TeamInk), barretta di 4x60dp sotto il nome. Nel foglio e nel contorno è riempimento di etichette StreetBadge con testo in TeamInk, oppure barrette da 4dp nel registro. Non è mai colore di testo: come testo su #1E1E1E sta sotto 4,5 nel 46,4% dei colori e sotto 3 nel 27,0%.
(2) Estensione della zona sul nero: se contrasto(c, #000000) < 3 (il 18,4% dei colori, es. #1A237E 1,59, #0D47A1 2,43), la zona + prende strokeWidth 2dp e strokeColor #E0E0E0 (15,91 sul nero). Per le barrette e le etichette su #1E1E1E vale la stessa soglia contro quel fondo, con un contorno da 1dp #9E9E9E.
(3) Il testo accanto al colore è sempre #E0E0E0 o #9E9E9E, quindi il colore non è mai l'unico segno che identifica la squadra.
(4) La stessa funzione vale per ogni vernice: pulsanti colore delle impostazioni, avatar, chip dei ruoli, etichette della cronologia, bande del PDF e, nella pista Orologio, le cifre del quadrante (oggi nel colore grezzo su nero, wear MainActivity 461-470).
(5) Test JVM core/src/test/kotlin/.../TeamInkTest.kt: scansione a passo 3 con asserzione minimo >= 4,5; casi fissi #FFD600 → nero (14,87), #1A237E → bianco (13,24), #5D60FF → 4,58 in entrambi i sensi, #F50057 → nero (5,02).

### Colori

| Token | Valore | Uso |
|---|---|---|
| game_bg (= asphalt_black esistente) | `#000000` | Fondo della sola schermata di gioco e dello spazio dietro il foglio. Sostituisce bg_asphalt_main (#121212) solo su main_root. |
| ink_white (nuovo) | `#FFFFFF` | Cifre del punteggio, cronometro, pallino del servizio. È anche l'inchiostro chiaro di TeamInk sopra un colore di squadra scuro. Mai altrove. |
| ink_black (= asphalt_black) | `#000000` | Inchiostro scuro di TeamInk sopra un colore chiaro; colorOnPrimary e colorOnSecondary del tema (rosa 5,02, ciano 13,65); testo sopra error_red (5,46). |
| asphalt_dark | `#121212` | Fondo delle schermate di contorno e del foglio PARTITA (invariato). |
| concrete_gray | `#1E1E1E` | Striscia dell'ultima azione, pulsante del tempo, card del foglio e del contorno (StreetCard). |
| graffiti_dark_gray | `#2C2C2C` | Zone + spente a partita finita, divisori da 1dp, fondo dei dialoghi (colorSurfaceContainerHigh/Highest). |
| stencil_white | `#E0E0E0` | Testo su fondi scuri: nomi, striscia, ANNULLA, valore del portiere, righe del registro. Stroke di 2dp sulle zone + scure. Mai sopra un colore di squadra. |
| sidewalk_gray | `#9E9E9E` | Didascalie (PORTIERE, GAME), minuti e righe INFO, numero dello sconfitto a partita finita, contorno da 1dp della barretta di un colore scuro. |
| outline_gray (nuovo) | `#6E6E6E` | Bordi funzionali: contorno del −1, dello slot del portiere e dei pulsanti outlined del foglio (4,12 sul nero, 3,27 su #1E1E1E, sopra 3:1 per la grafica). |
| graffiti_pink | `#F50057` | Solo contorno: TERMINA pieno nel foglio, AVANTI e INIZIA dell'onboarding, podio delle statistiche, sempre con testo #000000. Mai nella schermata di gioco. |
| neon_cyan | `#00E5FF` | Solo contorno: focus dei campi e colorPrimary dell'overlay dei dialoghi. Mai in gioco. |
| error_red | `#FF1744` | Solo grafica e riempimenti: badge dell'orologio, barretta dell'avviso nel foglio, cestino, slot del portiere scaduto (pieno, testo nero). Mai testo su #1E1E1E (4,33). |
| error_text (nuovo) | `#FF6E6E` | Testo di azioni distruttive su fondi grigi: SCARTA nel dialogo di fine partita (6,12 su #1E1E1E, 5,13 su #2C2C2C). |
| team_spray_yellow / team_electric_green | `#FFD600 / #76FF03` | Solo colori iniziali delle squadre, da una sola fonte (ColorRepository). Non più accenti d'interfaccia: l'icona dell'orologio collegato passa da #76FF03 a #E0E0E0. |
| esempio di colore utente scuro (solo bozze e test) | `#1A237E` | Caso di prova per la regola dello stroke: sul nero vale 1,59, quindi la zona + prende lo stroke #E0E0E0. |

### Tipografia

| Ruolo | sp | Peso e forma | Uso |
|---|---|---|---|
| Numero | 150 | sans-serif-condensed bold, letterSpacing 0, includeFontPadding false | Punteggio. Dimensione FISSA per tutta la partita, calcolata una volta in applyCapabilities (doOnLayout): con Paint.measureText si misura il token più largo dello sport («88» nel calcio, «AV» nella racchetta) nel box di mezza colonna e si prende il minimo fra 150sp e ciò che entra, con limite basso 72sp (orizzontale: 48-110sp). Niente autoSize di sistema, che cambierebbe dimensione fra «1» e «15». |
| Cronometro | 32 | condensed bold, fontFeatureSettings tnum | Tempo del calcio dentro il pulsante play/pausa della barra. Cifre tabulari: il testo non si allarga a ogni secondo. |
| Valore secondario | 24 | condensed bold, tnum | Game o set sotto i numeri (5-3, 6-4 · 3-2), valore del portiere. |
| Nome squadra | 20 | condensed bold, maiuscolo, letterSpacing 0.06 | Riga dei nomi in gioco, titolo del foglio. |
| Comando e striscia | 16 | condensed bold, maiuscolo, letterSpacing 0.06 | Striscia dell'ultima azione, ANNULLA, testo della barra (PADEL · SERVE ROSSI), −1, righe del registro (bold per i gol). |
| Corpo del foglio | 14 | condensed regular | Sottorighe del registro (marcatore), righe INFO, avvisi dell'orologio, pulsanti del foglio (bold maiuscolo). |
| Didascalia | 12 | condensed bold, maiuscolo, letterSpacing 0.12, #9E9E9E | PORTIERE, GAME, SET · GAME, PARTITA sotto l'icona ≡, intestazioni ROSE e REGISTRO, minuto del registro (regular). Nel contorno restano gli stili Street esistenti. |

### Forme e spaziature

Schermata di gioco in verticale (Pixel 9a, 412x923dp; inset già applicati come padding di main_root), dall'alto:
- barra superiore 56dp;
- riga dei nomi 48dp;
- zona numeri a peso 1 (su 923dp circa 570dp, su 640dp circa 270dp);
- slot dettaglio 32dp più didascalia 16dp, solo negli sport a set;
- striscia 56dp;
- 8dp;
- zone + 112dp;
- 8dp sopra l'inset di navigazione.
Margini laterali 16dp; 16dp di stacco neutro fra le zone +, che sono quindi larghe circa 182dp. Fra i numeri e la striscia resta una fascia vuota voluta: il palmo che regge il telefono non tocca niente di attivo.

Forme. Le zone + usano ShapeAppearance.App.StreetButton (taglio 8dp), cardElevation 0. Il −1 e il pulsante del tempo sono StreetButton; lo slot del portiere e le etichette di squadra sono StreetBadge (taglio 4dp). Il foglio ha il taglio 12dp solo agli angoli superiori. Card del foglio e del contorno StreetCard (12dp), come oggi. In gioco niente elevazioni, ombre, badge VS o lampi di colore.

Regola degli slot fissi: durante la partita nessuna vista della schermata di gioco cambia misura. Le visibilità GONE/VISIBLE si decidono solo in applyCapabilities, al cambio sport, che la guardia già blocca a partita iniziata. Dentro box di misura fissa (badge dell'orologio, pallino del servizio, glifo del +) si usano INVISIBLE o alpha. ANNULLA si spegne (isEnabled false, alpha 0,38) invece di sparire.

Bersagli di almeno 48dp: tempo 132x48, portiere 112x48, orologio 48x48, ≡ PARTITA 64x48, −1 48x40 dentro una riga di 48dp, ANNULLA 96x56, zone + 182x112.

Orizzontale (values-land/dimens.xml, stesso layout): barra 48, nomi 48, dettaglio 24, striscia 48, zone 72dp, numeri nel resto (circa 100dp su 364 utili).

### Contrasti

| Coppia | Rapporto | Esito |
|---|---|---|
| #FFFFFF numero su #000000 fondo di gioco | 21.00:1 | AA e AAA |
| #E0E0E0 su #000000 (nomi, barra) | 15.91:1 | AA |
| #E0E0E0 su #1E1E1E (striscia, ANNULLA, righe del foglio) | 12.63:1 | AA |
| #E0E0E0 su #121212 (foglio, contorno) | 14.19:1 | AA |
| #E0E0E0 su #2C2C2C (dialoghi) | 10.58:1 | AA |
| #9E9E9E su #000000 (didascalie in gioco, sconfitto) | 7.84:1 | AA |
| #9E9E9E su #1E1E1E (minuti, righe INFO) | 6.22:1 | AA |
| #9E9E9E su #2C2C2C | 5.21:1 | AA |
| TeamInk, caso peggiore su tutto l'RGB (#5D60FF, nero o bianco) | 4.58:1 | AA per qualsiasi colore scelto (soglia L = 0,1791) |
| Regola attuale: #E0E0E0 su #FF4BFF (luma gamma < 0,5) | 2.07:1 | Non passa nemmeno 3:1; sotto 3 nel 7,1% dei colori, sotto 4,5 nel 26,1% |
| Miglior scelta fra #E0E0E0 e #1E1E1E, caso peggiore #66758A | 3.55:1 | Non passa AA: servono nero e bianco puri |
| Regola attuale su #FF5722 (#E0E0E0; TeamInk dà nero 6,64) | 2.40:1 | Non passa oggi, AA con TeamInk |
| Zona + #1A237E sul nero | 1.59:1 | Sotto 3:1: stroke 2dp #E0E0E0 (15,91 sul nero); serve al 18,4% dei colori |
| Stroke #E0E0E0 su #1A237E (correzione del 13,24 della proposta, che era #FFFFFF) | 10.03:1 | Informativo: lo stroke conta contro il nero |
| Glifo #FFFFFF su zona #1A237E | 13.24:1 | AA |
| Glifo #000000 su zona #FFD600 | 14.87:1 | AA |
| Glifo #000000 su zona #76FF03 | 16.08:1 | AA |
| Zona spenta #2C2C2C su #000000 | 1.50:1 | Componente disabilitato, esente da 1.4.11; lo segnala la barra colore e la striscia PARTITA FINITA |
| #6E6E6E contorno del −1 e dello slot su #000000 | 4.12:1 | Sopra 3:1 per la grafica |
| #6E6E6E contorno dei pulsanti del foglio su #1E1E1E | 3.27:1 | Sopra 3:1 per la grafica |
| #E0E0E0 su #F50057 (pulsanti pieni oggi) | 3.17:1 | Non passa AA a 14-16sp |
| #121212 su #F50057 | 4.48:1 | Non passa per 0,02: per questo il testo è nero puro |
| #000000 su #F50057 (TERMINA, AVANTI, podio) | 5.02:1 | AA |
| #E0E0E0 su #00E5FF (icona FAB giocatori oggi) | 1.17:1 | Non passa |
| #000000 su #00E5FF (nuovo colorOnSecondary) | 13.65:1 | AA |
| #E0E0E0 su #FFD600 / #76FF03 (pulsanti colore delle impostazioni oggi) | 1.07:1 | Non passa (1,01 sul verde) |
| #FF1744 testo ANNULLA su #1E1E1E (oggi) | 4.33:1 | Non passa per il testo; va bene come icona e cestino |
| #000000 su #FF1744 (slot portiere scaduto, dopo L8) | 5.46:1 | AA |
| #FF6E6E SCARTA su #2C2C2C | 5.13:1 | AA (6,12 su #1E1E1E) |
| #1A237E come testo su #1E1E1E (rose e registro oggi) | 1.26:1 | Non passa: il colore resta sulla barretta; come testo il colore di squadra sta sotto 4,5 nel 46,4% dei colori |
| #0D47A1 nome squadra su #121212 (PDF oggi) | 2.17:1 | Non passa: bande con TeamInk |
| #F50057 rosa + contro la card gialla predefinita (oggi) | 2.96:1 | Non passa 3:1 per la grafica |
| #00E5FF rank su #FFD600 (podio statistiche oggi) | 1.09:1 | Non passa |
| Sole (1500 nit più 300 di riflesso): #FFFFFF su #000000 | 6.00:1 | Riferimento della proposta |
| Sole: #E0E0E0 su #000000 | 4.73:1 | Motivo del bianco puro per le cifre |
| Sole: #1E1E1E su #FFD600 (numero oggi, squadra 1) | 4.20:1 | Peggio del bianco su nero |
| Sole: #1E1E1E su #00E5FF (numero oggi, squadra 2) | 3.91:1 | Peggio |
| Sole: #FFFFFF su colore di luminanza 0,179 (correzione del 3,15) | 3.17:1 | Peggio |
| Sole: numero su colore di squadra con TeamInk, colore appena sopra la soglia (inchiostro nero) | 1.90:1 | Caso peggiore della variante 'numero sul colore': argomento decisivo per il nero |

### Piano

#### 1. Due testi che oggi fanno danni. watch_batch_rejected in values e values-it diventa «L'orologio ha mandato p

1. Due testi che oggi fanno danni. watch_batch_rejected in values e values-it diventa «L'orologio ha mandato punti che non sono entrati in questa partita.», senza l'invito a chiuderla. onboarding_finish 'Fines' diventa «Inizia». Si può fare subito, prima di L1-L3.

- **Costo:** piccolo
- **File:** `mobile/src/main/res/values/strings.xml`, `mobile/src/main/res/values-it/strings.xml`
- **Verifica:** Lettura del diff. Su emulatore con lingua italiana: il tasto finale del tutorial dice Inizia. Grep che nessuna stringa contenga più 'Chiudila'.

#### 2. TeamInk in :core con il suo test JVM.

2. TeamInk in :core con il suo test JVM.

- **Costo:** piccolo
- **File:** `core/src/main/kotlin/it/vantaggi/scoreboardessential/core/TeamInk.kt (nuovo)`, `core/src/test/kotlin/it/vantaggi/scoreboardessential/core/TeamInkTest.kt (nuovo)`
- **Verifica:** ./gradlew :core:test. Il test scorre l'RGB a passo 3 e fallisce se il minimo scende sotto 4,5 (atteso 4,58 su #5D60FF). Casi fissi: #FFD600 nero, #1A237E bianco, #F50057 nero, #FF1744 nero.

#### 3. Contrasti di L11 senza toccare la struttura. colorOnPrimary e colorOnSecondary diventano @color/asphalt_bla

3. Contrasti di L11 senza toccare la struttura. colorOnPrimary e colorOnSecondary diventano @color/asphalt_black. Nei pulsanti colore delle impostazioni setTextColor e iconTint = TeamInk. Nel registro il testo passa a colorOnSurface, con il colore solo su team_indicator. Le etichette delle rose e delle formazioni diventano tag nel colore della squadra con testo TeamInk; 'No formation' da risorsa. Titolo del marcatore da risorsa.

- **Costo:** piccolo
- **File:** `mobile/src/main/res/values/themes.xml`, `mobile/src/main/java/it/vantaggi/scoreboardessential/ui/MatchSettingsActivity.kt`, `mobile/src/main/java/it/vantaggi/scoreboardessential/MatchLogAdapter.kt`, `mobile/src/main/java/it/vantaggi/scoreboardessential/MainActivity.kt (298, 311, updateFormation)`, `mobile/src/main/res/layout/content_scoreboard_details.xml`, `mobile/src/main/res/layout/dialog_select_scorer.xml`, `mobile/src/main/res/values/strings.xml`, `mobile/src/main/res/values-it/strings.xml`
- **Verifica:** Su Pixel_9a: impostazioni con giallo e verde predefiniti, la scritta è nera e leggibile. Squadra 2 in #1A237E: rose e registro leggibili, barretta visibile. FAB e pulsanti pieni con testo nero. ./gradlew :mobile:lint senza nuove voci.

#### 4. Schermo acceso, toast, vibrazione e minuto. FLAG_KEEP_SCREEN_ON acceso con almeno un SCORE e partita non fi

4. Schermo acceso, toast, vibrazione e minuto. FLAG_KEEP_SCREEN_ON acceso con almeno un SCORE e partita non finita, tolto altrimenti. Via le due Toast.makeText. playGoalVibrationPattern (500ms) sostituita da EFFECT_CLICK. TimeUtils.matchMinute(ms) usata da addMatchEvent nel calcio; timestamp vuoto negli sport senza cronometro. Colonna del minuto wrap_content con minWidth 40dp e maxLines 1.

- **Costo:** piccolo
- **File:** `mobile/src/main/java/it/vantaggi/scoreboardessential/MainActivity.kt`, `mobile/src/main/java/it/vantaggi/scoreboardessential/MainViewModel.kt (1384-1385)`, `mobile/src/main/java/it/vantaggi/scoreboardessential/utils/TimeUtils.kt`, `mobile/src/test/java/it/vantaggi/scoreboardessential/utils/TimeUtilsTest.kt`, `mobile/src/main/res/layout/match_event_item.xml`
- **Verifica:** TimeUtilsTest: 0 → 1', 59999 → 1', 60000 → 2', 3910000 → 66'. Il test gira anche con TimeZone.setDefault(Asia/Kolkata). Emulatore: timeout schermo a 15s, dopo il primo + lo schermo resta acceso per 2 minuti e dopo TERMINA si spegne. Rotazione con l'orologio spento: nessun toast. La vibrazione breve va provata su dispositivo.

#### 5. Contenitore e foglio PARTITA. activity_main con BottomSheetBehavior sulla NestedScrollView del dettaglio, O

5. Contenitore e foglio PARTITA. activity_main con BottomSheetBehavior sulla NestedScrollView del dettaglio, OnBackPressedCallback, azioni del foglio su due righe (più AZZERA TEMPO nel calcio). Via FAB, FabOverlap e keepFabsOffMatchActions. Cancellato layout-land/activity_main.xml. Per ora ≡ PARTITA si aggiunge nella riga dell'ingranaggio esistente.

- **Costo:** medio
- **File:** `mobile/src/main/res/layout/activity_main.xml`, `mobile/src/main/res/layout-land/activity_main.xml (cancellato)`, `mobile/src/main/res/layout/content_scoreboard_details.xml`, `mobile/src/main/java/it/vantaggi/scoreboardessential/MainActivity.kt`, `mobile/src/main/java/it/vantaggi/scoreboardessential/utils/FabOverlap.kt (cancellato)`, `mobile/src/test/java/it/vantaggi/scoreboardessential/utils/FabOverlapTest.kt (cancellato)`, `mobile/src/androidTest/java/it/vantaggi/scoreboardessential/MainActivityLayoutTest.kt`, `mobile/lint-baseline.xml`
- **Verifica:** Nuovi test strumentati in MainActivityLayoutTest: il foglio è HIDDEN all'avvio, ≡ lo porta a EXPANDED, pressBack lo richiude e un secondo pressBack esce. Il test dei FAB viene tolto. Emulatore: scorrere il registro nel foglio con 30 righe; il trascinamento del foglio non ruba lo scorrimento della lista. Verticale e orizzontale.

#### 6. Colonna di gioco a slot fissi. Riscrittura di content_scoreboard_live: barra con tempo come pulsante e slot

6. Colonna di gioco a slot fissi. Riscrittura di content_scoreboard_live: barra con tempo come pulsante e slot del portiere fisso di sola lettura, nomi con −1 e pallino, numeri bianchi a dimensione fissa per sport, dettaglio, striscia con ANNULLA spento e non GONE (dialogo ancora presente), zone + con TeamInk e stroke. Via VS e animazioni di colore; dimen nuovi anche in values-land; fondo #000000; contentDescription dinamiche; testi del tutorial aggiornati.

- **Costo:** grande
- **File:** `mobile/src/main/res/layout/content_scoreboard_live.xml`, `mobile/src/main/res/values/dimens.xml`, `mobile/src/main/res/values-land/dimens.xml`, `mobile/src/main/res/values/colors.xml`, `mobile/src/main/res/values/themes.xml`, `mobile/src/main/java/it/vantaggi/scoreboardessential/MainActivity.kt`, `mobile/src/main/java/it/vantaggi/scoreboardessential/utils/AnimationUtils.kt`, `mobile/src/main/res/values/strings.xml`, `mobile/src/main/res/values-it/strings.xml`, `mobile/src/androidTest/java/it/vantaggi/scoreboardessential/MainActivityLayoutTest.kt`
- **Verifica:** Nuovo test strumentato laZonaPiuNonSiSposta. Legge getLocationOnScreen e le misure di team1_add_button_card, team2_add_button_card e last_action_strip, poi le confronta dopo: un tocco su + (ANNULLA si accende), tocco sul tempo (START → PAUSA), conferma di ANNULLA (ANNULLA si spegne) e, nel padel, 24 tocchi fino al 6-0 (partita finita). Differenza ammessa 0px. Si riscrivono i 5 test esistenti; il test della rinomina resta. Test che la zona + misuri almeno 48dp e che il numero abbia la stessa textSize con '0' e con '15'. Emulatore Pixel_9a in verticale e orizzontale, carattere di sistema al 200%, squadra 2 in #1A237E (stroke visibile). Confronto con le bozze SVG.

#### 7. Striscia dell'ultima azione: testo dal primo SCORE, scorciatoia del marcatore (apriAttribuzione estratta da

7. Striscia dell'ultima azione: testo dal primo SCORE, scorciatoia del marcatore (apriAttribuzione estratta dal lambda di MatchLogAdapter), messaggi temporanei di 3s al posto delle Snackbar goal_by e watchBatchApplied, setAnchorView(striscia) su tutte le altre Snackbar.

- **Costo:** medio
- **File:** `mobile/src/main/java/it/vantaggi/scoreboardessential/MainActivity.kt`, `mobile/src/main/res/values/strings.xml`, `mobile/src/main/res/values-it/strings.xml`
- **Verifica:** Test strumentato: dopo + nel calcio la striscia contiene il nome della squadra e 'CHI HA SEGNATO'; toccandola si apre SelectScorerDialogFragment.TAG. Test che la posizione Y di una Snackbar mostrata sia sopra la striscia. Emulatore: attribuire l'ultimo gol dalla striscia e controllare che la riga del registro mostri il marcatore.

#### 8. Barra e servizio per la racchetta (testo da periodLabel o etichetta dello sport più SERVE, pallino da servi

8. Barra e servizio per la racchetta (testo da periodLabel o etichetta dello sport più SERVE, pallino da servingSide, didascalia GAME o SET · GAME). Partita finita dichiarata: zone spente toccabili con tre tick, VINCE nella barra, sconfitto grigio. Dialogo di fine partita con il punteggio del display, SALVA quando matchOver, SCARTA in #FF6E6E. Vibrazioni per game e fine partita solo su tocco locale. Da fare DOPO L1: altrimenti il dialogo mostra 5-3 e poi risponde 'non iniziata'.

- **Costo:** piccolo
- **File:** `mobile/src/main/java/it/vantaggi/scoreboardessential/MainActivity.kt (bindPeriod, bindScoreDetail, applyMatchOver, showEndMatchConfirmation)`, `mobile/src/main/res/values/colors.xml`, `mobile/src/main/res/values/strings.xml`, `mobile/src/main/res/values-it/strings.xml`
- **Verifica:** Emulatore: nel padel, al primo punto la barra dice PADEL · SERVE <squadra> e il pallino passa di lato a ogni game. Sul 5-3 e 30-15 TERMINA mostra «game 5-3 · punto 30-15». Al 6-3 la barra dice VINCE, un tocco sulla zona dà PARTITA FINITA nella striscia e ANNULLA riapre. Tennis: SET 2 dopo il primo set. Le vibrazioni vanno provate su dispositivo.

#### 9. Stato dell'orologio persistente: WatchNotice al posto dei due SingleLiveEvent, azzerato in startNewMatch. I

9. Stato dell'orologio persistente: WatchNotice al posto dei due SingleLiveEvent, azzerato in startNewMatch. Icona #E0E0E0/#9E9E9E con badge #FF1744; card d'avviso in cima al foglio; «N PUNTI DALL'OROLOGIO» nella striscia.

- **Costo:** medio
- **File:** `mobile/src/main/java/it/vantaggi/scoreboardessential/MainViewModel.kt`, `mobile/src/main/java/it/vantaggi/scoreboardessential/MainActivity.kt`, `mobile/src/main/res/layout/content_scoreboard_details.xml`, `mobile/src/main/res/layout/content_scoreboard_live.xml`, `mobile/src/main/res/values/strings.xml`, `mobile/src/main/res/values-it/strings.xml`
- **Verifica:** Test JVM del ViewModel, sullo stile dei test esistenti di applyWatchBatch: dopo un batch rifiutato watchNotice è Rejected e resta tale dopo una rotazione; dopo startNewMatch è null. Emulatore con Wear_OS_Small_Round: punti offline al polso su una partita iniziata dal telefono, poi riconnessione; il badge resta finché non si avvia una partita nuova.

#### 10. Contorno, sistema: mappatura completa dei ruoli M3, materialAlertDialogTheme globale, valori iniziali dell

10. Contorno, sistema: mappatura completa dei ruoli M3, materialAlertDialogTheme globale, valori iniziali delle squadre da ColorRepository.

- **Costo:** piccolo
- **File:** `mobile/src/main/res/values/themes.xml`, `mobile/src/main/java/it/vantaggi/scoreboardessential/MainViewModel.kt (386-389)`, `mobile/src/main/java/it/vantaggi/scoreboardessential/repository/ColorRepository.kt`
- **Verifica:** Foto prima e dopo di tutti i dialoghi (marcatore, nome, fine partita, annulla, reset tempo) e degli avatar delle rose: nessun viola. Primo avvio su emulatore pulito: squadre giallo e verde, niente arancio o lime.

#### 11. Contorno, schermate: impostazioni (anteprima nel selettore, riga delle regole), cronologia (Team.color, vi

11. Contorno, schermate: impostazioni (anteprima nel selettore, riga delle regole), cronologia (Team.color, vincitore e sconfitto, sport e durata; la riga dei set solo quando esiste il ViewModel della cronologia di L2), statistiche (podio rosa, stringhe), giocatori (TeamInk su avatar e chip, 48dp, descrizioni), onboarding (AVANTI nero), PDF (bande con TeamInk, niente TABELLINO senza marcatore, use{}).

- **Costo:** grande
- **File:** `mobile/src/main/res/layout/activity_match_settings.xml`, `mobile/src/main/res/layout/dialog_color_picker.xml`, `mobile/src/main/res/layout/match_item.xml`, `mobile/src/main/java/it/vantaggi/scoreboardessential/MatchHistoryAdapter.kt`, `mobile/src/main/res/layout/item_player_stat.xml`, `mobile/src/main/java/it/vantaggi/scoreboardessential/ui/statistics/StatisticsAdapter.kt`, `mobile/src/main/res/layout/item_player_management.xml`, `mobile/src/main/java/it/vantaggi/scoreboardessential/PlayersManagementAdapter.kt`, `mobile/src/main/java/it/vantaggi/scoreboardessential/utils/RoleChipExtensions.kt`, `mobile/src/main/res/layout/pdf_match_report.xml`, `mobile/src/main/java/it/vantaggi/scoreboardessential/utils/MatchReportUtils.kt`, `mobile/src/main/res/values/strings.xml`, `mobile/src/main/res/values-it/strings.xml`
- **Verifica:** Foto prima e dopo per ogni schermata, in verticale. PDF di un padel e di un calcio aperti sul telefono: nel padel niente TABELLINO. Una squadra #1A237E leggibile ovunque. MatchReportUtilsTest ancora verde (il benchmark e' stato tolto con L12). lint senza nuove voci.

#### 12. ANNULLA con un tocco, senza dialogo, nel padel e nel tennis: doppio tick e «ANNULLATO: PUNTO ROSSI» per 3s

12. ANNULLA con un tocco, senza dialogo, nel padel e nel tennis: doppio tick e «ANNULLATO: PUNTO ROSSI» per 3s. Il calcio tiene il dialogo. Da fare dopo L3 e dopo la guardia di L7 in addRemotePoint: prima, un punto inerte dell'orologio lascia una riga fantasma e l'annulla toglie un punto vero.

- **Costo:** piccolo
- **File:** `mobile/src/main/java/it/vantaggi/scoreboardessential/MainActivity.kt (setupMatchActions, undoGoalButton)`
- **Verifica:** Test strumentato: nel padel, + poi ANNULLA senza dialogo riporta 0-0 e la striscia mostra ANNULLATO. Nel calcio compare ancora il dialogo.

#### 13. Portiere comandabile dal telefono, dopo L8: tocco sullo slot fermo o scaduto per avviare dalla durata pien

13. Portiere comandabile dal telefono, dopo L8: tocco sullo slot fermo o scaduto per avviare dalla durata piena, tocco in corso per ripartire da capo (cambio avvenuto). Stato SCADUTO: slot pieno #FF1744 con CAMBIO in #000000.

- **Costo:** piccolo
- **File:** `mobile/src/main/res/layout/content_scoreboard_live.xml`, `mobile/src/main/java/it/vantaggi/scoreboardessential/MainActivity.kt`, `mobile/src/main/java/it/vantaggi/scoreboardessential/MainViewModel.kt (solo lettura del nuovo evento di scadenza di L8)`
- **Verifica:** Emulatore: durata 20s dalle impostazioni, tocco sullo slot, dopo 20s lo slot diventa rosso senza che il + si sposti (stesso test di laZonaPiuNonSiSposta). Registro senza 'expired' falsi dopo un riavvio.

#### 14. COPPIE nel foglio, insieme al rimedio L2 sulle rose persistenti: SportCapabilities.hasRoles diviso in hasF

14. COPPIE nel foglio, insieme al rimedio L2 sulle rose persistenti: SportCapabilities.hasRoles diviso in hasFormations e playersPerSide (null nel calcio, 2 nel padel, 1 nel tennis). Due posti numerati per lato, scambio da 48dp, lucchetto dopo il primo punto; il nome di chi serve nella barra quando servingPlayerId è noto.

- **Costo:** medio
- **File:** `core/src/main/kotlin/it/vantaggi/scoreboardessential/core/SportRules.kt`, `core/src/main/kotlin/it/vantaggi/scoreboardessential/core/FootballRules.kt`, `core/src/main/kotlin/it/vantaggi/scoreboardessential/core/RacketRules.kt`, `mobile/src/main/res/layout/content_scoreboard_details.xml`, `mobile/src/main/java/it/vantaggi/scoreboardessential/MainActivity.kt`, `mobile/src/main/java/it/vantaggi/scoreboardessential/MainViewModel.kt`
- **Verifica:** Test in :core su capabilities. Emulatore, padel: aggiungere 2+2 giocatori, la barra dice SERVE <giocatore>; l'export Padel Elite non risponde più 'Servono esattamente 4 giocatori'. Dopo rotazione e dopo la freccia 'su' le coppie restano (L2).

#### 15. Registro del padel e del tennis con una riga per game, derivato dal motore (MatchNarrative in :core). Solo

15. Registro del padel e del tennis con una riga per game, derivato dal motore (MatchNarrative in :core). Solo dopo L3 e da fare insieme alle sue funzioni (addScorer, undoLastGoal, rebuildEventsAndUndo), mai in parallelo.

- **Costo:** grande
- **File:** `core/src/main/kotlin/it/vantaggi/scoreboardessential/core/MatchNarrative.kt (nuovo)`, `mobile/src/main/res/layout/match_game_item.xml (nuovo)`, `mobile/src/main/java/it/vantaggi/scoreboardessential/MatchLogAdapter.kt`, `mobile/src/main/java/it/vantaggi/scoreboardessential/MainViewModel.kt`
- **Verifica:** Test JVM di MatchNarrative contro i registri di RacketRulesTest. Emulatore: una partita consegnata dall'orologio mostra le righe dei game.

### Schermata per schermata

#### Gioco: contenitore e foglio PARTITA

- **Ora:** activity_main.xml verticale: CoordinatorLayout con un LinearLayout che contiene content_scoreboard_live (wrap_content) e una NestedScrollView a peso 1 con content_scoreboard_details. Sopra ci sono due FAB (statistiche rosa con icona #E0E0E0 a 3,17, giocatori ciano a 1,17), che keepFabsOffMatchActions/FabOverlap nasconde quando coprono HISTORY/END/SHARE. layout-land/activity_main.xml fa scorrere tutto, + compresi.
- **Dopo:** Un solo activity_main.xml: CoordinatorLayout main_root con fondo #000000. Contiene content_scoreboard_live a tutta altezza (la colonna di gioco) e una NestedScrollView id match_sheet con app:layout_behavior BottomSheetBehavior (behavior_hideable true, behavior_skipCollapsed true, stato iniziale STATE_HIDDEN, fondo #121212, taglio 12dp in alto). Il foglio include content_scoreboard_details. Si apre col pulsante ≡ PARTITA della barra (STATE_EXPANDED). Si chiude trascinando, con CHIUDI in cima al foglio o con indietro: un OnBackPressedCallback abilitato solo quando lo stato non è HIDDEN. I FAB spariscono; STATISTICHE, GIOCATORI e IMPOSTAZIONI diventano pulsanti del foglio. layout-land/activity_main.xml si cancella: l'orizzontale usa lo stesso file con values-land/dimens.xml. Si cancellano FabOverlap.kt, FabOverlapTest.kt e keepFabsOffMatchActions/boxInWindow in MainActivity.
- **Perche':** Mentre si gioca servono solo numeri, tempo o set, servizio, portiere, +, correzione, annulla e ultimo marcatore. Rose, registro, formazioni e fine partita si consultano e non si usano durante il gioco: stanno a un tocco. Il foglio vive nella stessa Activity perché un'Activity nuova creerebbe un altro MainViewModel, che è il difetto alto di L2.
- **File:** `mobile/src/main/res/layout/activity_main.xml`, `mobile/src/main/res/layout-land/activity_main.xml (cancellato)`, `mobile/src/main/res/layout/content_scoreboard_details.xml`, `mobile/src/main/java/it/vantaggi/scoreboardessential/MainActivity.kt`, `mobile/src/main/java/it/vantaggi/scoreboardessential/utils/FabOverlap.kt (cancellato)`, `mobile/src/test/java/it/vantaggi/scoreboardessential/utils/FabOverlapTest.kt (cancellato)`, `mobile/src/androidTest/java/it/vantaggi/scoreboardessential/MainActivityLayoutTest.kt`, `mobile/lint-baseline.xml (voci rimappate, non rigenerate)`
- **Difetti della validazione:** L11 Contrasti insufficienti: icona del FAB giocatori (circa 1,17:1) e testo dei pulsanti pieni (circa 3,17:1) (i FAB escono dal gioco); L2 La cronologia (e l'onboarding) crea un secondo MainViewModel completo (il foglio non ne aggiunge un terzo)

#### Gioco: colonna a slot fissi (verticale)

- **Ora:** content_scoreboard_live.xml: una card d'intestazione a righe wrap_content (annulla GONE/VISIBLE, periodo GONE/VISIBLE, Flow tempo più START/RESET che va a capo, portiere GONE/VISIBLE) e sotto due card di squadra da 280dp con nome, numero, dettaglio GONE/VISIBLE e i pulsanti − e +. Ogni commutazione sposta il + sotto il dito.
- **Dopo:** content_scoreboard_live.xml riscritto come LinearLayout verticale a tutta altezza: barra (56dp), riga nomi (48dp), riga numeri (0dp, peso 1), slot dettaglio (48dp, GONE nel calcio), striscia (56dp), zone + (112dp). Gli id delle zone restano team1_add_button_card e team2_add_button_card, così test e listener non cambiano nome. Nuovi dimen: game_bar_height, game_names_height, game_strip_height, game_zone_height, score_text_max, score_text_min, con valori propri in values-land. Spariscono score_card_min_height, score_section_min_height e vs_indicator.
- **Perche':** La fascia bassa è quella che il pollice della mano che regge il telefono raggiunge senza cambiare presa. In alto vanno solo cose da leggere o comandi rari. Se niente cambia misura, il bersaglio sta sempre dove il dito se lo aspetta, anche senza guardare.
- **File:** `mobile/src/main/res/layout/content_scoreboard_live.xml`, `mobile/src/main/res/values/dimens.xml`, `mobile/src/main/res/values-land/dimens.xml`, `mobile/src/main/java/it/vantaggi/scoreboardessential/MainActivity.kt (initializeViews, applyCapabilities, refreshUndoButtonVisibility, updateKeeperTimerTextView, bindPeriod, bindScoreDetail)`
- **Difetti della validazione:** Fase D, residuo di D4: il + a metà schermo e l'intestazione che cambia altezza (non è in VALIDAZIONE, è la contestazione esplicita della D4)

#### Gioco: il numero

- **Ora:** 86sp (56 in orizzontale) in #1E1E1E o #E0E0E0 sopra la card nel colore della squadra, scelto con la luma gamma sotto 0,5 (applyReadableTextColor, MainActivity 815-827): nel caso peggiore 2,07. A ogni punto: scala, rotazione e lampo verso colorPrimary e colorSecondary (AnimationUtils 69-104; il ciano sulla card ciano predefinita fa 1,0 per 400ms), e il VS ruota di 360° (animateVsIndicator).
- **Dopo:** Due TextView affiancate in #FFFFFF su #000000, maxLines 1, includeFontPadding false, dimensione fissa calcolata una volta per sport (vedi i token), accessibilityLiveRegion polite, contentDescription «ROSSI, 30». Non sono cliccabili. Il colore della squadra sta solo nella barretta sotto il nome e nella zona +. Animazione: solo scala 1 → 1,06 → 1 in 150ms (trasformazione, nessun layout). Si tolgono playNativeGoalAnimation, animateVsIndicator e il VS. applyReadableTextColor viene sostituita da TeamInk dove serve ancora (zone, foglio). A partita finita il numero dello sconfitto passa a #9E9E9E.
- **Perche':** A 2m la cifra passa da circa 9,7mm a circa 16,9mm di altezza. Al sole (modello 1500 nit più 300 di riflesso) bianco su nero vale 6,0. Oggi vale 4,20 sul giallo e 3,91 sul ciano predefiniti; con un colore qualsiasi, anche con l'inchiostro WCAG ottimo, si scende a 1,90. Il numero non dipende più dal colore scelto dall'utente.
- **File:** `mobile/src/main/res/layout/content_scoreboard_live.xml`, `mobile/src/main/res/values/colors.xml (ink_white)`, `mobile/src/main/res/values/themes.xml (TextAppearance.App.Game.Score)`, `mobile/src/main/java/it/vantaggi/scoreboardessential/utils/AnimationUtils.kt`, `mobile/src/main/java/it/vantaggi/scoreboardessential/MainActivity.kt`
- **Difetti della validazione:** L11 Etichette delle rose e righe dei punti nel registro nel colore grezzo della squadra (stessa regola: il colore non porta testo)

#### Gioco: zone +

- **Ora:** MaterialCardView 72x72dp rosa #F50057 con glifo #E0E0E0 (3,17), elevazione 8, a metà schermo, a 20dp dal −. Contro la card gialla predefinita il rosa fa 2,96; con una squadra rosa sparisce. A ogni tocco una vibrazione da 500ms in tutti gli sport. contentDescription fissa «aumenta squadra 1». A partita finita alpha 0,4 e isClickable false: il tocco non dà nessuna risposta.
- **Dopo:** Due MaterialCardView a peso 1, alte 112dp, margini esterni 16dp e 8dp ciascuna verso il centro (16dp di stacco neutro). cardBackgroundColor è il colore della squadra, shape StreetButton, elevazione 0. Lo stroke di 2dp #E0E0E0 compare solo se contrasto(colore, nero) < 3. rippleColor è l'inchiostro TeamInk al 24%. Dentro, ImageView ic_plus 48dp con tint TeamInk. Riscontro: scala 0,96 → 1 in 100ms e VibrationEffect.createPredefined(EFFECT_CLICK) dal Vibrator (funziona anche con il feedback tattile di sistema spento). contentDescription dinamica da risorsa: «Punto a ROSSI. 30 a 15» o «Gol a ROSSI. 2 a 1».
- **Perche':** Il bersaglio primario è il più grande dello schermo e sta nella zona del pollice (Fitts). Il colore dice di chi è senza bisogno di leggere. Qualunque colore scelga l'utente, il glifo vale almeno 4,58 e la zona si distingue dal nero di almeno 3:1, stroke compreso. Una vibrazione breve e sempre uguale conferma il tocco senza chiedere gli occhi.
- **File:** `mobile/src/main/res/layout/content_scoreboard_live.xml`, `mobile/src/main/java/it/vantaggi/scoreboardessential/MainActivity.kt (setupScoreButtons, observer dei colori, playGoalVibrationPattern sostituita)`, `core/src/main/kotlin/it/vantaggi/scoreboardessential/core/TeamInk.kt (nuovo)`, `mobile/src/main/res/values/strings.xml`, `mobile/src/main/res/values-it/strings.xml`
- **Difetti della validazione:** L11 Contrasti insufficienti: testo dei pulsanti pieni (circa 3,17:1); L11 Sulla card TalkBack non legge il nome della squadra (la zona annuncia squadra e punteggio)

#### Gioco: striscia dell'ultima azione e ANNULLA

- **Ora:** undo_goal_button in rosso #FF1744 su #1E1E1E (4,33, sotto AA a 14sp) passa da GONE a VISIBLE nella prima riga dell'intestazione (refreshUndoButtonVisibility 458-468). Chiede sempre conferma con un dialogo. Il marcatore si attribuisce solo toccando la riga nel registro. Circa 12 Snackbar compaiono in basso, dove ora staranno le zone +.
- **Dopo:** Riga fissa di 56dp su #1E1E1E. A sinistra una TextView a peso 1, 16sp bold maiuscolo #E0E0E0, maxLines 1, ellipsize end. A destra ANNULLA, MaterialButton testuale largo 96dp con icona e testo #E0E0E0; senza niente da annullare è disabilitato (alpha 0,38), mai GONE. Il testo a sinistra viene dal primo evento SCORE di matchEvents. Racchetta: «PUNTO ROSSI · 40-30» (primari del display). Calcio: «GOL ROSSI · MARCO B.» oppure, se playerId è null, «GOL ROSSI · CHI HA SEGNATO? ›»; toccando si apre SelectScorerDialogFragment con l'engineIndex di quell'evento, con la stessa funzione oggi passata a MatchLogAdapter, estratta in apriAttribuzione(evento). Senza eventi: «NESSUN GOL» o «NESSUN PUNTO». Messaggi temporanei di 3s (postDelayed sulla striscia, poi si torna al testo base): «GOL DI MARCO B.» al posto della Snackbar goal_by_message, «CORREZIONE −1 ROSSI», «ANNULLATO», «N PUNTI DALL'OROLOGIO». In questa fase ANNULLA mantiene il dialogo di conferma in tutti gli sport. Tutte le Snackbar rimaste in MainActivity ricevono setAnchorView(R.id.last_action_strip).
- **Perche':** Cambia il testo, mai l'altezza. ANNULLA spento dice la verità: una partita arrivata dall'orologio mostra 5-3 con ANNULLA spento e la striscia su NESSUN PUNTO, invece di un pulsante che non c'è. Nel calcio l'ultimo gol si attribuisce senza scorrere il registro, rispettando l'attenzione (Fase D, punto di attrito del marcatore). Nessun messaggio copre più le zone +.
- **File:** `mobile/src/main/res/layout/content_scoreboard_live.xml`, `mobile/src/main/java/it/vantaggi/scoreboardessential/MainActivity.kt (refreshUndoButtonVisibility, setupRecyclerViews, onScorerSelected, tutte le Snackbar.make)`, `mobile/src/main/res/values/strings.xml`, `mobile/src/main/res/values-it/strings.xml`
- **Difetti della validazione:** L3 Partita consegnata dall'orologio: nessuna riga nel registro, e l'annullamento non fa niente (resa visibile: ANNULLA spento e striscia vuota); L11 Contrasti insufficienti (testo rosso dell'annulla a 4,33); L3 attributeScorer incrementa i gol anche quando l'indice non punta più al punto scelto (la scorciatoia usa lo stesso percorso del registro: non peggiora, non corregge)

#### Gioco: barra superiore nel calcio (tempo e portiere)

- **Ora:** Etichetta TEMPO PARTITA (HeadlineMedium forzato a 14sp), tempo a 48sp con letterSpacing 0,08, START/PAUSA e RESET in un Flow che va a capo. La riga PORTIERE (valore 28sp in rosa) è VISIBLE solo se il valore è > 0. Il valore diventa la durata configurata quando si applicano le impostazioni, quindi di solito mostra 05:00 fermo. Il conto parte solo dall'orologio: startKeeperTimer è chiamato solo con fromRemote=true. Alla scadenza il valore va a 0 e la riga diventa GONE: l'intestazione si accorcia e il + salta proprio in quel momento. Il dialogo showKeeperTimerExpiredAlert non parte mai, perché nessuno osserva showKeeperTimerExpired (L8); restano solo la notifica e la vibrazione del servizio. Ingranaggio e icona dell'orologio verde #76FF03.
- **Dopo:** Riga fissa di 56dp. A sinistra il pulsante del tempo 132x48 su #1E1E1E: glifo play/pausa e «34:12» a 32sp #FFFFFF con cifre tabulari. Tocca startStopTimer; lo stato si legge dal glifo, non da una parola che cambia larghezza. Poi lo slot PORTIERE 112x48 (StreetBadge, contorno 1dp #6E6E6E): didascalia 12sp #9E9E9E, valore 24sp. Fermo: durata in #9E9E9E. In corso: conto in #E0E0E0. A 0: «00:00» in #9E9E9E. È sempre VISIBLE nel calcio e GONE solo in applyCapabilities negli sport senza hasAuxCountdown. In questa fase non è toccabile. A destra l'icona dell'orologio (area 48x48) e ≡ PARTITA (64x48, didascalia 12sp). RESET del tempo (col suo dialogo) e impostazioni passano nel foglio. Si toglie showKeeperTimerExpiredAlert, che è codice morto. Lo stato SCADUTO (slot pieno #FF1744, «CAMBIO» in #000000, 5,46) arriva solo dopo L8, che introduce un evento di scadenza dedicato: oggi il collector confonde pausa e azzeramento con la scadenza.
- **Come e' stato fatto (passo 6, 30 settembre 2026):** a 360dp la barra ha 328dp utili, e con
  le misure qui sopra ≡ PARTITA usciva dallo schermo (quasi tutto col font a 1,3). Nel codice: il
  pulsante del foglio e' solo il glifo ≡ (48x48, descrizione a voce «Apri il foglio della partita»);
  lo slot del portiere e' largo quanto il testo, con didascalia e valore in dp (non seguono il font di
  sistema) e didascalia inglese KEEPER; il pulsante del tempo ha la larghezza minima di «100:00»,
  cosi' non si allarga oltre i 99 minuti. Se lo spazio non basta cede solo il testo del periodo.
- **Perche':** La fascia alta è la più lontana dal pollice, quindi ospita solo cose da leggere o comandi rari. Uno slot che scompare alla scadenza è il caso peggiore: sposta il bersaglio e nasconde l'informazione nello stesso istante. Mostrare SCADUTO prima di L8 significherebbe dirlo anche dopo una pausa.
- **File:** `mobile/src/main/res/layout/content_scoreboard_live.xml`, `mobile/src/main/java/it/vantaggi/scoreboardessential/MainActivity.kt (updateKeeperTimerTextView, observer isMatchTimerRunning e isWearConnected, showKeeperTimerExpiredAlert tolta)`, `mobile/src/main/res/values/themes.xml (TextAppearance.App.Game.Clock)`, `mobile/src/main/res/values/strings.xml`, `mobile/src/main/res/values-it/strings.xml`
- **Difetti della validazione:** L8 Quando il timer del portiere scade ... la finestra di scadenza è codice morto (l'interfaccia smette di contarci; lo stato SCADUTO arriva con L8); L8 'Keeper timer expired!' scritto nel registro anche su pausa o azzeramento (motivo per cui la barra non mostra SCADUTO prima di L8)

#### Gioco: barra e servizio in padel e tennis

- **Ora:** match_period_textview è visibile solo se periodLabel non è null. RacketRules.display (66-72) lo lascia null nel padel a set unico fuori dal tie-break, quindi chi serve non compare mai, anche se servingSide è calcolato e spedito all'orologio. Il dettaglio dei game sta dentro ogni card a 16sp, ognuno dal proprio punto di vista (3-2 a sinistra, 2-3 a destra).
- **Dopo:** A sinistra nella barra una TextView di una riga, 16sp bold, larghezza fino all'icona dell'orologio: [periodLabel oppure l'etichetta dello sport] più «· SERVE <NOME>» quando servingSide non è null. Esempi: «PADEL · SERVE ROSSI», «SET 2 · SERVE ANNA», «TIE-BREAK · SERVE BRUNO», «PARTITA FINITA». Accanto a ciascun nome, nella riga dei nomi, c'è uno slot fisso di 12dp con un pallino #FFFFFF, VISIBLE dalla parte di chi serve e INVISIBLE dall'altra. Sotto i numeri, lo slot dettaglio: didascalia 12sp #9E9E9E («GAME» se side1Secondary non contiene ' · ', altrimenti «SET · GAME») e side1Secondary a 24sp #E0E0E0. È sempre dal punto di vista della squadra di sinistra, come la schermata. Nessun campo nuovo in :core.
- **Perche':** 'Chi serve?' è la prima domanda fra un punto e l'altro. servingSide c'è già, ed è l'informazione giusta per rispondere. Un solo dettaglio letto da sinistra a destra toglie l'ambiguità 3-2 / 2-3.
- **File:** `mobile/src/main/res/layout/content_scoreboard_live.xml`, `mobile/src/main/java/it/vantaggi/scoreboardessential/MainActivity.kt (bindPeriod, bindScoreDetail)`, `mobile/src/main/java/it/vantaggi/scoreboardessential/SportLabels.kt`, `mobile/src/main/res/values/strings.xml`, `mobile/src/main/res/values-it/strings.xml`
- **Difetti della validazione:** L9 Tennis: dopo un tie-break chiuso con N punti ... il set successivo lo apre al servizio il lato sbagliato (con il servizio sempre in vista l'errore diventa visibile a chi è in campo); L11 Nel padel e nel tennis il registro dice 'press START to begin', ma START è nascosto (la barra non nomina mai START fuori dal calcio)

#### Gioco: riga dei nomi e correzione −1 nel calcio

- **Ora:** Nomi a 20sp dentro le card, contenitore cliccabile che apre TeamNameDialogFragment, contentDescription fissa «modifica nome squadra 1». Nel calcio un − da 60dp a 20dp dal + dentro ogni card; negli sport a set i − sono GONE (applyCapabilities 437-439).
- **Dopo:** Riga di 48dp. Nel calcio c'è un pulsante «−1» outlined (contorno 2dp #6E6E6E, testo 16sp #E0E0E0, 48x40) sul bordo esterno: a sinistra per la squadra 1, a destra per la squadra 2. Nel padel e nel tennis è GONE, deciso in applyCapabilities. In mezzo il nome a 20sp #E0E0E0 con la barretta 4x60dp nel colore della squadra (contorno 1dp #9E9E9E se il colore sul nero sta sotto 3:1) e lo slot del pallino di servizio. Toccare il nome apre ancora il dialogo di rinomina: è una scelta già presa nel codice, lontana dal pollice e reversibile. La contentDescription diventa «Squadra 1, ROSSI. Tocca per rinominare». Dopo un −1 la striscia mostra «CORREZIONE −1 ROSSI».
- **Perche':** La correzione è rara e voluta: non deve stare accanto al bersaglio che si colpisce senza guardare. Toglierla del tutto vorrebbe dire cambiare il motore, cioè fuori perimetro.
- **File:** `mobile/src/main/res/layout/content_scoreboard_live.xml`, `mobile/src/main/java/it/vantaggi/scoreboardessential/MainActivity.kt (setupScoreButtons, applyCapabilities)`, `mobile/src/main/res/values/strings.xml`, `mobile/src/main/res/values-it/strings.xml`
- **Difetti della validazione:** L11 Sulla card TalkBack non legge il nome della squadra; L3 Nel calcio, ANNULLA dopo una correzione '-' toglie l'evento sbagliato (la correzione si allontana dal gesto rapido; il difetto logico resta di L3)

#### Gioco: partita finita e dialogo di fine partita

- **Ora:** applyMatchOver mette i + ad alpha 0,4 e non cliccabili: il tocco resta muto. Nessuna scritta dice chi ha vinto. Per terminare si scorre fino a END MATCH. showEndMatchConfirmation mostra team1Score e team2Score, cioè headline(): nel padel a set unico non finito scrive «ROSSI: 0 / BIANCHI: 0» anche sul 5-3.
- **Dopo:** Zone spente: cardBackgroundColor #2C2C2C, glifo ad alpha 0, una barra di 4dp nel colore della squadra in fondo alla zona (sempre presente, alpha 0 durante il gioco). Restano cliccabili solo per rispondere con tre tick brevi (waveform 0,30,60,30,60,30) e «PARTITA FINITA» nella striscia. La barra dice «VINCE ROSSI · 6-4»: vincitore dal confronto dei primari interi, set o game da side1Secondary. Il numero dello sconfitto passa a #9E9E9E. La striscia diventa «PARTITA FINITA · TERMINA ›» e apre il dialogo esistente. ANNULLA resta dov'è e riapre la partita. Il messaggio del dialogo si compone dal display: calcio «ROSSI 2-1 LUPI»; racchetta in corso «ROSSI – BIANCHI · game 5-3 · punto 30-15»; a partita chiusa titolo «PARTITA FINITA», messaggio «VINCE ROSSI · 6-4» e positivo SALVA. SCARTA in #FF6E6E con getButton(BUTTON_NEUTRAL).setTextColor, solo in questo dialogo. Vibrazioni per la racchetta, solo dopo un tocco locale (un flag messo dal listener e consumato dall'observer): game chiuso (side1Secondary cambiato) EFFECT_DOUBLE_CLICK, partita finita EFFECT_HEAVY_CLICK.
- **Perche':** Il comando che conclude non va mai dove stava il +: il pollice che ha appena segnato l'ultimo punto potrebbe ritoccare. Un tocco inerte deve dichiararsi, altrimenti sembra un'app bloccata. Un dialogo che scrive 0-0 sopra un 5-3 fa sembrare l'app rotta.
- **File:** `mobile/src/main/java/it/vantaggi/scoreboardessential/MainActivity.kt (applyMatchOver, showEndMatchConfirmation, observer di scoreDisplay)`, `mobile/src/main/res/layout/content_scoreboard_live.xml`, `mobile/src/main/res/values/colors.xml (error_text)`, `mobile/src/main/res/values/strings.xml`, `mobile/src/main/res/values-it/strings.xml`
- **Difetti della validazione:** L1 Padel e tennis non si possono salvare finché nessuno ha vinto un set (il dialogo mostra il 5-3 vero, quindi il rifiuto 'non iniziata' diventa evidente: L1 va chiuso prima di questo passo); L7 A partita finita i lati dell'orologio restano toccabili (il telefono dà lo schema del tocco inerte dichiarato; al polso lo fa la pista Orologio); L9 Riassunto di una partita a racchetta non finita (il dialogo non usa più headline())

#### Gioco: schermo acceso, messaggi, toast

- **Ora:** Nessun FLAG_KEEP_SCREEN_ON nel repo: col timeout di sistema lo schermo si spegne fra un punto e l'altro. Il toast «Wear OS Not Connected» / «Connected» parte a ogni onCreate (MainActivity 177, 180), quindi anche a ogni rotazione.
- **Dopo:** window.addFlags(FLAG_KEEP_SCREEN_ON) quando matchEvents contiene almeno un evento SCORE e il display non è matchOver; clearFlags altrimenti (nuova partita, fine partita). Le due chiamate Toast.makeText si tolgono: lo stato del collegamento lo mostra già l'icona tramite isWearConnected.
- **Perche':** Sbloccare il telefono a ogni punto rompe l'uso a una mano. Sul telefono la batteria pesa meno che sul polso, dove la stessa scelta resta aperta.
- **File:** `mobile/src/main/java/it/vantaggi/scoreboardessential/MainActivity.kt`
- **Difetti della validazione:** L11 A ogni ricreazione dell'Activity ricompare il toast sullo stato di Wear OS

#### Gioco e foglio: stato dell'orologio

- **Ora:** Icona verde #76FF03 se collegato, grigia se no. Arretrato applicato o rifiutato: Snackbar LENGTH_LONG da SingleLiveEvent (MainViewModel 530, 533). watch_batch_rejected (values-it 105) dice «Chiudila: poi arriverà quella registrata al polso»: seguendolo si salva la partita senza quei punti e ne nasce una di un punto solo. Azzeramento e fine partita dal polso non lasciano traccia.
- **Dopo:** Nel MainViewModel, watchBatchApplied e watchBatchRejected diventano un solo LiveData<WatchNotice?>: sealed class con Applied(count) e Rejected. Si impostano dove oggi partono gli eventi (1084, 1106) e tornano null in startNewMatch. La logica non cambia. Icona dell'orologio: #E0E0E0 se collegato, #9E9E9E con il glifo barrato se no, badge di 12dp #FF1744 (anello 2dp #000000) finché la notizia è Rejected. Toccarla apre il foglio. In cima al foglio c'è una card con barretta #FF1744 e il testo nuovo: «L'orologio ha mandato punti che non sono entrati in questa partita.» Nessun invito a chiuderla. Applied mostra 3s «N PUNTI DALL'OROLOGIO» nella striscia. Righe per azzeramento e fine partita dal polso («L'OROLOGIO HA AZZERATO: ERA 3-2», «PARTITA CHIUSA DALL'OROLOGIO · SALVATA 3-2») arrivano con L4, che introduce l'intenzione di fine partita da cui leggerle.
- **Perche':** L'interfaccia non corregge L4 e L5, ma oggi nasconde il problema o, peggio, suggerisce proprio l'azione che fa perdere il punto. Uno stato che resta fino alla partita successiva arriva anche a chi guardava il campo quando la Snackbar è passata. Così lo 0-3 al fischio d'inizio, dovuto alla coda vecchia, ha una spiegazione visibile.
- **File:** `mobile/src/main/java/it/vantaggi/scoreboardessential/MainViewModel.kt (530-533, 1084, 1106, startNewMatch)`, `mobile/src/main/java/it/vantaggi/scoreboardessential/MainActivity.kt (observer 333-347, isWearConnected)`, `mobile/src/main/res/layout/content_scoreboard_details.xml (card avviso in cima)`, `mobile/src/main/res/values/strings.xml`, `mobile/src/main/res/values-it/strings.xml`
- **Difetti della validazione:** L5 Il telefono rifiuta sempre l'arretrato di una partita cominciata col telefono (tolto il consiglio che fa perdere il punto, reso persistente); L5 Una coda rifiutata o non confermata non si scarta mai e viene applicata alla partita successiva (il badge dura fino alla nuova partita, 'N PUNTI DALL'OROLOGIO' spiega il punteggio iniziale); L4 AZZERA/Finisci sull'orologio nel calcio svuota il motore del telefono (reso visibile insieme a L4)

#### Gioco in orizzontale

- **Ora:** layout-land/activity_main.xml fa scorrere tutto. Le card scendono a 180dp e il numero a 56sp. I comandi di fine partita si raggiungono solo scorrendo.
- **Dopo:** Stesso activity_main e stesso content_scoreboard_live; cambiano solo i dimen in values-land: barra 48, nomi 48, dettaglio 24, striscia 48, zone 72dp, numero da 48 a 110sp. Le zone + stanno agli angoli in basso, raggiungibili con i pollici a due mani. Il foglio PARTITA funziona come in verticale.
- **Perche':** Tolto il contenuto da consultare, l'orizzontale non ha più motivo di scorrere e il + non può finire sotto la piega. Due file di layout in meno da tenere allineati.
- **File:** `mobile/src/main/res/layout-land/activity_main.xml (cancellato)`, `mobile/src/main/res/values-land/dimens.xml`
- **Difetti della validazione:** VALIDAZIONE, cosa serve un dispositivo: calcio in orizzontale sotto i 360dp di altezza utile

#### Foglio PARTITA: rose, registro, formazioni, azioni

- **Ora:** Etichette delle rose nel colore grezzo della squadra (MainActivity 298, 311). Righe dei gol nel registro nel colore della squadra (MatchLogAdapter 117-119: #1A237E su #1E1E1E fa 1,26). Etichette delle formazioni in rosa e ciano del tema, 'No formation' cablato. Ora in 'mm:ss' da SimpleDateFormat su Date (MainViewModel 1384-1385): un gol al 65' diventa 05:00, e nei fusi con la mezz'ora tutto slitta. Colonna fissa da 50dp; margine in fondo di 80dp per i FAB. HISTORY ed END MATCH sono due pulsanti rosa uguali.
- **Dopo:** Stesso contenuto e identità street (StreetCard #1E1E1E su #121212). In cima: titolo PARTITA 20sp, CHIUDI, eventuale avviso dell'orologio. Azioni su due righe da tre, 48dp: CRONOLOGIA (outlined), TERMINA (pieno rosa, testo #000000, 5,02), CONDIVIDI (outlined); STATISTICHE, GIOCATORI, IMPOSTAZIONI (outlined, contorno #6E6E6E). Nel calcio c'è una terza riga con AZZERA TEMPO. Le etichette di rose e formazioni diventano tag StreetBadge pieni nel colore della squadra con testo TeamInk; 'No formation' passa da risorsa. Nel registro il testo è sempre #E0E0E0 (colorOnSurface); il colore resta sulla barretta team_indicator di 4dp, con contorno 1dp #9E9E9E se sotto 3:1 su #1E1E1E. Minuto del calcio calcolato senza Date, TimeUtils.matchMinute(ms) = ms/60000+1 col segno ' (0:30 → 1', 65:10 → 66'). Negli sport senza cronometro la colonna resta vuota. Colonna wrap_content con minWidth 40dp e maxLines 1. Via il margine di 80dp. Più avanti, con L2, la card COPPIE (vedi piano).
- **Perche':** È la parte da consultare: tiene l'identità. Un solo pulsante pieno nella riga distingue 'termina' da tutto il resto, cioè la confusione azzera/termina già segnalata nella Fase D. Il minuto è il modo in cui si parla di calcio, ed è giusto per costruzione.
- **File:** `mobile/src/main/res/layout/content_scoreboard_details.xml`, `mobile/src/main/res/layout/match_event_item.xml`, `mobile/src/main/java/it/vantaggi/scoreboardessential/MatchLogAdapter.kt (97-124)`, `mobile/src/main/java/it/vantaggi/scoreboardessential/MainActivity.kt (observer dei colori, updateFormation, setupMatchActions)`, `mobile/src/main/java/it/vantaggi/scoreboardessential/MainViewModel.kt (addMatchEvent 1384-1385)`, `mobile/src/main/java/it/vantaggi/scoreboardessential/utils/TimeUtils.kt`, `mobile/src/test/java/it/vantaggi/scoreboardessential/utils/TimeUtilsTest.kt`
- **Difetti della validazione:** L11 L'ora del registro è sbagliata oltre i 60 minuti e nei fusi con la mezz'ora; L11 Ora del registro in una colonna fissa da 50dp; L11 Etichette delle rose e righe dei punti nel registro nel colore grezzo della squadra; L11 Etichette delle formazioni ancora nei colori del tema e 'No formation' cablato

#### Contorno: tema e dialoghi

- **Ora:** colorOnPrimary e colorOnSecondary valgono #E0E0E0: 3,17 sul rosa, 1,17 sul ciano. colorPrimaryContainer, colorSurfaceContainer* e colorOutline* non sono mappati e restano ai valori base viola di M3 (per esempio gli avatar delle rose su colorPrimaryContainer). I MaterialAlertDialog senza overlay prendono lo sfondo dai ruoli non mappati. I colori predefiniti delle squadre sono tre coppie: #FFA726/#AEEA00 in MainViewModel 386-389, giallo/verde in ColorRepository, giallo/ciano nei layout.
- **Dopo:** In themes.xml: colorOnPrimary e colorOnSecondary diventano @color/asphalt_black; colorPrimaryContainer #2C2C2C con On #E0E0E0; colorSurfaceContainerLowest/Low/base/High/Highest = #121212/#1E1E1E/#1E1E1E/#2C2C2C/#2C2C2C; colorOutline #6E6E6E; colorOutlineVariant #2C2C2C. ThemeOverlay.App.MaterialAlertDialog diventa materialAlertDialogTheme del tema. I valori iniziali di _team1Color e _team2Color vengono da ColorRepository. Il tema resta solo scuro.
- **Perche':** I viola di base sono un'identità che nessuno ha scelto. Il nero sul rosa è l'unico inchiostro che supera AA: #121212 si ferma a 4,48.
- **File:** `mobile/src/main/res/values/themes.xml`, `mobile/src/main/res/values/colors.xml`, `mobile/src/main/java/it/vantaggi/scoreboardessential/MainViewModel.kt (386-389)`, `mobile/src/main/java/it/vantaggi/scoreboardessential/repository/ColorRepository.kt`
- **Difetti della validazione:** L11 Contrasti insufficienti: icona del FAB giocatori (circa 1,17:1) e testo dei pulsanti pieni (circa 3,17:1)

#### Impostazioni

- **Ora:** I pulsanti TEAM 1 COLOR e TEAM 2 COLOR cambiano solo lo sfondo (MatchSettingsActivity 104-110): testo e icona #E0E0E0 fanno 1,07 sul giallo e 1,01 sul verde. Il selettore dello sport non dice quali regole applica il motore. Campi in stile M2 OutlinedBox.
- **Dopo:** Passo breve: setTextColor e iconTint = TeamInk.on(colore). Passo successivo: sotto la ruota del selettore c'è un'anteprima 96x64 nel colore scelto con «12» in TeamInk e il nome squadra, aggiornata a ogni movimento. Sotto il selettore dello sport una riga 14sp #9E9E9E composta da SportConfig: «Set unico · punto d'oro sul 40-40 · tie-break a 7», «Al meglio di 3 set · vantaggi · tie-break a 7», «Cronometro · cambio portiere». Layout e stile street invariati.
- **Perche':** Il testo sopra la vernice lo decide TeamInk. L'utente vede come si leggerà il colore prima della partita, non a bordo campo, e sa quale regola arbitrerà l'app prima del primo 40-40.
- **File:** `mobile/src/main/java/it/vantaggi/scoreboardessential/ui/MatchSettingsActivity.kt`, `mobile/src/main/res/layout/activity_match_settings.xml`, `mobile/src/main/res/layout/dialog_color_picker.xml`, `mobile/src/main/java/it/vantaggi/scoreboardessential/SportLabels.kt`, `mobile/src/main/res/values/strings.xml`, `mobile/src/main/res/values-it/strings.xml`
- **Difetti della validazione:** L11 Nelle impostazioni la scritta dei pulsanti colore è quasi bianca sul colore della squadra: illeggibile con i colori predefiniti

#### Cronologia

- **Ora:** Nomi sempre in giallo e verde fissi (match_item.xml 41-86), Team.color ignorato. Risultato in DisplayLarge 57sp. Nessuno sport: un 6-4 di padel, un 2-1 di tennis e un 2-1 di calcio sono indistinguibili. La partita in corso compare con il cestino. 'No matches found' cablato.
- **Dopo:** Card StreetCard. Meta in 12sp #9E9E9E: «PADEL · 12/09 18:30 · 47 MIN». Una riga per squadra con un tag StreetBadge nel suo Team.color e nome in TeamInk, risultato 32sp: vincitore #E0E0E0, sconfitto #9E9E9E (6,22). Per la racchetta una riga con i set («6-4 · 3-6 · 7-6 (7-4)») o con la regola, decodificata con MatchLogCodec e SportRegistry nel ViewModel della cronologia previsto da L2. Cestino 48dp in #FF1744 (4,33, sufficiente per la grafica). La riga viva viene esclusa, come prescrive il rimedio L2. Stringhe da risorsa.
- **Perche':** I colori mostrati sono quelli con cui si è giocato. Chi ha vinto si legge senza colore, e il numero dice in che unità è espresso.
- **File:** `mobile/src/main/res/layout/match_item.xml`, `mobile/src/main/res/layout/activity_match_history.xml`, `mobile/src/main/java/it/vantaggi/scoreboardessential/MatchHistoryAdapter.kt (55-64)`, `mobile/src/main/java/it/vantaggi/scoreboardessential/ui/MatchHistoryUiState.kt`, `mobile/src/main/res/values/strings.xml`, `mobile/src/main/res/values-it/strings.xml`
- **Difetti della validazione:** L2 La cronologia mostra la partita in corso, e cancellarla da lì fa smettere di salvarla; L11 Titoli e schermate secondarie non tradotti

#### Statistiche e gestione giocatori

- **Ora:** Statistiche: toolbar #000000 diversa dalle altre; card del primo in giallo con il rank in ciano (1,09); gol in verde; 'Gol', 'Presenze' e 'Nessuna partita giocata' cablati. Giocatori: avatar in 12 colori con iniziali #E0E0E0 (fino a 1,04); chip dei ruoli #E0E0E0 su rosa, ciano, giallo e verde (3,17 / 1,17 / 1,07 / 1,01); ImageButton da 40dp con descrizioni inglesi cablate; add_player_fab senza contentDescription.
- **Dopo:** Statistiche: toolbar trasparente come le altre, podio in card rosa piena con testo #000000 (5,02), gol #E0E0E0 con didascalia GOL, plurali da risorsa. Giocatori: avatar e chip con TeamInk (minimo 4,58 per costruzione), pulsanti portati a 48dp, contentDescription da strings, FAB con descrizione e icona nera. Stile street invariato.
- **Perche':** La stessa regola della schermata di gioco vale per ogni vernice dell'app. Il giallo di squadra smette di fare da evidenziatore.
- **File:** `mobile/src/main/res/layout/activity_statistics.xml`, `mobile/src/main/res/layout/item_player_stat.xml`, `mobile/src/main/java/it/vantaggi/scoreboardessential/ui/statistics/StatisticsAdapter.kt (47-73)`, `mobile/src/main/res/layout/item_player_management.xml`, `mobile/src/main/res/layout/activity_players_management.xml`, `mobile/src/main/java/it/vantaggi/scoreboardessential/PlayersManagementAdapter.kt (59-77)`, `mobile/src/main/java/it/vantaggi/scoreboardessential/utils/RoleChipExtensions.kt`, `mobile/src/main/res/values/strings.xml`, `mobile/src/main/res/values-it/strings.xml`
- **Difetti della validazione:** L11 Bersagli sotto i 48dp e comandi senza etichetta nella gestione giocatori; L11 Titoli e schermate secondarie non tradotti

#### Onboarding e dialogo del marcatore

- **Ora:** Il tutorial descrive i pulsanti + e − dentro le card e promette 'sempre sincronizzati' con l'orologio. Il tasto finale in italiano è 'Fines'. AVANTI è rosa con testo #E0E0E0 (3,17). Il dialogo del marcatore ha 'Chi ha segnato?' cablato in italiano.
- **Dopo:** Testi aggiornati alla nuova schermata: «Tocca il colore della tua squadra, in basso, per segnare. ANNULLA toglie l'ultimo punto. Rose, registro e fine partita sono in PARTITA.» Pagina dell'orologio: «Segna dal polso: il punteggio compare qui e sull'orologio», senza 'sempre'. onboarding_finish diventa «Inizia». AVANTI e INIZIA prendono il testo nero da colorOnPrimary. Il titolo del marcatore passa da risorsa: «Gol di %s».
- **Perche':** Un tutorial che descrive una schermata che non c'è più, o promette una sincronizzazione che L4 e L5 smentiscono, è la stessa bugia che la Fase D aveva tolto dal primo tutorial.
- **File:** `mobile/src/main/res/values/strings.xml`, `mobile/src/main/res/values-it/strings.xml`, `mobile/src/main/res/layout/dialog_select_scorer.xml`, `mobile/src/main/java/it/vantaggi/scoreboardessential/SelectScorerDialogFragment.kt`
- **Difetti della validazione:** L11 Tasto 'Fine' del tutorial tradotto 'Fines' in italiano; L11 Titoli e schermate secondarie non tradotti, a volte con l'italiano mostrato agli utenti inglesi

#### Report PDF

- **Ora:** Nomi e punteggi nel colore grezzo della squadra su #121212 (MatchReportUtils 43-50; #0D47A1 fa 2,17). 'MATCH REPORT', 'FORMAZIONI' e 'TABELLINO MARCATORI' cablati. Nel padel il tabellino elenca le squadre come marcatori (76-81). Il FileOutputStream resta aperto (126-134).
- **Aggiornato da G-8 (7 ottobre 2026):** il fondo scuro e le bande piene sono usciti: carta chiara, gruppi tonali e il colore di squadra come barretta (vedi G-8 e conflitto 14). Quanto segue e' la decisione di prima.
- **Dopo:** Il fondo scuro resta (è contorno street). L'intestazione diventa due bande, una per squadra, nel suo colore, con nome e punteggio in TeamInk. Sezioni con didascalie #9E9E9E e righe #E0E0E0. FORMAZIONI e MARCATORI compaiono solo se capabilities.attributesScorer, e contano solo gli eventi con playerId. Titoli da risorsa, scrittura con use{}, nessun Intent se la scrittura fallisce.
- **Perche':** È l'unica superficie che esce dal telefono: se una squadra ha un colore scuro, il risultato deve restare leggibile per chi lo riceve.
- **File:** `mobile/src/main/res/layout/pdf_match_report.xml`, `mobile/src/main/java/it/vantaggi/scoreboardessential/utils/MatchReportUtils.kt`, `mobile/src/main/res/values/strings.xml`, `mobile/src/main/res/values-it/strings.xml`
- **Difetti della validazione:** L11 Il PDF mette 'TABELLINO MARCATORI' anche nel padel e ci elenca le squadre con il numero di punti; L11 Il report PDF lascia aperto il FileOutputStream e condivide il file anche se la scrittura fallisce

#### Contratto con la pista Orologio (L7, nessun file :wear toccato qui)

- **Ora:** Quadrante con le cifre nel colore grezzo della squadra su nero (wear MainActivity 461-470; #1A237E fa 1,59). contentDescription fissa sui lati: TalkBack non legge il punteggio. A partita finita i lati restano toccabili.
- **Dopo:** Questa pista consegna TeamInk in :core, già dipendenza di :wear, e tre schemi che il polso può ripetere: cifre #FFFFFF con il colore della squadra solo come riempimento o arco, glifi in TeamInk; contentDescription dinamica «ROSSI, 30» come le zone del telefono; tocco inerte dichiarato (vibrazione a tre tick e scritta FINITA) invece del silenzio. Realizzarli spetta alla pista Orologio, sul Wear_OS_Small_Round.
- **Perche':** Stessa regola sui due schermi, quindi un solo test la protegge. Il telefono non peggiora L7 e rende la sua correzione più piccola.
- **File:** `core/src/main/kotlin/it/vantaggi/scoreboardessential/core/TeamInk.kt`, `wear/src/main/java/it/vantaggi/scoreboardessential/wear/MainActivity.kt (solo per la pista Orologio)`
- **Difetti della validazione:** L7 Con TalkBack il punteggio dell'orologio non viene letto; L7 A partita finita i lati dell'orologio restano toccabili

### Rischi

Ordine e dipendenze. I passi 1-4 non dipendono da nessun lotto e si possono fare subito. I passi 5-7 toccano MainActivity nelle stesse funzioni su cui lavora L3 (setupRecyclerViews, undoGoalButton): conviene farli dopo L1-L3, come già consigliato dall'ordine dei lotti, e mai in parallelo. Il passo 8 dipende da L1: con il punteggio vero nel dialogo, il rifiuto «non iniziata» sotto un 5-3 diventa evidente. Il 12 dipende da L3 e dalla guardia di L7 in addRemotePoint, il 13 da L8, il 14 da L2, il 15 da L3. Le righe «azzerato/chiuso dall'orologio» arrivano con L4.

Rischi tecnici.
- BottomSheetBehavior su una NestedScrollView che contiene tre RecyclerView: il trascinamento del foglio e lo scorrimento delle liste possono contendersi il gesto. Va provato con 30 righe di registro; se succede, le RecyclerView restano con nestedScrollingEnabled=false (come oggi dentro la NestedScrollView) e si limita l'altezza del registro.
- Dimensione del numero calcolata una volta con Paint: con il carattere di sistema al 200% il valore in sp cresce, quindi il limite va applicato in px dopo il calcolo, non in sp.
- Su telefoni 360x640 in orizzontale (circa 312dp utili) il numero scende verso 48sp. Se non basta, la variante è un layout-land dove il nome entra nella zona +; va misurato prima di scriverla.
- FLAG_KEEP_SCREEN_ON consuma batteria in partite lunghe senza punti, perché resta acceso finché la partita è aperta.
- Riscrivere MainActivityLayoutTest butta via le prove D4 già verificate. Il nuovo test «niente si sposta» le sostituisce e va eseguito sull'emulatore prima di chiudere il passo.
- La baseline di lint va rimappata voce per voce, come in D4, non rigenerata.
- Senza test a screenshot la regressione visiva del contorno si controlla solo con foto prima e dopo.

Rischio d'uso. Il proprietario usa l'app ogni settimana e cambia il posto del + e del registro. La prima partita dopo il cambio va giocata sapendo che il registro è in PARTITA; il tutorial va aggiornato nello stesso passo 6.

Opzione separata, sconsigliata ora: Compose solo per la colonna di gioco (ComposeView in MainActivity). Beneficio: slot fissi e dimensioni legate allo stato più naturali. Costo: BOM Compose, plugin del compilatore, circa 2-3MB di APK, 2-3 giorni in più e test da migrare a compose-ui-test. Per una schermata non ripaga; da riconsiderare solo se l'orologio passa a Compose for Wear OS.

Stima complessiva nel sistema a View attuale: passi 1-4 circa 1,5 giorni; 5-9 circa 4-5 giorni più mezza giornata su dispositivo (una prova vera all'aperto a 2m); 10-11 circa 3-4 giorni; 12-15 legati ai lotti, circa 4-5 giorni.

### Decisioni del proprietario

- Sulla schermata di gioco il colore della squadra esce da dietro il numero: numeri bianchi su nero, colore solo sulle zone + e sulla barretta sotto il nome. Al sole il numero passa da un caso peggiore di 1,90 a 6,0, ma la card colorata dietro il punteggio sparisce. Confermi? L'alternativa è tenere la card colorata con l'inchiostro TeamInk, che regge per WCAG (almeno 4,58) ma non al sole.
- Rose, registro, formazioni e fine partita escono dalla schermata e vanno nel foglio PARTITA. Nel calcio l'ultimo gol si attribuisce dalla striscia, ma un gol precedente costa un tocco in più (aprire il foglio). Ti va bene per come attribuisci di solito i marcatori?
- Nel padel e nel tennis vuoi ANNULLA con un tocco, senza conferma (doppia vibrazione e scritta ANNULLATO per 3s), una volta chiusi L3 e la guardia di L7? Oppure preferisci tenere il dialogo, come nel calcio?
- Usi davvero il telefono in orizzontale durante una partita? Se no, bloccare la schermata di gioco in verticale toglierebbe il rischio sui telefoni piccoli e una variante da mantenere.
- Priorità: i passi 1-4 (testi dannosi, contrasti, schermo acceso, minuto 66') possono andare subito, prima di L1-L3? La nuova schermata di gioco (passi 5-9) invece aspetterebbe L1-L3. Confermi questo ordine?
- Colori predefiniti delle squadre: oggi convivono tre coppie. Ne resta una sola: giallo #FFD600 e verde #76FF03 di ColorRepository. È quella che vuoi vedere al primo avvio?

### Il giudizio

Il criterio che pesa di più è l'uso reale: una mano, al sole, occhi sul campo. Solo la prima proposta cambia ciò che conta in quel momento.

**Perché vince la prima.**
- **Pollice.** Il bersaglio primario passa da metà schermo alla fascia del pollice e diventa il più grande dello schermo.
- **Niente che si sposta.** Oggi, verificato nel layout e in MainActivity, l'annulla che compare, il portiere che si accende e START che diventa PAUSA spingono giù il + sotto il dito. FabOverlap.kt esiste proprio per tamponare questo sintomo.
- **Schermo acceso.** Oggi non c'è FLAG_KEEP_SCREEN_ON in tutto il repo: senza, fra un punto e l'altro lo schermo si spegne.
- **Numero leggibile con qualsiasi colore.** Il numero non dipende più dal colore scelto dall'utente: 21:1, e 6,0 contro 3,9-4,2 nel modello al sole. Ho ricalcolato quei valori.

**Verità dello stato.** È forte anche qui:
- ANNULLA resta visibile ma spento, invece di sparire;
- la striscia mostra l'ultima azione;
- la partita finita è dichiarata e il tocco inerte lo dice;
- gli stati dell'orologio restano a schermo;
- sparisce il testo di watch_batch_rejected che oggi porta a perdere il punto (L5).

**La contestazione della Fase D è legittima.** Dice apertamente che la D4 ha tolto lo scorrimento senza portare il comando nella zona del pollice. Toglie il colore di squadra da sotto il numero, e questo va confermato dal proprietario, ma la rinuncia resta confinata alla schermata di gioco, come la Fase D permette. L'identità street resta intatta nel contorno.

**La terza è seconda.** Trova i difetti di stato più gravi del padel, tutti verificati:
- chi serve non compare mai, perché periodLabel è null a set unico;
- le coppie non si possono impostare, perché rosters_card è spenta insieme all'unico «aggiungi giocatore»;
- il dialogo di fine partita dà 0-0 sopra un 5-3.

Però lascia il + a metà schermo e un'intestazione da circa 490dp. In più sconfina nella logica di :core, con un costo tre volte quello della prima.

**La seconda è terza.** È il miglior sistema di token e di contorno, ma sulla schermata di gioco non tocca né la raggiungibilità né gli spostamenti di layout.

**Innesti.**
- Dalla terza:
  - card COPPIE nel foglio PARTITA, insieme al rimedio L2 sulle rose;
  - servizio indicato da servingSide (freccia o pallino);
  - dialogo di fine partita con il punteggio vero;
  - vibrazione distinta per game e per set;
  - registro con una riga per game derivato dal motore, solo dopo L3;
  - minuto del calcio in formato 66';
  - riga delle regole nelle impostazioni e sport nella cronologia.
- Dalla seconda:
  - TeamInk in :core, riusabile per le cifre del quadrante (L7);
  - mappatura completa dei ruoli M3 e tema dei dialoghi globale;
  - una sola fonte dei colori predefiniti;
  - cronologia, statistiche, avatar e chip con TeamInk;
  - token color_error_text;
  - anteprima del colore nelle impostazioni.

**Ordine consigliato.**
1. L1-L3.
2. Slot fissi, zone + in basso e schermo acceso (prima proposta).
3. TeamInk e ruoli M3 (seconda).
4. COPPIE e servizio (terza, con L2).
5. Registro derivato dal motore (terza, con L3).

**Errori trovati dal giudice nelle proposte.** **Ricalcolati.** Tutti i rapporti con la formula WCAG (luminanza linearizzata) e una scansione RGB a passo 3. Il minimo nero/bianco è 4,58 (#5D60FF, #CF0DCC); la regola attuale scende a 2,07 su #FF4BFF, sotto 3:1 nel 7,1% dei colori e sotto 4,5 nel 26,1%. Il minimo di #1E1E1E/#E0E0E0 è 3,55, quello di #121212/#E0E0E0 3,77, quello di #1E1E1E/#FFFFFF 4,08. Valori singoli: 3,17, 1,17, 4,48, 5,02, 13,65, 4,33, 1,26, 1,59, 2,43, 1,07, 1,01, 1,09, 1,04, 2,96, 1,93, 2,17, 6,12, 5,13, 10,84, 12,18, 4,95. Al sole: 6,0, 4,73, 4,20, 3,91.

**Errori.**
1. **P1, contrasto sbagliato.** Dà «stroke #E0E0E0 (13,24:1 sul blu #1A237E)», ma 13,24 è #FFFFFF su #1A237E; #E0E0E0 su #1A237E fa 10,03:1. Non cambia l'esito: lo stroke conta contro il nero, dove fa 15,91.
2. **P1, arrotondamento.** «Bianco su colore di luminanza 0,18 al sole: 3,15»: con il suo stesso modello viene 3,16-3,17. Scarto trascurabile.
3. **P2, affermazione falsa sul codice.** In non_cambia e nella schermata della card scrive che D3 fa cambiare glifo al − nel padel e che «resta com'è». Nel codice attuale applyCapabilities (MainActivity 437-439) mette GONE i due − quando decrementIsUndo è vero: negli sport a set il − non esiste più.
4. **P3, stima gonfiata.** «Il VS ruota circa 150 volte in un padel», ma il padel in SportRegistry è a set unico (sets=1): circa 60-80 punti. 150 vale per un tennis al meglio di tre.
5. **P3, dettaglio impreciso.** I casi peggiori #1582B0 e #D03B60 sono esempi validi solo nel senso che ogni colore al punto di pareggio dà lo stesso minimo; i valori (4,33 e 3,55) sono giusti.

**Affermazioni verificate vere.**
- FabOverlap.kt e FabOverlapTest.kt esistono.
- Toast di Wear a ogni onCreate (MainActivity 169-181).
- Nessun FLAG_KEEP_SCREEN_ON né keepScreenOn nel repo.
- Dialogo del portiere con setCancelable(false) (984-992).
- Vibrazione di 500ms a ogni punto.
- VS che ruota a ogni punto (animateVsIndicator).
- Lampo del numero verso colorPrimary e colorSecondary (AnimationUtils 69-104).
- applyReadableTextColor con soglia 0,5 sulla luma gamma (815-827).
- undo_goal_button GONE/VISIBLE in rosso #FF1744.
- Etichette delle rose nel colore grezzo (MainActivity 298, 311).
- Card da 280dp e numero a 86sp (56 in orizzontale).
- 112 @color grezzi contro 13 ?attr nei layout.
- Colori iniziali #FFA726/#AEEA00 nel ViewModel (386-389).
- Cronologia con giallo e verde fissi (match_item.xml).
- Podio delle statistiche con il ciano sul giallo.
- Avatar su colorPrimaryContainer non mappato.
- PDF con il colore grezzo (MatchReportUtils 43-50).
- Quadrante con il colore grezzo (wear MainActivity 462-470).
- periodLabel null a set unico (RacketRules 66-72, padel sets=1).
- add_team1_player_button dentro rosters_card, spenta con hasRoles=false.
- refreshServeOrder che richiede 2+2 giocatori.
- Testo di watch_batch_rejected che invita a chiudere la partita.

Nessun file del repo è stato modificato: i conti sono in uno script nella scratchpad.

## Orologio: Mezzo secondo: un numero, poi un altro numero, poi niente

| Proposta | Voto |
|---|---|
| Mezzo secondo: un numero, poi un altro numero, poi niente | 8 |
| Polso sicuro: bersagli grandi, un solo significato per gesto, tenere premuto solo per fermare o distruggere | 6.5 |
| Il quadrante che dice quando non sa | 6 |

### Direzione

Parto da "Mezzo secondo" e ne tengo l'ossatura. Sul tondo da 192dp il quadrante esce dal quadrato inscritto (BoxInsetLayout) e usa le fasce orizzontali del cerchio, con tre livelli di lettura: le due cifre bianche su nero a 58sp fissi; un secondo numero sempre nello stesso posto (cronometro nel calcio, game del set nella racchetta); una riga di stato che parla solo quando qualcosa non va. Il colore della squadra esce dalle cifre e diventa una striscia portata ad almeno 3:1 sul nero, così nessun colore scelto dall'utente può far sparire un punteggio. Dalle altre due proposte innesto: la vibrazione di conferma che parte quando il telefono restituisce lo stato v2 con il registro cambiato, non quando il messaggio parte, con 1 impulso per la sinistra e 2 per la destra; la riga di stato calcolata da una funzione pura testata su JVM (StatoFiducia), con "n NON CONSEGNATI" dopo 10s e l'ora dell'ultimo dato accanto a SCOLLEGATO; l'ambra per la coda e il rosso solo per ciò che chiede di intervenire; il marcatore che non si apre più da solo ma si offre per 8s dal bersaglio in basso; la fine partita bloccata finché ci sono punti in coda; la guardia di 500ms al risveglio e il quadrante che non si chiude con uno swipe. Correggo cinque punti della proposta di partenza. 1) Il portiere sale nella fascia alta e il bersaglio in basso diventa alto 60dp, così nessun comando resta sotto i 42-44dp visibili. 2) Le cifre hanno una misura fissa: con l'autoSize per lato, "AV" e "40" uscirebbero a misure diverse. 3) A partita finita la riga dei game non mostra un set chiuso come se fosse in corso. 4) Il separatore dei set di :core è privato: l'orologio ne tiene una copia, fissata da un test sul motore vero. 5) "NON CONSEGNATI" si mostra ma non riprova da solo, perché VALIDAZIONE L5 dice che un nuovo tentativo senza id idempotente fa danni. La Fase D resta: identità street (asphalt, StreetCard, condensed bold) nel menu e nella selezione sport, riduzione sul quadrante. La contesto su un punto: anche la selezione del marcatore è una schermata di gioco e va ridotta. Ambient sì e keep-screen-on no è la mia raccomandazione, ma il piano dice che vanno decisi, quindi la scelta finale resta al proprietario.

### Bozze

- [Quadrante nel padel, collegato, partita in corso](design/wear-quadrante-nel-padel-collegato-partita-in-corso.svg): Padel collegato, 40-15, 4-3 nei game. La squadra 1 (sinistra) serve: pallino bianco sul lato esterno. Colori di squadra solo nelle strisce. Riga E senza anomalie: mostra il gesto in grigio. In basso il glifo del menu. Nessun pallino di collegamento: il silenzio vuol dire che va tutto bene.
- [Quadrante nel calcio, telefono scollegato, 2 punti in coda](design/wear-quadrante-nel-calcio-telefono-scollegato-2-punti.svg): Calcio senza telefono. Il 3-2 è calcolato al polso con :core; le cifre restano bianche. La riga E dice '2 IN CODA' in ambra. In fascia A il cronometro corre, bianco, e a destra il portiere 'K 4:12' in rosa con l'anello sul bordo. Nessun controllo fra i due lati.
- [Quadrante nel tennis, partita finita](design/wear-quadrante-nel-tennis-partita-finita.svg): Tennis finito 2-1 nei set. Risultato a piena intensità, non più ad alpha 0.4. Fascia A vuota, perché a partita finita non c'è un set in corso. I set chiusi stanno in D; la riga E dice PARTITA FINITA; niente pallino del servizio. I lati non vibrano e non mandano niente.
- [Ambient nel calcio, collegato, nessuna anomalia](design/wear-ambient-nel-calcio-collegato-nessuna-anomalia.svg): Polso abbassato: fondo nero, cifre light bianche nella stessa posizione, minuti senza secondi. Strisce, anello, K e menu sono nascosti; riga E vuota perché non c'è niente che non va. Con burn-in protection la radice si sposta di ±4dp a ogni aggiornamento.

### Il colore della squadra

Sull'orologio nessun testo sta mai sopra un colore di squadra, e il colore di squadra non è mai il colore di un testo. Diventa solo grafica (striscia, barra), quindi basta 3:1 contro il fondo (WCAG 1.4.11).

Regola 1, grafica su nero: ReadableColor.graphicOnBlack(c, 3.0).
- Se contrast(c, #000000) >= 3.0, si usa c così com'è.
- Altrimenti si mescola c col bianco (canale = c + (255 - c)·t, arrotondato) con la t più piccola, a passo 0.01, che porta il contrasto ad almeno 3.0. La tinta resta, sale la luminanza.
Casi calcolati:
- navy #000080: da 1.31 a #4F4FA7 (t=0.31), 3.01;
- blu #0000FF: da 2.44 a #3333FF (t=0.20), 3.06;
- nero #000000: #5C5C5C (t=0.36), 3.14;
- indaco #1A237E: da 1.59 a #4C539A (t=0.22), 3.01;
- rosso scuro #8B0000: da 2.10 a #A23333 (t=0.20), 3.06;
- #FFD600, #76FF03, #F50057 e #FF0000 restano invariati.
Scansione dello spazio RGB a passo 5: minimo 3.00 (#000528 diventa #54586F).

Regola 2, testo sopra un colore: ReadableColor.textOn(bg). Sull'orologio non serve; è per il telefono (L11). Restituisce #000000 se la luminanza relativa WCAG (canali linearizzati) supera 0.179, altrimenti #FFFFFF. Il minimo garantito per QUALSIASI colore è 4.58:1: scansione a passo 3, caso peggiore #8454F6. La regola attuale del telefono (applyReadableTextColor: #E0E0E0 o #1E1E1E, media pesata NON linearizzata, soglia 0.5) scende a 2.07:1 su #FF4BFF.

L'utilità va in :shared come Kotlin puro su Int ARGB, senza android.graphics, così i test girano su JVM: shared/src/main/java/it/vantaggi/scoreboardessential/shared/utils/ReadableColor.kt. Espone luminance, contrast, graphicOnBlack e textOn. La riusa il telefono quando l'altra pista correggerà L11.

Se due squadre scelgono colori simili, le distinguono la posizione (squadra 1 a sinistra, come sul telefono) e la vibrazione (1 impulso a sinistra, 2 a destra).

### Colori

| Token | Valore | Uso |
|---|---|---|
| face_black (nuovo) | `#000000` | Fondo del quadrante di gioco, dell'ambient e della selezione marcatore. Sull'OLED sono pixel spenti; il bianco arriva a 21:1 contro i 18.73 su #121212 e il rosa del K sale da 4.48 a 5.02. |
| score_white (nuovo) | `#FFFFFF` | Solo cifre del punteggio, riga A (cronometro che corre, game del set in corso), pallino del servizio, testo "CHI?". Anche in ambient. |
| stencil_white | `#E0E0E0` | Riga di stato informativa (PARTITA FINITA, INVIO n…, n CONSEGNATI); testi delle superfici di contorno (menu, selezione sport). |
| sidewalk_gray | `#9E9E9E` | Livello 3: cronometro in pausa, K fermo, dettaglio dei set, suggerimento del gesto, glifo del menu, sottotitoli nel menu. 7.84:1 su nero, 6.22:1 su #1E1E1E. |
| graffiti_pink | `#F50057` | Unico accento di marca in gioco: "K m:ss" e anello del portiere mentre corre, bordo della capsula CHI?. Mai testo su #1E1E1E (3.99) né testo bianco sopra il rosa (4.18). |
| signal_amber (nuovo) | `#FFB300` | Stati di consegna: "n IN CODA", "n NON CONSEGNATI", "SCOLLEGATO · hh:mm", sottotitolo "Prima consegna n punti" nel menu. Il punto è salvo, ma il telefono non ce l'ha. 11.7:1 su nero. |
| error_red | `#FF1744` | Solo ciò che chiede un intervento: "NON CONFERMATO" (tocco senza ricevuta), "n RIFIUTATI" (quando L5 porterà il NACK), K scaduto, voce FINE PARTITA (20sp bold) e il suo stato di conferma pieno rosso con testo nero. |
| ambient_gray (nuovo) | `#BDBDBD` | Riga di stato in ambient, solo con un'anomalia o a partita finita. In ambient niente ambra né rosso: sugli schermi low-bit non sono garantiti. |
| asphalt_dark / concrete_gray | `#121212 / #1E1E1E` | Fondo e card StreetCard del menu e della selezione sport (contorno, identità street invariata). |
| neon_cyan | `#00E5FF` | Solo nei menu: la ✓ dello sport in uso (10.84:1 su #1E1E1E). |
| striscia squadra (derivato) | `ReadableColor.graphicOnBlack(colore utente, 3.0)` | Striscia 28×4dp sotto ogni colonna di cifre e barra 28×4dp sotto il titolo della selezione marcatore. Mai testo. I default #FFD600 e #76FF03 restano invariati. |

### Tipografia

| Ruolo | sp | Peso e forma | Uso |
|---|---|---|---|
| Punteggio | 58 | sans-serif-condensed bold, dimensione FISSA (niente autoSize); in ambient sans-serif-condensed-light | Le due cifre. 58sp a 192dp: "AV" misura circa 65dp contro i 68dp utili della colonna, "40" circa 59dp. 68sp in values-sw210dp. Dimensione fissa perché i due lati e gli stati (15, 40, AV, PV) non cambino misura. |
| Contesto (fascia A) | 20 | sans-serif-condensed bold | Calcio: cronometro "34:12", #FFFFFF se corre e #9E9E9E se fermo. Racchetta: game del set in corso "4 – 3" bianco. Ambient calcio: minuti "34'". 24sp in sw210dp. |
| Portiere (fascia A, a destra, solo calcio) | 14 | sans-serif-condensed bold | "K" grigio da fermo, "K 4:12" rosa mentre corre, "K 0:00" rosso scaduto. 16sp in sw210dp. |
| Dettaglio (fascia D) | 13 | sans-serif-condensed regular | Racchetta: "SET 3 · 6-4 · 3-6", "TIE-BREAK · 6-4", a partita finita "6-4 · 3-6 · 7-5". Padel in corso: vuota. Calcio: vuota. 15sp in sw210dp. |
| Stato (fascia E) | 12 | sans-serif-condensed bold, maiuscolo, letterSpacing 0.04, autoSize 10-12sp in altezza fissa 16dp | Una frase sola, al massimo 18 caratteri in entrambe le lingue. Oggi è a 9sp. 14sp in sw210dp. |
| CHI? (fascia F, 8s dopo un gol) | 13 | sans-serif-condensed bold | Testo bianco in una capsula col bordo rosa da 1.5dp. Il glifo del menu (tre punti) è una vector da 4dp per punto, non testo. |
| Titolo delle liste | 14 | sans-serif-condensed bold, maiuscolo | "GOL · 3–2", "SPORT", "PARTITA" su una riga. Oggi HeadlineMedium a 28sp, stimato su 2-3 righe. |
| Voce di menu e di sport | 20 | sans-serif-condensed bold | 20sp bold è testo grande WCAG (≥18.66): serve a FINE PARTITA rosso su #1E1E1E (4.33:1). Righe da almeno 52dp. |
| Nome giocatore | 18 | sans-serif-condensed bold, maxLines 1, ellipsize end | Selezione marcatore, bianco su nero (21:1), righe da 52dp, senza ruolo. |
| Sottotitolo di voce | 13 | sans-serif-condensed regular | "Partita in corso", "Prima consegna 2 punti", "✓ in uso", "Tocca di nuovo". |

### Forme e spaziature

QUADRANTE, tondo da 192dp: FrameLayout radice #000000. Sopra resta l'anello del portiere invariato (raggio 83.5-89.5dp). Poi una ConstraintLayout SENZA BoxInsetLayout, con guide orizzontali in percentuale dell'altezza. Fasce visive, in dp a 192:
- A 18-48 (9.4%-25%): contesto e K;
- B 50-124 (26%-64.6%): cifre;
- C 126-130: strisce;
- D 132-148: dettaglio;
- E 150-166: stato;
- F 168-188: glifo del menu o CHI?.
Margini orizzontali per fascia, dalla corda del cerchio al bordo alto della fascia: B 12dp, D 16dp, E 30dp. A è centrata ed è la più stretta: il gruppo cronometro+K è largo circa 92dp contro i 102 disponibili dentro l'anello a y=32.
Colonne di B: sinistra x 12-92, destra x 100-180, con un gutter morto di 8dp al centro. Ogni colonna riserva 12dp sul lato esterno per il pallino del servizio (8dp, x 14-22 oppure 170-178, y 66-74). Le cifre stanno nei restanti 68dp.
Strisce: 28×4dp centrate sotto le cifre (x 44-72 e 120-148), estremi dritti.
Fascia A nel calcio: cronometro allineato a destra su x=91, K allineato a sinistra da x=101. Posizioni fisse, non si spostano quando il K cambia testo.
BERSAGLI (viste di tocco separate dal testo):
- cronometro x 0-96 e K x 96-192, y 0-46: visibili circa 46×44 e 56×42dp;
- zona morta y 46-50;
- lati x 0-96 e 96-192, y 50-124: circa 80×74dp visibili ciascuno. Il tocco sul gutter di 8dp è assegnato al lato più vicino; il gutter è morto solo visivamente, niente comandi al centro;
- zona morta y 124-132;
- bersaglio inferiore y 132-192, x 24-168: menu, oppure CHI? per 8s. D ed E non sono mai toccabili da sole: stanno dentro questo bersaglio.
Nessuna card, ombra o angolo tagliato sul quadrante.
227dp (sw210dp): stesse percentuali, margini ×1.18 (B 14, D 19, E 35), strisce 33×5dp, pallino 10dp, testi dalla colonna sw210 della tipografia.
Quadrati (values-notround, nuovo): margini di fascia a 0 e l'anello resta. Lo spazio recuperato negli angoli resta vuoto.
CONTORNO (menu, selezione sport): BoxInsetLayout, StreetCard cut 12dp su concrete_gray, padding 16dp, righe da almeno 52dp.
SELEZIONE MARCATORE: righe da 52dp su nero, senza card, WearableRecyclerView a tutta larghezza con isEdgeItemsCenteringEnabled.

### Contrasti

| Coppia | Rapporto | Esito |
|---|---|---|
| Cifre #FFFFFF su #000000 (dopo) | 21.00:1 | AAA, il massimo possibile |
| Cifre #FFD600 (default squadra 1) su #121212 (oggi) | 13.27:1 | Passa, ma solo perché è il default |
| Cifre navy #000080 scelto dall'utente su #121212 (oggi) | 1.17:1 | NON passa neanche 3:1: il punteggio sparisce |
| Cifre #0000FF su #121212 (oggi) | 2.18:1 | NON passa 3:1 |
| Cifre #000000 su #121212 (oggi) | 1.12:1 | NON passa |
| Partita finita: #FFD600 ad alpha 0.4 = #71600B su #121212 (oggi) | 3.02:1 | Al limite del 3:1 per testo grande; dopo: nessuna opacità, 21:1 |
| Partita finita: #76FF03 ad alpha 0.4 = #3A710C su #121212 (oggi) | 3.16:1 | Al limite; tolto |
| Striscia navy #000080 portata a #4F4FA7 su #000000 | 3.01:1 | Passa 3:1 per grafica (1.4.11) |
| Striscia #0000FF portata a #3333FF su #000000 | 3.06:1 | Passa 3:1 per grafica |
| Striscia nera portata a #5C5C5C su #000000 | 3.14:1 | Passa 3:1 per grafica |
| Striscia, caso peggiore sulla griglia RGB a passo 5 (#000528 portato a #54586F) | 3.00:1 | Passa: la regola garantisce 3:1 per qualsiasi colore |
| Striscia #FFD600 / #76FF03 su #000000 (default) | 14.87:1 | Invariati (il verde fa 16.08) |
| #E0E0E0 su #000000 (stato informativo) | 15.91:1 | AAA |
| #9E9E9E su #000000 (pausa, K fermo, set, suggerimento, glifo) | 7.84:1 | AAA per testo a 12-13sp |
| #F50057 su #121212 (K a 16sp bold, oggi) | 4.48:1 | NON AA: 16sp bold è testo normale |
| #F50057 su #000000 (K e bordo di CHI?, dopo) | 5.02:1 | AA testo normale |
| #FF1744 su #000000 (NON CONFERMATO, K scaduto) | 5.46:1 | AA |
| #FFB300 su #000000 (IN CODA, NON CONSEGNATI, SCOLLEGATO) | 11.70:1 | AAA |
| #FF1744 contro #FFB300 (i due colori di stato fra loro) | 2.14:1 | Non bastano da soli a distinguersi: li distingue la parola, come previsto |
| #BDBDBD su #000000 (stato in ambient) | 11.18:1 | AAA |
| #FF1744 su #1E1E1E (FINE PARTITA nella card del menu) | 4.33:1 | Sotto 4.5: passa solo come testo grande, per questo le voci di menu sono a 20sp bold (soglia 3:1) |
| #000000 su #FF1744 (card di conferma CHIUDERE 3–2?) | 5.46:1 | AA |
| #E0E0E0 su #1E1E1E (voci di menu e sport) | 12.63:1 | AAA |
| #9E9E9E su #1E1E1E (sottotitoli nel menu) | 6.22:1 | AA |
| #FFB300 su #1E1E1E (Prima consegna n punti) | 9.29:1 | AAA |
| #00E5FF su #1E1E1E (✓ in uso) | 10.84:1 | AAA |
| #E0E0E0 su #F50057 (colorOnPrimary del tema) | 3.17:1 | NON AA: nessun testo chiaro su rosa sull'orologio |
| Testo nero o bianco con soglia L=0.179 su qualsiasi colore; caso peggiore #8454F6 col nero | 4.58:1 | AA garantito per ogni colore (scansione a passo 3) |
| Regola attuale del telefono #E0E0E0/#1E1E1E con media non linearizzata; caso peggiore #FF4BFF con #E0E0E0 | 2.07:1 | NON passa: la regola da portare in :shared è l'altra |
| Modello di abbagliamento, non WCAG: #FFFFFF su nero con schermo a 1000 nit e 300 nit riflessi | 4.33:1 | Con gli stessi numeri: #E0E0E0 3.48, #FFD600 3.31, #FFB300 2.78, #9E9E9E 2.14, #F50057 1.67, navy 1.05. Al sole conta la luminanza |

### Piano

#### 0. Misurare il quadrante di oggi prima di toccarlo: Layout Inspector su Wear_OS_Small_Round in calcio e padel.

0. Misurare il quadrante di oggi prima di toccarlo: Layout Inspector su Wear_OS_Small_Round in calcio e padel. Altezza reale della fascia dei punteggi, textSize effettivo delle cifre dopo l'autoSize, rettangoli toccabili dei lati, larghezza di 'NIENTE TELEFONO - 12 IN ATTESA'. Screenshot 'prima'.

- **Costo:** piccolo
- **File:** `MIGRATION_PLAN.md (solo annotazione delle misure)`
- **Verifica:** Le misure confermano o smentiscono le stime (circa 30dp di fascia, circa 40×30dp per lato, 12-14sp nel padel). Se sono molto diverse, ricalibrare le percentuali delle fasce prima del passo 6.

#### 1. Prerequisiti da L7, col rimedio già scritto in VALIDAZIONE. Collector di matchTimer con condizione scoreSta

1. Prerequisiti da L7, col rimedio già scritto in VALIDAZIONE. Collector di matchTimer con condizione scoreState == null || scoreState.hasClock (riga 476). Nel ramo hasClock di applyClockRole scrivere subito matchTimer.value. In incrementScore, return se _scoreState.value?.matchOver == true.

- **Costo:** piccolo
- **File:** `wear/src/main/java/it/vantaggi/scoreboardessential/wear/MainActivity.kt`, `wear/src/main/java/it/vantaggi/scoreboardessential/wear/WearViewModel.kt`, `wear/src/test/java/it/vantaggi/scoreboardessential/wear/WearViewModelTest.kt`
- **Verifica:** Test: con uno stato v2 matchOver=true, incrementScore non incrementa la sequenza e non mette niente in coda. Emulatore: calcio con telefono v2, avviare il cronometro sul telefono: al polso avanza. Passando da padel a calcio 'Set 1' sparisce.

#### 2. ReadableColor in :shared: Kotlin puro su Int ARGB con luminance, contrast, graphicOnBlack(c, min=3.0) e tex

2. ReadableColor in :shared: Kotlin puro su Int ARGB con luminance, contrast, graphicOnBlack(c, min=3.0) e textOn(bg).

- **Costo:** piccolo
- **File:** `shared/src/main/java/it/vantaggi/scoreboardessential/shared/utils/ReadableColor.kt (nuovo)`, `shared/src/test/java/it/vantaggi/scoreboardessential/shared/utils/ReadableColorTest.kt (nuovo)`
- **Verifica:** Test JVM: contrast(#FFFFFF,#000000)=21; graphicOnBlack(#000080) ha contrasto >= 3.0 e vale #4F4FA7; nero dà #5C5C5C; #FFD600 invariato. Griglia RGB a passo 15: graphicOnBlack sempre >= 3.0 e textOn sempre >= 4.5; textOn(#8454F6) = nero.

#### 3. Modifiche a basso rischio sul layout attuale:

3. Modifiche a basso rischio sul layout attuale:
- fondo del quadrante #000000;
- applyMatchOver senza alpha;
- K che mostra 'K m:ss' mentre corre;
- contentDescription dinamica con il punteggio e liveRegion;
- WearScoreState.servingSide letto, con default 0, da fromDataMap e da rebuildLocalState. Non ancora disegnato.

- **Costo:** piccolo
- **File:** `wear/src/main/res/layout/activity_main.xml`, `wear/src/main/res/values/colors.xml`, `wear/src/main/java/it/vantaggi/scoreboardessential/wear/MainActivity.kt`, `wear/src/main/java/it/vantaggi/scoreboardessential/wear/WearViewModel.kt`, `wear/src/main/res/values/strings.xml`, `wear/src/main/res/values-it/strings.xml`, `wear/src/test/java/it/vantaggi/scoreboardessential/wear/WearViewModelTest.kt`
- **Verifica:** Test: fromDataMap con serving_side=2 dà 2, senza la chiave dà 0. Emulatore: padel finito mostra il risultato a piena intensità; TalkBack legge 'Squadra 1, 40'; il K mostra i secondi che scendono.

#### 4. StatoFiducia e riga di stato. Funzione pura con la priorità a 8 livelli. Il ViewModel espone gli input: col

4. StatoFiducia e riga di stato. Funzione pura con la priorità a 8 livelli. Il ViewModel espone gli input: collegato, verificaInCorso per al massimo 2s, istante in cui la coda è diventata non vuota da collegati, ultimo stato vivo, transitori. renderStatus scrive nel gestureHint esistente. Il pallino viene rimosso. refreshConnection ogni 15s a partita in corso. Stringhe it/en entro 18 caratteri.

- **Costo:** medio
- **File:** `wear/src/main/java/it/vantaggi/scoreboardessential/wear/StatoFiducia.kt (nuovo)`, `wear/src/test/java/it/vantaggi/scoreboardessential/wear/StatoFiduciaTest.kt (nuovo)`, `wear/src/main/java/it/vantaggi/scoreboardessential/wear/WearViewModel.kt`, `wear/src/main/java/it/vantaggi/scoreboardessential/wear/MainActivity.kt`, `wear/src/main/java/it/vantaggi/scoreboardessential/wear/LastKnownMatch.kt`, `wear/src/main/res/layout/activity_main.xml`, `wear/src/main/res/values/strings.xml`, `wear/src/main/res/values-it/strings.xml`
- **Verifica:** Test a tabella, un caso per priorità più i conflitti:
- coda e scollegato danno IN CODA;
- coda da collegati per 10s dà NON CONSEGNATI;
- verificaInCorso non mostra SCOLLEGATO;
- matchOver senza anomalie dà PARTITA FINITA;
- tutte le stringhe hanno al massimo 18 caratteri, letto dalle risorse in un test.
Emulatore: chiudere l'app del telefono o scollegare il telefono emulato: 'SCOLLEGATO · hh:mm'; toccare due volte: '2 IN CODA'; misurare col Layout Inspector che la frase più lunga non sia troncata.

#### 5. Menu partita al posto di SPORT e AZZERA, ancora nel layout attuale: un solo bottone col glifo a tre punti a

5. Menu partita al posto di SPORT e AZZERA, ancora nel layout attuale: un solo bottone col glifo a tre punti apre MenuActivity. Voci decise da MenuVoci, funzione pura, con i blocchi: coda > 0, scollegato, calcio dopo il primo v2 finché L4 non è fatto. Conferma sul posto (card rossa, secondo tocco fra 600ms e 5s). Chiusura dopo 10s. Toast e AlertDialog tolti.

- **Costo:** medio
- **File:** `wear/src/main/java/it/vantaggi/scoreboardessential/wear/MenuActivity.kt (nuovo)`, `wear/src/main/java/it/vantaggi/scoreboardessential/wear/MenuVoci.kt (nuovo)`, `wear/src/test/java/it/vantaggi/scoreboardessential/wear/MenuVociTest.kt (nuovo)`, `wear/src/main/res/layout/activity_menu.xml (nuovo)`, `wear/src/main/res/layout/item_sport_wear.xml`, `wear/src/main/res/drawable/ic_menu_dots.xml (nuovo)`, `wear/src/main/AndroidManifest.xml`, `wear/src/main/java/it/vantaggi/scoreboardessential/wear/MainActivity.kt`, `wear/src/main/res/values/strings.xml`, `wear/src/main/res/values-it/strings.xml`
- **Verifica:** Test: con coda > 0 FINE PARTITA è disattivata e il sottotitolo dice quanti punti; nel calcio v2 è disattivata; con partita in corso SPORT è disattivata. Emulatore: nessun Toast; padel sul 6-4 chiuso dal polso con doppio tocco arriva salvato sul telefono; un tocco singolo non chiude niente e dopo 5s la card torna com'era.

#### 6. Quadrante a fasce, il passo principale:

6. Quadrante a fasce, il passo principale:
- nuovo activity_main.xml senza BoxInset, guide in percentuale, margini di corda in dimens;
- values-notround nuovo e sw210dp aggiornato;
- cifre bianche a 58sp fissi;
- strisce con graphicOnBlack;
- pallino del servizio;
- FaceText.split per le fasce A e D, con la regola a partita finita;
- K in fascia A;
- viste di tocco separate: cronometro, K, lati, bersaglio inferiore.

- **Costo:** grande
- **File:** `wear/src/main/res/layout/activity_main.xml`, `wear/src/main/res/values/dimens.xml`, `wear/src/main/res/values-sw210dp/dimens.xml`, `wear/src/main/res/values-notround/dimens.xml (nuovo)`, `wear/src/main/res/values/theme.xml`, `wear/src/main/java/it/vantaggi/scoreboardessential/wear/FaceText.kt (nuovo)`, `wear/src/test/java/it/vantaggi/scoreboardessential/wear/FaceTextTest.kt (nuovo)`, `wear/src/main/java/it/vantaggi/scoreboardessential/wear/MainActivity.kt`
- **Verifica:** FaceTextTest con MatchEngine e le RacketRules vere:
- tennis in corso nel set 2: A '4 – 3', D 'SET 2 · 6-4';
- tie-break: D inizia con 'TIE-BREAK';
- tennis finito: A vuota, D coi tre set;
- padel finito: A e D vuote;
- stringa senza separatore: A = stringa intera.
Emulatore Wear_OS_Small_Round, screenshot in calcio, padel e tennis. Col Layout Inspector: cifre a 58sp senza taglio con 'AV', '40' e '15'; lati di almeno 78×72dp; gruppo cronometro+K dentro l'anello; nessun testo tagliato dal bordo.

**Come e' stato fatto (passo 6, 2 ottobre 2026).** Le cifre sono **58dp e 68dp**, non sp: in sp, col
carattere di sistema grande (fontScale 1,06-1,24 su Wear OS), 'AV' diventava 'A' e '40' diventava '4'
dentro le colonne da 68dp. La misura resta fissa per tutta la partita e uguale sui due lati, come voleva
la scheda; gli altri livelli del quadrante (cronometro, K, D, E) restano in sp. Nella racchetta i game
della fascia A sono centrati sullo schermo; nel calcio il gruppo cronometro+K resta allineato a destra
sull'asse. Dove questo documento dice "58sp" per le cifre dell'orologio, vale 58dp.

#### 7. Aptica per lato e ricevuta del tocco: WearHaptics con interfaccia iniettabile, ricevute con lunghezza del r

7. Aptica per lato e ricevuta del tocco: WearHaptics con interfaccia iniettabile, ricevute con lunghezza del registro e scadenza a 2.5s, tick immediato, NON CONFERMATO senza messa in coda, requestSport confermato all'arrivo dello stato.

- **Costo:** medio
- **File:** `wear/src/main/java/it/vantaggi/scoreboardessential/wear/WearHaptics.kt (nuovo)`, `wear/src/main/java/it/vantaggi/scoreboardessential/wear/WearViewModel.kt`, `wear/src/main/java/it/vantaggi/scoreboardessential/wear/MainActivity.kt`, `wear/src/test/java/it/vantaggi/scoreboardessential/wear/WearViewModelTest.kt`
- **Verifica:** Test col vibratore finto:
- invio riuscito e poi applyStateV2 con registro +1: pattern 'conferma destra' per il lato 2;
- nessuno stato entro 2.5s: [0,400] e transitorio NON CONFERMATO, coda invariata;
- invio fallito: pattern 'in coda' del lato.
L'emulatore non vibra: la distinzione fra 1 e 2 impulsi va provata su un orologio vero, a braccio in movimento.

#### 8. Marcatore non automatico: finestra CHI? di 8s dopo la ricevuta del gol, solo da collegati. Lista su nero co

8. Marcatore non automatico: finestra CHI? di 8s dopo la ricevuta del gol, solo da collegati. Lista su nero con SALTA in cima, righe da 52dp, barra colore, 400ms di guardia all'apertura, requestFocus, chiusura dopo 15s.

- **Costo:** medio
- **File:** `wear/src/main/java/it/vantaggi/scoreboardessential/wear/WearViewModel.kt`, `wear/src/main/java/it/vantaggi/scoreboardessential/wear/MainActivity.kt`, `wear/src/main/java/it/vantaggi/scoreboardessential/wear/PlayerSelectionActivity.kt`, `wear/src/main/res/layout/activity_player_selection.xml`, `wear/src/main/res/layout/item_player_wear.xml`, `wear/src/main/res/values/strings.xml`, `wear/src/main/res/values-it/strings.xml`
- **Verifica:** Test: dopo applyStateV2 con registro più lungo, a seguito di un tocco nel calcio con rosa, la finestra CHI? è aperta; da scollegati no; dopo 8s si chiude. Emulatore: la lista mostra almeno 2 giocatori insieme, la corona scorre, un tocco entro 400ms dall'apertura non sceglie nessuno.

#### 9. Ambient, guardia al risveglio e uscita. Da fare dopo la risposta del proprietario sulle domande 2 e 3. Ambi

9. Ambient, guardia al risveglio e uscita. Da fare dopo la risposta del proprietario sulle domande 2 e 3. AmbientLifecycleObserver con applyAmbient(on); spostamento anti burn-in; guardia di 500ms in onResume e all'uscita dall'ambient; tema Face con windowSwipeToDismiss=false solo su MainActivity.

- **Costo:** medio
- **File:** `wear/src/main/java/it/vantaggi/scoreboardessential/wear/MainActivity.kt`, `wear/src/main/res/values/theme.xml`, `wear/src/main/AndroidManifest.xml`
- **Verifica:** Emulatore con always-on attivo: abbassando lo schermo restano le cifre light e '34'' nel calcio, e nessun elemento colorato. Al risveglio il primo tocco entro 500ms non segna. Uno swipe verso destra sul quadrante non lo chiude. Su orologio vero: misurare il consumo di un'ora in ambient, e vedere se senza ambient il sistema torna all'app al risveglio.

#### 10. Selezione sport: titolo a 14sp, voci a 20sp, '✓ in uso' cyan, requestFocus, 'CAMBIO SPORT…' e 'SPORT NON C

10. Selezione sport: titolo a 14sp, voci a 20sp, '✓ in uso' cyan, requestFocus, 'CAMBIO SPORT…' e 'SPORT NON CAMBIATO' nella riga E.

- **Costo:** piccolo
- **File:** `wear/src/main/res/layout/activity_sport_selection.xml`, `wear/src/main/res/layout/item_sport_wear.xml`, `wear/src/main/java/it/vantaggi/scoreboardessential/wear/SportSelectionActivity.kt`, `wear/src/main/res/values/strings.xml`, `wear/src/main/res/values-it/strings.xml`
- **Verifica:** Emulatore: titolo su una riga, corona che scorre, conferma solo quando il quadrante è ridisegnato col nuovo sport.

#### 11. Verifica finale su tre AVD: Wear_OS_Small_Round 192dp, un tondo da 227dp, un quadrato. Screenshot 'dopo' d

11. Verifica finale su tre AVD: Wear_OS_Small_Round 192dp, un tondo da 227dp, un quadrato. Screenshot 'dopo' dei quattro stati delle bozze, misure col Layout Inspector confrontate col passo 0, annotazione in MIGRATION_PLAN.md.

- **Costo:** medio
- **File:** `MIGRATION_PLAN.md`
- **Verifica:** Sul quadrato gli angoli restano vuoti e le fasce hanno lo stesso ordine. Sul 227dp le cifre sono a 68sp. Nessun testo troncato in italiano né in inglese. Le misure prima/dopo sono scritte nel piano.

### Schermata per schermata

#### Quadrante di gioco: impaginazione a fasce (calcio, padel, tennis)

- **Ora:** activity_main.xml mette tutto in un BoxInsetLayout (boxedEdges=all) più 4dp di padding: il contenuto è 127.8×127.8dp dentro un cerchio da 192. Dall'alto:
- pallino di collegamento da 12dp;
- cronometro o periodo a 18sp (marginTop 16dp);
- lati, dalla guida al 20% (25.6dp) fino alla riga del gesto a 9sp;
- SPORT e AZZERA: 48dp più 12dp di margine, cioè 60 dei 127.8dp (47%).
Conto dal layout, da confermare al passo 0: la fascia dei punteggi è alta circa 30dp, quindi circa 26sp nel calcio. Nel padel la riga dei set da 14dp lascia al numero circa 16dp, cioè 12-14sp, meno del "Set 1" a 18sp. Il K da 48dp sta fra i lati: ogni lato toccabile misura circa 40×30dp nel calcio e 64×30 nella racchetta.
- **Dopo:** Radice nera. Sopra resta l'anello del portiere; poi una ConstraintLayout con guide in percentuale e margini di corda per fascia (vedi i token).
- A (18-48dp): contesto a 20sp, più il K nel calcio.
- B (50-124): due cifre bianche a 58sp fissi.
- C: strisce.
- D (132-148): dettaglio a 13sp.
- E (150-166): stato a 12sp.
- F (168-188): glifo del menu.
Bersagli: cronometro e K in alto (circa 46×44 e 56×42dp visibili); lati da circa 80×74dp; bersaglio inferiore alto 60dp per menu o CHI?; zone morte di 4dp e 8dp fra le fasce toccabili.
Ordine di lettura: 1) le cifre; 2) la fascia A, sempre un numero nello stesso posto; 3) la fascia E, solo se c'è un'anomalia. Dal primo sguardo escono SPORT, AZZERA, il pallino, il K centrale e il colore sulle cifre.
- **Perche':** In mezzo secondo, col braccio in movimento, si legge una forma grande e nient'altro. La fascia centrale del cerchio è larga fino a 178dp, il quadrato inscritto 128. L'altezza delle cifre passa da circa 26sp (calcio) e 12-14sp (padel) a 58sp; l'area di ogni lato passa da circa 1.200 a circa 5.900dp². Un solo layout per i tre sport: il numero non cambia misura quando si cambia sport.
- **File:** `wear/src/main/res/layout/activity_main.xml`, `wear/src/main/res/values/dimens.xml`, `wear/src/main/res/values-sw210dp/dimens.xml`, `wear/src/main/res/values-notround/dimens.xml (nuovo)`, `wear/src/main/res/values/theme.xml (TextAppearance.Face.Score, .Context, .Keeper, .Detail, .Status)`, `wear/src/main/res/values/colors.xml`, `wear/src/main/java/it/vantaggi/scoreboardessential/wear/MainActivity.kt`
- **Difetti della validazione:** L7 [bassa] La riga di stato senza telefono in italiano probabilmente non entra nel quadrante da 192dp; MIGRATION_PLAN, passata sull'orologio: il K da 48dp è la cucitura fra i due lati e ne toglie la larghezza

#### Quadrante di gioco: cifre bianche e strisce di squadra

- **Ora:** Il collector di team1Color/team2Color fa setTextColor(colore grezzo) su #121212. I default reggono (giallo 13.27:1, verde 14.34:1), ma il telefono sceglie il colore con un ColorPickerView libero con barra della luminosità. Il blu #0000FF fa 2.18:1, il navy #000080 1.17:1, il nero 1.12:1: il punteggio sparisce. È il difetto che L11 registra sul telefono; per l'orologio non è registrato.
- **Dopo:** Cifre sempre #FFFFFF su #000000 (21:1). Il colore della squadra passa a una View striscia 28×4dp sotto ogni colonna, colorata con ReadableColor.graphicOnBlack(c, 3.0) (navy diventa #4F4FA7). Finché il colore non arriva dal telefono restano i default #FFD600 e #76FF03.
- **Perche':** Al sole conta la luminanza, non la tinta. Nel modello indicativo con schermo a 1000 nit e 300 nit riflessi, il bianco resta a 4.33:1, il rosa scende a 1.67 e il navy a 1.05. La tinta la sceglie l'utente, quindi non le si può affidare il numero. La posizione fissa più la striscia dicono di chi è il numero.
- **File:** `wear/src/main/res/layout/activity_main.xml (View team1Stripe, team2Stripe)`, `wear/src/main/java/it/vantaggi/scoreboardessential/wear/MainActivity.kt (collector dei colori: setBackgroundColor sulla striscia, non setTextColor)`, `wear/src/main/res/values/colors.xml (face_black, score_white, signal_amber, ambient_gray)`, `shared/src/main/java/it/vantaggi/scoreboardessential/shared/utils/ReadableColor.kt (nuovo)`, `shared/src/test/java/it/vantaggi/scoreboardessential/shared/utils/ReadableColorTest.kt (nuovo)`
- **Difetti della validazione:** L11 [media] Etichette delle rose e righe dei punti nel registro nel colore grezzo della squadra (stesso difetto sull'orologio, non registrato); L11 [media] Nelle impostazioni la scritta dei pulsanti colore è quasi bianca sul colore della squadra (textOn in :shared è l'utilità comune che il rimedio chiede)

#### Quadrante di gioco: fascia A (contesto) e fascia D (dettaglio)

- **Ora:** Calcio: "00:00" a 18sp bianco anche in pausa; col v2 resta fermo per tutta la partita (L7 alta, riga 476). Racchetta: la stessa riga mostra "Set 2" grigio a 18sp, e game e set stanno in due righe speculari side1Secondary/side2Secondary ("6-4 · 3-6 · 2-1" e "4-6 · 6-3 · 1-2") in autoSize 7-11sp: la stessa informazione due volte al corpo più piccolo.
- **Dopo:** Calcio: A = cronometro a 20sp bold, #FFFFFF se corre e #9E9E9E se fermo; il tocco lo avvia e lo ferma come oggi. D vuota.

Racchetta, partita in corso:
- A = ultimo segmento di side1Secondary, diviso sul separatore " · " e scritto "4 – 3" (en dash).
- D = periodLabel in maiuscolo, più i segmenti precedenti (set chiusi): "SET 3 · 6-4 · 3-6", "TIE-BREAK · 6-4", "SET 1".
- Padel a set unico: D vuota.

Racchetta, partita finita (matchOver):
- A vuota, perché RacketRules.secondary non accoda i game quando wonBy != null e l'ultimo segmento sarebbe un set chiuso.
- D = side1Secondary intera, solo se è diversa da "side1Primary-side2Primary". Nel padel finito primario e riga coincidono (finalScore = game del set), quindi D resta vuota.

In tutti i casi: side2Secondary non si mostra più. Se il separatore non c'è, A mostra la stringa intera.

RacketRules.SEPARATOR è private: l'orologio ne tiene una copia (FaceText.SET_SEPARATOR = " · ") in una funzione pura FaceText.split(state): Pair<String,String>, protetta da un test JVM che fa girare MatchEngine con le RacketRules vere.
- **Perche':** La seconda lettura deve essere un numero, sempre nello stesso posto. Oggi nella racchetta quel posto è occupato da un'etichetta e il dato che conta (i game) sta a 7sp. Il colore del cronometro dice se corre, senza un segno in più.
- **File:** `wear/src/main/java/it/vantaggi/scoreboardessential/wear/FaceText.kt (nuovo, funzione pura)`, `wear/src/test/java/it/vantaggi/scoreboardessential/wear/FaceTextTest.kt (nuovo)`, `wear/src/main/java/it/vantaggi/scoreboardessential/wear/MainActivity.kt (applyClockRole, bindDetail)`, `wear/src/main/res/layout/activity_main.xml`
- **Difetti della validazione:** L7 [alta] Nel calcio col protocollo v2 il cronometro dell'orologio resta fermo sul valore del primo stato v2: prerequisito (passo 1). Senza, il design promuoverebbe a seconda lettura un '00:00' falso; L9 [media] Riassunto di una partita a racchetta non finita: stessa trappola sui game del set in corso, qui evitata a schermo

#### Quadrante di gioco: portiere (solo calcio)

- **Ora:** "K" a 16sp in un quadrato da 48dp al centro, fra i due lati: toglie 48dp alla loro larghezza ed è il bersaglio più vicino a quelli più toccati. Il testo dice sempre "K" (keeperTimer.text = "K" in tutti i rami): il tempo si legge solo dall'anello rosa. Un tocco con il timer che corre lo AZZERA (toggleKeeperTimer chiama resetKeeperTimer). Rosa su #121212 fa 4.48:1, sotto AA.
- **Dopo:** Il K sale in fascia A, a destra del cronometro (da x=101), a 14sp bold:
- "K" #9E9E9E da fermo;
- "K 4:12" #F50057 mentre corre, con i secondi da keeperProgress (5.02:1 sul nero);
- "K 0:00" #FF1744 scaduto.
Bersaglio x 96-192, y 0-46 (circa 56×42dp visibili). L'anello resta sul bordo, rosa, solo mentre corre. Il comportamento del tocco non cambia (avvio, e azzeramento se corre): cambiarlo è logica di L8.
- **Perche':** Libera il centro per le cifre. Il tempo si legge in cifre invece che stimarlo da un arco. Porta il bersaglio da 96×36 (proposta di partenza, in fascia D) a circa 56×42 senza rubare altezza al punteggio. L'anello resta perché il bordo è l'unico posto dove un'avanzata si vede senza guardare.
- **File:** `wear/src/main/res/layout/activity_main.xml`, `wear/src/main/java/it/vantaggi/scoreboardessential/wear/MainActivity.kt (collector di keeperTimer e keeperProgress)`, `wear/src/main/res/values/dimens.xml`, `wear/src/main/res/values-sw210dp/dimens.xml`
- **Difetti della validazione:** L8 [media] L'anello del portiere ha un massimo fisso di 300: con le cifre accanto, un anello pieno davanti a 'K 9:12' rende visibile l'errore; L11 [bassa] Contrasti insufficienti (stesso tipo sull'orologio: K rosa a 4.48:1 su #121212, 5.02 sul nero)

#### Quadrante di gioco: chi serve (padel, tennis)

- **Ora:** Il telefono pubblica KEY_SERVING_SIDE nello stato v2 (MainViewModel.kt:862, 0 se nessuno serve), ma WearScoreState non ha il campo e l'orologio non mostra niente. Al polso non si sa chi serve.
- **Dopo:** WearScoreState riceve val servingSide: Int = 0, con default, così i costruttori esistenti e i test non cambiano. fromDataMap lo legge con getInt(KEY_SERVING_SIDE, 0); rebuildLocalState lo prende da display.servingSide ?: 0. Il lato che serve ha un pallino bianco da 8dp sul lato esterno della sua colonna, all'altezza della sommità delle cifre. Le colonne riservano sempre 12dp, quindi il numero non si sposta. Con 0 (calcio, partita finita) il pallino è nascosto. In ambient resta. Nessuna chiave nuova: il golden test del protocollo non cambia.
- **Perche':** Nel padel e nel tennis chi segna col polso è spesso un giocatore, e dopo il punteggio la domanda è chi serve. Oggi la risposta sta solo sul telefono in borsa.
- **File:** `wear/src/main/java/it/vantaggi/scoreboardessential/wear/WearViewModel.kt (WearScoreState, fromDataMap, statoDaDisco, rebuildLocalState)`, `wear/src/main/java/it/vantaggi/scoreboardessential/wear/MainActivity.kt`, `wear/src/main/res/layout/activity_main.xml`, `wear/src/test/java/it/vantaggi/scoreboardessential/wear/WearViewModelTest.kt`
- **Difetti della validazione:** L9 [media] Tennis: dopo un tie-break il set successivo lo apre al servizio il lato sbagliato: diventa visibile al polso, il pallino starebbe dalla parte sbagliata davanti a chi gioca

#### Quadrante di gioco: riga di stato E (sostituisce pallino e riga del gesto)

- **Ora:** C'è un pallino da 12dp verde o rosso in cima. Il 13 settembre il piano ha mostrato che il verde mente: la capability risponde anche a collegamento caduto. All'avvio il pallino parte rosso, perché ConnectionState vale Disconnected finché non arriva la risposta. refreshHint scrive in rosso a 9sp "NIENTE TELEFONO - 12 IN ATTESA", stimato 135-140dp contro circa 128 disponibili, quindi troncato. Il conteggio compare solo da scollegati: con il telefono raggiungibile e una coda bloccata non dice niente.
- **Dopo:** Pallino rimosso. Una sola riga E, 12sp bold maiuscolo, calcolata da StatoFiducia.calcola(input), funzione pura. Input: collegato, verificaInCorso, pendingCount, collegatoConCodaDaMs, matchOver, decrementIsUndo, ultimoStatoVivoAlle, messaggio transitorio. Vince la prima condizione vera:
1) "n RIFIUTATI", rosso. Posto riservato: si accende solo quando L5 porterà il NACK.
2) Messaggio transitorio, 2-3s: "NON CONFERMATO" rosso; "n CONSEGNATI" e "CHIUSURA…" #E0E0E0; "NON CONFERMATA" ambra (fino al passo 5 era "NON CHIUSA": falso, perche' il comando di chiusura non si ritira).
3) Collegato, coda > 0 da 10s o più: "n NON CONSEGNATI", ambra. SOLO visualizzazione: nessun nuovo invio automatico, perché L5 avverte che un tentativo senza id idempotente può applicare la coda alla partita sbagliata.
4) Collegato, coda > 0 da meno di 10s: "INVIO n…", #E0E0E0.
5) Scollegato, coda > 0: "n IN CODA", ambra.
6) Scollegato, coda vuota, verifica conclusa: "SCOLLEGATO · 18:42", ambra. L'ora è quella dell'ultimo stato v2 ricevuto dal vivo; senza, solo "SCOLLEGATO".
7) "PARTITA FINITA", #E0E0E0.
8) Suggerimento "TIENI: −1" oppure "TIENI: ANNULLA", #9E9E9E.

verificaInCorso vale true per al massimo 2s dopo onResume e refreshConnection: in quel tempo le voci 5-6 non compaiono, così all'avvio non lampeggia SCOLLEGATO. Con partita in corso e schermo acceso, refreshConnection ogni 15s (il listener non vede il Bluetooth che cade). Tutte le frasi stanno sotto i 18 caratteri (EN: n REJECTED, NOT CONFIRMED, n NOT DELIVERED, SENDING n…, n QUEUED, OFFLINE · 18:42, MATCH OVER, HOLD: −1, HOLD: UNDO). La più lunga, "SCOLLEGATO · 18:42", è stimata circa 128dp contro circa 131 utili, con autoSize fino a 10sp come riserva. accessibilityLiveRegion=polite.
- **Perche':** Sul polso la norma è che vada tutto bene: un segnale fisso di "ok" costa attenzione a ogni sguardo e, quando mente, costa fiducia. L'anomalia si dice a parole, non solo col colore: funziona per i daltonici e al sole. Ambra e rosso distano solo 2.14:1 fra loro, quindi le distingue la parola. La coda resta visibile anche da collegati, così i blocchi di L5 smettono di essere silenziosi.
- **File:** `wear/src/main/java/it/vantaggi/scoreboardessential/wear/StatoFiducia.kt (nuovo, funzione pura)`, `wear/src/test/java/it/vantaggi/scoreboardessential/wear/StatoFiduciaTest.kt (nuovo)`, `wear/src/main/java/it/vantaggi/scoreboardessential/wear/WearViewModel.kt (istante di inizio coda da collegati, ultimo stato vivo, verificaInCorso, messaggi transitori)`, `wear/src/main/java/it/vantaggi/scoreboardessential/wear/MainActivity.kt (refreshHint diventa renderStatus; applyConnectionState non colora più il pallino; refresh ogni 15s)`, `wear/src/main/java/it/vantaggi/scoreboardessential/wear/LastKnownMatch.kt (ricevutoAlle, per l'ora dopo un riavvio)`, `wear/src/main/res/layout/activity_main.xml`, `wear/src/main/res/values/strings.xml`, `wear/src/main/res/values-it/strings.xml`
- **Difetti della validazione:** L7 [bassa] La riga di stato senza telefono in italiano probabilmente non entra nel quadrante da 192dp; L5 [bassa] A telefono raggiungibile il polso non dice più che ci sono punti non consegnati; L5 [alta] Un arretrato consegnato e mai confermato blocca l'orologio (visibile: INVIO diventa NON CONSEGNATI dopo 10s); L5 [alta] Una coda rifiutata o non confermata non si scarta mai (visibile prima della partita successiva); L5 [media] Un tocco dal vivo al ricollegamento arriva prima dell'arretrato (il refresh ogni 15s riduce la finestra in cui Connected resta falso); MIGRATION_PLAN 13 settembre: il pallino verde mente

#### Quadrante di gioco: bersaglio inferiore (menu) e nuovo menu partita

- **Ora:** Due bottoni borderless a 14sp, 48dp, sempre visibili. SPORT a partita cominciata mostra solo un Toast "Partita in corso": per tutta la partita è un bersaglio che non fa niente. AZZERA apre un android.app.AlertDialog mai visto sul tondo ("Finire la partita?" / "Punteggi e cronometri tornano a zero."): il bottone dice azzera e il dialogo dice finisci. Nel calcio v2 quel percorso perde la partita (L4 alta).
- **Dopo:** Sul quadrante resta un solo bersaglio in basso (y 132-192, x 24-168) con il glifo a tre punti #9E9E9E e contentDescription "Menu partita". Apre MenuActivity, di contorno e quindi street: BoxInset, asphalt_dark, card StreetCard su concrete_gray, voci a 20sp.
Voci:
- "SPORT" con sottotitolo lo sport in uso. Disattivata (alpha della card 0.5, testo sempre ≥ 4.5:1) con sottotitolo "Partita in corso" oppure "Prima consegna n punti" in ambra.
- "FINE PARTITA" in #FF1744, sottotitolo "Salva 3–2 sul telefono". Al primo tocco la card si riempie di #FF1744 e dice "CHIUDERE 3–2?" in nero, sottotitolo "Tocca di nuovo". Il secondo tocco vale solo dopo 600ms ed entro 5s, poi la card torna com'era. Niente AlertDialog.
- FINE PARTITA disattivata in tre casi: con coda > 0 ("Prima consegna n punti"); da scollegati ("Serve il telefono"); nel calcio dopo il primo v2 ("Nel calcio chiudi dal telefono"), finché il rimedio L4 non è fatto.
Dopo la conferma, la riga E dice "CHIUSURA…" finché non arriva un v2 con matchInProgress=false. Senza risposta entro 10s dice "NON CONFERMATA" in ambra: il comando, inviato urgente, puo' ancora arrivare. La conferma vale solo se il registro passa da pieno a vuoto dopo il comando; a partita non cominciata FINE PARTITA e' spenta ("Niente da salvare").
La voce "n RIFIUTATI" si aggiunge in cima quando L5 porterà il NACK e la schermata di decisione TIENI/SCARTA. Il menu si chiude da solo dopo 10s senza input. La corona lo scorre (requestFocus).
- **Perche':** Sono azioni da una volta per partita: oggi occupano quasi metà dell'altezza utile e mettono un bersaglio distruttivo sotto il pollice di chi segna senza guardare. Se cambia il significato cambia anche il segno: la card diventa rossa piena. Bloccare la fine con la coda piena impedisce di fondere due partite (L4 alta) finché la logica non sa separarle. Il blocco nel calcio evita di perdere la partita.
- **File:** `wear/src/main/java/it/vantaggi/scoreboardessential/wear/MenuActivity.kt (nuovo, sul modello di SportSelectionActivity)`, `wear/src/main/java/it/vantaggi/scoreboardessential/wear/MenuVoci.kt (nuovo, funzione pura che decide voci e blocchi)`, `wear/src/test/java/it/vantaggi/scoreboardessential/wear/MenuVociTest.kt (nuovo)`, `wear/src/main/res/layout/activity_menu.xml (nuovo)`, `wear/src/main/res/layout/item_sport_wear.xml (riuso: titolo più riga secondaria)`, `wear/src/main/res/drawable/ic_menu_dots.xml (nuovo)`, `wear/src/main/AndroidManifest.xml`, `wear/src/main/java/it/vantaggi/scoreboardessential/wear/MainActivity.kt (via btn_sport, btn_start_new_match, Toast e AlertDialog)`, `wear/src/main/res/values/strings.xml`, `wear/src/main/res/values-it/strings.xml`
- **Difetti della validazione:** L4 [alta] AZZERA/Finisci sull'orologio nel calcio svuota il motore del telefono: evitato bloccando la voce nel calcio v2 fino al rimedio L4; L4 [alta] NUOVA PARTITA/AZZERA dal polso non separa la coda: evitato bloccando la fine con coda > 0; L5 [media] L'arretrato non dice di che sport è: con coda > 0 non si cambia sport dal polso

#### Quadrante di gioco: partita finita

- **Ora:** applyMatchOver porta i lati ad alpha 0.4: il giallo diventa #71600B su #121212 (3.02:1), il verde #3A710C (3.16:1). Il risultato finale è la cosa meno leggibile dello schermo. isClickable=false non basta: il tocco vibra la conferma o va in coda per un punto che il motore ignora (L7 media).
- **Dopo:** Cifre a piena intensità (21:1). La riga E dice "PARTITA FINITA". I lati non vibrano e non mandano niente: guardia in incrementScore (rimedio L7) più un controllo di matchOver nel listener del tocco. Il tocco lungo resta attivo, perché annullare l'ultimo punto riapre la partita, e la riga lo ricorda solo se non c'è altro da dire. Nella racchetta D mostra i set ("6-4 · 3-6 · 7-5"). In ambient il risultato resta.
- **Perche':** A partita finita tutti chiedono com'è finita: abbassare proprio allora il contrasto è l'opposto di ciò che serve. Che i lati siano spenti lo dicono la parola e l'assenza di vibrazione.
- **File:** `wear/src/main/java/it/vantaggi/scoreboardessential/wear/MainActivity.kt (applyMatchOver, listener dei lati)`, `wear/src/main/java/it/vantaggi/scoreboardessential/wear/WearViewModel.kt (incrementScore)`
- **Difetti della validazione:** L7 [media] A partita finita i lati dell'orologio restano toccabili, e il telefono registra una riga e un annullamento fantasma

#### Aptica: ricevuta del tocco

- **Ora:** sendScoreIntent vibra PATTERN_CONFIRM (120ms) appena sendMessage restituisce true, cioè alla consegna al servizio del telefono, anche ad app chiusa, quando il punto va perso (L5 alta). In coda: 180ms. Errore: 60/120/60. Allarme portiere: 200/100/200. La conferma è uguale per i due lati.
- **Dopo:** Nuovo WearHaptics.kt con i pattern dell'orologio:
- conferma sinistra [0,70];
- conferma destra [0,70,90,70];
- in coda sinistra [0,70,120,350];
- in coda destra [0,70,90,70,120,350];
- annullamento/correzione [0,30,50,30,50,30];
- non confermato/errore [0,400]: un colpo solo, perché il vecchio doppio colpo coinciderebbe con "destra";
- allarme del portiere invariato.
Al tocco accettato parte subito un tick di sistema (performHapticFeedback, VIRTUAL_KEY), così non si tocca una seconda volta perché non ha vibrato.
Se sendMessage restituisce true, il ViewModel registra una ricevuta attesa: lato, tipo, lunghezza del registro in quel momento (MatchLogCodec.decode(eventLog)?.size), scadenza a 2.5s. Il primo applyStateV2 con un registro di lunghezza diversa chiude la ricevuta più vecchia e suona il pattern del lato (o l'annullamento). Alla scadenza senza cambio: [0,400] e "NON CONFERMATO" in riga E per 3s. Il tocco NON viene messo in coda dopo il timeout: senza id idempotente (L5) potrebbe contare due volte. Se sendMessage fallisce resta la coda di oggi, col pattern in coda del lato. L'aptica passa da un'interfaccia iniettabile, per i test.
- **Perche':** Il numero di impulsi dice per chi, la coda lunga dice che il telefono non ce l'ha: è l'unico canale che non chiede di togliere gli occhi dal campo, e deve dire la verità. È il rimedio che L5 stessa indica ("preso solo quando arriva uno stato v2 con un registro più lungo"). Qui però si ferma alla segnalazione, e la messa in coda resta a L5.
- **File:** `wear/src/main/java/it/vantaggi/scoreboardessential/wear/WearHaptics.kt (nuovo)`, `wear/src/main/java/it/vantaggi/scoreboardessential/wear/WearViewModel.kt (sendScoreIntent, applyStateV2, ricevute)`, `wear/src/main/java/it/vantaggi/scoreboardessential/wear/MainActivity.kt (tick al tocco)`, `wear/src/test/java/it/vantaggi/scoreboardessential/wear/WearViewModelTest.kt`
- **Difetti della validazione:** L5 [alta] Un tocco singolo consegnato quando il ViewModel del telefono non esiste va perso, dopo la vibrazione di conferma (reso visibile: NON CONFERMATO invece di una conferma bugiarda); L5 [media] Due intenzioni ravvicinate partono in parallelo (la seconda ricevuta scade e lo dice); L7 [media] A partita finita il punto inerte vibrava la conferma

**Come e' stato fatto (passo 7, 2 ottobre 2026).** La ricevuta si apre al tocco, con la lunghezza del
registro di quel momento, e si toglie senza suonare se l'invio fallisce (vale la coda). Il tick e'
EFFECT_TICK suonato da WearHaptics, non performHapticFeedback: non dipende dall'impostazione di sistema
e non si somma agli impulsi; la conferma di lato parte non prima di 250ms dal tick dello stesso tocco.
Mentre un arretrato e' in volo nessuna ricevuta si chiude; senza un registro leggibile nello stato v2
le ricevute non si aprono. NON CONFERMATO non copre CHIUSURA…; a partita finita i lati stanno in
silenzio. Limite noto finche' L5 non mette nello stato v2 la sequenza dell'ultimo intento applicato: una
ricevuta si puo' chiudere per un punto, un ANNULLA o una correzione fatti dal telefono nello stesso
momento, oppure scadere su un tocco preso (errore dalla parte sicura). Un tocco ravvicinato tronca la
vibrazione precedente.

#### Quadrante di gioco: TalkBack

- **Ora:** contentDescription fissa dei lati ("Squadra 1. Tocca per segnare…"), senza il punteggio; le cifre non hanno una live region. Sul 30-15 il focus non legge nessun numero. Il pallino dice solo collegato o no.
- **Dopo:** renderScoreState scrive: "Squadra 1, 30. Tocca per segnare, tieni premuto per annullare". "AV" si legge "vantaggio" e "PV" "punto decisivo". Da scollegati si aggiunge "ultimo dato delle 18:42". accessibilityLiveRegion=polite sulle cifre e sulla riga E. Descrizioni per cronometro ("Cronometro 34:12, in corso. Tocca per fermare"), K e bersaglio in basso ("Menu partita" oppure "Chi ha segnato?"). TalkBack passa da performClick, quindi le guardie di tempo (risveglio) non lo bloccano.
- **Perche':** Chi non guarda deve ricevere prima di tutto il numero, come chi guarda.
- **File:** `wear/src/main/java/it/vantaggi/scoreboardessential/wear/MainActivity.kt (applyGestureLabels, renderScoreState)`, `wear/src/main/res/layout/activity_main.xml`, `wear/src/main/res/values/strings.xml`, `wear/src/main/res/values-it/strings.xml`
- **Difetti della validazione:** L7 [media] Con TalkBack il punteggio dell'orologio non viene letto

#### Ambient, risveglio e uscita dal quadrante

- **Ora:** Nessun AmbientLifecycleObserver e nessun FLAG_KEEP_SCREEN_ON: abbassando il polso subentra il quadrante di sistema. Al risveglio il primo tocco non ha nessuna protezione. Il tema è Theme.Material3.Dark.NoActionBar: lo swipe verso destra probabilmente chiude la schermata e distrugge il WearViewModel (da verificare). Il piano lascia ambient e keep-screen-on "da decidere".
- **Dopo:** Proposta, da confermare dal proprietario: ambient sì, keep-screen-on no. AmbientLifecycleObserver di androidx.wear 1.3.0, già dipendenza.
In ambient:
- fondo nero;
- cifre nella stessa posizione e misura, sans-serif-condensed-light bianco;
- calcio: A = "34'", aggiornata in onUpdateAmbient;
- racchetta: A invariata e pallino del servizio;
- nascosti: strisce, anello, K, D, glifo del menu, suggerimento;
- riga E solo con un'anomalia o a partita finita, in #BDBDBD.
burnInProtectionRequired: la radice si sposta di ±4dp a ogni aggiornamento. lowBitAmbient: niente anti-alias.
All'uscita dall'ambient e in onResume: 500ms in cui i tocchi sui lati sono ignorati in silenzio. Tema Theme.ScoreboardEssential.Face con android:windowSwipeToDismiss=false, solo per MainActivity: si esce col tasto. Le altre schermate tengono lo swipe come indietro.
- **Perche':** L'orologio serve per lo sguardo a polso basso. Con l'ambient il punteggio c'è quando si alza il braccio, senza tenere lo schermo acceso; se l'always-on è spento nel sistema il costo è zero. Il primo tocco dopo il risveglio serve quasi sempre a svegliare, non a segnare. Chiudere il quadrante per sbaglio a metà partita perde lo stato in memoria e l'ack (L5).
- **File:** `wear/src/main/java/it/vantaggi/scoreboardessential/wear/MainActivity.kt (observer, applyAmbient, guardia al tocco)`, `wear/src/main/res/values/theme.xml (Theme.ScoreboardEssential.Face, TextAppearance.Face.Score.Ambient)`, `wear/src/main/AndroidManifest.xml (tema di MainActivity; WAKE_LOCK è già dichiarato)`
- **Difetti della validazione:** MIGRATION_PLAN 'Non fatti': keep-screen-on e ambient da decidere; L5 [alta] Ack perso: si perde anche se l'Activity è stata chiusa con uno swipe (esposizione ridotta); L6 [bassa] Al risveglio l'orologio rigioca anche i DataItem che ha scritto lui (con l'ambient l'Activity non viene ricreata)

#### Orologio: l'icona della partita sul quadrante (Ongoing Activity)

- **Ora:** L'ambient c'e' (passo 9), ma vale solo finche' lo schermo resta in always-on. Allo spegnimento (timeout, o always-on spento) Wear OS manda l'attivita' in background e al risveglio mostra il quadrante: l'app e' viva, ma per riaprirla a meta' partita bisogna cercarla. Segnalato da Cioti il 28 ottobre 2026 sull'orologio vero.
- **Piano (UI Constitution):** *Gerarchia:* niente di nuovo dentro l'app; la superficie e' del sistema (l'icona sul quadrante e la riga fra i recenti). *Layout:* una sola riga, "Partita in corso", l'icona `sports` e il tocco che riapre il tabellone. *Componenti riusati:* nessuno; e' una notifica di sistema. *Stati:* in corso (icona e riga presenti), finita o chiusa da chi gioca (spariscono), permesso delle notifiche negato (niente icona, il tabellone funziona come prima), sistema che rifiuta il servizio in background (idem). *Movimento:* nessuno. *Accessibilita':* la riga e' testo, letto dal sistema; il tocco e' la superficie intera. *Schermo stretto e 200%:* non sono nostri, li decide il quadrante.
- **Dopo:** `PartitaInCorsoServizio` (servizio in primo piano di tipo `specialUse`, non esportato) pubblica una notifica in corso con `OngoingActivity` (androidx.wear:wear-ongoing): categoria `workout`, icona statica `notifica_partita`, stato "Partita in corso", tocco su `MainActivity`. `MainActivity` lo allinea a `WearViewModel.partitaInCorso`: parte solo a schermo acceso (da background il sistema rifiuta un servizio in primo piano), si ferma con la partita anche a schermo spento, e si ferma quando chi gioca chiude l'app (`isFinishing`); se e' il sistema a distruggere l'attivita' l'icona resta, ed e' lei che la riporta. Il permesso `POST_NOTIFICATIONS` (a runtime da Wear OS 4) si chiede una volta sola, alla prima partita. Il servizio non e' `STICKY`.
- **Perche':** Il testo e' fisso e non porta il punteggio: a schermo spento l'attivita' non ascolta piu' il telefono, e un numero fermo all'ultima accensione, proprio dove si guarda per fidarsi, sarebbe peggio di nessun numero. Il servizio in primo piano tiene vivo anche il processo: lo stato in memoria e gli ack in attesa (L5) non si perdono con lo schermo spento. Keep-screen-on resta escluso (batteria, decisione del passo 9).
- **Conflitti e debiti:** (1) `notifica_partita` e' lo stesso simbolo `sports` di `ic_sports` ma senza il tint del tema, che una notifica non risolve: sta fuori dalla tabella G-3 (nome senza `ic_`) e chiude per l'orologio il debito «icone di notifica di sistema» gia' dichiarato per il telefono, che resta aperto. (2) Se la partita comincia o finisce a schermo spento l'icona si allinea al prossimo risveglio (all'inizio) o subito (alla fine, perche' fermare e' sempre consentito). (3) Su Samsung esiste anche l'impostazione di sistema per rimanere sull'ultima app dopo il blocco: e' dell'utente, l'app non la puo' cambiare.
- **Pattern vietati (review.md):** nessuno dei vietati: nessun colore, durata o ombra scritti a mano; una sola riga di testo.
- **Orientamento (richiesta di Cioti, 28 ottobre 2026):** tutte le attivita' dell'orologio hanno `screenOrientation="portrait"`: l'orologio ruota lo schermo con il polso o l'inclinazione e il tabellone girava con lui, senza alcun vantaggio. Resta in verticale come un quadrante classico. Test: `OrientamentoVerticaleTest`.
- **File:** `wear/src/main/java/it/vantaggi/scoreboardessential/wear/PartitaInCorsoServizio.kt`, `MainActivity.kt` (osservaPartitaInCorso, sincronizzaPartitaInCorso), `wear/src/main/res/drawable/notifica_partita.xml`, `wear/src/main/AndroidManifest.xml`, `gradle/libs.versions.toml`

#### Selezione del marcatore (calcio)

- **Ora:** Si apre da sola dopo ogni tocco di gol, se la rosa non è vuota, anche da scollegati. BoxInset più 16dp di padding (circa 104dp di larghezza). Titolo HeadlineMedium 28sp "CHI HA SEGNATO?", stimato su 2-3 righe. Card StreetCard con nome a 24sp e ruolo a 16sp, alte circa 90dp: stimo un giocatore visibile per volta. Non dice quale gol né quale squadra. NESSUNO sta in fondo. Il rimbalzo del tocco può cadere sulla prima voce. Da scollegati la scelta si perde in silenzio.
- **Dopo:** Non si apre più da sola. Nei 8s dopo che il gol è confermato (ricevuta v2, vedi Aptica), da collegati, con attributesScorer e rosa non vuota, il glifo in basso diventa "CHI?" (13sp bianco, capsula col bordo #F50057). Toccando il bersaglio inferiore si apre la lista.
La lista:
- fondo nero, perché è in gioco;
- intestazione "GOL · 3–2" a 14sp e sotto una barra 28×4dp nel colore di squadra (regola 3:1);
- prima voce SALTA (l'attuale NESSUNO, spostato in cima e rinominato);
- poi i nomi a 18sp bold su righe da 52dp, senza card né ruolo;
- WearableRecyclerView a tutta larghezza con centratura dei bordi e requestFocus per la corona;
- per 400ms dall'apertura i tocchi sono ignorati;
- dopo 15s senza input si chiude come SALTA.
Da scollegati CHI? non compare: l'attribuzione si fa dopo, dal registro del telefono.
- **Perche':** La Fase D lo dice già: il dialogo interrompe nel momento di massima attenzione, e attribuire dopo rispetta l'attenzione. Il marcatore si riconosce dal nome, non dal ruolo. Una scelta che si perde in silenzio è un'interfaccia che mente: da scollegati non si offre.
- **File:** `wear/src/main/java/it/vantaggi/scoreboardessential/wear/WearViewModel.kt (showPlayerSelection non più automatico, finestra CHI?)`, `wear/src/main/java/it/vantaggi/scoreboardessential/wear/MainActivity.kt (bersaglio inferiore, extra: colore, punteggio)`, `wear/src/main/java/it/vantaggi/scoreboardessential/wear/PlayerSelectionActivity.kt`, `wear/src/main/res/layout/activity_player_selection.xml`, `wear/src/main/res/layout/item_player_wear.xml`, `wear/src/main/res/values/strings.xml`, `wear/src/main/res/values-it/strings.xml`
- **Difetti della validazione:** L3 [media] Senza telefono l'attribuzione del marcatore scelta al polso si perde in silenzio (evitato); L3 [media] Il marcatore scelto sull'orologio va all'ultimo punto del motore nel momento in cui arriva (finestra ridotta: si offre solo dopo la ricevuta, e l'intestazione dice quale gol); L3 [media] Marcatore dall'orologio: telefono e orologio decidono con rose diverse (meno esposto: non parte più da solo)

#### Selezione sport

- **Ora:** Titolo "SPORT" HeadlineMedium a 28sp; card StreetCard su concrete_gray col nome a 24sp e "in uso" in BodyLarge. Ci si arriva dal bottone SPORT del quadrante. La lista non chiama requestFocus, quindi la corona probabilmente non la scorre (da verificare).
- **Dopo:** Resta street: asphalt, StreetCard, condensed. Titolo a 14sp su una riga. Voci a 20sp in righe da almeno 52dp. "in uso" diventa "✓ in uso", con la ✓ in #00E5FF (10.84:1). requestFocus per la corona. Ci si arriva dal menu, che non la apre con partita in corso o coda piena. Dopo la scelta la riga E dice "CAMBIO SPORT…" finché non arriva un v2 col nuovo sportId; dopo 3s senza risposta "SPORT NON CAMBIATO" in ambra. La vibrazione di conferma arriva con lo stato, non con la consegna.
- **Perche':** Si usa prima della partita: qui l'identità ha il suo posto. Il resto è adattamento a 192dp più la stessa ricevuta onesta del tocco.
- **File:** `wear/src/main/res/layout/activity_sport_selection.xml`, `wear/src/main/res/layout/item_sport_wear.xml`, `wear/src/main/java/it/vantaggi/scoreboardessential/wear/SportSelectionActivity.kt`, `wear/src/main/java/it/vantaggi/scoreboardessential/wear/WearViewModel.kt (requestSport: conferma all'arrivo dello stato)`, `wear/src/main/res/values/strings.xml`, `wear/src/main/res/values-it/strings.xml`
- **Difetti della validazione:** L5 [alta] Un tocco singolo consegnato quando il ViewModel del telefono non esiste va perso ('lo stesso vale per il cambio sport'): la conferma non vibra più alla consegna

#### Formati: tondo da 227dp e quadrati

- **Ora:** values-sw210dp alza solo tre testi (timer 22sp, K 18sp, bottoni 16sp). Nessuna variante per i quadrati: BoxInset tiene tutto nel quadrato inscritto anche dove gli angoli ci sono.
- **Dopo:** 227dp: stesse guide in percentuale. In sw210dp: cifre 68sp, A 24sp, K 16sp, D 15sp, E 14sp; strisce 33×5dp, pallino 10dp; margini di corda ×1.18. Nessun elemento in più, niente nomi delle squadre. Quadrati (values-notround): margini di fascia a 0, stesse misure dei testi, stesso ordine delle fasce; gli angoli recuperati restano vuoti.
- **Perche':** Il mezzo secondo non cambia con il diametro: lo spazio in più va alla misura dei livelli già presenti, non a nuovi livelli.
- **File:** `wear/src/main/res/values-sw210dp/dimens.xml`, `wear/src/main/res/values-notround/dimens.xml (nuovo)`

### Rischi

CORREZIONI RISPETTO ALLA PROPOSTA VINCENTE, già applicate sopra:
1) K (96×36dp) e glifo del menu (64×32dp) erano sotto i 48dp. Il K sale in fascia A (circa 56×42dp visibili). Il bersaglio inferiore è alto 60dp e contiene D ed E, che non sono mai toccabili da sole.
2) L'autoSize per lato avrebbe dato a 'AV' e '40' misure diverse: cifre a dimensione fissa, 58sp e 68sp in sw210dp.
3) A partita finita l'ultimo segmento di side1Secondary è un set chiuso (RacketRules.secondary non accoda i game quando wonBy != null): con matchOver la fascia A resta vuota.
4) RacketRules.SEPARATOR è in un companion private: FaceText ne tiene una copia fissata da un test sul motore vero.
5) Il doppio colpo di errore di oggi (60/120/60) coinciderebbe con 'destra': l'errore diventa un colpo solo da 400ms.
6) 'Toccabile per riprovare' (innesto da P3) non entra: VALIDAZIONE L5 dice che un timeout da solo rimanda un batch che verrà rifiutato o applicato alla partita sbagliata. Si mostra 'NON CONSEGNATI' e il nuovo invio resta a L5.
7) Un tocco senza ricevuta non va in coda dopo il timeout, per non contare due volte: lo dice e basta.
8) FINE PARTITA rosso su #1E1E1E fa 4.33:1: voci di menu a 20sp bold, testo grande WCAG.
9) All'avvio ConnectionState vale Disconnected finché non arriva la risposta: senza verificaInCorso la riga direbbe SCOLLEGATO a ogni accensione.

RISCHI APERTI:
a) Tutte le misure in dp sono calcolate dal layout, non viste. Il passo 0 serve a questo, e le percentuali delle fasce vanno ritoccate se le misure lo chiedono. Il gruppo cronometro+K in fascia A ha solo circa 5dp di margine dall'anello.
b) Il design rende visibili i difetti invece di nasconderli. Finché L5 non è corretto, 'n NON CONSEGNATI' può restare acceso per sempre, e 'NON CONFERMATO' comparirà ogni volta che l'app del telefono è chiusa. È voluto, ma va detto a chi usa l'orologio.
c) La ricevuta usa la lunghezza del registro: un punto segnato dal telefono nello stesso istante può chiudere per errore la ricevuta di un tocco del polso. Sarà esatta quando L5 metterà nello stato v2 la sequenza dell'ultimo intento applicato.
d) L'emulatore non vibra: la distinguibilità di 1 e 2 impulsi da 70ms e dei colpi lunghi va provata su un orologio vero, e su alcuni motori 70ms potrebbero essere deboli.
e) L'ambient: se il sistema torni all'app al risveglio, con o senza ambient, dipende da versione e impostazioni di Wear OS, e va misurato. Anche il font sans-serif-condensed-light va verificato sull'immagine Wear; il ripiego è condensed regular.
f) windowSwipeToDismiss=false va contro l'abitudine delle app Wear: si esce solo col tasto. Da confermare col proprietario.
g) I colori di squadra arrivano per la via v1 (ACTION_TEAM_COLOR_UPDATE), non ancora provata: le strisce partono dai default.
h) Bloccare FINE PARTITA dal polso nel calcio toglie una funzione, oggi comunque rotta (L4 alta), finché L4 non è corretto.
i) Costo realistico nel sistema a View: circa 8,5 giorni-persona. Passi 0-5 circa 3 giorni, e danno già la riga di stato onesta, il menu e le correzioni di contrasto; passo 6 circa 1,5; passi 7-11 circa 4. È più dei 5-6 della proposta di partenza, per via degli innesti (aptica, StatoFiducia, marcatore, menu senza AlertDialog).

OPZIONE SEPARATA, Compose for Wear OS (Material 3 per Wear). Porterebbe CurvedText (la riga di stato lungo l'arco, spazio per frasi più lunghe), EdgeButton (il bersaglio inferiore già sagomato sul tondo), ScalingLazyColumn per marcatore, sport e menu con la corona già gestita, e l'ambient integrato. Costo stimato 8-10 giorni in più: riscrittura di 5 layout e del rendering di 3 Activity, compilatore Compose, APK più pesante di qualche MB, test d'interfaccia da rifare, una seconda tecnologia rispetto al telefono. Non ora: StatoFiducia, FaceText, MenuVoci e ReadableColor sono funzioni pure e passerebbero identiche a Compose, quindi questo lavoro non va perso se un giorno si cambia.

### Decisioni del proprietario

- Identità: sul quadrante le cifre diventano bianche e il colore della squadra passa a una striscia sotto il numero. Si guadagna la lettura al sole e nessun colore può far sparire il punteggio, ma si perdono i numeri gialli e verdi che oggi riconosci a colpo d'occhio. Va bene, o preferisci cifre nel colore di squadra schiarito ad almeno 4.5:1 (per esempio il navy diventa #6A70AB), sapendo che al sole rendono meno?
- Il piano dice che ambient e keep-screen-on vanno decisi da te. Raccomando ambient sì (punteggio visibile a polso basso, cifre light, consumo da misurare sul tuo orologio) e keep-screen-on no. Confermi?
- Il quadrante non si chiude più con uno swipe verso destra: si esce solo col tasto dell'orologio. Protegge la partita da una chiusura accidentale, ma va contro l'abitudine delle app Wear. Lo vuoi?
- Il marcatore non si apre più da solo dopo il gol: per 8 secondi il bersaglio in basso diventa 'CHI?', e dopo si attribuisce dal registro del telefono. Quanto usate l'attribuzione dal polso? Se la usate a ogni gol, 8 secondi bastano o servono di più?
- Priorità: finché L4 non è corretto propongo di disattivare FINE PARTITA dal polso nel calcio, perché oggi quel percorso perde la partita. Preferisci questo blocco, oppure correggere L4 prima di questa pista, così la voce resta attiva anche nel calcio?

### Il giudizio

Pesando i criteri nell'ordine dato vince la prima proposta, perché è l'unica che affronta il problema dominante dell'orologio.

**Uso reale.** Il problema dominante è il punteggio schiacciato in una fascia di circa 30dp del quadrato inscritto. Ho verificato su activity_main.xml:
- BoxInsetLayout con boxedEdges=all e padding 4dp;
- guida al 20%;
- K da 48dp fra i lati;
- SPORT e AZZERA da 48dp più 12dp di margine sul fondo;
- nel padel, riga dei set da 14dp e cifre in autoSize.

P1 porta le cifre a circa 64sp e i bersagli a circa 90x80dp, con una sola impaginazione per tutti gli sport. Le cifre sono sempre bianche su nero (21:1, e al sole la luminanza è l'unica cosa che conta). Il colore della squadra passa a una striscia a 3:1.

P2 ottiene bersagli simili (circa 83x74dp) ma aggiunge un filtro che scarta in silenzio tocchi legittimi: il blocco di 1s per lato perde i tocchi rapidi di recupero. Toglie anche la correzione per lato nel calcio. Costa 8-9 giorni.

P3 lascia l'impaginazione quasi com'è: è la più onesta sullo stato, ma non risolve la lettura in mezzo secondo, e il suo stato "di memoria" in grigio light peggiora proprio la leggibilità.

**Verità dello stato.** P1 fa già molto:
- toglie il pallino verde che mente;
- rende visibile la coda anche da collegati;
- smette di spegnere il risultato finale;
- mostra il punteggio nella conferma di fine partita.

I due punti deboli di P1 (vibrazione alla consegna invece che all'applicazione, nessun timeout del batch in volo) si coprono innestando il vocabolario aptico di P2 e il modello StatoFiducia di P3.

**Accessibilità.** Le tre proposte hanno conti corretti; P1 è anche l'unica che smonta numericamente la regola attuale del telefono.

**Costo.** P1 costa 5-6 giorni contro gli 8-9 di P2. P3 costa 3 giorni più 2, ma il suo tempo 2 dipende da L5.

**Fase D.** P1 la estende e la contesta apertamente su due punti argomentati: la selezione del marcatore è anch'essa una schermata in gioco, e l'ambient va deciso.

Correzioni da fare al vincitore prima di implementarlo:
1. Sostituire la chiusura automatica del marcatore dopo 10s con la pill CHI di P2, senza apertura automatica.
2. Prendere l'aptica di P2 (conferma quando torna lo stato v2, 1 o 2 impulsi secondo il lato) e il timeout di 10s con "NON CONSEGNATI" di P3.
3. Aggiungere windowSwipeToDismiss=false e la guardia su matchOver in incrementScore.
4. A partita vinta, non mostrare in A l'ultimo set chiuso come se fossero i game in corso.
5. Correggere prima L7 alta (riga 476), come P1 stesso dichiara.

**Errori trovati dal giudice nelle proposte.** Ho ricalcolato con la formula WCAG oltre 40 coppie di colori, comprese le mescolanze al bianco con il passo che ciascuna proposta dichiara. I rapporti sono tutti corretti entro 0.01, e anche i t minimi della mescolanza tornano:
- P1, soglia 3:1 a passo 0.01: t=0.31, 0.20 e 0.36;
- P2, soglia 4.5 a passo 0.05: t=0.35, 0.40, 0.35 e 0.50;
- P3, a passo 0.01: t=0.35, 0.37, 0.21 e 0.46.

Il caso peggiore nero/bianco dà 4.58 (#8454F6 con la soglia 0.179, #5D60FF col massimo fra i due). Gli alpha a partita finita danno #71600B 3.02 e #3A710C 3.16.

Errori e imprecisioni trovati:

P1:
- Con la regola attuale del telefono (coppia #E0E0E0/#1E1E1E) il massimo raggiungibile è 3.556, non "nessuna soglia supera 3.55": arrotondamento trascurabile.
- Navy al sole: 1.05 contro #000, non 1.07. Trascurabile, perché dipende dal fondo scelto nel modello.
- Incoerenza interna: i comandi in fondo occupano "47%" in una scheda e "31%" in un'altra.
- Il margine di 28dp per la fascia A è il valore a metà fascia: al bordo alto servirebbero circa 43dp. Non cambia nulla, perché il testo è corto e centrato.
- A partita vinta l'ultimo segmento di side1Secondary è l'ultimo set chiuso (RacketRules.secondary non accoda gamesInSet quando wonBy != null), quindi la riga A mostrerebbe un set chiuso come se fosse in corso.

P2:
- La premessa del blocco di 1s per lato ("in nessuno dei tre sport due punti stanno in un secondo") è falsa nell'uso reale: chi riallinea il punteggio tocca più volte in fretta, e i tocchi scartati sono silenziosi.
- La pill del cronometro da 56dp è quasi certamente troppo stretta per il glifo ▶ più "34:12" a 20sp in una capsula da 46dp.
- Lo swipe-to-dismiss attivo di default con il tema Material3 non-Wear è dichiarato da verificare, correttamente.

P3:
- Viola il proprio limite di 18 caratteri: "MARCATORE NON INVIATO" ne ha 21 e "CHIUSURA NON CONFERMATA" 23.
- Dice che il marcatore si apre "al tocco": è vero solo col protocollo v2 e con attributesScorer e rosa non vuota, che però è il caso normale.

Affermazioni sul codice che ho verificato vere:
- KEY_SERVING_SIDE è in MainViewModel.kt:862 e WearScoreState non la legge;
- toggleKeeperTimer azzera il timer se corre;
- keeperTimer.text è sempre "K";
- il colore della squadra è grezzo via setTextColor;
- applyMatchOver con isClickable=false e alpha 0.4;
- il toast di SPORT;
- l'AlertDialog di AZZERA;
- la riga 476 è la condizione di L7;
- l'ack passa per LocalBroadcast all'Activity;
- l'anello ha innerRadiusRatio 2.3 e spessore 6dp;
- androidx.wear 1.3.0 e WAKE_LOCK già dichiarati;
- il selettore del telefono è ColorPickerView con BrightnessSlideBar, quindi il nero si può scegliere;
- NESSUNO è in fondo alla lista del marcatore.

Nessun file inesistente è citato come esistente. I file indicati come nuovi sono dichiarati tali.

## Decisioni prese - 24 settembre 2026

Il proprietario ha lasciato la scelta ("hai liberta' di scelta, procedi"). Queste sono le
risposte alle domande delle due piste; ognuna si puo' rivedere, e nessuna e' irreversibile.

**Telefono**
1. Il colore della squadra esce da dietro il numero: **si'**. Al sole il caso peggiore passa da
   1,90:1 a 6,0:1, e l'identita' street resta nelle schermate di contorno, come da Fase D.
2. Rose, registro, formazioni e fine partita nel foglio PARTITA: **si'**.
3. ANNULLA con un tocco, senza dialogo, nel padel e nel tennis: **si', ma solo dopo L3** e la
   guardia di L7. Il calcio tiene il dialogo.
4. Schermata di gioco bloccata in verticale: **si'**. Nessun caso d'uso a bordo campo chiede
   l'orizzontale, e toglie una variante da mantenere. Si puo' riaprire.
5. Ordine: **passi 1-4 subito**, la schermata di gioco nuova (passi 5-9) dopo L1-L3.
6. Colori predefiniti delle squadre: **giallo #FFD600 e verde #76FF03** di ColorRepository.

**Orologio**
1. Cifre bianche e colore della squadra come striscia: **si'**.
2. Ambient **si'**, schermo sempre acceso **no**. Consumo da misurare su un orologio vero.
3. Niente chiusura con lo swipe durante la partita, si esce col tasto: **si'**.
4. Marcatore non automatico, finestra CHI? di 8 secondi: **si'**.
5. FINE PARTITA dal polso bloccata nel calcio finche' L4 non e' corretto: **si'**. Oggi quel
   percorso perde la partita; un comando assente e' meglio di uno che distrugge.

**Collocazione della regola del colore:** una sola implementazione, `TeamInk` in `:core`, usata
da entrambi i moduli (`:wear` dipende gia' da `:core` per il calcolo offline). La sintesi
dell'orologio proponeva `ReadableColor` in `:shared`: diventa una seconda funzione dello stesso
oggetto (`graphicOnBlack`), non un secondo file.

## Coerenza fra telefono e orologio - 24 settembre 2026

Le due piste sono state progettate in parallelo da agenti diversi. Un ultimo agente le ha confrontate su questo file. **Tutte le soluzioni qui sotto sono adottate** e prevalgono sulle righe delle due piste che contraddicono.

### Conflitti e soluzioni

- **La stessa funzione è pianificata due volte in due moduli. Telefono > Piano passo 2 crea core/.../core/TeamInk.kt con TeamInkTest (Telefono righe 28, 148-154). Orologio > Piano passo 2 crea shared/.../shared/utils/ReadableColor.kt con textOn e graphicOnBlack, e aggiunge che 'la riusa il telefono quando l'altra pista correggerà L11' (Orologio righe 574, 687-693). Ognuna delle due sintesi crede di essere la fonte per l'altra: la sezione 'Contratto con la pista Orologio' (Telefono riga 417) dice che l'orologio userà TeamInk in :core.**
  - *Soluzione adottata:* Un solo file in :core, perché core/build.gradle usa il plugin kotlin.jvm: il compilatore impedisce import android.* e i test girano su JVM puro. :core è già dipendenza di :wear (wear/build.gradle riga 89) e di :mobile (riga 110). L'oggetto espone luminance, contrast, ink/textOn (l'attuale TeamInk.on) e graphicOnBlack. Si cancella il passo 2 dell'Orologio e il suo ReadableColor in :shared, e si fondono i due test (scansione a passo 3, casi fissi di tutte e due le sintesi).
- **La soglia dell'inchiostro non è la stessa. Il telefono restituisce nero se L >= 0,1791 (Telefono riga 28). L'orologio restituisce nero se L > 0,179 (Orologio riga 572). Il valore esatto è sqrt(1,05x0,05)-0,05 = 0,179129. Per i colori con 0,179 < L < 0,1791 il telefono dà bianco e l'orologio nero. Il contrasto cambia di poco (circa 4,58 in tutti e due i casi), ma lo stesso colore di squadra, arrivato dal telefono col protocollo Wear (WearConstants.KEY_TEAM_COLOR, WearDataLayerService righe 107-119), avrebbe inchiostri diversi sui due lati. Per questo i casi peggiori citati non coincidono: #5D60FF per il telefono, #8454F6 per l'orologio (il giudice lo nota alla riga 1059).**
  - *Soluzione adottata:* Una sola costante calcolata, val SOGLIA = sqrt(1.05*0.05) - 0.05, con un solo confronto (>=), nella funzione unica in :core. Nel test va un caso fisso con L dentro la banda (0,179; 0,17913) che fissi l'esito, così nessuno può reintrodurre un arrotondamento diverso.
- **Una barretta sottile di un colore scuro sul nero riceve due rimedi diversi. Sul telefono la barretta 4x60dp sotto il nome tiene il colore vero con un contorno di 1dp #9E9E9E (Telefono righe 36 e 321). Sull'orologio la striscia 28x4dp viene schiarita (Orologio riga 822). Così #1A237E appare #1A237E sul telefono e #4C539A al polso, e lo stesso elemento cambia colore passando da uno schermo all'altro. Un contorno da 1dp intorno a 4dp di colore, inoltre, si legge più come grigio che come colore della squadra.**
  - *Soluzione adottata:* Il predicato è già lo stesso: contrasto(c, #000000) < 3. Per le barrette e le strisce sottili si usa graphicOnBlack su tutti e due i lati: la barretta sotto il nome e il team_indicator del registro sul telefono, la striscia e la barra del marcatore sull'orologio. Per il registro su #1E1E1E si passa lo sfondo come parametro. Il contorno #E0E0E0 da 2dp resta solo sulle zone + del telefono: lì il riempimento deve restare il colore vero, perché è quello su cui si calcola l'inchiostro del glifo.
- **Il testo rosso su #1E1E1E ha due regole opposte. Il telefono scrive error_red 'Mai testo su #1E1E1E (4,33)' e introduce error_text #FF6E6E (Telefono righe 56-57, 124). L'orologio usa proprio error_red come testo di FINE PARTITA su #1E1E1E a 4,33 e lo giustifica con i 20sp bold, cioè testo grande (Orologio righe 605, 658 e Rischi punto 8). Inoltre il telefono dice che error_red è 'solo grafica e riempimenti', mentre l'orologio lo usa come testo su nero ('NON CONFERMATO', 'K 0:00').**
  - *Soluzione adottata:* Una regola sola per il token. error_red come testo solo su #000000 (5,46, AA per qualsiasi corpo) e come riempimento con testo #000000. Su #1E1E1E e #2C2C2C il testo distruttivo usa error_text #FF6E6E (6,12 su #1E1E1E). L'orologio adotta error_text per FINE PARTITA nella card del menu. La voce resta a 20sp per la leggibilità, non più per il contrasto. Il telefono corregge la riga 56 in 'mai testo su fondi grigi'.
- **Il tocco inerte a partita finita ha comportamenti opposti. Il contratto del telefono chiede al polso di ripetere il 'tocco inerte dichiarato (vibrazione a tre tick e scritta FINITA) invece del silenzio' (Telefono righe 329 e 417). L'orologio decide invece che 'I lati non vibrano e non mandano niente' (Orologio riga 903). In più l'orologio assegna quasi lo stesso schema a tre tick all'annullamento/correzione, [0,30,50,30,50,30] (riga 916), mentre sul telefono [0,30,60,30,60,30] vuol dire partita finita e l'annullamento del passo 12 è un doppio tick (riga 228). Il vocabolario aptico non è condiviso.**
  - *Soluzione adottata:* Si corregge il contratto alla riga 417 a favore dell'orologio: al polso ogni vibrazione dopo un tocco si legge come 'preso', quindi il silenzio più la parola PARTITA FINITA in riga E è la scelta giusta. Si allinea poi il significato dei tre tick: 'annullamento' su tutti e due i lati (lo schema dell'orologio). Il tocco inerte del telefono passa al colpo unico lungo [0,400], che al polso vuol dire già 'non preso'. Le costanti dei pattern condivisi vanno in :core o :shared, accanto alla funzione del colore.
- **Il rosa in gioco. Il telefono: graffiti_pink 'Mai nella schermata di gioco', e il portiere che corre si mostra in #E0E0E0 (Telefono righe 54 e 305). L'orologio: il rosa è 'Unico accento di marca in gioco', con 'K 4:12' e l'anello rosa mentre corre (Orologio righe 586 e 853). Lo stesso stato, portiere in corso, è bianco sul telefono e rosa al polso, e il divieto del telefono è smentito dall'altra metà della stessa identità.**
  - *Soluzione adottata:* Una regola condivisa: 'rosa in gioco solo per il portiere che corre'. Il telefono mostra il conto del portiere in #F50057 su nero (5,02, AA) nello slot della barra. Nel resto della schermata di gioco il rosa resta vietato. Si corregge la riga 54 del telefono.
- **Il cronometro fermo ha due segni diversi. L'orologio lo mostra #FFFFFF se corre e #9E9E9E se è fermo (Orologio riga 599). Il telefono lo tiene sempre #FFFFFF e affida lo stato al solo glifo play/pausa (Telefono riga 305). Il numero stesso ha quindi significati diversi sui due schermi.**
  - *Soluzione adottata:* Il telefono adotta anche il grigio #9E9E9E da fermo (7,84 sul nero) e tiene il glifo. Costa un setTextColor nell'observer di isMatchTimerRunning.
- **A partita finita il perdente cambia colore solo sul telefono. Lì il numero dello sconfitto passa a #9E9E9E (Telefono righe 281 e 329). L'orologio tiene le due cifre a piena intensità (riga 903). L'argomento dell'orologio però riguarda l'alpha 0,4 di oggi (3,02:1), non un grigio a 7,84:1.**
  - *Soluzione adottata:* L'orologio adotta la regola del telefono: sconfitto #9E9E9E, vincitore #FFFFFF, tutti e due bianchi in caso di pari nel calcio. Il contrasto resta AAA e anche al polso si legge chi ha vinto.
- **I nomi dei token si sdoppiano: game_bg, ink_black e face_black per #000000; ink_white e score_white per #FFFFFF (Telefono righe 45-47; Orologio righe 582-583). Con il nero e il bianco puri al centro di tutte e due le sintesi, due nomi per lo stesso valore sono il primo passo per farli divergere.**
  - *Soluzione adottata:* Due nomi soli, condivisi: ink_black #000000 (fondo di gioco e inchiostro scuro, alias dell'attuale asphalt_black) e ink_white #FFFFFF. game_bg e face_black, se servono, sono alias di ruolo nel modulo che li usa (@color/ink_black), non valori nuovi.
- **TalkBack legge il punteggio in due forme. Il contratto del telefono chiede 'ROSSI, 30' (Telefono riga 417). L'orologio progetta 'Squadra 1, 30. Tocca per segnare…' (Orologio riga 928), eppure i nomi arrivano già al polso (WearViewModel righe 176-177 e 296, KEY_TEAM1_NAME).**
  - *Soluzione adottata:* L'orologio usa team1Name e team2Name nella contentDescription, con 'Squadra 1' solo come ripiego quando il nome non è ancora arrivato. Stessa stringa da risorsa sui due lati.

### Divergenze volute

- Misure del numero: 150sp sul telefono (Telefono > Tipografia riga 65) contro 58sp, e 68sp in sw210dp, sull'orologio (Orologio > Tipografia riga 598). Il principio è lo stesso: dimensione fissa per tutta la partita, niente autoSize, misurata sul token più largo ('AV', '88'). È un'identità sola, con misure diverse.
- Dove va il colore della squadra: sul telefono riempie le zone + (182x112dp) e una barretta sotto il nome (Telefono righe 35 e 289). Sull'orologio diventa una striscia 28x4dp sotto le cifre (Orologio righe 592 e 822). Il polso non ha spazio per un bersaglio colorato né per i nomi in fascia B. La regola di fondo è la stessa sui due lati: cifre bianche su nero e colore mai usato come testo.
- Sull'orologio nessun testo sta sopra un colore di squadra, quindi textOn non serve (Orologio riga 572). Il telefono invece mette testo sopra il colore in molti punti: glifo +, etichette, avatar, PDF (Telefono, corollario 4, riga 38). L'asimmetria è giusta: al polso l'inchiostro TeamInk non ha niente da colorare.
- Rimedio per un colore scuro sul nero: sul telefono la zona + è grande, porta un glifo e tiene il colore vero con un contorno #E0E0E0 di 2dp (Telefono riga 36). Sull'orologio la striscia è alta 4dp, un contorno non si vedrebbe, quindi la si schiarisce (Orologio righe 560-569). Per le zone è una divergenza giusta. Per le barrette sottili vedi i conflitti.
- Ambra per la coda (signal_amber) e grigio per l'ambient (ambient_gray) solo sull'orologio (Orologio righe 587 e 589): descrivono stati che esistono solo al polso (punti in coda, schermi low-bit).
- Niente forme street sul quadrante (Orologio > Forme riga 628: 'Nessuna card, ombra o angolo tagliato'). Sul telefono invece le zone + in gioco usano StreetButton, taglio 8dp (Telefono > Forme riga 86). Tutti e due tengono lo street nel contorno (menu e selezione sport: StreetCard 12dp su concrete_gray, come le card del foglio), quindi la Fase D è applicata allo stesso modo.
- Scadenza del portiere: sul telefono lo slot si riempie di #FF1744 con 'CAMBIO' in #000000 (Telefono riga 305). Sull'orologio c'è 'K 0:00' scritto in #FF1744 su nero (Orologio riga 854). Il colore e il significato sono gli stessi, la forma cambia perché la fascia A è larga 56dp.
- Fondo di gioco nero su tutti e due, fondo di contorno #121212 su tutti e due. Sull'orologio anche la selezione del marcatore è su nero, perché 'è in gioco' (Orologio riga 955). È coerente con il telefono, dove il nero sta sotto tutto ciò che si usa durante la partita.
- Pallino del servizio bianco su tutti e due: slot da 12dp accanto al nome sul telefono (riga 313), 8dp sul lato esterno delle cifre sull'orologio (riga 863). Stesso segno e stesso colore, misure proporzionate allo schermo.
- La vibrazione per lato (1 impulso a sinistra, 2 a destra, Orologio riga 911-915) esiste solo al polso. È giusto: il telefono si guarda, l'orologio spesso no.

### Token condivisi

| Token | Valore | Stato |
|---|---|---|
| nero puro #000000: telefono game_bg / ink_black (= asphalt_black, già in mobile/src/main/res/values/colors.xml) contro orologio face_black (nuovo) (DESIGN.md, Telefono > Colori righe 45 e 47; Orologio > Colori riga 582) | `#000000` | diverge: stesso valore, tre nomi diversi. asphalt_black manca in wear/src/main/res/values/colors.xml |
| bianco puro #FFFFFF: telefono ink_white contro orologio score_white, entrambi nuovi (Telefono > Colori riga 46; Orologio > Colori riga 583) | `#FFFFFF` | diverge: stesso valore e stesso ruolo (cifre, cronometro, pallino del servizio), nome diverso |
| asphalt_dark (Telefono > Colori riga 48; Orologio > Colori riga 590; presente in tutti e due i colors.xml) | `#121212` | coincide |
| concrete_gray (Telefono riga 49; Orologio riga 590; tutti e due i colors.xml) | `#1E1E1E` | coincide |
| stencil_white (Telefono riga 51; Orologio riga 584; tutti e due i colors.xml). Stessa regola sui due lati: mai sopra un colore di squadra | `#E0E0E0` | coincide |
| sidewalk_gray (Telefono riga 52; Orologio riga 585; tutti e due i colors.xml) | `#9E9E9E` | coincide (ma lo stato 'cronometro fermo' ha due segni diversi, vedi conflitti) |
| graffiti_pink (Telefono riga 54; Orologio riga 586; tutti e due i colors.xml) | `#F50057` | coincide nel nome e nel valore; diverge nella regola d'uso: il telefono dice 'mai nella schermata di gioco', l'orologio lo usa in gioco per K e CHI? (vedi conflitti) |
| neon_cyan (Telefono riga 55; Orologio riga 591; tutti e due i colors.xml). Regola d'uso coerente: solo contorno o menu, mai in gioco | `#00E5FF` | coincide |
| error_red (Telefono riga 56; Orologio riga 588; tutti e due i colors.xml) | `#FF1744` | coincide nel nome e nel valore; regola d'uso in contraddizione come testo su #1E1E1E (vedi conflitti) |
| team_spray_yellow / team_electric_green, colori iniziali delle squadre (Telefono riga 58; Orologio: solo come valori nella riga 'striscia squadra' 592 e nella schermata 'cifre bianche e strisce', riga 822; tutti e due i colors.xml) | `#FFD600 / #76FF03` | coincide nel valore; la sintesi Orologio non li nomina come token. Oggi sono duplicati: il telefono li legge da ColorRepository, l'orologio dai suoi colors.xml |
| error_text, testo distruttivo su fondi grigi (Telefono riga 57, nuovo) | `#FF6E6E` | manca in una delle due: serve anche all'orologio per FINE PARTITA su #1E1E1E (Orologio > Tipografia riga 605, Contrasti riga 658) |
| graffiti_dark_gray (Telefono riga 50; solo in mobile/src/main/res/values/colors.xml) | `#2C2C2C` | manca in una delle due (va bene: l'orologio non ha dialoghi né zone spente) |
| outline_gray (Telefono riga 53, nuovo) | `#6E6E6E` | manca in una delle due (va bene: sull'orologio non ci sono comandi contornati) |
| signal_amber (Orologio riga 587, nuovo) | `#FFB300` | manca in una delle due (va bene: la coda di consegna esiste solo al polso) |
| ambient_gray (Orologio riga 589, nuovo) | `#BDBDBD` | manca in una delle due (va bene: l'ambient esiste solo sull'orologio) |
| soglia 3:1 del colore di squadra come grafica sul nero: telefono corollario (2), contrasto(c,#000) < 3 aggiunge un contorno (Telefono > Il colore della squadra riga 36); orologio Regola 1, graphicOnBlack(c, 3.0) (Orologio > Il colore della squadra righe 560-562) | `soglia 3.0 contro #000000` | coincide nel test (stessa condizione), diverge nel rimedio: contorno #E0E0E0 o #9E9E9E sul telefono, colore schiarito verso il bianco sull'orologio |
| soglia dell'inchiostro nero o bianco sopra un colore: TeamInk.on in :core (Telefono riga 28) contro ReadableColor.textOn in :shared (Orologio riga 572) | `L >= 0.1791 contro L > 0.179` | diverge: due funzioni in due moduli, con costante e confronto diversi |

### Dove vivono i token

Stato oggi (letto da mobile/src/main/res/values/colors.xml e wear/src/main/res/values/colors.xml). Nove colori sono duplicati identici nei due file: asphalt_dark, concrete_gray, stencil_white, sidewalk_gray, graffiti_pink, neon_cyan, team_spray_yellow, team_electric_green, error_red. Solo il telefono ha asphalt_black e graffiti_dark_gray. Solo l'orologio ha gli alias background e surface, che un grep su wear/src/main non trova usati da nessuna parte: sono codice morto, lo segnalo e non lo tocco. Non ci sono ancora divergenze di valore, ma niente le impedisce: le due sintesi aggiungono token nuovi per lo stesso valore (ink_white contro score_white).

Proposta: una collocazione per i valori e una per la logica.

1) Colori (risorse) in :shared, nel nuovo file shared/src/main/res/values/colors.xml. :shared è già un modulo android.library (shared/build.gradle, namespace it.vantaggi.scoreboardessential.shared) ed è già dipendenza di tutti e due (mobile/build.gradle riga 111, wear/build.gradle riga 83). Oggi non ha una cartella res, ma il merge delle risorse lo gestisce senza configurazione in più. Un modulo di risorse nuovo, per esempio :designsystem, porterebbe solo un modulo in più senza un vantaggio concreto. Entrano in :shared i token dell'identità unica: ink_black #000000 (con asphalt_black come alias finché i layout lo citano), ink_white #FFFFFF, asphalt_dark, concrete_gray, stencil_white, sidewalk_gray, graffiti_pink, neon_cyan, error_red, error_text #FF6E6E, team_spray_yellow, team_electric_green. Restano nel modulo i token di una sola piattaforma. In mobile/.../colors.xml: graffiti_dark_gray, outline_gray, ed eventualmente l'alias di ruolo game_bg. In wear/.../colors.xml: signal_amber, ambient_gray, ed eventualmente l'alias face_black. Nello stesso passo i duplicati vanno TOLTI dai due colors.xml di modulo, perché una risorsa dell'app con lo stesso nome vince in silenzio su quella della libreria e ricreerebbe la divergenza senza errori. Attenzione: gradle.properties ha android.nonTransitiveRClass=true. In XML @color/x continua a funzionare, ma nel Kotlin i riferimenti R.color.x a token spostati vanno qualificati con it.vantaggi.scoreboardessential.shared.R. Oggi i punti sono ColorRepository righe 10-12, StatisticsAdapter, RoleUtils, MainActivity del telefono riga 373, wear MainActivity riga 329 e i tre R.color.error_red, graffiti_pink, sidewalk_gray e stencil_white dell'orologio. Verifica: grep di ogni nome spostato nei due res/values dei moduli (zero risultati), poi :mobile:lint e :wear:lint senza voci nuove.

2) Regola di leggibilità (codice) in :core, un solo file: core/src/main/kotlin/it/vantaggi/scoreboardessential/core/TeamInk.kt (o ReadableColor.kt, un nome solo), con luminance, contrast, l'inchiostro nero o bianco con la costante esatta sqrt(1,05x0,05)-0,05 e graphicOnBlack. Il motivo è che core/build.gradle usa kotlin.jvm: la purezza è garantita dal compilatore e non solo dalla buona volontà, come invece sarebbe in :shared, che è una libreria Android. Il ReadableColor in :shared del passo 2 dell'Orologio (DESIGN.md righe 574 e 687-693) si cancella.

3) I colori delle squadre viaggiano già come Int ARGB (WearConstants.KEY_TEAM_COLOR, letto in WearDataLayerService righe 107-119). Con la funzione in :core e i default in :shared, i due lati calcolano inchiostro e striscia dallo stesso Int con la stessa funzione e gli stessi default. Oggi il telefono li prende da ColorRepository e l'orologio dal proprio colors.xml. Se il proprietario cambia la coppia predefinita (Telefono > Decisioni, riga 448), la modifica resta una sola.

## Adattamento alla UI Constitution - 6 ottobre 2026

Il proprietario ha un design system trasversale, la **UI Constitution** (<https://claude.ai/artifact/8DhrtZW85wiHTsfw1vmXSn>), che il progetto adotta. Questa sezione e' l'**unica fonte** dell'adattamento: identita', mappatura dei ruoli, conflitti dichiarati e passi G-1b..G-10. Segue la procedura di `adapting.md` della Constitution: i valori del progetto vincono, si tengono i ruoli; i buchi si riempiono coi predefiniti e si dice quali sono.

La pista "Coerenza con Padel Elite" (sotto) resta per cio' che e' gia' fatto (G-0 e G-1) e per i **valori** di Padel Elite e i loro contrasti. I suoi passi G-2..G-9 sono stati **tolti** da li' e rifatti qui: li sostituiscono. Dove la pista dice JetBrains Mono, Heroicons o "pillole" va letto con questa sezione.

Sigle dell'origine dei valori: **P** valore di Padel Elite (preso dalla dashboard); **C** predefinito della Constitution; **A** scelta dell'app (derivata o corretta, con la ragione).

### Decisioni del proprietario, 6 ottobre 2026

1. **Numeri in Inter con cifre tabulari** (feature `tnum`), non JetBrains Mono, che **esce dall'app** (file, famiglia e licenza). Cambia la decisione G3 del 5 ottobre.
2. **Chi serve si segna con l'accento lime `#C8F135`**: i pallini del servizio sul telefono e sull'orologio. In ambient nessun colore.
3. **Il tema ad alto contrasto si fa piu' avanti**, come passo G-10.

Restano valide: valori di Padel Elite mappati sui ruoli (G0 e la tabella sotto); **Inter come carattere del testo**, eccezione dichiarata rispetto al "font di sistema" della Constitution e approvata dal proprietario per coerenza con la dashboard (la Constitution dice "non aggiungere un font senza approvazione": c'e'); quadrante dell'orologio con le cifre condensate se Inter non entra (misura qui sotto); solo tema scuro (G4); via l'identita' street dal contorno (G1); lati lime e ciano (G2); nome e icona G5: applicati in G-9 come **proposta da far confermare al proprietario** (nome invariato, icona nuova sui token).

### Identita' (le sei righe, una per superficie)

Telefono e orologio condividono accento, caratteri e nomi; non la densita' ne' il movimento (`adapting.md` punto 5).

```text
TELEFONO
Product character: tabellone da bordo campo, letto di sfuggita e toccato col pollice; il contorno (storico, Cronaca, statistiche) e' la pagina di una dashboard sobria, la stessa di Padel Elite
Density: balanced nel contorno; in gioco un numero per lato e niente altro (le tre fasce a slot fissi)
Geometry: precise (raggi 8, 14, 14; gruppi tonali; mai angoli tagliati, mai pillole)
Accent behavior: restrained (lime solo per l'azione primaria, la selezione, il fuoco e chi serve)
Motion character: precise (due stati alla volta, nessuna decorazione, niente oltre 240ms)
Primary domain pattern: tabellone a due meta' con punteggio tabulare, registro cronologico e Cronaca derivata dal motore
Distinctive visual behavior: nero puro in gioco, cifre bianche giganti in Inter 600 tabulare, il colore della squadra solo nelle zone + e nella barretta (con inchiostro TeamInk), pallino lime per il servizio, il punteggio che rotola nella direzione del cambio

OROLOGIO
Product character: un numero al polso, letto in mezzo secondo e toccato con un dito; il resto e' sul telefono
Density: compact (un valore focale, al massimo due di supporto, un'azione)
Geometry: precise (tondo prima, quadrato dopo; nessuna card, nessuna ombra; raggi di Padel Elite nel menu)
Accent behavior: functional (il lime marca una cosa sola: chi serve; in ambient nessun colore)
Motion character: calm (corto, a molla 'control', niente in loop, niente al risveglio)
Primary domain pattern: quadrante a fasce con la coppia di cifre al centro e il gesto del tocco, con ricevuta aptica per lato
Distinctive visual behavior: cifre bianche su nero puro, strisce di squadra sotto le cifre, pallino lime sul lato di chi serve, vibrazione diversa a sinistra e a destra, ambient con le sole cifre sottili
```

### Mappatura dei ruoli della Constitution

Il tema e' uno solo, **scuro** (decisione G4): i valori sono quelli del tema scuro della Constitution; il chiaro non c'e' (conflitto dichiarato, sotto) e l'alto contrasto e' G-10. La fonte dei valori e' `mobile/src/main/res/values/colors.xml` (token `elite_*`), il quadrante la riceve in G-7. I nomi marcati *(nuovo)* non esistono ancora: li crea G-2.

| Ruolo | Token Android | Valore | Origine |
|---|---|---|---|
| `background-canvas` | `elite_background` (`android:colorBackground`) | `#0D0D0F` | P |
| `background-surface` | `elite_surface` (`colorSurface`) | `#161618` | P |
| `background-elevated` | `elite_surface_raised` (`colorSurfaceContainerHigh`) | `#1E1E22` | P |
| `background-watch` | `ink_black`; nero del gioco sul telefono e del quadrante | `#000000` | C (uguale al valore dell'app) |
| `text-primary` | `elite_text_primary` (`colorOnSurface`); le cifre del gioco e del quadrante sono `ink_white` | `#D1D1D8` (cifre `#FFFFFF`) | P (cifre A: dati su nero) |
| `text-secondary` | `elite_text_secondary` (`colorOnSurfaceVariant`) | `#8A8A9A` | P |
| `text-disabled` | `elite_text_disabled` *(nuovo)* | `#6B7077` | C |
| (nessun ruolo) | `elite_text_tertiary` | `#7F7F93` | P; la Constitution non ha un terzo livello leggibile: si usa `text-secondary`; il token si toglie in G-4 quando nessun layout lo cita |
| `border-subtle` | `elite_border_strong` (`colorOutlineVariant`) | `#2A2A2E` | P (nel ruolo di linea sottile fra le righe di un gruppo) |
| `border-group` | `elite_border` | `#1E1E22` | P; **visibile anche in scuro** (la Constitution lo vuole trasparente: qui i due toni distano 1,07:1) |
| `border-strong` | `elite_outline` (`colorOutline`) | `#6E6E7E` | A (il bordo forte di Padel Elite fa 1,36:1 e non passa 3:1, vedi G-0) |
| `accent-default` | `elite_lime` (`colorPrimary`) | `#C8F135` | P |
| `accent-hover`, `accent-pressed` | derivati dal lime col feedback di pressione (opacita' 0,85): sul fondo `#ACCF2F` | derivati | A (P non ha stati; il blu della Constitution non si usa) |
| `on-accent` | `elite_on_lime` (`colorOnPrimary`) | `#0D0D0F` | P |
| `status-success` | `elite_success` (alias del lime) | `#C8F135` | P |
| `status-warning` | `elite_warning` | `#E09A35` | P |
| `status-error` | `elite_error` (`colorError`) | `#E05252` | P |
| `status-info` | `elite_info` *(nuovo)* | `#7FBDEA` | C (P usa il ciano solo nei grafici) |
| `status-success-subtle` | `elite_success_subtle` *(nuovo)* | `#15291F` | C |
| `status-warning-subtle` | `elite_warning_subtle` *(nuovo)* | `#2E2410` | C |
| `status-error-subtle` | `elite_error_subtle` *(nuovo)* | `#2A1415` | A (il `#33191A` di C con l'errore di P fa 4,25:1 e l'etichetta non passa 4,5) |
| `status-info-subtle` | `elite_info_subtle` *(nuovo)* | `#14262F` | C |
| `focus-ring` | `elite_lime`, tratto pieno 2dp con distanza 2dp | `#C8F135` | P (campi con focus lime; il blu di C si scarta) |
| `state-hover` | `elite_surface_hover` (`colorSurfaceBright`), solo puntatore e tastiera | `#2A2A2E` | P |
| `state-pressed` | `elite_state_pressed` *(nuovo)*: il testo primario al 12% | `#1FD1D1D8` | A (C dark e' `rgba(236,238,240,0.12)`: stessa regola sul testo di P) |
| `state-selected` | `elite_surface_raised` piu' un secondo segno (indicatore lime, peso 600) | `#1E1E22` | P/A |
| `scrim` | `elite_overlay` | nero all'85% (`#D9000000`) | P (C dark e' 60%) |
| `chart-1` | `elite_lime` (lato 1) | `#C8F135` | P |
| `chart-2` | `elite_cyan` (lato 2) | `#00E5FF` | P |
| `chart-3`, `chart-4`, `chart-5` | `elite_chart_3`, `_4`, `_5` *(nuovi)* | `#F0A36B`, `#D79BDB`, `#A4A9B0` | C (dark, in ordine; saltati il blu e il verde acqua di C: troppo vicini al ciano) |
| `chart-grid` | = `border-subtle` | `#2A2A2E` | C |
| `radius-control` | 8dp (bottoni, campi, voci) | 8dp | P (C: 10) |
| `radius-surface` | 14dp (gruppi, card, foglio) | 14dp | P (C: 16) |
| `radius-overlay` | 14dp (dialoghi, menu) | 14dp | P (C: 22): P non distingue dalle card |
| `radius-full` | solo interruttori e indicatori | tondo | C |
| (nessun ruolo) | raggio dei badge | 6dp | P: badge solo bordo, senza sfondo |
| `space-4`..`space-64` | `space_4`..`space_64` *(nuovi, `dimens.xml`)* | 4, 8, 12, 16, 24, 32, 48, 64dp | C |
| `control-touch` | altezza e larghezza minime dei comandi | 48dp | C (P: 44, app: 48) |
| `control-standard` | altezza dei bottoni sul telefono | 48dp | A (C: 44: su Android il minimo e' 48) |
| `icon-compact`, `-standard`, `-prominent` | 16, 20, 24dp | 16, 20, 24dp | C |
| icone | Material Symbols **outlined**, peso 400, scaricate una per una come vettoriali (non il font); niente `icon-stroke` (vale per Lucide) | | C (`integration.md`); sostituisce Heroicons |
| `duration-instant`, `-fast`, `-standard` | `duration_instant`, `duration_fast`, `duration_standard` *(nuovi, `integers.xml`)* | 100, 160, 240ms | C = tetto di P (240ms) |
| `duration-expressive` | non si usa | 380ms | C; fuori dal tetto di P |
| molle | `control` stiffness 743 e smorzamento 1,0; `surface` 400 e 0,83 (`SpringAnimation`, non `tween`) | | C (`integration.md`); `expressive` mai sull'orologio |

**Tipografia.** Inter, tre pesi (400, 500, 600). Ruoli sulla scala M3 (come `integration.md`): `display` displaySmall, `page-title` headlineLarge, `section-heading` titleLarge, `body` bodyLarge, `label` labelLarge, `caption` bodySmall, `numeric` lo stile `Punteggio`. Pesi: `display`, `page-title`, `section-heading` e `body-strong` 600; `body` e `caption` 400; `label` 500; `numeric` 500 (600 per i punteggi grandi). Non servono altri pesi: **al massimo tre per schermata**, e il 700 e il 900 sono usciti dall'app. I corpi sono quelli della scala M3 in sp (36, 32, 22, 16, 14, 12), non i px della Constitution (scostamento dichiarato: sp, per il carattere al 200%); `display` non si usa negli strumenti.

### Contrasti (WCAG, ricalcolati il 6 ottobre; tema scuro, l'unico)

I valori di P sono quelli gia' verificati da `TokenEliteTest`; qui si aggiungono i ruoli nuovi.

| Coppia | Rapporto | Esito |
|---|---|---|
| `text-primary` su canvas / surface / elevated / hover | 12,78 / 11,90 / 10,94 / 9,41 | AAA |
| `text-secondary` su canvas / surface / elevated / hover | 5,72 / 5,32 / 4,89 / 4,21 | AA; **mai su hover** |
| `text-disabled` `#6B7077` su canvas / surface / elevated | 3,89 / 3,62 / 3,33 | sotto 4,5 per scelta della Constitution (solo etichette disattivate) |
| `accent-default` lime su canvas / surface / elevated / nero | 14,88 / 13,85 / 12,73 / 16,09 | AAA, anche come testo, icona e pallino |
| `on-accent` `#0D0D0F` su lime | 14,88 | AAA |
| `on-accent` sul lime premuto `#ACCF2F` | 10,84 | AAA |
| `status-error` su canvas / surface / elevated | 5,08 / 4,73 / 4,35 | AA su canvas e surface; su elevated vale come icona (3:1) e testo grande |
| `status-warning` su canvas / surface / elevated | 8,18 / 7,61 / 7,00 | AAA |
| `status-info` `#7FBDEA` su canvas / surface / elevated | 9,59 / 8,92 / 8,20 | AAA |
| `*-subtle`: `text-primary` su success / warning / error / info | 10,10 / 10,05 / 11,43 / 10,25 | AAA |
| `*-subtle`: colore di stato su success / warning / error / info (etichetta) | 11,75 / 6,43 / 4,55 / 7,69 | AA, tutti sopra 4,5 |
| `border-strong` `#6E6E7E` su canvas / surface / elevated | 3,88 / 3,61 / 3,32 | 3:1, passa |
| `border-group` `#1E1E22` su canvas / surface | 1,17 / 1,09 | decorativo: separa, non identifica un comando |
| `border-subtle` `#2A2A2E` su canvas / surface | 1,36 / 1,26 | decorativo |
| surface su canvas | 1,07 | il gruppo si legge per tono **e** per `border-group` (conflitto dichiarato) |
| `chart-1`..`chart-5` su canvas / surface | 14,88-8,21 / 13,85-7,64 | 3:1 con ampio margine |
| cifre `#FFFFFF` su nero / lime su nero (pallino) | 21,00 / 16,09 | AAA |
| grigio `#9E9E9E` (cronometro fermo, perdente) su nero | 7,84 | AAA |
| carta del PDF (G-8): `print_text_primary` `#1A1C1F` su `print_background` / `print_surface` | 17,08 / 15,65 | AAA |
| carta del PDF: `print_text_secondary` `#5A5F66` su `print_background` / `print_surface` | 6,43 / 5,90 | AA |
| carta del PDF: lime `#C8F135` e ciano `#00E5FF` su `print_surface` (come testo o barretta) | 1,20 / 1,41 | non passano: sulla carta sono barrette scurite a 3:1 (`#7E9821` 3,01, `#009CAD` 3,03) |
| carta del PDF: `print_surface` su `print_background` / `print_border` su `print_surface` | 1,09 / 1,26 | decorativo: il gruppo si legge per tono e bordo (come in scuro) |

### Conflitti dichiarati

La Constitution ammette di cambiare ogni valore, ma non di lasciar cadere un ruolo, saltare uno stato o usare un pattern vietato senza dichiararlo. Qui sono tutti:

1. **Inter al posto del font di sistema.** La Constitution dice `--font-sans` di sistema e "non aggiungere un font senza approvazione". Inter e' approvato dal proprietario per coerenza con la dashboard. Costa tre file da circa 96 KB (288 KB in tutto) e li porta l'APK; non si usa un font scaricabile (niente Play Services in partita).
2. **Niente tema chiaro** (G4). La Constitution chiede chiaro, scuro e alto contrasto. Il chiaro e' fuori; l'alto contrasto e' G-10. **Superato il 28 ottobre 2026** da "Tema chiaro" (in fondo): l'app ha scuro, chiaro e come il telefono, scelti nelle Impostazioni.
3. **Cifre giganti fuori dalla scala dei ruoli.** Il punteggio del gioco e' il valore focale e si misura per entrare nella meta' colonna (da 72dp a un tetto di 150dp): e' `numeric` con un corpo calcolato. E' l'unico corpo fuori scala.
4. **Cifre del quadrante in condensato di sistema**, non in Inter, perche' Inter non entra (misura qui sotto). E' un secondo carattere sul quadrante, limitato alle cifre e al cronometro.
5. **`border-group` visibile in scuro**, la Constitution lo vuole invisibile dove il tono basta: qui i due toni distano 1,07:1 (Padel Elite le disegna con un bordo di 1px).
6. **`border-strong` e' `elite_outline`**, non il bordo forte di Padel Elite (1,36:1, non passa 3:1).
7. **Successo = lime = accento**: la tinta non distingue lo stato (la Constitution lo vieta da solo), quindi lo stato ha sempre una parola o un'icona. Il lime e' anche il colore del lato 1 in gioco e nella Cronaca; il lato e' sempre accompagnato dal nome.
8. **`radius-overlay` = `radius-surface`** (14dp): Padel Elite non distingue i dialoghi dalle card. Il raggio interno resta minore (8) del contenitore.
9. **`control-standard` 48dp** al posto di 44, e le molle con `SpringAnimation` perche' il progetto e' a View e non a Compose.
10. **Corpi in sp sulla scala M3**, non i px della Constitution.
11. **Maiuscolo.** La Constitution lo vuole raro. Oggi i bottoni e i titoli del contorno sono in `textAllCaps`: G-2 e G-4 lo tolgono. G-6 ha deciso: in gioco i nomi delle squadre e le etichette dei comandi, del foglio e delle didascalie non sono piu' in maiuscolo (le squadre si scrivono come l'utente le ha scritte). **Chiuso da G-9 (7 ottobre 2026):** anche i messaggi composti della striscia, della barra, del dialogo di fine partita e del registro dei game sono in frase («Punto Rossi · 40-30», «Vince Rossi · 6-3», «Game Rossi · 2-3»), con i nomi delle squadre come l'utente li ha scritti, e sull'orologio la riga di stato E, «Chi?», «Salta», «Gol», «Partita finita» e la fascia D («Set 2 · 6-4», «Tie-break») pure. **L'unico maiuscolo che resta e' «CAMBIO» del portiere scaduto** (stato d'allarme dichiarato, si legge a due metri, e un test lo fissa come unica eccezione). In piu', col predefinito lime della squadra 1 il pallino del servizio ha lo stesso colore della zona +: si distingue per forma e posizione e non e' mai l'unico segno.
12. **Colori di squadra scelti dall'utente** fuori dai ruoli: sono contenuto, non tema; `TeamInk` garantisce il contrasto.
13. **Stati del movimento sull'orologio.** Il pallino del servizio non anima; in ambient niente colore (decisione 2) e niente movimento.
14. **Il report PDF sta su carta chiara, non sul tema scuro (G-8).** Il tema scuro e' l'unico dell'app (conflitto 2), ma il PDF esce dal telefono e si stampa: una pagina `#0D0D0F` reggerebbe il contrasto (12,78:1) e non la stampa (inchiostro, testo chiaro impastato, la pagina disegnata solo per l'altezza del contenuto). Quindi i ruoli sono gli stessi nei valori chiari della Constitution, token `print_*` (canvas `#FFFFFF`, surface `#F4F5F7`, border `#D0D3D9`, testo `#1A1C1F` e `#5A5F66`; contrasti nella tabella sopra). Il lime non e' mai testo sulla carta (1,31:1) e i colori di squadra sono solo barrette, scurite fino a 3:1 da `TeamInk.graphicOnLight` (il lime diventa un oliva `#7E9821`): la tinta resta riconoscibile ma il lato non si riconosce dal colore da solo, c'e' sempre il nome accanto. E' l'unica superficie chiara dell'app.

### Piano

#### G-1b. Numeri in Inter con cifre tabulari; JetBrains Mono esce. FATTO (6 ottobre 2026).

**Come e' stato fatto.**

- **File.** `shared/src/main/res/font` contiene solo `inter_regular` (400), `inter_medium` (500) e la nuova `inter_semibold` (600), piu' `inter.xml` con i tre pesi. `inter_semibold` e' il file ufficiale Inter 4.1 (`Inter-SemiBold.ttf`, release dei progetti) ridotto con `pyftsubset` (fontTools) allo stesso sottoinsieme latino di G-1 (stessi unicode, `--layout-features='*'`, hinting tolto): 96.728 B. Il procedimento riprodotto con `inter_regular` da' i 95.020 B di G-1, quindi e' lo stesso. Via `inter_bold`, `inter_black`, `jetbrains_mono.xml`, `jetbrains_mono_bold`, `jetbrains_mono_extrabold` e `docs/licenses/OFL-JetBrainsMono.txt`: l'APK perde 145 KB di mono e 192 KB di Inter 700 e 900 e prende 96 KB, quindi dai 529 KB di G-1 ai 288 KB di oggi. La licenza di Inter resta in `docs/licenses/OFL-Inter.txt`.
- **Pesi.** Chi chiede il grassetto (700) cade sul 600, il piu' vicino, senza grassetto finto (la sintesi del grassetto scatta solo con due gradini di differenza, e qui ce n'e' uno): lo fissa un test. Gli stili che dicevano `bold` o `black` dicono `textFontWeight` 600: `DisplayLarge.Street`, `HeadlineMedium.Street`, `TextAppearance.App.Button`, i tre stili del tema dell'orologio che usano Inter, `Face.Who` e `Face.Status`; nei layout i dieci `textStyle bold` che stavano su Inter (sette sul telefono, tre sull'orologio) e il 900 di `dialog_team_name`. La Cronaca costruisce le viste in codice: 600 dal corpo 20sp in su e 500 sotto per i numeri, 600 e 400 per il testo.
- **Stile del punteggio.** `TextAppearance.App.Punteggio` e' Inter 600 con `fontFeatureSettings tnum` e spaziatura 0; il set (`sets_textview`) e le cifre delle statistiche sono 600 tabulari, il minuto del registro 500 tabulare. Inter ha le cifre **proporzionali** di default: senza `tnum` "11" e "88" misurano diverso (e' la falsificazione nel test), con `tnum` stanno in fila.
- **Cifre del gioco** (misura rifatta col `Paint` vero della vista, `CaratteriDelTelefonoTest`): `dimensioneDelNumero` non e' stata toccata, copia il `Paint` e quindi misura gia' Inter 600 tabulare. Con le cifre tabulari "AV" e' il token piu' largo (in JetBrains Mono erano tutti uguali). **411dp (Pixel 9a):** "88" a **133,5dp** (89% del tetto di 150dp, 172,7dp su 173,5dp di mezza colonna), "AV" a **124,8dp** (83%, 172,3dp su 173,5dp). **360dp:** "88" a **113,8dp**, "AV" a **106,5dp**, sopra il pavimento di 72dp. Costo visivo: le cifre di Inter sono alte 0,73 em, quindi "AV" a 124,8dp vale circa 91dp di altezza contro i 105dp del mono a 144,6dp (G-1) e i 106dp del condensato a 150dp: **le cifre del padel e del tennis perdono circa il 13% di altezza** rispetto a G-1. Se il proprietario le trova piccole, due rimedi per G-6 (nessuno fatto ora): spaziatura negativa di 0,02 em, che guadagna circa il 4%, oppure la variante Inter Display 4.1 (file ufficiale, stesso procedimento), che e' disegnata per i corpi grandi e piu' stretta.
- **Cifre del quadrante** (`CifreDelQuadranteTest`, grafica nativa, xhdpi, file veri): Inter 600 tabulare, con la regola "solo se entra a 58dp e 68dp". **Tondo da 192dp, corpo 58dp: "AV" 80dp, "40" 75dp contro 68dp di colonna: non entra.** **Tondo da 227dp, corpo 68dp: "AV" 94dp, "40" 88dp contro 80,5dp: non entra.** Per entrare servirebbero 49dp e 58dp (il 15% in meno); a 56dp "AV" fa ancora 77dp. Il condensato entra (AV 66dp, 40 59dp a 58dp; 77,5dp e 69dp a 68dp). **Decisione: le cifre del quadrante (`Face.Score`, `Face.Context`, `Face.Keeper`, ambient compreso) restano condensate**, Inter va sul resto del quadrante, come gia' dopo G-1. Il test fallisce se Inter comincia a entrare (e dice di rifare la scelta) o se il condensato smette di entrare, e rifiuta il fuori-misura "per un soffio" (Inter deve restare oltre il 10% sotto).
- **Test.** `CaratteriDelTelefonoTest` aggiornato alla decisione: i tre file e la famiglia si caricano e hanno pesi diversi; JetBrains Mono, `inter_bold` e `inter_black` non esistono piu' (falsificazione); il 700 cade sul 600; `tnum` e' necessario (senza, "11" e "88" misurano diverso); i quindici ruoli M3 sono Inter; punteggio dello storico, set, statistiche e minuto del registro hanno il peso e la feature giusti; "88" e "AV" entrano nella mezza colonna a 411dp e restano sopra i valori misurati, a 360dp sopra il pavimento; "AV" scende sotto "88". `CifreDelQuadranteTest` come sopra.
- **Resta fuori:** `Game.Clock`, `Game.Value`, `Game.Name`, `Game.Command` e `Game.Caption` sono ancora in condensato (G-6); il PDF (G-8); gli screenshot.

- **Costo:** piccolo
- **File:** `shared/src/main/res/font/*`, `docs/licenses/OFL-JetBrainsMono.txt`, `mobile/src/main/res/values/themes.xml`, `wear/src/main/res/values/theme.xml`, layout (`match_item.xml`, `match_event_item.xml`, `chronicle_section.xml`, `dialog_color_picker.xml`, `dialog_team_name.xml`, `item_role_header.xml`, `wear/.../activity_menu.xml`, `activity_player_selection.xml`, `item_player_wear.xml`), `mobile/.../ui/chronicle/ChronicleActivity.kt`, test `CaratteriDelTelefonoTest`, `CifreDelQuadranteTest`
- **Verifica:** `./gradlew test ktlintCheck lintDebug assembleDebug`; i due test dei caratteri verdi e falsificati; nessun riferimento a `jetbrains` nel repository fuori dai documenti.

#### G-2. Ruoli mancanti, gruppi tonali, bottoni e stati; la stessa pressione ovunque. FATTO (6 ottobre 2026).

Il passo che porta la Constitution nel tema; i passi dopo lo usano.

- **Token mancanti**, in `colors.xml`, `dimens.xml` e un nuovo `integers.xml`: `elite_text_disabled`, `elite_info`, i quattro `elite_*_subtle`, `elite_state_pressed`, `elite_chart_3..5`, `space_4..space_64`, i quattro raggi (8, 14, 14, 6), `duration_instant/fast/standard`. Il tema li dichiara come attributi, cosi' i layout citano ruoli e non esadecimali. Un test controlla che ogni ruolo della tabella risolva al valore dichiarato e che i contrasti sopra reggano (estende `TokenEliteTest`).
- **Gruppi tonali.** Un gruppo e' `elite_surface` su `elite_background`, raggio 14, `elite_border` di 1dp, **senza ombra** (`bg_concrete_card` diventa `bg_group`); dentro, le righe sono separate da una linea sottile di `elite_border_strong` **rientrata fino al bordo del testo** (divisore con inset). **Una card non e' un gruppo:** si usa una `MaterialCardView` rialzata solo per un elemento indipendente, spostabile, selezionabile o sollevato (il foglio PARTITA mentre si trascina, i dialoghi). Oggi le card sono usate come gruppi (`content_scoreboard_details.xml`, `match_item`, `item_player_stat`, `item_player_management`): G-4 le riporta a gruppi con righe.
- **Niente ombre sulle superfici ferme.** `cardElevation` e `elevation` a 0 su tutto cio' che sta fermo (`match_item.xml` e `item_player_stat.xml` hanno 4dp, il foglio usa lo stile `materialCardViewElevatedStyle`); `shadow` solo per foglio trascinato, dialoghi, snackbar. Grep di verifica.
- **Bottoni.** `radius-control` 8dp (M3 li fa a pillola: `shapeAppearance` sul tema). **Un solo primario per regione** (lime, testo `elite_on_lime`); secondario a contorno `elite_outline`; distruttivo `TextButton` in `elite_error` (su elevated con l'icona o a corpo grande: 4,35:1); un bottone che lavora **tiene la larghezza**: la stessa vista diventa `progress_activity` che gira una volta ogni 0,8s (ferma con il movimento ridotto) e poi `check` disegnato lungo il tratto in `duration_standard`; larghezza minima fissata sull'etichetta piu' lunga. Via `textAllCaps` dai bottoni: i bottoni dicono l'azione ("Salva risultato"), non "Continua".
- **La pressione e' la stessa ovunque:** scala 0,97 e opacita' 0,85 in `duration_instant`, al rilascio torna com'era. Un solo `StateListAnimator` (`res/animator/press_feedback.xml`) applicato a bottoni, righe, zone + e comandi con icona; niente increspatura (`colorControlHighlight` trasparente). Con il movimento ridotto niente scala: solo opacita', e le righe prendono l'overlay `state-pressed`. Sostituisce `animateZoneTap` (scala 0,96 a mano).
- **Tutti gli stati** dove valgono: normale, premuto, fuoco (anello lime pieno 2dp, distanza 2dp: `foreground` col drawable di fuoco), selezionato (con un secondo segno), disattivo (testo `text-disabled`, icone a 0,5), in corso, sola lettura, errore, vuoto. Un campo con errore mostra il messaggio sotto il campo e tiene cio' che l'utente ha scritto.
- **Movimento.** Via cio' che la Constitution vieta: `AnimationUtils.kt` ha `playEnhancedScoreAnimation` (zoom 1,4, rotazione, colori, rimbalzo: nessun chiamante, si cancella), `pulseAnimation` (ciclo infinito di una card: si cancella) e `animateTextChange` in `MainActivity.kt` (traslazione 20 con `OvershootInterpolator`: rimbalzo decorativo, diventa dissolvenza di `duration_fast`). Nessuna animazione oltre `duration_standard` (240ms).
- **Forme.** Tutte `rounded` (G1): `StreetCard` 14, `StreetButton` e campi 8, `StreetBadge` 6, `MatchSheet` 14 in alto; badge solo bordo e testo dello stesso colore.
- **Pesi.** Tre al massimo per schermata: lo dice il tema (400, 500, 600) e un test conta i pesi distinti dei `TextView` gonfiati di ogni layout del contorno.

**Come e' stato fatto.**

- **Token.** `colors.xml`: `elite_text_disabled`, `elite_info`, i quattro `elite_*_subtle`, `elite_state_pressed`, `elite_chart_3..5`, ognuno col nome del ruolo nel commento. `dimens.xml`: `space_4..space_64`, `radius_control` 8, `radius_surface` 14, `radius_overlay` 14, `radius_badge` 6, `control_touch` 48, piu' `border_width` 1dp, `focus_ring_width` 2dp e `scrim_dim_amount` 0,85 (nomi di ruolo che servivano agli stili). `integers.xml` (nuovo): `duration_instant/fast/standard` 100, 160, 240. **Non fatto:** gli attributi di tema per ogni ruolo ("il tema li dichiara come attributi"): gli stili citano i token `elite_*` direttamente; si aggiungono se G-4 ne ha bisogno. `TokenEliteTest` controlla i valori di tutti i ruoli nuovi, le spaziature, i raggi e le durate, i contrasti della tabella (testo primario sui quattro subtle >= 4,5, etichetta di stato sul proprio subtle >= 4,5 con l'errore a 4,55, info >= 4,5 su fondo, superficie e rialzata, grafici >= 3, testo scuro sul lime premuto >= 4,5) e, in falsificazione, che `text-disabled` stia sotto 4,5 e che il `#33191A` della Constitution col nostro errore non passi.
- **Forme.** Il tema mappa small/medium/large su `ShapeAppearance.App.Control` (8), `Surface` (14), `Overlay` (14); `Badge` (6); `MatchSheet` 14 in alto. Tutte `rounded`: nel tema del telefono non c'e' piu' un `cornerFamily cut`. I nomi `StreetButton` e `StreetBadge` (e gli stili bottone `.Street`) restano come alias arrotondati perche' li cita ancora la schermata di gioco (G-6 li toglie); `StreetCard` e `Chip.Street` non li citava piu' nessuno e sono stati tolti (le forme dell'orologio sono nel suo tema e non si toccano).
- **Componenti** (`styles.xml`). Bottoni: `Widget.App.Button.Primary` (lime, testo `elite_on_lime`, raggio 8, `minHeight` 48dp, elevazione 0, mai a pillola), `.Secondary` (superficie rialzata, bordo `elite_outline`, testo `elite_text_secondary`), `.Destructive` (solo testo `elite_error`), `.Text` (solo testo primario); sono i predefiniti del tema (`materialButtonStyle`, `materialButtonOutlinedStyle`, `borderlessButtonStyle`). `Widget.App.Group` (superficie su canvas, bordo `elite_border` 1dp, raggio 14, nessuna ombra) per i contenitori statici; `Widget.App.Card` (rialzata, bordo `elite_border_strong`, raggio 14, nessuna ombra, bordo lime se selezionata) per un elemento indipendente, predefinita del tema (`materialCardViewStyle`). `Widget.App.TextInputLayout` (e la variante a tendina): fondo `elite_surface`, bordo a 3:1, fuoco lime di 2dp, errore sotto il campo in `elite_error` con l'icona. `Widget.App.Chip` (solo bordo e testo, selezionato lime e con la spunta) e `Widget.App.Badge` (senza fondo, raggio 6). `CheckBox`, `RadioButton`, `Switch`: selezione lime (`color/selection_tint`) e secondo segno (la spunta; il pomello dell'interruttore la mostra solo da acceso, `ic_switch_thumb`). Dialoghi: `ThemeOverlay.App.MaterialAlertDialog` porta la forma `Overlay` al builder M3, `ThemeOverlay.App.Dialog` ha `bg_overlay` (rialzato, bordo, raggio 14) e lo scrim nero all'85% (`backgroundDimAmount` da `scrim_dim_amount`). `bg_concrete_card` e' diventato `bg_group`; nel foglio i gruppi sono `MaterialCardView` con lo stile Group, e l'anello di fuoco e' `bg_focus_ring` (usato come foreground della card dei giocatori). I colori a stati sono in `res/color`.
- **Pressione.** `res/animator/press_feedback.xml` e' l'unico `StateListAnimator`: scala 0,97 e opacita' 0,85 in `duration_instant`, ritorno al rilascio. Lo citano gli stili dei controlli con `?attr/pressFeedback`, un attributo del tema (`attrs.xml`). Il movimento ridotto non ha un qualificatore di risorsa: `MovimentoRidotto.kt` registra un `ActivityLifecycleCallbacks` (`onActivityPreCreated`, minSdk 30) che, con `ANIMATOR_DURATION_SCALE` a 0, sovrappone `ThemeOverlay.App.MovimentoRidotto`, che fa risolvere `pressFeedback` in `press_feedback_reduced` (solo opacita'); lo cambia a ogni activity che nasce, non durante la vita di una. Niente increspatura sui controlli del contorno (`rippleColor` trasparente nei loro stili, non nel tema: la schermata di gioco usa ancora `selectableItemBackground`).
- **Movimento tolto.** `playEnhancedScoreAnimation` e `pulseAnimation` non avevano chiamanti e sono state cancellate. `animateTextChange` (funzione privata di `MainActivity` senza chiamanti) e' una dissolvenza di `duration_fast` in tutto, senza traslazione ne' rimbalzo. **Restano per G-6:** `animateZoneTap` (scala 0,96 a mano, non rispetta il movimento ridotto: la sostituisce lo `StateListAnimator` sulle zone +) e `animateScoreNumber` (scala 1,06 sul punteggio), perche' le usa la schermata di gioco.
- **Applicato ai layout del contorno** (sostituzione diretta, nessuna schermata ridisegnata): `activity_match_settings` (cinque gruppi, campi nello stile nuovo, i due bottoni del colore passano da pieni a secondari: nella schermata non c'e' un'azione primaria), `content_scoreboard_details` (quattro gruppi senza ombra e senza il fondo a mano, TERMINA e' l'unico primario, gli altri secondari o testuali), `activity_onboarding` (AVANTI/FINE primari, SALTA testuale), `dialog_create_player` (campo nuovo, SCEGLI RUOLI secondario), `dialog_team_name` (senza il fondo proprio: lo da' il dialogo), `activity_match_history`, `chronicle_section`, `match_item`, `item_player_stat` (gruppi; via le ombre a 4dp), `item_player_management` (card), `item_chip_filter` (chip nuovo), `view_role_chip` e `view_formation_player_marker` (forma Badge), `activity_add_edit_player` (campo). Le due classi che costruivano badge in codice (`ChronicleActivity`, `TeamColorViews`) usano `ShapeAppearance_App_Badge`.
- **Test.** `ComponentiDelContornoTest` (23 test): il primario ha raggio 8, almeno 48dp, fondo lime, testo on-accent, elevazione 0 e non e' maiuscolo; secondario e distruttivo/testuale; bottone disattivo; i gruppi gonfiati dai layout veri non hanno elevazione ne' ombra; la card; il campo (fuoco lime 2dp, errore sotto, in colore errore); chip e badge (raggio 6, spunta); casella e interruttore; il dialogo (scrim 0,85, raggio 14 anche nel builder M3); la pressione (0,97 e 0,85, ritorno) su bottone, card e casella; il movimento ridotto (senza scala, con l'opacita') e l'overlay applicato da un'activity vera; al massimo un primario visibile per layout; nessuna forma tagliata ne' rosa in `themes.xml` e `styles.xml`. **Falsificazioni:** il bottone e la card di fabbrica M3 sono a pillola e sollevati (la misura distingue); senza lo `StateListAnimator` la pressione non fa niente; senza l'overlay la scala c'e' anche a zero; con le animazioni normali l'overlay non si applica; il controllo delle forme tagliate ne trova una se c'e'. I test esistenti non hanno richiesto modifiche (citano i colori, non gli stili).
- **Resta fuori:** i titoli e le etichette in `textAllCaps` (G-4), le icone (G-3), i layout ancora costruiti con card come gruppi con piu' righe e divisori con inset (G-4), il rosa dei layout (`item_role`, `item_player_stat`, statistiche: G-4), i colori Street dei layout (`concrete_gray`, `stencil_white`), la schermata di gioco e il foglio PARTITA mentre si trascina (G-6), il bottone che lavora (`ProgressButton`, G-4 dove serve).

- **Costo:** medio
- **File:** `mobile/src/main/res/values/colors.xml`, `dimens.xml`, `integers.xml` (nuovo), `themes.xml`, `styles.xml`, `mobile/src/main/res/animator/press_feedback.xml` (nuovo), `drawable/bg_concrete_card.xml` (-> `bg_group.xml`), `drawable/bg_focus_ring.xml` (nuovo), `mobile/.../utils/AnimationUtils.kt`, `utils/TeamColorViews.kt`, `MainActivity.kt` (`animateTextChange`), `mobile/src/test/.../TokenEliteTest.kt`
- **Verifica:** `TokenEliteTest` esteso verde (ogni ruolo risolve, contrasti della tabella); grep: zero `cornerFamily cut`, zero `textAllCaps` sui bottoni, `cardElevation` e `elevation` solo a 0 fuori da foglio e dialoghi; test che conta un solo bottone pieno per layout (come prima) e che la pressione sia scala 0,97 e opacita' 0,85 (e solo opacita' con `ANIMATOR_DURATION_SCALE` a zero), **falsificato** togliendo l'animator; `ContrastiDelTelefonoTest` verde.

#### G-3. Icone: Material Symbols outlined, con la tabella concetto-icona.

La Constitution fissa il concetto, non il file (`Icons/README.md`: su Android Material Symbols, outlined, un solo peso). **Non Heroicons** (la decisione del 5 ottobre cade). Vettoriali scaricati dal catalogo Material Symbols uno per uno (outlined, peso 400, riempimento 0, 24dp), nome `ic_<concetto>.xml`, colore `?attr/...` o `currentColor`, tre misure 16, 20 e 24dp (`icon-compact`, `-standard`, `-prominent`); **niente emoji ne' altre famiglie**; i comandi di sola icona hanno `contentDescription`, quelle decorative `importantForAccessibility="no"`. Selezione: l'icona selezionata **tiene la forma a contorno**: la selezione si mostra con l'indicatore e il lime, non col riempimento. Progresso: sempre `progress_activity` che gira; successo `check_circle` o `check` disegnato; errore `cancel`.

| Concetto | Simbolo (Material Symbols outlined) | Drawable del telefono | Drawable dell'orologio |
|---|---|---|---|
| Indietro | `arrow_back` | `ic_arrow_back` | - |
| Chiudi | `close` | - | - |
| Menu | `menu` | `ic_menu` | - |
| Casa | `home` | - | - |
| Vai al dettaglio | `chevron_right` | `ic_chevron_right` | - |
| Espandi | `expand_more` | `ic_expand_more` | - |
| Comprimi | `expand_less` | `ic_expand_less` | - |
| Altre azioni | `more_horiz` | - | `ic_more_horiz` |
| Apre fuori dall'app | `open_in_new` | `ic_open_in_new` | - |
| Aggiungi | `add` | `ic_add` | - |
| Modifica | `edit` | `ic_edit` | - |
| Elimina | `delete` | `ic_delete` | `ic_delete` |
| Cerca | `search` | `ic_search` | - |
| Filtra | `filter_list` | - | - |
| Ordina | `swap_vert` | `ic_swap_vert` | - |
| Condividi | `share` | `ic_share` | - |
| Scarica, esporta | `download` | - | - |
| Importa | `upload` | - | - |
| Copia | `content_copy` | - | - |
| Salva | `save` | - | - |
| Annulla (undo) | `undo` | `ic_undo` | - |
| Aggiorna | `refresh` | `ic_refresh` | - |
| Mostra | `visibility` | - | - |
| Nascondi | `visibility_off` | - | - |
| Avvia, riprendi | `play_arrow` | `ic_play_arrow` | - |
| Pausa | `pause` | `ic_pause` | - |
| Fatto, selezionato | `check` | `ic_check` | `ic_check` |
| Successo | `check_circle` | `ic_check_circle` | - |
| Avviso | `warning` | `ic_warning` | - |
| Errore | `cancel` | `ic_cancel` | - |
| Informazione | `info` | - | - |
| In corso | `progress_activity` | `ic_progress_activity` | - |
| Statistiche | `bar_chart` | `ic_bar_chart` | - |
| Data | `calendar_today` | - | - |
| Persona | `person` | `ic_person` | - |
| Squadra, gruppo (anche un girone) | `group` | `ic_group` | - |
| Impostazioni | `settings` | `ic_settings` | - |
| Notifiche | `notifications` | - | - |
| Sport, palla, gol | `sports_soccer` | - | - |
| Racchetta (padel, tennis) | `sports_tennis` | - | `ic_sports_tennis` |
| Portiere | `sports_handball` | - | `ic_sports_handball` |
| Cronometro | `timer` | - | - |
| Scegli lo sport | `sports` | - | `ic_sports` |
| Fine partita | `flag` | - | `ic_flag` |
| Vibrazione | `vibration` | - | `ic_vibration` |
| Storico delle partite | `history` | `ic_history` | - |
| Cronaca della partita | `timeline` | `ic_timeline` | - |
| Presenze | `event_available` | `ic_event_available` | - |
| Scambio dei posti | `swap_horiz` | `ic_swap_horiz` | - |
| Aggiungi giocatore | `person_add` | `ic_person_add` | - |
| Serata, chi c'e' stasera | `groups` | `ic_groups` | `ic_groups` |
| Ruota le coppie | `autorenew` | `ic_autorenew` | - |
| Stesse coppie | `repeat` | `ic_repeat` | - |
| Scambia i lati | `sync_alt` | `ic_sync_alt` | - |
| Blocco, accedi di nuovo | `lock` | `ic_lock` | - |
| Orologio collegato | `watch` | `ic_watch` | - |
| Orologio scollegato | `watch_off` | `ic_watch_off` | - |
| Invio a Padel Elite | `send` | `ic_send` | - |
| In coda (invio) | `schedule` | `ic_schedule` | - |
| In attesa dell'admin | `hourglass_empty` | `ic_hourglass_empty` | - |
| Diretta, in onda | `sensors` | `ic_sensors` | - |

Le righe da `sports_soccer` in giu' sono le **icone di dominio**: la tabella della Constitution non le ha, sono del progetto e vengono dallo stesso catalogo (stesso peso, stessa geometria). Il colore dell'orologio collegato e' il lime e quello dello scollegato il testo secondario, mai il verde `#76FF03`. Le icone del launcher sono G5. Una riga con `-` e' un concetto fissato ma non ancora usato da nessuna schermata: il suo vettoriale non c'e' (non si tengono file morti) e si scarica dal catalogo, col nome `ic_<simbolo>`, quando serve. `IconeDelTelefonoTest` legge questa tabella: ogni `ic_*` in `drawable/` e' una sua riga, ogni riga con un drawable ha il file, e un concetto ha un solo simbolo e un solo file.

**Come e' stato fatto.**

- **Vettoriali.** Scaricati uno per uno dal catalogo ufficiale `google/material-design-icons` (cartella `symbols/android/<nome>/materialsymbolsoutlined/<nome>_24px.xml`, che e' il file di peso 400, grade 0, optical size 24, riempimento 0), non ridisegnati e non modificati: viewport 960, 24dp, `@android:color/white` con `android:tint="?attr/colorControlNormal"` (il bianco di base e' il segnale per il tint, non un colore), piu' una riga di commento in testa. Il colore lo decide chi usa l'icona (`app:tint`, `app:iconTint`, `drawableTint`); `ic_arrow_back` ha anche `autoMirrored`. Sono 22 nel telefono e 2 nell'orologio (`ic_more_horiz`, `ic_check`), che ha le sue copie perche' i moduli non condividono le risorse.
- **Rinomi e tolti.** Il nome del file e' quello del simbolo: `ic_plus` -> `ic_add`, `ic_play` -> `ic_play_arrow`, `ic_stats` -> `ic_bar_chart`, `ic_swap` -> `ic_swap_horiz`, `ic_color_picker` -> `ic_palette`, `ic_watch_connected` / `ic_watch_disconnected` -> `ic_watch` / `ic_watch_off` (prima erano un orologio da muro e un cerchio, non un orologio da polso), `ic_menu_dots` (tre cerchi a mano) -> `ic_more_horiz`. `ic_switch_check`, la spunta scritta a mano in G-2 con un `fillColor` proprio, e' tolta: il pomello dell'interruttore usa `ic_check` e il colore sul lime (`elite_on_lime`) viene dall'attributo `thumbIconTint` dello stile `Widget.App.Switch`. Le icone di sistema `@android:drawable/ic_menu_search` e `ic_menu_sort_by_size` del menu dei giocatori sono `ic_search` e `ic_swap_vert`.
- **Un concetto, un'icona.** La Cronaca aveva lo stesso glifo delle statistiche (`ic_stats`): ora la Cronaca e' `timeline` e le statistiche `bar_chart`. Le emoji usate come icone sono tolte: la riga dei giocatori mostrava "⚽ 12" e "🎮 24" come testo, ora e' il numero con `sports_soccer` e `event_available` come `drawableStart` (il significato sta nella `contentDescription`, da `stats_goals` e `stats_appearances`); la spunta "✓ in uso" della scelta dello sport sull'orologio e' `ic_check` a 16dp con il tint del testo (il menu dell'orologio riusa la stessa riga per sottotitoli grigi, senza icona, perche' la spunta si mette da codice).
- **Misure.** `icon_compact` 16dp, `icon_standard` 20dp, `icon_prominent` 24dp in `dimens.xml` del telefono e dell'orologio. I bottoni con testo e icona hanno `app:iconSize="@dimen/icon_standard"`; le tre illustrazioni da 120dp (stato vuoto dei giocatori, delle statistiche, passo dell'onboarding) sono a `icon_prominent` e `importantForAccessibility="no"`. Restano a 48dp la zona + della schermata di gioco (e' un glifo che riempie una zona, non un'icona di comando: lo decide G-6) e i comandi di sola icona (bersaglio di 48dp con l'icona da 24 dentro).
- **Accessibilita'.** I comandi di sola icona (`ImageButton`, `FloatingActionButton`, il pulsante dello stato dell'orologio, il menu dell'orologio) hanno `contentDescription`; i glifi decorativi `importantForAccessibility="no"`. Il test lo controlla su tutti i layout.
- **Fondo del dialogo.** Difetto visto a schermo: "Termina partita?" aveva il fondo verde oliva e non `background-elevated` #1E1E22. Non e' un colore sbagliato ma la **tinta di elevazione** di Material 3: nel tema `elevationOverlayEnabled` era vero e `elevationOverlayColor` e' `colorPrimary`, il lime, che Material mescola nelle superfici sollevate. `colorSurfaceTint` non c'entra in questa versione (material 1.13 non lo espone come attributo). Ora `elevationOverlayEnabled` e' falso nel tema e negli overlay `ThemeOverlay.App.MaterialAlertDialog` e `ThemeOverlay.App.Dialog`: le superfici si alzano di tono coi token (`elite_surface_raised`), mai di tinta. `TintaDelleSuperficiTest` disegna il fondo vero del dialogo del builder M3 su una bitmap e legge un pixel: era `#303424` (prima), ora e' esattamente `elite_surface_raised`; la falsificazione fa la stessa misura sul tema stock di M3 e deve vedere il fondo cambiare.
- **Non fatto.** Le icone di sistema delle notifiche del servizio del cronometro (`android.R.drawable.ic_dialog_info`, `ic_media_pause`, `ic_menu_close_clear_cancel`, `ic_dialog_alert` in `MatchTimerService.kt`) restano: una icona di notifica la carica il sistema fuori dal tema dell'app e `?attr/colorControlNormal` non si risolverebbe; serve una variante senza attributi, che per la nostra regola (nessun colore scritto nel vettore) e' una decisione da prendere. I `app:tint` e `iconTint` dei layout che citano ancora i vecchi colori (`neon_cyan`, `sidewalk_gray`, `stencil_white`, `team_spray_yellow`) sono di G-4 (contorno) e G-6 (gioco). Nessuno screenshot: il passo e' stato fatto senza emulatore.

- **Costo:** piccolo
- **File:** `mobile/src/main/res/drawable/ic_*.xml` (quelle in tabella), `values/dimens.xml` (telefono e orologio), `values/styles.xml` (`Widget.App.Switch`), `values/themes.xml` (overlay di elevazione), `menu/menu_players_management.xml`, i layout che citano le icone, `PlayersManagementAdapter.kt`, `SportSelectionActivity.kt` (orologio), `wear/src/main/res/drawable/ic_more_horiz.xml`, `ic_check.xml`; test `IconeDelTelefonoTest`, `IconeDelPolsoTest`, `TintaDelleSuperficiTest`, `SelezioneSportTest`
- **Verifica:** lint senza voci nuove; `IconeDelTelefonoTest` (ogni `ic_*` e' una riga della tabella con viewport 960 e senza colori scritti a mano, un concetto un solo file, comandi di sola icona con `contentDescription`, misure delle tre dimensioni, falsificato su vettori e layout sbagliati); `TintaDelleSuperficiTest`; bersagli di 48dp invariati; screenshot da fare sul dispositivo.

#### G-4. Contorno: foglio PARTITA, storico, statistiche, giocatori, impostazioni, onboarding, e "Essential information". FATTO per il contorno (7 ottobre 2026); il foglio PARTITA mentre si gioca e' G-6.

Ogni layout del contorno smette di citare i vecchi token (`asphalt_dark`, `concrete_gray`, `graffiti_dark_gray`, `stencil_white`, `sidewalk_gray`, `outline_gray`, `graffiti_pink`, `neon_cyan`) e cita i ruoli `elite_*`. Rosa e ciano escono dal chrome: il podio e i numeri delle statistiche passano a lime (`StatisticsAdapter.kt`, `item_player_stat.xml`, `activity_statistics.xml`), i ruoli dei giocatori non si distinguono piu' per rosa e ciano ma per etichetta e testo (`RoleUtils.kt`, `item_role.xml`, `view_role_chip.xml`, `view_formation_player_marker.xml`); il ciano resta nel grafico della Cronaca. Dialoghi del marcatore, del ruolo, del nome squadra, del colore e del giocatore: fondo rialzato, un solo primario. Dopo l'ultima citazione si cancellano dal blocco "STREET (eredita')" di `colors.xml` i vecchi nomi che nessuno usa piu'. In piu', le regole della Constitution:

- **Gruppi, non card.** Ogni lista diventa **un gruppo**: storico, giocatori, statistiche, registro, rose; le righe sono separate da linee sottili rientrate al testo e **senza ombra**. Si cambia `match_item.xml`, `item_player_stat.xml`, `item_player_management.xml`, `team_player_item.xml` e i quattro blocchi di `content_scoreboard_details.xml` (che oggi sono card elevate, vietate per un gruppo). Non resta nessuna card nel contorno: se ne serve una, si giustifica per iscritto (indipendente, spostabile, selezionabile, sollevata).
- **Essential information: si toglie prima di rimpicciolire.** Ogni schermata ha un compito e un valore piu' importante, e il resto passa a un secondo livello, o sparisce. Per schermata: **storico** una riga = squadre, punteggio e data (il resto, set, luogo e giocatori, nel dettaglio); **statistiche** una riga = nome e il valore che conta (il rango e le presenze passano nel dettaglio); **giocatori** una riga = nome e ruolo; **impostazioni** una sezione per volta, la riga delle regole solo dove cambia qualcosa; **foglio PARTITA** primario TERMINA e al massimo due azioni frequenti visibili, le altre in un menu; **onboarding** un solo messaggio per passo. Nessun titolo che ripete la scheda, nessuna etichetta che ripete l'icona, nessun aiuto che ripete l'etichetta. Un valore mostrato ha etichetta, unita' e una ragione: "una cifra con il suo significato, non cinque senza".
- **Gli stati e le parole.** Vuoto dice che cosa e' vuoto, perche' e che fare ("Nessuna partita questo mese", poi cosa fare); errore dice che cosa e' successo e come rimediare e tiene il lavoro dell'utente; niente tono da marketing, niente punti esclamativi.
- **Un solo primario per vista** (come G-2), e **via `textAllCaps`** dai titoli di schermata.

**Come e' stato fatto (7 ottobre 2026, passo wf38; il contorno, non il foglio PARTITA).** Il foglio mentre si gioca (`content_scoreboard_details`, `team_player_item`, `match_event_item`, `match_game_item`), la schermata di gioco, la Cronaca (G-5) e il PDF (G-8) non sono stati toccati, salvo il rosa del marcatore delle formazioni (`view_formation_player_marker`, solo i token) e il fatto che i chip dei ruoli e il titolo `HeadlineMedium` non sono piu' in maiuscolo anche li'.

- **Componenti nuovi** (nessuno e' nella cartella dei componenti della Constitution consultata: EmptyState, RowDetail, ProgressButton, Tabs, SegmentedControl e Notice non erano fra i file letti, e si e' seguito il loro nome e le regole del README e di `layout.md`).
  - `EmptyStateView` (`ui/`, con `view_empty_state.xml` e gli attributi `emptyIcon`, `emptyTitle`, `emptyReason`, `emptyAction`): titolo che dice cosa e' vuoto, motivo e cosa fare, piu' il comando; l'icona a `icon_prominent` c'e' solo dove aiuta (statistiche e giocatori), non nello storico. Un vuoto dovuto alla ricerca o al filtro dice altro da un elenco vuoto (`setMessage`).
  - `ProgressButton` (`ui/`): lo stesso bottone che prende `ic_progress_activity` che gira una volta ogni 0,8s (`RotateDrawable` con `ObjectAnimator` lineare; col movimento ridotto la durata scala a 0 e l'icona resta ferma), non accetta altri tocchi, tiene la larghezza minima che aveva e dice che cosa fa (`loadingLabel`). Lo usa il salvataggio del giocatore. **Non fatto:** il `check` disegnato lungo il tratto a fine lavoro (la schermata si chiude subito e il vettore `check` non e' un tratto).
  - `InsetDividerDecoration` (`ui/`): `border-subtle` di 1dp, rientrata di `space_16` (il bordo del testo), disegnata sull'ultimo pixel della riga e non dopo l'ultima.
  - Stili: `Widget.App.Toolbar` (titolo `section-heading` 600, fondo trasparente sul canvas), `Widget.App.RowDivider`, `Widget.App.TabLayout` (un solo indicatore lime di 2dp, scheda scelta in testo primario e peso 600), e i tre ruoli di testo `TextAppearance.App.SectionHeading` (600), `RowTitle` (500), `Caption` (400): tre pesi per schermata. Nuova dimen `floating_action_clearance` (72dp, lo spazio sotto una lista per il tasto che galleggia). Icone `expand_more`, `expand_less`, `progress_activity` scaricate dal catalogo e messe in tabella G-3.
- **Per schermata: il compito, il valore piu' importante, cio' che si e' tolto.**
  - **Impostazioni.** Il compito e' preparare la partita; il valore e' il nome e il colore di ciascuna squadra. Tolto: i cinque riquadri da una riga (ora tre gruppi tonali: Squadre, Partita, Padel Elite, con righe e linee rientrate); le etichette "Colore squadra 1/2" che ripetevano l'icona (il comando e' la tavolozza dipinta del colore scelto, 48dp, con la descrizione per TalkBack); il suggerimento "si cambia solo a partita ferma" (la Snackbar lo dice al momento in cui serve); titoli in `titleMedium` e colori Street. La riga delle regole resta solo sotto lo sport, dove cambia insieme alla scelta; il timer del portiere sparisce fuori dal calcio.
  - **Onboarding.** Il compito e' dire una cosa per passo; il valore e' il titolo del passo. Tolto: i titoli in maiuscolo da marketing ("Gestione punteggio facile"), l'icona gialla (ora lime), la frase sul pulsante ≡ e le rose, che era una seconda notizia nello stesso passo (**da rivedere**: la scoperta del foglio PARTITA resta affidata alla `contentDescription` del pulsante), il "sempre sincronizzati" (ora "finche' e' collegato", che e' vero).
  - **Storico.** Il compito e' ritrovare una partita; il valore e' il punteggio con le due squadre. Tolto: il riquadro "Partite totali" (ripeteva la lista), il maiuscolo della riga sport e data, i colori Street, e dalla scheda chiusa set, regola del padel, giocatori, Cronaca, Esporta ed Elimina: stanno nel dettaglio che si apre toccando la scheda (freccia `expand_more`/`expand_less`, stato per partita che sopravvive al riciclo delle viste, `stateDescription` e azione di clic per TalkBack, scorrimento dei vicini in `duration_fast`). Restano in vista lo stato dell'invio a Padel Elite e il suo comando: sono cio' che l'utente deve poter rimediare.
  - **Statistiche.** Il compito e' vedere chi ha segnato di piu'; il valore sono i gol. Tolto: il podio rosa (ora il primo ha i gol in lime, che e' l'enfasi con significato), il rango "#1" e le presenze in riga (il rango e' l'ordine, la descrizione della riga li dice a TalkBack; le presenze stanno nel dettaglio di Gestisci giocatori), la card per riga (ora un gruppo con righe e linee rientrate), la scheda rosa (indicatore lime), "Gioca un match per vedere chi e' il King!" (ora dice che cosa conta e cosa fare).
  - **Gestisci giocatori.** Il compito e' tenere l'elenco di chi gioca; il valore e' il nome. Tolto: l'avatar a colori (un'iniziale in un quadrato colorato non dice nulla che il nome non dica; `avatar_colors` e' stato cancellato), gol e presenze in riga (stanno nel dettaglio dietro `bar_chart`), il secondo bottone "modifica" (la riga stessa si tocca per modificare), le card (un gruppo con righe), i chip dei ruoli colorati. Il FAB e' lime e unico primario dell'elenco, e si nasconde quando lo stato vuoto ha il suo bottone "Aggiungi giocatore".
  - **Aggiungi o modifica giocatore.** Il compito e' nome e ruoli. Tolto: l'icona di sola barra come salva, ora un bottone "Salva giocatore" (l'azione per nome, `ProgressButton`); i toast d'errore, ora sotto il campo con il testo che resta, che l'utente corregge senza perderlo.
  - **Dialoghi.** Nome squadra: via l'etichetta che ripeteva il titolo, l'anteprima gialla in maiuscolo e la riga "scelte rapide" (i suggerimenti sono chip dei token, nomi uguali nelle due lingue); marcatore e ruoli: titolo `section-heading`, righe da 48dp con la pressione condivisa, intestazioni di categoria in testo secondario (non gialle); nuovo giocatore: bottone "Scegli i ruoli" in sentence case; colore: spaziature su token. Il primario di ogni dialogo e' il suo bottone positivo.
  - **Padel Elite (schermata dell'invio).** Il debito 6 dell'invio e' chiuso: i gruppi hanno la linea rientrata fra una riga e l'altra (aggiunta da codice, `PadelEliteActivity.rigaDivisoria`).
- **Ruoli dei giocatori.** `RoleUtils.getCategoryColor` e' cancellata: la categoria non ha piu' una tinta (rosa, ciano, giallo e verde erano colori Street). Il chip e' la sigla (POR, DC) in un badge solo bordo e testo, con il nome intero per TalkBack; senza ruoli non c'e' nessun chip (via "N/A").
- **Card rimaste nel contorno, giustificate.** `match_item` (una partita dello storico: indipendente, si espande, ha comandi propri). I `Widget.App.Group` sono gruppi e non card (impostazioni, statistiche, giocatori, Padel Elite). Nel foglio (G-6): `team_player_item` e `view_formation_player_marker`. L'unica ombra nel contorno e' quella del FAB dei giocatori, che galleggia sopra la lista (`shadow-floating`).
- **Maiuscolo.** Via `textAllCaps` da ogni layout del contorno; il contorno non chiama piu' `.uppercase()` (solo il campo del nome squadra tiene `textCapCharacters` come tipo di tastiera, perche' in gioco il nome e' in maiuscolo). `save` ora e' "Salva" e non "SALVA"; i titoli delle schermate sono in sentence case. Restano in maiuscolo le stringhe del foglio e del gioco (G-6): `label_match_history`, `label_statistics`, `label_players`, `label_settings` e simili.
- **Colori Street rimasti.** Nessuno e' citato dal contorno; `asphalt_black`, `asphalt_dark`, `concrete_gray`, `graffiti_dark_gray`, `stencil_white`, `sidewalk_gray`, `graffiti_pink`, `neon_cyan`, `outline_gray`, `error_red`, `error_text`, `team_spray_yellow` e `team_electric_green` restano in `colors.xml` perche' li citano ancora il gioco e il foglio (G-6), la Cronaca (G-5), il PDF (G-8) e i colori predefiniti delle squadre. `elite_text_tertiary` e' tolto. Risorse inutili segnalate da lint dopo G-2: `Widget.App.Pressable` ora lo usano le righe cliccabili (giocatori, marcatore, partita e i loro comandi di sola icona); `bg_group`, che nessuno citava dopo la riscrittura delle schermate (i gruppi sono `Widget.App.Group`), e lo stile `Widget.App.Badge`, che il ruolo di un giocatore ha sostituito con il chip `view_role_chip`, sono cancellati; con loro `ic_save`, `ic_event_available` e `ic_sports_soccer` (il salva in barra e i numeri in riga non ci sono piu': nella tabella G-3 tornano a `-`) e `avatar_colors` (`arrays.xml`).
- **Test.** `ContornoDelTelefonoG4Test` (nuovo): nessun layout del contorno ha maiuscolo forzato, rosa, colori Street, ripple o ombra (testo e viste gonfiate, con il solo FAB che galleggia); gli stili di testo non sono in maiuscolo; le righe cliccabili hanno la pressione (0,97 e 0,85, ritorno) e le altre no; gli stati vuoti hanno titolo, motivo e strada e non hanno punti esclamativi; il `ProgressButton` tiene la larghezza e non accetta tocchi; le linee fra le righe sono rientrate e non ci sono dopo l'ultima (pixel); le schede hanno il testo giusto e l'indicatore lime; la scheda dello storico si apre e si chiude, tiene lo stato per partita nel riciclo e non nasconde lo stato dell'invio. **Falsificazioni:** il controllo dei layout trova ogni difetto in un esempio sbagliato (e nella schermata di gioco, che li ha ancora), una riga senza stile non ha la pressione, uno stato vuoto senza azione non mostra il bottone, un bottone qualunque con un testo piu' corto si stringe, un pixel dentro una riga non ha la linea. Adattati senza cambiarne il significato: `StoricoDelContornoTest`, `GiocatoriEStatisticheDelContornoTest` (il podio rosa e il FAB rosa diventano lime; l'avatar esce; i chip sono neutri), `ImpostazioniDelContornoTest`, `CaratteriDelTelefonoTest`, `AccessibilitaDelTelefonoTest`, `ComponentiDelContornoTest`, `TokenEliteTest`, `TitoliDelleSchermateTest`, `PlayersManagementAdapterTest`, `AddEditPlayerActivityTest`; `RoleUtilsAndroidTest` e' cancellato (provava solo i colori di categoria, che non esistono piu').
- **Non verificato:** nessuno screenshot, nessun emulatore, nessuna prova al 200% o a schermo stretto: le schermate sono state costruite per larghezza fluida (`0dp`, `wrap_content`, nessuna altezza fissa su testo), ma la prova a occhio e' del proprietario. L'anello di fuoco e la pressione sulle righe dentro un gruppo (scala 0,97 di una riga in un contenitore con angoli) vanno guardati sul dispositivo.

- **Costo:** medio
- **File:** `mobile/src/main/res/layout/activity_match_settings.xml`, `activity_match_history.xml`, `match_item.xml`, `match_game_item.xml`, `activity_statistics.xml`, `item_player_stat.xml`, `activity_players_management.xml`, `item_player_management.xml`, `activity_add_edit_player.xml`, `dialog_create_player.xml`, `dialog_role_selection.xml`, `dialog_select_scorer.xml`, `dialog_color_picker.xml`, `dialog_team_name.xml`, `item_role.xml`, `item_role_header.xml`, `view_role_chip.xml`, `view_formation_player_marker.xml`, `team_player_item.xml`, `scorer_item.xml`, `content_scoreboard_details.xml` (i quattro blocchi), `activity_onboarding.xml`, `fragment_onboarding_step.xml`; `mobile/src/main/java/it/vantaggi/scoreboardessential/ui/statistics/StatisticsAdapter.kt`, `utils/RoleUtils.kt`, `MatchHistoryAdapter.kt`, `MatchLogAdapter.kt`, `TeamRosterAdapter.kt`, `MainActivity.kt` (le righe del foglio); `values/strings.xml` e `values-it/strings.xml` (stati vuoti ed errori); test che nominano i vecchi token (`MatchLogAdapterTest`, `StoricoDelContornoTest`, `GiocatoriEStatisticheDelContornoTest`, `PartitaARacchettaTest`, `utils/RoleUtilsAndroidTest`) e `TitoliDelleSchermateTest`, `AccessibilitaDelTelefonoTest`
- **Verifica:** grep dei vecchi nomi nel contorno (zero risultati fuori da gioco e PDF) e di `MaterialCardView` nel contorno (zero, o ciascuna giustificata in `DESIGN.md`); per ogni schermata una riga "il compito e' ..., il valore piu' importante e' ..., tolto: ..." nel registro dei lavori; test dei titoli e dell'accessibilita' verdi; screenshot accanto alla pagina della dashboard; carattere al 200% senza tagli.

#### G-5. Cronaca dell'app allineata alla Cronaca della dashboard. FATTO (7 ottobre 2026).

Stesso ordine delle sezioni della Cronaca della dashboard; lati lime (lato 1) e ciano (lato 2) quando le squadre hanno i colori predefiniti, con colori scelti dall'utente l'app tiene il loro colore schiarito da `TeamInk.graphicOnBlack` (la dashboard non ha colori di squadra e l'app si': differenza voluta); apice del tie-break e "B" sui break come nella dashboard. In piu': la Cronaca e' un gruppo tonale con righe sottili (come G-4); il grafico del momentum risponde a **una domanda sola** (chi sta spingendo), nella forma piu' semplice, con `chart-1` lime e `chart-2` ciano, **etichette dirette** sulle serie e un secondo segno oltre al colore (forma o testo): lime e ciano si confondono con alcune forme di daltonismo; griglia `chart-grid`; nessun grafico decorativo. I numeri della Cronaca sono Inter tabulare (gia' fatto in G-1b).
- **Costo:** medio
- **File:** `mobile/.../ui/chronicle/ChronicleActivity.kt`, `MomentumView.kt`, `ChronicleText.kt`, `activity_chronicle.xml`, `chronicle_section.xml`, `mobile/src/test/.../ui/chronicle/ChronicleTextTest.kt`
- **Verifica:** test sul testo del tie-break e della "B"; contrasto dei lati sul fondo >= 3 e la serie ha un segno oltre al colore; la Cronaca della stessa partita nell'app e nella dashboard affiancate.

**FATTO (7 ottobre 2026, passo wf41). Come e' stato fatto.** La Cronaca del telefono (`ChronicleActivity`, `MomentumView`, `ChronicleText`, `activity_chronicle`, `chronicle_section`); nessuno screenshot, nessun emulatore (la prova a occhio e la Cronaca affiancata a quella della dashboard sono del proprietario).

- **Compito e valore.** Il compito e' capire com'e' andata la partita; il valore piu' importante e' il tabellone dei set, in cima. Tolto: le card (le sei sezioni sono gruppi), il maiuscolo (titolo della schermata, intestazioni di sezione, «Interrotta»), il blocco pieno di colore con il testo sopra (l'etichetta di squadra e i riquadri dei game), i riempimenti del grafico (ripetevano quello che dice la posizione), la legenda a parole del grafico (la sostituiscono le etichette dirette), il tono del «CHRONICLE»/«CRONACA» a tutto maiuscolo.
- **Ordine e gruppi.** Stesso ordine della dashboard (Tabellone, Andamento, Game per game, Servizio, Tempi, Momenti chiave). Ogni sezione e' un `Widget.App.Group` (surface su canvas con `border-group`, nessuna ombra) con l'intestazione `Caption` sopra e righe da `space_16` e `space_12` separate da `border-subtle` di 1dp rientrata di `space_16` (nessuna linea prima della prima riga ne' dopo l'ultima, anche dentro le due `TableLayout`, cosi' le colonne di numeri restano in fila). Il corpo del gruppo non ha margini: ogni riga porta i suoi (`ChronicleActivity.addRow`, `addTableRow`). Nessuna card nella Cronaca, quindi niente da giustificare.
- **Testi su token.** Solo `elite_text_primary` e `elite_text_secondary`; il colore di squadra e' sempre grafica (barretta, tratto, linea), mai testo. Corpi della scala M3 (12, 14, 16 e 22sp per i set del tabellone, che prima erano 24), tre pesi (400, 500, 600). **Tutti i testi** hanno `fontFeatureSettings` `tnum`, quindi anche i numeri dentro le frasi («Punti vinti 24 – 0», «4/8  50%», «3 di fila») stanno in fila. Apice del tie-break (`SuperscriptSpan` a 0,55), «B» sui break, «TB 7-5» come prima.
- **Lati.** Il colore di ciascun lato e' quello con cui si e' giocato; con i predefiniti (G-6) sono lime (lato 1, `chart-1`) e ciano (lato 2, `chart-2`), e restano esattamente quelli perche' reggono 3:1 sul gruppo (13,85 e piu'). Con un colore scelto dall'utente si schiarisce con `TeamInk.graphicOn` **contro il fondo del gruppo** (`#161618`): `ChronicleText.graphicOn` ora delega a `TeamInk.graphicOn`, che misura contro il fondo vero; prima misurava contro il nero con un fattore che serviva a rimediare. Differenza voluta con la dashboard (che non ha colori di squadra), come da conflitto 12. Una partita salvata prima di G-6 con il vecchio giallo e verde si legge in giallo e verde: sono i colori con cui si e' giocato.
- **Tabellone.** Il nome di ogni lato e' testo primario con la barretta di 4dp (`etichettaConBarretta`, la stessa del foglio PARTITA); chi ha perso ha i set in testo secondario **e** peso 400 (il colore non e' l'unico segno); «Interrotta» e' un badge solo bordo e testo, raggio 6.
- **Game per game.** Un riquadro `elite_surface_raised` (raggio 8) per game con la barretta del vincitore da 4dp: **a sinistra se ha vinto il lato 1, a destra se il lato 2**, cosi' il lato si legge anche senza il colore; la legenda lo dice coi nomi («La barretta dice chi ha vinto il game: Rossi a sinistra, Blu a destra»). Il punteggio dopo il game, la «B» e «TB 7-5» sono testo primario tabulare.
- **Andamento (`MomentumView`).** Risponde a una domanda sola: chi e' stato avanti nei punti vinti. La forma piu' semplice: una linea (un tratto per punto, del colore di chi era avanti), l'asse dello zero in `border-strong` (perche' dice sopra e sotto e il bordo forte passa 3:1), le due estremita' sulla griglia `chart-grid` (`border-subtle`), le linee **tratteggiate** a fine set. Il secondo segno oltre al colore e' doppio: la **posizione** (lato 1 sopra lo zero, lato 2 sotto) e le **etichette dirette**, una fascia sopra per il lato 1 e una sotto per il lato 2 col tratto del suo colore, il nome in testo primario e il vantaggio massimo («Rossi +12»; un lato mai avanti ha il solo nome: un «+0» non direbbe niente). Le fasce crescono col carattere (altezza dalle misure del testo, testo ellittico se il nome e' lungo), quindi al 200% niente taglia; non c'e' movimento. La descrizione per TalkBack resta («Vantaggio massimo: Rossi +3, Blu +1»). Il grafico espone `sideColors` e `labels` per i test.
- **Stati vuoti.** Ognuno ha un titolo che dice cosa manca (una frase, senza punto) e un motivo in testo secondario che dice perche' e cosa fare, in values e values-it: nessun punto («La Cronaca si ricava dai punti. Questa partita e' stata salvata senza, per esempio perche' si e' segnato a game...»), nessun game, servizio sconosciuto («Il tabellone non sapeva chi serviva» / «Con due giocatori per squadra nelle rose prima del primo punto...»), servizio nel singolare, tempi non registrati. La Cronaca non disponibile usa `EmptyStateView` (titolo e motivo, senza comando: la freccia della barra riporta allo storico). Nessun punto esclamativo.
- **Colori Street.** La Cronaca non ne cita piu' nessuno (layout, codice e grafico). Tolti da `colors.xml`: `concrete_gray`, `graffiti_dark_gray` e `outline_gray`, piu' il drawable `bg_asphalt_main`, che usava solo la Cronaca. **Restano** `asphalt_black`, `asphalt_dark`, `stencil_white` e `sidewalk_gray` perche' li cita ancora il PDF (G-8: `pdf_match_report.xml`, `MatchReportUtils.kt`; `asphalt_dark` anche lo sfondo dell'icona) e, per l'icona, `team_spray_yellow` e `team_electric_green` (G-9). Un test fa cadere la build il giorno in cui uno di loro non lo cita piu' nessuno.
- **Maiuscolo.** Nella Cronaca non ce n'e' piu' (conflitto 11 chiuso per questa schermata): titolo della schermata, intestazioni di sezione e «Interrotta».
- **Test.** `CronacaG5Test` (nuovo): i due layout senza maiuscolo, colori Street, card che non siano `Widget.App.Group` ne' ombra; il codice senza colori Street, `uppercase`, `isAllCaps` o colori scritti a mano; le stringhe `chronicle_*` non in maiuscolo in en e it e con le stesse chiavi; i tre colori usciti non esistono piu' e i quattro rimasti li cita il PDF e non la Cronaca. `MomentumViewTest` (nuovo, disegna su una bitmap vera): lato 1 lime sopra lo zero e lato 2 ciano sotto, lime e ciano a 3:1 sul gruppo, nome e vantaggio massimo di ogni lato, linea di fine set tratteggiata, la fascia dei nomi cresce al 200%. `ChronicleActivityTest` (nuovi casi): ogni testo con un numero e' `tnum` e nessuno e' in maiuscolo o fuori dai due livelli di testo; le sei sezioni sono gruppi senza ombra nell'ordine della dashboard; linee solo fra le righe e rientrate; ogni stato vuoto ha titolo e motivo; il grafico e' lime e ciano con i predefiniti e leggibile con gli altri colori; l'apice del tie-break e i nomi sul grafico. **Falsificazioni:** il controllo dei layout e del codice trova ogni difetto in un esempio sbagliato; un testo con un numero senza `tnum`, col grigio di prima o in maiuscolo viene trovato; coi colori scambiati il grafico non e' piu' lime sopra e ciano sotto; senza fine set non c'e' tratteggio; la riga di un gruppo non e' alta come una linea. Adattati senza cambiarne il significato: `ChronicleTextTest` (fondo del gruppo `#161618`, e lime e ciano restano i loro) e le frasi dei due vuoti e della legenda di `ChronicleActivityTest` (da una riga a titolo e motivo; legenda della barretta; la «B» sui game si legge ora da testi veri: «0-1 B», «1-1 B», «2-1»).
- **Non verificato.** Nessuno screenshot ne' emulatore: la barretta sinistra e destra dei riquadri dei game (`LayerDrawable` con gravita' e margini), le etichette del grafico sul carattere al 200% e la Cronaca a schermo stretto sono costruite per larghezza fluida e misurate in test, ma vanno guardate sul dispositivo. Non c'e' ancora un controllo del grafico per chi non distingue lime e ciano oltre alla posizione e al nome (c'e' entrambi, non e' stato provato con un simulatore di daltonismo). La Cronaca affiancata a quella della dashboard e' del proprietario.

#### G-6. Schermata di gioco (telefono): cifre in Inter, il punteggio che rotola, pallini lime, colori dei lati. FATTO (7 ottobre 2026).

Resta quasi tutto com'e' (nero puro, slot fissi, zone +, regola `TeamInk`); cambiano:

- **NumberRoll per il punteggio.** Il numero che cambia mostra la **direzione**: la vecchia cifra esce verso l'alto e la nuova entra dal basso quando il valore **sale**, il contrario quando **scende** (annulla, correzione -1), in `duration_standard` con `ease-standard`, solo `translationY` e `alpha` (specifica completa nel registro: Trigger il punteggio cambia di un passo; elemento che resta: la scatola della cifra e l'etichetta; interruzione: un cambio nuovo sostituisce subito quello in corso; scopo: far vedere che e come e' cambiato). **Scatola di larghezza fissa** (le cifre tabulari non spostano nulla attorno), un solo numero per lato, non si anima nient'altro. Il nuovo valore e' annunciato da una **live region** `polite` (`accessibilityLiveRegion`) con il nome della squadra, come gia' fa TalkBack ("ROSSI, 30"): l'animazione non e' mai l'unico segnale. **Movimento ridotto** (`ANIMATOR_DURATION_SCALE` a 0 o `Settings.Global`): la cifra si sostituisce con una breve dissolvenza, e il risultato e' identico. Ogni tocco aggiorna subito, anche a raffica. Sostituisce `animateScoreNumber` (zoom 1,06, senza direzione). Il cronometro non rotola: scorre da solo, non e' un valore che cambia di un passo.
- **Cifre.** `Game.Clock` e `Game.Value` passano a Inter 600 tabulare, con la misura rifatta sulla barra (`ColonnaDiGiocoTest`: 100:00 in 32sp). Se il corpo di "AV" (124,8dp a 411dp, vedi G-1b) risulta piccolo: spaziatura -0,02 em o Inter Display. Nomi e comandi restano per ora maiuscoli (conflitto 11).
- **Pallini del servizio lime** `#C8F135`: `bg_serve_dot` (e i due del padel) da bianco a `elite_lime`, 12dp; il rosa del conto del portiere che corre passa a lime (`MainActivity.kt`, `content_scoreboard_live.xml`); il lime non e' mai l'unico segno (il pallino sta accanto al nome e la barra dice anche "SERVE" a parole, come oggi per TalkBack). Contrasto 16,09:1 su nero.
- **Colori predefiniti dei lati** lime e ciano (G2) via `ColorRepository`; zone + col glifo `TeamInk` nero (>= 4,5). Le partite gia' salvate restano come sono.
- **Icone** `ic_play_arrow`, `ic_pause`, `ic_add`, `ic_undo`: gia' sostituite da G-3 (stessi simboli Material, a 24dp il play, la pausa e l'annulla; con `iconSize` esplicito; la zona + resta a 48dp e lo decide questo passo); **pressione** di G-2 sulle zone +; bersagli >= 48dp.

**FATTO (7 ottobre 2026, passo wf40). Come e' stato fatto.** Gioco e foglio PARTITA; nessuno screenshot, nessun emulatore (le prove a occhio e gli strumentati sono del proprietario).

- **Colori predefiniti.** `team_side_1` = lime `#C8F135` e `team_side_2` = ciano `#00E5FF` (ruoli, alias dei token `elite_lime` e `elite_cyan`), citati da `ColorRepository` (unica fonte dei predefiniti, anche per `MainViewModel`, `MatchRepository`, `MatchSettingsRepository`) e dai ripieghi dello storico, della Cronaca, del PDF e del selettore. Restano personalizzabili; la zona + prende il glifo `TeamInk` nero (lime e ciano su nero: 14,88 e 16,09; glifo nero sul lime 14,88). **Migrazione senza codice:** le preferenze salvano solo la scelta dell'utente (`saveTeamXColor`), mai il predefinito, quindi chi non ha mai scelto passa da solo a lime e ciano alla prima apertura, e chi ha scelto un colore, anche proprio il vecchio giallo `#FFD600` o verde `#76FF03`, lo tiene (la distinzione si fa per presenza della chiave, non per valore). Le partite salvate tengono il colore che avevano; quelle senza colore usano i nuovi predefiniti. `team_spray_yellow` e `team_electric_green` restano solo per l'icona dell'app (G-9).
- **Pallini del servizio.** `bg_serve_dot` e' `elite_lime`, 12dp (anche i due del padel); il conto del portiere che corre e' lime (era rosa). **Conflitto:** col predefinito la zona + della squadra 1 e il pallino hanno lo stesso lime; il pallino non si confonde perche' e' un cerchio da 12dp accanto al nome (forma e posizione), mai un riempimento, e la barra dice anche «SERVE <nome>» a parole.
- **NumberRoll.** `utils/NumberRoll.kt` (che sostituisce `AnimationUtils.kt`): la cifra vera e un fantasma della vecchia (nascosto a TalkBack) in `translationY` e `alpha`, `duration_standard` con `ease_standard` (nuovo `res/interpolator/ease_standard.xml`, `cubic-bezier(0.2, 0, 0, 1)`), corsa di meta' altezza della scatola (`fraction/number_roll_travel`). Su se sale (la vecchia esce in alto, la nuova arriva dal basso), giu' se scende. Interrompibile: un cambio nuovo ferma e toglie il fantasma e riparte dal nuovo. Movimento ridotto (`ANIMATOR_DURATION_SCALE` a 0): cambio immediato, nessun fantasma, nessuna traslazione. Il verso lo dichiara chi provoca il cambio (+ su un punto, - su correzione o annullamento) solo dentro quella chiamata; se il cambio viene da altrove (un punto dell'orologio) si ricava dai due punteggi (`NumberRoll.direzione`, con «AV» fra 40 e il game). Limite noto: un game chiuso dall'orologio (40 che torna a 0) rotola in giu'. Larghezza fissa: `fissaLaScatola` mette come larghezza minima quella del token piu' largo dello sport, a testo centrato, quindi fra «1» e «15» niente si sposta (`laZonaPiuNonSiSposta` resta valido: il layout non cambia). I numeri restano regioni live `polite` con nome e valore. Il cronometro non rotola. `animateScoreNumber` e `animateZoneTap` sono cancellate: la zona + ha lo `StateListAnimator` condiviso (0,97 e 0,85, solo opacita' col movimento ridotto) e niente increspatura; lo stesso su nomi, striscia, slot del portiere e comandi.
- **Cifre e testi.** `Game.Score`, `Game.Clock`, `Game.Value` in Inter 600 tabulare, `Game.Name` e `Game.Command` Inter 500, `Game.Caption` Inter 400: tre pesi. Via `textAllCaps` e spaziatura da titolo. I nomi delle squadre si scrivono come l'utente li ha scritti (`mostraNomeSquadra` non maiuscola piu'; la tastiera del dialogo e' `textCapWords` e le scelte rapide non si riscrivono in maiuscolo). **Misura delle cifre** (stessa regola di `dimensioneDelNumero`, col pennello vero, tetto 150dp): senza spaziatura a 411dp «88» 133,5dp e «AV» 124,8dp (cifra alta 90,7dp); con `letterSpacing` -0,02 em «88» 137,7dp e «AV» 128,5dp (cifra alta 93,4dp, +3%); a 360dp «88» 113,8 -> 117,5dp e «AV» 106,5 -> 109,6dp (tutti sopra il pavimento di 72dp, «88» e «AV» entrano nella mezza colonna). **Scelto -0,02 em sulle cifre giganti** (`Game.Score`). Il rimedio «token piu' largo per sport» non aggiunge niente: nel padel e nel tennis il token piu' largo che si mostra davvero e' proprio «AV» (le altre cifre sono tutte piu' strette), e nel calcio «88» e «10» misurano uguale; resta come ultimo rimedio Inter Display 4.1, non fatto. Le cifre del padel e del tennis restano circa il 10% piu' basse del mono di G-1 (105dp).
- **Foglio PARTITA.** Quattro gruppi tonali (rose, coppie, registro, formazioni: `Widget.App.Group`, senza ombra), intestazione di gruppo `Caption` e titolo `SectionHeading`; le righe delle rose sono righe di un gruppo (`team_player_item` non e' piu' una card e non ha l'avatar, con la pressione condivisa e l'anello di fuoco) separate da linee rientrate (`InsetDividerDecoration` anche sul registro). Il nome di ogni squadra nelle rose, nelle coppie e nelle formazioni e' testo primario con il colore **solo come barretta** di 4dp (`etichettaConBarretta`, `TeamInk.graphicOn` se non regge 3:1): via i blocchi pieni con il testo sopra. Formazione assente: «Rossi: nessun modulo» (era «ROSSI (nessun modulo)»); il campo delle formazioni non e' piu' verde ma `elite_background` con linee `elite_outline`. Avviso dell'orologio: `elite_error_subtle` con barretta `elite_error` e testo primario. Azioni: **FINE PARTITA e' l'unico primario**, a tutta larghezza; sotto, due file di secondari con la stessa misura (Storico, Condividi, Statistiche; Giocatori, Impostazioni, Azzera tempo: col cronometro spento la fila si divide in due). Foglio di fondo `elite_background` senza tinta e `elevation` 0 (lo separa lo scrim). Maiuscolo tolto da tutte le `label_*`, dalle didascalie «Game», «Set» e dal «Gestisci» (resta «CAMBIO» del portiere scaduto: e' lo stato d'allarme e si legge a due metri).
- **Schermata di gioco, colori.** Via ogni colore Street: testi `elite_text_primary` e `elite_text_secondary`, linee `elite_outline`, striscia in un gruppo tonale (`bg_strip`, `elite_surface`, raggio 14), zona spenta `elite_surface_raised` con linea `elite_border_strong`, slot del portiere scaduto `elite_error` con «CAMBIO» in `elite_background`, nero del gioco `ink_black` (nuovo, ruolo `background-watch`), badge dell'orologio `elite_error`. SCARTA e CONTINUA del dialogo di fine partita sono testo primario e solo SALVA e' lime (l'errore su rialzata fa 4,35:1, sotto il 4,5 per un testo di 14sp).
- **Alias Street.** Cancellati `Widget.App.Button.Street`, `.TextButton.Street`, `.OutlinedButton.Street`, `ShapeAppearance.App.StreetButton` e `StreetBadge`; cancellati anche `graffiti_pink`, `error_red`, `error_text` e `neon_cyan`. **Restano** in `colors.xml` `asphalt_black`, `asphalt_dark`, `concrete_gray`, `graffiti_dark_gray`, `stencil_white`, `sidewalk_gray`, `outline_gray`: li citano ancora la Cronaca (G-5: `ChronicleActivity`, `MomentumView`, `activity_chronicle`, `chronicle_section`) e il PDF (G-8: `pdf_match_report`, `MatchReportUtils`), e `team_spray_yellow` e `team_electric_green` per l'icona (G-9). I tre stili di testo `*.Street` (nomi vecchi di `DisplayLarge`, `HeadlineMedium`, `BodyLarge`) restano: non sono alias di G-2 e li citano tema e PDF.
- **Test.** `GiocoG6Test` (nuovo): predefiniti lime e ciano e la loro migrazione (chiave assente o presente, anche col vecchio giallo), zone lime e ciano con inchiostro nero, pallino lime e leggibile sul nero, NumberRoll (su e giu', interruzione, movimento ridotto, scatola di larghezza fissa, verso dai due punteggi con «AV», regioni live), nessun maiuscolo ne' colore Street ne' alias Street nei sei layout di gioco e foglio, stili `Game.*` senza maiuscolo e nei tre pesi, etichette del foglio non tutte maiuscole e nome squadra come scritto, la pressione condivisa sulle zone. **Falsificazioni:** il controllo dei layout trova ogni difetto in un esempio sbagliato; il pallino non e' piu' bianco; i predefiniti non sono giallo e verde; «1» e «15» misurano diverso senza la scatola fissa; invertire il verso scambia su e giu'. Adattati senza cambiarne il significato: `ColonnaDiGiocoTest` (zona spenta e stroke sui token), `StatoOrologioTest` (badge), `TemaDelTelefonoTest` (la riga della rosa non ha l'avatar), `PartitaARacchettaTest`, `ContrastiDelTelefonoTest`, `ContornoDelTelefonoG4Test`, `AccessibilitaDelTelefonoTest`, `TestiDelTelefonoTest`, `MainViewModelTest`, `TokenEliteTest`, `GiocatoriEStatisticheDelContornoTest`, e `MainActivityLayoutTest` (strumentato, solo compilato).
- **Resta fuori (dichiarato).** I messaggi composti della striscia, della barra, del dialogo di fine partita e del registro dei game («PUNTO ROSSI · 40-30», «VINCE ROSSI · 6-3», «GAME ROSSI · 2-3») sono ancora scritti in maiuscolo da `strings.xml` e da `maiuscolo()` (circa sessanta stringhe attese da `StrisciaTest`, `PartitaARacchettaTest`, `MatchLogAdapterTest`): conflitto 11 non ancora chiuso del tutto, da decidere con G-9. Il quadrante e il PDF restano a G-7 e G-8, la Cronaca a G-5. Non verificato a occhio: la barretta delle etichette, la zona spenta con la sua linea, il rotolo a 60fps sul telefono vero, il carattere al 200% nel foglio.
- **Costo:** medio
- **File:** `mobile/src/main/res/values/themes.xml` (`Game.*`), `colors.xml` (`team_*`), `mobile/.../repository/ColorRepository.kt`, `MainViewModel.kt` (predefiniti), `MainActivity.kt` (portiere, pallini, `animateScoreNumber`), `utils/AnimationUtils.kt` (`NumberRoll`), `content_scoreboard_live.xml`, `drawable/bg_serve_dot.xml`, `ic_play.xml`, `ic_pause.xml`, `ic_plus.xml`, `ic_undo.xml`; test `ColonnaDiGiocoTest`, `ContrastiDelTelefonoTest`, `CaratteriDelTelefonoTest`
- **Verifica:** test che il rotolo va in su quando il punteggio sale e in giu' quando scende (falsificato invertendo il segno), che la scatola non cambia di larghezza fra "1" e "15", che a movimento ridotto non c'e' traslazione, che la live region dice il nome e il valore; "88" e "AV" entrano ancora; pallino lime = `elite_lime` e contrasto >= 4,5 sul nero; `ColonnaDiGiocoTest` verde; screenshot a 412x923dp e al 200%.

#### G-7. Orologio: menu e selezione sport sui token, chi serve in lime, il punteggio che rotola. FATTO (7 ottobre 2026).

I token passano al quadrante (in `shared/src/main/res/values/colors.xml`, come gia' proposto in "Dove vivono i token": attenzione a `android.nonTransitiveRClass=true`); il tema dell'orologio smette di dire rosa e ciano. Menu e selezione sport: **gruppo tonale** su `elite_surface_raised` con righe sottili e raggi di Padel Elite (non piu' `cut`), lime per l'azione, **un primario per schermata**, bersagli >= 48dp (`control-touch`). Quadrante: nero puro, cifre bianche **condensate** (misura di G-1b: Inter non entra), strisce di squadra, `TeamInk`. In piu':

- **Chi serve e' lime** `#C8F135` (decisione 2): il pallino sul lato di chi serve (`bg_serving_dot`), 8dp, 16,09:1 sul nero; **in ambient nessun colore**: il pallino non si accende lime ma esce dal quadrante o diventa il solo contorno sottile bianco, nessuna fascia colorata, nessun movimento, aggiornamento al massimo una volta al minuto o a ogni cambio di punto (`wearable.md`). Il lime non e' mai l'unico segno (il lato e' anche detto dal gesto e dall'etichetta di TalkBack).
- **NumberRoll sulle cifre** (come G-6, con corsa piu' breve: la meta', `duration_fast` con la molla `control`, mai `expressive`): su si' quando sale, giu' quando scende; **niente al risveglio** e niente in ambient; fallback senza movimento; live region polite per le cifre, con `team1Name` e `team2Name`. Ogni tocco di punteggio ha comunque la vibrazione per lato (1 impulso a sinistra, 2 a destra), che resta il segnale primario.
- **Tondo e quadrato** con la stessa gerarchia (`WatchLayouts`): valore focale al centro nell'area inscritta (circa il 71% del diametro), un'azione, al massimo due valori di supporto; il tondo prima, il quadrato poi, mai il contrario; misure in proporzione. Si rimisurano 192dp e 227dp e un quadrato.
**FATTO (7 ottobre 2026, passo wf42). Come e' stato fatto.** Solo l'orologio; nessuno screenshot, nessun emulatore (il quadrante a occhio sui tre AVD e' del proprietario). Protocollo, coda e logica del ViewModel non toccati.

- **Token.** `wear/src/main/res/values/colors.xml` ha gli stessi nomi e valori del telefono per i ruoli che usa (`ink_black` canvas, `elite_surface` gruppo, `elite_border_strong` linea, `elite_text_primary`, `elite_text_secondary`, `elite_text_disabled`, `elite_lime`, `elite_on_lime`, `elite_cyan`, `elite_warning`, `elite_error`, `team_side_1`, `team_side_2`) e `TokenDelPolsoTest` li confronta file contro file con quelli del telefono (anche `duration_instant` e `duration_fast`). **Via i nove colori Street** (`asphalt_dark`, `concrete_gray`, `stencil_white`, `sidewalk_gray`, `graffiti_pink`, `neon_cyan`, `team_spray_yellow`, `team_electric_green`, `error_red`) e anche `error_text` e `signal_amber`; nessun layout, disegno, tema o file Kotlin li cita (il test lo controlla e si falsifica su un esempio sbagliato). Restano `ambient_gray` (grigio senza tinta: in ambient niente colore, e il vecchio test chiede R=G=B) e tre colori `launcher_*` che sono i valori dell'icona di prima, rinominati perche' l'icona non e' un ruolo e la rifa G-9. Il tema (`theme.xml`) mappa primario lime, secondario neutro, errore `elite_error`, fondo e finestra `ink_black` (il menu e la scelta dello sport non dipingono piu' un secondo fondo grigio), superficie e testi sui token; via `ShapeAppearance.App.StreetCard`, gli stili `*.Street` (ora `TextAppearance.App.HeadlineMedium/HeadlineSmall/BodyLarge`) e `textAllCaps`; forme `ShapeAppearance.App.Control` (8) e `Surface` (14), tutte `rounded`.
- **Menu e scelta dello sport: gruppi tonali con righe.** `menu_voci` e la lista degli sport hanno il fondo `bg_group` (surface, raggio 14, senza ombra, ritagliato) e la linea sottile `bg_group_divider` fra le righe (`showDividers` nel menu, `DividerItemDecoration` nella lista). Le righe sono `Widget.App.GroupRow` (stesso tono del gruppo, raggio 8, nessuna ombra, niente increspatura, minimo 52dp); solo la voce armata (FINE PARTITA e SCARTA LA CODA al primo tocco) si riempie di `elite_error` con testo nero (5,5:1). **Conflitto con il testo del passo:** G-7 diceva «su `elite_surface_raised`», ma l'errore su rialzata fa 4,35:1 e il titolo scende a 14sp (non e' testo grande): si e' scelto `elite_surface`, come il gruppo del telefono (errore 4,73:1, testo primario 11,9, secondario 5,3). Voce spenta: titolo in `elite_text_disabled` (la Constitution lo ammette per le etichette spente), sottotitolo del perche' in `elite_text_secondary` (o ambra `elite_warning` se parla di punti non consegnati); non c'e' piu' la meta' opacita' sul fondo. «In uso» e la spunta nella scelta dello sport passano da ciano a `elite_text_primary`: il ciano sull'orologio resta solo il predefinito del lato 2.
- **Maiuscolo.** Tolto da menu e scelta dello sport sia come `textAllCaps` sia nelle stringhe (it e en): «Fine partita», «Chiudere 3-2?», «Scarta la coda», «Scartare?», «Partita», «Sport». **Restano in maiuscolo** la riga di stato E («OFFLINE», «NON CONFERMATO», «TIENI: -1», ...: i test di larghezza e `StatoFiducia` la vogliono cosi'; si leggono di sfuggita e a due metri) e le frasi in gioco del quadrante («CHI?», «SALTA», «GOL», «CHI HA SEGNATO?», «PARTITA FINITA»), che G-7 non chiedeva di toccare.
- **Pressione condivisa.** `animator/press_feedback.xml` e `press_feedback_reduced.xml` sono copie di quelli del telefono (scala 0,97 e opacita' 0,85 in `duration_instant`; col movimento ridotto la sola opacita'), citati dall'attributo `pressFeedback` del tema; `MovimentoRidotto.applica` sovrappone `ThemeOverlay.App.MovimentoRidotto` quando `ANIMATOR_DURATION_SCALE` e' 0, chiamato da ogni schermata prima di gonfiare il layout (l'orologio non ha una classe `Application`). La usano le righe del menu e della scelta dello sport e le righe del marcatore. **Non la usano** il bersaglio del menu in basso e i due lati del quadrante: non hanno niente di visibile da scalare (il bersaglio inferiore tiene il suo `selectableItemBackgroundBorderless`; il segnale primario dei lati e' la vibrazione).
- **Pallino del servizio lime** (`bg_serving_dot`, `elite_lime`, 16,09:1 su nero), uno o due come prima. **In ambient** `MainActivity.disegnaPallini` lo sostituisce con `bg_serving_dot_ambient`: solo contorno bianco da 1dp, nessun riempimento, stessa misura; all'uscita torna lime. Il lime non e' mai l'unico segno (la descrizione del lato dice chi serve). **Dove il lime non c'e':** secondo l'identita' dell'orologio («il lime marca solo chi serve») il conto del portiere che corre passa da rosa a `elite_text_primary` (sul telefono G-6 l'aveva fatto lime), il suo anello a `elite_text_secondary` e il bordo di CHI? a `elite_text_primary` con raggio 8; il portiere scaduto e' `elite_error`, fermo `elite_text_secondary`. Da confermare con il proprietario se vuole il lime anche li.
- **Predefiniti delle squadre.** Le strisce partono `team_side_1` (lime) e `team_side_2` (ciano) e lo stesso il marcatore (`PlayerSelectionActivity`, `apriMarcatore`); quando il telefono manda i suoi colori restano quelli, portati a 3:1 da `TeamInk.graphicOnBlack` come prima.
- **NumberRoll sulle cifre** (`wear/.../NumberRoll.kt`, specifica nel commento): `translationY` e `alpha` della cifra e di un fantasma della vecchia (nascosto a TalkBack), su se sale e giu' se scende (`NumberRoll.direzione` sui due valori stampati, «AV» fra 40 e il game), **corsa di un quarto dell'altezza della scatola** (`number_roll_travel` 25%, la meta' del telefono), `duration_fast` (160ms) con `ease_standard`; **nessuna molla**: la corsa e' un tween breve su opacita' e traslazione e non c'e' posizione da far assestare (la `control` di `motion.md` serve a posizioni e contenitori). Interrompibile (un cambio nuovo ferma e toglie il fantasma), a raffica ogni tocco aggiorna subito. Il fantasma ha gli stessi vincoli della cifra (non il costruttore di copia di `ConstraintLayout.LayoutParams`: condivide il `ConstraintWidget` e la cifra restava a larghezza 0, trovato dal test). **Scatola di larghezza fissa:** le cifre stanno nelle colonne fisse da 68dp (81dp sul 227dp), quindi `team1Score` e `team2Score` non cambiano larghezza con nessun valore (test con «0», «15», «30», «40», «AV»). Live region `polite` gia' presente sulle cifre, descrizione del lato con nome e valore. **Mai rotolo** in ambient (un rotolo in corso si ferma entrando), con `ANIMATOR_DURATION_SCALE` a 0 (cambio immediato), **nei 500ms dopo il risveglio e l'apertura** (`rotoloVietatoFinoA`: si apre in `onStart`, prima che i collector ridisegnino, e con la guardia in `onResume` e all'uscita dall'ambient, che ora precede il ridisegno). Le cifre restano condensate (`CifreDelQuadranteTest` verde: Inter non entra, il condensato si). Limiti noti: un game chiuso (40 che torna a 0) rotola in giu', come sul telefono; un punteggio ripristinato dal Data Layer dopo piu' di 500ms dall'apertura rotola una volta da 0.
- **Tondo e quadrato.** La gerarchia del quadrante non e' cambiata (valore focale al centro, un'azione, al massimo due valori di supporto: cifre, fascia A, menu): le guide in percentuale e i margini di corda restano, e i test di fasce e di larghezza (`QuadranteFasceTest`, `RigaStatoLarghezzaTest`, `MenuPiegaTest`, `TitoliIntegriTest`, `CifreDelQuadranteTest`) sono verdi senza toccare le misure. Non rimisurato a occhio su 192dp, 227dp e quadrato.
- **Test.** Nuovi: `OrologioG7Test` (pallino lime uno e due, senza colore in ambient e di nuovo lime all'uscita, predefiniti lime e ciano con il colore del telefono che vince, NumberRoll su e giu' a meta' corsa, un cambio che sostituisce il precedente, scatola fissa, movimento ridotto, ambient, risveglio e apertura, regioni live); `TokenDelPolsoTest` (token uguali al telefono, nessun colore vecchio, nessuna forma `cut`, nessun maiuscolo e al massimo tre pesi, pressione uguale a quella del telefono); `MenuG7Test` (gruppo con linea, righe a raggio 8 senza ombra, pressione intera e ridotta). **Falsificazioni:** il controllo dei colori vecchi trova un esempio sbagliato, il disegno del pallino in ambient senza `<solid>` e senza lime, il verso invertito fa partire la cifra dall'altra parte. Adattati senza cambiarne il significato (nomi dei colori e stringhe in frase): `AmbientTest`, `MainActivityTest`, `MenuActivityTest`, `SelezioneSportTest`, `TitoliIntegriTest`.
- **Costo:** medio
- **File:** `wear/src/main/res/values/colors.xml` (-> token condivisi), `theme.xml`, `layout/item_sport_wear.xml`, `activity_menu.xml`, `activity_sport_selection.xml`, `activity_player_selection.xml`, `item_player_wear.xml`, `activity_main.xml`, `drawable/bg_serving_dot.xml`, `bg_chi_capsule.xml`, `wear/.../MainActivity.kt` (ambient e rotolo), `MenuActivity.kt`, `MenuVoci.kt`, `SportSelectionActivity.kt`, `PlayerSelectionActivity.kt`; test `MenuActivityTest`, `SelezioneSportTest`, `MainActivityTest`, `AmbientTest`, `CifreDelQuadranteTest`
- **Verifica:** `./gradlew :wear:test`; `CifreDelQuadranteTest` verde (Inter non entra, il condensato si); tre AVD (192dp, 227dp, quadrato); lo stesso token ha lo stesso valore sui due lati (test che confronta i due `R.color`); test che in ambient il pallino non ha il lime e non c'e' animazione; che l'animazione del rotolo non parte al risveglio; che il rotolo sale e scende.

#### G-8. PDF del report con gli stessi token e caratteri. FATTO (7 ottobre 2026).

Il report e' una vista gonfiata e disegnata su un PDF: oggi `asphalt_dark` di fondo, `stencil_white` e `sidewalk_gray` per il testo, bande nel colore di squadra con testo `TeamInk`. Passa ai token e ai caratteri di G-1b (Inter 400, 500, 600, cifre tabulari). La pagina e' scura su carta; se si sceglie la carta bianca (da valutare per la stampa) il lime NON e' leggibile come testo (1,31:1 sul bianco) e va solo come banda con `elite_on_lime`. Gruppi con righe sottili, nessuna ombra.
**FATTO (7 ottobre 2026, passo wf43). Come e' stato fatto.** Solo il PDF; nessun PDF aperto a mano, nessun emulatore (la pagina a occhio e la stampa su carta sono del proprietario).

- **Il fondo: carta CHIARA, non scura (conflitto 14).** Il PDF e' l'unica cosa che esce dal telefono e si stampa. Il fondo scuro `#0D0D0F` reggerebbe il contrasto (testo primario 12,78:1), ma in stampa no: una pagina nera consuma l'inchiostro, il testo chiaro a 14pt su fondo pieno si impasta nelle stampanti laser e a getto, e la pagina non e' nemmeno piena (`view.draw` disegna solo l'altezza del contenuto; il resto dei 842 punti e' carta bianca, quindi mezza pagina scura e mezza no). Scelta: gli stessi ruoli della Constitution nei valori chiari, token `print_*` in `colors.xml`: `print_background` `#FFFFFF` (canvas), `print_surface` `#F4F5F7` (il gruppo), `print_border` `#D0D3D9` (linea sottile, decorativa), `print_text_primary` `#1A1C1F` (17,08:1 sulla carta, 15,65:1 sul gruppo), `print_text_secondary` `#5A5F66` (6,43:1 e 5,90:1). Il lime non e' mai testo sulla carta (1,31:1): e' solo barretta.
- **Colori dei lati = quelli della partita, come nella Cronaca.** Il colore con cui si e' giocato; con i predefiniti `team_side_1` e `team_side_2`, cioe' lime e ciano (come la Cronaca e la dashboard); se la squadra non c'e' piu' ripiegano sui predefiniti. Sono **sempre e solo grafica**: una barretta di 4dp sul bordo iniziale del nome (`etichettaConBarretta`, la stessa del foglio PARTITA e della Cronaca, ora con `testo` e `sfondoChiaro`). Non c'e' piu' il blocco pieno con il testo sopra: nome e punteggio sono `print_text_primary`, quindi la leggibilita' non dipende dal colore scelto (con qualunque colore, bianco e nero compresi, il testo fa 4,5:1 o piu' sulla carta e sul gruppo). **Su carta chiara la barretta si scurisce, non si schiarisce:** nuova `TeamInk.graphicOnLight(colore, sfondo, min = 3)` (in `:core`, accanto a `graphicOn`), che mescola verso il nero al passo di 0,01 finche' il colore arriva a 3:1 sul gruppo `#F4F5F7`. Chi ha gia' 3:1 resta com'e' (il blu notte `#1A237E` fa 12,14:1); il lime `#C8F135` (1,20:1) diventa `#7E9821` (3,01:1) e il ciano `#00E5FF` (1,41:1) `#009CAD` (3,03:1): restano verde e azzurro, scende solo la luminanza. Nelle formazioni la stessa barretta, col nome della squadra in testo secondario sopra la colonna: il colore non e' mai l'unico segno (prima le due colonne non dicevano di chi fossero).
- **Gruppi tonali, righe sottili.** Il risultato (una riga per squadra, il punteggio a destra), le formazioni (due colonne) e il tabellino sono ciascuno un gruppo (`bg_print_group`: `print_surface`, bordo 1dp, raggio 14, nessuna ombra); le righe si separano con `print_divider` (1dp, `print_border`) col `showDividers` del `LinearLayout`, non con riquadri. Titolo della pagina in `headlineLarge` 600, intestazioni in `SectionHeading` 600, righe in `BodyMedium` 400, didascalie in `Caption` 400. Pesi usati: 400 e 600. Nessun maiuscolo: `report_pdf_title` e `report_pdf_scorers_title` passano da «MATCH REPORT»/«REPORT PARTITA» e «SCORERS»/«TABELLINO MARCATORI» a «Match report»/«Report partita» e «Scorers»/«Tabellino marcatori». Tutti i testi hanno `fontFeatureSettings` `tnum`, il punteggio e' `TextAppearance.App.Punteggio` a 32sp. Stato vuoto nuovo: una squadra senza giocatori dice «No players»/«Nessun giocatore» in testo secondario (`report_pdf_no_players`), invece di una colonna bianca.
- **La pagina era dipinta alla densita' del telefono: ora no.** Difetto trovato per strada. La pagina e' larga 595 punti e `view.draw` disegna un pixel per punto, ma il layout si gonfiava col contesto dell'attivita': su un Pixel (densita' 2,6) i 16sp diventavano 42 punti e un titolo da 57sp misurava 150, con circa venti caratteri per riga. `contestoDellaPagina` gonfia con densita' 160 e carattere al 100%: un dp e' un punto, i corpi sono quelli della scala e il carattere ingrandito del telefono non cambia un foglio che si stampa. Un report di calcio undici contro undici sta in un A4 (altezza misurata in test).
- **Regole che restano.** Formazioni e tabellino non compaiono dove non si attribuisce il marcatore (padel, tennis: `attributesScorer` falso): nessun TABELLINO senza marcatore, e contano solo le marcature con `playerId`. Scrittura con `use`, nessun `Intent` se la scrittura fallisce.
- **Colori Street.** Tolti da `colors.xml`: `asphalt_black`, `stencil_white` e `sidewalk_gray`, che citava solo il PDF. **Resta** `asphalt_dark` perche' lo cita lo sfondo dell'icona (`ic_launcher_background_vs.xml`, G-9), con `team_spray_yellow` e `team_electric_green`. `CronacaG5Test` ora vuole che `asphalt_dark` sia citato solo da quel file e che gli altri tre non esistano. Gli stili `*.Street` restano perche' il tema li cita (`textAppearanceDisplayLarge`, `HeadlineMedium`, `BodyLarge`): il PDF non ne usa piu' nessuno.
- **Test.** `ReportPdfG8Test` (nuovo): nessun colore Street, colore scritto a mano, maiuscolo o ombra nei file del PDF (con la falsificazione di ciascun difetto e di un commento che li nomina); stringhe `report_pdf_*` non in maiuscolo in en e it; `asphalt_black`, `stencil_white` e `sidewalk_gray` non esistono piu'; ogni testo e' `tnum` (e "11" e "88" misurano uguale, mentre senza la feature no); pesi fra 400, 500 e 600 e Inter e non un altro carattere (larghezza contro il file); colori dei lati come la partita, lime e ciano coi predefiniti, schiariti dal solo `graphicOn` il giallo non arriverebbe a 3:1; squadra come barretta (la riga non ha sfondo, lo strato e' largo 4); testo a 4,5:1 sulla carta e sul gruppo con cinque colori di squadra diversi, e il testo del tema scuro e il lime come testo su carta che non passano; pagina senza card ne' ombre; densita' 1 e carattere 100% anche su un contesto a xxhdpi; undici contro undici in un A4; stato vuoto; tabellino. `GiocatoriEStatisticheDelContornoTest`: i due test del PDF hanno lo stesso scopo (nome e punteggio leggibili con qualunque colore, ripiego sui predefiniti) e ora guardano la barretta. `MatchReportUtilsTest` non e' cambiato. `TeamInkTest`: `graphicOnLight` da' 3:1 su tutto l'RGB e scurisce, e `graphicOn` sul bianco non ci arriva (falsificazione).
- **Non verificato.** Nessun PDF aperto: il risultato a occhio, la stampa in bianco e nero (le barrette di lime e ciano scurite si distinguono per posizione e per il nome accanto, non per il tono) e il foglio unico per le partite con piu' di circa quindici giocatori per lato (il PDF e' ancora una pagina sola e il resto sarebbe tagliato: fuori da G-8).
- **Costo:** piccolo
- **File:** `mobile/src/main/res/layout/pdf_match_report.xml`, `mobile/src/main/res/drawable/bg_print_group.xml`, `print_divider.xml`, `mobile/.../utils/MatchReportUtils.kt`, `TeamColorViews.kt`, `core/.../TeamInk.kt`, `mobile/src/main/res/values/colors.xml`, `strings.xml` (en e it), `ReportPdfG8Test.kt`, `GiocatoriEStatisticheDelContornoTest.kt`, `CronacaG5Test.kt`, `TeamInkTest.kt`
- **Verifica:** `./gradlew test ktlintCheck lintDebug assembleDebug`; `ReportPdfG8Test`, `MatchReportUtilsTest` e `TeamInkTest` verdi; PDF di una partita di padel e di una di calcio aperti a mano (resta al proprietario); contrasto del testo >= 4,5.

#### G-9. Chiusura: maiuscolo dei messaggi, icona, pulizia e controllo finale. FATTO (7 ottobre 2026).

Schermate dell'app accanto alle pagine della dashboard (storico, Cronaca, tabellone); tre AVD dell'orologio; carattere al 200% sul telefono; passata dei contrasti con `TokenEliteTest` e a occhio; decisione G5 (nome e icona) presa e applicata; `MIGRATION_PLAN.md` aggiornato; `CHANGELOG.md`, `STORE_LISTING.md` e le schermate della scheda Play se cambiano nome o icona. **Piu' i controlli di `review.md`** della Constitution: le **otto porte di qualita'** (compito e punto focale; ogni contenitore ha una ragione; larghezze strette e larghe, 200% e testo lungo senza salti; colori semantici, accento sobrio, raggi e ombre per ruolo; icone di una famiglia, comandi di sola icona con nome; ogni stato e ogni animazione con la sua ragione e il suo fallback; tastiera, fuoco, contrasto; riconoscibile senza il logo) e la **lista dei pattern vietati** (griglie di card uguali; sfumature e vetro; pillole ovunque; icone in quadrati colorati; emoji; maiuscolo ovunque; ombre sulle superfici ferme; entrate a scaglioni; effetti che spostano il layout; grigio su grigio), con il rapporto finale del lavoro (che cosa e' cambiato, componenti e token riusati, stati, movimenti, movimento ridotto, accessibilita', rischi). Le divergenze sono confrontate con la tabella dei **conflitti dichiarati**.
- **Costo:** medio
- **File:** `MIGRATION_PLAN.md`, `CHANGELOG.md`, `STORE_LISTING.md`, `mobile/src/main/res/mipmap-anydpi-v26/ic_launcher*.xml`, `drawable/ic_launcher_*_vs.xml` (e i tre di `wear`), `strings.xml` e `values-it/strings.xml` (solo se G5 cambia il nome)
- **Verifica:** `./gradlew test ktlintCheck lintDebug assembleDebug`; i vecchi nomi del blocco "STREET" spariti o giustificati; la lista dei pattern vietati controllata schermata per schermata e firmata; confronto visivo firmato dal proprietario.

**FATTO (7 ottobre 2026, passo wf45). Come e' stato fatto.** Senza screenshot ne' emulatore: il confronto a occhio (app e dashboard affiancate, i tre AVD dell'orologio, il carattere al 200% sul dispositivo, l'icona) resta del proprietario. Il controllo di `review.md` qui sotto e' fatto leggendo i layout, con `grep` sui sorgenti e con i test, e dice che cosa e' stato provato come.

- **Maiuscolo dei messaggi composti (conflitto 11 chiuso).** Tolti `maiuscolo()` e `uppercase()` da `StatoStriscia.kt`, `PartitaARacchetta.kt`, `TestiDelGame.kt` e `MainActivity.kt` (correzione e «Gol di»): i nomi delle squadre e dei marcatori entrano nei messaggi come l'utente li ha scritti. `strings.xml` (en e it) in frase: striscia, messaggi di 3 secondi, plurali «N punti dall'orologio», titolo e barra di fine partita, registro dei game («Game Rossi · 2-3», «Tenuto», «Break», «Set Rossi · 7-6», «Partita Rossi»), «Gol! ...» del registro. **Resta maiuscolo «CAMBIO»/«CHANGE»** del portiere scaduto, stato d'allarme. Sull'orologio in frase: riga di stato E (tutte le frasi di `StatoFiducia`), «Tieni: -1», «Tieni: annulla», «Chi?», «Chi ha segnato?», «Salta», «Gol · 3-2», «Partita finita» e la fascia D (`FaceText`: «Set 2 · 6-4», «Tie-break · 6-4»). In frase e' piu' stretto del maiuscolo, quindi `RigaStatoLarghezzaTest` (misura vera a 10sp nel tondo da 192dp), `StringheStatoTest` (18 caratteri) e le misure delle fasce passano senza toccare le misure: **non e' servito tenere il maiuscolo da nessuna parte**.
- **Test adattati senza cambiarne il significato** (circa sessanta attese): `StrisciaTest`, `PartitaARacchettaTest`, `MatchLogAdapterTest`, `StatoOrologioTest` sul telefono; `StringheStatoTest`, `MainActivityTest`, `FaceTextTest`, `QuadranteFasceTest` sull'orologio.
- **Conto del portiere che corre: testo primario.** Sul telefono era lime (G-6), come ora sull'orologio e' testo primario: il lime marca solo chi serve. Tre stati, tre segni: fermo `elite_text_secondary`, in corso `elite_text_primary`, scaduto pieno `elite_error` con la parola «CAMBIO».
- **Icona (G5, proposta da confermare).** Vedi la riga G5 della pista Padel Elite. `ic_launcher_background_vs` e' `elite_background_standard`, `ic_launcher_foreground_vs` ha la colonna avanti `elite_lime` e l'altra `elite_text_primary_standard` (colori pieni: il launcher non ha il tema dell'app, quindi non si citano i token che seguono il tema), `ic_launcher_monochrome_vs` usa `ink_white` (conta solo l'alfa). Sull'orologio lo stesso con `elite_background` (nuovo in `wear/.../colors.xml`, `TokenDelPolsoTest` lo confronta col telefono) e senza i tre `launcher_*`. Usciti da `colors.xml`: `asphalt_dark`, `team_spray_yellow`, `team_electric_green`; **non c'e' piu' nessun colore Street**. I tre stili `TextAppearance.App.{DisplayLarge,HeadlineMedium,BodyLarge}.Street` hanno perso il suffisso (il tema li cita ancora, con gli stessi valori). Cancellati anche i PNG legacy dei mipmap di telefono e orologio (`minSdk` 30: le icone adattive coprono ogni dispositivo e nessun file li citava); con loro escono 17 voci della baseline di lint per modulo. Nome visibile invariato («Scoreboard Essential»): `STORE_LISTING.md` e `CHANGELOG.md` non cambiano; le schermate della scheda Play con l'icona vecchia si rifanno quando il proprietario conferma.
- **Pulizia.** Baseline di lint ripulita **voce per voce** (come vuole la regola dei Rischi: non rigenerata): tolte 32 voci del telefono e 25 dell'orologio che lint non riporta piu' (icone PNG, `SetTextI18n`, `HardcodedText`, `ContentDescription`, `Overdraw`, `UnusedResources`, `MissingQuantity`), nessuna voce aggiunta. `UseKtx` in `InvioStore`, `SessionStore` (`edit { }`) e `PadelEliteActivity` (`isNotEmpty`); `cd_edit_player` cancellata (e anche `label_open_match_sheet`, vedi sotto); italiano dell'orologio con il plurale `many`; `PluralsCandidate` ignorato sulle quattro frasi di stato dell'orologio (limite sotto).
- **Difetti piccoli trovati dal controllo e corretti.** Il pulsante del foglio PARTITA era il glifo di testo «≡», fuori dalla famiglia Material Symbols (G-3 dice una famiglia sola): ora e' `ic_menu` (concetto «Menu» della tabella G-3, che aveva `-` come drawable), solo icona con la descrizione a voce, 48dp; `ColonnaDiGiocoTest` adattato (il pulsante non ha testo e ha l'icona). Il campo delle formazioni aveva il raggio 20dp scritto a mano (`bg_football_field`): ora `radius_surface`.
- **Test.** `ChiusuraG9Test` (nuovo): nessun messaggio composto in maiuscolo in en e it (stringhe `strip_*`, `bar_*`, `log_*`, `end_match_over*`), l'unica parola in maiuscolo fra i comandi e' `label_keeper_change`, nessun `maiuscolo()` ne' `uppercase()` nel codice dei messaggi, striscia, barra e dialogo con nomi scritti come si vuole («Real Madrid», «aC mIlAn»), il conto che corre e' testo primario e non lime. **Falsificazioni:** il controllo vede «PUNTO %1$s · %2$s», «PARTITA FINITA · TERMINA ›» e «%1$d PUNTI DALL'OROLOGIO» e lascia stare le stesse in frase; vede `nomeSquadra1.uppercase(Locale.getDefault())` e `.maiuscolo()`; la riga di prima del portiere (`elite_lime`) non e' testo primario. `CronacaG5Test` (due test): nessun colore Street esiste ancora in `colors.xml` e nessun sorgente li cita, l'icona del telefono e' fatta di token pieni, con la falsificazione sui nomi vecchi.
- **Verifica:** `./gradlew test ktlintCheck lintDebug assembleDebug assembleDebugAndroidTest`: 1291 test JVM sulla variante debug (core 180, shared 34, telefono 685, orologio 392), 0 falliti, 1 saltato (il solo test che scrive il PDF e lo condivide, che vale solo dove il separatore e' quello di Android: saltato su Windows come prima); lo stesso su release; `ktlintCheck` pulito; `lintDebug` senza errori e con 4 avvisi vecchi fuori baseline (1 sul telefono, 3 sull'orologio, vedi i difetti), nessuno nuovo; `assembleDebug` e `assembleDebugAndroidTest` riusciti. Il giro e' a Gradle gia' avviato, senza `clean`.

##### Controllo finale (G-9): le otto porte e i pattern vietati di `review.md`, schermata per schermata

Metodo: lettura dei layout e dei sorgenti, `grep` su tutto `res/` e `java/` dei due moduli (ombre: solo `cardElevation` a 0dp negli stili; sfumature, vetro e sfocature: nessuna; colori scritti a mano: nessuno nei layout, nei disegni e nei colori di sistema; `textAllCaps`: nessuno; pesi: l'unico `textStyle="bold"` e' in `match_game_item.xml` e su Inter cade sul 600; emoji nell'interfaccia: nessuna, ci sono solo nei messaggi di `Log`; suggerimenti (tooltip): uno, sull'icona dell'orologio, che ha gia' la sua descrizione; movimento ripetuto: solo `ProgressButton`, che gira finche' c'e' un lavoro in corso), e i test dei passi G-1..G-10 (contrasti, carattere al 130% e a larghezze strette, stati, movimento ridotto). Nessuno screenshot. Esiti: **ok** (nessun difetto trovato), **ok, riserva** (passa, con un limite scritto), **corretto** (difetto trovato e sistemato in G-9).

| Schermata | Compito e punto focale | Contenitori e raggi | Stati e movimento | Icone, caratteri, colori | Pattern vietati | Esito |
|---|---|---|---|---|---|---|
| Telefono, gioco (calcio, padel, tennis) | segnare; un numero per lato su nero puro | le sole card sono i comandi indipendenti (zone +, slot del portiere, tempo); nessuna ombra | normale, premuto (0,97 e 0,85), disattivo a partita finita, scaduto, solo lettura; il numero rotola solo fra due valori, movimento ridotto = cambio immediato | `ic_*` Material, comandi di sola icona con descrizione, cifre Inter `tnum`; il lime solo per chi serve | nessuno; maiuscolo solo «CAMBIO» | corretto: pulsante del foglio da «≡» a `ic_menu` |
| Telefono, foglio PARTITA | consultare e chiudere: un solo primario (Fine partita) | quattro gruppi tonali con righe e linee rientrate | normale, premuto, fuoco, vuoto, errore dell'orologio | etichette in frase, colore di squadra solo barretta | nessuno | ok |
| Telefono, dialoghi (fine partita, annulla, nome, colore, marcatore, ruolo, giocatore) | una decisione per dialogo; un primario | fondo `background-elevated`, raggio 14, nessuna tinta di elevazione | normale, premuto, errore del campo, disattivo | titoli e messaggi in frase («Partita finita», «Vince Rossi · 6-3») | nessuno | ok |
| Telefono, storico | ritrovare una partita; il punteggio con le due squadre | una card per partita: si espande, e' indipendente (G-4); nessuna ombra | normale, selezionata (indicatore lime), espansa, vuoto, filtro senza risultati, invio in corso o in errore | freccia `expand_more`, testi su due livelli | elenco verticale di card dello stesso tipo, non griglia; non ripete icona, etichetta, numero e tendenza | ok, riserva: a occhio con molte partite le card si ripetono; il confronto con la dashboard e' del proprietario |
| Telefono, Cronaca | capire com'e' andata; il tabellone dei set | sei gruppi tonali, nessuna card | normale, vuoto con titolo e motivo | lati lime e ciano con nome e posizione, grafico a una domanda, etichette dirette | nessuno (nessun grafico decorativo) | ok |
| Telefono, statistiche | chi segna di piu'; nome e valore | un gruppo con righe sottili | normale, vuoto, in corso | podio e numeri lime | nessuno | ok |
| Telefono, giocatori, aggiungi e modifica | gestire la rosa; nome e ruolo | un gruppo; campi con raggio 8 | normale, fuoco lime, errore sotto il campo, in corso (`ProgressButton`) | `ic_person_add`, `ic_edit`, `ic_delete` con descrizione | nessuno | ok |
| Telefono, impostazioni (partita, accessibilita', Padel Elite) | una sezione per volta | gruppi tonali; l'interruttore del tema ad alto contrasto spiega lo stato | normale, fuoco, disattivo, interruttore con nome | `MaterialSwitch` col nome per TalkBack | nessuno | ok |
| Telefono, Padel Elite (accesso, gruppi, invio) | accedere e scegliere il gruppo | gruppi tonali | normale, in corso, errore, in coda, in attesa dell'admin | `ic_lock`, `ic_send`, `ic_schedule`, `ic_hourglass_empty` | nessuno | ok |
| Telefono, onboarding | un passo per volta | nessuna card | normale, ultimo passo | illustrazioni a `icon_prominent`, non decorative | nessuna illustrazione a caso | ok |
| Telefono, report PDF | un foglio da stampare | gruppi tonali su carta chiara (conflitto 14) | stato vuoto «Nessun giocatore» | Inter 400 e 600, colore di squadra solo barretta | nessuno | ok |
| Telefono, notifica del cronometro | dire che il tempo corre | notifica di sistema | corre, in pausa | icone di sistema (`android.R.drawable.*`), non della famiglia | un'altra famiglia di icone | ok, riserva: dichiarato in G-3 («Non fatto»), serve una variante senza attributi del tema |
| Orologio, quadrante (tondo 192dp, 227dp, quadrato) | leggere il punteggio in mezzo secondo | nessuna card; strisce di squadra | normale, ambient (solo cifre sottili, niente colore), fine partita, scollegato, non confermato; il numero rotola solo fra due valori | cifre condensate, pallino lime solo per chi serve, riga di stato in frase | nessuno | ok, riserva: «1 rifiutati», «1 in coda» in italiano al singolare (le quattro frasi di stato non sono plurali) |
| Orologio, menu partita | chiudere o scartare, un'azione armata alla volta | un gruppo tonale con righe a raggio 8 | normale, premuto, armato (`elite_error`), spento con il perche' | `ic_more_horiz`, frasi in frase | nessuno | ok, riserva: `clipToOutline` vale da API 31 e `minSdk` e' 30, quindi su Wear OS 3 gli angoli del gruppo non si ritagliano (le righe hanno lo stesso tono, non si vede) |
| Orologio, selezione sport | scegliere lo sport fuori partita | un gruppo con righe | normale, in uso (spunta), bloccato a partita in corso | spunta `ic_check` | nessuno | ok, riserva: lo stesso `clipToOutline` |
| Orologio, selezione marcatore | dire chi ha segnato in 8 secondi | lista su nero con «Salta» in cima | normale, vuoto («Nessun giocatore»), guardia dei primi 400ms | frasi in frase | nessuno | ok |

**Difetti rimasti (nessuno grosso).**
1. **`ic_menu` e' scritto a mano**, non scaricato dal catalogo come le altre (non c'era rete): la geometria e' quella di `menu` (tre barre da 80), da sostituire col file ufficiale alla prima occasione (G-3 chiede file scaricati, non ridisegnati).
2. **Icone di notifica di sistema** del servizio del cronometro (G-3, «Non fatto»).
3. **Singolare dell'italiano sull'orologio:** «1 rifiutati», «1 consegnati», «1 non consegnati», «1 in coda» (erano gia' cosi' in maiuscolo). Servono quattro plurali e il codice di `Frase.testo`; il lint e' zittito con `tools:ignore="PluralsCandidate"` e il limite sta qui.
4. **`clipToOutline` a API 30** sui due gruppi dell'orologio (`WearBackNavigation` su `theme.xml:79` e `PluralsCandidate` su `player_name_too_long` sono avvisi vecchi, ancora fuori baseline: quattro avvisi in tutto, nessun errore).
5. **`textStyle="bold"` in `match_game_item.xml`**: cade sul 600, ma sarebbe piu' chiaro `textFontWeight` 600.
6. **Da fare a occhio, del proprietario:** app e dashboard affiancate, i tre AVD (192dp, 227dp, quadrato), il carattere al 200% su telefono e orologio, l'icona nuova sul launcher (chiara, scura, a tema), le schermate nei due temi.

**Rapporto finale della pista (`review.md`).** *Che cosa e' cambiato:* il linguaggio del contorno e del gioco e' quello della Constitution su telefono e orologio (G-1b..G-10), e G-9 ha chiuso il maiuscolo, l'icona e gli ultimi colori Street. *Componenti e token riusati:* gruppi tonali (`Widget.App.Group`), `Widget.App.Card` solo per elementi indipendenti, `EmptyStateView`, `ProgressButton`, `NumberRoll`, pressione condivisa, `TeamInk`, i ruoli `elite_*` e `ink_*`, le icone della tabella G-3. *Stati:* normale, premuto, fuoco, selezionato, disattivo, in corso, errore, vuoto, sola lettura, movimento ridotto, dove valgono. *Movimento:* solo fra due stati, in 100, 160 o 240ms, con le molle `control` e `surface`; il solo ciclo e' `ProgressButton`. *Movimento ridotto:* cifre e righe cambiano subito, resta la sola opacita' della pressione. *Accessibilita':* contrasti calcolati e provati, tema ad alto contrasto, comandi di sola icona con descrizione, regioni live sui punteggi, bersagli da 48dp, carattere al 130% provato nei test. *Responsivo:* 360dp per il telefono, tondo da 192dp prima per l'orologio. *Nuovo token o componente:* `elite_background` sull'orologio (stesso valore del telefono) e `ic_menu`. *Rischi rimasti:* l'elenco qui sopra; nessuna prova a schermo.

#### G-10. Tema ad alto contrasto. FATTO (7 ottobre 2026).

Un tema **aggiunto** (la Constitution: 7:1 sul testo e 3:1 sui bordi), scuro, che non tocca gli altri. Valori proposti, gia' calcolati (da confermare in G-10 con `TokenEliteTest` esteso):

| Ruolo | Valore | Contrasto sul nero / su elevated `#0D0D0F` |
|---|---|---|
| `background-canvas`, `background-surface` | `#000000` (uguali: il gruppo si vede per il bordo) | |
| `background-elevated` | `#0D0D0F` | |
| `text-primary` | `#FFFFFF` | 21,00 / 19,42 |
| `text-secondary` | `#C9C9D2` | 12,77 / 11,81 |
| `text-disabled` | `#8A8A9A` | 6,18 / 5,72 |
| `border-subtle` | `#6E6E7E` | 4,19 / 3,88 |
| `border-group` | `#8A8A9A` (visibile: e' il solo segno del gruppo) | 6,18 / 5,72 |
| `border-strong` | `#B4B4C0` | 10,23 / 9,46 |
| `accent-default`, `status-success`, `focus-ring` | `#C8F135` (focus `#FFFFFF`) | 16,09 / 14,88 |
| `on-accent` | `#000000` | 16,09 sul lime |
| `status-warning` | `#F0B35A` | 11,30 / 10,45 |
| `status-error` | `#FF8A8A` | 9,25 / 8,56 |
| `status-info` | `#9ED0F5` | 12,79 / 11,83 |
| `status-*-subtle` | `#0A1A10`, `#1F1606`, `#2B0D0D`, `#0A1822`: `#FFFFFF` fa 17,98-18,01, il colore di stato 13,77, 9,61, 7,94, 10,97 | |

Si attiva con un interruttore nelle impostazioni **e** seguendo il sistema (`UiModeManager.getContrast()` da Android 14; sotto, solo l'interruttore: minSdk e' 30). E' un `ThemeOverlay` sul tema (stessi ruoli, nuovi valori), scelto prima di `setContentView`; nel gioco e sul quadrante, che sono gia' bianco su nero, cambiano solo i grigi (`#9E9E9E` a `#C9C9D2`) e i bordi. Il tema ad alto contrasto **tiene** le forme, i pesi e il movimento. Ambient dell'orologio invariato.
- **Costo:** medio
- **File:** `mobile/src/main/res/values/colors.xml`, `themes.xml` (overlay), `mobile/.../ui/settings` (interruttore), `MainActivity.kt` e le altre attivita' (applicazione dell'overlay), `wear/src/main/res/values/theme.xml`; test `TokenEliteTest` esteso
- **Verifica:** test che ogni coppia testo/sfondo del tema ha >= 7 e ogni bordo e icona >= 3, falsificato abbassando di un passo il testo secondario; l'overlay non cambia le forme, i pesi e i raggi; screenshot di ogni schermata del contorno nei due temi; carattere al 200%.
**FATTO (7 ottobre 2026, passo wf44). Come e' stato fatto.** Solo il telefono; nessuno screenshot, nessun emulatore (le schermate nei due temi e il carattere al 200% sono del proprietario). **L'orologio non e' toccato**: non ha impostazioni dove mettere l'interruttore e seguirebbe solo il contrasto di sistema; la riga `wear/.../theme.xml` della lista dei file e i grigi del quadrante restano a un passo a parte, da decidere (l'ambient resta invariato in ogni caso). Valori della Constitution: il suo alto contrasto e' chiaro (tela bianca), qui conta la tabella sopra, scura, che vince come per gli altri temi.

- **Come un tema cambia i colori senza toccare i layout.** Un `ThemeOverlay` cambia gli attributi del tema, ma i layout, i disegni e il codice citano `@color/elite_*` e `R.color.elite_*` (circa 180 e 60 punti). I 20 token che cambiano sono quindi **file in `res/color`** (`elite_text_primary.xml`, ...) che puntano a un attributo (`?attr/eliteTextPrimary`): i nomi restano gli stessi e seguono il tema, senza toccare i layout. I valori stanno in `colors.xml` con il suffisso `_standard` (invariati) e in `colors_alto_contrasto.xml` (`_alto_contrasto`); `Theme.ScoreboardEssential` assegna gli standard e le sue voci `color*` (ruoli di M3) puntano agli stessi attributi, `ThemeOverlay.App.AltoContrasto` assegna gli altri. Le selezioni di colore di `res/color` (bottoni, card, chip, campo, casella) citano l'attributo senza passare da un file dentro un file. **Il contesto dell'applicazione** non riceve il tema del manifest (solo le activity): `ScoreboardEssentialApplication` fa `setTheme(Theme_ScoreboardEssential)`, senza il quale un `getColor()` da servizio o da test usciva magenta.
- **Valori usati** (quelli gia' calcolati della tabella; due ruoli che la tabella non dava): sfondo e superficie `#000000`, rialzata `#0D0D0F`; testo `#FFFFFF`, secondario `#C9C9D2`, disattivo `#8A8A9A`; bordo del gruppo `#8A8A9A`, bordo sottile `#6E6E7E`, contorno dei comandi `#B4B4C0`; lime e ciano invariati; sopra il lime `#000000`; avviso `#F0B35A`, errore `#FF8A8A`, info `#9ED0F5`; fondi degli avvisi `#0A1A10`, `#1F1606`, `#2B0D0D`, `#0A1822`; premuto il testo al 12% (`#1FFFFFFF`). **Nuovi:** passaggio del puntatore `#26262B` (la tabella non lo dava: bianco circa 15:1, secondario circa 9:1, sopra le due soglie; il bordo sottile ci fa circa 2,98:1, e il passaggio e' solo puntatore e tastiera, non un comando da riconoscere) e **`elite_focus_ring`** (il fuoco: lime nello standard, **bianco** in alto contrasto come dice la tabella; lo usano l'anello di fuoco `bg_focus_ring` e il bordo del campo in fuoco).
- **Cosa decide l'alto contrasto** (`utils/AltoContrasto.kt`, come `MovimentoRidotto`: l'overlay si applica a ogni activity in `onActivityPreCreated`, quindi prima di `onCreate`). La scelta dell'utente se c'e' (preferenza `high_contrast` in `user_preferences`, assente finche' non tocca l'interruttore; l'interruttore salva anche `false`, e spento vince sul sistema); altrimenti **`UiModeManager.getContrast() >= 0,5`** (`SOGLIA_DI_SISTEMA`: il sistema ha tre livelli, 0, 0,5 e 1, il tema ne ha uno, quindi segue dal "medio" in su; sotto Android 14 vale 0 e conta solo l'interruttore). Al cambio del contrasto di sistema (`addContrastChangeListener`, da Android 14) le activity aperte il cui aspetto cambia si ricreano; l'interruttore ricrea la schermata. Il valore di sistema e' una proprieta' che i test sostituiscono per simulare `getContrast()`.
- **Interruttore.** Nuovo gruppo tonale **Accessibilita'** nelle impostazioni (dopo Partita, prima di Padel Elite): titolo, una riga con «Alto contrasto» e la spiegazione («Segue il contrasto del sistema finche' non scegli.»), un `MaterialSwitch` col nome per TalkBack; stringhe in `values` e `values-it`. L'interruttore mostra lo stato in vigore (anche se viene dal sistema) e non salva il proprio stato di vista, perche' un valore ripristinato a contrasto di sistema cambiato sarebbe scambiato per una scelta.
- **Gioco.** Gia' bianco su nero: le cifre (`ink_white`) e il pallino (lime) non cambiano, i grigi del tabellone (`elite_text_secondary`) passano da 6,18:1 a 12,77:1 sul nero e il test inflaziona `content_scoreboard_live` nei due temi e controlla che nessun testo peggiori e che tutti abbiano 7:1 sul nero.
- **Resta fuori (dichiarato).** Il PDF resta com'e' (pagina scura su carta, colori suoi). Le tinte di squadra scelte dall'utente restano contenuto e `TeamInk` garantisce il loro contrasto come prima. Un'activity che ricevesse un contesto senza tema (un servizio, un ricevitore) legge i token dal tema dell'applicazione, cioe' sempre lo standard: oggi nessuno dei due ne usa. Non verificato a occhio: ogni schermata del contorno nei due temi, il carattere al 200%, il cambio del contrasto di sistema su un telefono con Android 14 (qui simulato).
- **Test.** `AltoContrastoTest` (nuovo, 24): contrasti letti dalle risorse vere attraverso il tema: testo primario e secondario >= 7:1 su sfondo, superficie, rialzata e passaggio; testo disattivo >= 4,5; bordo del gruppo, bordo sottile e contorno >= 3:1 su ogni superficie (e il bordo del gruppo >= 4,5, perche' sfondo e superficie coincidono: e' il solo segno del gruppo); anello di fuoco >= 3:1 anche sul passaggio; lime, avviso, errore e info >= 7:1; testo sopra il lime >= 7:1; fondi degli avvisi con testo e colore di stato >= 7:1; lime, ciano e `chart_3..5` >= 3:1; i valori della tabella uno per uno; **senza overlay i valori standard sono quelli di sempre**; l'overlay ha solo voci di colore di ruolo (nessuna forma, peso, raggio, durata o stile) e forme, stili, tipografia e pressione risolvono uguali nei due temi; layout delle impostazioni, bottoni primario e secondario e dialogo presi dall'overlay; interruttore che salva la scelta (acceso e spento) e activity nuove che la usano; sistema seguito dalla soglia in su (simulando `getContrast`), con la scelta dell'utente che vince nei due sensi; overlay su ogni activity (storico, giocatori); sotto Android 14 vale solo l'interruttore (`@Config(sdk = [30])`). **Falsificazioni:** il testo secondario al valore standard (6,18:1) fa fallire il 7:1 e il bordo forte standard fa fallire il 3:1; il controllo dell'overlay trova una forma (`cornerSize`), un peso (`textFontWeight`) e la pressione (`pressFeedback`) in un esempio sbagliato.

## Coerenza con Padel Elite - 5 ottobre 2026

Terza pista, dopo telefono e orologio. Nasce da `PIANO_PADEL_ELITE.md` (filone 2) e dalle decisioni del proprietario del 5 ottobre. I valori dei token sono quelli veri di `css/style.css` della dashboard, tema Navy scuro; i rapporti di contrasto sono ricalcolati qui con la formula WCAG (stessa di `TeamInk.contrast`).

### Direzione

**La dashboard da' il contorno, l'app tiene la schermata di gioco.** Foglio PARTITA, storico, Cronaca, statistiche, giocatori, impostazioni, onboarding, dialoghi, PDF e menu dell'orologio prendono token, caratteri, forme e regole di Padel Elite. La schermata di gioco e il quadrante restano come sono (nero puro, cifre giganti, colore di squadra con `TeamInk`) e cambiano solo le cifre (JetBrains Mono) e gli accenti (lime al posto del rosa), nei passi G-6 e G-7. Nessun passo tocca la dashboard.

> **6 ottobre 2026:** le cifre dei punteggi non sono piu' in JetBrains Mono ma in Inter con cifre tabulari, e i passi G-2..G-9 sono rifatti nella sezione "Adattamento alla UI Constitution". Il resto di questa direzione vale.

Regole di Padel Elite che l'app adotta nel contorno: un solo bottone primario per vista (lime, testo `#0D0D0F`, raggio 8, altezza almeno 48dp sull'app); secondario su superficie rialzata con bordo e testo secondario; distruttivo solo testo rosso; badge solo bordo e testo colorati, mai sfondo pieno; card raggio 14 con bordo 1px e senza ombra; campi su fondo con bordo, focus lime; niente glow; animazioni di 240ms al massimo; ciano `#00E5FF` SOLO in visualizzazioni e grafici, mai nel chrome; successo = lime.

### Decisioni prese (proprietario, 5 ottobre 2026, sera)

- **G1. Identita' street**: via dal contorno, tutta. Via gli angoli tagliati (`cornerFamily cut`), via `StreetCard`, `StreetButton`, `StreetBadge`, `MatchSheet` nella forma tagliata; restano i nomi degli stili finche' non li si rinomina, ma la famiglia diventa `rounded`.
- **G2. Lati**: lime e ciano, come la Cronaca della dashboard. Sostituiscono giallo `#FFD600` e verde `#76FF03` come colori predefiniti delle squadre (G-6). Restano personalizzabili e `TeamInk` garantisce il contrasto: sul nero il lime fa 16,09:1 e il ciano 13,65:1, quindi nessuno stroke di rimedio.
- **G3. Caratteri** (cambiata il 6 ottobre: i numeri sono Inter con `tnum`, JetBrains Mono esce): Inter per il testo, JetBrains Mono 700-800 con numeri tabulari per i punteggi. Sull'orologio le cifre vanno **misurate** prima (G-7): a 58sp nel tondo da 192dp "AV" e "40" devono entrare; se non entrano il condensato resta sulle cifre e Inter sul resto.
- **G4. Temi**: solo il Navy scuro. Niente tema chiaro dell'app (e' un lavoro a parte). Il tema dell'app resta `Theme.Material3.Dark` e non DayNight.
- **G5. Nome e icona**: da proporre. **Proposta, da confermare:** l'app resta una app a se', ma con il marchio di Padel Elite accanto: nome visibile **"Scoreboard Essential"** invariato (la scheda Play e le recensioni non cambiano), icona nuova nel linguaggio della dashboard: fondo `#0D0D0F`, un pallino lime `#C8F135` (la pallina) sopra due linee a tratto 2 che richiamano il tabellone, niente rosa; icona monocromatica (themed) sullo stesso disegno. In alternativa piu' forte, **"Padel Elite Score"** con la stessa icona, che pero' cambia `app_name` in due moduli, la scheda Play, `STORE_LISTING.md` e il tutorial, e vincola lo sport (l'app e' anche calcio e tennis). **Da confermare** con il proprietario prima di G-9; nessun file dell'icona si tocca prima. **Applicata in G-9 (7 ottobre 2026), come proposta da confermare:** nome invariato; icona adattiva su fondo `#0D0D0F` con il segno che c'era gia' (le due colonne di punteggio, una avanti all'altra) ridisegnato nei token, lime `#C8F135` per la colonna avanti e testo primario per l'altra; stessa icona sull'orologio e silhouette monocromatica per il tema delle icone di Android 13+. Il pallino sopra due linee della proposta di partenza non si e' fatto: il segno delle due colonne e' quello che il proprietario ha gia' visto nell'onboarding.

### Colori: mappatura token Padel Elite -> Android

Una sola fonte: `mobile/src/main/res/values/colors.xml` (G-0, fatto). `values-night` non esiste: il tema e' solo scuro (G4). Il quadrante ha il suo `wear/src/main/res/values/colors.xml`, che riceve i token in G-7.

| Token Padel Elite | Valore | Nome Android | Ruolo M3 / uso |
|---|---|---|---|
| fondo | `#0D0D0F` | `elite_background` | `android:colorBackground`, `colorSurfaceDim`, `colorSurfaceContainerLowest`, `colorOnSecondary`, `colorOnError`, `colorOnSurfaceInverse`; sfondo di `bg_asphalt_main` |
| superficie | `#161618` | `elite_surface` | `colorSurface`, `colorSurfaceContainer`, `colorSurfaceContainerLow`, `colorErrorContainer`; card (`bg_concrete_card`) |
| superficie rialzata | `#1E1E22` | `elite_surface_raised` | `colorSurfaceContainerHigh`, `colorSurfaceContainerHighest`, `colorSurfaceVariant`, contenitori primario/secondario/terziario; dialoghi, campi, bottone secondario |
| hover | `#2A2A2E` | `elite_surface_hover` | `colorSurfaceBright`; stato premuto o in hover |
| bordo | `#1E1E22` | `elite_border` | bordo 1px delle card (decorativo, 1,1:1) |
| bordo forte | `#2A2A2E` | `elite_border_strong` | `colorOutlineVariant`; divisori (decorativo, 1,4:1) |
| (nessuno: scelta dell'app) | `#6E6E7E` | `elite_outline` | `colorOutline`: contorno di campi e pulsanti secondari, 3,3:1 sulla rialzata |
| testo primario | `#D1D1D8` | `elite_text_primary` | `colorOnSurface`, `colorOnBackground`, `colorOnPrimaryContainer`, `colorSurfaceInverse` |
| testo secondario | `#8A8A9A` | `elite_text_secondary` | `colorOnSurfaceVariant`, `colorSecondary` |
| testo terziario | `#7F7F93` | `elite_text_tertiary` | suggerimenti e didascalie, solo su fondo e superficie |
| marchio lime | `#C8F135` | `elite_lime` | `colorPrimary`, accento dei dialoghi, bottone primario, focus |
| testo sul lime | `#0D0D0F` | `elite_on_lime` | `colorOnPrimary` |
| successo | = lime | `elite_success` (alias di `elite_lime`) | esiti positivi |
| ciano | `#00E5FF` | `elite_cyan` | SOLO grafici e visualizzazioni; `neon_cyan` e' ora un alias |
| avviso | `#E09A35` | `elite_warning` | avvisi (nessun ruolo M3) |
| errore | `#E05252` | `elite_error` | `colorError`, `colorOnErrorContainer`; testo e icone distruttivi |
| overlay | `rgba(0,0,0,0.85)` | `elite_overlay` (`#D9000000`) | velo dei dialoghi (usato in G-2) |

Scostamento dichiarato dalla dashboard: `colorOutline` NON e' il bordo forte. Il bordo forte `#2A2A2E` fa 1,36:1 sul fondo e 1,16:1 sulla rialzata: va bene per separare una card, non per far trovare un campo (WCAG 1.4.11 chiede 3:1 per i componenti). L'app aggiunge `elite_outline` (tinta del testo secondario scurita) e lo usa dove un comando ha un contorno. Secondo ruolo senza controparte: la dashboard non ha un colore secondario, quindi `colorSecondary` e' il testo secondario (neutro) e non il ciano. Terzo accento: segue il testo primario.

I vecchi nomi (`asphalt_dark`, `concrete_gray`, `graffiti_dark_gray`, `stencil_white`, `sidewalk_gray`, `graffiti_pink`, `error_red`, `error_text`, `outline_gray`, `team_*`, `ink_white`, `asphalt_black`) restano nello stesso file, in un blocco "STREET (eredita')", finche' qualcuno li cita. Si tolgono quando non li cita piu' nessun layout, classe o test.

### Contrasti (WCAG, ricalcolati)

| Coppia | Rapporto | Esito |
|---|---|---|
| testo primario `#D1D1D8` su fondo / superficie / rialzata / hover | 12,78 / 11,90 / 10,94 / 9,41 | AA e AAA |
| testo secondario `#8A8A9A` su fondo / superficie / rialzata | 5,72 / 5,32 / 4,89 | AA |
| testo secondario su hover | 4,21 | Non passa 4,5: mai testo secondario su hover |
| testo terziario `#7F7F93` su fondo / superficie | 4,96 / 4,61 | AA (solo qui) |
| testo terziario su rialzata / hover | 4,24 / 3,65 | Non passa: il terziario non sta su dialoghi e campi |
| lime `#C8F135` su fondo / superficie / rialzata / hover | 14,88 / 13,85 / 12,73 / 10,95 | AAA, anche come testo |
| testo scuro `#0D0D0F` su lime (bottone primario) | 14,88 | AAA |
| bianco `#FFFFFF` su lime | 1,31 | Non passa: per questo `colorOnPrimary` e' il fondo |
| testo primario su lime / secondario su lime | 1,16 / 2,60 | Non passano: sul lime solo `elite_on_lime` |
| errore `#E05252` su fondo / superficie | 5,08 / 4,73 | AA |
| errore su rialzata / hover | 4,35 / 3,74 | Non passa 4,5: il testo distruttivo non va su dialoghi e campi a corpo piccolo; vale come icona (3:1) e come testo grande |
| `error_text` `#FF6E6E` (eredita') su fondo / superficie / rialzata | 7,13 / 6,63 / 6,10 | AA: soluzione disponibile per il testo distruttivo su rialzata (G-2 decide se usarla) |
| avviso `#E09A35` su fondo / superficie / rialzata / hover | 8,18 / 7,61 / 7,00 / 6,02 | AA |
| ciano `#00E5FF` su fondo / superficie / rialzata | 12,62 / 11,75 / 10,80 | Grafica (3:1) e testo |
| bordo forte `#2A2A2E` su fondo / superficie / rialzata | 1,36 / 1,26 / 1,16 | Decorativo: non passa 3:1 come contorno di comando |
| bordo `#1E1E22` su fondo / superficie | 1,17 / 1,09 | Decorativo |
| `elite_outline` `#6E6E7E` su fondo / superficie / rialzata / hover | 3,88 / 3,61 / 3,32 / 2,86 | 3:1 per la grafica su fondo, superficie e rialzata; non su hover (stato passeggero) |
| lime `#C8F135` e ciano `#00E5FF` come zone + sul nero (G2) | 16,09 / 13,65 | Sopra 3:1: nessuno stroke; inchiostro `TeamInk` nero (16,09 e 13,65) |
| vecchi `#E0E0E0` e `#9E9E9E` su `#0D0D0F` (confronto) | 14,71 / 7,25 | Il nuovo testo primario perde 1,9 punti, resta AAA |

Verificati da `TokenEliteTest` (mobile/src/test/.../TokenEliteTest.kt): i valori esadecimali dei token, successo = lime, `neon_cyan` = `elite_cyan`, overlay nero all'85%, testo primario e secondario >= 4,5 su fondo e superfici, terziario su fondo e superficie, lime e testo scuro sul lime >= 4,5, errore e avviso >= 4,5 su fondo e superficie, ciano >= 3, `elite_outline` >= 3, piu' una falsificazione che fissa i limiti noti (bordo forte < 3, bianco su lime < 3, secondario su lime < 4,5, terziario ed errore su rialzata < 4,5). `TemaDelTelefonoTest` controlla in piu' che i ruoli M3 risolvano ai token e che testi e contorno del tema reggano sui fondi del tema.

### Piano

#### G-0. Token: colori e tema M3 sui valori di Padel Elite. FATTO (5 ottobre 2026).

`colors.xml` con i token `elite_*` come fonte unica e il blocco "STREET (eredita')" sotto; `themes.xml` con la mappatura completa dei ruoli M3 (`colorPrimary` lime, `colorOnPrimary` `#0D0D0F`, superfici, `colorOutline` = `elite_outline`, errore, ruoli fissi, ruoli dell'errore che prima restavano ai default) e l'overlay dei dialoghi su lime e superficie rialzata; `bg_asphalt_main.xml` e `bg_concrete_card.xml` ripuntati ai token, che ritinge in un colpo fondo e card di tutto il contorno senza toccare i layout. Il quadrante non e' toccato. Non toccati: layout, caratteri, StreetCard, schermata di gioco, colori predefiniti delle squadre.

- **Costo:** piccolo
- **File:** `mobile/src/main/res/values/colors.xml`, `mobile/src/main/res/values/themes.xml`, `mobile/src/main/res/drawable/bg_asphalt_main.xml`, `mobile/src/main/res/drawable/bg_concrete_card.xml`, `mobile/src/test/java/it/vantaggi/scoreboardessential/TokenEliteTest.kt` (nuovo), `TemaDelTelefonoTest.kt`, `ContrastiDelTelefonoTest.kt`, `MatchLogAdapterTest.kt`
- **Verifica:** `./gradlew test ktlintCheck lintDebug assembleDebug`; `TokenEliteTest` e `TemaDelTelefonoTest` verdi; nessun ruolo M3 viola.

#### G-1. Caratteri: Inter e JetBrains Mono, numeri tabulari ovunque ci sono punteggi. FATTO (5 ottobre 2026).

> **Superato il 6 ottobre 2026 da G-1b** (sezione "Adattamento alla UI Constitution"): JetBrains Mono e' uscito dall'app, i pesi sono 400, 500, 600 e i numeri sono Inter 600 con `tnum`. Le misure di sotto sono quelle del mono e restano come storia.

Inter 400, 500, 600, 700 e JetBrains Mono 700 e 800 inclusi in `res/font` (licenza OFL, circa 600 KB; si evita il font scaricabile di Google per non dipendere da Play Services in partita). Il tema dichiara `android:fontFamily` Inter; i `TextAppearance.App.*Street` smettono di dire `sans-serif-condensed`. Dove c'e' un punteggio o un tempo: JetBrains Mono con `fontFeatureSettings tnum`. Il numero della schermata di gioco misura con `Paint.measureText` il token piu' largo: la misura va rifatta con il nuovo `Typeface`, altrimenti il corpo scelto non e' quello vero. La Cronaca costruisce le viste in codice con `Typeface.create(CONDENSED, ...)`: va sostituito. Il PDF usa gli stessi stili.

**Come e' stato fatto (5 ottobre 2026).**

- **File dei caratteri.** I sei file stanno in `shared/src/main/res/font` e non in `mobile`, perche' li usano telefono e orologio e cosi' c'e' una sola copia (con `nonTransitiveRClass` il Kotlin li cita come `it.vantaggi.scoreboardessential.shared.R.font.*`; i layout, che risolvono sul merge, li citano come `@font/inter`). Sono i file ufficiali scaricati dalle release dei progetti, Inter 4.1 (`Inter-Regular`, `Medium`, `Bold`, `Black`, tutti statici) e JetBrains Mono 2.304 (`Bold`, `ExtraBold`), ridotti con `pyftsubset` (fontTools) al sottoinsieme latino che l'app usa: Basic Latin, Latin-1, Latin Esteso A, punteggiatura generale, frecce, meno matematico, `≡`, spunte; tutte le feature OpenType tenute (`tnum`, `calt`...), hinting tolto. Nessuna modifica ai disegni. Pesi e dimensioni: `inter_regular` 95.020 B (400), `inter_medium` 96.692 B (500), `inter_bold` 96.564 B (700), `inter_black` 95.796 B (900), `jetbrains_mono_bold` 72.700 B (700), `jetbrains_mono_extrabold` 72.724 B (800): 529.496 B in tutto, circa 517 KB, contro i 600 KB previsti. Due famiglie XML, `font/inter.xml` e `font/jetbrains_mono.xml`, con `android:fontWeight` (minSdk 30: niente attributi `app:`). Nessun font scaricabile di Google: funziona senza rete e senza Play Services. Licenza OFL 1.1 di entrambi in `docs/licenses/OFL-Inter.txt` e `docs/licenses/OFL-JetBrainsMono.txt` (il progetto non ha un file di note sulle licenze: i testi stanno li' e questo paragrafo e' la voce); nessuno dei due ha un Reserved Font Name, quindi il sottoinsieme puo' tenere il nome. Se serve rifarlo: stessi file di partenza, stesso elenco di caratteri, `pyftsubset --layout-features='*' --no-hinting`.
- **Tema.** Tutta la scala di Material 3 (i quindici ruoli, da `DisplayLarge` a `LabelSmall`) e' ripuntata su Inter con un `TextAppearance.App.*` per ruolo; i tre che c'erano gia' (`DisplayLarge.Street`, `HeadlineMedium.Street`, `BodyLarge.Street`) e `TextAppearance.App.Button` tengono il nome, il maiuscolo e la spaziatura e cambiano solo la famiglia. **Niente `android:fontFamily` nel tema**, ne' del telefono ne' dell'orologio: la vista lo legge dopo il suo `textAppearance` e lo sovrascrive, e il primo tentativo ha infatti portato Inter sulle cifre del quadrante e rotto undici test di larghezza. Stile del punteggio: `TextAppearance.App.Punteggio` (JetBrains Mono, `textFontWeight` 800, `fontFeatureSettings tnum`, spaziatura 0; il padre e' il `TextAppearance` di base e non uno di M3, perche' M3 porta un'altezza di riga fissa che taglierebbe un 32sp). JetBrains Mono ha le cifre gia' tutte larghe 0,6 em e non ha la feature `tnum`: il `tnum` dello stile non cambia nulla qui, ma e' dichiarato come da pista e coprirebbe un cambio di carattere.
- **Contorno.** Ogni `sans-serif-condensed`, `-condensed-medium`, `-black` e `monospace` dei layout del telefono e' passato a Inter (400/700 col `textStyle`, 500 e 900 con `textFontWeight`) salvo i punteggi, che passano a `Punteggio`: i due punti di `match_item.xml`, `text_rank` e `text_goals` di `item_player_stat.xml`; il risultato dei set (`sets_textview`) e il minuto del registro (`match_event_item.xml`) sono JetBrains Mono 700 con `tnum`. La Cronaca costruisce le viste in codice: `text()` in `ChronicleActivity.kt` prende un parametro `score` (JetBrains Mono, 800 dal corpo 20sp in su e 700 sotto, `tnum`; vale per il parziale dei game, le caselle dei game, le statistiche di servizio e i tempi) e per il resto Inter 400/700; sparisce la costante `CONDENSED`. Il PDF usa gli stessi stili: si controlla in G-8.
- **Schermata di gioco: misura delle cifre.** Le cifre giganti (`TextAppearance.App.Game.Score`) passano a JetBrains Mono 800 con `tnum`. La misura di `MainActivity.dimensioneDelNumero` non si e' dovuta toccare: copia il `Paint` della vista, quindi misura gia' col carattere nuovo. In JetBrains Mono "88" e "AV" valgono 1,2 em e **a 150dp non entrano**: 180dp contro i 173,7dp della mezza colonna del Pixel 9a (la riga e' larga 379dp, meno 16dp di aria per lato della meta'). La regola pero' non fissa il corpo, prende il minore fra il tetto e cio' che entra: **144,6dp a 411dp** (il 96% del tetto; l'altezza non limita, la riga e' 635dp) e **123,3dp a 360dp**, sempre sopra il minimo di 72. Le cifre del mono sono piu' alte di quelle del condensato (0,73 em contro circa 0,71), quindi a 144,6dp si leggono alte come prima a 150dp: **decisione, restano in JetBrains Mono**. Dove la pista diceva "a 150sp entrano nella mezza colonna" va letto "entrano alla misura che la regola calcola". Resta a G-6 il resto della schermata di gioco (cronometro `Game.Clock`, `Game.Value`, `Game.Name`, `Game.Command`, `Game.Caption`): condensato, come da "il gioco cambia solo le cifre".
- **Orologio: misura e decisione (G3).** Misurato sul quadrante vero (`CifreDelQuadranteTest`, grafica nativa, xhdpi, file veri): in JetBrains Mono 800 ogni cifra e' larga 0,6 em, quindi "AV", "40" e "88" misurano uguale. **Tondo da 192dp, cifre a 58dp: 69,6dp contro i 68dp della colonna, non entra** (condensato: AV 67dp, 40 58dp). **Tondo da 227dp, a 68dp: 81,6dp contro gli 81dp, non entra** (condensato: AV 78dp, 40 68dp). Per la regola di G3 le cifre del quadrante (`Face.Score`, `Face.Context`, `Face.Keeper`: punteggio, cronometro, K) **tengono il condensato**, ambient compreso; Inter va sul resto: titoli e voci del menu e della selezione sport e dei giocatori (`activity_menu.xml`, `activity_player_selection.xml`, `item_player_wear.xml`, `TextAppearance.App.*` del tema dell'orologio) e le tre righe di testo del quadrante (`Face.Detail`, `Face.Who`, `Face.Status`). Per un soffio: a 56dp il mono entrerebbe (67,2dp), ma cambiare la misura delle cifre non e' compito di G-1; se ne riparla in G-7. Il test fallisce se le colonne si allargano o il condensato smette di entrare: e' la falsificazione della scelta.
- **Layout dell'orologio sistemati per la larghezza di Inter** (che e' circa un quinto piu' largo del condensato): `MenuPiegaTest` sul 227dp falliva (sottotitolo di una voce spenta su tre righe, fondo a 118dp contro 101,5dp visibili); il sottotitolo di `item_sport_wear.xml` passa da 13sp a 12sp con `letterSpacing` 0 (due righe, fondo a 100dp). `TitoliIntegriTest` e `RigaStatoLarghezzaTest` sono rimasti verdi senza toccare nulla.
- **Test.** `CaratteriDelTelefonoTest` (telefono): i sei file e le due famiglie si caricano e sono diversi fra loro; i quindici ruoli M3 del tema risolvono a Inter; una `TextView` del contorno (`match_item`) ha la larghezza di Inter e non quella del condensato; un punteggio dello storico ha JetBrains Mono 800, `tnum`, peso 800 e "11" largo come "88"; set, minuto del registro e cifre delle statistiche in JetBrains Mono; "88" e "AV" entrano nella mezza colonna a 411dp (>= 142dp) e a 360dp restano sopra i 72dp. `CifreDelQuadranteTest` (orologio): la misura qui sopra.
- **Resta fuori da G-1:** screenshot (si guardano a mano), carattere al 200% su dispositivo, PDF (G-8), cifre della barra di gioco (G-6), cifre del quadrante in mono (G-7, solo se si accetta un corpo piu' piccolo di 58dp).

- **Costo:** piccolo
- **File:** `mobile/src/main/res/font/*` (nuovi), `mobile/src/main/res/values/themes.xml` (famiglia del tema e `TextAppearance.App.*`), `mobile/src/main/java/it/vantaggi/scoreboardessential/MainActivity.kt` (misura delle cifre, riga 767), `mobile/src/main/java/it/vantaggi/scoreboardessential/ui/chronicle/ChronicleActivity.kt` (`CONDENSED`, riga 464), `mobile/src/main/res/layout/match_item.xml`, `mobile/src/main/res/layout/match_event_item.xml` (`fontFamily monospace`), file di licenza in `docs/`
- **Verifica:** test Robolectric che `ResourcesCompat.getFont` carichi i sei file; "88" e "AV" a 150sp entrano nella mezza colonna del Pixel 9a; schermate prima e dopo; carattere al 200% senza testi tagliati.

#### G-2..G-9. Rifatti nella sezione "Adattamento alla UI Constitution" (6 ottobre 2026).

I passi G-2 (componenti), G-3 (icone), G-4 (contorno), G-5 (Cronaca), G-6 (gioco), G-7 (orologio), G-8 (PDF) e G-9 (verifica) che stavano qui sono stati **tolti**: la fonte e' ora la sezione che sta sopra, che li rivede secondo la UI Constitution (icone Material Symbols e non Heroicons, gruppi tonali e card solo per elementi indipendenti, nessuna ombra sulle superfici ferme, bottoni non a pillola, pressione uguale ovunque, NumberRoll per i punteggi, pallini del servizio lime, "Essential information" nel contorno) e aggiunge G-10 (alto contrasto). Il testo di prima resta nella cronologia di git.

### Rischi

- **Lime come marchio e come colore di squadra** (G2): nella schermata di gioco non c'e' nessun bottone primario lime, quindi non si confondono; nel foglio PARTITA, dove stanno insieme, il lato 1 e' sempre accompagnato dal nome e dal testo, e il primario e' un pulsante pieno con testo scuro. Il lime e il ciano non si distinguono con alcune forme di daltonismo: il colore non e' mai l'unico segno (il nome della squadra c'e' sempre).
- **Bordo forte della dashboard sotto 3:1.** E' un limite della dashboard, non dell'app: l'app lo evita con `elite_outline`. Se il proprietario vuole i bordi identici, i campi perdono il 3:1 (decisione sua, non presa).
- **Il contorno e' gia' ritinto in parte da G-0.** Il fondo e le card di tutte le schermate di contorno prendono `#0D0D0F` e `#161618` dai due drawable, mentre i layout citano ancora `stencil_white`, `sidewalk_gray`, rosa e ciano: fino a G-4 le schermate sono ibride (fondo nuovo, testi vecchi). I contrasti dei testi vecchi sui fondi nuovi restano AAA (14,71 e 7,25 sul fondo), quindi nulla diventa illeggibile.
- **Il quadrante ha un `colors.xml` suo** (risolto da G-7: stessi nomi e valori del telefono per i ruoli che usa, confrontati da `TokenDelPolsoTest`; i token non sono in `:shared` ma duplicati, perche' `:shared` non ha risorse di colore e il test garantisce che non divergano).
- **Font inclusi nell'APK**: accettato (G3); dopo G-1b sono tre file di Inter (288 KB); sull'orologio le cifre sono state misurate e restano condensate.
- **Lint sulle risorse non ancora usate:** i token `elite_success`, `elite_warning`, `elite_overlay`, `elite_text_tertiary`, `elite_border` e altri sono definiti ma citati solo dai test finche' G-2 e G-4 non li usano.

## Invio a Padel Elite, passi A-1..A-4 - 7 ottobre 2026

Filone 3 di `PIANO_PADEL_ELITE.md`. Il contratto del server e' `docs/dashboard/SCOREBOARD_FORMAT.md` par. 6 (casella d'arrivo, migrazione 64). Nessuna prova ha toccato il Supabase vero: tutto con un server finto (MockWebServer) nei test JVM.

**Come e' stato fatto (passi A-1..A-4, 7 ottobre 2026).**

- **A-1, configurazione.** `PADEL_ELITE_SUPABASE_URL` e `PADEL_ELITE_SUPABASE_KEY` si leggono da `local.properties` (o dall'ambiente della CI) in `mobile/build.gradle` e finiscono in `BuildConfig` (`PadelEliteConfig.fromBuildConfig`). Mancando uno dei due, o con un URL non http(s), la funzione e' **spenta**: nessuna card nelle impostazioni, nessun comando, nessuna casella nel dialogo, nessuna richiesta. `local.properties` e' in `.gitignore` (verificato con `git check-ignore`). Permesso `INTERNET` nel manifest di `:mobile`. Il README dice dove mettere i valori.
- **Libreria o REST: REST diretto su `HttpURLConnection`, nessuna dipendenza di rete nuova.** `supabase-kt` porta Ktor e kotlinx.serialization (con il suo plugin del compilatore) per quattro richieste; OkHttp sarebbe stato una dipendenza in piu' per fare quello che su Android fa gia' `HttpURLConnection` (che e' OkHttp della piattaforma, con il TLS e i timeout di sistema). Chiamate: GoTrue `POST /auth/v1/token?grant_type=password|refresh_token`, PostgREST `GET /rest/v1/group_members?select=group_id,role,groups(name)&user_id=eq.<uid>` e `GET /rest/v1/v2_scoreboard_inbox?select=status,match_id&external_id=eq.match:<matchId>`, RPC `POST /rest/v1/rpc/submit_scoreboard_match` con `apikey` e `Authorization: Bearer`. Il file v2 non si rilegge ne' si riscrive: va nel corpo com'e' lo produce `MatchExporter.toJson` (nessuna copia del formato). Le uniche dipendenze nuove sono `androidx.work` (la coda, richiesta dal compito) e, di test, `work-testing` e `mockwebserver`.
- **A-2, accesso.** Solo email e password. Sessione (access e refresh token, scadenza, utente) in un file di preferenze privato `padel_elite_session`, **cifrata con AES-256-GCM e una chiave dell'Android Keystore** non esportabile (`KeystoreSecretBox`, 40 righe di piattaforma: `EncryptedSharedPreferences` e' deprecata e porta Tink). Il file e' escluso dai backup (`data_extraction_rules.xml`, `backup_rules.xml`; `allowBackup` era gia' falso). Una sessione che non si decifra vale come nessuna sessione. Rinnovo: il token si rinnova da solo se scade entro un minuto o se il server lo rifiuta (401, `not_authenticated`), **una volta sola** per chiamata, sotto un `Mutex` (Supabase invalida il refresh token a ogni uso: due rinnovi insieme ne brucerebbero uno); un rinnovo rifiutato cancella la sessione e chiede l'accesso, un rinnovo senza rete la lascia. Esci cancella sessione e gruppo; un altro utente non eredita il gruppo.
- **A-3, gruppi.** Dopo l'accesso, l'elenco dei gruppi di cui l'utente e' membro con il ruolo; un solo gruppo si sceglie da solo; la scelta e' salvata (e si scarta se non si e' piu' membri).
- **A-4, invio.** `InvioRunner` e' la logica di un tentativo (costruisce il file dal database con `MatchRepository.buildSavedExportByUuid`, che riusa `MatchExportUtils.savedMatchExport`, e lo consegna); `InvioWorker` e' il lavoro WorkManager: **unico per partita** (`padel-elite-invio-<matchId>`, `ExistingWorkPolicy.KEEP`), vincolo di rete, backoff esponenziale da 30 s. Il file non passa da WorkManager (limite 10 KB, il file arriva a 170 KB): si rifa' dal database a ogni tentativo. Esiti: rete assente, 5xx, 408/429 e `inbox_full` ritentano; `invalid_payload` (con il campo in `details`) e `payload_too_large` no, "non inviabile" col motivo; oltre 200 KB non si prova nemmeno la rete; `not_authenticated` dopo un rinnovo gia' tentato porta a "accedi di nuovo"; `not_authorized` e' "non inviabile: non sei piu' nel gruppo"; una risposta fuori contratto (404 se la 64 non e' ancora in produzione) ritenta cinque volte e poi si ferma; la riga della partita non ancora scritta si aspetta tre giri. `already_submitted` e' un successo. Un invio automatico non si ripete a ogni avvio: dopo la consegna il lavoro e' finito, e uno scarto dell'admin non rimette in coda (solo un nuovo comando dell'utente, "Invia di nuovo" dal 8 ottobre, M-2, che il server riapre). Stato per partita in preferenze (`padel_elite_invii`, per `matchUuid`), non in Room: nessuna migrazione dello schema per un dato che si puo' anche perdere. Le partite in "inviata" si aggiornano (importata o scartata) interrogando la casella quando si apre lo storico.

**Schermate e stati (rapporto UI).**

- **"Padel Elite"** (`PadelEliteActivity`, `activity_padel_elite.xml`), da Impostazioni. Compito: collegarsi e scegliere il gruppo. Senza accesso, il modulo: **Field con etichetta sempre visibile sopra il campo** (`labelFor`), **errore sotto il campo** (campo vuoto, credenziali, email non confermata) o, per gli errori che non sono di un campo (rete, troppi tentativi), una riga con icona sopra il bottone; **un solo primario, "Accedi"** (lime, testo `elite_on_lime`), largo quanto la riga, cosi' in "Accesso in corso" cambia solo la parola e la larghezza non si muove; campi e bottone disattivati durante l'invio; riuscito = si passa al pannello dell'account. Con accesso: "Collegato come ...", gruppi come righe a scelta singola da 48dp (stato selezionato: pallino pieno e testo), "Esci" contornato; stati dei gruppi: in caricamento, vuoto (con cosa fare), errore di rete con "Riprova".
- **Impostazioni**: card "Padel Elite" solo con la funzione accesa: "Non collegato" + "Padel Elite: accedi", oppure "<email> · <gruppo>" + "Gruppo" e "Esci".
- **Card dello storico**: comando "Invia a Padel Elite" (solo padel chiuso, con registro e con identificativo; non mentre e' in coda o gia' arrivata) e riga di stato con **parola e icona**, mai solo il colore: in coda (`schedule`), inviata e in attesa di un admin (`hourglass_empty`), importata (`check_circle`, lime), scartata dall'admin (`warning`), non inviabile col motivo (`cancel`), accedi di nuovo (`lock`). Il testo e' sempre `elite_text_primary`; l'icona porta il colore dello stato (lime, avviso, errore, secondario), tutti sopra 3:1 sul fondo della card.
- **Dialogo di fine partita**: una casella "Invia anche a Padel Elite" sotto il messaggio (solo padel, funzione accesa, file esportabile). Se si salva con la casella spuntata e manca l'accesso o il gruppo, si apre "Padel Elite" e dal ritorno il comando e' sulla card.

**Conflitti e debiti dichiarati.** I primi tre erano debiti verso G-2 e G-3, chiusi il 7 ottobre 2026 con l'integrazione dei due rami (passo wf37).

1. ~~Stili di prima.~~ **Chiuso.** Le schermate dell'invio usano i componenti di G-2: la card "Padel Elite" delle impostazioni e' un gruppo tonale (`Widget.App.Group`, come i quattro vicini), "Padel Elite: accedi" e "Gruppo" sono `Widget.App.Button.Secondary` ed "Esci" e' `Widget.App.Button.Text`; nella schermata "Padel Elite" i campi sono `Widget.App.TextInputLayout`, "Accedi" e' l'unico `Widget.App.Button.Primary`, "Riprova" e' secondario, "Esci" testuale, e i gruppi sono righe `MaterialRadioButton` da 48dp (`item_padel_elite_group.xml`, `Widget.App.RadioButton`) dentro un `Widget.App.Group`. Sulla card dello storico il comando e' `Widget.App.Button.Text` e la riga di stato ha il testo in `elite_text_primary`. La casella del dialogo di fine partita e' una `MaterialCheckBox`, cosi' prende lo stile del tema (lime, spunta) e non quello di fabbrica. Nessun nome `Street` resta in queste schermate; `ShapeAppearance.App.StreetCard`, tolta da G-2, non e' piu' citata.
2. ~~Spaziature scritte a mano.~~ **Chiuso** per le schermate dell'invio: `space_4..space_32` e `control_touch` al posto di 4, 8, 12, 16, 24, 32 e 48dp.
3. ~~Icone a mano.~~ **Chiuso.** `ic_send`, `ic_schedule`, `ic_hourglass_empty`, `ic_check_circle`, `ic_cancel` e `ic_warning` sono i Material Symbols outlined scaricati da `google/material-design-icons` come quelli di G-3, e hanno la loro riga nella tabella (concetti di dominio: invio `send`, in coda `schedule`, in attesa dell'admin `hourglass_empty`; "accedi di nuovo" e' `lock`, la riga "Blocco"). `ic_hourglass` ed `ic_error` sono tolti: la scartata dall'admin e' `warning` (avviso) e il non inviabile e l'errore del modulo sono `cancel` (la Constitution dice circle-x).
4. Il dialogo di fine partita non ha un quarto bottone: la casella e' una scelta, non un comando.
5. Movimento: nessuno aggiunto (nessun ciclo; il "in corso" e' una parola, quindi vale anche con il movimento ridotto). Non verificato a schermo ne' al 200%: nessun emulatore in questo passo.
6. ~~Resta, del contorno e non dell'invio.~~ **Chiuso il 7 ottobre 2026 da G-4 (wf38):** i colori Street e il maiuscolo dei vicini (impostazioni, `match_item`) sono tolti, e le righe dei gruppi di Padel Elite hanno il divisore con inset (`PadelEliteActivity.rigaDivisoria`).

**Non fatto in questo passo:** A-5 (integrazione contro Supabase locale: Docker non parte su questa macchina), A-6 (`PRIVACY_POLICY.md`, scheda "Sicurezza dei dati", `RELEASE_CHECKLIST.md`: il README dice ancora "nothing is uploaded"), A-7 (prova sul campo). La `KeystoreSecretBox` e il comportamento con rete vera non si provano su JVM.

### R-1, rose dalla dashboard e `padelPlayerId` nel file inviato - 8 ottobre 2026

Punto 2 della sezione 4 di `PIANO_PADEL_ELITE.md`. Con l'accesso e un gruppo scelto l'app legge i giocatori di quel gruppo, li collega ai giocatori locali e, **nell'invio a quel gruppo**, mette `padelPlayerId` sui giocatori collegati: nella casella d'arrivo l'admin trova i nomi gia' scelti. Senza accesso non cambia niente (la sezione non c'e', nessuna richiesta, il file e' identico). Nessuna chiamata al Supabase vero: tutto provato col server finto.

**Come e' fatto.**

- **Tabella nuova `padel_elite_links`** (Room 14 -> 15, solo `CREATE TABLE` e `CREATE UNIQUE INDEX`): (`localPlayerId`, `groupId`, `remotePlayerId`, `remoteName`). Chiave (giocatore locale, gruppo), indice unico (gruppo, giocatore della dashboard), cascata alla cancellazione del giocatore locale. **Non riusa** `players.padelPlayerId` (dismessa, vecchi valori senza gruppo): un id della dashboard vale solo nel suo gruppo. Il testo SQL combacia con `schemas/.../15.json`. Test strumentato `migrazione14a15_...` e catena 11 -> 15 in `DatabaseMigrationTest` (compilano, non lanciati: serve un dispositivo).
- **Lettura della rosa.** `PadelEliteApi.fetchRoster`: `GET /rest/v1/v2_players?group_id=eq.<uuid>&select=id,name&order=name` (solo id e nome: `linked_user_id` non serve e non si chiede finche' la privacy A-6 non e' chiusa), stesse intestazioni e stessi errori classificati di `fetchGroups` (401 -> `NotAuthenticated`, rete/5xx/corpo non una lista -> `Network`) piu' il 403 -> `NotAuthorized` (non e' rete: la sezione dice che non si e' piu' membri e offre "Aggiorna i gruppi"); le righe senza id positivo o senza nome si saltano. `PadelEliteAccount.roster` la fa passare dal rinnovo del token.
- **Logica** (`RosaGruppo`): collegare risolve le due unicita' in transazione (l'ultima scelta vince), "crea in locale" crea il `Player` col nome della dashboard e lo collega in una transazione sola, `sync` aggiorna il nome e toglie i collegamenti a chi la dashboard non ha piu' (una rosa vuota non cancella niente: in dubbio un collegamento si tiene). **Proposta per nome**: nome identico senza maiuscole e spazi, solo fra non collegati; se il nome non e' univoco da una parte (due Marco in locale o nella rosa) **non si propone** (l'omonimo sbagliato manderebbe la partita sul giocatore sbagliato). La proposta calcola, non scrive: i collegamenti nascono solo col comando.
- **Export e invio.** `MatchExporter.build(..., padelPlayerIds = emptyMap())`: la mappa id locale -> id della dashboard e' facoltativa; ogni voce diventa `"padelPlayerId"` del giocatore, e si filtra a cio' che la dashboard conserva (interi da 1 a 9 cifre) e a chi gioca davvero. Il campo sta in `MatchExport.padelPlayerIds`, non in `MatchPlayer` (che e' anche la proiezione di una query Room). Senza mappa il file e' byte per byte quello di prima (il fixture `export-v2-sample.json` non cambia). `InvioRunner` passa il **gruppo di destinazione** al costruttore del file, `MatchRepository.buildSavedExport(matchId, padelGroupId)` prende i collegamenti di QUEL gruppo.
- **Decisione: l'export su file NON porta `padelPlayerId`.** Il file e' anonimo, si condivide con chiunque (WhatsApp, mail) e non dice per quale gruppo vale; un id della dashboard e' significativo solo nel suo gruppo, e un utente in piu' gruppi avrebbe id di un gruppo in un file che importera' in un altro (la dashboard allora chiede all'admin di scegliere, come senza id, ma l'id e' rumore e un dato interno in piu' fuori controllo). L'unico chiamante che passa la mappa e' l'invio a Padel Elite. Il commento del formato 2 in `MatchExport.kt` e' aggiornato.

**Rapporto UI (sezione "Giocatori del gruppo" in `PadelEliteActivity`).**

- *Compito e valore:* collegare ogni giocatore della dashboard a uno dell'app; il valore che conta e' lo stato del collegamento di ogni riga. *Gerarchia:* titolo della sezione, una riga di spiegazione, il gruppo con le righe, poi i comandi.
- *Componenti riusati:* `Widget.App.Group` con righe (non card) e linee rientrate, `item_padel_elite_player` (nome in `RowTitle` 500, stato in `Caption` 400; con il titolo della sezione, tre pesi), `EmptyStateView`, `Widget.App.Button.Secondary` ("Collega i nomi uguali (n)") e `.Text` ("Aggiorna i giocatori"), `MaterialAlertDialogBuilder` per la scelta (elenco di voci: crea nell'app, scollega, giocatori non ancora collegati). **Nessun primario** nella regione: e' una scelta, non un invio. Icone: la spunta `ic_check` a 16dp (concetto "Fatto, selezionato" della tabella G-3); nessun concetto nuovo, quindi nessuna riga aggiunta (`refresh` resta `-`).
- *Stati:* nessun gruppo scelto (riga di testo), in caricamento (riga di testo, nessun ciclo), vuota (titolo, motivo, "Aggiorna i giocatori"), errore di rete (titolo, motivo, "Riprova"; il lavoro dell'utente, i collegamenti, resta), pronta, non collegato ("Non collegato"), proposto ("Stesso nome di X, da confermare"), collegato ("Collegato a X" con la spunta), accesso scaduto (torna al modulo). **Sola lettura senza accesso:** senza accesso la sezione non esiste (la schermata e' il modulo di accesso). Premuto/fuoco: `Widget.App.Pressable` e anello di fuoco della riga.
- *Movimento:* nessuno aggiunto. *Accessibilita':* riga da 48dp, la riga si legge nome + stato; lo stato e' sempre una parola, la spunta e' decorativa. *Schermo stretto e 200%:* il nome si accorcia con ellissi su una riga, lo stato va a capo; i comandi sono `wrap_content`. *Conflitti:* nessuno nuovo.
- *Controllo dei pattern vietati:* niente maiuscolo, niente emoji, niente ombre, niente colore scritto a mano, un solo peso in piu' dei tre della schermata. **Non visto a schermo** (nessun emulatore in questo passo).

**Debiti dichiarati.** (1) La rosa non si tiene in cache: senza rete la sezione dice l'errore e non mostra i collegamenti gia' fatti (restano nel database e partono comunque nell'invio). (2) `linked_user_id` non si legge piu' (servira' alla proposta "chi invia" della sezione 4, punto 3, quando A-6 sara' chiuso). (3) Un collegamento a un giocatore della dashboard che sparisce si toglie alla lettura successiva; fino ad allora un invio porta un id che il gruppo non ha, e la dashboard chiede all'admin di scegliere. (4) Dall'elenco di scelta non si ricerca per nome (le rose reali sono di decine di nomi). (5) ~~**Un collegamento cambiato dopo la consegna non aggiorna la voce gia' nella casella d'arrivo**~~ **Ridotto l'8 ottobre 2026 da M-2 (sezione seguente):** "Invia di nuovo" aggiorna la voce ancora in attesa (con il server nuovo); resta aperto cio' che segue. Il problema era: la voce e' idempotente su (gruppo, `external_id`) e `already_submitted` non la riscrive, quindi se l'utente collega un giocatore dopo aver inviato la partita, l'admin trova ancora il file di prima (senza `padelPlayerId`, o con quello vecchio). Si risolve solo se la dashboard permette di aggiornare una voce ancora in attesa (sezione 4, punto 4, "modificabile dopo").

**Correzioni dalla revisione (8 ottobre 2026).** (a) "Annulla" dopo lo swipe di cancellazione di un giocatore rimette anche i suoi collegamenti: `PlayersManagementViewModel` li legge prima e `PlayerRepository.restorePlayer` li reinserisce (la CASCADE li aveva tolti). (b) La proposta per nome conta gli omonimi su tutta la rosa e su tutti i giocatori locali, anche collegati. (c) `sync` non scollega nessuno con 1000 righe o piu' (`max_rows` di PostgREST: la rosa puo' essere troncata). (d) La sessione scaduta scoperta dalla rosa la segnala una volta il ViewModel (`onSessionLost` -> `PadelEliteViewModel.sessionLost()`), non il disegno: nessun ciclo di rilettura. (e) Le scritture dei collegamenti non mandano in crash se falliscono (chiave esterna): la lista si rilegge. (f) Il 403 e' uno stato proprio.

### M-2, "Invia di nuovo": modificabile dopo la consegna - 8 ottobre 2026

Punto 4 della sezione 4 di `PIANO_PADEL_ELITE.md` (lato app). **Chiude il debito (5) di R-1** per quanto puo' l'app: dipende dal server nuovo (migrazione 64 corretta, in lavorazione nella dashboard, non in produzione). Nessuna chiamata al Supabase vero: tutto col server finto.

**Contratto letto dall'app.** `submit_scoreboard_match` risponde `{ "item": {...}, "already_submitted": bool, "updated": bool }`. Stesso utente, stessa partita, voce in attesa: il server sostituisce il file (`already_submitted: true, updated: true`, o `updated: false` se identico). Voce importata, o inviata da un altro: `already_submitted: true, updated: false`, niente cambia. **Voce scartata rimandata dallo stesso gruppo: torna in attesa col file nuovo** e la risposta e' `already_submitted: false, updated: false` (serve a recuperare uno scarto sbagliato; contratto definitivo della dashboard, PR #218): l'app la legge come "inviata" (`SENT`). `updated` puo' mancare (server vecchio): vale `false` (`SubmitResult.Accepted.updated`).

**Stati nuovi** (`InvioState`, sempre parola e icona; il testo resta `elite_text_primary`, l'icona `hourglass_empty` in secondario: sono tutti "in attesa di un admin"):

| Stato | Quando | Testo |
|---|---|---|
| `SENT` | consegna nuova (`already_submitted: false`) | Inviata: in attesa di un admin |
| `UPDATED` | `updated: true`: il server ha sostituito il file | Aggiornata nella casella: in attesa di un admin |
| `PRESENT` | `already_submitted: true, updated: false` su una voce in attesa (file identico, server vecchio, o l'ha inviata un altro) | Gia' nella casella: in attesa di un admin |
| `IMPORTED` / `DISCARDED` | lo `status` della voce, che comanda sempre (mai "aggiornata" su una voce chiusa) | invariati |

`PRESENT` non dice "aggiornata" per non mentire: con un server vecchio il file non e' stato sostituito. `refreshStatuses` interroga la casella per le tre (`InvioInfo.isPending`), non solo per `SENT`.

**Comando.** Lo stesso bottone di "Invia a Padel Elite", **nello stesso punto della card** (sotto la riga di stato, in vista, non nel dettaglio che si espande): lo stato dice cosa e' successo e il comando che agisce su quello stato gli sta sotto. Una regione, un solo comando per volta: per una voce in attesa il testo diventa **"Invia di nuovo"** (stessa icona `send` della tabella G-3: il concetto e' ancora "invio", nessuna riga nuova), per un invio fallito resta "Invia a Padel Elite", per importata e in coda non c'e' bottone. E' un comando testuale con parola, quindi non serve `contentDescription`; stile `Widget.App.Button.Text` come prima, nessun secondo primario. **Anche una voce scartata dall'admin ha "Invia di nuovo"** (stato "Scartata da un admin: puoi rimandarla"): il server la rimette in attesa col file nuovo, quindi serve a recuperare uno scarto sbagliato; il rimando va al gruppo della voce come per le altre e, se riesce, lo stato diventa "inviata" (`SENT`); se fallisce in modo definitivo la voce resta scartata (con gruppo e firma). Importata no: non cambia piu'. `refreshStatuses` non interroga le scartate (per la casella sono gia' chiuse): solo quelle in attesa.

**Cosa fa il tocco.** `PadelEliteServices.send` mette in coda `InvioWorker` (sempre `ExistingWorkPolicy.KEEP`): un solo lavoro per partita (`padel-elite-invio-<matchUuid>`), un secondo tocco non ne crea un altro, mai due invii in parallelo (la card toglie il bottone appena lo stato diventa "in coda"). Il file non viaggia nel lavoro: `InvioRunner` lo **ricostruisce al momento dell'invio** dal database con i collegamenti R-1 attuali di quel gruppo (`buildSavedExportByUuidWithLinks(uuid, gruppo)`: una lettura sola, il file e la sua firma non possono divergere), come per ogni tentativo. Mentre e' in coda lo stato e' `QUEUED`; l'esito lo porta a `UPDATED`, `PRESENT`, `IMPORTED`, `DISCARDED` o a un errore come un invio normale.

**Il rimando va al gruppo della voce, non a quello scelto adesso** (regola scritta il 8 ottobre dopo la revisione). Lo stato di invio ricorda il gruppo a cui e' partito l'ultimo file (`InvioInfo.group`, sempre scritto alla consegna). Con un gruppo scelto diverso, un rimando verso il gruppo scelto avrebbe creato un doppione in un'altra casella. Quindi: "Invia di nuovo" parte solo se il gruppo della voce e' noto (`InvioInfo.canResend` = in attesa **e** gruppo noto) e va a quel gruppo, anche senza un gruppo scelto; per una voce in attesa di cui il gruppo non si conosce (stato scritto da una versione precedente) la regola piu' sicura e' **non offrire il comando** (`send` risponde `NOT_RESENDABLE` e non mette niente in coda). Un invio nuovo (nessuna voce in casella) va, come prima, al gruppo scelto.

**Un rimando che fallisce non perde la voce.** Il tocco scrive `QUEUED` ricordando gruppo, firma e stato di prima (`InvioInfo.previous`); mentre aspetta rete o riprova (`waiting`) li conserva. Se il rimando fallisce in modo definitivo (file non valido, non piu' membro, troppo grande, accesso scaduto, cinque risposte fuori contratto) `InvioRunner.settle` torna allo stato in attesa precedente con gruppo e firma: il file di prima in casella c'e' ancora, il comando e' ancora li' e `refreshStatuses` continua a seguirla (importata o scartata dall'admin). L'errore del rimando non resta scritto sulla card (un invio nuovo che fallisce, invece, resta un errore). Nelle preferenze lo stato porta in coda `<U+001F>gruppo<U+001F>=firma<U+001F>precedente`.

**Senza accesso o senza configurazione niente cambia.** Funzione spenta: nessun comando e nessuna richiesta. Funzione accesa ma senza sessione: "Invia di nuovo" non compare (`MatchHistoryUiState.padelEliteAccess`, da `PadelEliteServices.hasAccess()` = configurata e con sessione; non serve un gruppo scelto, il rimando va al gruppo della voce). La sessione sta in un file cifrato col Keystore, quindi `MatchHistoryViewModel.rileggiAccesso()` la legge su `Dispatchers.IO` (all'avvio e a ogni `onResume` dello storico, perche' l'accesso si cambia in un'altra schermata): finche' la lettura non finisce il comando non c'e', poi compare. Se un tocco arrivasse lo stesso senza sessione, `send` porta all'accesso senza mettere niente in coda.

**Suggerimento "i giocatori collegati sono cambiati"** (fatto, era la parte facoltativa). Lo stato di invio ricorda il gruppo e la **firma dei collegamenti** del file partito (`FirmaCollegamenti`: "giocatore locale:giocatore della dashboard", ordinati, solo i giocatori di quella partita e solo di quel gruppo). La card la confronta con i collegamenti di adesso (`PadelEliteLinkDao.observeAll`, un flusso Room: cambia un collegamento, la lista si ricompone) e, **solo per una voce in attesa con "Invia di nuovo" disponibile**, mostra sotto lo stato una riga di solo testo in `Caption` e `elite_text_secondary`: "I giocatori collegati sono cambiati: invia di nuovo per aggiornare la casella". Non rimanda mai da sola. Un collegamento di un giocatore che non gioca quella partita, o di un altro gruppo, non conta. Preferenze: la firma e' un campo in coda al valore salvato, dopo un separatore U+001F; un valore scritto prima si legge com'era e, senza firma, il suggerimento non compare (non si sa).

**Rapporto UI.** *Compito e valore:* sapere se la casella ha l'ultima versione della partita e, se no, aggiornarla; il valore e' la riga di stato. *Gerarchia:* stato, poi (se serve) il suggerimento, poi il bottone. *Componenti riusati:* la riga di stato e il bottone di prima, un `TextView` `Caption`. *Stati:* in coda, inviata, aggiornata, gia' presente, importata, scartata, errore, accedi di nuovo (comando e stato, mai solo colore); sola lettura senza accesso (nessun comando); premuto e fuoco: `Widget.App.Pressable` e anello del bottone esistente. *Movimento:* nessuno nuovo. *Accessibilita':* bottone con parola, 48dp; il suggerimento e' testo letto nell'ordine della card. *Schermo stretto e 200%:* riga e suggerimento vanno a capo, il bottone e' `wrap_content` a destra come prima. *Pattern vietati:* niente maiuscolo, emoji, ombre, colori a mano; un solo peso sulla riga (400). **Non visto a schermo** (nessun emulatore in questo passo).

**Debiti dichiarati.** (1) Contro il server **vecchio** il comando c'e' ma il file in casella non cambia (`PRESENT`); la firma salvata non lo sa e il suggerimento sparisce dopo il rimando. (2) `PRESENT` copre tre casi che l'app non distingue (identico, server vecchio, inviata da un altro). (3) Un rimando fallito torna alla voce in attesa senza dire perche': l'errore (per esempio "file non valido") non resta sulla card, e il comando e' li' per riprovare. (4) La firma conta anche i collegamenti a id della dashboard fuori da 1..999999999, che il file non porta: e' coerente fra invio e card, quindi non produce falsi suggerimenti, ma un collegamento cosi' non e' "visto" dal file. (5) Dall'app non si modifica la partita gia' importata: e' una partita della dashboard e si modifica li'.

## Serata - 8 ottobre 2026 (R-2)

Richiesta del proprietario: una sera giocano in quattro (o piu'), le coppie cambiano a ogni set e ogni set e' una partita a se'; cambiare chi gioca con chi deve essere facilissimo, e si deve poter mettere dentro un ospite. Piano di `PIANO_PADEL_ELITE.md`, sezione 4, punto 3. Questa sottosezione e' il piano dell'interfaccia (CLAUDE.md, "Prima di ogni modifica all'interfaccia") e il registro di cio' che e' stato fatto. Lavora solo sull'app e sui giocatori locali: l'orologio, il protocollo, `padelelite/` e il collegamento con la dashboard non si toccano.

### Dove si apre, e perche' li'

- **Dal foglio PARTITA**, in un gruppo "Serata" sopra le Rose (visibile solo in padel e tennis). E' il punto piu' naturale perche' il foglio e' gia' il posto dove si compongono rose e coppie e si scambiano i posti; oggi non esiste un "come iniziare" separato, e metterne uno davanti al gioco sarebbe un passo in piu' per chi non fa la serata.
- **Dal dialogo di fine partita**, con la casella "Prossima partita della serata" (accesa se la partita veniva dalla serata): dopo il salvataggio apre la schermata Serata, che ha gia' proposto le coppie successive. Non e' un quarto bottone: il dialogo ne ha tre, e la casella e' una scelta, come "Invia anche a Padel Elite".
- La schermata torna alla partita con "Inizia la partita": le coppie diventano le rose.

### Piano (CLAUDE.md, punto 3)

- **Un compito, un valore.** "Chi gioca la prossima": il valore piu' importante sono le due coppie. Il resto (panchina, presenti) sta sotto e si toglie prima di rimpicciolire.
- **Gerarchia e layout.** Una colonna che scorre, tre gruppi tonali con righe rientrate (`Widget.App.Group`, linee di `InsetDividerDecoration`), nessuna card: (1) *Partita n* con le intestazioni "Coppia 1" e "Coppia 2", i quattro posti e i tre comandi; (2) *In panchina*; (3) *N presenti*, tutta la rosa con la spunta e "Aggiungi un ospite" in fondo; poi "Chiudi la serata" (solo testo, distruttivo). Il **primario** e' uno solo, "Inizia la partita", fuori dallo scorrimento in fondo, sempre alla portata del pollice, con sopra il motivo per cui e' spento ("Servono almeno 4 presenti. Ne mancano 2."). Il foglio PARTITA tiene Fine partita come primario: il gruppo Serata ha un solo comando secondario.
- **Componenti riusati.** `Widget.App.Group`, `Widget.App.Pressable` (la pressione condivisa: scala 0,97, opacita' 0,85), `bg_focus_ring`, `InsetDividerDecoration`, `Widget.App.Button.Primary/Secondary/Destructive`, `Widget.App.TextInputLayout`, toolbar come le altre schermate, la nota di sola lettura come `watch_notice_card` (fondo `elite_info_subtle`, barretta `elite_info`). Nuovi: `item_serata_row` (la riga: icona, numero del posto, nome fino a due righe, nota, spunta), `bg_row_selected`, `TextAppearance.App.RowTitleSelected` (RowTitle a 600). Le righe sono un solo adapter (`SerataRigheAdapter`) per i quattro elenchi.
- **Tutto token.** Colori `elite_*`, spazi `space_*`, nessun valore scritto a mano; tre pesi (400 caption, 500 titolo di riga, 600 titolo di gruppo e riga selezionata); niente maiuscolo; niente emoji; niente ombre; nessuna animazione nuova.
- **Icone** (tabella G-3, quattro righe nuove, tutte da `google/material-design-icons` come le altre): Serata `groups`, Ruota `autorenew`, Stesse coppie `repeat`, Scambia i lati `sync_alt`. I comandi sono righe con icona e testo, quindi non ci sono comandi di sola icona; le icone nelle righe sono decorative (`importantForAccessibility="no"`).

| Stato | Cosa si vede |
|---|---|
| Vuoto (nessuna serata) | solo il gruppo dei presenti con la rosa (nessuno spuntato) e "Aggiungi un ospite"; primario spento, sopra "Servono almeno 4 presenti. Ne mancano 4." |
| Rosa vuota | nel gruppo dei presenti una frase che dice cosa fare ("Non ci sono ancora giocatori. Aggiungi un ospite, o crea i giocatori da Giocatori.") e comunque "Aggiungi un ospite" |
| Meno di 4 presenti | come il vuoto, ma con la serata ricordata: i presenti restano spuntati, nessuna bozza, "Chiudi la serata" visibile |
| Quattro o piu' presenti | compaiono *Partita n*, *In panchina* e il primario acceso |
| Panchina vuota | con quattro presenti il gruppo c'e' e dice "Nessuno in panchina. Tutti i presenti giocano." |
| Normale / premuto | riga a riposo / pressione condivisa |
| Fuoco | anello lime di 2dp (`bg_focus_ring`) |
| Selezionato | sfondo `elite_surface_raised`, spunta lime, nome a 600: tre segni, e `stateDescription` "Selezionato" per TalkBack |
| Disattivo | testo `elite_text_disabled`, icona a 0,5 ("Stesse coppie" prima di una partita giocata, o se uno dei quattro se n'e' andato) |
| Sola lettura (partita in corso) | nota in testa "C'e' una partita in corso. La prossima si compone a fine partita.", tutte le righe spente, niente "Chiudi la serata", primario spento |
| Errore | un ospite senza nome non chiude il dialogo: il messaggio sta sotto il campo e il testo scritto resta |
| In corso (lavoro) | nessuno: i comandi sono sincroni; la creazione dell'ospite e' una scrittura locale |
| Movimento ridotto | non applicabile: la schermata non muove niente da se' |

- **Movimento.** Nessuno nuovo: le righe cambiano contenuto senza animazione (`itemAnimator` spento, per non far scattare il gruppo a ogni scambio). La sola animazione e' la pressione condivisa.
- **Accessibilita'.** Ogni riga ha una `contentDescription` che dice il posto ("Coppia 2, posto 1: Nudi"), la panchina e le partite, e se un giocatore e' presente o assente e cosa fa il tocco. Le intestazioni dei gruppi sono `accessibilityHeading`. Bersagli di almeno 48dp. La nota di sola lettura e' una regione live.
- **Schermo stretto e carattere al 200%.** Una colonna sola, nessuna coppia di colonne per lato: i due nomi di una coppia stanno in due righe, non affiancati, e il nome puo' andare a capo fino a due righe. I tre comandi di rotazione sono righe, non tre bottoni in fila (che a 360dp non sarebbero entrati). Il primario sta fuori dallo scorrimento ma e' alto una riga: a 200% la nota sopra puo' andare a capo senza coprire le righe.
- **Conflitti dichiarati.** (1) Nessuno nuovo sui token. (2) La spunta e' lime come il fuoco e l'accento, quindi la selezione ha il secondo segno del peso. (3) Come in G-4, i componenti `Tabs`, `RowDetail` e `SegmentedControl` della Constitution non erano fra i file letti e non servono: si e' seguito il README e `layout.md`.

### La regola di rotazione

Una sola, in `core/Serata.kt` (`RotazioneSerata`), funzione pura dei presenti e delle partite fatte:

1. **Chi gioca.** Giocano i quattro che hanno giocato meno partite; a parita' chi ha giocato piu' tempo fa (chi non ha mai giocato per primo); a parita' chi e' arrivato prima. Con quattro presenti giocano tutti.
2. **Con chi.** I quattro hanno tre modi di dividersi in coppie (A+D/B+C, A+C/B+D, A+B/C+D, con A..D nell'ordine di arrivo: dal 27 ottobre 2026 l'ordine dei gironi della dashboard, vedi "Serata: ordine dei gironi e spareggio"). Si sceglie quello che ripete meno coppie gia' fatte; a parita' quello giocato meno di recente; a parita' il primo dell'elenco. Con quattro presenti e' un ciclo di tre che non ripete una combinazione prima di averle fatte tutte; con piu' presenti nessuno gioca due partite piu' di un altro.
3. **Posti.** Dentro ogni coppia il primo posto, che serve per primo, e' di chi e' arrivato prima; la coppia col primo arrivato e' la prima. L'ordine di servizio della partita e' quello dei posti (A1, B1, A2, B2), come in `CoppieNelFoglio`.

**Ruota** propone la successiva trattando la bozza di adesso come gia' fatta (cosi' percorre tutte le combinazioni invece di rimbalzare fra due); **Stesse coppie** rimette le coppie dell'ultima partita (spento se uno dei quattro se n'e' andato); **Scambia i lati** porta ogni coppia dall'altra parte; **toccare due nomi** li scambia (anche fra coppie, o con uno in panchina). Chi arriva a meta' serata sta in panchina finche' la bozza non si cambia (poi, non avendo giocato, gioca); chi esce dalla bozza e' sostituito dal primo della panchina, nello stesso posto.

### Come vive la serata

- **E' un valore** (`Serata`: presenti, partite giocate, bozza, proposte saltate, partita in gioco) con il suo testo versionato (`SerataCodec`, `serata1`), ricordato nelle preferenze dell'app (`app_prefs`, chiave `serata`) da `SerataPrefsStore` a ogni comando: sopravvive al riavvio. **Nessuna tabella Room, nessuna migrazione:** le partite giocate sono nello storico come ogni altra e non hanno un legame con la serata; chiudere la serata toglie il testo e non cancella niente. Un testo illeggibile vale come serata assente.
- **Versione del testo.** La prima riga e' `serata1`. Un'app piu' vecchia che incontri un testo di una versione futura lo legge come illeggibile e quindi come serata assente: se il formato cambia, la versione nuova deve saper leggere `serata1`, e conviene non scendere di versione (la serata si perde, le partite no).
- **Ogni comando parte dalla memoria**, non dalla copia letta all'apertura della schermata (`store.load()` a ogni modifica), perche' `MainViewModel` puo' chiudere la partita nel frattempo (anche dall'orologio) e riscriverla da una copia vecchia la rifarebbe. `onStart` rilegge la serata. La sola lettura, invece, la decide l'intent all'apertura: la schermata non puo' sapere se una partita parte mentre e' aperta.
- **Una partita senza due coppie non e' della serata.** Se l'avvio e' seguito da un cambio di sport, o a fine partita le rose non sono due contro due, `inGioco` si annulla e la partita non si registra con la composizione consegnata. Il dialogo di fine partita apre la Serata con "partita in corso" falso (`MainViewModel.partitaInCorso()` e' sincrono) e non la apre se l'invio a Padel Elite ha appena aperto la sua schermata di accesso.
- **Un ospite** e' un `Player` locale creato al volo (`PlayerDao.insert`), quindi statistiche, storico ed export funzionano come oggi. Lo stesso nome (senza badare alle maiuscole) e' la stessa persona: nessun doppione.
- **Una partita della serata e' una partita normale.** `MainViewModel.avviaPartitaDellaSerata()` mette le due coppie nelle rose (solo a registro vuoto, solo in padel e tennis) passando da `refreshServeOrder` e `salvaRoseDellaRigaViva`, come `addPlayerToTeam`; il tennis con quattro nomi diventa doppio da solo. `endMatch()` chiude la partita della serata (con le coppie con cui si e' giocato davvero, non con quelle proposte) e propone la successiva; `discardMatch()` la scarta e lascia la bozza. Una partita giocata fuori dalla serata non la tocca.
- **Durante la partita** la schermata e' di sola lettura e le coppie seguono le regole di oggi (scambio solo a registro vuoto).
- **Un giocatore eliminato dalla rosa** esce dalla serata.

### Non fatto

~~Gironi e iscritti dalla dashboard~~ (fatto il 15 ottobre 2026: sezione "Serata dalla dashboard" qui sotto), rose dalla dashboard e `padelPlayerId` (altro ramo, poi R-1); gli strumentati e gli screenshot (nessun emulatore in questo passo: la schermata e' provata con Robolectric sul layout vero); il tennis in doppio e' gratis ma provato solo fino alla creazione del doppio dal ViewModel; la scelta fra piu' serate (ce n'e' una).

## Serata dalla dashboard - 15 ottobre 2026 (gironi e iscritti)

Punto 5 della sezione 4 di `PIANO_PADEL_ELITE.md` ("gironi e iscritti"). Con l'accesso a Padel Elite e un gruppo scelto, la schermata Serata dice qual e' la prossima serata del gruppo, quanti hanno confermato e quali gironi ha salvato la dashboard, e li propone come presenti. **Solo letture, con le policy che ci sono gia'**: nessuna scrittura sul Supabase di produzione, nessuna migrazione. Senza configurazione, senza accesso o senza un gruppo scelto la schermata e' quella di prima e non parte nessuna richiesta. Nessuna chiamata al Supabase vero: tutto provato col server finto.

### Contratto letto

| Lettura | Richiesta | Cosa se ne usa |
|---|---|---|
| Serata configurata | `GET /rest/v1/v2_settings?group_id=eq.<g>&key=eq.venue_config&select=value` | `day_of_week` (0 = domenica, come `Date.getDay()`), la **prima** prenotazione (`bookings[0]`, non la piu' presto): `time` "HH:MM" e `duration` |
| Presenti | `GET /rest/v1/v2_rsvp?group_id=eq.<g>&session_date=eq.<AAAA-MM-GG>&status=eq.confirmed&select=player_id&order=created_at.asc,player_id.asc` | gli id di `v2_players` confermati, in ordine di risposta |
| Gironi | `GET /rest/v1/v2_settings?group_id=eq.<g>&key=eq.gironi_serata&select=value` | `{ v: 1, serata, gironi: [[id, ...], ...] }` (PR #229 della dashboard); `presenti` e `aggiornata_il` non servono |
| Nomi | `GET /rest/v1/v2_players?...` (la lettura di R-1, `fetchRoster`) | il nome di chi non e' collegato |

Errori come la rosa di R-1: 401 rinnova il token una volta e ripete (`PadelEliteAccount.leggi`, che passa da `authenticated`), un rinnovo rifiutato e' "accesso scaduto"; 403 e' "fuori dal gruppo"; rete assente, 5xx e corpo che non e' una lista sono "errore, Riprova". Le letture sono in fila, e si ferma alla prima che non va.

**La data della prossima serata** e' quella di `getNextSessionDate` in `js/nextmatch.js` della dashboard, riscritta in `:core` (`ProssimaSerata.data`, funzione pura con l'adesso iniettato): il prossimo `day_of_week` da oggi; se e' oggi e la prima prenotazione e' **strettamente** finita (inizio + durata, quindi alla fine esatta e' ancora oggi), quella della settimana dopo. Ora locale del telefono, come la dashboard usa quella del browser. La durata e' quella di `parseDurationMin`: un numero com'e', un testo per le cifre in testa ("90m" = 90), altrimenti **90 minuti**; senza prenotazioni valgono le 19:00 e 90 minuti (i predefiniti della dashboard). `end_month` non conta: la dashboard non lo usa per la prossima serata.

**Differenze volute dalla dashboard** (dichiarate): (1) senza la riga `venue_config`, o con un giorno che manca o non e' fra 0 e 6, l'app dice "nessuna serata configurata" invece di usare il martedi' alle 19 di fabbrica della dashboard: non inventa una serata. (2) Un'ora che non si legge vale le 19:00 (la dashboard in quel caso calcola una data non valida). (3) Un `gironi_serata` con `v` diverso da 1 o una `serata` che non e' una data si ignora: un formato nuovo non si indovina.

### Flusso

1. **Apertura e ritorno sulla schermata** (`onStart`): la fonte controlla fuori dal thread principale se c'e' qualcosa da leggere (configurazione, sessione cifrata, gruppo scelto). Se no il gruppo "Dalla dashboard" non c'e' e non compare nemmeno per un attimo. Se si', "in lettura" e le quattro letture (`LettoreSerataDashboard`). Una serata gia' letta resta in vista mentre si rilegge.
2. **Traduzione** (in `:core`, `TraduzionePresenti`): un id collegato con R-1 (`padel_elite_links` del gruppo) a un giocatore locale che c'e' diventa quel giocatore, col nome dell'app; uno non collegato col nome nella rosa diventa un **ospite** col nome della dashboard; uno senza ne' l'uno ne' l'altro resta fuori e si conta ("1 non e' nella rosa del gruppo e resta fuori"). Un id ripetuto vale una volta. Niente si scrive qui.
3. **Il tocco** su "Usa i presenti" o su "Girone A: ..." (`SerataViewModel.usaIPresenti`, `usaIlGirone`): gli ospiti diventano giocatori locali (lo stesso meccanismo dell'ospite scritto a mano di R-2), poi la serata prende quei presenti con `Serata.conIPresenti` e un messaggio dice "8 presenti dalla dashboard, 2 come ospiti" con **Annulla**. La riga usata ha la spunta finche' i presenti restano quelli.
4. **Annulla** rimette la serata di prima (anche le partite gia' giocate, la bozza e le proposte saltate).
5. Le coppie poi ruotano con la regola di R-2, **invariata**.

### Scelte di prodotto (prese con la raccomandazione del piano: il proprietario le puo' cambiare)

1. **Il girone sostituisce i presenti**, non si aggiunge: il telefono segna un campo, quindi un girone. Chi non e' nel girone esce dalla serata come se fosse andato via.
2. **Le partite giocate restano.** Usare i presenti a meta' serata e' come togliere e aggiungere a mano: prima escono gli assenti, poi entrano i nuovi in coda, e la bozza si ricompone con la regola di sempre. Cosi' una serata gia' iniziata non perde il conto delle partite.
3. **Ordine di arrivo**: per i presenti l'ordine in cui hanno risposto nella dashboard (`created_at`, poi id); per un girone l'ordine del girone. Decide chi e' A, B, C, D nella prima rotazione e chi serve per primo.
4. **Gli ospiti non si collegano da soli.** Un non collegato entra come ospite col nome della dashboard; nessun collegamento R-1 nasce in silenzio (resta un comando della schermata Padel Elite). L'ospite **riusa il giocatore locale con lo stesso nome** (senza maiuscole e spazi ai lati), come l'ospite scritto a mano, ma **solo** se e' uno solo, non e' collegato a un altro giocatore della dashboard in questo gruppo e non e' gia' preso da un altro presente: altrimenti nell'invio la partita andrebbe al giocatore sbagliato. In dubbio se ne crea uno nuovo. E' una regola piu' stretta di quella di R-2, ed e' voluta.
5. **Annulla non cancella gli ospiti creati**: sono giocatori locali come quelli scritti a mano e possono gia' servire altrove (e' lo stesso di R-2, dove chiudere la serata non cancella nessuno).
6. **Si rilegge a ogni ritorno sulla schermata**, non in sottofondo: quattro richieste piccole, e i collegamenti fatti nel frattempo in Padel Elite si vedono subito.
7. **Gironi di un'altra serata**: non si propongono, e la riga di stato lo dice.
8. **Durante una partita** il gruppo si vede ma le righe sono spente, come il resto della schermata.
9. **Sessione scaduta**: "Accedi" apre Padel Elite; tornando, la schermata rilegge (e se l'accesso non c'e' piu' il gruppo sparisce, come senza accesso). **Fuori dal gruppo** (403): "Apri Padel Elite" per sceglierne un altro.

### Piano UI (CLAUDE.md, punto 3)

- **Un compito, un valore.** Il compito della schermata resta "chi gioca la prossima" e il valore resta le due coppie. Il gruppo nuovo e' un modo per riempire i presenti, quindi sta **sopra il gruppo dei presenti** e sotto coppie e panchina: a inizio serata (nessuna bozza) e' il primo gruppo che si vede, a serata avviata non spinge giu' le coppie.
- **Gerarchia e layout.** Un gruppo tonale (`Widget.App.Group`) come gli altri della Serata, con righe rientrate (`InsetDividerDecoration`): titolo "Dalla dashboard" (`SectionHeading`), una riga di stato in frase (`Caption`, regione live), le proposte come righe (`item_serata_row`: icona, titolo fino a due righe, nota, spunta) e, solo quando serve, un comando testuale (`Widget.App.Button.Text`: "Riprova", "Accedi", "Apri Padel Elite"). **Nessun primario nuovo**: il primario resta "Inizia la partita".
- **Componenti riusati.** `Widget.App.Group`, `SerataRigheAdapter` e `item_serata_row` (pressione condivisa, anello di fuoco, selezione con tre segni), `Widget.App.Button.Text`, lo Snackbar con "Annulla" come in Giocatori. Nessuno stile nuovo, nessun token nuovo, nessun colore scritto a mano; spazi `space_*`.
- **Icone** (tabella G-3): "Usa i presenti" ha `event_available` (concetto Presenze, `ic_event_available`), i gironi `group` (concetto Squadra, gruppo: un girone e' un gruppo di giocatori, `ic_group`); due vettoriali scaricati da `google/material-design-icons` come gli altri, righe della tabella aggiornate. Le icone nelle righe sono decorative (`importantForAccessibility="no"`): le righe hanno testo, quindi non ci sono comandi di sola icona.

| Stato | Cosa si vede |
|---|---|
| Spenta (senza configurazione, accesso o gruppo) | il gruppo non c'e'; la schermata e' quella di prima e nessuna richiesta parte |
| In lettura | "Lettura della serata dalla dashboard in corso.", nessuna proposta, nessun ciclo (e' una frase, vale anche col movimento ridotto) |
| Nessuna serata configurata | "Il gruppo non ha una serata configurata nella dashboard." |
| Pronta | "Serata di giovedi' 15 ottobre, 8 presenti." piu', se servono, "2 non collegati entrano come ospiti.", "1 non e' nella rosa del gruppo e resta fuori."; righe "Usa i presenti" (nota: quanti) e "Girone A: Anna, Bruno, Carlo, Dario" |
| Nessun presente | "Serata di giovedi' 15 ottobre. Nessun presente confermato."; nessuna riga "Usa i presenti" (i gironi, se ci sono, si) |
| Gironi della serata precedente | la riga di stato finisce con "I gironi salvati sono di una serata diversa e non si usano." e i gironi non compaiono |
| Errore di rete | "La dashboard non risponde. Controlla la connessione." e "Riprova" |
| Sessione scaduta | "Accesso a Padel Elite scaduto. Accedi di nuovo per leggere la serata." e "Accedi" |
| Fuori dal gruppo (403) | "Questo account non legge piu' il gruppo scelto. Sceglilo di nuovo in Padel Elite." e "Apri Padel Elite" |
| Normale / premuto / fuoco | riga a riposo / pressione condivisa (scala 0,97, opacita' 0,85) / anello lime di 2dp |
| Selezionato | la proposta in uso: sfondo rialzato, spunta lime, titolo a 600, `stateDescription` "Selezionato" |
| Disattivo / sola lettura | partita in corso: righe spente (testo `elite_text_disabled`, icona a 0,5) |
| In corso (applicazione) | nessuno stato visibile: creare gli ospiti e scrivere la serata e' locale e immediato |
| Fatto | messaggio "8 presenti dalla dashboard, 2 come ospiti" con "Annulla" |

- **Movimento.** Nessuno nuovo nelle righe (`itemAnimator` spento, come gli altri gruppi). Lo Snackbar entra in **dissolvenza** (`ANIMATION_MODE_FADE`), non scorrendo.
- **Accessibilita'.** Titolo `accessibilityHeading`; riga di stato regione live (si sente quando la lettura finisce); ogni proposta ha una descrizione completa ("Girone A: Anna, Bruno, Carlo, Dario. Tocca per usarlo come presenti", "Presenti dalla dashboard: 8. Tocca per usarli come presenti della serata"); bersagli di 48dp; lo stato non e' mai solo colore.
- **Schermo stretto e carattere al 200%.** Una colonna; la riga di stato va a capo; il titolo di un girone va su due righe e poi si accorcia con l'ellissi (i nomi interi restano nella descrizione per TalkBack); il comando testuale e' `wrap_content`.
- **Conflitti dichiarati.** (1) Nessuno sui token. (2) Lo Snackbar tiene la durata di Material (come "Annulla" in Giocatori), non i token di durata: e' un componente di sistema, con la dissolvenza al posto dello scorrimento.
- **Pattern vietati** (`review.md`): niente maiuscolo, niente emoji, niente ombre (gruppo a 0dp), niente colore scritto a mano, niente card per elementi che non sono indipendenti (un gruppo con righe), al massimo tre pesi (400 stato, 500 riga, 600 titolo e riga selezionata), un solo primario per regione (invariato), nessun ciclo. Nessuna stringa nuova con apostrofo, nessun `--` nei commenti XML.

### Come si prova

`SerataDallaDashboardTest` (`:core`: data, durata, ora, gironi di un'altra serata, traduzione, `conIPresenti`), `SerataDashboardApiTest` (le tre letture col server finto: 200, 401, 403, rete assente, piu' il rinnovo del token), `LettoreSerataDashboardTest` (le quattro letture insieme, la data calcolata usata davvero nella richiesta, i collegamenti dal database in memoria, gli stati), `SerataConLaDashboardTest` (il ViewModel con una fonte finta: solo al tocco, ospiti, omonimo, girone, Annulla, sola lettura, Riprova, niente lampo senza accesso), `SerataActivityTest` (la schermata gonfiata con Robolectric in ogni stato principale, il tocco su "Usa i presenti", "Riprova" e "Accedi").

### Debiti dichiarati

1. **Nessuna cache**: senza rete la riga dice l'errore e non mostra l'ultima serata letta in un'apertura precedente.
2. **Gli ospiti non sono collegati**: nell'invio non portano `padelPlayerId` finche' qualcuno non li collega in Padel Elite (l'admin li sceglie nella casella, come oggi).
3. **Annulla non toglie gli ospiti creati** (scelta 5), e annulla anche un cambio fatto a mano mentre il messaggio e' ancora visibile.
4. **Fuso orario**: la data e' calcolata con l'ora del telefono; un telefono in un fuso diverso da quello di chi usa la dashboard puo' vedere un'altra serata vicino alla mezzanotte.
5. **Piu' campi, piu' telefoni**: ogni telefono sceglie il suo girone; non c'e' un coordinamento (due telefoni possono scegliere lo stesso).
6. **Rosa oltre 1000 giocatori**: la lettura dei nomi e' quella di R-1 (tetto di PostgREST); un presente oltre il tetto risulterebbe "non nella rosa".
7. **Quattro richieste in fila** e non in parallelo: sono piccole, ma con una rete lenta "in lettura" dura quattro andate e ritorni.
8. ~~**Non visto a schermo**~~: visto il 27 ottobre 2026 sul telefono del proprietario contro la dashboard vera (sezione "Serata dalla dashboard, la verifica a schermo" in fondo).

## Serata: ordine dei gironi e spareggio - 27 ottobre 2026

Richiesta del proprietario: la serata dura un'ora e mezza, si giocano tre partite tirate e, se resta tempo, una quarta; passare da una partita all'altra non deve costare configurazione. Due cambi, solo nell'app (`:core` e la schermata Serata):

1. **L'ordine delle partite e' quello dei gironi della dashboard** (`js/ui.js`): con #1..#4 nell'ordine del girone (per valore, dal piu' alto) si gioca #1+#4 contro #2+#3, poi #1+#3 contro #2+#4, poi #1+#2 contro #3+#4. In `RotazioneSerata` e' l'ordine delle tre candidate, quindi vale anche per la serata composta a mano (A..D nell'ordine di arrivo); il resto della regola non cambia. Dentro ogni coppia serve per primo chi viene prima (#1 e #2).
2. **La quarta partita degli stessi quattro rigioca la piu' combattuta delle tre** (`SpareggioSerata`): lo scarto di game piu' piccolo; a parita' quella con piu' game, poi la prima giocata. Stesse coppie e stessi posti. Vale solo quando i quattro hanno fatto le tre combinazioni una per una e tutte e tre hanno un risultato; dalla quinta si torna alla rotazione. **Ruota** la scarta come ogni proposta. E' "opzionale" nel senso della serata: se non c'e' tempo non si gioca, e la serata si chiude come sempre.

**I risultati.** `Serata.risultati` ha, per ogni partita giocata, i game di ciascuna coppia contando i set chiusi e quello in corso (`Risultato.da(engine.state)` in `endMatch`): una partita fermata dal tempo sul 5-4 vale 5-4. Senza nemmeno un game non c'e' risultato. Il testo della serata passa a `serata2` con la riga `r:` (`6-4;?;5-6`, `?` = non si sa) e legge ancora `serata1` (senza risultati: lo spareggio non scatta e resta la rotazione). Un'app piu' vecchia legge `serata2` come serata assente: si perde la serata, non le partite (come detto in "Come vive la serata").

**Il girone decide l'ordine.** Prima della prima partita `conIPresenti` mette i presenti nell'ordine della proposta (prima chi era gia' spuntato restava davanti e diventava "#1"). A serata iniziata resta la regola di prima: escono gli assenti, entrano i nuovi in coda.

**L'ordine di battuta** (`OrdineDiBattuta`, richiesta del proprietario dello stesso giorno). Nella prima partita si sceglie chi batte per primo e chi per secondo (toccando due nomi, e con Scambia i lati): batte il primo posto della coppia 1, poi il primo della coppia 2, poi i loro compagni. Chiusa la partita, quell'ordine diventa la precedenza dei giocatori e ogni proposta successiva ci mette i posti: dentro ogni coppia batte per primo chi viene prima, e batte per prima la coppia di chi viene prima di tutti; chi non ha mai giocato sta dietro. Ogni partita chiusa aggiorna la precedenza con l'ordine giocato, quindi una correzione a mano vale per le successive. Senza scelte, il girone esce identico alla dashboard (1, 2, 3, 4 battono come #1, #2, #4, #3 nella prima, #1, #2, #3, #4 nella seconda). Va nel testo della serata (`o:`). Nella schermata il numero di ogni posto e' il **turno di battuta** (1 e 3 la coppia 1, 2 e 4 la coppia 2) al posto del vecchio "posto 1, posto 2", e la frase sotto le coppie lo spiega.

**"Usa i presenti" chiede il girone** (richiesta del proprietario dello stesso giorno). Con i gironi salvati per la serata, il tocco su "Usa i presenti" apre una scelta: "Quale girone gioca su questo campo?", con i gironi (gli stessi titoli delle righe) e in fondo "Tutti i presenti, scelgo io i quattro". Prima entravano tutti e la prima bozza erano i primi quattro confermati, di solito il girone A. Annulla non cambia niente. Senza gironi salvati il tocco usa i presenti come prima. L'app non sa quale giocatore sia chi tiene il telefono, quindi non preseleziona il suo girone (debito 4). Dialogo di sistema (`MaterialAlertDialogBuilder.setItems`), nessun componente nuovo.

**In un tocco.** Il passaggio resta quello di R-2: "Fine partita" con la casella "Prossima partita della serata" (accesa) salva e apre la Serata con la prossima gia' composta, e "Inizia la partita" la porta in campo. Non e' stato aggiunto un avvio automatico senza passare dalla Serata: si vedono le coppie prima di cominciare, e uno scambio resta a un tocco.

### Piano UI (CLAUDE.md, punto 3)

- **Un compito, un valore.** Invariati: "chi gioca la prossima", le due coppie. Lo spareggio aggiunge solo il perche' di quelle coppie, e solo quando c'e'.
- **Turno di battuta.** Il numero in testa a ogni posto (lo stesso `lead` di `item_serata_row`) e' il turno di battuta, non il posto nella coppia; la frase `serata_seats_hint` lo spiega. Per TalkBack: "Coppia 2, turno di battuta 2: Nome".
- **Gerarchia e layout.** Una riga `Caption` sotto "Partita n", nel gruppo delle coppie: "Spareggio: si rigioca la partita 2, la piu' combattuta (6-5 in game)." Nessun gruppo, card, bottone o primario nuovo.
- **Componenti riusati.** `TextAppearance.App.Caption` (Inter 400, `elite_text_secondary`) con `fontFeatureSettings="tnum"` per le cifre, spazi `space_16` e `space_4`. Nessun token, stile, colore o icona nuovi.

| Stato | Cosa si vede |
|---|---|
| Bozza di rotazione (partite 1-3, dalla quinta, dopo Ruota) | nessuna riga |
| Spareggio | la riga, con il numero della partita rigiocata e i suoi game |
| Senza risultati (serata ricordata con `serata1`, partite senza un game) | nessuno spareggio, nessuna riga: si propone la rotazione |
| Sola lettura (partita in corso) | la riga resta se la bozza e' lo spareggio, come il resto, spenta nel senso che non ha comandi |
| Normale, premuto, fuoco, selezionato, disattivo, in corso, errore, vuoto | non applicabili: e' testo, non un comando |
| Movimento ridotto | non applicabile: nessun movimento |

- **Movimento.** Nessuno.
- **Accessibilita'.** E' testo letto da TalkBack nell'ordine, dopo il titolo; dice partita e punteggio in parole e cifre, non con un colore.
- **Schermo stretto e carattere al 200%.** `wrap_content`, va a capo; non sposta il primario (fuori dallo scorrimento).
- **Conflitti dichiarati.** Nessuno. Le cifre sono in Inter con `tnum` come chiede CLAUDE.md.
- **Pattern vietati** (`review.md`): niente maiuscolo, emoji, ombre, colori scritti a mano, card, pesi oltre i tre, cicli; un solo primario per regione (invariato). Nessun `--` nel commento XML nuovo, nessun apostrofo nelle stringhe nuove.

### Come si prova

`SerataTest` (`:core`: l'ordine del girone con coppie e posti, lo spareggio e le sue parita', i game con il set interrotto, niente spareggio senza i tre risultati o con piu' di quattro presenti, il ritorno alla rotazione, `serata2` scritta e riletta e `serata1` letta ancora), `SerataDallaDashboardTest` (il girone decide l'ordine prima della prima partita), `MainViewModelTest` (la partita salvata registra coppie e game), `SerataActivityTest` (la riga dello spareggio). Falsificati: con l'ordine di prima e senza lo spareggio 15 test di `:core` diventano rossi.

### Debiti dichiarati

1. **Non visto a schermo**: nessun SDK Android nel container; `:core` eseguito sulla JVM, l'app verificata dalla CI (Robolectric).
2. **L'ordine della dashboard e' preso dalla richiesta** (il file `js/ui.js` sta nell'altro repository e non e' stato riletto in questo passo).
3. **Piu' di quattro presenti**: lo spareggio non c'e', perche' la rotazione cambia i quattro.
4. **Il girone di chi tiene il telefono** non si preseleziona: servirebbe sapere quale giocatore della dashboard e' l'account collegato.

## Diretta - 9 ottobre 2026 (D-2)

Punto 6 della sezione 4 di `PIANO_PADEL_ELITE.md`, lato app. La diretta la trasmettono **solo owner e admin del gruppo** (decisione del 7 ottobre). Dipende dalla migrazione 67 della dashboard (non in produzione): nessuna prova ha toccato il Supabase vero, tutto col server finto (MockWebServer) nei test JVM. Senza configurazione, senza accesso, senza gruppo scelto con ruolo `owner` o `admin`, o fuori dal padel: **nessun comando, nessuna richiesta, app identica a oggi**. L'orologio non cambia.

### Contratto del server (migrazione 67)

- `POST /rest/v1/rpc/publish_live_from_app` con `{"p_group": uuid, "p_payload": <file v2 PARZIALE>}` risponde `{"live":{"id","group_id","external_id":"match:<matchId>","status":"live"|"ended","updated_at"},"created":bool,"written":bool}`. `written: false` vuol dire file identico al salvato oppure diretta gia' chiusa: l'app distingue dallo `status` (`ended` = definitivo, smette di trasmettere quella partita; `live` = identico, continua).
- `POST /rest/v1/rpc/end_live_from_app` con `{"p_group", "p_match_id": "<matchId>", "p_payload": <file v2 FINALE> | null}` risponde `{"live":{...,"status":"ended"},"ended":true,"already_ended":bool,"written":bool}` (`written`: il file finale e' stato scritto). A partita finita si chiude col file finale (vincitore); scartata o interrotta senza. **Contratto finale (PR #221 della dashboard, `docs/SCOREBOARD_FORMAT.md` sezione 7):** col `p_payload` il `matchId` del file deve coincidere con `p_match_id` (altrimenti `invalid_payload`, `details` = `matchId`: nell'app e' lo stesso `matchUuid`); **solo chi ha APERTO la diretta puo' scrivere il file finale**, senza payload chiude qualunque owner o admin; una diretta chiusa e' definitiva (publish risponde `written: false`, un end col file non la tocca piu': `already_ended: true`); un file con meno punti (annullamento) e' accettato. L'app, se un end col file e' rifiutato (`invalid_payload`, `payload_too_large`, `not_authorized`, `live_conflict`), rifa' l'end senza file, cosi' la diretta si chiude comunque.
- **File v2 parziale**: stesso formato dell'export v2, costruito da `MatchExporter.buildPartial` (core), che e' `build` senza l'obbligo dei punti: stesso rigioco, stessa timeline (anche `[]` a inizio partita), `setScores` solo dei set chiusi, `winnerTeam` nullo finche' non finisce, quattro giocatori obbligatori. I `padelPlayerId` sono i collegamenti R-1 del gruppo di destinazione, come nell'invio. L'inizio (`startedAt`) manca finche' non c'e' il primo punto (campo facoltativo del formato). Tetti: 200 KB (`InvioRunner.MAX_PAYLOAD_BYTES`), 3000 punti, nomi di 60 caratteri (rifiutati, non tagliati).
- **Errori, letti dal campo `message` e mai dallo stato HTTP** (PostgREST manda 54xxx, 55xxx e P0002 come 500; `PadelEliteApi.classifyLiveError`):

| `message` | Risultato | Cosa fa la diretta |
|---|---|---|
| `not_authenticated` (o 401) | `NotAuthenticated`, dopo un rinnovo del token gia' tentato | in attesa "accedi di nuovo", riprova ogni 30 s |
| `not_authorized` | `NotAuthorized` | si spegne ("non sei admin"), non si ritenta, niente da chiudere |
| `invalid_payload` | `InvalidPayload(details)` | si spegne scrivendo il dettaglio, chiusura senza file |
| `payload_too_large` | `PayloadTooLarge` | si spegne, chiusura senza file |
| `live_conflict` (`details` = `other_sender`) | `LiveConflict` | si spegne ("un altro admin trasmette"); **non** la chiude, e' la sua |
| `live_full` (`details` = `group_live`) | `LiveFull` | in attesa, riprova ogni 30 s |
| `join_code_exhausted` | `JoinCodeExhausted` | in attesa, riprova ogni 30 s con lo stesso file |
| `not_found` (solo end) | `NotFound` | ok: gia' sparita |
| 23505, 40P01, 5xx senza message noto, 408, 429, rete | `Network` | in attesa "manca la rete", riprova con attesa crescente (5 s, 10 s, 20 s, 30 s) o al punto dopo |
| altro (404 se la 67 non c'e') | `Unexpected(status)` | cinque giri, poi si spegne ("il server non risponde come previsto") |

### Come pubblica

- **Un solo consumatore** (`Diretta`, in `padelelite/`) su un canale `CONFLATED`: le richieste vanno **in serie** (mai due in volo per la stessa partita), con almeno **un secondo** fra una e l'altra; un cambiamento arrivato mentre se ne fa una si fonde con gli altri e, quando tocca, il file si costruisce dall'**ultima** foto (registro, rose, origine: immutabile, catturata sul thread principale; il file si rifa' altrove con un motore nuovo rigiocato dal registro, mai quello vivo). Un file identico all'ultimo consegnato non rifa' la richiesta. **Nessuna coda persistente**: conta solo l'ultimo stato.
- **Cosa la fa partire**: ogni cambiamento del registro passa da `MainViewModel.publishEngineState` (un punto sul telefono, un annullamento, un arretrato o un tocco dell'orologio: e' lo stesso imbuto che aggiorna l'orologio), e ogni cambiamento delle rose da `salvaRoseDellaRigaViva` (servono quattro giocatori: finche' mancano la diretta e' in attesa e non fa richieste).
- **Offline**: la richiesta salta, lo stato dice "manca la rete", si riprova con attesa crescente e al punto dopo. **Non** c'e' un ascoltatore della connettivita': il ritorno della rete si scopre con il ritentativo (al piu' 30 s), non subito.
- **Dove vive**: coroutine nello scope dell'applicazione (`PadelEliteServices`, `SupervisorJob` su `Dispatchers.Default`), non del ViewModel: sopravvive a rotazione e a un ViewModel ricreato. **Niente WorkManager per i punti** (e' un flusso vivo legato alla partita).
- **App in background**: l'orologio continua a mandare punti al telefono, e il processo li riceve finche' il sistema non lo ferma. **Limite dichiarato**: nel padel il cronometro non parte e il servizio in primo piano (`MatchTimerService`) non c'e', quindi con l'app in secondo piano a lungo Android puo' sospendere o chiudere il processo, e con lui la diretta. In gioco lo schermo resta acceso (`FLAG_KEEP_SCREEN_ON` dopo il primo punto), quindi nel caso normale l'app e' in primo piano. Un servizio in primo piano per la diretta (con la sua notifica e il suo tipo) non e' stato aggiunto: e' una decisione da prendere, vedi i debiti. **D-3:** il servizio in primo piano c'e' (vedi la sezione D-3); il limite resta solo se il sistema nega l'avvio in secondo piano o a Android 15 dopo 6 ore di `dataSync` in un giorno, ed e' detto nel foglio.
- **Cosa persiste** (`DirettaStore`, preferenze `padel_elite_diretta`): `matchId`, gruppo (id e nome), e "accesa" (c'e' il valore). Niente altro: il punteggio vive nel registro. **D-3:** anche le chiusure in sospeso (`chiusure_pendenti`, scritte con `commit` prima di consegnarle).

### Chiusura

- `endMatch` (dall'app o dall'orologio): **`Diretta.chiudi(conRisultato = true)`** appena la chiusura e' presa; da li' nessuna pubblicazione. La richiesta passa a **`DirettaChiusuraWorker`** (WorkManager, **lavoro unico per `matchId`**, `KEEP`, rete richiesta, attesa crescente da 30 s, fino a 10 tentativi): il risultato finale non deve perdersi. Il file finale lo rifa' `DirettaChiusuraRunner` dal database con i collegamenti R-1 del gruppo (lo stesso file dell'invio alla casella). Se la riga non c'e' ancora aspetta tre giri, poi chiude senza file; una partita chiusa **senza vincitore** (interrotta) porta comunque il suo file dal database, col `winnerTeam` nullo (il server lo accetta); il lavoro legge la partita solo quando e' **chiusa** (`MatchRepository.isClosedByUuid`: la riga viva a meta' non e' il file finale, si aspetta `closeMatch`); senza accesso valido (`not_authenticated`) ritenta con un tetto di cinque giri invece di finire come riuscito; se il server rifiuta il file finale (`invalid_payload`, `payload_too_large`) chiude subito senza, almeno la diretta finisce.
- `discardMatch`, cambio sport e "Interrompi" a mano: **`chiudi(conRisultato = false)`**, `p_payload: null`.
- **L'invio alla casella (M-2) resta com'e' e indipendente**: stesso `matchId`, stesso file, altro canale.
- **Ripristino**: al ritorno della partita (`fineRipristino`) `Diretta.riprendi`: se la diretta salvata e' per **quella** partita riprende e pubblica; se e' per un'altra, o la partita non c'e' piu', la diretta e' abbandonata e si chiude senza file. Prima del primo punto la partita non ha una riga: se il processo muore li la diretta si chiude al riavvio.
- **L'identificativo della partita** nasce **all'accensione** se manca (di norma nasce col primo punto): `MainViewModel.accendiLaDiretta` lo mette in `matchUuid`, e la riga viva, l'invio e la chiusura lo riusano. L'orologio continua a ricevere `""` finche' non c'e' il primo punto.

### Piano UI (CLAUDE.md, punto 3)

- **Dove si accende e perche'**: nel **foglio PARTITA**, in un gruppo tonale nuovo "Diretta" (`diretta_card`, `Widget.App.Group`) fra Serata e Rose. In gioco c'e' un numero per lato e niente altro; la diretta si prepara dove si compongono le coppie, ed e' una scelta che si fa una volta per partita. Non e' nel dialogo di fine partita (quello riguarda il file salvato) ne' nella schermata "Padel Elite" (quella e' per l'accesso e il gruppo).
- **Compito e valore**: sapere se si sta trasmettendo e a chi; il valore e' la riga di stato. **Gerarchia**: titolo del gruppo (`Caption`), riga con l'etichetta "Trasmetti in diretta" (`RowTitle` 500) e l'interruttore, sotto la riga di stato (icona e parola, `Caption` 400). Pesi: 400 e 500.
- **Componenti riusati**: `Widget.App.Group`, `Widget.App.Switch` (`MaterialSwitch`, lime e spunta: lo stato non e' mai il solo colore), `TextAppearance.App.RowTitle` e `Caption`, `MaterialAlertDialogBuilder` per la conferma. **Nessun bottone primario** nella regione (il primario del foglio resta Fine partita): un interruttore e' una scelta, non un invio.
- **Icona nuova**: `sensors` ("Diretta, in onda") aggiunta alla tabella G-3, Material Symbols outlined peso 400 scaricato com'e' da `google/material-design-icons`, `ic_sensors`. Le altre sono righe che ci sono: `schedule` (non trasmette, in attesa), `cancel` (spenta), `check_circle` (chiusa). Le icone della riga di stato sono decorative (`importantForAccessibility="no"`): il significato e' nel testo. L'interruttore ha `contentDescription` (il testo dell'etichetta).
- **Stati**: spenta (il gruppo c'e' solo se si puo' trasmettere, interruttore spento, nessuna riga); **in diretta**: "In diretta su <gruppo>" (icona `sensors`, lime); **non trasmette, ripartira' da sola**: rete, accedi di nuovo, servono quattro giocatori, nome oltre 60 caratteri, troppe dirette, codice, risposta inattesa (icona `schedule`, avviso); **spenta con un perche'**: non sei admin, file rifiutato (col campo), file troppo grande, oltre 3000 punti, un altro admin trasmette, gia' chiusa, il server non risponde come previsto (icona `cancel`, errore); **chiusa**: "Diretta chiusa" (icona `check_circle`, secondario). Premuto e fuoco dell'interruttore: quelli di `Widget.App.Switch`. Sola lettura: il gruppo non c'e' senza permesso. Movimento ridotto: nessun movimento aggiunto.
- **Interrompere e' definitivo** (anche sul server: la diretta chiusa non si riapre per quella stessa partita), quindi spegnere l'interruttore chiede conferma ("Interrompere la diretta?", "Interrompi" / "Continua"); riaccenderla per la stessa partita non e' possibile e lo dice (snackbar). Per la partita dopo l'interruttore e' di nuovo libero.
- **Movimento**: nessuno nuovo (nessun ciclo; "in diretta" e' una parola e un'icona ferma). **Schermo stretto e 200%**: la riga dell'etichetta e' `weight` con l'interruttore a destra, il testo di stato va a capo, le altezze sono `wrap_content` con minimo 48dp dell'interruttore. **Conflitti**: nessuno nuovo; il lime per "in diretta" e l'interruttore sono quelli del conflitto 7 (successo = lime): c'e' sempre la parola.
- **Pattern vietati** (`review.md`): niente maiuscolo, emoji, ombre, colori scritti a mano (`elite_lime`, `elite_warning`, `elite_error`, `elite_text_secondary` e `elite_text_primary`), un solo accento, niente primario in piu'. **Non visto a schermo** (nessun emulatore in questo passo).

### Come si prova

`DirettaTest` (controllo, server finto: file parziale a inizio e a meta' partita, conflazione di dieci punti in 300 ms, serie, errori letti dal `message` con HTTP 500, rete assente, diretta gia' chiusa, meno di quattro giocatori, nome oltre 60 caratteri, silenzio dopo la chiusura, ripresa e abbandono al ripristino), `DirettaChiusuraTest` (file finale col vincitore, scarto senza file, interrotta senza file, ritentativo con lo stesso file, file finale rifiutato, riga non ancora scritta, lavoro unico per partita, visibilita' per ruolo), `DirettaNelViewModelTest` (accensione prima della partita, punti, fine partita, scarto, spegnimento, ripristino, funzione spenta, member), `MatchExportTest` (tre test del file parziale). **Falsificati** (mutando il codice di produzione e vedendo i test rossi): ammettere il ruolo `member` rompe i test di visibilita' (tre); decidere dallo stato HTTP (500 = rete) rompe i test degli errori letti dal `message` (cinque, piu' due di chiusura); non fermare il ciclo a `chiudi` rompe "dopo la chiusura nessuna pubblicazione" e "fine partita chiude la diretta"; canale illimitato senza deduplica porta le richieste da 2-3 a 6 e rompe la conflazione; dare sempre il file finale rompe "interrotta senza vincitore". Il tetto dei 3000 punti non ha test: il motore del padel non arriva a quel numero (dopo 24 punti la partita e' finita e i tocchi sono inerti), e' una guardia.

### Debiti dichiarati

1. ~~**App in secondo piano**: nessun servizio in primo piano per la diretta.~~ **Chiuso da D-3** (servizio `dataSync` e notifica fissa, sezione D-3 qui sotto).
2. **Ritorno della rete**: si scopre con il ritentativo (al piu' 30 s), non con un ascoltatore di connettivita'.
3. ~~**Il ruolo e' l'ultimo letto**: chi aveva scelto il gruppo prima di D-2 non vede il comando finche' non apre la schermata "Padel Elite"; un admin retrocesso lo vede ancora.~~ **Chiuso da D-3:** il ruolo si rilegge una volta per avvio. ~~Resta: un gruppo da cui si e' stati tolti non viene dimenticato.~~ **Chiuso dal debito 16.**
4. ~~**La chiusura finale non ha uno stato visibile**: "Diretta chiusa" vale subito anche se il lavoro in coda ritenta.~~ **Chiuso da D-3:** il foglio dice "Chiusura della diretta in corso" mentre il lavoro e' in coda e "La diretta non si e' chiusa" se rinuncia.
5. ~~Partita senza vincitore chiusa senza file.~~ **Chiuso dalla revisione:** porta il suo file col vincitore nullo.
6. **Ripristino prima del primo punto**: la partita non ha ancora una riga, la diretta si chiude al riavvio e va riaccesa.
7. **Non provato** contro il server vero (migrazione 67 non in produzione), ne' a schermo ne' sugli emulatori: il layout e' provato solo con la compilazione e con le prove sul ViewModel.

### Correzioni dalla revisione (11 ottobre 2026)

- **Giocatore sostituito a meta' partita**: se l'ordine di servizio o un servitore della timeline non e' fra i quattro del file, la diretta resta **in attesa** (motivo nuovo, "un giocatore e' stato sostituito a partita iniziata, rimetti quello di prima") senza chiudere; riparte da sola quando i quattro tornano coerenti.
- **Id della partita**: accesa e poi spenta o scartata prima del primo punto, la partita si scorda l'id (`matchUuid` e inizio azzerati), cosi' la prossima accensione ne ha uno nuovo; una riga viva senza id (versione vecchia) non si trasmette.
- **Eccezioni**: un'eccezione nella costruzione o nella pubblicazione (il Keystore nel rinnovo del token) mette la diretta in attesa ("errore imprevisto") e si riprova con attesa crescente; lo scope ha un `CoroutineExceptionHandler` di sicurezza; la `CancellationException` si rilancia sempre (anche nella lettura dei collegamenti).
- **Richiesta in volo**: non si annulla (`NonCancellable`), per non perdere il refresh token ruotato; la chiusura ([`Diretta.chiudiDopo`]) parte **dopo** la sua fine, cosi' una pubblicazione che crea la riga non arriva dopo l'end. Conseguenza: `allaChiusura` e' asincrona rispetto a `chiudi`.
- **Messaggio**: con la diretta non disponibile (sessione, ruolo, sport) il tocco dice perche', non "chiusa e non si puo' riaprire".
- Punto 10 della revisione: chi ha scelto il gruppo prima di D-2 non ha il ruolo salvato e non vede il comando finche' non riapre la schermata "Padel Elite" (debito 3, da scrivere nelle note di rilascio).

### Debiti dal controllo delle correzioni (11 ottobre 2026, Opus: unibile)

8. ~~**Chiusura persa se il processo muore con una richiesta in volo.**~~ **Chiuso da D-3:** la chiusura si segna in `DirettaStore` prima di consegnarla e si accoda al ripristino. La finestra residua e' chiusa dal debito 13.
9. ~~**Un `Error` (non `Exception`) nel ciclo lo ferma senza aggiornare lo stato.**~~ **Chiuso da D-3:** un `finally` in `trasmetti` ferma la diretta con il motivo `ERRORE_GRAVE` e la chiude sul server.
10. ~~**Eccezione persistente**: nessun tetto, nessun log, nome vuoto per una classe anonima.~~ **Chiuso da D-3:** dieci eccezioni di fila fermano la diretta (`ERRORE_GRAVE`), l'eccezione va nel log (`Log.w`) e il nome e' `simpleName` o, se vuoto, il nome intero.
11. ~~Il KDoc di `DirettaChiusura` e il messaggio della riga viva senza uuid.~~ **Chiuso da D-3:** il KDoc dice che anche la partita senza vincitore porta il file; la snackbar per una riga viva senza identificativo dice "non disponibile per questa partita: e' cominciata con una versione precedente" (`diretta_unavailable_old_match`).


## Diretta, i limiti - 11 ottobre 2026 (D-3)

Chiude i debiti 1, 3, 4, 8, 9, 10 e 11 di D-2. Nessuna prova ha toccato il Supabase vero (server finto) e **niente e' stato visto a schermo**. Decisioni del proprietario (11 ottobre): servizio in primo piano di tipo **`dataSync`**, **separato dal cronometro** (`MatchTimerService` e' `specialUse` e nel padel non parte); notifica fissa «In diretta su <gruppo> · <punteggio>» con il comando «Interrompi diretta» che **chiede conferma** prima di chiudere.

### Il servizio

- **`DirettaService`** (`padelelite/`, `foregroundServiceType="dataSync"`, `exported="false"`, permesso `FOREGROUND_SERVICE_DATA_SYNC`). **Non contiene la logica della diretta**: la classe `Diretta` resta nello scope dell'applicazione; il servizio la tiene viva (esistendo) e ne mostra lo stato.
- **Chi lo avvia: il controllo. Chi lo ferma: il servizio stesso, mai nessuno da fuori.** `DirettaServizioControllo` guarda `Diretta.stato` (e' creato insieme alla diretta, `PadelEliteServices.direttaServizio`) e **solo avvia**: da SPENTA/FERMA/CHIUSA a IN_CORSO o IN_ATTESA, cioe' all'accensione (tocco sull'interruttore: app in primo piano, avvio consentito) e al ripristino (`riprendi`). Un passaggio da in corso a in attesa non riavvia niente. **Non c'e' uno `stopService` dall'esterno**: fermarlo prima che abbia chiamato `startForeground()` fa cadere l'app («startForegroundService() did not then call startForeground()»), e basta una diretta che passa a FERMA pochi millisecondi dopo l'accensione. Il servizio entra **sempre** in primo piano per primo, poi se la diretta non e' piu' accesa se ne va (allo `onStartCommand`), e per tutta la vita guarda `Diretta.stato` e si ferma appena non e' piu' accesa, senza aspettare la pausa fra due notifiche: fine partita, scarto, cambio sport, errore definitivo del server, `Error` nel ciclo, tetto delle eccezioni, interruttore, notifica. Il controllo tiene solo la fase detta nel foglio.
- **Avvio negato.** `DirettaServiceAvviatore.avvia()` cattura `IllegalStateException` (`ForegroundServiceStartNotAllowedException` ne e' una sottoclasse, esiste dall'API 31) e `SecurityException` come `MatchTimerService.startForegroundWithPermissionCheck`; lo stesso fa il servizio attorno a `startForeground`. La diretta prosegue senza servizio e lo stato lo dice: `DirettaServizioFase.NON_AVVIATO`.
- **Android 15.** `dataSync` ha un tetto di 6 ore al giorno: `onTimeout(startId, fgsType)` ferma il servizio (`stopForeground` + `stopSelf`, subito, o il processo cade in ANR) e scrive `SCADUTO`; **la diretta continua finche' il processo vive**. Il foglio lo dice (sotto).
- **Senza il permesso per le notifiche** il servizio parte lo stesso (come il cronometro): sopra Android 13 la notifica e' soppressa dal sistema ma il servizio c'e', e non si prova a riscriverla.
- **Ricevuto ma non provato su dispositivo:** il comportamento reale di `startForegroundService` dopo la morte del processo, e il tetto delle 6 ore (si prova solo chiamando `onTimeout` a mano).

### La notifica

- **Canale** `scoreboard_diretta_channel`, importanza **bassa**, niente suono, niente vibrazione, niente badge (nome e descrizione in it/en). **Icona** `notifica_sensors`: lo stesso simbolo `sensors` della tabella G-3 in una variante **senza attributi del tema** (`ic_sensors` ha `android:tint="?attr/colorControlNormal"`, un attributo del tema che l'icona di una notifica, inflazionata dal sistema, non ha; la nota di G-3 sul cronometro chiedeva proprio «una variante senza attributi del tema»); non comincia per `ic_` perche' non e' un'icona dell'interfaccia (`IconeDelTelefonoTest` non la tocca) e il bianco e' la sagoma che il sistema tinge.
- **Testo.** Titolo «In diretta su <gruppo>» (`diretta_live`, la stessa frase del foglio); testo il punteggio corrente, **"40-15 · 6-4 · 3-2"** (punto, poi set e game: `ScoreDisplay.side1Primary-side2Primary` e il dettaglio di `side1Secondary`). Quando la diretta non trasmette il testo e' il **motivo**, con le stesse frasi del foglio PARTITA («Non trasmette: manca la rete. Riprovo da solo»), non una nuova frase per ogni motivo. Nota: il proprietario aveva proposto «Diretta in attesa: <motivo>»; si era riusata la frase esistente. **Cambiato il 27 ottobre (debito 14):** la notifica dice «Diretta in attesa: <motivo>» (`testoDiAttesaInNotifica`), il foglio no.
- **Aggiornamento al massimo una volta al secondo**, senza notifiche a raffica: il punteggio nasce nel **ciclo di pubblicazione** (lo stesso motore del file, `Diretta.punteggio`), che fa gia' al piu' una richiesta al secondo; il servizio legge `combine(stato, punteggio).conflate()` e dopo ogni scrittura aspetta 1 s; un testo uguale a quello gia' scritto non si riscrive.
- **Tocco** = apre l'app sul foglio PARTITA, dove sta il gruppo Diretta. **«Interrompi diretta»** = apre l'app sul foglio e mostra **la stessa conferma** dell'interruttore («Interrompere la diretta?», «Interrompi» / «Continua»); «Continua» lascia tutto com'e'. Se la diretta intanto si e' chiusa non c'e' niente da chiedere e si apre solo il foglio.
- **Scelta per la conferma: un intento verso `MainActivity` con un extra, non un'activity di conferma trasparente.** Una notifica non puo' mostrare un dialogo. L'activity trasparente avrebbe voluto un'altra voce nel manifest, un tema suo e una seconda copia del dialogo (parole, bottoni, comportamento di «Continua»); l'intento riusa `chiediDiSpegnereLaDiretta` e porta l'utente dove la diretta si vede e si governa. Costo: l'app passa in primo piano; per uno stop **definitivo** e' un pregio (si vede il punteggio prima di chiudere). Dettagli: azione dell'intento distinta per APRI e INTERROMPI (gli extra non bastano a distinguere due `PendingIntent`), `SINGLE_TOP | CLEAR_TOP | NEW_TASK` perche' `MainActivity` e' standard e la schermata gia' aperta riceva `onNewIntent` invece di raddoppiare, e l'extra si **consuma** dopo l'uso, e un'activity **ricreata** (`savedInstanceState != null`, anche dopo la morte del processo) o riaperta dalla cronologia (`FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY`) non riesegue l'azione (`DirettaIntent.azioneDaEseguire`): l'intento e' quello di prima e il dialogo non deve ricomparire.

### Il resto

- **Ruolo (debito 3).** `PadelEliteServices.aggiornaIlRuolo()` lancia `MainViewModel` all'avvio: **una sola** richiesta (`group_members`) per avvio del processo, anche se fallisce; aggiorna nome e ruolo del gruppo scelto, ma solo se alla risposta la sessione c'e' ancora e il gruppo scelto e' ancora quello letto (nel frattempo l'utente puo' aver scelto un altro gruppo o essere uscito: il risultato vecchio non si scrive); senza configurazione, accesso o gruppo scelto non fa niente e non consuma la lettura; ogni eccezione e' inghiottita, nessun errore mostrato. Se il ruolo cambia il foglio si ridisegna (`ruoloDirettaAggiornato`). Chi aveva scelto il gruppo prima di D-2 vede l'interruttore, un admin retrocesso non lo vede piu'.
- **Chiusura in sospeso (debito 8).** Prima di `chiudiDopo`, `Diretta` scrive in `DirettaStore` (set di stringhe `chiusure_pendenti`, `commit` sincrono) la richiesta (matchId, gruppo, con o senza risultato); la toglie **dopo** che `allaChiusura` (l'accodamento) e' tornata. `riprendi` all'avvio/ripristino accoda le pendenti (lavoro unico per `matchId`, KEEP: due accodamenti non ne fanno due), salvo quelle la cui richiesta in volo e' ancora di questo processo; e una diretta salvata la cui chiusura e' pendente **non si riaccende**. Anche l'abbandono al ripristino (diretta salvata per un'altra partita) e le fermate con chiusura sul server passano dallo stesso segno.
- **`Error` nel ciclo (debito 9).** `trasmetti` e' in un `try/finally`: se il ciclo esce per un `Throwable` non di cancellazione, `ferma(ERRORE_GRAVE, nome della classe, chiudiSulServer = ...)`, con la chiusura sul server **solo se al server e' arrivato almeno un file** (`Attiva.pubblicata`; dopo un ripristino si presume di si, perche' il processo prima potrebbe aver pubblicato); lo stato diventa FERMA («Diretta spenta: errore interno (…)»), il servizio si ferma, e l'`Error` arriva comunque al `CoroutineExceptionHandler` dello scope (che lo scrive nel log).
- **Chiusura finale visibile (debito 4).** `DirettaChiusuraWorker.fase(context, matchId)` legge il lavoro unico di chiusura da WorkManager (`getWorkInfosForUniqueWorkFlow`) e lo riduce a `ChiusuraFase`: NESSUNA, IN_CORSO (in coda, in esecuzione o in ritentativo, **anche mentre aspetta la rete**), FALLITA. Mentre la chiusura aspetta la richiesta in volo (il lavoro non c'e' ancora: `Diretta.chiusureInVolo`) il foglio dice gia' «Chiusura della diretta in corso». Il foglio PARTITA, dove di solito c'e' «Diretta chiusa», dice **«Chiusura della diretta in corso»** o **«La diretta non si e' chiusa: la chiude la dashboard o si chiude da sola entro 6 ore»**: una riga, nella stessa riga di stato. Per poter dire il vero `DirettaChiusuraRunner` ora **rinuncia davvero** (`RunOutcome.GIVE_UP` -> `Result.failure()`) quando senza accesso valido o con risposte inattese ha finito i suoi cinque tentativi, e subito quando il server nega il permesso o la diretta e' di un altro admin (`not_authorized`, `live_conflict`, anche dopo il tentativo senza file: la diretta NON e' chiusa, prima risultava `DONE`): prima tornava `DONE` e il lavoro risultava riuscito senza aver chiuso niente. Non fatto: la riga nella card dello storico (la diretta non ha una voce nello storico).
- **Eccezione persistente (debito 10).** Vedi il debito: dieci eccezioni di fila (`Diretta.MAX_ECCEZIONI`; con i ritentativi da 5 a 30 s sono circa quattro minuti e mezzo) fermano la diretta con `ERRORE_GRAVE` e (se almeno un file e' arrivato) la chiudono sul server; il contatore riparte da zero a ogni giro riuscito.

### Piano UI (CLAUDE.md, punto 3)

- **Dove e perche'.** Nessuna schermata nuova. La notifica fissa e' lo strumento di sistema per un servizio in primo piano; il resto e' la riga di stato che c'era gia' nel gruppo Diretta del foglio PARTITA. **Compito e valore:** sapere se si sta trasmettendo e come finisce; il valore e' la riga di stato (una).
- **Gerarchia e componenti riusati:** `Widget.App.Group`, riga di stato con icona e parola (`direttaLook`), `MaterialAlertDialogBuilder` per la conferma (invariato). **Niente bottoni nuovi**, nessun primario in piu'. Notifica: titolo, testo, un'azione di testo.
- **Stati della riga** (parola + icona, mai il solo colore): in diretta (`ic_sensors`, lime); **senza notifica fissa** («In diretta su X. Senza notifica fissa: tieni l'app aperta», avvio negato) e **notifica scaduta** («…La notifica fissa si e' fermata dopo 6 ore: tieni l'app aperta»), stessa icona e colore di «in diretta» perche' si trasmette; non trasmette (`ic_schedule`, avviso); spenta col motivo (`ic_cancel`, errore), con il motivo nuovo «errore interno»; **chiusura in corso** (`ic_schedule`, secondario); **non chiusa** (`ic_warning`, avviso: la dashboard la chiude, non e' un errore dell'utente); chiusa (`ic_check_circle`, secondario). Sola lettura, premuto e fuoco: invariati (nessun controllo nuovo).
- **Movimento:** nessuno (nessuna icona gira, nessun ciclo; «chiusura in corso» e' una parola). **Accessibilita':** testi di stato letti da TalkBack come prima; l'azione della notifica e' testuale. **Schermo stretto e 200%:** la riga di stato va a capo (invariata); le frasi nuove sono piu' lunghe (la piu' lunga e' quella della chiusura non riuscita) e il testo va a capo. **Conflitti:** nessuno nuovo.
- **Pattern vietati (`review.md`):** niente maiuscolo, emoji, ombre, colori scritti a mano (`elite_*` per i token, il vettore della notifica ha il solo bianco-sagoma), un solo accento. **Non visto a schermo.**

### Come si prova

`DirettaLimitiTest` (chiusura in sospeso con processo "ucciso" = nuovo `Diretta` e nuovo `DirettaStore` sugli stessi dati, diretta chiusa un attimo prima della morte che non si riaccende, store con piu' chiusure, `Error` nel ciclo, tetto delle eccezioni con classe anonima, punteggio), `DirettaServizioTest` (il controllo con la diretta vera: parte all'accensione e al ripristino, una volta sola nei passaggi in attesa, torna a riposo a fine partita / scarto / interruttore / errore del server / `Error`; avvio negato; l'avviatore vero e il suo rifiuto), `DirettaServiceTest` (Robolectric, il servizio vero: notifica, canale, azioni e intenti, aggiornamento al secondo a tempo virtuale, motivo di attesa, arresto da solo e subito, **nessuno `stopService` se la diretta si chiude prima che il servizio parta**, `onTimeout`, `startForeground` negato, senza permesso notifiche), `RuoloEChiusuraVisibileTest` (ruolo si / no / invariato / fallisce in silenzio / eccezione / una sola lettura / senza accesso o gruppo / funzione spenta; fasi della chiusura e righe del foglio in it/en), `DirettaNelViewModelTest` (+1: all'avvio il ruolo si rilegge una volta e il foglio e' avvertito), `DirettaChiusuraTest` (il tetto senza accesso ora e' `GIVE_UP`). **Falsificati** (mutando il codice di produzione e vedendo i test rossi): togliere il segno della chiusura prima di consegnarla (1 test: la chiusura sopravvissuta al processo ucciso), disattivare il `finally` dell'`Error` (3: il suo test, quello del servizio che si ferma, e quello dello stato), togliere `ferma()` dal controllo (7 test del servizio che si ferma), togliere la guardia della lettura unica del ruolo (2), togliere l'arresto da `onTimeout` (1). Il dialogo di «Interrompi diretta» **non e' provato**: `MainActivity` non si lancia sotto Robolectric in questo progetto (le prove della schermata sono strumentate); sono provati l'intento della notifica, la lettura e il consumo dell'azione (e le regole per non rieseguirla) e lo spegnimento che il dialogo conferma (`Diretta.spegni`).

### Nota per la Play Console

Con `targetSdk` 34 (Android 14) in su ogni tipo di servizio in primo piano va dichiarato nella Play Console (Contenuto dell'app, «Autorizzazioni per i servizi in primo piano»); per `dataSync` servono la descrizione dell'uso, l'impatto sull'utente se il compito si interrompe e un video che mostri l'avvio. Il form e' in inglese; la versione italiana serve al proprietario. Testo proposto:

- **Italiano.** «Quando l'utente accende l'interruttore "Trasmetti in diretta", l'app invia il punteggio della partita di padel in corso al server di Padel Elite a ogni punto, perche' i giocatori del suo gruppo possano seguirla in tempo reale. Il servizio in primo piano mostra una notifica fissa con il gruppo e il punteggio e impedisce al sistema di sospendere l'invio quando lo schermo si spegne o l'app passa in secondo piano. Parte solo su azione dell'utente, si ferma da solo a fine partita o quando l'utente interrompe la diretta (anche dalla notifica) e non trasferisce altri dati.»
- **English.** "When the user turns on the \"Broadcast live\" switch, the app sends the score of the padel match in progress to the Padel Elite server after every point, so the players of the user's group can follow it in real time. The foreground service shows an ongoing notification with the group name and the score, and keeps the system from suspending the upload when the screen turns off or the app goes to the background. It starts only on a user action, stops by itself at the end of the match or when the user stops the broadcast (also from the notification), and transfers no other data."
- **Impatto se interrotto / If interrupted.** Gli spettatori vedono il punteggio fermo all'ultimo invio; nulla si perde (la partita si salva e la diretta si chiude da sola entro 6 ore). / Viewers see the score frozen at the last upload; nothing is lost (the match is saved and the broadcast closes on its own within 6 hours).
- **Tipo:** `dataSync`; permesso `FOREGROUND_SERVICE_DATA_SYNC`. Android 15 limita `dataSync` a 6 ore al giorno: l'app gestisce `onTimeout` (si ferma, la diretta continua con l'app aperta).
- **Video richiesto:** accendere la diretta, mostrare la notifica, mandare l'app in secondo piano, toccare «Interrompi diretta».

### Debiti dichiarati dopo D-3

12. ~~**Non visto su dispositivo.** Servizio, notifica, tetto delle 6 ore, rifiuto in secondo piano e dialogo da notifica si provano solo sulla JVM (Robolectric) e con la compilazione dello strumentato; manca una prova su telefono con l'app in secondo piano a lungo, e su Android 15.~~ **Chiuso in parte (27 ottobre, vedi "Debiti 12, 14 e 20"):** servizio, notifica, «Interrompi diretta» con la conferma e tetto di `dataSync` (accorciato a 60 s) visti sull'emulatore `Pixel_9a_Test`, Android 16, senza server. **Resta:** il rifiuto dell'avvio in secondo piano (un processo strumentato non va mai in secondo piano), la diretta che trasmette davvero (serve un server: quello vero e' produzione), un telefono vero con l'app in secondo piano a lungo.
13. ~~**Le pendenti si accodano solo quando `MainViewModel` finisce il ripristino.** Se il processo risorge senza aprire la schermata (un messaggio dell'orologio), la chiusura resta segnata finche' l'app non si apre; e fra `allaChiusura` e la scrittura del lavoro di WorkManager (asincrona) c'e' ancora una finestra di millisecondi.~~ **Chiuso (vedi "Debiti 13, 15 e 16 chiusi" qui sotto):** le pendenti si accodano a ogni avvio del processo, e il segno si toglie solo a lavoro scritto.
14. ~~**Il testo della notifica quando non trasmette e' la frase del foglio**, non «Diretta in attesa: <motivo>» come proposto.~~ **Chiuso:** la notifica dice «Diretta in attesa: <motivo>» con un motivo breve; il foglio tiene la sua frase.
15. ~~**Dopo il tetto delle 6 ore non c'e' piu' notifica fissa** fino alla diretta successiva; il foglio lo dice, la notifica no. Una notifica ordinaria con lo stesso avviso non e' stata aggiunta.~~ **Chiuso:** un avviso ordinario che dice la condizione («La diretta continua solo con questa app aperta»), non lo stato: resta vero anche a processo morto.
16. ~~**Un gruppo da cui si e' stati tolti** non viene dimenticato dalla rilettura del ruolo (si aggiorna solo se il gruppo c'e' ancora nell'elenco).~~ **Chiuso:** il gruppo si dimentica e la diretta si spegne con un motivo suo.
17. **`segnaPendente` e `togliPendente` scrivono con `commit` sul thread di chi chiama**, che per `chiudi` puo' essere il principale: sono pochi byte, ma e' I/O. Spostarli su un altro thread romperebbe l'ordine «segno su disco prima di svuotare la diretta salvata» che il debito 8 richiede; si lascia cosi'. (Dopo il debito 13 il solo `togliPendente` gira su un altro thread. In piu', `Application.onCreate` legge `getSharedPreferences(DirettaStore.FILE)` sul thread principale a ogni avvio del processo: voluto, e' un file di pochi byte e serve a vedere le pendenti prima che qualunque altro codice giri.)
18. **Le chiusure gia' accodate verso il gruppo perso** (debito 16) non si possono eseguire: se una chiusura era gia' in coda in WorkManager quando l'account e' stato tolto dal gruppo, il server risponde `not_authorized`, il lavoro rinuncia e il foglio dice «La diretta non si e' chiusa». La riga viva la chiude la dashboard o la pulizia delle 6 ore.
19. **Doppione innocuo della chiusura**: se il processo muore fra `await()` e `togliPendente`, il segno resta e al prossimo avvio la stessa chiusura si riaccoda. Con il lavoro precedente ancora in coda il KEEP non ne crea un secondo; se era gia' finito ne parte uno nuovo, e il server risponde «gia' chiusa» (nessun danno).
20. ~~**Il blocco per file di `DirettaStore` non ha un test suo**: nessun test fa scrivere due store sullo stesso file da due thread, e tornare a un blocco per istanza lascerebbe la suite verde. E il lancio di `accodaLeRimaste` da `Application.onCreate` usa un `CoroutineScope(Dispatchers.IO)` che nessuno possiede: sotto Robolectric la coroutine sopravvive al test, e il blocco della suite visto il 15 ottobre non si ripresenta solo perche' Robolectric azzera la cache delle preferenze fra un test e l'altro. Rimedio: scope sostituibile nei test e un test di concorrenza sul blocco (controllo Opus del 15 ottobre).~~ **Chiuso:** lo scope e' `scopeDiAvvio`, che nei test possiede e chiude `ProvaApplication`; il blocco ha il suo test di concorrenza.

### Debiti 13, 15 e 16 chiusi - 15 ottobre 2026 (D-3, branch `claude/d3-debiti`)

Tre debiti di D-3 chiusi senza toccare il resto. Niente dashboard, niente database, niente schermate nuove: **non visto a schermo e non provato su dispositivo** (il debito 12 resta; il 14 resta).

- **13, le pendenti.** `ScoreboardEssentialApplication.onCreate` chiama `DirettaChiusuraWorker.accodaLeRimaste`: legge solo le preferenze di `DirettaStore` (nessun Keystore, nessuna rete, niente `padelElite`), accoda **una chiusura per partita** come lavoro unico di WorkManager (KEEP) su un altro thread, e **non apre la schermata**: un messaggio dell'orologio che fa risorgere il processo basta. `DirettaStore.chiusureRimaste()` dimentica anche la diretta salvata che una pendente ha chiuso (processo morto fra il segno e la pulizia), perche' tolto il segno nient'altro la proteggerebbe dal ripristino. **La finestra fra l'accodamento e il lavoro scritto e' chiusa**: `allaChiusura` e' ora `suspend` e ritorna quando l'`Operation` di WorkManager e' finita (`await()`), e `Diretta.consegnaSubito` toglie il segno **solo allora**; fino ad allora la partita e' "in volo" (il ripristino non la riaccoda, il foglio dice gia' «Chiusura della diretta in corso»). Se la scrittura fallisce il segno resta e lo ritrova il prossimo avvio. Per questo `segnaPendente` e `togliPendente` stanno sotto un blocco per file di preferenze (lo stesso per due `DirettaStore` sullo stesso file, che in un processo Android e' una sola istanza): `togliPendente` ora gira su un altro thread e non deve cancellare un segno appena messo. Effetto sul debito 17: il `commit` di `togliPendente` non e' piu' sul thread di chi chiude, quello di `segnaPendente` si'.
- **15, dopo le 6 ore.** `DirettaAvviso` (interfaccia) e `DirettaAvvisoNotifica` (`DirettaAvviso.kt`): una notifica **ordinaria** (id 4, non fissa, si scorre via, tocco = foglio PARTITA) sullo stesso canale a importanza bassa, senza suono ne' vibrazione, titolo «Diretta su <gruppo>» e testo «La diretta continua solo con questa app aperta» (en «Live broadcast: <group>», «The live feed only continues with the app open»; `diretta_avviso_title` e `diretta_avviso_text`). **Dice la condizione, non lo stato**: non e' il testo del foglio («In diretta su X. La notifica fissa si e' fermata...»), perche' l'avviso puo' restare nel cassetto dopo la morte del processo e un «In diretta» sarebbe falso. La mostra `DirettaServizioControllo.scaduto()` (chiamato da `onTimeout`), solo se la diretta e' ancora accesa; la tolgono la fine della diretta, una diretta nuova e l'avvio del processo (`Application.onCreate`). **Limite dichiarato:** se il processo muore, nessuno puo' togliere la notifica fino al prossimo avvio del processo; per questo il testo resta vero anche allora. Senza il permesso per le notifiche non si mostra. (Il servizio che riparte nella stessa diretta non esiste: dopo `scaduto()` nulla lo riavvia, e una diretta nuova toglie gia' l'avviso.)
- **16, il gruppo perso.** `PadelEliteServices.aggiornaIlRuolo`: se la lettura riesce (`GroupsOutcome.Ok`) e il gruppo scelto non c'e' piu' nell'elenco, `clearSelectedGroup()` (come la sessione persa) e `Diretta.fuoriDalGruppo(gruppo)`; ritorna true (il foglio si ridisegna). **Un errore di rete o una sessione scaduta non e' "tolto dal gruppo"**: non arrivano a quel ramo. Motivo nuovo `DirettaMotivo.FUORI_DAL_GRUPPO`, stato FERMA, **senza chiusura sul server** (chi non e' piu' nel gruppo non potrebbe chiuderla: `not_authorized`, e il foglio direbbe «non chiusa» per niente; la riga viva la chiude la dashboard o la pulizia delle 6 ore). Una diretta verso un altro gruppo non si tocca; una salvata non ancora ripresa si dimentica **e lo stato diventa FERMA con lo stesso motivo** (gruppo e partita della salvata), cosi' il foglio dice la frase anche quando la rete risponde prima del ripristino. Testo: it «Diretta spenta: non fai più parte di questo gruppo», en «Live off: you are no longer in this group» (`diretta_stop_left_group`, senza apostrofi).
- **Prove** (JVM, server finto): `DirettaAlAvvioTest` (nuovo, 6: due pendenti accodate e segni tolti a lavoro scritto, nessun doppione col KEEP, niente da fare senza segni, la diretta salvata chiusa si dimentica e quella di un'altra partita no, `Application.onCreate` le accoda senza schermata, l'avviso di un processo morto si toglie), `DirettaLimitiTest` (+5, con lo stato FERMA della salvata: il segno resta finche' il lavoro non e' scritto, se la scrittura fallisce il segno resta e il ripristino dopo riprova, `fuoriDalGruppo` spegne con il motivo e senza chiusura, non tocca un altro gruppo, dimentica la salvata), `DirettaServizioTest` (+3: l'avviso compare allo scadere e sparisce alla fine, uno scadere dopo la fine non lascia avvisi, una diretta nuova non eredita l'avviso), `DirettaServiceTest` (+4: la notifica ordinaria dopo `onTimeout`, titolo e testo che dicono la condizione in it e in en senza apostrofi, si toglie a fine diretta), `FuoriDalGruppoTest` (nuovo, 7: gruppo assente, elenco vuoto, **errore di rete no**, gruppo presente con ruolo cambiato, senza diretta, testi it/en). **Falsificati** (mutando il codice di produzione e vedendo i test rossi): togliere il segno prima di `allaChiusura` (2), togliere il segno anche nel `finally` (1), togliere la chiamata dall'`Application` (1), togliere il `clear()` della salvata in `chiusureRimaste` (1), non mostrare l'avviso in `scaduto()` (4), non toglierlo a fine diretta (1), a diretta nuova (1) o all'avvio del processo (1), tornare a `?: return false` sul gruppo mancante (3), trattare l'errore di rete come "tolto" (2), non guardare il gruppo in `fuoriDalGruppo` (1), non pubblicare lo stato FERMA della diretta salvata (1), tornare al titolo «In diretta su» dell'avviso (1). **Correzioni della revisione Opus:** l'avviso dice la condizione e non lo stato (sopra), `fuoriDalGruppo` pubblica lo stato anche per la salvata, tolta la riga morta di `DirettaService.onStartCommand` col suo test; il blocco di `DirettaStore` e' **per file di preferenze** (`WeakHashMap` sull'istanza di `SharedPreferences`, una per file in Android) invece di uno solo per il processo: con uno solo, sotto Robolectric il `commit` di una coroutine rimasta da un test precedente (che attende il thread principale) bloccava `Application.onCreate` del test dopo, e la suite si fermava per sempre. Verifica: `cleanTest test ktlintCheck lintDebug assembleDebug assembleDebugAndroidTest` verde (`clean` e' bloccato da un processo di un altro agente), core 215, shared 68, mobile 1818 (2 saltati come prima), wear 784. Gli strumentati compilano, **non lanciati**.

### Debiti 12, 14 e 20 - 27 ottobre 2026 (D-3)

Tre debiti di D-3. Nessuna schermata nuova, nessun token nuovo, nessuna richiesta al Supabase vero (ne' dai test JVM, ne' dall'emulatore).

- **20, lo scope dell'avvio e il blocco per file.** `ScoreboardEssentialApplication` (ora `open`) lancia `accodaLeRimaste` in `scopeDiAvvio` (`internal open`, `SupervisorJob + Dispatchers.IO`) invece di un `CoroutineScope` senza proprietario. Tutti i test Robolectric di `:mobile` girano con `ProvaApplication` (`robolectric.properties`, `application=`), che lo sostituisce con uno suo e a fine test (`TestLifecycleApplication.afterTest`) lo cancella e lo aspetta, con un tetto di 10 s oltre il quale il test fallisce: nessuna coroutine dell'avvio sopravvive al test che l'ha lanciata. `DirettaStoreConcorrenzaTest`: due `DirettaStore` sullo stesso file, due thread (uno segna e toglie, l'altro segna, 200 giri ciascuno); alla fine ci sono tutti i segni del secondo e nessuno del primo.
- **14, la notifica in attesa.** `testoDiAttesaInNotifica` (accanto a `testoDiAttesa`, in `DirettaLook.kt`): «Diretta in attesa: <motivo>» / «Live on hold: <reason>», un motivo breve per ognuno dei nove casi di attesa (manca la rete, accedi di nuovo, servono quattro giocatori, un nome supera i 60 caratteri, troppe dirette nel gruppo, nessun codice libero, risposta inattesa del server, errore imprevisto, un giocatore è stato sostituito; un caso sconosciuto vale la rete, come nel foglio). Stringhe `diretta_notif_waiting` e `diretta_notif_why_*`, senza apostrofi. Il titolo resta «In diretta su <gruppo>». La riga del foglio PARTITA non cambia (`testoDiAttesa`).
- **12, sul dispositivo.** `DirettaSulDispositivoTest` (strumentato). Il runner `ProvaRunner` usa `ProvaApplicationStrumentata`, che punta Padel Elite a `https://127.0.0.1:1` (il telefono stesso, porta chiusa) con una sessione finta e un gruppo admin: la diretta si accende e resta in attesa per la rete, e nessuna richiesta esce dal telefono. `padelElite` dell'applicazione e' ora `open` per questo. Prove: notifica fissa (titolo, «Diretta in attesa: manca la rete», fissa, canale, azione) e servizio che se ne va da solo allo spegnimento; «Interrompi diretta» dalla notifica apre la conferma sul foglio, «Continua» lascia la diretta accesa, «Interrompi» la spegne e toglie la notifica; con `tetto=1` (argomento dello strumentato) e il limite accorciato (`adb shell device_config put activity_manager data_sync_fgs_timeout_duration 60000`, `am compat enable FGS_INTRODUCE_TIME_LIMITS it.vantaggi.scoreboardessential`) il sistema ferma il servizio («FGS (dataSync) timed out» nel log, circa 60 s dopo, con l'app in secondo piano), la diretta continua, compare l'avviso del debito 15 e sparisce a fine diretta. **Visto sull'emulatore `Pixel_9a_Test` (Android 16, API 36)**, schermate in inglese (lingua dell'emulatore): la notifica «Live on Padel / Live on hold: no network» nel cassetto, il dialogo «Stop the live broadcast?» sul foglio aperto dalla notifica, l'avviso «Live broadcast: Padel / The live feed only continues with the app open» dopo il tetto. Notato: l'avviso chiuso nel cassetto tronca il testo («...with the app ...»), si legge aprendolo. **Non provato:** il rifiuto dell'avvio in secondo piano. Un processo strumentato resta a importanza 125 (servizio in primo piano) anche dopo HOME e 15 s, e il sistema lo lascia partire; il test e' stato tolto e il rifiuto resta provato solo sulla JVM. Non provati nemmeno la diretta che trasmette davvero (serve un server, quello vero e' produzione) e un telefono vero. Due accorgimenti del test: la schermata si apre con un intento come dal launcher (ActivityScenario non segue l'activity dopo `onNewIntent` e non riesce a chiuderla) e la diretta si accende due secondi dopo, perche' il ripristino asincrono di `MainViewModel` chiude una diretta di un'altra partita (`Diretta.riprendi`).
- **Falsificati** (mutando il codice di produzione e vedendo i test rossi, poi rimesso): blocco per istanza in `DirettaStore` (il test di concorrenza: segni persi), `CoroutineScope(Dispatchers.IO)` al posto di `scopeDiAvvio` (1: il lavoro non e' nello scope dell'applicazione), la notifica di nuovo con `testoDiAttesa` (1 JVM in `DirettaServiceTest` e 1 strumentato sull'emulatore), `ERRORE_INATTESO` mappato sulla rete (3 di `AttesaNellaNotificaTest`), l'inglese «Not broadcasting: %1$s» (1).
- **Verifica:** `test ktlintCheck lintDebug assembleDebug assembleDebugAndroidTest` verde (core 235, shared 34, mobile 981 dopo l'unione di `main` con 1 saltato come prima, wear 392, contati per la variante debug: la release ripete gli stessi); lint senza avvisi nuovi. Dopo l'unione di `main` e' uscito rosso una volta `DirettaServizioTest` (esistente): il finto avviatore conta l'avvio prima che il controllo scriva la fase ATTIVO, e il test leggeva la fase appena visto il conto. Corretto il test (aspetta la fase, poi conta gli avvii), non il codice. Strumentati su `Pixel_9a_Test`: 42, nessuno fallito, 1 saltato (il tetto, che vuole `tetto=1`); il tetto lanciato a parte, verde. Il telefono fisico collegato non e' stato toccato (`ANDROID_SERIAL=emulator-5554`); limite e compatibilita' rimessi come prima.
- **Piano UI e pattern vietati.** Solo testo in una notifica di sistema: nessun colore, spazio, icona, movimento o componente nuovo; frasi brevi, nessun maiuscolo, nessuna emoji, nessun apostrofo; il motivo resta detto a parole (mai il solo colore). A schermo stretto e al 200% e' il sistema a impaginare la notifica.

### A-2b, "Accedi con Google" - 27 ottobre 2026

Molti giocatori, il proprietario compreso, entrano nella dashboard con Google e non hanno una password: il modulo con sola email e password li lasciava fuori. Ora la schermata "Padel Elite" offre anche Google, con lo stesso provider gia' configurato nel progetto Supabase della dashboard. Nessuna chiamata al Supabase vero: tutto col server finto.

**Come e' fatto.** OAuth di Supabase nel browser con PKCE (RFC 7636), scritto a mano come il resto del client (nessuna dipendenza nuova, niente Custom Tabs). `AccessoGoogle` crea il verificatore (32 byte casuali, base64url) e la sfida `s256`, e compone `/auth/v1/authorize?provider=google&redirect_to=scoreboardessential://padel-elite/accesso&code_challenge=...&code_challenge_method=s256`. Il verificatore si salva nelle preferenze private della sessione (`SessionStore.savePkceVerifier`: mentre il browser e' aperto il sistema puo' chiudere l'app) e vale per un solo scambio. Supabase passa da Google e torna sul deep link con `?code=`; `PadelEliteActivity` (ora `exported` con l'intent-filter e `singleTask`, cosi' il browser consegna il link all'istanza aperta) lo legge in `onCreate` o `onNewIntent` con `RitornoGoogle.da`, e `PadelEliteApi.exchangeCode` lo scambia con `POST /auth/v1/token?grant_type=pkce` (`auth_code`, `code_verifier`). La sessione e' la stessa di email e password: cifrata col Keystore, rinnovo, gruppo che non passa a un altro utente. Un link di ritorno senza verificatore salvato non manda nessuna richiesta; con la funzione spenta l'Activity si chiude subito.

**Configurazione (produzione, del proprietario).** Una sola: `scoreboardessential://padel-elite/accesso` fra le Redirect URLs di Supabase Auth. Niente in Google Cloud: Google torna a Supabase, non all'app. Finche' manca, il bottone porta a Google e poi al sito della dashboard (gia' collegato), non all'app.

**Rapporto UI (modulo di accesso di `PadelEliteActivity`).**

- *Compito e valore:* entrare con l'account della dashboard. *Gerarchia:* titolo, una riga di spiegazione, "Accedi con Google", la riga "Oppure con email e password", il modulo di prima.
- *Componenti riusati:* `Widget.App.Button.Secondary` per Google (largo quanto la riga, icona `ic_open_in_new` a 20dp dopo il testo: concetto "Apre fuori dall'app" della tabella G-3, che ora ha il suo drawable); **il primario della regione resta "Accedi"** del modulo. Google sta prima perche' e' l'accesso dei piu': la posizione porta la gerarchia, l'accento resta sul primario.
- *Stati:* normale, premuto e fuoco del bottone secondario; in corso (il ritorno con il codice: "Accesso in corso" sul bottone di Google, largo uguale; "Accedi", i campi e Google disattivati, e lo stesso vale per Google durante l'accesso con email); pagina di Google chiusa o rifiutata (`access_denied`: il modulo torna com'era, nessun errore); errore (codice scaduto o rifiutato, errore di Supabase: "Accesso con Google non riuscito. Riprova."; nessun browser: "Per accedere con Google serve un browser sul telefono."; rete; troppi tentativi), sempre nella riga con l'icona `cancel`, mai sotto i campi. Riuscito: il pannello dell'account come con la password.
- *Movimento:* nessuno aggiunto (vale anche col movimento ridotto). *Accessibilita':* il bottone e' testo (l'icona e' decorativa), 48dp; l'ordine di lettura segue la gerarchia. *Schermo stretto e 200%:* il bottone e' largo quanto la riga e va a capo; la riga "Oppure" va a capo.
- *Conflitti:* **nessun logo "G" di Google.** Le linee guida del marchio di Google lo chiedono sul bottone; la Constitution vuole una sola famiglia di icone e nessun marchio come icona. Si tiene la Constitution: la parola "Google" nel testo, l'icona "apre fuori dall'app". Da rivedere se si passa al selettore nativo (Credential Manager), dove il logo lo disegna il sistema.
- *Controllo dei pattern vietati:* niente maiuscolo, niente emoji, niente ombre, nessun colore, spaziatura o misura scritti a mano (`space_16`, `space_24`, `icon_standard`), un solo primario, tre pesi. **Non visto a schermo** (nessun SDK Android nel container): da provare in locale con `local.properties` e la Redirect URL ammessa.

**Debiti dichiarati.** (1) La scheda del browser resta aperta dietro l'app dopo il ritorno (con Custom Tabs si chiuderebbe da sola, ma porta `androidx.browser`). (2) Un ritorno senza rete obbliga a ricominciare da Google: il codice non si conserva. (3) Se la Redirect URL non e' ammessa l'app non lo sa: il bottone porta al sito.

## Sigle delle coppie - 27 ottobre 2026

Richiesta del proprietario: con le coppie scelte e il nome della squadra lasciato com'e', il tabellone diceva "TEAM 1" contro "TEAM 2" e a bordo campo non si capiva chi fosse chi.

### Piano UI (CLAUDE.md, punto 3)

- **Gerarchia e layout**: invariati. Cambia solo il testo del nome di squadra sopra la zona: "VAN/GLO" al posto di "TEAM 1". Stessa `TextView` (`team1_name_textview`, `TextAppearance.App.Game.Name`, una riga con ellissi), nessun token, colore, icona o movimento nuovo.
- **Regola** (`EtichettaSquadra`, pura): se il nome scelto e' il predefinito (vuoto, "TEAM n", "Team n", "Squadra n", senza badare alle maiuscole) e la rosa ha uno o due giocatori, il nome mostrato e' la sigla di ciascuno unita da "/": prime tre lettere della prima parola del nome (il cognome: in rosa i giocatori sono scritti col solo cognome o cognome e nome), solo lettere, in maiuscolo ("Vantaggi" e "Glorio" danno "VAN/GLO", "Ferrario Stefano" da' "FER", "D'Amico" da' "DAM"). Un nome scelto vince sempre. Con piu' di due giocatori (il calcio) resta il nome: cinque sigle non entrano in mezzo tabellone. Se un giocatore non ha lettere resta il nome.
- **Dove arriva**: `MainViewModel.team1Name`/`team2Name` sono ora il nome **mostrato** (un `MediatorLiveData` di nome scelto e rosa), quindi tabellone, barra del periodo, pallini del servizio, registro del foglio, coppie nel dettaglio, striscia, orologio (`PATH_TEAM_NAMES`), riassunto testuale, PDF e nome del file d'export usano le sigle senza altre modifiche. Il nome **scelto** resta in `team1ChosenName`/`team2ChosenName` ed e' quello che il dialogo propone: aprire il dialogo e confermare non trasforma le sigle in un nome fisso. Registro e orologio si aggiornano anche quando cambia solo la rosa (un osservatore permanente nel ViewModel, tolto in `onCleared`).
- **Stati**: rosa vuota (il predefinito), un giocatore (singolare: "VAN"), due giocatori, rosa che cambia durante la scelta (le sigle seguono), nome scelto, ripristino dopo la chiusura dell'app (le rose tornano dalla riga viva e con loro le sigle). Nessuno stato nuovo di pressione o di fuoco: il contenitore e' quello di prima.
- **Accessibilita'**: la descrizione del contenitore segue il nome mostrato ("Modifica nome VAN/GLO"). Come TalkBack legge "/" va sentito sul telefono.
- **Schermo stretto e 200%**: "VAN/GLO" sono sette caratteri, meno di "TEAM 1" piu' margine; se non entra si accorcia con l'ellissi come prima.
- **Conflitti**: nessuno. Storico e Cronaca non cambiano: leggono le squadre fisse 1 e 2 del database, non il nome della partita.

### Debiti

- Omonimi di sigla (Rossi e Rossini sono tutti e due "ROS"): non si distinguono, si sceglie un nome.
- Un cognome di due parole ("De Luca") da' "DE"; un giocatore scritto nome e cognome ("Mattia Vantaggi") da' la sigla del nome ("MAT").

### Controllo dei pattern vietati (`review.md`)

Nessun valore scritto a mano, nessuna emoji, nessuna icona nuova, nessuna ombra, nessun movimento nuovo, nessun peso nuovo: il cambio e' solo del testo.

## Giocatori della dashboard nella scelta della partita - 27 ottobre 2026

Richiesta del proprietario: con l'accesso e il gruppo scelto vedeva la rosa della dashboard, ma per usarla doveva prima ricreare o collegare a mano ogni giocatore in Padel Elite. Ora "Aggiungi un giocatore" (le due rose del foglio PARTITA) offre direttamente la rosa del gruppo: il giocatore scelto, se non e' ancora nell'app, nasce nell'app col nome della dashboard e **gia' collegato** (`padel_elite_links`), cosi' nel file inviato e nella diretta porta il suo `padelPlayerId`. Solo scritture Room sul telefono; sulla dashboard solo la lettura della rosa che R-1 gia' fa. Nessuna migrazione (le query nuove sono letture su tabelle che ci sono).

**Regola degli omonimi (decisione del proprietario, 27 ottobre).** Nella dashboard spesso c'e' solo il cognome: due persone con lo stesso nome non sono per forza la stessa. Quindi non si unisce mai da solo. Se nell'app non c'e' nessuno con lo stesso nome (senza maiuscole e spazi) e libero in questo gruppo, si crea e si collega subito; se c'e', l'app chiede "Rossi e' gia' nell'app?" e per ogni omonimo mostra le partite giocate e l'ultima (data, con chi, contro chi); l'utente tocca l'omonimo ("e' lui": si collega e tiene le sue statistiche) o "E' un'altra persona" (un giocatore nuovo, collegato). Un omonimo gia' collegato a un altro giocatore della dashboard non e' un candidato.

**Ordine dell'elenco** (richiesta del proprietario, 27 ottobre: "ci iscriviamo alla serata, e chi gioca con me e' nel mio girone"). Le sezioni, ognuna in ordine di nome: "Nello stesso girone stasera" (i compagni di girone di chi e' gia' in campo, nei gironi salvati dalla dashboard per la prossima serata: scelto il primo, gli altri tre sono in cima), "Iscritti alla prossima serata" (i confermati di `v2_rsvp`), "Padel Elite · <gruppo>" (il resto della rosa), "Solo nell'app" (i giocatori dell'app non collegati in questo gruppo). La serata si legge come nella Serata (`venue_config`, `ProssimaSerata`, `v2_rsvp`, `gironi_serata` valido solo per quella data), insieme alla rosa e in sottofondo; se non si legge, l'elenco e' solo rosa e app. Chi e' gia' in campo, in una qualunque delle due squadre, non si offre. Tre dalla dashboard e uno locale si possono mettere insieme: il locale arriva all'admin col nome, come prima.

**Serata.** "Usa i presenti" e i gironi: un presente non collegato e senza omonimi nasce gia' collegato al gruppo letto (prima nasceva come ospite senza collegamento), e la volta dopo entra come collegato. Per chi ha un omonimo nell'app (prima riusato in silenzio) la Serata fa la stessa domanda, un presente alla volta, prima di applicare la proposta: "e' lui" lo collega, "un'altra persona" ne crea uno nuovo collegato, Annulla (o indietro) lascia cadere la proposta senza scrivere niente. Un omonimo gia' scelto per un altro presente non si offre di nuovo. Decisione del proprietario del 27 ottobre. Chiude il debito 2 di "Serata dalla dashboard".

**Codice.** `SceltaDelGiocatore` (le voci, pura), `RosaGruppo.scegliPerLaPartita`, `unisci`, `creaNuovo` e gli indizi (`MatchDao.countClosedMatchesOf`, `lastClosedMatchOf`), `GiocatoriDalGruppo` (rosa in memoria per gruppo, riletta in sottofondo a ogni ritorno sulla schermata e a ogni apertura del dialogo, attesa massima di 4 secondi la prima volta), `RigheDelDialogo` (righe con sezioni per i dialoghi di scelta). Il testo di Padel Elite sotto "Giocatori del gruppo" ora dice che collegarli prima non serve.

### Piano UI (CLAUDE.md, punto 3)

- **Compito e valore.** Il compito resta "metti un giocatore in questa squadra", il valore e' il nome. La rosa della dashboard viene prima perche' e' quella da cui si sceglie quando c'e' un gruppo.
- **Gerarchia e layout.** Il dialogo di Material di prima, con un elenco a sezioni: intestazioni non toccabili (`item_role_header`, testo secondario, `accessibilityHeading`) e righe col nome e una didascalia (`item_padel_elite_player`: "Si aggiunge all'app quando lo scegli", "Gia' nell'app", "Nell'app come Mattia"; le righe "Solo nell'app" non hanno didascalia). La domanda sugli omonimi e' un secondo dialogo dello stesso tipo: una riga di spiegazione come intestazione, gli omonimi con l'indizio, "E' un'altra persona" in fondo. Nessun primario nuovo: il solo comando del dialogo e' "Annulla", come prima.
- **Componenti riusati.** `MaterialAlertDialogBuilder`, `item_role_header`, `item_padel_elite_player` (pressione condivisa e anello di fuoco), lo Snackbar sopra la striscia. Nessuno stile, token, colore, spaziatura o icona nuovi.

| Stato | Cosa si vede |
|---|---|
| Senza funzione, accesso o gruppo, o sport non padel | il dialogo di sempre coi soli giocatori dell'app |
| In corso (prima lettura della rosa) | "Aggiungi" della squadra disattivato finche' l'elenco e' pronto, al massimo 4 secondi; poi il dialogo |
| Pronto | "Nello stesso girone stasera", "Iscritti alla prossima serata", "Padel Elite · <gruppo>" (le sezioni vuote non compaiono) con le didascalie, poi "Solo nell'app" |
| Dashboard che non risponde (rete, 403, sessione) | una sola intestazione "Padel Elite non risponde: giocatori dell'app" e i giocatori dell'app |
| Vuoto | il messaggio di prima "Nessun giocatore disponibile" con "Gestisci" |
| Omonimo | "Rossi e' gia' nell'app?", la spiegazione, gli omonimi con "2 partite · ultima il 12 ott 2026 · con Luca · contro Anna, Bea" o "Nessuna partita giocata", e "E' un'altra persona" |
| Errore di scrittura | Snackbar "Non sono riuscito ad aggiungere il giocatore. Riprova." |
| Fatto | lo Snackbar di prima "Rossi aggiunto a <squadra>" |
| Normale / premuto / fuoco | riga a riposo / pressione della lista sulla riga (scala 0,97, opacita' 0,85) / anello lime |
| Disattivo | le intestazioni (non si toccano); chi e' gia' nella squadra non compare |

- **Movimento.** Nessuno nuovo: i dialoghi e lo Snackbar sono quelli di sistema, come prima.
- **Accessibilita'.** Intestazioni come titoli per TalkBack e non abilitate; righe da 48dp di testo (nessun comando di sola icona); lo stato e' sempre in parole.
- **Schermo stretto e 200%.** Una colonna nel dialogo che scorre; il nome si accorcia con l'ellissi, la didascalia va a capo.
- **Conflitti dichiarati.** Nessuno sui token. L'indizio dell'omonimo e' una frase lunga in una didascalia: a 200% occupa piu' righe, scelta voluta (e' l'informazione per decidere).
- **Pattern vietati** (`review.md`): niente maiuscolo, niente emoji, niente ombre nuove, niente colore scritto a mano, nessuna card, al massimo tre pesi (400 didascalia, 500 nome e intestazione), un solo comando nel dialogo, nessun ciclo. Stringhe con apostrofo escapate, nessun `--` nei commenti XML.

### Come si prova

`SceltaDelGiocatoreTest` (le voci: ordine, sezioni, esclusi, rosa non letta, elenco vuoto), `ScegliPerLaPartitaTest` (database in memoria e server finto: collegato, creato e collegato, omonimo non unito da solo con partite e ultima partita, omonimo collegato ad altri escluso, "e' lui", "un'altra persona"; l'elenco con la rosa, l'ordine compagni di girone, iscritti e resto con la serata del server finto, senza gruppo nessuna richiesta, la dashboard che non risponde, la rosa che arriva tardi e la volta dopo e' pronta), `SerataConLaDashboardTest` (l'ospite nuovo nasce collegato e non si ricrea; con un omonimo si chiede e niente si scrive prima della risposta; "e' lui" collega l'omonimo; "un'altra persona" ne crea uno collegato; Annulla non scrive niente), `LettoreSerataDashboardTest` (la serata porta il gruppo letto). Cosa li rende rossi: togliere la creazione collegata in `scegliPerLaPartita` o in `nuovoDallaDashboard`, unire l'omonimo in automatico, togliere l'esclusione degli omonimi collegati, aspettare la rete oltre `attesaMassimaMs`, non salvare la rosa letta in memoria.

### Debiti dichiarati

1. **Non visto a schermo** (nessun SDK Android nel container): i due dialoghi sono provati solo nella logica; da guardare a occhio, anche a 360dp e al 200%.
2. **Senza collegamenti** (funzione spenta o serata senza gruppo letto) la Serata riusa ancora l'omonimo come prima: in quel caso non c'e' un gruppo a cui collegarlo.
3. **La rosa in memoria** vive quanto la schermata principale: un giocatore aggiunto nella dashboard compare dalla volta dopo che il dialogo si apre (la lettura in sottofondo lo porta).
4. **Il tennis** non offre la rosa della dashboard (Padel Elite e' padel).

## Serata dalla dashboard, la verifica a schermo - 27 ottobre 2026

La checklist "Da guardare in locale" della PR #182, eseguita sul telefono del proprietario (SM-S948B, build di `main` 8746779b, accesso a Padel Elite e gruppo ART Padel) contro la dashboard vera, **solo in lettura**: nessuna partita inviata, nessun girone creato, la Diretta spenta durante le prove.

| Punto | Esito |
|---|---|
| Serata, data e presenti | funziona: "Serata di martedì 27 ottobre, 12 presenti. 11 non collegati entrano come ospiti." (la data non e' confrontata con `venue_config` a mano) |
| Girone salvato dalla dashboard | funziona: il girone toccato diventa i presenti, con la spunta |
| "Usa i presenti", ospiti, Annulla | funziona: solo al tocco, "12 presenti dalla dashboard, 11 come ospiti", Annulla rimette la serata di prima |
| Senza rete e "Riprova" | funziona: "La dashboard non risponde. Controlla la connessione.", Riprova rilegge |
| 360dp e carattere al 200% | **non funzionava**: le righe dei gironi si fermavano a due righe con l'ellissi ("Girone B: Chainykova, Ma…"). Corretto qui sotto |
| Partita in corso | funziona: le proposte si spengono dal primo punto (a 0-0 la partita non e' ancora in corso, come nel codice) |

**La correzione.** Le righe dei gironi vanno a capo per intero: `RigaSerata.titoloIntero`, che l'adapter traduce in `maxLines` senza limite e niente ellissi; le altre righe (posti, panchina, presenti, "Usa i presenti") restano a due righe come prima, perche' un nome solo tagliato si riconosce e un elenco di quattro nomi no. Nessun token nuovo, nessun componente nuovo. Il test `le righe dei gironi vanno a capo per intero, le altre restano a due righe` di `SerataActivityTest` e' rosso se il girone resta a due righe o se il limite si toglie anche alle altre (il riciclo dell'adapter lo riporterebbe sulle righe sbagliate).

**Nota, non un errore.** Il messaggio con "Annulla" resta circa 3 secondi (durata lunga di sistema): a bordo campo col pollice puo' essere poco. Allungato a 8 secondi, vedi "Testi e Annulla della Serata" qui sotto.

**Pattern vietati** (`review.md`): niente maiuscolo, emoji, ombre, colori o misure scritti a mano, nessun peso nuovo, nessun movimento nuovo. A carattere grande il gruppo si allunga e la schermata scorre come prima.

**Debito.** La correzione non e' ancora vista a schermo a 360dp e al 200%: va riguardata sul telefono.

## Testi e Annulla della Serata - 27 ottobre 2026

Due ritocchi emersi dalla verifica a schermo di oggi, sul contorno; il gioco non cambia.

- **Il sottotitolo dell'accesso a Padel Elite** diceva "Serve solo per inviare le partite al tuo gruppo", ma l'accesso ora serve anche alla Serata. Ora: "Usa lo stesso account della dashboard. Serve per leggere la serata del tuo gruppo, i presenti e i gironi, e per inviare le partite." (in inglese nello stesso ordine). Stesso stile e stessa posizione, solo il testo.
- **Il messaggio con "Annulla"** dopo "Usa i presenti" o un girone resta 8 secondi invece dei circa 3 della durata lunga di sistema. Il valore e' un token nuovo, `undo_window` in `integers.xml`, che nomina un ruolo riusabile (il tempo per ripensarci di un messaggio con Annulla), separato dalle durate di movimento: non e' un'animazione. Con TalkBack Material lo allunga ancora secondo le impostazioni di accessibilita'. Il test `il messaggio con Annulla resta otto secondi, non i tre di sistema` di `SerataActivityTest` e' rosso se si torna a `LENGTH_LONG`.

**Come e' stato provato.** Test strumentato `SerataAnnullaStrumentatoTest` sull'emulatore `Pixel_9a_Test` (Android 16), dashboard finta e database in memoria: il messaggio resta 8,6 e 8,8 secondi e si chiude per tempo scaduto (`DISMISS_EVENT_TIMEOUT`), senza e con la domanda sull'omonimo prima; e' rosso se dura meno di 7,5 s o si chiude per altro. Sul telefono del proprietario non e' stato visto: una prova del 27 ottobre misurava circa 3,4 s, ma sul telefono c'era la build di un'altra sessione installata sopra (senza `undo_window` e col sottotitolo vecchio, controllato con `aapt2 dump resources`), quindi quella misura non riguarda questa modifica. Il sottotitolo nuovo non e' visto a schermo (il proprietario e' collegato e non si e' fatto logout).

**Piano UI.** Gerarchia, layout, componenti, stati e movimento invariati (stessa Snackbar ancorata sopra la barra di avvio, dissolvenza come prima). A 360dp e al 200% il sottotitolo e' un testo a capo libero dentro la schermata che scorre: una riga in piu' al massimo.

**Pattern vietati** (`review.md`): niente maiuscolo, emoji, ombre, colori o misure scritti a mano, nessun peso nuovo, nessun movimento nuovo; la sola cifra nuova e' un token con un ruolo.

## Orologio: squadre in campo e prossima partita della serata - 27 ottobre 2026

Richiesta del proprietario: dal menu dell'orologio sapere chi gioca in ogni squadra e, a serata in corso, cambiare le coppie della partita dopo e farla cominciare senza riprendere il telefono. Le impostazioni essenziali al polso (Sul 40-40, vibrazione, durata del portiere nel calcio, scelta del proprietario) arrivano in un secondo passo, dopo "Sul 40-40" (#194) su `main`.

### Piano UI (CLAUDE.md, punto 3)

- **Gerarchia**: il quadrante non cambia. Nel menu la prima card diventa "Prossima partita" (solo a serata in corso, con quattro nomi e la partita ferma), con le sigle delle coppie come sottotitolo ("VAN/BIA – GLO/ROS"); sotto le voci di sempre, un gruppo "In campo" con una riga per squadra: barretta del colore di squadra (`TeamInk.graphicOnBlack`), nome mostrato, giocatori. Il gruppo sta sotto le voci perche' la prima card resti sopra la piega (`MenuPiegaTest`).
- **Schermata "Prossima partita"** (`ProssimaPartitaActivity`): un compito, scegliere le due coppie. Titolo "Partita N", un avviso di una riga, per coppia la sigla (`Widget.App.Sigla`, 500 12sp, testo secondario) e un gruppo tonale con due righe (`item_player_wear`: barretta di squadra e nome), chi resta fuori, poi **Ruota** (tonale) e **Inizia** (l'unico primario, lime su `on-accent`).
- **Componenti riusati**: gruppi tonali `bg_group`, righe di `item_player_wear`, card di `item_sport_wear`, pressione condivisa `pressFeedback`. Nuovi solo come ruoli: `Widget.App.Button` e `.Tonal` (raggio `radius_control`, `control_touch` 48dp, nessuna elevazione), `elite_surface_raised` (#1E1E22, lo stesso valore del telefono) per la riga scelta, `space_4..16`, `team_bar_*`.
- **Stati**: normale; premuto (scala 0,97 e opacita' 0,85); riga scelta (fondo `elite_surface_raised`, nome lime, `isSelected`), secondo tocco sulla stessa riga la toglie, tocco su un'altra chiede lo scambio; in corso ("Aspetto il telefono…", comandi disattivi, una richiesta alla volta); errore (nessuna risposta in 3 s o invio fallito: "Il telefono non ha risposto" in `elite_warning` e vibrazione NON_CONFERMATO, comandi di nuovo attivi); gia' a tabellone ("Già a tabellone", Inizia diventa "Al punteggio"); spareggio (titolo dello spareggio); vuoto (senza proposta la schermata si chiude, il menu non mostra la voce; senza rose il gruppo "In campo" sparisce); sola lettura (a partita cominciata la voce non c'e' e il telefono rifiuta); movimento ridotto (nessun movimento nuovo).
- **Movimento**: nessuno nuovo. La risposta del telefono e' una piccola vibrazione, non un'animazione.
- **Accessibilita'**: ogni riga e' un comando con `contentDescription` ("Vantaggi, coppia 1, scelto"), l'avviso e' una live region educata, ogni riga di "In campo" si legge intera ("VAN/BIA: Vantaggi, Bianchi"); le sigle non si leggono due volte (`importantForAccessibility=no`).
- **Schermo stretto e 200%**: tutto dentro uno `ScrollView` in `BoxInsetLayout`; nomi su una riga con ellissi; i bottoni crescono in altezza.
- **Conflitti**: nessuno con la Constitution. Il telefono resta l'autorita': l'orologio legge nomi e chiede `rotate`, `swap` o `start` sulla bozza che ha visto (`next_base`), e il telefono applica solo se la bozza e' ancora quella e la partita e' ferma.

### Impostazioni essenziali al polso (scelta del proprietario, 27 ottobre 2026)

- **Cosa**: in fondo al menu, sotto "In campo", un gruppo "Impostazioni" con le stesse righe delle voci (`item_sport_wear`, nome e valore come sottotitolo): **Sul 40-40** (solo negli sport col 40-40; spenta a partita cominciata, con la coda o senza telefono, e anche spenta dice quale regola vale: "Killer point · Partita in corso"), **Vibrazione** (del polso, sempre attiva: "A ogni punto" / "Solo avvisi", cambia sul posto con un tocco, un tick quando si riaccende), **Portiere** (solo nel calcio, la durata; anche a partita in corso, come sul telefono). Il resto delle impostazioni resta sul telefono. Quali righe ci sono e quali sono spente lo decide `MenuVoci.impostazioni`, pura.
- **Scelta**: Sul 40-40 e Portiere aprono la lista dello sport (`SportSelectionActivity` con un titolo), con la spunta sul valore in uso. Il portiere si sceglie **a minuti** (Off, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 12, 15, 20, 30 min, piu' la durata in uso, se il telefono ne ha scritta un'altra, anche in secondi): il cambio si fa ogni 4-10 minuti, non ogni 45 secondi, e sul tondo non si scrive un numero.
- **Vibrazione spenta** (scelta del proprietario: "spegne i punti, avvisi si'"): tacciono il tick del tocco, i colpi del lato, la conferma di un cambio e l'annullamento; il punto in coda perde i colpi del lato e tiene il colpo lungo; non confermato e allarme del portiere suonano come sempre (`HapticsDelPolso`).
- **Autorita'**: il polso chiede (`MSG_SETTING_INTENT`), il telefono scrive nelle sue impostazioni (`MatchSettingsRepository`) e da li' il flusso delle impostazioni applica, con le guardie di sempre; la risposta e' lo stato v2, che ora porta le regole fra cui scegliere (`deuce_ids`, `deuce_labels`, gia' tradotte) e la durata del portiere (`keeper_seconds`).
- **Stati**: normale, premuto, spenta col perche', in corso (la lista resta aperta finche' non si sceglie), errore (messaggio non partito: vibrazione dell'errore), vuoto (senza 40-40 ne' portiere resta la sola vibrazione), movimento ridotto (nessun movimento nuovo).

### Portiere in minuti e Off (richiesta del proprietario, 28 ottobre 2026)

- **Perche'**: si gioca con il cambio portiere ogni 4, 5, 6, 10 minuti; c'e' chi cambia a gol subiti e non a tempo. Le scelte da 15/30/45 secondi non servivano a nessuno.
- **Telefono, Impostazioni**: nella riga del portiere un interruttore "Cambio portiere a tempo" (gruppo tonale, stesso pattern dell'alto contrasto) e, solo se acceso, il campo "Durata del portiere (minuti)", anche con la virgola ("4,5"). Spento si scrive 0; riacceso torna l'ultima durata, ricordata nelle impostazioni (cinque minuti la prima volta). Il campo accetta virgola e punto (`android:digits`, perche' la tastiera numerica decimale segue la lingua di sistema e su un telefono en-GB non offriva la virgola) e il tasto fatto della tastiera salva. Il predefinito per chi non ha mai scelto e' 5 minuti (era 30 secondi). Le impostazioni tengono ancora i secondi: nessuna migrazione.
- **Off**: la durata 0 spegne tutto. Sul telefono lo slot del portiere sparisce dalla barra (nessuna barra che gira, nessun tocco che avvia). Al polso il quadrante non mostra "K" ne' l'anello (`KEY_CAP_HAS_AUX_TIMER` falso), ma il menu offre ancora la riga Portiere con "Off" per riaccenderlo (`KEY_KEEPER_OFFERED`, chiave nuova e additiva). Un avvio del portiere che arrivasse dall'orologio a durata 0 si ignora.
- **Dal polso**: la lista del portiere parte da Off; `MSG_SETTING_INTENT` accetta 0 oppure 5 s - 60 min. Se l'app del telefono non e' in memoria (nessun ViewModel che prenda il broadcast), la durata si scrive comunque nelle impostazioni dal service e vale alla prossima apertura; il 40-40 no, perche' vale solo a partita ferma e il motore sta nel ViewModel (resta perso, come lo sport).
- **Stati**: normale, premuto, acceso/spento, errore (campo con un valore non valido: resta l'ultima durata), in corso (il conto in corso si ferma se si spegne), sola lettura (nessuno), movimento ridotto (nessun movimento nuovo).
- **Non visto a schermo** quando scritto: da provare su telefono e orologio.

### Protocollo (additivo, golden test di `:shared`)

`PATH_PAIRS` (DataItem del telefono: rose, prossima partita, sigle, panchina, spareggio, consegnata, versione) e `MSG_SERATA_INTENT` (messaggio del polso con azione, base, posti e sequenza). Le sequenze del polso restano una sola per nodo (`SequenzaDelPolso`): quadrante e schermata non si scavalcano. Impostazioni: `KEY_DEUCE_IDS`, `KEY_DEUCE_LABELS`, `KEY_KEEPER_SECONDS` (0 se il portiere e' spento) e `KEY_KEEPER_OFFERED` nello stato v2 e `MSG_SETTING_INTENT` (`setting`, `setting_value`, `seq`).

### Debiti

- La schermata Serata del telefono non si ridisegna mentre l'orologio cambia la bozza: la rilegge quando torna in primo piano.
- Con l'app del telefono chiusa la richiesta del polso va persa (nessuna custodia come per i punti): l'orologio dice "Il telefono non ha risposto".
- `WearViewModel.applicaCoppie` non ha un test suo; le descrizioni TalkBack del quadrante non dicono i giocatori.
- `clipToOutline` dei gruppi vale da API 31 (minimo 30), come nella scelta dello sport.
- Il menu si chiude da solo dopo 10 secondi senza tocchi: con "In campo" e le impostazioni sotto la piega c'e' da scorrere (segnalato alla revisione dei menu, che ridisegna il menu dopo questa PR).
- Dopo una scelta dalla lista il quadrante non riapre il menu: il valore nuovo si legge alla prossima apertura.
- Visti sul Galaxy Watch il 27 ottobre: "In campo" (VAN/SER Vantaggi e Serrone, RAG/GLO Ragni e Glorio, con le barrette di squadra) e il gruppo Impostazioni a partita in corso nel padel (Sul 40-40 spento, Vibrazione "A ogni punto", niente Portiere); non toccate le voci. La prossima partita non e' vista a schermo (nessuna serata aperta). Per arrivare alle Impostazioni servono tre o quattro trascinamenti.

### Controllo dei pattern vietati (`review.md`)

Nessun colore, spaziatura, raggio o durata scritti a mano (solo token, i nuovi nominano un ruolo); icone nessuna nuova; niente emoji, ombre, maiuscolo, cicli senza significato; un solo primario per schermata; pesi 400, 500, 600.

## Regola del 40-40 a scelta, anche sull'orologio - 27 ottobre 2026

Richiesta del proprietario: dall'orologio i punti arrivano al telefono, ma "non ci sono i vantaggi e la regola del killer point". La causa: il padel era fissato nel registro degli sport a punto secco, sul telefono come sull'orologio; il motore di `:core` sapeva gia' fare i vantaggi e il killer point, ma nessuno poteva sceglierli. Come gioca il gruppo (parole del proprietario): "40-40, vantaggio. Un'unica volta, se si ritorna in parita', killer point. Chi fa il punto vince": e' `DeuceRule.KILLER_POINT`.

**Cosa cambia.** In Impostazioni, sotto lo sport, "Sul 40-40" con tre voci: Punto secco, Killer point, Vantaggi. C'e' solo negli sport con racchetta, una scelta per sport (il padel parte dal punto secco, il tennis dai vantaggi, come prima). Si cambia a partita ferma, come lo sport: a partita cominciata lo Snackbar "Termina la partita in corso per cambiare la regola del 40-40". La riga delle regole sotto il selettore dice la regola scelta ("Set unico · un vantaggio, poi punto secco · tie-break a 7").

**Dove vive la regola.** `SportRegistry.forMatch(id, serveOrder, deuce)` innesta la regola nella configurazione come l'ordine di servizio. La partita la salva nella colonna `matches.deuceRule` (migrazione 15 -> 16, additiva, `''` = la regola dello sport, cioe' quella con cui le partite di prima sono state giocate) e ripristino, storico, Cronaca ed export la rigiocano con `Match.regole()`. Il telefono la manda all'orologio nello stato v2 (`deuce_rule`, chiave additiva: un telefono che non la manda vale "la regola dello sport"); l'orologio la usa quando rifa' il conto dei tocchi in coda e la salva in `LastKnownMatch` per il riavvio senza telefono. Le etichette vengono dal motore: "AV" al vantaggio, "PV" sul 40-40 decisivo, sul telefono e sul quadrante.

### Piano UI (CLAUDE.md, punto 3)

- **Compito e valore.** Il compito della schermata resta preparare la partita; il valore nuovo e' la regola del 40-40, accanto allo sport perche' e' una regola dello sport.
- **Gerarchia e layout.** Nel gruppo "Partita", nella stessa riga dello sport: selettore dello sport, selettore "Sul 40-40" (12dp sotto), riga delle regole come didascalia. Nessun gruppo nuovo, nessuna card.
- **Componenti riusati.** `Widget.App.TextInputLayout.ExposedDropdownMenu` con `AutoCompleteTextView`, come sport e lingua; lo Snackbar del cambio sport rifiutato; `space_12`. Nessun token, colore, icona o stile nuovi.

| Stato | Cosa si vede |
|---|---|
| Calcio | il selettore non c'e' |
| Padel o tennis | il selettore con la regola in vigore (scelta, o quella dello sport) |
| Premuto / fuoco / selezionato | quelli del menu a tendina di Material, come sport e lingua |
| Partita in corso | il tocco su un'altra voce mostra lo Snackbar e il selettore torna alla regola vera |
| In gioco | "AV" e "PV" nelle cifre del telefono e del quadrante, come gia' nel tennis |

- **Movimento.** Nessuno nuovo.
- **Accessibilita'.** Il selettore ha l'etichetta visibile "Sul 40-40" (letta da TalkBack); le voci sono parole.
- **Schermo stretto e 200%.** Il selettore occupa la larghezza della riga; le voci sono brevi.
- **Conflitti dichiarati.** Nessuno.
- **Pattern vietati** (`review.md`): niente maiuscolo, emoji, ombre, colori scritti a mano, card nuove; nessun peso nuovo; nessun ciclo. Nessun `--` nei commenti XML, nessun apostrofo nelle stringhe nuove.

### Come si prova

`SportRegistryTest` (forMatch innesta la regola e il padel gioca AV e poi PV, daNome), `OfflineScoreTest` (col killer point il polso gioca il vantaggio e il punto secco come il telefono; un orologio riavviato ritrova la regola), `WearViewModelTest` (la chiave additiva), `WearProtocolGoldenTest` (la chiave congelata), `MainViewModelTest` (alla ripresa vale la regola della riga; la regola delle impostazioni vale per la partita e arriva all'orologio), `DatabaseMigrationTest` (15 -> 16 e la catena, strumentati). Cosa li rende rossi: leggere le regole dal registro nel ripristino o nel conto al polso, non mandare la chiave all'orologio, non salvarla con la partita.

### Debiti dichiarati

1. Il file della partita (export e Diretta) porta gia' la regola in `config.deuce`, e la dashboard la traduce in `goldenPoint` e `killerPoint` (`docs/dashboard/riferimento-match-log.js`): ora puo' valere `KILLER_POINT` o `ADVANTAGE`. Non provato contro la dashboard vera (produzione).
2. L'orologio non ha un selettore: la regola si sceglie sul telefono.

## Revisione di menu e pagine - 27 ottobre 2026

Richiesta del proprietario: i menu di telefono e orologio sono "poco leggibili e molto confusionari", e sull'orologio lo spazio non e' usato bene; poi, approvata la direzione, la revisione di tutte le pagine (non il tabellone). Audit con le foto prese stasera sul telefono S26 e sul Galaxy Watch (480x480, 340dpi, circa 227dp) e proposta schermata per schermata: <https://claude.ai/artifact/R97K4QRxHkd33ohzcpMQf5>. Le foto stanno sul PC in `build\audit_menu_prima\` (ignorata da git).

**Le tre cause ricorrenti.** (1) Sull'orologio menu, scelta dello sport e Prossima partita stanno in un `BoxInsetLayout` con `boxedEdges="all"` e il titolo fermo sopra: le voci occupano circa 270x215 pixel su 480x480 e gia' la seconda esce tagliata. Sul tondo le liste vanno a tutta larghezza (`wearable.md`: si stringono solo vicino al bordo alto e basso). (2) Sul telefono il testo dei bottoni secondari era `text-secondary` (#8A8A9A), quasi uguale al disattivo (#6B7077): Storico, Condividi, Apri la serata, Gruppo sembravano spenti. (3) Il foglio Partita e le Impostazioni mescolano la partita in corso e l'app (griglia di sei bottoni uguali, lingua e account fra le regole). In piu', trasversali: lingua mista ("Team 1" nello storico e nelle impostazioni, "Squadra 1" nella Cronaca, maiuscole all'inglese) e lessico del calcio nel padel (gol, Attacco e Difesa, ruoli).

**I passi, una PR ciascuno.** 1 grigio dei bottoni e testi; 2 orologio a tutta larghezza (dopo la PR #195 e le impostazioni al polso, che toccano gli stessi file); 3 foglio Partita (prima la partita, poi l'app come righe con la freccia; un solo "In campo") e Impostazioni (Squadre, Regole, Padel Elite, App); 4 Padel Elite (collegati in cima, gli altri dietro una ricerca); 5 Storico e Cronaca (un tocco apre la Cronaca, le sezioni senza dati spariscono); 6 Statistiche, Giocatori e Modifica giocatore secondo lo sport; 7 dialoghi (tavolozza di colori, ricerca in Aggiungi giocatore, sport con la spunta).

### Passo 1: grigio dei bottoni e testi

- **Bottone secondario.** `button_text_secondary` passa da `eliteTextSecondary` a `eliteTextPrimary`; il disattivo resta `eliteTextDisabled`. Lo usano tutti i `Widget.App.Button.Secondary` (testo e icona). Con l'alto contrasto segue il tema come prima.
- **Il nome predefinito della squadra.** Nei dati resta "Team 1"/"Team 2" (`MatchSettingsRepository.DEFAULT_TEAM1_NAME`): e' il segnale "nessun nome scelto" per `EtichettaSquadra`, per l'orologio e per le partite gia' salvate. A schermo `EtichettaSquadra.mostrato` lo traduce nel predefinito della lingua ("Squadra 1"): storico, anteprima del colore, registro. Nelle Impostazioni il campo resta vuoto e dice "Nome squadra 1"; lasciato vuoto, salva di nuovo il predefinito e non una stringa vuota.
- **Registro.** Le righe informative scritte nel codice in inglese ("Keeper timer set to 30 seconds", "Score correction for", "Undo: Goal removed", "added to", "Match ended") vengono dalle risorse: in inglese restano le parole di prima, in italiano "Portiere a 30 secondi", "Correzione per Squadra 1", "Annullato un gol", "Partita finita: 3 – 2". La durata del portiere entra nel registro solo negli sport col portiere (`hasAuxCountdown`) e mai alla prima lettura delle impostazioni: quella arriva prima dello sport e scriveva la riga a ogni apertura dell'app, anche nel padel (visto sul telefono).
- **Maiuscole in frase.** "Nome del giocatore", "Aggiungi un giocatore alla squadra 1", "Colore della squadra 1", "Terminare la partita?", "Azzerare il tempo?" e le altre; "Gestisci giocatori" diventa "Giocatori", come il comando che la apre.

| Stato | Cosa si vede |
|---|---|
| Secondario normale | testo e icona in `text-primary` su `surface-raised` col contorno |
| Premuto / fuoco | la pressione condivisa e l'anello di fuoco, come prima |
| Disattivo | testo `text-disabled`, ora distinguibile dal normale |
| Nome non scelto | campo vuoto con l'etichetta "Nome squadra 1"; nello storico "Squadra 1" |
| Registro, padel | nessuna riga del portiere |

- **Movimento.** Nessuno nuovo. **Accessibilita'.** Il testo dei secondari passa da 4,9:1 a 10,9:1 su `surface-raised` (tema standard); il disattivo resta a 3,3:1, quindi i due ora si distinguono. **Schermo stretto e 200%.** Testi piu' lunghi di qualche lettera nei titoli dei dialoghi ("Aggiungi un giocatore alla squadra 1"): vanno a capo. **Conflitti dichiarati.** Nessuno. **Pattern vietati** (`review.md`): nessun colore scritto a mano, nessun token nuovo; niente maiuscolo; nessun `--` nei commenti XML e nessun apostrofo nelle stringhe nuove.

### Debiti dichiarati

1. Le partite gia' salvate con un registro in inglese restano in inglese: si traducono solo le righe nuove.
2. Non visto a schermo finche' non e' provato sul telefono (passo nella PR).

### Passo 2: le liste dell'orologio a tutta larghezza

Il problema era la causa (1): menu, scelta dello sport e Prossima partita in un `BoxInsetLayout` con `boxedEdges="all"`, cioe' nel quadrato inscritto nel cerchio, e il titolo fermo sopra. `wearable.md`: il quadrato inscritto vale per il testo e i bersagli di una schermata con un valore solo; le liste invece vanno a tutta larghezza e si stringono vicino al bordo alto e basso, con la riga al centro intera.

- **Gerarchia.** Un compito per schermata, com'era: il menu (le voci della partita, chi gioca, le impostazioni del polso), la scelta (sport, regola del 40-40, durata del portiere), la prossima partita (le coppie e Inizia, l'unico primario). L'ordine delle voci e la logica di `MenuVoci` non cambiano: cambia solo come si vedono.
- **Layout.** Una `ScrollView` a tutto schermo con una colonna; i margini sono ruoli nuovi e riusabili, `lista_margine_lati` (5% del diametro: 10dp sul 192, 12dp sul 227, 8dp sui quadrati), `lista_margine_alto` (16, 19, 8dp) e `lista_margine_basso` (70, 83, 16dp: quanto basta perche' l'ultima riga arrivi al centro, dove il tondo e' largo). Il titolo e' la prima riga della colonna e scorre via. Su 192dp le voci passano da circa 130dp a 172dp di larghezza, e a schermo si leggono tre voci invece di una e mezza.
- **Le righe.** `item_sport_wear` come i bottoni di Wear: icona a sinistra (`icon_standard`, decorativa), nome a 15sp in 600 e sotto la riga del perche' o del valore, allineati all'inizio; alta almeno 52dp; `Widget.App.GroupRow` (surface, raggio 8, pressione condivisa). Ogni riga e' sul suo tono e fra l'una e l'altra c'e' il nero (`bg_spazio_righe`, space-4) invece della linea: un gruppo con un fondo solo non si puo' stringere riga per riga. Le squadre in campo restano righe sul nero, senza fondo (si leggono, non si toccano). Nella Prossima partita le due coppie restano gruppi tonali (si tocca un nome e poi l'altro): si stringe il gruppo intero.
- **Stringersi sul bordo.** `BordoTondo.scala` (pura): il rapporto fra la corda del cerchio all'altezza del bordo della riga piu' lontano dal centro e la larghezza della riga, fra 0,7 e 1. `ListaTonda` lo applica a ogni scorrimento e a ogni impaginazione, solo sul tondo, con la matrice di disegno della riga (`animationMatrix`): `scaleX` e `scaleY` sono della pressione condivisa, e le due cose si sommano. Il tocco resta sulla riga intera.
- **Icone** (tabella G-3, colonna dell'orologio): Prossima partita `groups`, Sport `sports`, Fine partita `flag`, Scarta la coda `delete`, Sul 40-40 `sports_tennis`, Vibrazione `vibration`, Portiere `sports_handball`. Nuovi concetti in tabella: "Scegli lo sport", "Fine partita", "Vibrazione". L'icona ha il colore del nome: spenta, rossa o nera sulla voce armata insieme a lui.
- **Chiusura automatica del menu** da 10 a 20 secondi: con le impostazioni in fondo la lista e' sempre piu' lunga di uno schermo, e chi leggeva "In campo" se la vedeva sparire. Tocco, scorrimento e corona la riarmano come prima.

| Stato | Cosa si vede |
|---|---|
| Normale | riga surface, icona e nome in `text-primary`, sotto il valore in `text-secondary` |
| Premuto | la pressione condivisa (0,97 e 0,85), sopra la scala del bordo |
| Fuoco (corona) | la colonna ha il fuoco e scorre; nessun cambio sulle righe |
| Selezionato | nella scelta, la spunta e "in uso" in `text-primary` |
| Disattivo | icona e nome in `text-disabled`, il perche' sotto in `text-secondary` |
| Armata (Fine partita, Scarta) | la riga si riempie di `status-error`, icona e testo neri, "Tocca di nuovo" |
| In attesa / errore | Prossima partita: la riga d'avviso, come prima |
| Vuoto | senza rose il gruppo "In campo" non c'e', come prima |
| Movimento ridotto | la pressione senza scala, come prima; la scala del bordo segue il dito e la corona, non e' un'animazione |

- **Movimento.** Nessuna animazione nuova: la scala del bordo e' geometria che segue lo scorrimento. **Accessibilita'.** Icone nascoste a TalkBack (il nome dice gia' tutto); bersagli da 52dp intatti anche dove la riga e' disegnata piu' piccola; i titoli restano intestazioni. **Testo grande.** Le righe vanno a capo in altezza; `TitoloIntero` scende fino a 14sp invece di spezzare una parola. **Quadrato.** Nessuna riga si stringe, margini piccoli e uguali.
- **Pattern vietati.** Nessuna griglia di card uguali (una colonna di comandi, come Wear); niente icone in quadrati colorati (l'icona sta sul tono della riga); niente ombre; un primario solo per schermata (Inizia nella Prossima partita, nessuno nel menu, dove Fine partita e' il comando distruttivo in rosso con la seconda conferma).
- **Conflitto dichiarato.** `wearable.md` dice anche "nothing essential in the outer ring": le righe vicino al bordo ci entrano, ma rimpicciolite e mai tagliate, e la corona le porta al centro. E' la lettura che la stessa pagina da' delle liste.

### Debiti dichiarati del passo 2

1. Non visto a schermo: verificato dai test (Robolectric, tondo 192dp e quadrato 180dp) e dalla CI; le foto sull'emulatore rotondo e sul Galaxy Watch restano da fare.
2. La scelta dello sport e' ora una colonna e non una `WearableRecyclerView`: le scelte sono al massimo una manciata. Se un elenco diventasse lungo (decine di voci) tornerebbe una lista che ricicla.

### Passo 3a: il foglio Partita, prima la partita e poi l'app

Il foglio finiva con sei bottoni secondari uguali in griglia (pattern vietato), che mescolavano comandi su questa partita (Condividi, Azzera tempo) e posti dell'app (Storico, Statistiche, Giocatori, Impostazioni).

- **Gerarchia e layout.** Sopra, invariati: Serata, Diretta, rose, coppie, registro, formazioni. Poi il gruppo **Questa partita** (didascalia): Condividi e, col cronometro, Azzera tempo, secondari della stessa misura in una fila (senza cronometro Condividi prende la fila intera); sotto **Fine partita**, l'unico primario del foglio, a tutta larghezza. In fondo il gruppo **App**: un `Widget.App.Group` con quattro righe (Storico, Statistiche, Giocatori, Impostazioni), icona a sinistra, nome `RowTitle`, freccia `chevron_right` in `text-secondary`, linee rientrate fra le righe (`Widget.App.RowDivider`). Le righe si leggono come "vai a", non come comandi.
- **Componenti riusati.** La riga e' quella della Serata (`Widget.App.Pressable`, `bg_focus_ring`, `control_touch`); gruppo, divisore e didascalia quelli del foglio. Icone nuove sul telefono, dalla tabella G-3: `history`, `bar_chart` (c'era), `person` (Giocatori: sono persone, `group` e' la squadra), `settings`, `chevron_right`.
- **Stati.** Riga normale, premuta (pressione condivisa), fuoco (anello), nessuna riga spenta. Azzera tempo spento finche' il servizio del cronometro non e' legato, come prima. Movimento: nessuno nuovo. Accessibilita': icone e freccia nascoste a TalkBack, la riga si legge col suo nome; le due didascalie sono intestazioni. 200%: le righe crescono in altezza, i due secondari vanno a capo su due righe come prima.
- **Pattern vietati.** Tolta la griglia di bottoni uguali; un solo primario; niente card nuove (il gruppo App e' un gruppo tonale con righe); niente ombre.

### Debiti dichiarati del passo 3a

1. **Un solo "In campo"** (proposta: nel padel e nel tennis solo le coppie) resta aperto: le rose sono l'unico posto dove si aggiungono i giocatori a un lato, e togliendole nel padel servirebbe un altro modo per farlo. Domanda per il proprietario.
2. Serata e Diretta restano gruppi col titolo: la proposta le vuole righe, da fare insieme al punto 1.
3. Le Impostazioni (Squadre, Regole, Padel Elite, App) sono il passo 3b.
4. Non visto a schermo: verificato solo dai test e dalla CI.

### Passo 3b: le Impostazioni, quattro gruppi nell'ordine in cui si usano

La schermata si chiamava "Impostazioni partita" ma teneva anche la lingua, in mezzo alle regole, e l'alto contrasto in un gruppo a parte "Accessibilita'"; Padel Elite stava in fondo. Il colore della squadra era un quadrato con l'icona della tavolozza, dipinto del colore: un'icona che non diceva il colore.

- **Gerarchia e layout.** Titolo "Impostazioni" (come la riga del foglio Partita). Quattro gruppi tonali: **Squadre** (nome e colore per squadra), **Regole** (sport con le regole che il motore applica, poi il timer del portiere dove c'e'), **Padel Elite** (solo se la build conosce il Supabase), **App** (lingua, poi alto contrasto con il perche' e l'interruttore). Prima cio' che prepara la partita, in fondo cio' che vale per tutta l'app. La linea sopra il timer del portiere sta dentro il suo blocco: sparito il timer, non resta una linea in fondo al gruppo.
- **Componenti riusati.** Gruppi, righe, divisori, campi e interruttore quelli di prima. Il colore resta il `Widget.App.Button.Secondary` dipinto con `dipingiDiSquadra`, con la forma nuova `ShapeAppearance.App.Swatch` (cerchio, `cornerSize` 50%): il bordo `elite_outline` del secondario resta, cosi' anche un blu notte si stacca dal gruppo. `ic_palette` non si usa piu' ed e' tolta, con la sua riga di G-3.
- **Stati.** Campi e interruttore come prima (normale, fuoco, premuto, spento dove serve). Il cerchio: premuto con la pressione condivisa, fuoco con l'anello del bottone, TalkBack legge "Colore squadra 1/2". Movimento: nessuno nuovo. 200% e 360dp: i gruppi sono colonne, crescono in altezza; il cerchio resta di 48dp accanto al campo del nome.
- **Pattern vietati.** Niente card nuove, niente ombre, nessun primario nuovo; un'icona decorativa in meno.

### Debiti dichiarati del passo 3b

1. Il gruppo Padel Elite e' quello di prima (stato, Entra, Gruppo, Esci): la proposta lo vuole una riga che porta alla sua schermata, con Esci spostato li'. Lo fa il passo 4.
2. Le scelte (sport, regola del 40-40, lingua) sono ancora campi con la tendina: la proposta le vuole righe nome e valore con il menu che si apre dalla riga. Cambia il componente e i test che toccano i campi (LinguaUnicaTest e i test dello sport): passo a se', dopo il 4.
3. Non visto a schermo: verificato solo dai test e dalla CI.

### Passo 4: Padel Elite, prima i collegati e gli altri dietro una ricerca

La rosa del gruppo erano cinquanta righe in ordine alfabetico, quasi tutte "Non collegato": i tre giocatori collegati, quelli che contano, sparivano in mezzo. Aggiorna ed Esci stavano sotto la lista, raggiungibili solo scorrendo fino in fondo. Nelle Impostazioni il gruppo Padel Elite aveva tre bottoni (Accedi, Gruppo, Esci) che portavano tutti, tranne Esci, alla stessa schermata.

- **Gerarchia e layout.** La schermata, con accesso: **Gruppo per l'invio** (le righe a scelta singola di prima), poi **Giocatori del gruppo** in due parti: **Collegati · n**, sempre in vista, in ordine di nome; **Da collegare · n** con il campo "Cerca un giocatore": a campo vuoto si vedono solo i nomi uguali da confermare (pochi, e chiedono una risposta) e una riga che dice di scrivere un nome; scrivendo compaiono quelli che corrispondono, senza badare a maiuscole e accenti. "Collega i nomi uguali" resta sotto i da collegare. In fondo **Esci da Padel Elite**, testuale, e sotto l'account ("Collegato come ..."), che prima stava in cima. **Aggiorna** passa nella barra in alto (icona `refresh` con la parola per TalkBack) e rilegge gruppi e giocatori. Nelle Impostazioni il gruppo Padel Elite e' **una riga** con la freccia: senza accesso "Accedi a Padel Elite" e "Non collegato", con accesso "Gruppo per l'invio" e "account · gruppo". Esci sta solo nella schermata di Padel Elite.
- **Componenti riusati.** Le righe della rosa (`item_padel_elite_player`), i gruppi tonali, il divisore rientrato, `EmptyStateView`, il campo `Widget.App.TextInputLayout` con l'icona `search` e la croce per svuotarlo; la riga delle Impostazioni e' quella del foglio Partita (`Widget.App.Pressable`, `bg_focus_ring`, `chevron_right`). La divisione e' una funzione pura, `RosaInVista`, che la schermata chiama a ogni lettura e a ogni lettera: la ricerca non fa richieste.
- **Stati.** Rosa: scegli un gruppo, in caricamento, vuota, errore, non piu' membro (come prima), pronta. Pronta: nessun collegato (la parte Collegati sparisce), tutti collegati (sparisce Da collegare), campo vuoto (suggerimento), nessun risultato ("Nessun giocatore da collegare con questo nome", annunciato da TalkBack), righe trovate. Aggiorna c'e' solo con l'accesso. Movimento: nessuno nuovo. 200% e 360dp: tutto e' una colonna; il campo di ricerca resta alto almeno 48dp.
- **Pattern vietati.** Tolti un bottone ripetuto (Gruppo che portava dove portava Accedi) e una lista da scorrere per arrivare ai comandi; nessun primario nuovo, niente card, niente ombre. Il debito 1 del passo 3b e' chiuso.

### Debiti dichiarati del passo 4

1. Il modulo di accesso (Google, email e password) non cambia: era gia' secondo le regole (G-4).
2. La rosa si ridisegna intera a ogni lettera: con le rose di oggi (meno di 60 nomi) non si vede; se crescono, passare a una RecyclerView con DiffUtil.
3. Non visto a schermo: verificato solo dai test e dalla CI.

### Passo 5a: la Cronaca mostra solo cio' che c'e'

La Cronaca di una partita salvata senza ordine di servizio e senza tempi aveva quattro gruppi su sei che spiegavano un'assenza (Game per game, Servizio, Tempi, Momenti chiave), ognuno con titolo e paragrafo; il grafico dell'andamento occupava la sua altezza anche per un punto solo.

- **Gerarchia e layout.** Il tabellone resta in cima e c'e' sempre: senza punti dice "Nessun punto registrato" e perche', e il resto non c'e'. Le altre sezioni compaiono solo coi loro dati: l'andamento da tre punti in su, Game per game con almeno un game chiuso, Servizio e Tempi se la partita li ha registrati, Momenti chiave se ce n'e' almeno uno. Sotto le sezioni, se mancano servizio o tempi, una riga sola: "Non registrati in questa partita: servizio e tempi" (al singolare con uno solo), e sotto, piu' piccolo, il perche' e cosa fare (le frasi di prima). Senza punti la riga non c'e': lo dice gia' il tabellone.
- **Componenti riusati.** Le sezioni (`chronicle_section`) e i loro gruppi; la riga in fondo e' `RowTitle` in testo secondario con la `Caption` sotto, fuori dai gruppi. Tolte sette stringhe che non si usano piu' (gli stati vuoti delle sezioni sparite), nelle due lingue.
- **Stati.** Partita completa (sei sezioni, nessuna riga in fondo); senza servizio e/o senza tempi (sezioni via, riga in fondo); senza punti (solo il tabellone); partita che non c'e' piu' (`EmptyStateView`, come prima). Movimento: nessuno. 200%: le sezioni sono colonne; la riga in fondo va a capo.
- **Pattern vietati.** Tolti quattro stati vuoti uguali uno sotto l'altro; niente card nuove, niente ombre.
- **Il resto del passo 5 (5b).** Lo Storico con le partite per giorno, la riga che apre la Cronaca e Invia, Esporta, Elimina nella barra della Cronaca e' il passo 5b: sposta comandi che scrivono (invio a Padel Elite, eliminazione) e merita una PR sua.

### Debiti dichiarati del passo 5a

1. Non visto a schermo: verificato solo dai test e dalla CI.

### Passo 6a: le Statistiche seguono lo sport

In una serata di padel le Statistiche dicevano Generale, Attacco, Difesa e contavano i gol, tutti a zero, col primo in lime; Attacco e Difesa dicevano "Nessuna statistica": la stessa assenza raccontata in due modi.

- **Gerarchia e layout.** Lo sport in uso (quello delle impostazioni) decide la classifica. Nel calcio restano le tre schede e i gol. Nel padel e nel tennis le schede non ci sono: una riga di titolo, "Padel · partite vinte", e la classifica delle partite vinte su quelle giocate ("Anna 3 vinte su 5"), prima chi ne ha vinte di piu', a parita' chi ne ha giocate di meno. Il dato c'e' gia': le formazioni salvate a fine partita (`MatchPlayerCrossRef.teamNumber`) e il risultato, la stessa regola delle vittorie di oggi; la nuova query `getPlayerRecords` le conta per sport, solo partite chiuse. Nessuna colonna nuova, nessuna migrazione.
- **Componenti riusati.** La riga `item_player_stat` e il gruppo della classifica; un adapter nuovo, `RecordAdapter`, per le vittorie. Le regole comuni stanno nella funzione pura `Classifica`: il lime marca il primo solo se e' davvero primo (sopra zero e davanti al secondo), e una classifica di soli zeri e' lo stato vuoto.
- **Stati.** Calcio con gol (come prima, lime solo a un primo vero), calcio con tutti a zero (stato vuoto), padel o tennis con partite (classifica), senza partite chiuse con giocatori (stato vuoto con il perche': servono le coppie in campo). TalkBack legge rango, nome e "3 vinte su 5". Movimento: nessuno. 200%: righe piu' alte, il valore resta a destra.
- **Pattern vietati.** Tolte tre schede che nel padel non volevano dire niente e un lime che non marcava nessuno; niente card nuove, niente ombre.

### Debiti dichiarati del passo 6a

1. Le partite giocate in padel senza giocatori in campo (senza rose o coppie) non contano: non si sa chi le ha giocate.
2. Giocatori e Modifica giocatore (filtri e ruoli da calcio anche nel padel, ordine attivo, ricerca) sono il passo 6b.
3. Non visto a schermo: verificato solo dai test e dalla CI.

### Passo 5b, prima parte: lo Storico per giorno

Lo Storico era una colonna di schede tutte uguali, ognuna con la sua data in piccolo ("Padel · 12/09 18:30 · 47 min"): per ritrovare la partita di ieri sera si leggevano le date una per una.

- **Gerarchia e layout.** Sopra la prima partita di ogni giorno c'e' il titolo del giorno: "Oggi", "Ieri", poi la data col giorno della settimana ("Domenica 25 ottobre"), con l'anno solo se non e' quello in corso. Nella scheda la riga meta tiene l'ora ("Padel · 18:30 · 47 min"). Il giorno si calcola nel fuso del telefono; le partite arrivano gia' dalla piu' recente. La logica sta in `GiorniDelloStorico` (funzione pura, `RigaDelloStorico` = giorno o partita).
- **Componenti riusati.** Il titolo e' come i titoli dei gruppi delle Impostazioni: `RowTitle` in testo secondario, intestazione per TalkBack (si salta da un giorno all'altro), allineato al testo delle schede. Le schede (`match_item`) non cambiano. Nessun colore, spazio o tipo nuovo; due stringhe nuove ("Oggi", "Ieri").
- **Stati.** Vuoto: nessun titolo, resta `EmptyStateView` come prima. Un giorno con una partita sola ha il suo titolo lo stesso. Movimento: nessuno nuovo (l'apertura del dettaglio resta quella di G-4). 200%: il titolo va a capo.
- **Pattern vietati.** Niente card per i giorni (i giorni non sono elementi indipendenti), niente ombre, niente date ripetute in ogni scheda.
- **Seconda parte, ferma su una domanda.** La proposta portava Invia, Esporta ed Elimina nella barra della Cronaca e faceva aprire la Cronaca toccando la scheda. Ma la Cronaca c'e' solo per padel e tennis con registro: il calcio e le partite salvate col solo punteggio non l'hanno, e per loro Elimina deve restare nella scheda. Due posti per lo stesso comando sono peggio di uno: la scelta e' di Cioti (vedi i debiti).

### Debiti dichiarati del passo 5b

1. Domanda per Cioti: i comandi della partita (Invia, Esporta, Elimina) restano nel dettaglio della scheda per tutti gli sport, oppure passano nella barra della Cronaca per la racchetta e restano nella scheda solo per il calcio?
2. Non visto a schermo: verificato solo dai test e dalla CI.

### Passo 6b: i Giocatori seguono lo sport

I Giocatori avevano i filtri per ruolo da calcio anche nel padel, la stessa icona a barre grigia e senza parola su ogni riga (apriva un dialogo con presenze, gol e "Azzera"), un menu Ordina che non diceva quale ordine fosse attivo e una lente che apriva un campo invisibile.

- **Gerarchia e layout.** In cima la ricerca, un campo vero e sempre visibile (lo stesso `TextInputLayout` con la lente e la croce di Padel Elite, passo 4); sotto, solo nel calcio, i filtri per ruolo su una riga che scorre; poi il gruppo delle righe. La riga e' il nome e sotto il valore che conta: "3 partite", nel calcio "3 partite · 5 gol"; i chip dei ruoli solo nel calcio. A destra la freccia (`chevron_right`, decorativa): la riga intera apre Modifica giocatore. Lo sport e' quello attivo nelle Impostazioni, come nelle Statistiche (passo 6a, `StatisticsViewModel.contaIGol`).
- **Ordine.** Nel menu Ordina le voci sono una scelta sola con la spunta su quella in uso (Per nome, Per partite giocate, Per gol); "Per gol" c'e' solo nel calcio, e passando a uno sport senza gol l'ordine torna per nome.
- **Azzera le statistiche.** Il dialogo dietro l'icona a barre e' tolto: partite e gol sono gia' nella riga. Azzerare sta nel menu di Modifica giocatore, solo se c'e' qualcosa da azzerare, con la conferma che dice cosa succede ("Partite e gol di Rossi tornano a zero.") e il primario "Azzera". In Modifica giocatore, fuori dal calcio, titolo e lista dei ruoli si nascondono: la pagina e' il nome; i ruoli gia' scelti restano salvati.
- **Componenti riusati.** Riga `Widget.App.Pressable` con `RowTitle` e `Caption`, gruppo tonale e divisori di prima, campo di ricerca di Padel Elite, freccia del foglio Partita (passo 3a). La ricerca ignora maiuscole e accenti con la stessa funzione di Padel Elite, ora in `utils/TestoPiano.kt`; nel calcio cerca anche nei ruoli. Stringhe nuove: `player_value_matches`, `player_value_with_goals`, `reset_stats_confirm`; tolte `menu_search`, `cd_player_stats`, `player_stats_title`, `player_stats_message`, `player_deleted`, `roles_not_specified`, che non si usavano piu'.
- **Stati.** Nessun giocatore (stato vuoto con "Aggiungi giocatore", FAB nascosto); la ricerca o il filtro non trovano nessuno (stato vuoto senza bottone, FAB visibile); padel e calcio come sopra; giocatore a zero partite (niente "Azzera"). Movimento: nessuno nuovo. 200%: il nome si tronca su una riga, il valore va a capo; il campo di ricerca resta alto `control_touch`.
- **Pattern vietati.** Tolti un comando di sola icona ripetuto su ogni riga, un menu che non mostra lo stato, una ricerca nascosta e le informazioni da calcio nel padel. Niente card, niente ombre nuove.

### Debiti dichiarati del passo 6b

1. Modifica giocatore nel calcio ha ancora la lista di 18 caselle di ruolo; i ruoli come chip in quattro gruppi della proposta sono un passo a parte (6c).
2. Non visto a schermo: verificato solo dai test e dalla CI.

### Passo 7a: il dialogo del colore della squadra

Il dialogo era una ruota con tutti i colori, un'anteprima con un "12" e il nome, e due bottoni di testo di peso uguale (Annulla e Seleziona): per scegliere un rosso si cercava a occhio sulla ruota, e un colore illeggibile sul nero del tabellone era possibile.

- **Gerarchia e layout.** Otto cerchi da 48dp (`control_touch`) in due righe da quattro, come il colore nelle Impostazioni (`ShapeAppearance.App.Swatch`, dipinti con `dipingiDiSquadra`); sotto, "Altro colore…" come bottone di testo, centrato. Il primario e' uno solo: "Usa questo colore" (prima "Seleziona"); Annulla resta testo. Il titolo e' "Colore della squadra 1". La ruota non e' sparita: "Altro colore…" chiude la tavolozza e apre il dialogo di prima (ruota, luminosita', anteprima) con lo stesso "Usa questo colore".
- **Tavolozza.** `team_palette_1..8` in `colors.xml`: i due predefiniti dei lati (lime e ciano) e sei colori nuovi (rosso, arancio, giallo, blu, viola, rosa). Ogni colore ha almeno 6,57:1 sul nero del tabellone, 5,65:1 su `elite_surface` e 5,20:1 su `elite_surface_raised` (WCAG 1.4.11 chiede 3:1), e l'inchiostro scuro di `TeamInk` sopra regge almeno 6,07:1; il test `i colori della tavolozza si staccano dal nero e dalle superfici` lo controlla. I valori nuovi nominano un ruolo riusabile (colore di squadra), non un colore da una schermata.
- **Stati.** Selezionato: anello chiaro da 2dp (`focus_ring_width`) e spunta `ic_check` nell'inchiostro di squadra, e `isSelected` per TalkBack: il colore non e' l'unico segno. Colore della ruota fuori dalla tavolozza: nessun cerchio segnato, e "Usa questo colore" lo salva com'e'. Ogni cerchio ha il nome del colore come `contentDescription` ("Arancio"). Movimento: la pressione di sempre (0,97 e 0,85), niente nuovo. 200%: il dialogo scorre.
- **Pattern vietati.** Tolti due bottoni di testo di peso uguale come unica via di scelta, e l'anteprima con un numero finto. Niente ombre, niente card.
- **Resto del passo 7.** "Aggiungi giocatore" con la ricerca in cima e lo sport con la spunta sulla voce in uso restano da fare (7b).

### Debiti dichiarati del passo 7a

1. Domanda per Cioti: i sei colori nuovi della tavolozza (rosso, arancio, giallo, blu, viola, rosa) vanno bene o ne vuole altri? Si cambiano in `colors.xml` senza toccare altro.
2. Non visto a schermo: verificato solo dai test e dalla CI.

### Passo 7b: "Aggiungi un giocatore" dice una volta sola come si entra nell'app

Nell'elenco con la rosa di Padel Elite ogni giocatore della dashboard non ancora nell'app aveva sotto il nome la stessa riga grigia, "Si aggiunge all'app quando lo scegli": con sei nomi, sei volte la stessa frase, e nessuna riga che dicesse qualcosa di diverso.

- **Gerarchia e layout.** La frase passa una volta sola sotto il titolo della prima sezione che viene dalla dashboard (Nello stesso girone, Iscritti o Padel Elite): "Chi non e' ancora nell'app ci entra quando lo scegli". Le righe dei giocatori nuovi hanno solo il nome; restano le didascalie che dicono qualcosa di diverso ("Gia' nell'app", "Nell'app come Bruno"). La logica sta in `righeDellaScelta` (funzione pura, prima era dentro `MainActivity`); l'intestazione di sezione ha una didascalia facoltativa (`item_dialog_section`, `RowTitle` in testo secondario con la `Caption` sotto).
- **Componenti riusati.** Riga e intestazione del dialogo di prima; stringa esistente con il testo cambiato (italiano e inglese). Nessun token nuovo.
- **Stati.** Rosa letta con giocatori nuovi (avviso sotto la prima sezione); tutti gia' nell'app (nessun avviso, restano le didascalie); rosa non letta (`DASHBOARD_NON_LETTA`, nessun avviso); solo giocatori dell'app (nessun avviso). Movimento: nessuno. 200%: il titolo e l'avviso vanno a capo.
- **Pattern vietati.** Tolta la stessa didascalia ripetuta su ogni riga.
- **Non toccato.** La ricerca in cima e lo sport con la spunta (resto del passo 7) restano da fare.

### Passo 3c, le Impostazioni a righe

Scelta di Cioti sulla bozza (28 ottobre 2026, B): sport, regola del 40-40 e lingua non sono piu' menu a tendina ma righe col nome a sinistra, il valore a destra e la freccia (stesso gesto della riga Padel Elite). Il tocco apre un foglio (`scegliDaElenco`, `MaterialAlertDialogBuilder` a scelta singola) con la spunta su quello in uso; toccarne una la sceglie e chiude, Annulla chiude. Il blocco del cambio sport o 40-40 a partita in corso dice come prima (snackbar). La riga del 40-40 resta nascosta fuori dagli sport con racchetta e la frase delle regole sta sotto le due righe.

- Stati: normale e premuto (`Widget.App.Pressable`), fuoco (anello), disattivo no (la riga e' sempre toccabile), errore (snackbar del blocco), movimento ridotto (nessun movimento nuovo).
- Schermo stretto e 200%: il nome va a capo, il valore resta a destra; da guardare a schermo.
- Debiti: la durata del portiere resta un campo numerico (la bozza la mostrava come riga con "3 minuti"); non visto a schermo.
- Controllo dei pattern vietati: nessun colore, spaziatura o durata scritti a mano; una sola famiglia di icone; nessuna ombra.

#### Prova a schermo del 28 ottobre 2026 (telefono vero SM-S948B, main 1007632, apk con lo stesso hash della build)

Visti e ok: punti 1 (Squadra 1/2, niente riga del portiere), 2 (quattro gruppi), 3 (Padel Elite: collegati prima, ricerca, Aggiorna), 4 (Cronaca con una partita povera di punti), 5 ("Ieri" e solo l'ora), 7 (Giocatori nel padel: partite, ricerca, ordine senza "Per gol"), 9 (avviso una volta sola). Non provabili col telefono com'e': "Oggi" nello Storico, la classifica e il calcio (servirebbe cambiare sport e toccare la partita in corso), "Azzera" (serve un giocatore con partite), la Cronaca ricca.

Difetti trovati e corretti in questa PR:
- Dialogo del colore squadra: due colori su otto (la `GridLayout` a pesi con cerchi da 48dp fissi); ora due righe da quattro, ogni cerchio in una cella a peso 1 (`dialog_team_palette.xml`). `LayoutAStrettoTest`.
- Storico a 360dp e 200%: il cestino usciva dalla scheda; i tre comandi stanno in un `Flow` che va a capo (`match_item.xml`). `LayoutAStrettoTest`.

Difetti noti, non corretti qui (tutti a 360dp e 200%): etichette della schermata di gioco troncate e didascalia "Game" persa; hint "Nome squadra" troncati nelle Impostazioni; la riga delle regole dello Storico lascia il "7" da solo ("tie-break a / 7", anche a 100%); la prima riga del Registro ("Partita ripresa") non ha l'ora. La slider di luminosita' nella ruota del colore non si vede, da verificare.

### Debiti dichiarati del passo 7b

1. Nessuna ricerca nel dialogo: con una rosa di cinquanta nomi si scorre ancora. Serve un dialogo con un campo e una lista filtrata al posto dell'adattatore del `MaterialAlertDialog`; non l'ho fatto di notte perche' tocca la scelta dalla dashboard di Padel Elite, che e' in produzione.
2. Lo sport nelle Impostazioni e' ancora un menu a tendina; la riga "Sport: Padel" con il dialogo e la spunta e' legata al debito 2 del passo 3b (righe nome e valore) e cambia molti test: da fare insieme.
3. Non visto a schermo: verificato solo dai test e dalla CI.

### Statistiche per sport (28 ottobre 2026, bozza A scelta nel thread)

Sul calcio le Statistiche mostravano ancora le partite di tutti gli sport insieme: `Player.appearances` e' un totale unico, e chi gioca a calcio e a padel vedeva nel calcio le partite di padel (e un giocatore di solo padel poteva comparire a zero gol). Per guardare il padel bisognava cambiare lo sport nelle Impostazioni.

- **Gerarchia e layout.** Sotto la barra, un chip per sport (Calcio, Padel, Tennis; scelta sola, sempre uno scelto): parte dallo sport in uso, ma si cambia lì senza toccare le Impostazioni. Sotto resta quello di prima: nel calcio le tre schede e i gol, nel padel e nel tennis la riga di titolo e le partite vinte. Il titolo ora dice solo "Partite vinte": lo sport lo dice il chip scelto e non si ripete. Nelle bozze il selettore era segmentato; l'ho fatto coi chip dei filtri per ruolo dei Giocatori (`item_chip_filter`) per non aggiungere un componente: **conflitto dichiarato** con `SegmentedControl` (indicatore che scivola), da rivedere se Cioti lo vuole segmentato.
- **Dati.** Nuova query `MatchDao.getPlayerSportCounts()`: partite chiuse giocate e vinte per giocatore e per sport, dalle formazioni salvate. Solo letture, nessuna colonna nuova, nessuna migrazione. `GetPlayerStatsUseCase` la usa per le partite e la percentuale del calcio, e toglie dalla classifica chi non ha giocato a calcio ne' ha gol. I Giocatori mostrano le partite dello sport in uso, e "Per partite giocate" ordina per quelle.
- **Azzera.** Le partite si contano dallo storico, quindi non si azzerano piu' dal giocatore: la voce diventa "Azzera i gol" (visibile solo con gol), e il messaggio dice che le partite restano nello storico. Prima azzerava anche `appearances`, che ormai non si mostra piu'.
- **Componenti riusati.** Chip, gruppo tonale, righe e stato vuoto di prima; nessun token, colore, spaziatura o durata nuovi.
- **Stati.** Calcio con gol; calcio senza gol (stato vuoto); padel o tennis con partite; senza partite chiuse con giocatori (stato vuoto con il perche'); chip scelto (testo, bordo e riempimento dello stile `Widget.App.Chip`, non solo il colore). Dopo una rotazione resta lo sport guardato. Movimento: nessuno nuovo.
- **Pattern vietati.** Tolto il titolo che ripeteva lo sport del chip; niente card nuove, niente ombre.

### Debiti dichiarati delle Statistiche per sport

1. **Bozza B (scheda del giocatore con un blocco per sport)** non fatta: `getPlayerSportCounts` ha gia' i dati, serve la schermata.
2. I Giocatori seguono lo sport in uso (non hanno il selettore): le partite sono di quello sport.
3. Le partite salvate prima delle formazioni (o senza giocatori in campo) non contano, come per le vittorie. Il totale vecchio di `appearances` resta nella colonna ma non si mostra.
4. Non visto a schermo: verificato solo dai test e dalla CI.

## Tema chiaro - 28 ottobre 2026

Scelta di Cioti sulla bozza 3 (B e C, 28 ottobre 2026): l'app ha un tema chiaro e una riga Aspetto nelle Impostazioni, gruppo App, sopra la lingua, con tre scelte: Scuro, Chiaro, Come il telefono. Di base resta Scuro: chi aggiorna non trova un'altra app. Il conflitto 2 ("niente tema chiaro") e' chiuso; il quadrante dell'orologio resta scuro (il chiaro e' solo del telefono).

### Piano UI (CLAUDE.md, punto 3)

- **Gerarchia e layout.** Nessuna schermata cambia disposizione: cambiano i colori dei ruoli. In gioco il campo e' bianco e le cifre quasi nere (`elite_game_background`, `elite_game_ink`), il resto della pagina e' la carta chiara della Constitution che il report PDF usava gia' (`print_*`). Il lime non regge su bianco (1,3:1): l'accento diventa un oliva scuro, `#4F6B00` (6,1:1 sul fondo, 5,6:1 sul gruppo), sia come riempimento (con testo bianco sopra) sia come testo, icona, pallino del servizio e anello di fuoco.
- **Come e' fatto.** Come l'alto contrasto: un tema AGGIUNTO. Gli attributi dei ruoli (`eliteBackground`... `eliteFocusRing`) restano quelli del tema scuro; `ThemeOverlay.App.Chiaro` ne assegna i valori chiari (`colors_chiaro.xml`) e i pochi colori del framework che la base scura fissa al bianco (testo dei campi, icone di sistema, barre). Tre ruoli nuovi: `eliteAccent` (lime nel scuro, oliva nel chiaro), `eliteGameBackground` e `eliteGameInk`. Tutto cio' che citava `@color/elite_lime`, `ink_black` o `ink_white` per un ruolo passa a `elite_accent`, `elite_game_background`, `elite_game_ink`; il lime resta com'e' dove e' contenuto (colore predefinito del lato 1, icona) e non tema. I dialoghi hanno la loro base chiara (`ThemeOverlay.App.Dialog.Chiaro`).
- **Chi decide.** `Aspetto.kt`: la scelta salvata nelle preferenze (`appearance`); "come il telefono" legge la modalita' notte del sistema. L'overlay lo applica ogni activity prima di gonfiare i layout; al cambio (scelta o notte/giorno) ogni activity aperta si ricrea al ritorno in primo piano. **L'alto contrasto e' solo scuro e vince**: con l'alto contrasto acceso il chiaro non si applica e la riga Aspetto lo dice ("Chiaro · sospeso con alto contrasto").
- **Barre di sistema.** `enableEdgeToEdge()` sceglieva le icone dalla notte del sistema e con l'app chiara su un telefono scuro l'orologio e la batteria erano bianchi sul bianco: `enableEdgeToEdgeConAspetto()` le sceglie dall'aspetto dell'app.
- **Colore di squadra su fondo chiaro.** `TeamInk.graphicOnSurface`: sul chiaro la grafica (barrette, strisce, anello della zona) si scurisce fino a 3:1 invece di schiarirsi; il lime del lato 1 sul bianco diventa un oliva, il ciano un petrolio, come sulla carta del PDF. La zona + resta del colore scelto, col contorno da 2dp quando non si stacca dal campo (il lime sul bianco), glifo in `TeamInk.on`.
- **Contrasti** (li controlla `ChiaroTest`): testo primario `#1A1C1F` e secondario `#5A5F66` almeno 4,5:1 su fondo, gruppo, rialzata e hover; accento 6,1:1 su fondo; bianco sull'accento 6,1:1; contorno dei comandi `#767A83` almeno 3:1; disattivato `#8D9199` non sotto 2,8:1 (solo etichette spente, come in scuro); stati (attenzione `#8A5A00`, errore `#B3261E`, informazione `#1D5F8F`) almeno 4,5:1 sulle superfici e sul proprio fondo tenue; cifre del gioco 7:1.
- **Stati.** Normale, premuto (stessa scala 0,97 e opacita' 0,85), fuoco (anello oliva), selezionato (oliva e un secondo segno: spunta, anello), disattivo, errore, vuoto. Movimento: nessuno di nuovo; il cambio di tema ricrea la schermata come il cambio di lingua.
- **Schermo stretto e 200%.** La riga Aspetto e' una riga nome e valore come Lingua: il nome va a capo, il valore resta a destra.
- **Conflitti dichiarati.** 1) Il lime non e' il colore di marchio sul chiaro: l'accento e' un oliva (come nel PDF, dove il lime non e' mai testo). 2) L'alto contrasto non ha una variante chiara: con lui acceso il chiaro e' sospeso. 3) Il quadrante dell'orologio e il suo protocollo restano scuri. 4) Il lampo del tema all'avvio (finestra di avvio scura prima della prima schermata chiara) non e' risolto: da guardare a schermo.
- **Controllo dei pattern vietati (`review.md`).** Nessun colore scritto a mano nei layout: i valori chiari stanno in `colors_chiaro.xml` con un ruolo ciascuno; nessuna ombra nuova; un solo primario per regione (l'oliva); niente card nuove.

### Corretto prima, dalla prova a schermo del 28 ottobre 2026 (360dp, carattere al 200%)

- Hint "Nome squadra 1/2" troncati: ora "Squadra 1" e "Squadra 2", il nome che compare se il campo resta vuoto.
- "Scambia" andava a capo in mezzo alla parola: bottone a tutta colonna con padding 8dp; da 1,5 di carattere in su l'icona sale sopra la parola.
- Ruota del colore: il dialogo non conteneva ruota, cursore e bottoni; ora e' scorrevole, l'anteprima cresce col carattere e il cursore della luminosita' (alto `wrap_content`, non si vedeva mai) e' alto 48dp.
- "tie-break a 7": lo spazio prima del numero e' unico, il 7 non resta da solo.

### Come si prova

- Test: `ChiaroTest` (contrasti dalle risorse vere attraverso il tema, che l'overlay cambia ogni ruolo, scelta di base scura, "come il telefono" con notte e giorno, alto contrasto che sospende il chiaro), `TeamInkTest` (`graphicOnSurface` su tutto l'RGB).
- A schermo (SM-S948B, 28 ottobre 2026): Impostazioni, menu Partita, gioco (calcio), Storico, Cronaca, Statistiche, Giocatori, Padel Elite, dialogo colore e ruota, foglio Aspetto, in chiaro; il gioco in scuro identico a prima.

### Debiti dichiarati

1. Non visto in chiaro: la serata, l'onboarding e le schermate con il padel in gioco (sul telefono c'era una partita di calcio). Il report PDF non cambia: e' gia' carta chiara. Da guardare.
2. La finestra di avvio e l'icona adattiva restano scure.
3. "Come il telefono": la logica e' provata dai test (notte e giorno), non a schermo; il cambio di notte con l'app aperta lo gestisce il sistema ricreando l'activity, le altre si ricreano al ritorno in primo piano.
4. Nel calcio i nomi di default compaiono come "Team 1" e "Team 2" nel gioco e nelle Formazioni (nei dati salvati, non nel campo del nome): da guardare.
