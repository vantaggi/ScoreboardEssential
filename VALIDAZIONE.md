# Validazione completa - 23 settembre 2026

Fatta su `cc142d7` con un workflow di 21 agenti: dieci aree (dominio, persistenza, protocollo Wear, coda offline, ViewModel, interfaccia del telefono, interfaccia dell'orologio, rete di test, build e sicurezza, concorrenza), ognuna cercata da un agente e poi smontata da un revisore indipendente, piu' una sintesi che ha unito i duplicati.

**Tutto in sola lettura:** nessuna build, nessun test eseguito, nessun emulatore. Ogni difetto viene dalla lettura del codice e porta file e scenario. Quelli che dipendono dal comportamento del Data Layer vanno confermati su dispositivo prima di correggerli (vedi in fondo).

**Esito:** 98 rilievi confermati e 8 respinti dai revisori; dopo la fusione dei duplicati **70 difetti: 16 alti, 25 medi, 29 bassi**, in 12 lotti.

## Giudizio

Il dominio in :core è solido: regole, codec e motore reggono. L'unico difetto vero lì è il servizio dopo un tie-break nel tennis. Il problema sta tutto nei bordi. Con una rosa assegnata, ogni partita di calcio chiusa col telefono cancella i gol segnati. Una partita di padel interrotta prima della fine non si può salvare. Tutto il percorso con l'orologio (intenzioni, arretrato, ack, AZZERA) ha diversi modi dimostrabili di perdere o raddoppiare punti. Non è mai stato provato su un dispositivo: fino a cc142d7 i servizi di ascolto non ricevevano niente. Oggi l'app è affidabile se si usa solo il telefono e senza rose. Con l'orologio non è pronta per chi ci conta davvero.

## Lotti, in ordine

| Lotto | Difetti | Alti | Perche' insieme |
|---|---|---|---|
| L1 Chiusura della partita sul telefono | 4 | 2 | Si correggono tutti dentro endMatch e il DAO: la guardia di partita non iniziata, l'incremento delle presenze con una query mirata e la transazione unica. È il lotto con il miglior rapporto fra costo e danno evitato, e va fatto per primo. |
| L2 Riga viva e ciclo di vita del MainViewModel | 8 | 2 | Tutti riguardano il fatto che la riga isActive e il MainViewModel devono essere uno per partita e uno per processo. Rimedi: creazione serializzata della riga, un ViewModel dedicato alla cronologia, la freccia su che fa finish(), le rose salvate con la riga, la riga viva esclusa dalla cronologia, cancellazione della riga vuota al cambio sport, guardia basata su decode, intenti remoti rimandati fino a fine ripristino. |
| L3 Annullamento e attribuzione legati al registro del motore | 7 | 2 | La causa comune è che actionStack e le righe del registro vivono accanto al registro del motore invece di derivarne. La correzione di fondo è una sola: annullare partendo dall'evento che il motore toglie, attribuire modificando il Point esistente con un esito restituito, registrare sempre la riga del punto remoto, ricostruire pila e righe dopo il batch. Conviene farla in un solo passaggio, con i test. |
| L4 Fine partita e comandi dall'orologio come intenzioni v2 | 4 | 2 | Dipendono tutti dal fatto che l'orologio v2 continua a pilotare il telefono con DataItem v1 assoluti e non urgenti. Si risolvono insieme introducendo un'intenzione 'fine partita' con sequenza, smettendo di scrivere PATH_SCORE e timer quando protocolV2Seen è vero, e separando la coda al reset. |
| L5 Arretrato dell'orologio: base, ack e idempotenza | 11 | 5 | È un'unica riprogettazione del protocollo dell'arretrato, e le correzioni parziali non bastano: un timeout su batchInVolo, da solo, rimanda un batch che verrà di nuovo rifiutato o applicato alla partita sbagliata. Servono insieme: la base e lo sport nel batch, un id stabile e idempotente, sempre un ack o un NACK, il servizio che non consuma la sequenza senza un ViewModel, un canale di invio unico e serializzato, una finestra di sequenze viste, conferma o coda anche per il tocco singolo, e un segnale al polso. |
| L6 Origine e ordine dei DataItem | 3 | 1 | Si correggono con lo stesso filtro su uri.host nei due servizi e in restoreStateFromDataItems, più una versione monotona nello stato v2. Prima di tutto va fatta la verifica su dispositivo dell'eco locale, che decide la gravità del primo rilievo. |
| L7 Quadrante dell'orologio | 4 | 1 | Tutti in wear MainActivity e nel rendering: la guardia del collector del cronometro, applyMatchOver, la contentDescription dinamica e la riga di stato. Si provano insieme sull'emulatore Wear_OS_Small_Round. |
| L8 Cronometro e portiere | 4 | 0 | MatchTimerService e il collector del portiere nel ViewModel. Serve un evento di scadenza dedicato, il rilascio di wake lock e primo piano, la distinzione fra durata e residuo nel messaggio, e la correzione del commento di B3. |
| L9 Dominio racchetta | 2 | 0 | Solo :core, con test JVM puri: si correggono senza dispositivo e senza toccare il resto. |
| L10 Rilascio e lingua | 3 | 1 | Configurazione di build e un solo meccanismo per la lingua (setApplicationLocales), che risolve insieme gli split e le Activity che non la applicano. targetSdk 35 per :wear blocca già ora gli aggiornamenti su Play. |
| L11 Registro a schermo, testi, colori e accessibilità del telefono | 16 | 0 | Interventi locali su addMatchEvent e startNewMatch, risorse, layout, un'utilità comune per il colore leggibile e il PDF. Nessun rischio sui dati, si possono fare in un passaggio unico di rifinitura. Il primo è l'ora del registro, che è sbagliata nel calcio. |
| L12 Rete di test | 4 | 0 | Test da irrobustire o aggiungere. Conviene farli insieme ai lotti L2, L3 e L5, perché i nuovi test di quei lotti chiudono anche i buchi di copertura segnalati qui (un DAO finto che si sospende mostrerebbe il doppio insert). |

## L1 Chiusura della partita sul telefono

### [alta] Fine partita: l'@Update di riga intera fatto dalla copia della rosa riporta indietro i gol segnati nella partita (B5 chiuso a metà)

`mobile/src/main/java/it/vantaggi/scoreboardessential/MainViewModel.kt` - aree: persistenza, viewmodel, concorrenza

**Scenario.** Calcio. Mario ha goals=5 ed è nella squadra 1. Gli viene attribuito un gol dal registro e incrementGoals porta il DB a 6. Con END MATCH, endMatch (1438-1440) fa it.player.apply{appearances++} sulla copia presa da _allPlayers quando è stato aggiunto alla rosa, poi chiama playerDao.updatePlayers, un @Update di riga intera (PlayerDao 20-21). Il DB torna a goals=5. Le rose non vengono svuotate, quindi ogni partita successiva riscrive di nuovo il valore vecchio. Allo stesso modo si perdono nome e padelPlayerId modificati a partita in corso. È il difetto che il commento di PlayerDao 26-31 descrive, ancora vivo in questo punto.

**Rimedio.** Usare la query UPDATE players SET appearances = appearances + 1 WHERE playerId IN (:ids) invece di updatePlayers, senza modificare le istanze in memoria. Mettere questa query, finalizeMatch e le cross-ref nella stessa transazione (vedi il rilievo sulle tre scritture).

### [alta] Padel e tennis non si possono salvare finché nessuno ha vinto un set: nel padel a set unico, nessuna partita interrotta si salva

`mobile/src/main/java/it/vantaggi/scoreboardessential/MainViewModel.kt` - aree: persistenza, viewmodel, dominio, ui-wear

**Scenario.** Rialzo la gravità da media ad alta. Nel padel del registro (sets=1) setsWon resta 0-0 finché il punto finale non chiude la partita, quindi è impossibile salvare qualunque partita non finita (campo a tempo, pioggia). endMatch (1399) usa team1Score e team2Score, cioè headline() = set vinti, e matchTimerValue, che con ClockMode.NONE resta 0. Padel sul 5-3: TERMINA risponde match_not_started_error e resta solo SCARTA. La riga resta isActive e al riavvio viene ripristinata. Un END MATCH dall'orologio viene ignorato senza avviso.

**Rimedio.** Usare engine.log.isEmpty() && matchTimerValue == 0L, lo stesso criterio di selectSport e discardMatch. Aggiungere a MainViewModelTest un caso padel (oggi il test 445 prova solo il calcio).

### [bassa] endMatch fa tre scritture senza transazione

`mobile/src/main/java/it/vantaggi/scoreboardessential/MainViewModel.kt` - aree: persistenza

**Scenario.** finalizeMatch, poi updatePlayers, poi le cross-ref, in chiamate separate. Se viewModelScope viene cancellato o il processo muore dopo la prima, la partita è nello storico senza giocatori e getPlayerWinCounts non la conta.

**Rimedio.** Un metodo @Transaction nel DAO, o db.withTransaction, insieme alla correzione di updatePlayers.

### [bassa] insertPlayerWithRoles è documentata come transazionale ma non lo è

`mobile/src/main/java/it/vantaggi/scoreboardessential/repository/PlayerRepository.kt` - aree: persistenza

**Scenario.** insert e addRolesToPlayer sono due chiamate separate (32). Un'interruzione fra le due lascia un giocatore senza ruoli, escluso da getTopScorersByRoleCategories.

**Rimedio.** Un metodo @Transaction in PlayerDao, come updatePlayerWithRoles.

## L2 Riga viva e ciclo di vita del MainViewModel

### [alta] L'arretrato dall'orologio crea due righe di partita attiva

`mobile/src/main/java/it/vantaggi/scoreboardessential/MainViewModel.kt` - aree: persistenza, viewmodel, concorrenza

**Scenario.** applyWatchBatch chiama publishEngineState, che esegue già persistLiveMatch (897), e poi chiama di nuovo persistLiveMatch (1105). Il batch si applica solo a registro vuoto, quindi currentMatchId è null: entrambe le coroutine leggono null prima che l'insert suspend ritorni. Il doppio insert avviene ogni volta, non per una corsa rara. Nascono due righe con isActive=1 e endMatch ne chiude una sola. L'altra compare doppia in cronologia, viene ripresa al riavvio come 'Partita ripresa' e blocca per sempre il cambio sport dalle impostazioni (MatchSettingsViewModel 109).

**Rimedio.** Togliere la persistLiveMatch della riga 1105 e serializzare la creazione della riga (Mutex o Deferred condiviso), perché la stessa finestra si apre con due punti ravvicinati o con END subito dopo il primo punto.

### [alta] La cronologia (e l'onboarding) crea un secondo MainViewModel completo, e gli eventi dell'orologio vengono applicati due volte

`mobile/src/main/java/it/vantaggi/scoreboardessential/MatchHistoryActivity.kt` - aree: viewmodel, persistenza

**Scenario.** MatchHistoryActivity:41 usa ViewModelProvider(this, factory)[MainViewModel]. Il suo init manda 0-0 e un v2 vuoto all'orologio, esegue il ripristino e registra un secondo receiver, mentre il ViewModel di MainActivity è vivo nel back stack. Con la cronologia aperta, un marcatore scelto sull'orologio viene applicato da entrambi e Mario prende 2 gol per 1. Un arretrato viene applicato due volte, con due doppi insert. END MATCH dall'orologio viene eseguito due volte. Un punto remoto a riga viva assente crea due righe. Nell'onboarding il ViewModel vive pochissimo: lì l'effetto è solo lo 0-0 mandato all'orologio.

**Rimedio.** Dare alla cronologia un ViewModel leggero su MatchRepository (allMatches, deleteMatch). L'onboarding scrive la preferenza direttamente. Un solo MainViewModel per processo.

### [media] La cronologia mostra la partita in corso, e cancellarla da lì fa smettere di salvarla mentre END MATCH risponde 'salvata'

`mobile/src/main/java/it/vantaggi/scoreboardessential/database/MatchDao.kt` - aree: persistenza, viewmodel

**Scenario.** getAllMatchesWithTeams non filtra isActive (151-153). Si cancella dalla cronologia la riga viva, un '2-1' mai terminato. currentMatchId resta valorizzato: updateLiveMatch e finalizeMatch aggiornano 0 righe senza errore, END MATCH dice 'Match saved', le presenze aumentano comunque e le cross-ref puntano a un matchId inesistente (non ci sono FK).

**Rimedio.** Escludere isActive=1 dalla cronologia, o mostrarla come 'in corso' senza possibilità di cancellarla. In updateLiveMatch/finalizeMatch controllare le righe toccate e reinserire la riga se sono zero.

### [media] Una riga attiva senza eventi resta orfana dopo un cambio sport dall'orologio, e poi riceve una partita di un altro sport

`mobile/src/main/java/it/vantaggi/scoreboardessential/MainViewModel.kt` - aree: persistenza

**Scenario.** Calcio: un gol e poi ANNULLA, la riga resta attiva con '1|'. Dall'orologio si passa al padel: applySport (500) mette currentMatchId=null senza deleteById. Al riavvio restoreActiveMatchIfAny non confronta sportId: compare 'Partita ripresa', e la partita di padel può finire scritta sulla riga 'football' (updateLiveMatch e finalizeMatch non aggiornano sportId). Altrimenti la riga resta orfana e appare in cronologia come 0-0.

**Rimedio.** In applySport cancellare la riga viva quando il registro è vuoto. Al ripristino, se sportId è diverso, applicare prima lo sport della riga.

### [media] La guardia sul cambio sport nelle impostazioni tratta il registro vuoto '1|' come una partita iniziata

`mobile/src/main/java/it/vantaggi/scoreboardessential/ui/MatchSettingsViewModel.kt` - aree: test, persistenza

**Scenario.** MatchLogCodec.encode(emptyList()) produce '1|'. Dopo un gol annullato la riga viva contiene '1|', eventLog.isNotEmpty() (109) è vero e il cambio sport viene rifiutato, mentre selectSport lo accetterebbe (dall'orologio passa). Il commento 104-105 ('la STESSA condizione') è falso. L'unico test usa '1|1'.

**Rimedio.** MatchLogCodec.decode(eventLog)?.isNotEmpty() ?: true. Test con '1|' (cambio accettato) e con '' (partita legacy).

### [media] La freccia 'su' di Cronologia e Impostazioni ricrea MainActivity e distrugge il ViewModel della partita

`mobile/src/main/java/it/vantaggi/scoreboardessential/ui/MatchSettingsActivity.kt` - aree: viewmodel

**Scenario.** Rilievo dei revisori, verificato leggendo. MatchHistoryActivity:24 e MatchSettingsActivity:44 attivano setDisplayHomeAsUpEnabled senza gestire android.R.id.home né onSupportNavigateUp, mentre Statistics (49) e PlayersManagement (296) fanno finish(). Resta la navigazione 'su' predefinita verso parentActivityName=.MainActivity, che ha launchMode standard (manifest 16). Il framework chiude e ricrea il genitore: questo va confermato su dispositivo. Effetti: onCleared con unbind del service, rose perse, righe INFO del registro perse, 0-0 mandato all'orologio prima del ripristino. Esempio: padel con 4 giocatori, HISTORY e poi la freccia: rose vuote ed export NEEDS_FOUR.

**Rimedio.** finish() su android.R.id.home in entrambe le Activity, come nelle altre due. In alternativa launchMode singleTop su MainActivity.

### [media] Le rose non sopravvivono alla ricreazione del ViewModel: si perdono presenze, export Padel Elite e ordine di servizio

`mobile/src/main/java/it/vantaggi/scoreboardessential/MainViewModel.kt` - aree: viewmodel

**Scenario.** _team1Players e _team2Players vivono solo in memoria (148), e il ripristino (945-968) non li riporta. refreshServeOrder esce se il registro non è vuoto (473), quindi rimettere i giocatori non ricostruisce serveOrder. Dopo la morte del processo o la freccia 'su', END MATCH salva senza presenze né cross-ref, e l'export dice NEEDS_FOUR o perde chi serviva.

**Rimedio.** Salvare gli id delle rose con la riga viva, ricaricarli al ripristino e ricostruire sportRules con SportRegistry.forMatch prima di restoreLog.

### [bassa] Il ripristino asincrono può sovrascrivere un punto o un arretrato arrivati prima che finisca

`mobile/src/main/java/it/vantaggi/scoreboardessential/MainViewModel.kt` - aree: persistenza, concorrenza

**Scenario.** Il receiver si registra (631-633) mentre restoreActiveMatchIfAny è sospeso su getActiveMatchOnce. Un evento arrivato in quella finestra viene applicato al motore vuoto: crea una riga e, per l'arretrato, manda l'ack. Poi restoreLog lo cancella e lascia una riga attiva orfana. La finestra è di pochi millisecondi e va verificata su dispositivo.

**Rimedio.** Rimandare gli intenti remoti fino alla fine del ripristino (un CompletableDeferred atteso da applyWatchBatch e addRemotePoint).

## L3 Annullamento e attribuzione legati al registro del motore

### [alta] Nel calcio, ANNULLA dopo una correzione '-' toglie l'evento sbagliato: il tabellone risale e il registro e i gol perdono un gol vero

`mobile/src/main/java/it/vantaggi/scoreboardessential/MainViewModel.kt` - aree: persistenza, viewmodel

**Scenario.** Gravità alta come ha deciso il revisore viewmodel, perché il punteggio a schermo diventa sbagliato. Nel calcio decrementIsUndo=false, quindi '-' e INTENT_CORRECTION passano da subtractScore, che mette una Correction nel motore ma niente in actionStack. Situazione: gol di Marco, gol, '-' (1-0). ANNULLA: la pila perde un GoalAction (Marco -1, la riga del gol sparisce) ed engine.undo toglie la Correction, quindi il tabellone torna a 2-0. Dopo un ripristino rebuildEventsAndUndo ricrea lo stesso disallineamento.

**Rimedio.** L'annullamento parte dall'ultimo evento del motore: se è un Point con playerId, decrementa quel giocatore e toglie la riga con quell'engineIndex; se è una Correction, toglie solo la riga della correzione. In alternativa mettere in pila anche le correzioni.

### [alta] Partita consegnata dall'orologio: nessuna riga nel registro, e l'annullamento (anche dal '−' dell'orologio) non fa niente

`mobile/src/main/java/it/vantaggi/scoreboardessential/MainViewModel.kt` - aree: viewmodel, test

**Scenario.** applyWatchBatch (1078-1115) non chiama rebuildEventsAndUndo, quindi actionStack resta vuota e canUndo false. È lo stesso difetto chiuso per il ripristino (piano 1297-1300), rimasto aperto su questo percorso. Padel segnato dal solo orologio e consegnato: il punteggio è giusto, ma il registro è vuoto. Il '−' dell'orologio manda INTENT_UNDO, undoLastGoal trova la pila vuota ed esce, l'orologio vibra la conferma e il punto resta. Nessun test copre applyWatchBatch.

**Rimedio.** Dopo il batch chiamare rebuildEventsAndUndo() in una coroutine, come fa restoreActiveMatchIfAny. Aggiungere test: batch su partita vuota (righe, canUndo, undo) e batch su partita non vuota (rifiuto).

### [media] Un marcatore attribuito dal registro non viene tolto quando si annulla il gol

`mobile/src/main/java/it/vantaggi/scoreboardessential/MainViewModel.kt` - aree: persistenza, viewmodel

**Scenario.** addScore mette in pila GoalAction(team, null), e attributeScorer (1260-1283) non la aggiorna. undoLastGoal decrementa solo lastAction.playerId (1303). Esempio: '+', si tocca la riga, si sceglie Marco (3→4), ANNULLA: il punteggio torna 0-0 ma Marco resta a 4. Dopo un riavvio invece il decremento avviene, perché la pila si ricostruisce da evento.playerId: il comportamento dipende dal riavvio. Media: il punteggio è giusto, la statistica no.

**Rimedio.** Ricavare il marcatore dall'evento del motore che si toglie ((engine.log.last().event as? Point)?.playerId). È lo stesso rimedio del rilievo sull'annullamento dopo una correzione.

### [media] Marcatore dall'orologio: telefono e orologio decidono con rose diverse se arriverà, e nascono righe e annullamenti doppi o mancanti

`mobile/src/main/java/it/vantaggi/scoreboardessential/MainViewModel.kt` - aree: protocollo, viewmodel, concorrenza, persistenza

**Scenario.** Il telefono salta riga e GoalAction se la rosa della SQUADRA non è vuota (1143-1147). L'orologio apre la scelta se non è vuota la lista di TUTTI i giocatori (WearViewModel 345). (a) Squadre vuote, archivio pieno: scegliendo Mario, attributeRemoteScorer passa da addScorer e aggiunge una seconda riga e una seconda GoalAction, quindi due annullamenti tolgono due punti. Succede anche con un nome sconosciuto (1246-1249). (b) Rosa piena, si sceglie NESSUNO o si esce con lo swipe (PlayerSelectionActivity 85-88, con un commento falso): il punto non ha né riga né voce in pila, e l'undo successivo toglie la GoalAction sbagliata.

**Rimedio.** Il telefono registra sempre riga e GoalAction per il punto remoto. MSG_SCORER_SELECTED aggiorna la riga e il Point esistenti come fa attributeScorer, invece di chiamare addScorer.

### [media] attributeScorer incrementa i gol anche quando l'indice non punta più al punto scelto

`mobile/src/main/java/it/vantaggi/scoreboardessential/MainViewModel.kt` - aree: viewmodel

**Scenario.** engine.attribute non fa nulla, e non lo segnala, se l'indice è fuori intervallo o non indica un Point, ma incrementGoals (1267) parte comunque. Il dialogo è aperto sull'indice 3 e un INTENT_UNDO dall'orologio toglie quel punto: scegliendo Mario, Mario prende +1 gol per un punto che non c'è. Se nel frattempo all'indice 3 è arrivato un altro punto, anche dell'altra squadra, il marcatore finisce su quello.

**Rimedio.** engine.attribute restituisce se ha attribuito un Point ancora senza marcatore, e si incrementa solo in quel caso. In mostraDialogo non aprire il dialogo se il tag è già presente.

### [media] Il marcatore scelto sull'orologio va all'ultimo punto del motore nel momento in cui arriva, che può essere un altro

`mobile/src/main/java/it/vantaggi/scoreboardessential/MainViewModel.kt` - aree: concorrenza

**Scenario.** attributeRemoteScorer usa engine.log.lastIndex all'arrivo della scelta (1245), ed engine.attribute non controlla il lato. Esempio: l'orologio segna per la squadra 1 (indice 4) e, mentre al polso si sceglie X, sul telefono si segna per la squadra 2 (indice 5). X riceve il gol della squadra 2, e il punto 4 resta senza riga e senza annullamento. Servono due operatori.

**Rimedio.** Far viaggiare con la scelta la sequenza o l'indice del punto, oppure cercare all'indietro l'ultimo Point del lato team ancora senza playerId.

### [media] Senza telefono l'attribuzione del marcatore scelta al polso si perde in silenzio

`wear/src/main/java/it/vantaggi/scoreboardessential/wear/PlayerSelectionActivity.kt` - aree: ui-wear

**Scenario.** incrementScore apre la scelta senza aspettare l'esito dell'invio. sendMessageToMobile (99-114) usa connectedNodes e con zero nodi non fa niente: niente coda, niente vibrazione d'errore. Calcio col telefono in borsa: si sceglie 'Rossi', la schermata si chiude come se fosse andato tutto bene, e il gol arriva poi senza marcatore.

**Rimedio.** Senza telefono non aprire la scelta, oppure accodare l'attribuzione insieme all'intenzione. Se nessun nodo riceve, vibrare l'errore.

## L4 Fine partita e comandi dall'orologio come intenzioni v2

### [alta] AZZERA/Finisci sull'orologio nel calcio: lo 0-0 v1 urgente svuota il motore del telefono prima di endMatch, e la partita va persa o viene salvata 0-0

`wear/src/main/java/it/vantaggi/scoreboardessential/wear/WearViewModel.kt` - aree: persistenza, protocollo, ui-wear, concorrenza

**Scenario.** Calcio sul 3-2, cronometro a 40:00. Sull'orologio si preme AZZERA e poi Finisci. resetMatch(false) (692-714) chiama updateScore(0,0), che manda PATH_SCORE con urgent=true, anche su un orologio v2. Poi manda TIMER_STATE, KEEPER_TIMER e MATCH_STATE=false, non urgenti. Sul telefono ACTION_SCORE_UPDATE esegue seedEngineFromAbsolute(0,0) (MainViewModel 244-255), che svuota il registro senza persistere. Poi MATCH_STATE chiama endMatch. Se il reset del cronometro è già arrivato, la guardia 0-0/0 (1399) esce: la riga resta attiva col vecchio contenuto e il primo punto della partita successiva la sovrascrive. Altrimenti finalizeMatch salva 0-0 con registro vuoto. Il dialogo dell'orologio promette di finire la partita. Padel e tennis non sono colpiti, perché ignorano il v1.

**Rimedio.** Con protocolV2Seen, resetMatch non deve scrivere PATH_SCORE né il timer. La fine partita diventa un'intenzione con sequenza, e il telefono esegue endMatch sul proprio stato. Il telefono ignora ACTION_SCORE_UPDATE quando engine.log non è vuoto o il nodo parla v2.

### [alta] NUOVA PARTITA/AZZERA dal polso non separa la coda e non cambia il quadrante v2: due partite offline si fondono

`wear/src/main/java/it/vantaggi/scoreboardessential/wear/WearViewModel.kt` - aree: offline, ui-wear

**Scenario.** resetMatch (692) azzera solo i contatori v1. Non tocca pending, statoDalTelefono, _scoreState, ultimaNota né LastKnownMatch. Offline si giocano 20 tocchi, si conferma AZZERA/Finisci e se ne giocano altri 15. Il quadrante resta sul punteggio vecchio e i nuovi tocchi lo continuano. Al ritorno il telefono riceve 35 tocchi come un'unica partita: se arriva prima MATCH_STATE, dopo startNewMatch; se arriva prima il batch, direttamente.

**Rimedio.** Con la coda non vuota il reset mette in coda un marcatore di fine partita (oppure si blocca finché la coda non viene consegnata), e riporta subito a zero lo stato locale.

### [bassa] Prima del primo stato v2 un tocco parte sia come intenzione sia come punteggio assoluto v1, e nel calcio può contare doppio

`wear/src/main/java/it/vantaggi/scoreboardessential/wear/WearViewModel.kt` - aree: protocollo, offline

**Scenario.** incrementScore (339-351) con protocolV2Seen falso manda l'intenzione (o la mette in coda) e anche PATH_SCORE. Nel calcio seedEngineFromAbsolute porta a N+1, poi l'intenzione a N+2, oppure appiattisce il registro. Offline il v1 può far rifiutare l'arretrato o sostituirlo. La finestra è quella dell'avvio a freddo, prima della rilettura dei DataItem; decrementScore non è colpito.

**Rimedio.** Impostare protocolV2Seen quando statoDaDisco restituisce uno stato, e usare un solo canale finché non si è visto un v2.

### [bassa] Comandi (cronometro, portiere, stato partita) viaggiano come DataItem non urgenti

`wear/src/main/java/it/vantaggi/scoreboardessential/wear/WearViewModel.kt` - aree: protocollo

**Scenario.** Solo PATH_SCORE e PATH_STATE_V2 sono urgenti (742). START sull'orologio può arrivare in ritardo. È anche ciò che rende probabile l'ordine sbagliato in AZZERA.

**Rimedio.** urgent=true sui path di comando, oppure trasformarli in messaggi.

## L5 Arretrato dell'orologio: base, ack e idempotenza

### [alta] Il telefono rifiuta sempre l'arretrato di una partita cominciata col telefono, cioè il caso per cui il calcolo offline è stato costruito

`mobile/src/main/java/it/vantaggi/scoreboardessential/MainViewModel.kt` - aree: offline, protocollo

**Scenario.** Padel cominciato sul telefono, 40-0. Il Bluetooth cade e il tocco finisce in coda. Il polso, con rebuildLocalState (WearViewModel 415-431), mostra il game vinto sopra il registro del telefono. Al ritorno applyWatchBatch (1083-1086) vede il registro non vuoto e rifiuta senza ack. Il punto non entra mai nella partita. Seguendo lo Snackbar ('End it, and the one from the watch will arrive') la partita vera viene salvata senza quel punto, e ne nasce una di un punto solo. Le decisioni del piano 'Punteggio offline al polso' (818-826) e 'Niente fusioni' (744-749) si contraddicono: la seconda è chiusa male, perché la coda è stata calcolata e mostrata proprio sopra quel registro.

**Rimedio.** Il batch porta la base su cui è stato calcolato (lunghezza o hash del registro visto). Il telefono lo accoda se il suo registro coincide con quella base. Se la base non coincide rifiuta e lo dice al polso con un NACK.

### [alta] Un arretrato consegnato e mai confermato blocca l'orologio: nessun nuovo tentativo e quadrante fermo sul calcolo locale

`wear/src/main/java/it/vantaggi/scoreboardessential/wear/WearViewModel.kt` - aree: protocollo, offline, ui-wear, concorrenza, test

**Scenario.** batchInVolo torna a null solo se sendMessage fallisce (492) o con l'ack giusto (498-502). L'ack non arriva in quattro casi: il telefono rifiuta (1083-1086); l'app del telefono è chiusa, e il servizio consuma comunque la sequenza (SimplifiedDataLayerListenerService 253-260) con un broadcast che nessuno riceve; l'ack non viene consegnato; il batch non ha voci applicabili (1101: 'if (applicati == 0) return' senza risposta). Da quel momento flushPending esce subito (475) e applyStateV2 non ridisegna più (285). I punti segnati dopo arrivano al telefono, ma il polso resta fermo finché il ViewModel dell'orologio non viene distrutto. Il piano (746-747) promette che 'riproverà al collegamento successivo': è chiuso male.

**Rimedio.** Il telefono risponde sempre, con un ack o con un NACK. Il servizio non consuma la sequenza se nessun ViewModel ha applicato il batch. Sull'orologio batchInVolo scade dopo un timeout e al passaggio a Connected, e applyStateV2 non congela lo schermo oltre la scadenza. Servono test su flushPending e onBatchAck.

### [alta] Ack perso: la coda non si svuota e il polso conta due volte l'arretrato già applicato

`wear/src/main/java/it/vantaggi/scoreboardessential/wear/WearViewModel.kt` - aree: protocollo, offline

**Scenario.** Cinque punti consegnati: il telefono li applica (5-0) e pubblica il v2, ma l'ack si perde. Il telefono ignora l'esito di sendMessage (1109-1114). L'ack arriva all'orologio solo via LocalBroadcast verso MainActivity (WearDataLayerService 209), quindi si perde anche se l'Activity è stata chiusa con uno swipe. applyStateV2 ha salvato in LastKnownMatch lo stato che contiene già l'arretrato (283). Alla riapertura refreshPendingCount e applyStateV2 (286, 459-460) riapplicano la coda: il polso mostra 10-0. flushPending viene rifiutato e si ricade nel blocco del rilievo precedente.

**Rimedio.** Un id di batch stabile, salvato con la coda. Il telefono persiste l'ultimo id applicato per nodo e riconferma i duplicati invece di rifiutarli. L'id dell'ultimo batch applicato viaggia anche nello stato v2. Il servizio dell'orologio toglie dalla coda su disco anche senza Activity.

### [alta] Una coda rifiutata o non confermata non si scarta mai e viene applicata alla partita successiva

`wear/src/main/java/it/vantaggi/scoreboardessential/wear/WearViewModel.kt` - aree: protocollo, offline

**Scenario.** Rilievo dei revisori, classificato qui. La coda si svuota solo in onBatchAck (501): non la toccano né resetMatch (692-714) né la fine partita sul telefono. Arretrato rifiutato (o applicato senza ack), poi l'utente chiude la partita sul telefono, come chiede lo Snackbar. Alla partita successiva il nuovo ViewModel dell'orologio ha batchInVolo null, flushPending rispedisce e applyWatchBatch, col registro vuoto, applica i tocchi vecchi alla partita nuova: 3-0 al fischio d'inizio. Nel caso dell'ack perso, gli stessi punti finiscono salvati in due partite. MatchClock.relative ancora l'inizio al primo atMillis visto (MatchClock 31-33), quindi i tempi della partita nuova risultano spostati di ore.

**Rimedio.** Lo stesso id di batch idempotente del rilievo precedente, più la base del registro nel batch: se la partita del telefono non è quella su cui la coda è stata calcolata, il batch si rifiuta esplicitamente e il polso chiede all'utente se scartarlo. Non si deve mai applicare a una partita nuova in silenzio.

### [alta] Un tocco singolo consegnato quando il ViewModel del telefono non esiste va perso, dopo la vibrazione di conferma

`mobile/src/main/java/it/vantaggi/scoreboardessential/SimplifiedDataLayerListenerService.kt` - aree: protocollo

**Scenario.** App del telefono chiusa (indietro, swipe o processo ucciso). La capability è statica, quindi sendMessage restituisce true: il polso vibra la conferma e non mette niente in coda (WearViewModel 557-560). Il servizio registra la sequenza (215) e fa un LocalBroadcast che solo il MainViewModel riceve (619-633). Il punto è perso: alla riapertura il ripristino dal DB non lo contiene. Lo stesso vale per il cambio sport.

**Rimedio.** Il tocco si considera preso solo quando arriva uno stato v2 con un registro più lungo, altrimenti va in coda dopo un timeout. In alternativa il servizio persiste l'intenzione quando nessun ricevitore è registrato.

### [media] Due intenzioni ravvicinate partono in parallelo: se arrivano invertite, la soglia sulla sequenza scarta la prima, già confermata al polso

`mobile/src/main/java/it/vantaggi/scoreboardessential/SimplifiedDataLayerListenerService.kt` - aree: protocollo, concorrenza

**Scenario.** I due revisori hanno dato gravità diverse (alta e media). Scelgo media: il danno è un punto perso, ma servono due tocchi a poche decine di millisecondi l'uno dall'altro, e la frequenza reale va vista su dispositivo. La sequenza si prende in modo sincrono (WearViewModel 545), ma l'invio passa per tre await su Dispatchers.IO (OptimizedWearDataSync 188-206) senza serializzazione. Il telefono tiene solo il massimo per nodo (208-215). Esempio: doppio tocco nel padel, arriva prima s+1, s viene scartato e il polso ha vibrato due conferme. Lo stesso vale fra l'arretrato (s) e un tocco dal vivo (s+1): l'arretrato viene scartato senza ack e l'orologio si blocca.

**Rimedio.** Sull'orologio serializzare tutti gli invii, arretrato compreso, con un Channel a consumatore unico o un Mutex che copra tutta sendMessage. Sul telefono deduplicare con una finestra di sequenze viste invece che con il massimo.

### [media] Un tocco dal vivo al ricollegamento arriva prima dell'arretrato e lo fa rifiutare o scartare, anche perché il flush può non partire

`wear/src/main/java/it/vantaggi/scoreboardessential/wear/WearViewModel.kt` - aree: offline

**Scenario.** sendScoreIntent (557) prova sempre l'invio dal vivo e non guarda né pending né batchInVolo. flushPending parte solo quando connectionState emette Connected, ma il listener della capability non vede il Bluetooth che cade (WearViewModel 189, OptimizedWearDataSync 100-101). Se lo stato è rimasto Connected, al ritorno lo StateFlow non emette e il flush aspetta il prossimo onStart. Intanto i tocchi dal vivo riempiono il registro del telefono. Scenario: 4 punti in coda, il telefono torna, l'utente tocca. Il telefono applica solo il nuovo 1-0, poi rifiuta l'arretrato.

**Rimedio.** Con la coda non vuota o un batch in volo, accodare anche i tocchi nuovi. Chiamare flushPending anche dopo un invio dal vivo riuscito con la coda non vuota.

### [media] L'arretrato non dice di che sport è: il telefono lo applica con lo sport che ha in quel momento

`mobile/src/main/java/it/vantaggi/scoreboardessential/MainViewModel.kt` - aree: offline

**Scenario.** Il payload contiene solo KEY_INTENT_BATCH e KEY_SEQ. Il polso registra un padel offline, e intanto sul telefono si gioca e si chiude un calcio. Al ritorno i punti del padel diventano gol, e il polso ricalcola la coda con le regole del calcio.

**Rimedio.** Mettere KEY_SPORT_ID nel batch. A registro vuoto il telefono passa a quello sport, altrimenti rifiuta con un NACK.

### [bassa] A telefono raggiungibile il polso non dice più che ci sono punti non consegnati

`wear/src/main/java/it/vantaggi/scoreboardessential/wear/MainActivity.kt` - aree: offline

**Scenario.** daConsegnare si usa solo con !telefonoRaggiungibile (376-388). Con un batch rifiutato o bloccato, il pallino è verde e la riga mostra il gesto normale.

**Rimedio.** Mostrare il conteggio con pendingCount>0 anche da collegato, e un messaggio al polso quando il telefono rifiuta.

### [bassa] Se l'ack arriva prima dello stato, il polso torna indietro per un attimo

`wear/src/main/java/it/vantaggi/scoreboardessential/wear/WearViewModel.kt` - aree: offline

**Scenario.** onBatchAck (505-506) mostra statoDalTelefono precedente all'arretrato: 0-0 finché non arriva il DataItem. È transitorio.

**Rimedio.** Nello stato v2 mettere la sequenza dell'ultimo batch applicato, e non cambiare lo schermo prima di riceverla.

### [bassa] Due tocchi offline ravvicinati possono entrare in coda in ordine invertito (da verificare su dispositivo)

`wear/src/main/java/it/vantaggi/scoreboardessential/wear/WearViewModel.kt` - aree: offline

**Scenario.** pending.add (565) avviene dopo un sendMessage sospeso, e niente serializza i tocchi. Nel padel sul 40-40 con golden point, l'ordine decide il game.

**Rimedio.** Offline accodare in modo sincrono prima di tentare l'invio, oppure lo stesso canale unico del rilievo sulla sequenza.

## L6 Origine e ordine dei DataItem

### [alta] Se il Data Layer consegna al servizio del telefono i DataItem scritti dal telefono stesso, ogni punto nel calcio riscrive il registro del motore (premessa da verificare)

`mobile/src/main/java/it/vantaggi/scoreboardessential/SimplifiedDataLayerListenerService.kt` - aree: protocollo, concorrenza, persistenza, viewmodel

**Scenario.** PREMESSA NON VERIFICATA: che WearableListenerService riceva anche le modifiche del nodo locale. Il filtro del manifest è host='*' e handleDataEvent (56-66) non guarda uri.host. Il telefono scrive PATH_SCORE, TIMER_STATE, KEEPER_TIMER e MATCH_STATE, cioè gli stessi path che ascolta. Se l'eco arriva, nel calcio ogni updateScore torna come seedEngineFromAbsolute: il registro diventa [P1…,P2…], senza playerId né tempi, e il persist successivo lo salva così. Un undo sull'1-1 toglie il punto della squadra 2 invece dell'ultimo gol. Il percorso è attivo solo da cc142d7. Se la premessa è falsa, il rilievo cade.

**Rimedio.** Scartare in entrambi i servizi gli eventi con uri.host uguale al nodo locale. Prima, verificare sul telefono: segnare un punto e cercare 'Broadcasted score update' nel logcat del telefono.

### [bassa] Al risveglio l'orologio rigioca anche i DataItem che ha scritto lui, in ordine arbitrario

`wear/src/main/java/it/vantaggi/scoreboardessential/wear/MainActivity.kt` - aree: protocollo

**Scenario.** restoreStateFromDataItems (170-185) non filtra per nodo. Un match_state=false lasciato dall'orologio può azzerare il cronometro mostrato (12:00 → 00:00) finché il telefono non manda il timer.

**Rimedio.** Tenere solo gli elementi il cui uri.host non è il nodo locale.

### [bassa] Stati v2 inviati in coroutine parallele con nuovi tentativi: il DataItem finale può essere il più vecchio

`shared/src/main/java/it/vantaggi/scoreboardessential/shared/communication/OptimizedWearDataSync.kt` - aree: protocollo

**Scenario.** Il put di A (1-0) fallisce, B (2-0) riesce, poi A viene ritentato e sovrascrive. Al polso resta 1-0 fino all'evento successivo. Servono un put fallito e un secondo stato entro 200 ms.

**Rimedio.** Una versione monotona nel v2, oppure un canale conflated che a ogni tentativo rilegge l'ultimo stato.

## L7 Quadrante dell'orologio

### [alta] Nel calcio col protocollo v2 il cronometro dell'orologio resta fermo sul valore del primo stato v2

`wear/src/main/java/it/vantaggi/scoreboardessential/wear/MainActivity.kt` - aree: ui-wear

**Scenario.** binding.matchTimer si scrive solo nel ramo senza cronometro di applyClockRole (414) e nel collector di matchTimer (476), che però scrive solo se scoreState == null. Dopo il primo v2 scoreState non torna mai null. Calcio con il telefono v2: il cronometro avanza sul telefono e in _matchTimer, ma il quadrante resta '00:00' per tutta la partita. Passando da padel a calcio resta scritto 'Set 1'.

**Rimedio.** Alla riga 476 usare la condizione scoreState == null || scoreState.hasClock. Nel ramo hasClock di applyClockRole scrivere subito matchTimer.value.

### [media] A partita finita i lati dell'orologio restano toccabili, e il telefono registra una riga e un annullamento fantasma per il punto inerte

`wear/src/main/java/it/vantaggi/scoreboardessential/wear/MainActivity.kt` - aree: ui-wear, protocollo

**Scenario.** Unisco due rilievi. applyMatchOver (368) mette solo isClickable=false, ma la vista resta LONG_CLICKABLE e performClick chiama comunque l'OnClickListener (il comportamento del framework va confermato). incrementScore non guarda matchOver. Padel finito: il tocco vibra la conferma, oppure va in coda come '1 IN ATTESA'. Sul telefono addRemotePoint (1131-1147), a differenza di addScore (1053), non ha la guardia engine.state == prima: il motore ignora il punto ma addScorer aggiunge una riga 'Point' e una GoalAction che puntano all'evento precedente. Un ANNULLA successivo toglie la riga fantasma, mentre engine.undo toglie l'ultimo punto vero e riapre la partita.

**Rimedio.** Sull'orologio: if (_scoreState.value?.matchOver == true) return in incrementScore. Sul telefono: in addRemotePoint la stessa guardia di addScore. applyWatchBatch non deve contare le voci inerti.

### [media] Con TalkBack il punteggio dell'orologio non viene letto: la contentDescription fissa del lato prende il posto delle cifre

`wear/src/main/java/it/vantaggi/scoreboardessential/wear/MainActivity.kt` - aree: ui-wear

**Scenario.** I contenitori cliccabili hanno una contentDescription fissa (311-313) e le cifre non hanno accessibilityLiveRegion. Nel padel sul 30-15 il focus legge 'Squadra 1. Tocca per segnare…' senza nessun numero. Il comportamento di TalkBack non è stato misurato.

**Rimedio.** Mettere il punteggio nella contentDescription dentro renderScoreState e aggiungere accessibilityLiveRegion=polite alle cifre.

### [bassa] La riga di stato senza telefono in italiano probabilmente non entra nel quadrante da 192dp

`wear/src/main/res/layout/activity_main.xml` - aree: ui-wear

**Scenario.** Stima: 'NIENTE TELEFONO - 12 IN ATTESA' misura circa 135-140dp contro circa 128dp disponibili. Con maxLines=1 si leggerebbe 'NIENTE TELEFONO - 12 IN'. Va misurato.

**Rimedio.** Accorciare la stringa, oppure 0dp con ellipsize o autoSize.

## L8 Cronometro e portiere

### [media] L'anello del portiere sull'orologio ha un massimo fisso di 300, e la durata configurata viene sostituita dal tempo residuo

`wear/src/main/java/it/vantaggi/scoreboardessential/wear/WearViewModel.kt` - aree: ui-wear

**Scenario.** activity_main.xml:18 ha max=300 e nessuno chiama setMax. Con 600 s l'anello resta pieno per 5 minuti, con 60 s parte al 20%. Il telefono manda il residuo in pausa e alla ripresa (MatchTimerService 305, 328), e l'orologio (219) e il telefono (MainViewModel 282-285) lo salvano come durata. Dopo una pausa a 2:00, ogni conto successivo dura 2 minuti invece di 5.

**Rimedio.** Impostare max alla durata in secondi. Nel messaggio distinguere la durata configurata dal residuo. Trattare la pausa come uno stato a sé.

### [media] Quando il timer del portiere scade, il servizio non rilascia wake lock e primo piano e non salva lo stato; la finestra di scadenza è codice morto

`mobile/src/main/java/it/vantaggi/scoreboardessential/service/MatchTimerService.kt` - aree: build, concorrenza

**Scenario.** Il ramo di scadenza (278-296) mette _isKeeperTimerRunning=false e fa cancel(), ma non chiama checkStopForegroundAndWakeLock() né saveState(). showKeeperTimerExpired (MainViewModel 190) non ha osservatori, quindi showKeeperTimerExpiredAlert (MainActivity 984-993), il cui OK faceva il reset, non parte mai (l'osservatore è stato tolto in 0f14e831). Calcio a cronometro fermo, portiere scaduto: PARTIAL_WAKE_LOCK senza timeout e notifica ferma finché non si preme pausa o stop. È la localizzazione precisa del 'wake lock non rilasciato' che il piano tiene fra i difetti aperti.

**Rimedio.** Nel ramo di scadenza chiamare checkStopForegroundAndWakeLock() e saveState(). Ricollegare l'observer o togliere il codice morto. Test Robolectric con ShadowPowerManager.

### [bassa] 'Keeper timer expired!' scritto nel registro anche su pausa o azzeramento

`mobile/src/main/java/it/vantaggi/scoreboardessential/MainViewModel.kt` - aree: viewmodel

**Scenario.** Il collector (189) tratta ogni passaggio true→false come una scadenza, ma pause e reset del service mettono il flag a false (MatchTimerService 320, 342). Una pausa dalla notifica o dall'orologio, oppure END MATCH, scrive una riga falsa.

**Rimedio.** Un evento di scadenza dedicato dal service.

### [bassa] B3 chiuso su una premessa falsa: bindService prima di startNewMatch non rende disponibile il service

`mobile/src/main/java/it/vantaggi/scoreboardessential/MainViewModel.kt` - aree: viewmodel

**Scenario.** onServiceConnected arriva sempre in un messaggio successivo: in init matchTimerService è null alla 719 in entrambi gli ordini. Il commento 588-591 e la voce B3 promettono un azzeramento che non avviene.

**Rimedio.** Correggere il commento e il piano. Se l'azzeramento serve, farlo in onServiceConnected dopo il ripristino.

## L9 Dominio racchetta

### [media] Riassunto di una partita a racchetta non finita: l'intestazione dà i set vinti (0-0 a set unico) e i game del set in corso non compaiono

`core/src/main/kotlin/it/vantaggi/scoreboardessential/core/MatchSummary.kt` - aree: dominio

**Scenario.** summarize() mette headline() in score (225, 230) e raccoglie solo i set chiusi (217-219). format() stampa la riga dei set solo con sets.size > 1 (259). Padel sul 5-3, Condividi > testo: '*Anna / Carla 0-0 Bruno / Dario*', senza traccia del 5-3. Nel tennis, con un solo set chiuso, i game del set in corso non compaiono.

**Rimedio.** Quando wonBy è null, aggiungere a MatchSummary i game del set in corso e farli stampare a format(). headline() non va toccato.

### [media] Tennis: dopo un tie-break chiuso con N punti, N mod 4 = 1 o 2, il set successivo lo apre al servizio il lato sbagliato fino a fine partita

`core/src/main/kotlin/it/vantaggi/scoreboardessential/core/RacketRules.kt` - aree: dominio

**Scenario.** serveIndex sale a ogni totale dispari nel tie-break (101), poi c'è il +1 di winGame (116, 139). Dopo un 7-2 (N=9) partendo da 12 si arriva a 18: nel secondo set servingSide vale 1 invece di 2, e tutti i game dopo sono invertiti. Nel riassunto break e percentuali al servizio sono scambiati. 7-5 e 7-4 tornano giusti. Il commento 98-100 è falso. Il padel non è colpito, perché è a set unico. Il punteggio resta giusto.

**Rimedio.** Salvare in GamePoints.TieBreak l'indice di apertura e alla chiusura mettere serveIndex = apertura + 1. Aggiungere test bo3 con 7-2, 7-3 e 8-6 che verifichino servingSide.

## L10 Rilascio e lingua

### [alta] :wear a targetSdk 34: dal 31 agosto 2026 Play rifiuta gli aggiornamenti Wear OS sotto API 35 (Fase T chiusa male)

`wear/build.gradle` - aree: build

**Scenario.** wear/build.gradle:25 ha targetSdk 34. MIGRATION_PLAN.md:118-119 dà Wear per esente, ma l'esenzione vale solo per il salto ad API 36: Wear deve comunque puntare ad API 35 o superiore (fonte esterna, developer.android.com, letta dal revisore). Oggi è il 23/9/2026, quindi il blocco è già attivo: seguendo RELEASE_CHECKLIST l'upload del bundle wear viene rifiutato. La proroga si può chiedere fino al 1/11/2026.

**Rimedio.** Portare :wear a targetSdk 35 e provare su un emulatore Wear i cambi di comportamento di Android 15. Correggere il piano. Se serve tempo, chiedere la proroga.

Corretto: e373e37, :wear a targetSdk 35 (compileSdk era già 36, :mobile già a 36). Cambi di Android 15 verificati nel codice: nessun servizio in primo piano, nessun PendingIntent di sistema, nessun avvio di Activity dallo sfondo, broadcast solo con LocalBroadcastManager, niente statusBarColor o setDecorFitsSystemWindows, nessuna collisione List.removeFirst/removeLast, String.format senza indici 0$. Wear OS 5 è API 34, quindi questi cambi valgono solo da Wear OS 6. TargetSdkTest legge il targetSdk dal manifest unito, rosso a 34. Il passaggio su emulatore Wear resta da fare.

### [media] Cambio lingua in-app inefficace con installazione da AAB: gli split di lingua non sono disattivati

`mobile/src/main/java/it/vantaggi/scoreboardessential/utils/LocaleHelper.kt` - aree: build

**Scenario.** Il cambio lingua passa da Configuration.setLocale. In mobile/build.gradle non c'è bundle{language{enableSplit=false}} e non si usa setApplicationLocales. Con telefono in inglese e app installata da Play, scegliendo Italiano values-it non è installato e le stringhe restano in inglese. È il comportamento documentato degli split, non osservato.

**Rimedio.** bundle { language { enableSplit = false } }, oppure AppCompatDelegate.setApplicationLocales con localeConfig.

Corretto: e373e37, tutti e due: `bundle { language { enableSplit = false } }` in mobile/build.gradle e la lingua passa da setApplicationLocales (voce sotto). Un test in LinguaUnicaTest legge il blocco dal file di build, rosso con enableSplit = true. La voce AppBundleLocaleChanges della baseline di lint non trova più il problema.

### [media] Solo MainActivity e MatchSettingsActivity applicano la lingua scelta; le altre cinque Activity seguono il sistema

`mobile/src/main/java/it/vantaggi/scoreboardessential/MainActivity.kt` - aree: build

**Scenario.** attachBaseContext con LocaleHelper.onAttach esiste solo in MainActivity:132, MatchSettingsActivity:28 e nell'Application. LocaleHelper:11 forza 'en' quando non c'è niente di salvato. Su un telefono italiano, alla prima installazione, l'onboarding compare in italiano e la schermata principale in inglese; Statistiche, Storico e Giocatori seguono il sistema. È ragionamento sul framework, non osservato.

**Rimedio.** Una sola via: AppCompatDelegate.setApplicationLocales (risolve anche gli split), oppure una BaseActivity con l'override.

Corretto: e373e37, una sola via, AppCompatDelegate.setApplicationLocales. Nel manifest ci sono AppLocalesMetadataHolderService con autoStoreLocales, per API < 33, e localeConfig. Tolti gli attachBaseContext di Application, MainActivity e MatchSettingsActivity, e la copia della lingua in MatchSettingsRepository e nel ViewModel. Senza scelta l'app segue il sistema e non forza più "en". Il selettore mostra la lingua che la schermata usa davvero. La scelta salvata dalla versione precedente viene portata una volta sola da MainActivity. Nei test di LinguaUnicaTest, girati sul codice di prima, restano rossi la scelta dalle impostazioni, Statistiche ancora in inglese (a sdk 32) e il recupero della scelta vecchia.

Corretto: afd71d7, due buchi del rimedio sopra. La versione precedente salvava "en" a ogni avvio anche senza scelta, e migrarlo bloccava in inglese chi ha il telefono in italiano: ora si migra solo una lingua diversa da "en". Sotto API 33 MatchTimerService avvolge il proprio contesto con la lingua scelta, perché AppCompat la applica solo alle Activity e le notifiche del cronometro restavano nella lingua del sistema. Due test in LinguaUnicaTest, rossi senza le due correzioni.

## L11 Registro a schermo, testi, colori e accessibilità del telefono

### [media] L'ora del registro è sbagliata oltre i 60 minuti e nei fusi con la mezz'ora (tempo trattato come data)

`mobile/src/main/java/it/vantaggi/scoreboardessential/MainViewModel.kt` - aree: ui-mobile

**Scenario.** Rilievo dei revisori, verificato leggendo. Alle righe 1384-1385 c'è SimpleDateFormat("mm:ss").format(Date(matchTimerValue)): il tempo trascorso diventa un istante e viene formattato nel fuso del dispositivo. 'mm' ricomincia da 00 ogni ora, quindi un gol al 65' compare come '05:00' in una partita da 90'. In Asia/Kolkata (+5:30) ogni riga è spostata di 30 minuti, anche nel padel, dove il cronometro è spento.

**Rimedio.** Calcolare i minuti con ms/60000 e i secondi con (ms/1000)%60, senza passare da Date. Aggiungere un test indipendente dal fuso.

### [media] Nelle impostazioni la scritta dei pulsanti colore è quasi bianca sul colore della squadra: illeggibile con i colori predefiniti

`mobile/src/main/java/it/vantaggi/scoreboardessential/ui/MatchSettingsActivity.kt` - aree: ui-mobile

**Scenario.** Gli observer (104-110) cambiano solo lo sfondo, e testo e icona restano #E0E0E0. Con #FFD600 e #76FF03 il contrasto è circa 1,07:1 e 1,01:1: TEAM 1 COLOR e TEAM 2 COLOR sono praticamente invisibili.

**Rimedio.** Portare applyReadableTextColor in un'utilità comune e applicarla a setTextColor e iconTint.

### [media] Etichette delle rose e righe dei punti nel registro nel colore grezzo della squadra: con un colore scuro spariscono sul fondo scuro

`mobile/src/main/java/it/vantaggi/scoreboardessential/MainActivity.kt` - aree: ui-mobile

**Scenario.** MainActivity:298 e 311, MatchLogAdapter 117-119: setTextColor(colore della squadra) su #1E1E1E, senza correzioni. Con la squadra 2 in blu scuro le sue righe nel registro, visibile in tutti gli sport, sono illeggibili.

**Rimedio.** Schiarire il testo quando il contrasto con #1E1E1E scende sotto la soglia, oppure usare colorOnSurface e tenere il colore solo sull'indicatore.

### [media] Il PDF mette 'TABELLINO MARCATORI' anche nel padel e ci elenca le squadre con il numero di punti

`mobile/src/main/java/it/vantaggi/scoreboardessential/utils/MatchReportUtils.kt` - aree: ui-mobile

**Scenario.** 76-81 contano ogni SCORE con player != null, e addScorer scrive player = nome della squadra (1209-1215). Nel padel il PDF mostra 'SQUADRA1 (n)' sotto TABELLINO MARCATORI. Nel calcio i gol non attribuiti finiscono nel tabellino sotto il nome della squadra. Titoli cablati nel layout.

**Rimedio.** Contare solo gli eventi con playerId != null, nascondere FORMAZIONI e TABELLINO negli sport senza marcatore, portare i titoli in strings.xml.

### [bassa] Il report PDF lascia aperto il FileOutputStream e condivide il file anche se la scrittura fallisce

`mobile/src/main/java/it/vantaggi/scoreboardessential/utils/MatchReportUtils.kt` - aree: concorrenza

**Scenario.** 126-134: ogni SHARE lascia un descrittore aperto. Con il disco pieno si condivide senza avviso un PDF troncato o vecchio.

**Rimedio.** FileOutputStream(pdfFile).use{…}, e in caso di errore non restituire l'Intent.

### [bassa] A ogni ricreazione dell'Activity ricompare il toast sullo stato di Wear OS

`mobile/src/main/java/it/vantaggi/scoreboardessential/MainActivity.kt` - aree: viewmodel

**Scenario.** Il test di connessione sta in onCreate (169). Con l'orologio spento, a ogni rotazione compare 'Wear OS Not Connected'.

**Rimedio.** Eseguirlo solo con savedInstanceState == null.

### [bassa] Ora del registro in una colonna fissa da 50dp: con caratteri molto grandi può andare a capo

`mobile/src/main/res/layout/match_event_item.xml` - aree: ui-mobile

**Scenario.** Colonna da 50dp senza maxLines (24). Con il carattere al 200% '00:00' misura circa 66dp e si spezza. Il caso delle tre cifre non esiste, perché il formato è mm:ss. Va verificato.

**Rimedio.** wrap_content con minWidth, oppure maxLines=1.

### [bassa] 'Partita ripresa' scritta in italiano nel codice, mentre la stringa match_resumed non la usa nessuno

`mobile/src/main/java/it/vantaggi/scoreboardessential/MainViewModel.kt` - aree: ui-mobile

**Scenario.** Riga 966: con il telefono in inglese compare 'Partita ripresa' in mezzo a righe inglesi.

**Rimedio.** Salvare un tipo di evento e tradurlo nell'adapter, oppure usare la risorsa.

### [bassa] Nel padel e nel tennis il registro dice 'press START to begin', ma START è nascosto

`mobile/src/main/java/it/vantaggi/scoreboardessential/MainViewModel.kt` - aree: ui-mobile

**Scenario.** startNewMatch (727) scrive sempre la frase, mentre applyCapabilities nasconde i comandi con ClockMode.NONE.

**Rimedio.** Scegliere la frase in base a capabilities.clock, presa da una risorsa.

### [bassa] Tasto 'Fine' del tutorial tradotto 'Fines' in italiano

`mobile/src/main/res/values-it/strings.xml` - aree: ui-mobile, build

**Scenario.** Riga 16, onboarding_finish='Fines'.

**Rimedio.** 'Fine' o 'Inizia'.

### [bassa] Titoli e schermate secondarie non tradotti, a volte con l'italiano mostrato agli utenti inglesi

`mobile/src/main/AndroidManifest.xml` - aree: ui-mobile

**Scenario.** Le label del manifest (24-37) sono letterali inglesi, e 'Manage Players' è cablato. Statistiche e il dialogo del marcatore hanno testi italiani cablati ('Chi ha segnato?'), altri dialoghi testi inglesi cablati.

**Rimedio.** Portare tutto in strings.xml e values-it.

### [bassa] Etichette delle formazioni ancora nei colori del tema e 'No formation' cablato

`mobile/src/main/res/layout/content_scoreboard_details.xml` - aree: ui-mobile

**Scenario.** 65 e 72 usano colorPrimary e colorSecondary, e nessuno le ricolora: accanto a rose gialla e verde compaiono rosa e ciano. updateFormation costruisce testo inglese cablato.

**Rimedio.** Colorarle nello stesso observer delle rose e mettere il testo in una risorsa con segnaposto.

### [bassa] Sulla card TalkBack non legge il nome della squadra

`mobile/src/main/res/layout/content_scoreboard_live.xml` - aree: ui-mobile

**Scenario.** I contenitori cliccabili (95, 139) hanno la contentDescription fissa cd_edit_team_N_name: il nome 'ROSSI' non si sente mai. Da verificare con TalkBack.

**Rimedio.** contentDescription dinamica con il nome della squadra.

### [bassa] Contrasti insufficienti: icona del FAB giocatori (circa 1,17:1) e testo dei pulsanti pieni (circa 3,17:1)

`mobile/src/main/res/values/themes.xml` - aree: ui-mobile

**Scenario.** #E0E0E0 su ciano #00E5FF (activity_main.xml:36) e su rosa #F50057 (themes 19-22), con testo a 14sp sotto la soglia AA.

**Rimedio.** colorOnSecondary e colorOnPrimary scuri, oppure colori primari più scuri.

### [bassa] Bersagli sotto i 48dp e comandi senza etichetta nella gestione giocatori

`mobile/src/main/res/layout/item_player_management.xml` - aree: ui-mobile

**Scenario.** ImageButton da 40dp con contentDescription cablate in inglese; add_player_fab senza contentDescription.

**Rimedio.** Portarli a 48dp, usare contentDescription da @string e aggiungere l'etichetta al FAB.

### [bassa] Risorse dichiarate e mai usate

`mobile/src/main/res/values/dimens.xml` - aree: ui-mobile

**Scenario.** Undici dimen, report_sets, report_draw e match_resumed senza riferimenti.

**Rimedio.** Rimuoverle, oppure collegare le stringhe ai testi oggi cablati.

## L12 Rete di test

### [bassa] Tre test verdi che non possono fallire: annullamento offline, fuga di dati nei log e quattro benchmark senza asserzioni

`wear/src/test/java/it/vantaggi/scoreboardessential/wear/OfflineScoreTest.kt` - aree: test

**Scenario.** OfflineScoreTest:182: base e atteso valgono entrambi '30', quindi il test passa anche senza calcolo locale. SimplifiedDataLayerListenerServiceTest:99-111 ha solo un'asserzione negativa, con le eccezioni inghiottite. StringConcatenationBenchmarkTest, MatchReportUtilsBenchmark, PerformanceTest.benchmarkBatchVsLoop ed ExampleUnitTest non asseriscono niente sul codice vero, e due di questi scrivono su /tmp.

**Rimedio.** Un primario sentinella 'X'. Un receiver che verifichi il broadcast 10/5. Togliere i benchmark dalla suite o dar loro asserzioni reali.

### [bassa] Buchi di copertura: nessun test sul protocollo v2 del telefono, sulla messa in coda del tocco non consegnato né sui path v2 recenti del golden

`mobile/src/main/java/it/vantaggi/scoreboardessential/SimplifiedDataLayerListenerService.kt` - aree: test

**Scenario.** Invertire seq <= ultima, togliere la guardia di selectSport o togliere pending.add lascia tutti i test verdi. WearProtocolGoldenTest:120 omette MSG_SPORT_INTENT, MSG_INTENT_BATCH e MSG_BATCH_ACK.

**Rimedio.** Test Robolectric sul service con MessageEvent (seq crescente, ripetuta, due nodi). Un test di messa in coda in OfflineScoreTest. Raccogliere per riflessione tutte le costanti di WearConstants.

### [bassa] Test dipendenti dal tempo reale o da una coroutine IO non controllata (MatchTimerServiceTest, OptimizedWearDataSyncTest)

`mobile/src/test/java/it/vantaggi/scoreboardessential/service/MatchTimerServiceTest.kt` - aree: test

**Scenario.** Thread.sleep(2500) con atLeast(2) invii su Dispatchers.Default. In OptimizedWearDataSyncTest (124-132) il costruttore lancia refreshConnection su IO fuori da runTest. L'instabilità è dedotta, non osservata.

**Rimedio.** Iniettare dispatcher e sorgente del tempo e usare advanceTimeBy. Impostare lo stub prima della costruzione.

### [bassa] Il totale '451 test JVM' del piano somma debug e release

`MIGRATION_PLAN.md` - aree: test

**Scenario.** Riga 1387: i test distinti sono 269, benchmark senza asserzioni compresi.

**Rimedio.** Riportare i test distinti, oppure scrivere 'debug + release'.

## Cosa NON e' stato validato

In nessuna area si è eseguito qualcosa: niente build, test, lint, adb o emulatore. Ogni rilievo viene dalla lettura del codice. I report sotto build/ sono del 13/9 e sono stati usati solo per i conteggi. In questa sintesi ho riletto solo tre punti: la freccia su di Cronologia e Impostazioni senza gestione di home, SimpleDateFormat("mm:ss") su Date alla riga 1384, e addRemotePoint senza la guardia sugli eventi inerti. Per area:
- DOMINIO: livescoring-reference.txt non letto riga per riga. Il difetto del tie-break è dimostrato con un conto a mano, non con un test. Il super tie-break non è modellato, ma non è richiesto.
- PERSISTENZA: le migrazioni 6->11 non sono verificabili senza gli schemi precedenti. MatchSummarizer e MatchExporter non sono stati letti. I test JVM con DAO mockati non sospendono, quindi non avrebbero comunque fatto emergere il doppio insert.
- PROTOCOLLO: non letti PendingIntents, LastKnownMatch, MatchTimerService (se tenga vivo il processo in primo piano nel padel conta per il rilievo del tocco perso ad app chiusa), SportSelectionActivity e il parser v2 di WearDataLayerService.
- OFFLINE: MatchEngine e MatchLogCodec non letti da questo revisore (li hanno letti dominio e concorrenza). Non verificato matchClock.relative per una partita col cronometro mai avviato (tempi del batch forse negativi o nulli). Non verificata la perdita dell'ultimo tocco con apply() per uno spegnimento brusco.
- VIEWMODEL: non letti OptimizedWearDataSync, MatchSettingsActivity e ViewModel, PlayersManagement, MatchLogAdapter e i layout. Tralasciate la corsa fra il timer su Default e stopForeground e gli AlertDialog persi alla rotazione.
- UI-MOBILE: misure del calcio in orizzontale (il '+' sotto i 360dp utili?), altezze dei MaterialButton testuali e aspetto con il carattere a 2x. Contrasti calcolati sugli esadecimali, non misurati a schermo.
- UI-WEAR: tutte le misure sul quadrante da 192dp sono stime. TalkBack e performClick descritti dalla conoscenza della piattaforma. Il dialogo di AZZERA sul quadrante tondo è stato escluso proprio perché non dimostrabile senza schermo.
- TEST: non letti i corpi di vari test del core e di parte dei test legacy. Le falsificazioni dichiarate nel piano sono state controllate solo leggendo.
- BUILD: il requisito targetSdk per Wear viene da una pagina esterna (developer.android.com), non dal codice. Non controllati i cambi di comportamento di API 35 sull'orologio. L'abbinamento delle voci di baseline di lint è ricostruito a mano.
- CONCORRENZA: gli adapter del telefono, OnboardingActivity, LocaleHelper, AddEditPlayer e RoleSelection sono stati guardati solo con grep. Scartati come trascurabili: letture di SharedPreferences sul main thread, doppia codifica del registro e campi del timer senza @Volatile.
Nessuna area ha coperto l'app intera. Fuori da ogni perimetro restano: StatisticsViewModel e GetPlayerStatsUseCase nel dettaglio, l'export verso Padel Elite oltre alla sua condizione d'accesso, e le migrazioni storiche.
Voci del piano che risultano chiuse male: B5 (updatePlayers), B3 (premessa falsa), 'riproverà al collegamento successivo' (arretrato), 'Niente fusioni decise al posto dell'utente' (contraddice il punteggio offline al polso), Fase T sull'esenzione Wear dal targetSdk, il commento 'la STESSA condizione' in MatchSettingsViewModel, il commento sul servizio dopo il tie-break in RacketRules, il totale di 451 test. Il rilievo del 13/9 sulle rose non ha coperto le etichette delle formazioni.

## Cosa serve un dispositivo per decidere

Il punto più importante, prima di tutto il resto: dopo cc142d7 la sincronizzazione dal vivo in tempo reale non è mai stata osservata in nessuna delle due direzioni. Tutto L4, L5 e L6 descrive percorsi che non hanno mai girato su un dispositivo.
Prove da fare su telefono con orologio (o emulatore Wear accoppiato), in quest'ordine:
1) Eco dei DataItem locali. Segnare un gol attribuito sul telefono e cercare 'Broadcasted score update' nel logcat del TELEFONO, poi leggere l'eventLog della riga viva. Decide se il rilievo sull'autoricezione è alto o cade.
2) Ordine di consegna in AZZERA/Finisci nel calcio (PATH_SCORE urgente contro MATCH_STATE e TIMER_STATE non urgenti), e ritardo reale dei DataItem non urgenti.
3) Arretrato. Consegna con l'app del telefono chiusa; ack con l'Activity dell'orologio chiusa; batch rifiutato su una partita già cominciata; comportamento del quadrante e della coda dopo ognuno dei tre casi; connectionState che resta Connected durante una caduta del Bluetooth con lo schermo acceso.
4) Frequenza dell'inversione di due MessageClient ravvicinati (doppio tocco) e dell'ordine di completamento dei Task GMS in coda offline.
5) Tocco singolo con l'app del telefono chiusa: il punto va perso?
6) Finestra all'avvio a freddo del telefono fra registerReceiver e la fine del ripristino.
7) Freccia su da Cronologia e Impostazioni: MainActivity viene davvero ricreata, e le rose si perdono?
8) Wear_OS_Small_Round: cronometro fermo nel calcio v2, lati toccabili a partita finita, riga di stato in italiano, TalkBack sul punteggio, anello del portiere con 60 e 600 secondi.
9) Telefono: wake lock dopo la scadenza del portiere (dumpsys power), lingua con installazione da AAB e lingua nelle cinque Activity senza override, colonna dell'ora con il carattere al 200%, TalkBack sulla card, calcio in orizzontale sotto i 360dp.
10) :wear portato a targetSdk 35: cambi di comportamento di Android 15 sull'orologio.
Si possono chiudere senza dispositivo: L1, L2 (tranne la finestra di avvio), L3, L9, L11 e L12, con test JVM o Robolectric. Per la doppia insert e il doppio ViewModel serve un DAO finto che sospenda davvero.
