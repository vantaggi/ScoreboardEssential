// =====================================================================
// js/match-log.js — La CRONACA di una partita (window.MatchLog)
//
// Motore PURO: niente DOM, niente rete. Legge il file del tabellone
// ScoreboardEssential, lo rigioca punto per punto e ne ricava le statistiche.
// L'interfaccia (import e finestra della cronaca) sta in js/match-log-ui.js.
// Contratto del file: docs/SCOREBOARD_FORMAT.md.
//
// Il punteggio NON e' nel file: si ricava rigiocando i punti con
// window.LiveScoring (js/livematch.js), lo stesso motore della Diretta, col
// tie-break vero acceso (config.tieBreakTo). Rigiocare e' anche il controllo
// di qualita' dell'import: se i punti non danno i set scritti nel file, i due
// motori non sono d'accordo e il file non si importa.
//
// Due forme dello stesso dato:
//   * il FILE, con gli id locali dell'app (parse → file);
//   * la CRONACA ("log"), con gli id di v2_players: e' quella che si salva in
//     v2_match_logs e che si guarda (fromFile / fromStored → log).
// =====================================================================
(function () {
    'use strict';

    const SUPPORTED_VERSIONS = [1, 2];
    const MAX_POINTS = 3000;
    const DEUCES = ['GOLDEN_POINT', 'KILLER_POINT', 'ADVANTAGE'];

    const isInt = (v) => Number.isInteger(v);
    const isSide = (v) => v === 1 || v === 2;

    function problem(code, message) {
        return { code, message };
    }

    // ---------------------------------------------------------------
    // 1) Lettura del file
    // ---------------------------------------------------------------

    // Restituisce { ok, file, problems }. I problemi sono frasi da mostrare
    // cosi' come sono: devono dire cosa fare, non solo cosa non va.
    function parse(input) {
        let raw;
        try {
            raw = typeof input === 'string' ? JSON.parse(input) : input;
        } catch (e) {
            return fail(problem('not_json', 'Il file non si legge: non è quello esportato dal tabellone, oppure si è rovinato nel passaggio. Esportalo di nuovo.'));
        }
        if (!raw || typeof raw !== 'object' || Array.isArray(raw)) {
            return fail(problem('not_json', 'Il file non si legge: non è quello esportato dal tabellone, oppure si è rovinato nel passaggio. Esportalo di nuovo.'));
        }

        const version = raw.formatVersion;
        if (!SUPPORTED_VERSIONS.includes(version)) {
            const newer = isInt(version) && version > Math.max(...SUPPORTED_VERSIONS);
            return fail(problem('unknown_version', newer
                ? `Il file viene da una versione più nuova del tabellone (formato ${version}): questa dashboard sa leggere fino al formato ${Math.max(...SUPPORTED_VERSIONS)}. Va aggiornata la dashboard.`
                : 'Il file non ha un formato riconosciuto: non sembra esportato dal tabellone.'));
        }
        if (raw.sportId !== 'padel') {
            return fail(problem('not_padel', `È una partita di ${typeof raw.sportId === 'string' ? raw.sportId : 'uno sport sconosciuto'}: qui si importano solo partite di padel.`));
        }

        const problems = [];
        const config = readConfig(raw.config);
        if (!config) problems.push(problem('bad_config', 'Il file non dice con quali regole si è giocato (punti, set, tie-break): esportalo di nuovo dal tabellone.'));

        const players = Array.isArray(raw.players) ? raw.players : [];
        const playersOk = players.every(p => p && isInt(p.localId) && isSide(p.side) && typeof p.name === 'string');
        if (players.length !== 4 || !playersOk) {
            problems.push(problem('wrong_player_count', `Servono 4 giocatori, due per coppia: nel file ${players.length === 4 ? 'i giocatori non sono completi' : 'ce ne sono ' + players.length}.`));
        } else {
            const s1 = players.filter(p => p.side === 1).length;
            if (s1 !== 2) problems.push(problem('unbalanced_sides', `Le coppie non sono da due: ${s1} giocatori da una parte e ${4 - s1} dall'altra.`));
            if (new Set(players.map(p => p.localId)).size !== 4) problems.push(problem('duplicate_players', 'Lo stesso giocatore compare due volte nella partita.'));
        }

        const localIds = new Set(players.map(p => p && p.localId));
        const timeline = Array.isArray(raw.timeline) ? raw.timeline : null;
        if (!timeline || timeline.length === 0) {
            problems.push(problem('no_points', 'Il file non contiene punti giocati: la partita non è mai iniziata.'));
        } else if (timeline.length > MAX_POINTS) {
            problems.push(problem('too_many_points', `Il file contiene ${timeline.length} punti: troppi per una partita di padel. Non lo importo.`));
        } else {
            let lastT = -1;
            const bad = timeline.some(pt => {
                if (!pt || !isSide(pt.side)) return true;
                if (pt.servingPlayerId != null && !localIds.has(pt.servingPlayerId)) return true;
                if (pt.atMillis != null) {
                    if (!isInt(pt.atMillis) || pt.atMillis < lastT) return true;
                    lastT = pt.atMillis;
                }
                return false;
            });
            if (bad) problems.push(problem('bad_points', 'Alcuni punti del file non sono validi (lato, servizio o orario): esportalo di nuovo dal tabellone.'));
        }

        const setScores = Array.isArray(raw.setScores) ? raw.setScores : null;
        if (!setScores || !setScores.every(s => Array.isArray(s) && s.length === 2 && s.every(g => isInt(g) && g >= 0))) {
            problems.push(problem('bad_set_scores', 'Il risultato dei set nel file non è leggibile.'));
        }
        const winnerTeam = raw.winnerTeam == null ? null : raw.winnerTeam;
        if (winnerTeam !== null && !isSide(winnerTeam)) {
            problems.push(problem('bad_set_scores', 'Il vincitore indicato nel file non è valido.'));
        }

        if (problems.length) return { ok: false, file: null, problems };

        const file = {
            formatVersion: version,
            config,
            players: players.map(p => ({
                localId: p.localId,
                name: p.name,
                side: p.side,
                padelPlayerId: isInt(p.padelPlayerId) ? p.padelPlayerId : null,
            })),
            winnerTeam,
            setScores: setScores.map(s => s.slice()),
            timeline: timeline.map(pt => ({
                side: pt.side,
                server: pt.servingPlayerId == null ? null : pt.servingPlayerId,
                t: pt.atMillis == null ? null : pt.atMillis,
            })),
            matchId: typeof raw.matchId === 'string' && /^[A-Za-z0-9-]{8,64}$/.test(raw.matchId) ? raw.matchId : null,
            startedAt: typeof raw.startedAt === 'string' && !isNaN(Date.parse(raw.startedAt)) ? raw.startedAt : null,
            appVersion: typeof raw.appVersion === 'string' ? raw.appVersion.slice(0, 40) : null,
        };

        // Il controllo che conta: i punti devono raccontare la stessa partita
        // che il file dichiara. Si rigioca con gli id locali, cosi' non serve
        // aver gia' collegato i giocatori.
        const rp = replay(localLog(file));
        const coherence = checkCoherence(file, rp);
        if (coherence) return { ok: false, file: null, problems: [coherence] };

        return { ok: true, file, problems: [] };
    }

    function fail(p) {
        return { ok: false, file: null, problems: [p] };
    }

    function readConfig(c) {
        if (!c || typeof c !== 'object') return null;
        if (c.mode !== 'POINTS' && c.mode !== 'GAMES') return null;
        if (!DEUCES.includes(c.deuce)) return null;
        if (![1, 3, 5].includes(c.sets)) return null;
        if (typeof c.tieBreak !== 'boolean') return null;
        if (!isInt(c.gamesPerSet) || c.gamesPerSet < 1 || c.gamesPerSet > 9) return null;
        if (!isInt(c.tieBreakTo) || c.tieBreakTo < 1 || c.tieBreakTo > 99) return null;
        const serveOrder = Array.isArray(c.serveOrder) && c.serveOrder.length === 4 && c.serveOrder.every(isInt)
            ? c.serveOrder.slice() : [];
        return {
            mode: c.mode, deuce: c.deuce, sets: c.sets, tieBreak: c.tieBreak,
            gamesPerSet: c.gamesPerSet, tieBreakTo: c.tieBreakTo, serveOrder,
        };
    }

    function checkCoherence(file, rp) {
        const fmt = (sets) => sets.length ? sets.map(s => s.join('-')).join(', ') : 'nessun set chiuso';
        const played = rp.final.closedSets;
        const sameSets = played.length === file.setScores.length
            && played.every((s, i) => s[0] === file.setScores[i][0] && s[1] === file.setScores[i][1]);
        const sameWinner = (rp.final.winnerTeam || null) === file.winnerTeam;
        if (sameSets && sameWinner && !rp.pointsAfterEnd) return null;
        return problem('inconsistent',
            `Il risultato scritto nel file (${fmt(file.setScores)}) non corrisponde ai punti giocati (${fmt(played)}). ` +
            'Non lo importo per non salvare un risultato sbagliato: segnalalo, allegando il file.');
    }

    // ---------------------------------------------------------------
    // 2) Dal file alla cronaca, e ritorno dal database
    // ---------------------------------------------------------------

    // La cronaca con gli id LOCALI dell'app: serve solo al controllo di
    // coerenza, prima che i giocatori siano collegati.
    function localLog(file) {
        return {
            config: file.config,
            players: file.players.map(p => ({ id: p.localId, side: p.side, name: p.name })),
            serveOrder: file.config.serveOrder.slice(),
            points: file.timeline.map(pt => ({ side: pt.side, server: pt.server, t: pt.t })),
        };
    }

    // Collegamento dei giocatori: localId → v2_players.id. Parte da
    // padelPlayerId del file, e l'admin puo' correggerlo (overrides).
    // groupPlayerIds: gli id dei giocatori del gruppo attivo.
    function resolvePlayers(file, groupPlayerIds, overrides) {
        const inGroup = new Set((groupPlayerIds || []).map(Number));
        const mapping = {};
        const unresolved = [];
        file.players.forEach(p => {
            const o = overrides && overrides[p.localId];
            const id = o != null && o !== '' ? Number(o) : p.padelPlayerId;
            if (id != null && inGroup.has(id)) mapping[p.localId] = id;
            else unresolved.push(p);
        });
        const ids = Object.values(mapping);
        const duplicated = ids.length !== new Set(ids).size;
        return { mapping, unresolved, duplicated, complete: unresolved.length === 0 && !duplicated };
    }

    // La cronaca in id padel, pronta per il database e per la finestra.
    function fromFile(file, mapping, names) {
        const idOf = (localId) => (localId == null ? null : (mapping[localId] != null ? mapping[localId] : null));
        return {
            config: stripServeOrder(file.config),
            players: file.players.map(p => ({
                id: idOf(p.localId), side: p.side,
                name: (names && names[idOf(p.localId)]) || p.name,
            })),
            serveOrder: file.config.serveOrder.length === 4 ? file.config.serveOrder.map(idOf) : [],
            points: file.timeline.map(pt => ({ side: pt.side, server: idOf(pt.server), t: pt.t })),
        };
    }

    // La cronaca come la restituisce il database: una riga di v2_match_logs
    // e la sua partita (player1/2 lato 1, player3/4 lato 2).
    function fromStored(row, match, names) {
        const ids = [match.player1_id, match.player2_id, match.player3_id, match.player4_id];
        return {
            config: row.config,
            players: ids.map((id, i) => ({ id, side: i < 2 ? 1 : 2, name: (names && names[id]) || '?' })),
            serveOrder: Array.isArray(row.serve_order) ? row.serve_order.slice() : [],
            points: (row.points || []).map(pt => ({ side: pt[0], server: pt[1], t: pt[2] })),
        };
    }

    function stripServeOrder(config) {
        const c = Object.assign({}, config);
        delete c.serveOrder;
        return c;
    }

    // ---------------------------------------------------------------
    // 3) Il replay
    // ---------------------------------------------------------------

    function engineConfig(config) {
        return {
            mode: config.mode === 'GAMES' ? 'games' : 'points',
            goldenPoint: config.deuce === 'GOLDEN_POINT',
            killerPoint: config.deuce === 'KILLER_POINT',
            sets: config.sets,
            tiebreak: !!config.tieBreak,
            gamesPerSet: config.gamesPerSet,
            // La chiave che accende il tie-break vero in LiveScoring.
            tieBreakTo: config.tieBreak ? config.tieBreakTo : 0,
        };
    }

    // Rigioca i punti. Per ogni punto dice com'era il punteggio PRIMA e che
    // cosa c'era in palio (palla break, set point, match point, punto
    // decisivo), e raccoglie game e set con chi serviva e quanto sono durati.
    function replay(log) {
        const LS = window.LiveScoring;
        const cfg = engineConfig(log.config);
        const sideOf = new Map(log.players.map(p => [p.id, p.side]));
        let state = LS.initState(cfg, [{ players: [] }, { players: [] }]);

        const points = [];
        const games = [];
        const sets = [];
        const won = [0, 0];
        let pointsAfterEnd = false;
        let setNo = 0;
        let game = null;
        let gameNo = 0;
        let lastGameEndT = log.points.length && log.points[0].t != null ? log.points[0].t : null;
        let lastSetEndT = lastGameEndT;
        let setGames = []; // punteggio in game del set in corso dopo ogni game, per le rimonte

        log.points.forEach((p, i) => {
            if (state.status === 'ended') { pointsAfterEnd = true; return; }
            const team = p.side - 1;
            const inTieBreak = !!state.tieBreak;
            const serverSide = p.server != null && sideOf.has(p.server) ? sideOf.get(p.server) : null;

            // Che cosa c'era in palio: si prova a dare il punto a ciascuno dei due.
            const probe = [LS.applyScore(state, cfg, 0), LS.applyScore(state, cfg, 1)];
            const gameFor = probe.map(r => r.gameWon);
            const setPointFor = probe.map(r => r.setWon);
            const matchPointFor = probe.map(r => r.matchWon);
            const deciding = cfg.mode === 'points' && !inTieBreak && gameFor[0] && gameFor[1];
            const receiver = serverSide ? 3 - serverSide : null;
            const breakPoint = cfg.mode === 'points' && !inTieBreak && receiver != null && gameFor[receiver - 1];

            const before = {
                points: state.points.slice(), games: state.games.slice(), sets: state.sets.slice(),
                tieBreak: state.tieBreak ? state.tieBreak.slice() : null,
            };
            const r = LS.applyScore(state, cfg, team);
            state = r.state;
            won[team]++;

            if (!game) {
                game = {
                    no: gameNo, set: setNo, index: before.games[0] + before.games[1],
                    server: p.server, serverSide, tieBreak: inTieBreak,
                    firstPoint: i, pointsWon: [0, 0], startT: lastGameEndT,
                };
            }
            game.pointsWon[team]++;

            points.push({
                i, side: p.side, server: p.server, serverSide, t: p.t,
                set: setNo, game: gameNo, inTieBreak, before,
                gameWon: r.gameWon, setWon: r.setWon, matchWon: r.matchWon,
                deciding, breakPoint, setPointFor, matchPointFor,
                won: won.slice(), diff: won[0] - won[1],
            });

            if (r.gameWon) {
                const endT = p.t;
                const gamesAfter = r.setWon ? state.setHistory[state.setHistory.length - 1].slice() : state.games.slice();
                game.winner = p.side;
                game.score = inTieBreak ? r.tieBreakScore.slice() : game.pointsWon.slice();
                game.gamesAfter = gamesAfter;
                game.hold = !inTieBreak && game.serverSide ? game.serverSide === p.side : null;
                game.endT = endT;
                // Dal primo all'ultimo punto del game: le pause fra un game e
                // l'altro (cambio campo, fine set) non sono tempo di gioco.
                const firstT = log.points[game.firstPoint].t;
                game.durationMs = endT != null && firstT != null ? endT - firstT : null;
                game.lastPoint = i;
                games.push(game);
                setGames.push(gamesAfter);
                if (endT != null) lastGameEndT = endT;
                game = null;
                gameNo++;

                if (r.setWon) {
                    sets.push({
                        no: setNo,
                        games: gamesAfter,
                        tieBreak: r.tieBreakScore ? r.tieBreakScore.slice() : null,
                        winner: p.side,
                        progress: setGames,
                        startT: lastSetEndT,
                        endT,
                        durationMs: endT != null && lastSetEndT != null ? endT - lastSetEndT : null,
                    });
                    if (endT != null) lastSetEndT = endT;
                    setGames = [];
                    setNo++;
                }
            }
        });

        const ended = state.status === 'ended';
        return {
            config: log.config,
            players: log.players,
            points,
            games,
            sets,
            // Il set in corso, se la partita si e' fermata prima della fine.
            current: ended ? null : {
                no: setNo, games: state.games.slice(), progress: setGames,
                tieBreak: state.tieBreak ? state.tieBreak.slice() : null,
                points: state.points.slice(),
                openGame: game ? { pointsWon: game.pointsWon.slice(), server: game.server, serverSide: game.serverSide } : null,
            },
            final: {
                closedSets: state.setHistory.map(s => s.slice()),
                setsWon: state.sets.slice(),
                ended,
                winnerTeam: state.winnerTeam || null,
            },
            pointsAfterEnd,
        };
    }

    // ---------------------------------------------------------------
    // 4) Verso create_match: le stesse regole del modulo "Inserisci match"
    // ---------------------------------------------------------------

    // set_scores = i set chiusi, piu' il set interrotto se non e' in parita'
    // (un set pari non ha un vincitore, e il modulo manuale lo rifiuta).
    // score_team1/2, come submitMatchInsert (js/ui.js) dalla 4.12.0: a set
    // unico i game del set (6-4), a piu' set i SET VINTI (2-1). La somma dei
    // game faceva di un 7-6 3-6 7-5 un 17-17, cioe' un pareggio per l'ELO.
    function toMatchResult(rp) {
        const sets = rp.final.closedSets.map(s => s.slice());
        let interruptedSet = null;
        if (!rp.final.ended && rp.current) {
            const g = rp.current.games;
            if (g[0] + g[1] > 0 && g[0] !== g[1]) {
                interruptedSet = g.slice();
                sets.push(interruptedSet);
            }
        }
        if (!sets.length) {
            return { ok: false, problem: problem('no_result', 'La partita si è fermata prima di avere un risultato (nessun set chiuso e quello in corso è in parità). Non c\'è niente da salvare nello storico.') };
        }
        const setsWon = [0, 0];
        sets.forEach(s => { if (s[0] > s[1]) setsWon[0]++; else if (s[1] > s[0]) setsWon[1]++; });
        const winner = rp.final.ended
            ? rp.final.winnerTeam
            : (setsWon[0] > setsWon[1] ? 1 : (setsWon[1] > setsWon[0] ? 2 : 0));
        const multiSet = sets.length > 1;
        return {
            ok: true,
            setScores: sets,
            s1: multiSet ? setsWon[0] : sets[0][0],
            s2: multiSet ? setsWon[1] : sets[0][1],
            winner,
            ended: rp.final.ended,
            interruptedSet,
        };
    }

    // I quattro giocatori nell'ordine di v2_matches: p1,p2 lato 1; p3,p4 lato
    // 2. Dentro la coppia si segue l'ordine di servizio se c'e' (A1 prima di
    // A2), altrimenti quello del roster.
    function orderedPlayers(file, mapping) {
        const order = file.config.serveOrder;
        const rank = (p) => { const k = order.indexOf(p.localId); return k < 0 ? 10 + file.players.indexOf(p) : k; };
        const bySide = (s) => file.players.filter(p => p.side === s).sort((a, b) => rank(a) - rank(b)).map(p => mapping[p.localId]);
        return bySide(1).concat(bySide(2));
    }

    // L'identificativo che impedisce il doppio import. v2: il matchId del
    // file. v1: un'impronta dei giocatori e dei punti, che per lo stesso
    // file e' sempre la stessa.
    function externalId(file) {
        if (file.matchId) return 'match:' + file.matchId;
        const basis = JSON.stringify({
            c: stripServeOrder(file.config),
            o: file.config.serveOrder,
            p: file.players.map(p => [p.localId, p.side, p.padelPlayerId]),
            t: file.timeline.map(pt => [pt.side, pt.server, pt.t]),
        });
        return 'v1:' + cyrb53(basis).toString(16) + '-' + file.timeline.length;
    }

    // Hash a 53 bit, deterministico e senza dipendenze (bryc, dominio pubblico).
    // Non serve a proteggere niente: solo a riconoscere lo stesso file.
    function cyrb53(str, seed = 0) {
        let h1 = 0xdeadbeef ^ seed, h2 = 0x41c6ce57 ^ seed;
        for (let i = 0; i < str.length; i++) {
            const ch = str.charCodeAt(i);
            h1 = Math.imul(h1 ^ ch, 2654435761);
            h2 = Math.imul(h2 ^ ch, 1597334677);
        }
        h1 = Math.imul(h1 ^ (h1 >>> 16), 2246822507) ^ Math.imul(h2 ^ (h2 >>> 13), 3266489909);
        h2 = Math.imul(h2 ^ (h2 >>> 16), 2246822507) ^ Math.imul(h1 ^ (h1 >>> 13), 3266489909);
        return 4294967296 * (2097151 & h2) + (h1 >>> 0);
    }

    // Data e ora proposte dal file (solo v2). Si prendono come scritte, con
    // l'offset di chi giocava: "le 21:15 di martedi'" restano tali anche per
    // chi importa da un altro fuso.
    function dateTimeFromFile(file) {
        const m = file.startedAt && /^(\d{4}-\d{2}-\d{2})T(\d{2}:\d{2})/.exec(file.startedAt);
        return m ? { date: m[1], time: m[2] } : { date: null, time: null };
    }

    function durationMs(log) {
        for (let i = log.points.length - 1; i >= 0; i--) {
            if (log.points[i].t != null) return log.points[i].t;
        }
        return null;
    }

    // Tutto quello che serve alla RPC import_scoreboard_match, in un colpo.
    function importArgs(file, mapping, extra) {
        const log = fromFile(file, mapping);
        const rp = replay(log);
        const result = toMatchResult(rp);
        if (!result.ok) return { ok: false, problem: result.problem };
        const ps = orderedPlayers(file, mapping);
        const e = extra || {};
        return {
            ok: true,
            result,
            params: {
                p_group: e.groupId,
                p_date: e.date,
                p1: ps[0], p2: ps[1], p3: ps[2], p4: ps[3],
                s1: result.s1, s2: result.s2, p_winner: result.winner,
                p_court_id: e.courtId || null,
                p_match_time: e.time || null,
                p_set_scores: result.setScores,
                p_external_id: externalId(file),
                p_format_version: file.formatVersion,
                p_app_version: file.appVersion,
                p_config: log.config,
                p_serve_order: log.serveOrder.every(x => x != null) ? log.serveOrder : [],
                p_points: log.points.map(pt => [pt.side, pt.server, pt.t]),
                p_started_at: file.startedAt,
                p_duration_ms: durationMs(log),
            },
        };
    }

    // ---------------------------------------------------------------
    // 5) Statistiche
    // ---------------------------------------------------------------

    function teamLabel(players, side) {
        return players.filter(p => p.side === side).map(p => p.name).join(' / ');
    }

    function stats(rp) {
        const players = rp.players;
        const serveKnown = rp.points.some(p => p.serverSide != null);
        const timeKnown = rp.points.length > 1 && rp.points.every(p => p.t != null);
        const pointsMode = rp.config.mode !== 'GAMES';

        // --- Servizio, per coppia e per giocatore
        const blank = () => ({ points: 0, won: 0, games: 0, held: 0 });
        const bySide = [blank(), blank()];
        const byPlayer = new Map(players.map(p => [p.id, Object.assign({ id: p.id, name: p.name, side: p.side }, blank())]));
        rp.points.forEach(pt => {
            if (pt.serverSide == null || pt.inTieBreak) return;
            const s = bySide[pt.serverSide - 1];
            const pl = byPlayer.get(pt.server);
            s.points++; if (pl) pl.points++;
            if (pt.side === pt.serverSide) { s.won++; if (pl) pl.won++; }
        });
        rp.games.forEach(g => {
            if (g.hold == null) return;
            const s = bySide[g.serverSide - 1];
            const pl = byPlayer.get(g.server);
            s.games++; if (pl) pl.games++;
            if (g.hold) { s.held++; if (pl) pl.held++; }
        });

        // --- Palle break, punti decisivi, set e match point
        const breaks = { chances: [0, 0], converted: [0, 0] };
        const deciding = { played: 0, won: [0, 0] };
        const setPointsSaved = [0, 0];
        const matchPointsSaved = [0, 0];
        rp.points.forEach(pt => {
            if (pt.breakPoint) {
                const rec = 3 - pt.serverSide;
                breaks.chances[rec - 1]++;
                if (pt.side === rec && pt.gameWon) breaks.converted[rec - 1]++;
            }
            if (pt.deciding) { deciding.played++; deciding.won[pt.side - 1]++; }
            [1, 2].forEach(s => {
                const other = 3 - s;
                // Chi vince un punto che all'altro avrebbe dato il set lo "annulla".
                if (pt.side === s && pt.setPointFor[other - 1] && !pt.matchPointFor[other - 1]) setPointsSaved[s - 1]++;
                if (pt.side === s && pt.matchPointFor[other - 1]) matchPointsSaved[s - 1]++;
            });
        });

        // --- Strisce di punti
        const streaks = [{ length: 0 }, { length: 0 }];
        let run = null;
        rp.points.forEach(pt => {
            if (run && run.side === pt.side) run.length++;
            else run = { side: pt.side, length: 1, set: pt.set, from: pt.before };
            if (run.length > streaks[pt.side - 1].length) streaks[pt.side - 1] = Object.assign({}, run);
        });

        // --- Rimonte: nei set, il massimo svantaggio in game recuperato da chi
        // l'ha vinto; nella partita, chi vince dopo aver perso il primo set.
        const comebacks = [];
        rp.sets.forEach(set => {
            const w = set.winner - 1, l = 1 - w;
            let worst = null;
            set.progress.forEach(g => {
                const deficit = g[l] - g[w];
                if (deficit >= 2 && (!worst || deficit > worst.deficit)) worst = { deficit, score: [g[w], g[l]] };
            });
            if (worst) comebacks.push({ set: set.no, side: set.winner, from: worst.score, to: [set.games[w], set.games[l]] });
        });
        const matchComeback = rp.final.ended && rp.sets.length > 1 && rp.sets[0].winner !== rp.final.winnerTeam;

        // --- Tempi
        let times = { known: false };
        if (timeKnown) {
            const intervals = [];
            for (let i = 1; i < rp.points.length; i++) intervals.push(rp.points[i].t - rp.points[i - 1].t);
            const timedGames = rp.games.filter(g => g.durationMs != null && !g.tieBreak);
            const longest = timedGames.reduce((a, g) => (!a || g.durationMs > a.durationMs ? g : a), null);
            times = {
                known: true,
                totalMs: rp.points[rp.points.length - 1].t - rp.points[0].t,
                sets: rp.sets.map(s => ({ no: s.no, games: s.games, durationMs: s.durationMs })),
                avgPointMs: intervals.length ? Math.round(intervals.reduce((a, b) => a + b, 0) / intervals.length) : null,
                avgGameMs: timedGames.length ? Math.round(timedGames.reduce((a, g) => a + g.durationMs, 0) / timedGames.length) : null,
                longestGame: longest,
            };
        }

        const totals = {
            points: rp.points.length ? rp.points[rp.points.length - 1].won.slice() : [0, 0],
            games: [rp.games.filter(g => g.winner === 1).length, rp.games.filter(g => g.winner === 2).length],
        };

        const result = {
            teams: [teamLabel(players, 1), teamLabel(players, 2)],
            pointsMode,
            totals,
            serve: { known: serveKnown && pointsMode, bySide, byPlayer: Array.from(byPlayer.values()) },
            breaks,
            deciding,
            setPointsSaved,
            matchPointsSaved,
            streaks,
            comebacks,
            matchComeback,
            times,
            momentum: rp.points.map(pt => ({ i: pt.i, diff: pt.diff, set: pt.set, gameWon: pt.gameWon, setWon: pt.setWon, deciding: pt.deciding, breakPoint: pt.breakPoint })),
        };
        result.moments = moments(rp, result);
        return result;
    }

    // Le frasi dei momenti chiave, dalla piu' pesante. Ognuna nasce da un
    // numero calcolato sopra: niente aggettivi che il dato non giustifica.
    function moments(rp, s) {
        const out = [];
        const team = (side) => s.teams[side - 1];
        const nth = (n) => `${n + 1}° set`;
        [1, 2].forEach(side => {
            const n = s.matchPointsSaved[side - 1];
            if (n) out.push({ kind: 'match_point', side, text: `${team(side)} annullano ${n === 1 ? 'un match point' : n + ' match point'}` });
        });
        if (s.matchComeback) {
            out.push({ kind: 'comeback', side: rp.final.winnerTeam, text: `${team(rp.final.winnerTeam)} vincono dopo aver perso il primo set` });
        }
        s.comebacks.forEach(c => {
            out.push({ kind: 'comeback', side: c.side, text: `Rimonta nel ${nth(c.set)}: ${team(c.side)} da ${c.from.join('-')} a ${c.to.join('-')}` });
        });
        rp.sets.forEach(set => {
            if (!set.tieBreak) return;
            const w = set.winner - 1;
            out.push({ kind: 'tiebreak', side: set.winner, text: `Tie-break del ${nth(set.no)} a ${team(set.winner)}, ${set.tieBreak[w]}-${set.tieBreak[1 - w]}` });
        });
        [1, 2].forEach(side => {
            const st = s.streaks[side - 1];
            if (s.pointsMode && st.length >= 5) out.push({ kind: 'streak', side, text: `${st.length} punti di fila per ${team(side)} nel ${nth(st.set)}` });
        });
        [1, 2].forEach(side => {
            const n = s.setPointsSaved[side - 1];
            if (n) out.push({ kind: 'set_point', side, text: `${team(side)} annullano ${n === 1 ? 'un set point' : n + ' set point'}` });
        });
        if (s.deciding.played >= 2) {
            const [a, b] = s.deciding.won;
            const side = a === b ? null : (a > b ? 1 : 2);
            out.push({
                kind: 'deciding', side,
                text: side
                    ? `Punti decisivi: ${team(side)} ne vincono ${Math.max(a, b)} su ${s.deciding.played}`
                    : `Punti decisivi: ${a} a testa su ${s.deciding.played}`,
            });
        }
        return out;
    }

    // "1 h 12 min", "38 min", "45 s"
    function formatDuration(ms) {
        if (ms == null || !isFinite(ms)) return '—';
        const totalSec = Math.round(ms / 1000);
        if (totalSec < 60) return `${totalSec} s`;
        const min = Math.round(totalSec / 60);
        if (min < 60) return `${min} min`;
        return `${Math.floor(min / 60)} h ${String(min % 60).padStart(2, '0')} min`;
    }

    window.MatchLog = {
        SUPPORTED_VERSIONS, MAX_POINTS,
        parse, resolvePlayers, fromFile, fromStored, replay, engineConfig,
        toMatchResult, orderedPlayers, externalId, dateTimeFromFile, durationMs,
        importArgs, stats, teamLabel, formatDuration,
    };
})();
