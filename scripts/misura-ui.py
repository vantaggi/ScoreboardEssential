#!/usr/bin/env python3
"""Misura dell'interfaccia contro la UI Constitution (09-checks, passo 1 di 02-integration "Adopting").

Conta cio' che i controlli automatici del sistema devono portare a zero, senza cambiare niente:

    python3 scripts/misura-ui.py              stampa il report in Markdown
    python3 scripts/misura-ui.py --dettaglio  aggiunge l'elenco delle righe trovate

I numeri sono la barra di avanzamento dell'adozione (docs/ui-constitution/misura.md). In CI gira come
avviso: esce sempre con 0. Quando un conteggio arriva a zero diventa bloccante (passo g del piano).

La geometria del quadrante non si misura qui ma con il test Robolectric
`GeometriaDelPolsoTest` (modulo :wear), che scrive `wear/build/misura/geometria-<forma>.txt`: se i
file ci sono, il report li riporta.
"""

import re
import sys
from collections import defaultdict
from pathlib import Path

RADICE = Path(__file__).resolve().parent.parent
MODULI = ["mobile", "wear"]
DETTAGLIO = "--dettaglio" in sys.argv


def righe(file: Path):
    try:
        return file.read_text(encoding="utf-8").splitlines()
    except UnicodeDecodeError:
        return []


def rel(file: Path) -> str:
    return str(file.relative_to(RADICE))


def xml_della_ui(modulo: str):
    res = RADICE / modulo / "src/main/res"
    return sorted(p for p in res.rglob("*.xml") if p.is_file())


def kotlin(modulo: str):
    return sorted((RADICE / modulo / "src/main/java").rglob("*.kt"))


def e_un_file_di_token(p: Path) -> bool:
    # I file dei valori che definiscono i token: colori, misure, interi.
    return p.parent.name.startswith("values") and (
        p.name.startswith("colors") or p.name in ("dimens.xml", "integers.xml")
    )


def e_un_commento_kotlin(riga: str) -> bool:
    s = riga.strip()
    return s.startswith("//") or s.startswith("*") or s.startswith("/*")


def senza_commenti_xml(testo: str) -> str:
    # Toglie i commenti tenendo le righe, cosi' i numeri di riga restano giusti.
    return re.sub(r"<!--.*?-->", lambda m: "\n" * m.group(0).count("\n"), testo, flags=re.S)


class Conteggio:
    def __init__(self, nome: str, perche: str):
        self.nome = nome
        self.perche = perche
        self.per_modulo = defaultdict(list)

    def aggiungi(self, modulo: str, dove: str):
        self.per_modulo[modulo].append(dove)

    def totale(self, modulo: str) -> int:
        return len(self.per_modulo[modulo])


def cerca_xml(conteggio: Conteggio, modulo: str, regex: str, filtro=lambda p: True):
    for p in xml_della_ui(modulo):
        if not filtro(p):
            continue
        for n, riga in enumerate(senza_commenti_xml(p.read_text(encoding="utf-8")).splitlines(), 1):
            for m in re.finditer(regex, riga):
                conteggio.aggiungi(modulo, f"{rel(p)}:{n}: {m.group(0)}")


def cerca_kotlin(conteggio: Conteggio, modulo: str, regex: str):
    for p in kotlin(modulo):
        for n, riga in enumerate(righe(p), 1):
            if e_un_commento_kotlin(riga):
                continue
            for m in re.finditer(regex, riga):
                conteggio.aggiungi(modulo, f"{rel(p)}:{n}: {m.group(0)}")


def non_icona(p: Path) -> bool:
    # Le dimensioni intrinseche dei vettori (24dp) sono il disegno, non una misura dell'interfaccia.
    return not (p.parent.name.startswith("drawable") and "<vector" in p.read_text(encoding="utf-8"))


def misure():
    risultati = []

    colori = Conteggio("Colori scritti a mano", "un valore esadecimale fuori dai file dei token, o un colore costruito nel codice")
    for m in MODULI:
        cerca_xml(colori, m, r"#[0-9A-Fa-f]{6,8}\b", lambda p: not e_un_file_di_token(p))
        cerca_kotlin(colori, m, r"Color\.(parseColor|rgb|argb)\(|0x[0-9A-Fa-f]{8}\b")
    risultati.append(colori)

    # Un numero diverso da zero: 0dp non e' una scelta di disegno.
    num = r"-?(?!0+(\.0+)?(?:sp|dp|px)\b)[0-9.]+"
    fuori_dagli_stili = lambda p: non_icona(p) and not p.parent.name.startswith("values")
    attributi = {
        # TextAppearance e ShapeAppearance in values/ sono le definizioni dei ruoli: si contano i layout e i disegni.
        "Corpi del testo scritti a mano": (rf'textSize="{num}(sp|dp|px)"', "un `textSize` in un layout invece di uno stile di testo", fuori_dagli_stili),
        "Raggi scritti a mano": (rf'(radius|Radius|cornerSize)[A-Za-z]*="{num}(dp|px)"', "un raggio in un layout o in un disegno senza `@dimen`", fuori_dagli_stili),
        "Ombre scritte a mano": (rf'elevation="{num}(dp|px)"|"(android:)?elevation">{num}(dp|px)<', "un'elevazione diversa da 0 senza `@dimen`", non_icona),
        "Spaziature scritte a mano": (rf'(margin|padding|Margin|Padding)[A-Za-z]*="{num}(dp|px)"|"(android:)?(layout_margin|padding)[A-Za-z]*">{num}(dp|px)<', "un margine o un padding diverso da 0 senza `@dimen`", non_icona),
        "Durate scritte a mano": (r'duration="[0-9]+"|"android:duration">[0-9]+<', "una durata senza `@integer`", non_icona),
    }
    for nome, (regex, perche, filtro) in attributi.items():
        c = Conteggio(nome, perche)
        for m in MODULI:
            cerca_xml(c, m, regex, filtro)
        risultati.append(c)

    durate_kt = Conteggio("Durate scritte a mano nel codice", "una durata in millisecondi data a un'animazione senza `R.integer`")
    for m in MODULI:
        cerca_kotlin(durate_kt, m, r"(setDuration|\.duration\s*=|setStartDelay|startDelay\s*=)\s*\(?\s*[0-9]+L?\b")
    risultati.append(durate_kt)
    return risultati


def colori_del_modulo(modulo: str):
    """nome -> (valore risolto, file) per ogni colore definito nei values del modulo."""
    grezzi = {}
    for p in (RADICE / modulo / "src/main/res").glob("values*/colors*.xml"):
        testo = senza_commenti_xml(p.read_text(encoding="utf-8"))
        for m in re.finditer(r'<color name="([a-z0-9_]+)">([^<]+)</color>', testo):
            grezzi[m.group(1)] = (m.group(2).strip(), rel(p))

    def risolvi(valore, visti=()):
        if valore.startswith("@color/"):
            nome = valore[len("@color/"):]
            if nome in grezzi and nome not in visti:
                return risolvi(grezzi[nome][0], visti + (nome,))
        return valore.upper()

    return {nome: (risolvi(v), f) for nome, (v, f) in grezzi.items()}


def colori_del_telefono():
    """nome -> valore nel tema scuro, per i nomi che layout e codice del telefono citano.

    Sul telefono i token che cambiano col tema sono selettori in `res/color` (`background_canvas.xml`
    punta a `?attr/colorBackgroundCanvas`), e il tema base assegna all'attributo il valore `_standard`: il nome
    del token e' quello del selettore. I valori `_standard`, `_chiaro` e `_alto_contrasto` sono i valori
    dei temi, non nomi da citare, e non si confrontano.
    """
    grezzi = colori_del_modulo("mobile")
    res = RADICE / "mobile/src/main/res"
    temi = senza_commenti_xml((res / "values/themes.xml").read_text(encoding="utf-8"))
    base = re.search(r'<style name="Theme\.ScoreboardEssential"[^>]*>(.*?)</style>', temi, flags=re.S).group(1)
    attributi = dict(re.findall(r'<item name="([A-Za-z]+)">@color/([a-z0-9_]+)</item>', base))
    out = {n: v for n, (v, f) in grezzi.items() if not re.search(r"_(standard|chiaro|alto_contrasto)$", n)}
    for p in (res / "color").glob("*.xml"):
        testo = senza_commenti_xml(p.read_text(encoding="utf-8"))
        x = re.search(r'android:color="\?attr/([A-Za-z]+)"', testo)
        # Un token e' un selettore di un elemento solo; quelli coi loro stati (bottoni, campi) sono componenti.
        if testo.count("<item") == 1 and x and x.group(1) in attributi and attributi[x.group(1)] in grezzi:
            out[p.stem] = grezzi[attributi[x.group(1)]][0]
    return out


def tema(nome: str) -> str:
    """Il tema di un colore dal suo nome: i valori chiari e la carta del PDF sono lo stesso tema chiaro."""
    if nome.endswith("_alto_contrasto"):
        return "alto contrasto"
    if nome.endswith("_chiaro") or nome.endswith("_print"):
        return "chiaro"
    return "scuro"


def nomi_doppi():
    """Lo stesso ruolo con due nomi, dentro un modulo e fra telefono e orologio.

    Due ruoli diversi possono avere lo stesso valore (il fondo e il testo sull'accento sono entrambi
    #0D0D0F): non e' un doppione, e non si conta. Gli alias `@color/` contano come un nome solo.
    """
    c = Conteggio("Nomi diversi per lo stesso colore", "lo stesso ruolo con lo stesso valore nello stesso tema sotto due nomi (`text_primary_chiaro` e `text_primary_print`)")
    for m in MODULI:
        per_ruolo = defaultdict(list)
        for p in (RADICE / m / "src/main/res").glob("values*/colors*.xml"):
            testo = senza_commenti_xml(p.read_text(encoding="utf-8"))
            for x in re.finditer(r'<color name="([a-z0-9_]+)">(#[0-9A-Fa-f]+)</color>', testo):
                ruolo = re.sub(r"_(standard|chiaro|alto_contrasto|print)$", "", x.group(1))
                per_ruolo[(tema(x.group(1)), ruolo, x.group(2).upper())].append(x.group(1))
        for (t, ruolo, valore), nomi in sorted(per_ruolo.items()):
            if len(nomi) > 1:
                c.aggiungi(m, f"{t}, {ruolo} {valore}: {', '.join(sorted(nomi))}")

    fra = Conteggio("Nomi diversi fra telefono e orologio", "un colore dell'orologio che sul telefono ha un altro nome o un altro valore")
    telefono = colori_del_telefono()
    polso = colori_del_modulo("wear")
    for nome, (valore, _) in sorted(polso.items()):
        if nome in telefono:
            if telefono[nome] != valore:
                fra.aggiungi("wear", f"{nome}: {valore} sull'orologio, {telefono[nome]} sul telefono")
            continue
        simili = sorted(n for n, v in telefono.items() if v == valore)
        if simili:
            fra.aggiungi("wear", f"{nome} ({valore}): sul telefono si chiama {', '.join(simili)}")

    # Il ruolo si cita col suo nome (`accent_default`), mai col valore di un tema (`accent_default_standard`,
    # `text_primary_chiaro`): quelli li assegnano soltanto i temi. Fanno eccezione le icone del launcher,
    # che si disegnano senza il tema dell'app e vogliono un colore pieno.
    uso = Conteggio("Valori di un tema citati al posto del ruolo", "un `_standard`, `_chiaro` o `_alto_contrasto` citato fuori dai file dei colori e dei temi; le icone del launcher non contano")
    di_tema = r"[a-z0-9_]+_(?:standard|chiaro|alto_contrasto)"
    for m in MODULI:
        for p in xml_della_ui(m):
            if e_un_file_di_token(p) or p.name.startswith("ic_launcher") or p.name.startswith(("themes", "theme")):
                continue
            for n, riga in enumerate(senza_commenti_xml(p.read_text(encoding="utf-8")).splitlines(), 1):
                for x in re.finditer(rf"@color/({di_tema})\b", riga):
                    uso.aggiungi(m, f"{rel(p)}:{n}: {x.group(1)}")
        for p in kotlin(m):
            for n, riga in enumerate(righe(p), 1):
                if e_un_commento_kotlin(riga):
                    continue
                for x in re.finditer(rf"R\.color\.({di_tema})\b", riga):
                    uso.aggiungi(m, f"{rel(p)}:{n}: {x.group(1)}")
    return [c, fra, uso]


def stringhe(modulo: str, cartella: str):
    out = {}
    for p in (RADICE / modulo / "src/main/res" / cartella).glob("strings*.xml"):
        testo = senza_commenti_xml(p.read_text(encoding="utf-8"))
        for x in re.finditer(r'<(string|plurals|string-array) name="([a-z0-9_]+)"([^>]*)>(.*?)</\1>', testo, flags=re.S):
            if 'translatable="false"' in x.group(3):
                continue
            out[x.group(2)] = (x.group(4), rel(p))
    return out


def testi():
    mancanti = Conteggio("Stringhe senza traduzione italiana", "una stringa in `values/` che `values-it/` non ha: a un utente italiano compare in inglese")
    for m in MODULI:
        en = stringhe(m, "values")
        it = stringhe(m, "values-it")
        for nome in sorted(set(en) - set(it)):
            mancanti.aggiungi(m, f"{en[nome][1]}: {nome}")

    letterali = Conteggio("Testi scritti nel layout o nel codice", "un testo a schermo che non passa dalle risorse")
    for m in MODULI:
        cerca_xml(letterali, m, r'android:(text|hint|contentDescription|title)="(?![@?])[^"]*[A-Za-z][^"]*"')
        cerca_kotlin(letterali, m, r'(setText|makeText\([^,]+,|contentDescription\s*=|\.text\s*=|setTitle|setMessage)\s*\(?\s*"[^"]*[A-Za-z][^"]*"')
    ident = Conteggio("Identificativi che possono finire a schermo", "\"Team 1\" e simili come ripiego di un nome, o \"Team\" in una stringa italiana")
    for m in MODULI:
        # Il predefinito salvato (MatchSettingsRepository.DEFAULT_TEAM1_NAME) e' il segnale "nessun nome
        # scelto" nei dati, letto anche dall'orologio e dalle partite gia' salvate: a schermo passa sempre da
        # EtichettaSquadra, che lo traduce. Si conta solo quando fa da ripiego in un'espressione.
        cerca_kotlin(ident, m, r'(?<!const val DEFAULT_TEAM1_NAME = )(?<!const val DEFAULT_TEAM2_NAME = )"Team [12]"')
        for nome, (valore, f) in sorted(stringhe(m, "values-it").items()):
            if re.search(r"\bTeam\b", valore):
                ident.aggiungi(m, f"{f}: {nome}")
    return [mancanti, letterali, ident]


def geometria():
    ordine = ["tondo-192", "tondo-227", "en-tondo-192", "en-tondo-227", "quadrato-180", "quadrato-192", "quadrato-227"]
    file = [RADICE / f"wear/build/misura/geometria-{forma}.txt" for forma in ordine]
    presenti = [f for f in file if f.exists()]
    if not presenti:
        return None
    return "\n".join(f.read_text(encoding="utf-8").rstrip() for f in presenti)


def main():
    tutte = misure() + nomi_doppi() + testi()
    print("| Controllo | Telefono | Orologio | Che cosa conta |")
    print("| --- | ---: | ---: | --- |")
    for c in tutte:
        print(f"| {c.nome} | {c.totale('mobile')} | {c.totale('wear')} | {c.perche} |")
    g = geometria()
    print()
    if g:
        print("Geometria del quadrante (`GeometriaDelPolsoTest`):")
        print()
        print(g)
    else:
        print("Geometria del quadrante: lanciare `./gradlew :wear:testDebugUnitTest --tests '*GeometriaDelPolsoTest'` per i file `wear/build/misura/geometria-<forma>.txt`.")
    if DETTAGLIO:
        for c in tutte:
            for m in MODULI:
                if c.per_modulo[m]:
                    print(f"\n### {c.nome}, {'telefono' if m == 'mobile' else 'orologio'}\n")
                    for d in c.per_modulo[m]:
                        print(f"- `{d}`")


if __name__ == "__main__":
    main()
