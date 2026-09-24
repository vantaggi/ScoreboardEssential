// =====================================================================
// js/match-log-ui.js — Import dal tabellone e finestra della Cronaca
//
// L'interfaccia sopra window.MatchLog (js/match-log.js, motore puro).
//   * Import (solo admin): dal modulo "Inserisci match" → file .json di
//     ScoreboardEssential → anteprima → RPC import_scoreboard_match.
//   * Cronaca (tutti): finestra con andamento, game per game, servizio,
//     tempi e momenti chiave. Si apre dallo storico partite (admin) e dalla
//     scheda del giocatore, tab Cronologia.
//
// UN DI PIU', NON UN OBBLIGO: tutto qui e' best-effort. Se la tabella
// v2_match_logs non c'e' ancora (migrazione 63 non applicata) o la lettura
// fallisce, la dashboard si comporta esattamente come prima: nessuna icona,
// nessuna sezione, nessun errore a schermo.
//
// Design system: window.showModal/hideModal, .im-select/.im-btn-*, token CSS,
// Heroicons, grafici da window.getChartDefaults(). I due lati usano --brand e
// --accent SOLO dentro le visualizzazioni (grafico e striscia dei game), che
// e' l'uso che DESIGN_SYSTEM.md §1 consente ad --accent.
// =====================================================================
(function () {
    'use strict';

    const ML = () => window.MatchLog;
    const esc = (s) => (window.escapeHTML ? window.escapeHTML(s) : String(s == null ? '' : s));
    const toast = (m, t) => { if (window.showToast) window.showToast(m, t); };
    const MAX_FILE_BYTES = 2 * 1024 * 1024;

    const ICON_LOG = 'M9 17v-2m3 2v-4m3 4v-6m2 10H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z';

    const UI = {
        file: null,        // file letto e valido (MatchLog.parse)
        problems: [],      // problemi del file, se non valido
        overrides: {},     // localId → v2_players.id scelto dall'admin
        form: { date: '', time: '', court: '' },
        saving: false,
        index: { gid: null, promise: null },
        chart: null,
    };

    // ---------------------------------------------------------------
    // Indice delle partite che hanno una cronaca (best-effort)
    // ---------------------------------------------------------------
    function loadLogIndex(force) {
        const gid = window.appState && window.appState.activeGroupId;
        const client = window.supabaseClient;
        if (!gid || !client || typeof client.from !== 'function') return Promise.resolve(new Set());
        if (!force && UI.index.gid === gid && UI.index.promise) return UI.index.promise;
        UI.index.gid = gid;
        UI.index.promise = (async () => {
            try {
                const { data, error } = await client.from('v2_match_logs').select('match_id').eq('group_id', gid);
                if (error || !Array.isArray(data)) return new Set();
                return new Set(data.map(r => r.match_id));
            } catch (e) {
                return new Set();
            }
        })();
        return UI.index.promise;
    }

    function playerNames() {
        const out = {};
        ((window.rawData && window.rawData.players) || []).forEach(p => { out[p.id] = p.name; });
        return out;
    }

    function findMatch(matchId) {
        return ((window.rawData && window.rawData.matches_history) || []).find(m => String(m.id) === String(matchId)) || null;
    }

    function formatDate(d) {
        const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(d || '');
        if (!m) return d || '';
        return new Date(+m[1], +m[2] - 1, +m[3]).toLocaleDateString('it-IT', { weekday: 'short', day: 'numeric', month: 'short', year: 'numeric' });
    }

    // Chiude le finestre UNA ALLA VOLTA e poi chiama fn. Non e' pignoleria:
    // hideModal consuma la voce di cronologia con history.back(), che e'
    // asincrono (js/history-nav.js). Chiudere e aprire nello stesso istante fa
    // arrivare il "back" dopo la nuova voce, la cronologia arretra una volta di
    // troppo e alla fine esce dall'app ricaricando la pagina.
    function closeThen(ids, fn) {
        const nav = window.navHistory;
        const next = () => {
            const id = ids.shift();
            if (!id) { fn(); return; }
            const el = document.getElementById(id);
            if (!el || el.classList.contains('hidden')) { next(); return; }
            const before = nav && nav._depth ? nav._depth() : 0;
            if (window.hideModal) window.hideModal(id);
            const issuedBack = nav && nav._depth ? nav._depth() < before : false;
            if (!issuedBack) { next(); return; }
            let done = false;
            const go = () => { if (done) return; done = true; window.removeEventListener('popstate', go); next(); };
            window.addEventListener('popstate', go);
            setTimeout(go, 600); // rete di sicurezza: un popstate che non arriva non blocca niente
        };
        next();
    }

    function setsText(sets) {
        return Array.isArray(sets) && sets.length ? sets.map(s => `${s[0]}-${s[1]}`).join('  ') : '';
    }

    // ---------------------------------------------------------------
    // Liste di partite: un bottone sulle sole partite con la cronaca.
    // Chi disegna una lista mette uno slot vuoto
    //   <span data-ml-match="ID" style="display:contents;"></span>
    // e poi chiama decorateHistory(contenitore). Vuoto, lo slot non occupa
    // spazio: le partite senza cronaca restano identiche a prima.
    //   * storico partite (admin): bottone icona, come gli altri della riga;
    //   * { compact: true }: etichetta "Cronaca", per le liste delle giornate
    //     (Classifica → giornata, Time Machine).
    // ---------------------------------------------------------------
    async function decorateHistory(listEl, opts) {
        if (!listEl) return;
        const ids = await loadLogIndex();
        if (!ids.size) return;
        const compact = !!(opts && opts.compact);
        listEl.querySelectorAll('[data-ml-match]').forEach(slot => {
            const id = slot.getAttribute('data-ml-match');
            if (!ids.has(id) || slot.firstChild) return;
            const icon = `<svg class="${compact ? 'w-4 h-4' : 'w-5 h-5'}" aria-hidden="true" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="${ICON_LOG}"></path></svg>`;
            slot.innerHTML = compact
                ? `<button type="button" onclick="window.openMatchLog('${esc(id)}')" aria-label="Apri la cronaca punto per punto" class="ml-open-chip">${icon}<span>Cronaca</span></button>`
                : `<button type="button" onclick="window.openMatchLog('${esc(id)}')" title="Apri la cronaca" aria-label="Apri la cronaca punto per punto" class="ml-open-icon">${icon}</button>`;
        });
    }

    // ---------------------------------------------------------------
    // Scheda giocatore: le sue partite con la cronaca
    // ---------------------------------------------------------------
    async function renderPlayerLogs(player) {
        const box = document.getElementById('modal-player-logs');
        if (!box || !player) return;
        box.hidden = true;
        box.innerHTML = '';
        const ids = await loadLogIndex();
        // La scheda puo' essere passata a un altro giocatore mentre si aspettava.
        const shown = (document.getElementById('player-modal-name') || {}).textContent;
        if (!ids.size || (shown && shown !== player.name)) return;
        const names = playerNames();
        const matches = ((window.rawData && window.rawData.matches_history) || [])
            .filter(m => ids.has(m.id) && [m.player1_id, m.player2_id, m.player3_id, m.player4_id].includes(player.id))
            .slice(0, 10);
        if (!matches.length) return;
        const pair = (a, b) => `${esc(names[a] || '?')} / ${esc(names[b] || '?')}`;
        box.innerHTML = `
            <div class="ml-player-head">
                <div class="section-label">Cronache</div>
                <span class="ml-player-hint">Registrate col tabellone</span>
            </div>
            <div class="ml-player-list">
                ${matches.map(m => `
                    <button type="button" class="ml-player-row" onclick="window.openMatchLog('${esc(m.id)}')">
                        <span class="ml-player-row-date">${esc(formatDate(m.match_date))}</span>
                        <span class="ml-player-row-teams">${pair(m.player1_id, m.player2_id)} <span class="ml-vs">vs</span> ${pair(m.player3_id, m.player4_id)}</span>
                        <span class="ml-player-row-score">${esc(setsText(m.set_scores) || `${m.score_team1}-${m.score_team2}`)}</span>
                    </button>`).join('')}
            </div>`;
        box.hidden = false;
    }

    // ---------------------------------------------------------------
    // Import
    // ---------------------------------------------------------------
    window.openScoreboardImport = function () {
        const input = document.getElementById('sbi-file');
        if (!input) return;
        input.value = '';
        input.click();
    };

    window.onScoreboardFile = async function (input) {
        const f = input && input.files && input.files[0];
        if (!f) return;
        UI.file = null; UI.problems = []; UI.overrides = {}; UI.saving = false;
        if (f.size > MAX_FILE_BYTES) {
            UI.problems = [{ code: 'too_big', message: 'Il file è troppo grande per essere una partita del tabellone. Controlla di aver scelto quello giusto.' }];
        } else {
            let text = '';
            try { text = await f.text(); } catch (e) { text = ''; }
            const res = ML().parse(text);
            if (res.ok) UI.file = res.file; else UI.problems = res.problems;
        }
        if (UI.file) {
            const dt = ML().dateTimeFromFile(UI.file);
            UI.form = {
                date: dt.date || (typeof window.serataDaRegistrare === 'function' ? window.serataDaRegistrare() : new Date().toISOString().slice(0, 10)),
                time: dt.time || '',
                court: '',
            };
        }
        // Sopra il modulo di inserimento, senza chiuderlo: "Annulla" riporta
        // li', e il tasto indietro del telefono fa lo stesso.
        renderImport();
        if (window.showModal) window.showModal('scoreboard-import-modal');
    };

    window.closeScoreboardImport = function () {
        if (window.hideModal) window.hideModal('scoreboard-import-modal');
    };

    window.sbiSetPlayer = function (localId, value) {
        UI.overrides[localId] = value;
        renderImport();
    };
    window.sbiSetField = function (key, value) {
        UI.form[key] = value;
    };

    function groupPlayers() {
        return ((window.rawData && window.rawData.players) || []).slice().sort((a, b) => a.name.localeCompare(b.name));
    }

    function renderImport() {
        const body = document.getElementById('sbi-body');
        const submit = document.getElementById('sbi-submit');
        if (!body) return;

        if (!UI.file) {
            if (submit) submit.hidden = true;
            body.innerHTML = `
                <div class="ml-problem" role="alert">
                    <div class="ml-problem-title">Questo file non si può importare</div>
                    <ul>${UI.problems.map(p => `<li>${esc(p.message)}</li>`).join('')}</ul>
                </div>
                <button type="button" class="im-btn-secondary" style="width:100%;" onclick="window.openScoreboardImport()">Scegli un altro file</button>`;
            return;
        }
        if (submit) {
            submit.hidden = false;
            submit.disabled = UI.saving;
            submit.textContent = UI.saving ? 'Importazione...' : 'Importa partita';
        }

        const file = UI.file;
        const players = groupPlayers();
        const res = ML().resolvePlayers(file, players.map(p => p.id), UI.overrides);
        const identity = {};
        file.players.forEach(p => { identity[p.localId] = p.localId; });
        const rp = ML().replay(ML().fromFile(file, identity));
        const result = ML().toMatchResult(rp);
        const duration = ML().durationMs(ML().fromFile(file, identity));

        const statusText = !result.ok
            ? result.problem.message
            : result.ended
                ? `Conclusa · ${rp.points.length} punti${duration != null ? ' · ' + ML().formatDuration(duration) : ''}`
                : `Interrotta${result.interruptedSet ? ' sul ' + result.interruptedSet.join('-') : ''}: si salva il risultato a quel punto · ${rp.points.length} punti`;

        // I giocatori si scelgono NELL'ORDINE DI SERVIZIO, quando il file ce l'ha:
        // 1° chi ha battuto il primo game (coppia 1), 2° il primo della coppia 2,
        // poi i due compagni. Il tabellone non conosce i giocatori della
        // dashboard (nomi suoi, "A", "B"... o quelli scelti dall'utente), quindi
        // e' l'admin a dire chi erano; il vecchio numero Padel Elite, se il file
        // lo porta ancora, precompila soltanto.
        const byLocal = new Map(file.players.map(p => [p.localId, p]));
        const serveOrder = file.config.serveOrder.length === 4 && file.config.serveOrder.every(id => byLocal.has(id))
            ? file.config.serveOrder.map(id => byLocal.get(id)) : null;
        const side = (s) => file.players.filter(p => p.side === s);
        const playerRow = (p, position) => {
            const chosen = res.mapping[p.localId] != null ? res.mapping[p.localId] : (UI.overrides[p.localId] || '');
            // Rosso solo se il file indicava un giocatore che qui non esiste: che
            // manchi il collegamento e' il caso normale, si sceglie e basta.
            const hint = res.unresolved.includes(p) && p.padelPlayerId != null
                ? `Il numero ${esc(p.padelPlayerId)} non è un giocatore di questo gruppo: sceglilo tu.` : '';
            const title = position
                ? `<span class="ml-map-pos">${position}° al servizio · Coppia ${p.side}</span> Nel tabellone: <strong>${esc(p.name)}</strong>`
                : `Nel tabellone: <strong>${esc(p.name)}</strong>`;
            return `
                <div class="ml-map-row">
                    <label class="ml-map-name" for="sbi-p-${p.localId}">${title}</label>
                    <select id="sbi-p-${p.localId}" class="im-select" onchange="window.sbiSetPlayer(${p.localId}, this.value)">
                        <option value="">— Scegli —</option>
                        ${players.map(gp => `<option value="${gp.id}" ${String(gp.id) === String(chosen) ? 'selected' : ''}>${esc(gp.name)}</option>`).join('')}
                    </select>
                    ${hint ? `<p class="ml-field-error">${hint}</p>` : ''}
                </div>`;
        };
        const courts = (window.rawData && window.rawData.courts) || [];

        body.innerHTML = `
            <div class="card-inner ml-preview">
                <div class="ml-preview-sets">${result.ok ? esc(result.setScores.map(s => s.join('-')).join('  ·  ')) : '—'}</div>
                <p class="ml-preview-status ${result.ok ? '' : 'ml-field-error'}">${esc(statusText)}</p>
            </div>
            <div>
                <div class="section-label" style="margin-bottom:4px;">Chi ha giocato</div>
                ${serveOrder ? `
                <p class="ml-note ml-map-intro">Scegli i giocatori <strong>nell'ordine in cui hanno servito</strong>: il 1° è chi ha battuto il primo game, il 2° è il primo a battere nella coppia avversaria, poi i due compagni. Così la cronaca sa chi serviva ogni punto.</p>
                <div class="ml-map-list">${serveOrder.map((p, i) => playerRow(p, i + 1)).join('')}</div>` : `
                <p class="ml-note ml-map-intro">Il tabellone non ha registrato l'ordine di servizio: scegli i giocatori per coppia. La cronaca non potrà dire chi serviva.</p>
                <div class="ml-map-grid">
                    <div class="ml-map-side"><div class="ml-map-side-label">Coppia 1</div>${side(1).map(p => playerRow(p)).join('')}</div>
                    <div class="ml-map-side"><div class="ml-map-side-label">Coppia 2</div>${side(2).map(p => playerRow(p)).join('')}</div>
                </div>`}
                ${res.duplicated ? '<p class="ml-field-error">Lo stesso giocatore è scelto due volte.</p>' : ''}
            </div>
            <div class="ml-form-grid">
                <div>
                    <label class="section-label" for="sbi-date" style="display:block;margin-bottom:4px;">Data</label>
                    <input type="date" id="sbi-date" class="im-select" value="${esc(UI.form.date)}" onchange="window.sbiSetField('date', this.value)">
                </div>
                <div>
                    <label class="section-label" for="sbi-time" style="display:block;margin-bottom:4px;">Orario</label>
                    <input type="time" id="sbi-time" class="im-select" value="${esc(UI.form.time)}" onchange="window.sbiSetField('time', this.value)">
                </div>
                <div class="ml-form-wide">
                    <label class="section-label" for="sbi-court" style="display:block;margin-bottom:4px;">Campo</label>
                    <select id="sbi-court" class="im-select" onchange="window.sbiSetField('court', this.value)">
                        <option value="">Nessun campo</option>
                        ${courts.map(c => `<option value="${esc(c.id)}" ${String(c.id) === String(UI.form.court) ? 'selected' : ''}>${esc(c.name)}</option>`).join('')}
                    </select>
                </div>
            </div>
            <p class="ml-note">Nello storico la partita conta come le altre: punteggio in game, come nell'inserimento a mano. In più avrà la cronaca punto per punto.</p>`;
    }

    window.submitScoreboardImport = async function () {
        if (UI.saving || !UI.file) return;
        const file = UI.file;
        const players = groupPlayers();
        const res = ML().resolvePlayers(file, players.map(p => p.id), UI.overrides);
        if (!res.complete) {
            toast(res.duplicated ? 'Lo stesso giocatore è scelto due volte' : 'Scegli tutti e quattro i giocatori', 'warning');
            return;
        }
        const date = UI.form.date;
        if (!/^\d{4}-\d{2}-\d{2}$/.test(date || '')) {
            toast('Scegli la data della partita', 'warning');
            return;
        }
        const time = /^\d{2}:\d{2}$/.test(UI.form.time || '') ? UI.form.time : null;
        const gid = window.appState && window.appState.activeGroupId;
        if (!window.supabaseClient || !gid) {
            toast('Supabase non disponibile', 'error');
            return;
        }
        const built = ML().importArgs(file, res.mapping, { groupId: gid, date, time, courtId: UI.form.court || null });
        if (!built.ok) {
            toast(built.problem.message, 'warning');
            return;
        }

        UI.saving = true;
        renderImport();
        let data = null, error = null;
        try {
            ({ data, error } = await window.supabaseClient.rpc('import_scoreboard_match', built.params));
        } catch (e) {
            error = e;
        }
        UI.saving = false;

        if (error || !data || !data.match) {
            renderImport();
            const msg = window.matchRpcErrorMessage ? window.matchRpcErrorMessage(error || {}) : ('Errore: ' + ((error && error.message) || 'risposta vuota'));
            toast(msg, 'error');
            return;
        }

        const match = data.match;
        UI.index.promise = null; // la prossima lettura dell'indice vede la partita nuova
        if (data.already_imported) {
            toast('Questa partita era già stata importata: nello storico non cambia niente.', 'info');
        } else {
            toast('Partita importata, con la cronaca', 'success');
            if (typeof window.addNotification === 'function') {
                const names = playerNames();
                window.addNotification('⚡ Match Aggiornato',
                    `${names[match.player1_id] || '?'} & C. vs ${names[match.player3_id] || '?'} & C. — ${setsText(match.set_scores)}`, 'match');
            }
            if (window.loadCloudData) window.loadCloudData();
        }
        // La conferma migliore e' vederla: si apre la cronaca appena salvata,
        // dopo aver chiuso import e modulo di inserimento.
        closeThen(['scoreboard-import-modal', 'insert-match-modal'], () => {
            window.openMatchLog(match.id, {
                match,
                log: { config: built.params.p_config, serve_order: built.params.p_serve_order, points: built.params.p_points },
            });
        });
    };

    // ---------------------------------------------------------------
    // Cronaca
    // ---------------------------------------------------------------
    window.closeMatchLog = function () {
        if (UI.chart) { try { UI.chart.destroy(); } catch (e) { /* gia' distrutto */ } UI.chart = null; }
        if (window.hideModal) window.hideModal('match-log-modal');
    };

    // preload: { match, log } per non rileggere dal database cio' che si ha gia'.
    window.openMatchLog = async function (matchId, preload) {
        const body = document.getElementById('ml-body');
        const subtitle = document.getElementById('ml-subtitle');
        if (!body) return;
        if (UI.chart) { try { UI.chart.destroy(); } catch (e) { /* gia' distrutto */ } UI.chart = null; }
        body.innerHTML = `<div class="ml-loading"><div class="ml-spinner"></div>Caricamento della cronaca...</div>`;
        if (subtitle) subtitle.textContent = '';
        if (window.showModal) window.showModal('match-log-modal');

        const match = (preload && preload.match) || findMatch(matchId);
        let row = preload && preload.log;
        if (!row) {
            try {
                const { data, error } = await window.supabaseClient.from('v2_match_logs').select('*').eq('match_id', matchId).maybeSingle();
                if (!error) row = data;
            } catch (e) { row = null; }
        }
        if (!match || !row) {
            body.innerHTML = `
                <div class="ml-empty">
                    <p class="ml-empty-title">Cronaca non disponibile</p>
                    <p>Questa partita non ha una cronaca, oppure non è stato possibile leggerla. Il risultato resta nello storico come sempre.</p>
                </div>`;
            return;
        }
        renderLog(match, row);
    };

    function renderLog(match, row) {
        const body = document.getElementById('ml-body');
        const subtitle = document.getElementById('ml-subtitle');
        const log = ML().fromStored(row, match, playerNames());
        const rp = ML().replay(log);
        const st = ML().stats(rp);

        const court = match.court_id && typeof window.getCourtName === 'function' ? window.getCourtName(match.court_id) : null;
        const meta = [formatDate(match.match_date), court, match.match_time, st.times.known ? ML().formatDuration(st.times.totalMs) : null].filter(Boolean);
        if (subtitle) subtitle.textContent = meta.join(' · ');

        body.innerHTML = [
            scoreboardHtml(rp, st),
            section('Andamento', momentumHtml(st)),
            section('Game per game', gamesHtml(rp, st)),
            section('Servizio', serveHtml(st)),
            section('Tempi', timesHtml(st)),
            section('Momenti chiave', momentsHtml(st)),
        ].join('');

        drawMomentum(rp, st);
    }

    function section(title, inner) {
        return `<section class="card-inner ml-section"><div class="section-label ml-section-title">${esc(title)}</div>${inner}</section>`;
    }

    function scoreboardHtml(rp, st) {
        const sets = rp.sets.map(s => ({ games: s.games, tb: s.tieBreak }));
        if (rp.current && (rp.current.games[0] + rp.current.games[1] > 0)) sets.push({ games: rp.current.games, tb: null, open: true });
        const winner = rp.final.ended ? rp.final.winnerTeam : null;
        const rowHtml = (side) => `
            <div class="ml-score-row ${winner === side ? 'ml-score-row--win' : ''}">
                <span class="ml-score-dot ml-side-${side}" aria-hidden="true"></span>
                <span class="ml-score-team">${esc(st.teams[side - 1])}</span>
                ${sets.map(s => `<span class="ml-score-set ${s.open ? 'ml-score-set--open' : ''}">${s.games[side - 1]}${s.tb ? `<sup>${s.tb[side - 1]}</sup>` : ''}</span>`).join('')}
            </div>`;
        const badge = rp.final.ended ? '' : '<span class="ml-badge">Interrotta</span>';
        return `
            <div class="ml-score">
                ${rowHtml(1)}${rowHtml(2)}
                <div class="ml-score-foot">
                    ${badge}
                    <span>Punti vinti <strong>${st.totals.points[0]}</strong> – <strong>${st.totals.points[1]}</strong></span>
                    <span>Game <strong>${st.totals.games[0]}</strong> – <strong>${st.totals.games[1]}</strong></span>
                </div>
            </div>`;
    }

    function momentumHtml(st) {
        return `
            <div class="ml-chart-wrap"><canvas id="ml-momentum" aria-label="Andamento della partita punto per punto" role="img"></canvas></div>
            <p class="ml-caption"><span class="ml-key ml-side-1"></span>Sopra la linea: ${esc(st.teams[0])} avanti nei punti vinti · <span class="ml-key ml-side-2"></span>Sotto: ${esc(st.teams[1])}. Le linee tratteggiate separano i set.</p>`;
    }

    function drawMomentum(rp, st) {
        const canvas = document.getElementById('ml-momentum');
        if (!canvas) return;
        if (typeof window.Chart === 'undefined' || typeof window.getChartDefaults !== 'function') {
            const wrap = canvas.parentElement;
            wrap.classList.add('ml-chart-wrap--none');
            wrap.innerHTML = '<p class="ml-caption">Il grafico non si è caricato: controlla la connessione e riapri la cronaca.</p>';
            return;
        }
        const css = (n) => (window.getCSS ? window.getCSS(n) : '');
        const brandRgb = css('--brand-rgb'), accentRgb = css('--accent-rgb');
        const setEnds = rp.points.filter(p => p.setWon && !p.matchWon).map(p => p.i);
        const setLines = {
            id: 'mlSetLines',
            afterDatasetsDraw(chart) {
                const { ctx, chartArea, scales } = chart;
                ctx.save();
                ctx.strokeStyle = css('--border-strong');
                ctx.setLineDash([4, 4]);
                ctx.lineWidth = 1;
                setEnds.forEach(i => {
                    const x = scales.x.getPixelForValue(i + 0.5);
                    ctx.beginPath(); ctx.moveTo(x, chartArea.top); ctx.lineTo(x, chartArea.bottom); ctx.stroke();
                });
                ctx.restore();
            },
        };
        const opts = window.getChartDefaults('line', {});
        opts.elements = { line: { tension: 0, fill: false }, point: { radius: 0, hoverRadius: 4 } };
        opts.interaction = { mode: 'index', intersect: false };
        opts.scales.x.ticks = Object.assign({}, opts.scales.x.ticks, { maxTicksLimit: 6, maxRotation: 0 });
        opts.scales.x.grid = Object.assign({}, opts.scales.x.grid, { display: false });
        opts.scales.y.ticks = Object.assign({}, opts.scales.y.ticks, { precision: 0 });
        opts.scales.y.grid = Object.assign({}, opts.scales.y.grid, {
            color: (c) => (c.tick && c.tick.value === 0 ? css('--border-strong') : css('--border-main')),
        });
        opts.plugins.tooltip = Object.assign({}, opts.plugins.tooltip, {
            callbacks: {
                title: (items) => {
                    const p = rp.points[items[0].dataIndex];
                    return `Punto ${p.i + 1} · ${p.set + 1}° set${p.inTieBreak ? ', tie-break' : ''}`;
                },
                label: (item) => {
                    const p = rp.points[item.dataIndex];
                    const lead = p.diff === 0 ? 'Punti vinti pari' : `${st.teams[p.diff > 0 ? 0 : 1]} +${Math.abs(p.diff)}`;
                    return [`Punto a ${st.teams[p.side - 1]}`, lead];
                },
            },
        });
        UI.chart = new window.Chart(canvas.getContext('2d'), {
            type: 'line',
            data: {
                labels: rp.points.map(p => p.i + 1),
                datasets: [{
                    data: st.momentum.map(m => m.diff),
                    borderWidth: 2,
                    borderColor: css('--text-secondary'),
                    segment: {
                        borderColor: (c) => (c.p1.parsed.y > 0 || (c.p1.parsed.y === 0 && c.p0.parsed.y > 0)
                            ? `rgb(${brandRgb})` : c.p1.parsed.y < 0 || c.p0.parsed.y < 0 ? `rgb(${accentRgb})` : css('--text-secondary')),
                    },
                    fill: { target: 'origin', above: `rgb(${brandRgb} / 0.15)`, below: `rgb(${accentRgb} / 0.12)` },
                }],
            },
            options: opts,
            plugins: [setLines],
        });
    }

    function gamesHtml(rp, st) {
        const all = rp.sets.map(s => ({ no: s.no, games: s.games, closed: true }));
        if (rp.current && rp.current.progress.length) all.push({ no: rp.current.no, games: rp.current.games, closed: false });
        if (!all.length) return '<p class="ml-caption">Nessun game concluso.</p>';
        const rows = all.map(s => {
            const games = rp.games.filter(g => g.set === s.no);
            const chips = games.map(g => {
                const who = st.teams[g.winner - 1];
                const brk = g.hold === false;
                const label = g.tieBreak ? `TB ${g.score[g.winner - 1]}-${g.score[2 - g.winner]}` : g.gamesAfter.join('-');
                const title = `${who} vince il game${g.tieBreak ? ' (tie-break)' : ''}${brk ? ', break' : ''}${g.durationMs != null ? ' · ' + ML().formatDuration(g.durationMs) : ''}`;
                return `<span class="ml-game ml-game--${g.winner} ${brk ? 'ml-game--break' : ''}" title="${esc(title)}">${esc(label)}${brk ? '<em>B</em>' : ''}</span>`;
            }).join('');
            return `<div class="ml-games-row"><span class="ml-games-set">${s.no + 1}° set${s.closed ? '' : ' (in corso)'}</span><div class="ml-games-chips">${chips}</div></div>`;
        }).join('');
        const legend = st.serve.known
            ? '<p class="ml-caption">Il colore dice chi ha vinto il game, il numero è il punteggio dopo il game. <strong>B</strong> = break: il game l\'ha vinto chi riceveva.</p>'
            : '<p class="ml-caption">Il colore dice chi ha vinto il game, il numero è il punteggio dopo il game.</p>';
        return rows + legend;
    }

    const pct = (a, b) => (b ? Math.round((a / b) * 100) + '%' : '—');

    function serveHtml(st) {
        if (!st.serve.known) {
            return `<div class="ml-empty"><p>Il tabellone non sapeva chi serviva: imposta l'ordine di servizio a inizio partita e qui vedrai punti e game vinti al servizio da ciascuno.</p></div>`;
        }
        const rows = st.serve.byPlayer.slice().sort((a, b) => a.side - b.side).map(p => `
            <tr>
                <td><span class="ml-key ml-side-${p.side}"></span>${esc(p.name)}</td>
                <td class="num">${p.won}/${p.points} <span class="ml-pct">${pct(p.won, p.points)}</span></td>
                <td class="num">${p.held}/${p.games}</td>
            </tr>`).join('');
        const brk = [1, 2].map(s => `<li><span class="ml-key ml-side-${s}"></span>${esc(st.teams[s - 1])}: palle break convertite <strong>${st.breaks.converted[s - 1]}</strong> su ${st.breaks.chances[s - 1]}</li>`).join('');
        return `
            <div class="ml-table-wrap">
                <table class="ml-table">
                    <thead><tr><th>Al servizio</th><th class="num">Punti vinti</th><th class="num">Game tenuti</th></tr></thead>
                    <tbody>${rows}</tbody>
                </table>
            </div>
            <ul class="ml-list">${brk}</ul>`;
    }

    function timesHtml(st) {
        const t = st.times;
        if (!t.known) {
            return `<div class="ml-empty"><p>Il tabellone non ha registrato i tempi di questa partita.</p></div>`;
        }
        const lg = t.longestGame;
        const items = [
            ['Durata', ML().formatDuration(t.totalMs)],
            ...t.sets.map(s => [`${s.no + 1}° set (${s.games.join('-')})`, ML().formatDuration(s.durationMs)]),
            ['Tra un punto e l\'altro', ML().formatDuration(t.avgPointMs)],
            ['Game medio', ML().formatDuration(t.avgGameMs)],
        ];
        const longest = lg ? `<p class="ml-caption">Il game più lungo: ${ML().formatDuration(lg.durationMs)} e ${lg.pointsWon[0] + lg.pointsWon[1]} punti nel ${lg.set + 1}° set, vinto da ${esc(st.teams[lg.winner - 1])} (${esc(lg.gamesAfter.join('-'))}).</p>` : '';
        return `<dl class="ml-kv">${items.map(([k, v]) => `<div><dt>${esc(k)}</dt><dd>${esc(v)}</dd></div>`).join('')}</dl>${longest}`;
    }

    function momentsHtml(st) {
        if (!st.moments.length) {
            return `<div class="ml-empty"><p>Partita senza sussulti: nessuna rimonta, nessun match point annullato, nessun tie-break.</p></div>`;
        }
        return `<ul class="ml-list">${st.moments.map(m => `<li>${m.side ? `<span class="ml-key ml-side-${m.side}"></span>` : ''}${esc(m.text)}</li>`).join('')}</ul>`;
    }

    window.MatchLogUI = { loadLogIndex, decorateHistory, renderPlayerLogs };
})();
