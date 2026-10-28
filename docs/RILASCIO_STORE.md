# Rilascio sugli store: valutazione del 28 ottobre 2026

Stato: **solo valutazione, nessun codice cambiato; tutto in standby** per decisione del proprietario (28 ottobre 2026): si riprende piu' avanti. Apple e' rimandato ancora piu' in la'. La versione completa e leggibile e' una pagina privata del proprietario; qui resta quello che serve a una sessione nuova.

Le regole di Google sono state lette sulle pagine ufficiali il 28 ottobre 2026 (elenco in fondo). Il codice e' stato letto, non compilato: nel cloud non c'e' l'SDK.

## Google Play: cosa e' a posto

- Target API: telefono `targetSdk 36`, orologio `targetSdk 35`. Dal 31 agosto 2026 Google chiede 36 al telefono e 35 a Wear OS.
- Telefono e orologio hanno lo stesso `applicationId` (`it.vantaggi.scoreboardessential`), come serve per un'unica scheda.
- Nessuna libreria di analisi, pubblicita' o segnalazione degli arresti; permessi: rete, vibrazione, notifiche, servizi in primo piano.
- Orologio: `android.hardware.type.watch` dichiarato, `standalone=false`, nessuna libreria nativa (64 bit), nessuna password digitata sull'orologio.

## Google Play: cosa manca

| Punto | Dove |
|---|---|
| Chiave di upload e `keystore.properties` (ignorato da git); Play App Signing | `mobile/build.gradle`, `wear/build.gradle` |
| URL e chiave di Padel Elite nel pacchetto di rilascio: vengono da `local.properties` o dalle variabili d'ambiente; senza, la funzione risulta spenta in silenzio. Il pacchetto di Play si costruisce sul PC del proprietario e si controlla | `mobile/build.gradle` |
| Privacy policy: quella del repository e' del 2 gennaio 2026, solo in inglese, e dice che nessun dato esce dal telefono (falso con Padel Elite). Va riscritta, pubblicata a un indirizzo web e collegata dall'app (Google la richiede anche dentro l'app) | `PRIVACY_POLICY.md` |
| Dichiarazione dei servizi in primo piano: sono due, il cronometro (`specialUse`) e la diretta (`dataSync`); la checklist prepara solo la seconda. Il video della diretta vuole una trasmissione vera: scrive in produzione, serve il si' esplicito del proprietario | `RELEASE_CHECKLIST.md`, `DESIGN.md` |
| Scheda del negozio: parla solo di calcetto e "Paddle", usa emoji e "Football Manager" (marchio altrui) fra le parole chiave, e dice che i dati restano sul dispositivo | `STORE_LISTING.md` |
| Screenshot (telefono e orologio), icona 512, immagine 1024x500, video | vedi sotto |
| Prova della build di rilascio (R8) su telefono e orologio veri | `RELEASE_CHECKLIST.md`, punto 4 |
| Piccole cose: titolo fisso "Select Scorer" nel manifest dell'orologio; `CHANGELOG.md` dice "First public release" del 31 maggio ma non e' mai uscito; icona di G-9 ancora da confermare | `wear/src/main/AndroidManifest.xml`, `CHANGELOG.md` |

La cancellazione dell'account dall'app e' obbligatoria solo se l'app permette di creare l'account o rimanda a una pagina esterna per crearlo. Nell'app non c'e' registrazione. Il modulo Data safety chiede comunque come si chiede la cancellazione dei dati: la risposta sta nella dashboard e non e' stata verificata.

## Regole di Google, con le date

- Quota di 25 dollari una tantum; account personale per hobbisti, di organizzazione con D-U-N-S (gratuito, fino a 30 giorni).
- Account personali creati dopo il 13 novembre 2023: test chiuso con almeno 12 tester iscritti da 14 giorni di fila, poi richiesta di produzione (revisione di solito entro 7 giorni). In pratica 3-4 settimane.
- Verifica degli sviluppatori Android (30 settembre 2026): con Play App Signing le app si registrano in automatico. La chiave persa non si recupera se la gestisci tu.
- Data safety obbligatoria dalle prove chiuse in su. Bozza: email, ID utente, nome (giocatori e gruppi) e contenuti (partite) raccolti **solo** se si usa Padel Elite, facoltativi, non condivisi; niente posizione, contatti, pagamenti; Room e orologio non vanno dichiarati. Cifratura in transito: da ricontrollare che l'indirizzo sia sempre `https`.
- Orologio: il pacchetto e' separato da quello del telefono (guida di packaging); una pagina dell'help parla di un bundle unico con supporto Wear, da confermare al primo caricamento. Almeno uno screenshot 1:1, minimo 384x384, senza cornice. Il test chiuso vale anche per Wear.

## Screenshot e video

- Il telefono del proprietario (S26) e' piu' lungo di 2 a 1: Google rifiuta screenshot con il lato lungo oltre il doppio del corto. Si usa un emulatore da 1080x2160, in italiano, con dati finti e il server finto delle prove strumentate. Mai i dati veri di Padel Elite.
- Orologio: `adb` dal Galaxy Watch (480x480) o da un emulatore rotondo.
- Video per i servizi in primo piano: `adb shell screenrecord` (massimo 3 minuti), YouTube non in elenco.
- Immagine in evidenza e icona: costruite in HTML e salvate come immagine.
- Il telefono e' condiviso con le prove degli altri thread: coordinarsi prima.

## Apple (iPhone e Apple Watch): rimandato

Decisione del proprietario, 28 ottobre 2026: **per ora resta indietro**. Quello che e' stato trovato, perche' la decisione si possa riprendere. Le stime sono ordini di grandezza, non preventivi; i costi e le regole di Apple non sono stati verificati sulle pagine ufficiali.

- Serve un'app separata: il pacchetto Android non gira su iPhone.
- Si riusa `core` (Kotlin puro, circa 3.500 righe: regole di calcio, padel e tennis, punteggio, Serata), portabile con Kotlin Multiplatform.
- Si rifa': interfaccia del telefono (circa 22.000 righe, schermate in XML), Room e WorkManager, sincronizzazione con l'orologio (il Wearable Data Layer non esiste su Apple: serve WatchConnectivity), app dell'orologio (circa 6.300 righe).
- Strade: A, nativa in SwiftUI per iPhone e Apple Watch con `core` condiviso (qualche mese per il telefono, uno o due in piu' per l'orologio); B, Compose Multiplatform per l'interfaccia del telefono (simile ad A, meno lavoro doppio dopo); C, una modalita' tabellone nel sito di Padel Elite, usabile da iPhone nel browser (poche settimane, nessun Mac, niente Apple Watch).
- Costi: programma sviluppatori Apple (99 dollari l'anno, da verificare), un Mac per Xcode e la firma (o un Mac in affitto), un iPhone e un Apple Watch.
- La diretta su iPhone va ripensata: non c'e' un servizio in primo piano per tenere viva la rete (Live Activities o notifiche dal server, con modifiche a Padel Elite). Con "Accedi con Google" Apple di solito chiede anche "Accedi con Apple" (regola 4.8, da ricontrollare).
- Indicazione: pubblicare prima su Android, vedere chi usa l'app, chiedersi quanti del gruppo hanno un iPhone; se pochi, basta la strada C. Preparare `core` per Kotlin Multiplatform solo quando la decisione c'e'.

## Domande aperte (da riprendere)

1. Il proprietario ha gia' un account sviluppatore Google Play? Da quando? (cambia il test chiuso)
2. Video della diretta: prova vera su un gruppo di prova (scrive in produzione, serve il suo si' esplicito) o solo server finto?
3. Nella dashboard esiste un modo per chiedere la cancellazione dell'account e dei dati?
4. Via libera per una PR con privacy policy nuova (italiano e inglese), collegamento nell'app, scheda del negozio riscritta, titolo dell'orologio tradotto e checklist con il cronometro (nessuna scrittura sul database).
5. Nome della scheda, paesi, dove ospitare la privacy policy, i 12 tester.

## Fonti (consultate il 28 ottobre 2026)

- <https://developer.android.com/google/play/requirements/target-sdk>
- <https://support.google.com/googleplay/android-developer/answer/14151465>
- <https://support.google.com/googleplay/android-developer/answer/13392821>
- <https://support.google.com/googleplay/android-developer/answer/10787469>, <https://support.google.com/googleplay/android-developer/answer/13327111>
- <https://developer.android.com/docs/quality-guidelines/wear-app-quality>, <https://developer.android.com/training/wearables/packaging>
- <https://support.google.com/googleplay/android-developer/answer/9866151>
- <https://developer.android.com/developer-verification/guides/faq>
