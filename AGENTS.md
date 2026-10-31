# Interfaccia di ScoreboardEssential: istruzioni per gli agenti

L'interfaccia di questo progetto (telefono Android e orologio Wear OS) segue il design system **UI Constitution**.

Design system: <https://claude.ai/artifact/8DhrtZW85wiHTsfw1vmXSn> (sola lettura: non pubblicarci niente).
Se non riesci ad aprire il design system o i file dei token di questo progetto, fermati e chiedili al proprietario. Non progettare a memoria.

File del progetto:
- Token: ogni colore ha il nome del suo ruolo nel sistema (`background_canvas`, `text_primary`, `accent_default`, `on_accent`, `status_error`, `background_watch`...), uguale su telefono e orologio. Telefono: i ruoli sono in `mobile/src/main/res/color/<ruolo>.xml` e seguono il tema (`?attr/color<Ruolo>`); i valori dei temi sono in `values/colors.xml` (`_standard`, lo scuro predefinito), `colors_chiaro.xml` e `colors_alto_contrasto.xml`. Orologio: `wear/src/main/res/values/colors.xml`, un tema solo. Layout e codice citano il ruolo, mai il valore di un tema. Durate in `integers.xml`, misure in `dimens.xml`, ruoli Material 3 in `themes.xml` (telefono) e `theme.xml` (orologio).
- Mappatura ruolo -> token, conflitti dichiarati e scelte del progetto: `DESIGN.md`, sezione "Adattamento alla UI Constitution".
- Parole a schermo: `GLOSSARIO.md`; i testi stanno in `res/values/strings*.xml` (inglese) e `res/values-it/strings*.xml` (italiano) di ciascun modulo, mai scritti nel layout o nel codice.
- `STYLE_GUIDE.md` ("Street Vibe") e' superata: non usarla.

Prima di ogni modifica all'interfaccia:
1. Leggi nel sistema il README e le sezioni 01-adapting, 03-layout, 05-motion e 10-review; per l'orologio anche 08-wearable; per pagine, indietro, menu e cambi di forma 04-transitions.
2. Leggi i file dei token, `GLOSSARIO.md` e i componenti che esistono gia'. Riusali.
3. Dichiara il piano: gerarchia, layout, componenti riusati, stati, movimento, accessibilita', schermo stretto e carattere al 200%, conflitti.

Sempre:
- Solo token: nessun colore, spaziatura, raggio, ombra, durata o curva scritti a mano.
- Icone dalla tabella concetto -> icona di `DESIGN.md`: Material Symbols outlined, una famiglia, niente emoji; i comandi di sola icona hanno `contentDescription`.
- Ogni stato che vale, i tre temi (scuro, chiaro, alto contrasto), TalkBack e movimento ridotto.
- Ogni animazione collega due stati e usa i token condivisi.
- Pagine, indietro, menu, tastiera, rotazione e cambi di misura seguono 04-transitions; ogni transizione si controlla con fotogrammi, rallentatore x5 e interruzione prima di dirla finita.
- Ogni PR si verifica con la matrice di 09-checks e dice quali celle ha visto; `python3 scripts/misura-ui.py` conta i valori scritti a mano, i nomi doppi e i testi da tradurre, e nessuna PR fa salire un conteggio (`docs/ui-constitution/misura.md`).
- Scelte del progetto (conflitti dichiarati in `DESIGN.md`): testo e numeri in Inter, numeri con `tnum`, al massimo tre pesi (400, 500, 600); sul quadrante dell'orologio le cifre restano condensate perche' Inter non entra; il ciano `#00E5FF` solo nei grafici e come colore predefinito del lato 2.

Identita' del progetto:
```text
Product character: tabellone sportivo (padel, tennis, calcio) da bordo campo, letto a colpo d'occhio e toccato col pollice; al polso un numero letto in mezzo secondo
Density: balanced nel contorno; in gioco un numero per lato; compact sull'orologio
Geometry: precise; orologio tondo prima, quadrato dopo
Accent behavior: restrained sul telefono; functional sull'orologio (il lime marca solo chi serve, in ambient nessun colore)
Motion character: precise sul telefono, calm sull'orologio
Primary domain pattern: tabellone a due meta' con punteggio tabulare, registro e Cronaca; quadrante a fasce con la coppia di cifre al centro
Distinctive visual behavior: nero puro in gioco, cifre bianche giganti, colore di squadra solo in zone + e barretta (TeamInk), pallino lime per il servizio
Tones and accent: background-canvas = #0D0D0F, background-surface = #161618, accent-default = lime #C8F135, on-accent = #0D0D0F; gioco e quadrante su background-watch #000000 con cifre text-on-watch #FFFFFF
```

Finisci con il rapporto e il controllo dei pattern vietati di 10-review.
