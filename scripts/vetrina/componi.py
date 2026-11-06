"""Compone le immagini del README dalle catture di Robolectric (scripts/vetrina.sh).

Legge mobile/build/vetrina e wear/build/vetrina, scrive docs/media/. Dati finti, colori dai token
del sistema (background_canvas, accent_default, text_primary, text_secondary).
"""
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

RADICE = Path(__file__).resolve().parents[2]
TEL = RADICE / "mobile/build/vetrina"
POLSO = RADICE / "wear/build/vetrina"
OUT = RADICE / "docs/media"
FONT = RADICE / "shared/src/main/res/font"

CANVAS = (0x0D, 0x0D, 0x0F)  # background_canvas
SURFACE = (0x16, 0x16, 0x18)  # background_surface
BORDO = (0x2A, 0x2A, 0x2E)  # border_subtle
TESTO = (0xFF, 0xFF, 0xFF)  # text_primary
TESTO2 = (0x8A, 0x8A, 0x9A)  # text_secondary
LIME = (0xC8, 0xF1, 0x35)  # accent_default


def inter(peso, misura):
    nome = {400: "inter_regular", 500: "inter_medium", 600: "inter_semibold"}[peso]
    return ImageFont.truetype(str(FONT / f"{nome}.ttf"), misura)


def telefono(cattura, larghezza):
    """La cattura dentro un telefono essenziale: cornice scura e angoli tondi."""
    im = Image.open(cattura).convert("RGB")
    h = round(im.height * larghezza / im.width)
    im = im.resize((larghezza, h), Image.LANCZOS)
    bordo = max(8, larghezza // 32)
    raggio = larghezza // 9
    W, H = larghezza + 2 * bordo, h + 2 * bordo
    out = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(out)
    d.rounded_rectangle((0, 0, W - 1, H - 1), raggio + bordo, fill=SURFACE, outline=BORDO, width=2)
    maschera = Image.new("L", im.size, 0)
    ImageDraw.Draw(maschera).rounded_rectangle((0, 0, larghezza - 1, h - 1), raggio, fill=255)
    out.paste(im, (bordo, bordo), maschera)
    return out


def orologio(cattura, diametro):
    """Il quadrante tondo dentro una ghiera scura."""
    im = Image.open(cattura).convert("RGBA").resize((diametro, diametro), Image.LANCZOS)
    ghiera = max(10, diametro // 14)
    D = diametro + 2 * ghiera
    out = Image.new("RGBA", (D, D), (0, 0, 0, 0))
    d = ImageDraw.Draw(out)
    d.ellipse((0, 0, D - 1, D - 1), fill=SURFACE, outline=BORDO, width=2)
    d.ellipse((ghiera - 3, ghiera - 3, D - ghiera + 2, D - ghiera + 2), fill=(0, 0, 0))
    maschera = Image.new("L", im.size, 0)
    ImageDraw.Draw(maschera).ellipse((0, 0, diametro - 1, diametro - 1), fill=255)
    out.paste(im, (ghiera, ghiera), maschera)
    return out


def copertina(tel, polso, nome, titolo, sottotitolo, W=1600, H=900):
    tela = Image.new("RGB", (W, H), CANVAS)
    d = ImageDraw.Draw(tela)
    t = telefono(tel, 340)
    o = orologio(polso, 280)
    x0 = 100
    d.text((x0, 300), titolo, font=inter(600, 64), fill=TESTO)
    y = 395
    for riga in sottotitolo:
        d.text((x0, y), riga, font=inter(400, 30), fill=TESTO2)
        y += 44
    # Il pallino lime: chi serve, il solo accento della pagina.
    d.ellipse((x0, y + 40, x0 + 18, y + 58), fill=LIME)
    d.text((x0 + 34, y + 30), "Padel · Tennis · Football", font=inter(500, 30), fill=TESTO)
    xt = W - t.width - 300
    yt = (H - t.height) // 2
    tela.paste(t, (xt, yt), t)
    xo = W - o.width - 80
    yo = H - o.height - 110
    tela.paste(o, (xo, yo), o)
    OUT.mkdir(parents=True, exist_ok=True)
    tela.save(OUT / nome, optimize=True)
    print(OUT / nome)


def teaser(nome, didascalia, W=960, H=540, fotogrammi=9):
    """La stessa partita sul polso e sul telefono, un punto al secondo: GIF in loop."""
    quadri = []
    for i in range(fotogrammi):
        tela = Image.new("RGB", (W, H), CANVAS)
        d = ImageDraw.Draw(tela)
        t = telefono(TEL / f"telefono-teaser-{i:02d}.png", 210)
        o = orologio(POLSO / f"polso-tondo-teaser-{i:02d}.png", 230)
        d.text((60, 70), didascalia[0], font=inter(600, 36), fill=TESTO)
        d.text((60, 120), didascalia[1], font=inter(400, 24), fill=TESTO2)
        tela.paste(o, (90, H - o.height - 60), o)
        tela.paste(t, (W - t.width - 110, (H - t.height) // 2), t)
        quadri.append(tela.convert("P", palette=Image.ADAPTIVE, colors=64))
    durate = [1600] + [900] * (fotogrammi - 2) + [2200]
    quadri[0].save(OUT / nome, save_all=True, append_images=quadri[1:], duration=durate, loop=0, optimize=True)
    print(OUT / nome)


def schermata(cattura, nome, larghezza=300):
    """Una schermata del telefono nella sua cornice, su fondo trasparente."""
    t = telefono(cattura, larghezza)
    t.save(OUT / nome, optimize=True)
    print(OUT / nome)


def polsi(nome, D=300):
    """Il tondo e il quadrato affiancati, la stessa partita."""
    o = orologio(POLSO / "polso-tondo-copertina.png", D)
    q = Image.open(POLSO / "polso-quadrato-copertina.png").convert("RGBA").resize((D - 20, D - 20), Image.LANCZOS)
    ghiera = max(10, D // 14)
    Q = Image.new("RGBA", (q.width + 2 * ghiera, q.height + 2 * ghiera), (0, 0, 0, 0))
    ImageDraw.Draw(Q).rounded_rectangle((0, 0, Q.width - 1, Q.height - 1), D // 5, fill=SURFACE, outline=BORDO, width=2)
    m = Image.new("L", q.size, 0)
    ImageDraw.Draw(m).rounded_rectangle((0, 0, q.width - 1, q.height - 1), D // 8, fill=255)
    Q.paste(q, (ghiera, ghiera), m)
    W = o.width + Q.width + 60
    tela = Image.new("RGBA", (W, max(o.height, Q.height)), (0, 0, 0, 0))
    tela.paste(o, (0, 0), o)
    tela.paste(Q, (o.width + 60, (o.height - Q.height) // 2), Q)
    tela.save(OUT / nome, optimize=True)
    print(OUT / nome)


if __name__ == "__main__":
    OUT.mkdir(parents=True, exist_ok=True)
    schermata(TEL / "telefono-copertina.png", "tabellone.png")
    schermata(TEL / "cronaca-1.png", "cronaca.png")
    schermata(TEL / "cronaca-3.png", "cronaca-servizio.png")
    schermata(TEL / "storico.png", "storico.png")
    schermata(TEL / "statistiche.png", "statistiche.png")
    polsi("polso.png")
    teaser("teaser.gif", ["Score on the wrist,", "the phone follows. Point by point."])
    copertina(
        TEL / "telefono-copertina.png",
        POLSO / "polso-tondo-copertina.png",
        "copertina.png",
        "ScoreboardEssential",
        ["The courtside scoreboard, read at a glance:", "on your phone and on your wrist."],
    )
