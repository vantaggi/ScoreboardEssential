# Street Vibe Evolved - Style Guide

## 1. Manifesto del Design

La nostra missione è infondere l'anima del calcio di strada in ogni pixel dell'applicazione. Ogni interazione deve trasmettere energia, grinta e creatività. I nostri pilastri sono:

-   **Energia Urbana:** Colori vibranti e accesi che catturano l'essenza dei playground cittadini.
-   **Chiarezza Ribelle:** Tipografia di sistema condensata e in grassetto per una leggibilità immediata e uno stile deciso.
-   **Autenticità Materica:** Sfondi che simulano superfici urbane come asfalto e cemento.
-   **Gerarchia Marcata:** Forme con angoli tagliati netti che definiscono chiaramente i componenti interattivi.

## 2. Palette Colori

La palette "Street Vibe" è il cuore del nostro design system.

| Nome Colore           | Hex       | Utilizzo Principale                               |
| --------------------- | --------- | ------------------------------------------------- |
| `asphalt_dark`        | `#121212` | Sfondo principale dell'applicazione (`android:colorBackground`) |
| `concrete_gray`       | `#1E1E1E` | Sfondo per superfici e card (`colorSurface`)      |
| `stencil_white`       | `#E0E0E0` | Testo e icone su sfondi scuri (`colorOnSurface`)  |
| `sidewalk_gray`       | `#9E9E9E` | Testo secondario, hint (`colorOnSurfaceVariant`)  |
| `graffiti_pink`       | `#F50057` | Colore primario per azioni principali (`colorPrimary`) |
| `neon_cyan`           | `#00E5FF` | Colore secondario per accenti (`colorSecondary`)  |
| `team_spray_yellow`   | `#FFD600` | Colore per Team 1 / Categoria Ruolo "Difesa"      |
| `team_electric_green` | `#76FF03` | Colore per Team 2 / Categoria Ruolo "Porta"       |
| `error_red`           | `#FF1744` | Colore per errori e azioni distruttive (`colorError`) |

## 3. Tipografia

Utilizziamo esclusivamente font di sistema per massimizzare le performance e la coerenza cross-device. Lo stile è definito da `sans-serif-condensed`.

-   **Display Large (Street):** `TextAppearance.App.DisplayLarge.Street`
    -   Uso: Titoli di grande impatto (es. punteggi).
    -   Stile: `sans-serif-condensed`, bold, letter spacing `0.08`.
-   **Headline Medium (Street):** `TextAppearance.App.HeadlineMedium.Street`
    -   Uso: Nomi delle squadre e titoli di sezione.
    -   Stile: `sans-serif-condensed`, bold, `textAllCaps`.
-   **Body Large (Street):** `TextAppearance.App.BodyLarge.Street`
    -   Uso: Testo del corpo principale.
    -   Stile: `sans-serif-condensed`.

## 4. Forme e Superfici

Le forme sono definite da angoli tagliati netti per un look aggressivo e distintivo.

-   **Card (Street):** `ShapeAppearance.App.StreetCard`
    -   Angoli: Tagliati (`cut`)
    -   Dimensione: `12dp`
-   **Button (Street):** `ShapeAppearance.App.StreetButton`
    -   Angoli: Tagliati (`cut`)
    -   Dimensione: `8dp`

Gli sfondi utilizzano drawable XML per simulare texture urbane:
-   `@drawable/bg_asphalt_main` per lo sfondo dell'app.
-   `@drawable/bg_concrete_card` per l'interno delle card.

## 5. Iconografia "Stencil"

Abbiamo rimpiazzato tutte le icone di sistema con un set custom in stile "stencil". Questo garantisce un'identità visiva unica. Le icone sono definite come VectorDrawable e utilizzano `?attr/colorOnSurface` per il colore, adattandosi al tema.

Icone implementate:
-   `ic_plus.xml` (precedentemente `ic_stencil_add.xml`)
-   `ic_minus.xml` (precedentemente `ic_stencil_remove.xml`)
-   `ic_person_add.xml`
-   `ic_delete.xml`
-   `ic_edit.xml`
-   `ic_stats.xml`

## 6. Animazioni

-   **Glitch & Snap:** Al cambio di punteggio, il testo del punteggio esegue una micro-animazione che ne aumenta la scala e "glitcha" il colore verso il `colorPrimary` del tema, per poi tornare allo stato normale. L'implementazione si trova in `AnimationUtils.kt`.
