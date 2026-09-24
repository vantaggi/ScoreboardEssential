// =====================================================================
// tests/fixtures/scoreboard/genera.mjs
//
// Genera i file di esempio del tabellone (ScoreboardEssential) usati dai test
// dell'import e della cronaca. Formato: docs/SCOREBOARD_FORMAT.md.
//
//   node tests/fixtures/scoreboard/genera.mjs
//
// PERCHE' UN GENERATORE E NON FILE SCRITTI A MANO: una partita a tre set sono
// duecento punti, e il servitore di ciascuno dipende da tutti quelli prima.
// A mano si sbaglia, e un fixture sbagliato fa passare un motore sbagliato.
//
// PERCHE' NON USA window.LiveScoring: e' il motore sotto test. Qui le partite
// si descrivono game per game ("il lato 1 vince a 30"), il punteggio dei set si
// CONTA dal copione e il servizio segue le regole di RacketRules.kt dell'app,
// riscritte da capo. Se il replay della dashboard e questo copione non sono
// d'accordo, uno dei due sbaglia: e' il senso del test.
//
// Quando l'app esportera' il suo file di esempio ufficiale (richiesta 3 del
// contratto), quello si affianchera' a questi come prova differenziale vera.
// =====================================================================
import { writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const QUI = dirname(fileURLToPath(import.meta.url));

// --- Copione di un game -----------------------------------------------------
// { w: 1|2, l: 0..3 }            game vinto da w, con l punti dell'avversario
//                                (l = 3 col punto secco = si arriva al 40-40)
// { w, deuces: n }               vantaggi: 40-40, poi n volte "vantaggio perso",
//                                poi vantaggio e game (solo regola ADVANTAGE)
// { tb: w, score: [a, b] }       tie-break vinto da w col punteggio indicato
function pointsOfGame(g) {
    if (g.tb) {
        const [a, b] = g.score;
        const w = g.tb, l = 3 - w;
        const wp = w === 1 ? a : b, lp = w === 1 ? b : a;
        const seq = [];
        // Alternati finche' il perdente ha punti da fare, poi il vincitore chiude.
        let wi = 0, li = 0;
        while (li < lp) { if (wi < wp - 1) { seq.push(w); wi++; } seq.push(l); li++; }
        while (wi < wp) { seq.push(w); wi++; }
        return seq;
    }
    const w = g.w, l = 3 - w;
    if (g.deuces !== undefined) {
        const seq = [w, l, w, l, w, l];            // 40-40
        for (let i = 0; i < g.deuces; i++) seq.push(w, l); // vantaggio, parita'
        seq.push(w, w);
        return seq;
    }
    const seq = [];
    for (let i = 0; i < g.l; i++) seq.push(l, w);
    while (seq.filter(x => x === w).length < 4) seq.push(w);
    // Con l = 3 la sequenza sopra fa l,w,l,w,l,w,w: al settimo punto il game e'
    // gia' chiuso sul 4-3 col punto secco, quindi va bene cosi'.
    return seq;
}

function partita({ formatVersion = 1, config, players, copione, extra = {}, orario = true }) {
    const order = config.serveOrder;
    const timeline = [];
    const setScores = [];
    const setsWon = [0, 0];
    const setsToWin = Math.floor(config.sets / 2) + 1;
    let serveIndex = 0;
    let t = 0;
    let winnerTeam = null;
    let n = 0;

    const push = (side) => {
        timeline.push({
            side,
            servingPlayerId: order.length === 4 ? order[serveIndex % 4] : null,
            atMillis: orario ? t : null,
        });
        n++;
        // 20-45 secondi a punto, deterministici.
        t += 20000 + ((n * 7919) % 26) * 1000;
    };

    copione.forEach((set, si) => {
        const games = [0, 0];
        set.forEach((g, gi) => {
            if (winnerTeam) throw new Error('copione oltre la fine della partita');
            if (g.tb) {
                const openedAt = serveIndex;
                let tot = 0;
                for (const side of pointsOfGame(g)) {
                    push(side);
                    tot++;
                    if (tot % 2 === 1) serveIndex++;
                }
                serveIndex = openedAt + 1;
                games[g.tb - 1]++;
            } else {
                for (const side of pointsOfGame(g)) push(side);
                serveIndex++;
                games[g.w - 1]++;
            }
            // Cambio campo sui game dispari.
            if ((games[0] + games[1]) % 2 === 1) t += 60000;
            const max = Math.max(...games), min = Math.min(...games);
            const chiuso = (max >= config.gamesPerSet && max - min >= 2)
                || (config.tieBreak && max === config.gamesPerSet + 1 && min === config.gamesPerSet);
            if (chiuso) {
                if (gi !== set.length - 1) throw new Error(`set ${si + 1} chiuso prima della fine del copione`);
                setScores.push(games.slice());
                const w = games[0] > games[1] ? 0 : 1;
                setsWon[w]++;
                t += 120000;
                if (setsWon[w] >= setsToWin) winnerTeam = w + 1;
            }
        });
    });

    const headline = winnerTeam && config.sets === 1 ? setScores[0] : setsWon;
    return {
        formatVersion,
        sportId: 'padel',
        config,
        players,
        scoreTeam1: headline[0],
        scoreTeam2: headline[1],
        winnerTeam,
        setScores,
        timeline,
        ...extra,
    };
}

// I giocatori del gruppo finto dei test (js/mocks.js): id 1..4. Il roster e'
// nell'ordine dell'app, NON per lato, come nell'export vero.
const roster = [
    { localId: 1, name: 'Marco', side: 1, padelPlayerId: 1 },
    { localId: 2, name: 'Anna', side: 2, padelPlayerId: 3 },
    { localId: 3, name: 'Luca', side: 1, padelPlayerId: 2 },
    { localId: 4, name: 'Sara', side: 2, padelPlayerId: 4 },
];
const baseConfig = {
    mode: 'POINTS', deuce: 'GOLDEN_POINT', sets: 1, tieBreak: true,
    gamesPerSet: 6, tieBreakTo: 7, serveOrder: [1, 2, 3, 4],
};

const G = (w, l) => ({ w, l });

// 1. Tre set: tie-break nel primo, secondo perso, rimonta da 2-5 nel terzo con
//    un match point annullato sul 5-2 (punto secco giocato e vinto dal lato 1).
const treSet = partita({
    config: { ...baseConfig, sets: 3 },
    players: roster,
    copione: [
        [G(1, 2), G(2, 1), G(1, 0), G(2, 3), G(1, 1), G(2, 2), G(1, 3), G(2, 0), G(1, 2), G(2, 1), G(1, 0), G(2, 2),
            { tb: 1, score: [7, 5] }],
        [G(2, 1), G(1, 2), G(2, 0), G(2, 3), G(1, 1), G(2, 2), G(1, 0), G(2, 1), G(2, 2)],
        [G(2, 1), G(1, 0), G(2, 2), G(2, 1), G(1, 3), G(2, 0), G(2, 2),
            G(1, 3), G(1, 1), G(1, 2), G(1, 0), G(1, 1)],
    ],
});

// 2. Partita interrotta sul 4-3 (campo scaduto), senza ordine di servizio.
const interrotta = partita({
    config: { ...baseConfig, serveOrder: [] },
    players: roster,
    copione: [[G(1, 1), G(2, 2), G(1, 0), G(2, 3), G(1, 2), G(1, 1), G(2, 0)]],
});

// 3. Set unico 6-2 usato per i casi di errore.
const breve = () => partita({
    config: baseConfig,
    players: roster,
    copione: [[G(1, 0), G(1, 1), G(2, 2), G(1, 3), G(1, 0), G(2, 1), G(1, 2), G(1, 0)]],
});

const incoerente = breve();
incoerente.setScores = [[6, 4]];

const nonCollegato = breve();
nonCollegato.players = roster.map(p =>
    p.localId === 2 ? { ...p, padelPlayerId: null } : p.localId === 4 ? { ...p, padelPlayerId: 99 } : p);

const sconosciuta = breve();
sconosciuta.formatVersion = 3;

// 4. Versione 2: vantaggi, 6-4, con matchId, startedAt e appVersion, senza tempi.
const v2 = partita({
    formatVersion: 2,
    config: { ...baseConfig, deuce: 'ADVANTAGE' },
    players: roster,
    orario: false,
    copione: [[G(1, 2), { w: 2, deuces: 2 }, G(1, 1), G(2, 0), { w: 1, deuces: 0 }, G(2, 3),
        G(1, 1), G(2, 2), G(1, 0), G(1, 2)]],
    extra: {
        matchId: '6f1c2d3e-4b5a-4c6d-8e7f-9a0b1c2d3e4f',
        startedAt: '2026-09-22T21:15:00+02:00',
        appVersion: '1.4.0',
    },
});

const file = (nome, dati) => {
    writeFileSync(join(QUI, nome), JSON.stringify(dati) + '\n', 'utf8');
    console.log(`${nome}: ${dati.timeline.length} punti, set ${JSON.stringify(dati.setScores)}, vince ${dati.winnerTeam}`);
};

file('v1-tre-set.json', treSet);
file('v1-interrotta.json', interrotta);
file('v1-incoerente.json', incoerente);
file('v1-non-collegato.json', nonCollegato);
file('v3-sconosciuta.json', sconosciuta);
file('v2-vantaggi.json', v2);
