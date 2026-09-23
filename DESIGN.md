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
- **Verifica:** Foto prima e dopo per ogni schermata, in verticale. PDF di un padel e di un calcio aperti sul telefono: nel padel niente TABELLINO. Una squadra #1A237E leggibile ovunque. MatchReportUtilsBenchmark ancora verde. lint senza nuove voci.

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
2) Messaggio transitorio, 2-3s: "NON CONFERMATO" rosso; "n CONSEGNATI" e "CHIUSURA…" #E0E0E0; "NON CHIUSA" ambra.
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
Dopo la conferma, la riga E dice "CHIUSURA…" finché non arriva un v2 con matchInProgress=false. Senza risposta entro 10s dice "NON CHIUSA" in ambra.
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
