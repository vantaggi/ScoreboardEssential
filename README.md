# ScoreboardEssential

ScoreboardEssential è un'applicazione semplice e intuitiva per Android e Wear OS per tenere traccia dei punteggi in una partita di calcio. L'app consente di aumentare e diminuire facilmente i punteggi di due squadre e presenta un timer sincronizzato su entrambi i dispositivi, mobile e smartwatch.

## Struttura del Progetto

Il progetto è suddiviso in tre moduli:

*   `mobile`: L'applicazione Android per dispositivi palmari.
*   `wear`: L'applicazione Wear OS per dispositivi indossabili.
*   `shared`: Un modulo comune che contiene la logica di sincronizzazione dei dati utilizzata da entrambi i moduli `mobile` e `wear`.

## Stato dell'Implementazione

Le seguenti funzionalità sono state implementate e sono funzionanti:

*   **Gestione Punteggio:** È possibile aggiungere o sottrarre punti per ogni squadra.
*   **Nomi Squadra Personalizzabili:** Gli utenti possono inserire i nomi per le due squadre.
*   **Timer Partita:** Un timer principale per la partita che può essere avviato, fermato e resettato.
*   **Timer Portiere:** Un timer secondario, utile per la rotazione dei portieri, che mostra un avviso alla scadenza.
*   **Gestione Giocatori:**
    *   Creazione di nuovi giocatori con nome e ruolo.
    *   Assegnazione dei giocatori alle squadre.
    *   Rimozione dei giocatori dalle squadre.
*   **Cronologia Partite:** I risultati delle partite vengono salvati e possono essere visualizzati in una schermata dedicata.
*   **Sincronizzazione Mobile -> Wear:** Le modifiche effettuate sul dispositivo mobile (punteggi, nomi, timer) si riflettono sullo smartwatch.
*   **Controlli Base su Wear OS:** L'app per smartwatch permette di modificare il punteggio e di interagire con il timer del portiere.

## Elenco delle Funzionalità non Complete o Mancanti

*   **Sincronizzazione Completa Wear -> Mobile:** La sincronizzazione dallo smartwatch al telefono non è completa. Ad esempio, non è possibile avviare o fermare il timer principale dallo smartwatch.
*   **Ruoli dei Giocatori:** È possibile assegnare dei "ruoli" ai giocatori, ma questa informazione non viene attualmente utilizzata o visualizzata in nessuna parte dell'applicazione.
*   **Colori delle Squadre:** Il codice sorgente prevede la possibilità di impostare colori personalizzati per le squadre, ma non è presente un'interfaccia utente per selezionarli.
*   **Configurazione del Backup:** Le regole per il backup automatico dei dati su Android non sono state definite.
*   **Nome del Pacchetto:** Il nome del pacchetto dell'applicazione (`com.example.scoreboardessential`) è quello di default e andrebbe modificato per una versione di produzione.
