'use strict';
/* Staff Console; Watchdog (anti-cheat) is one of its views. All server text is rendered with textContent; nothing from the API becomes markup. */

const $ = (selector, root = document) => root.querySelector(selector);
const $$ = (selector, root = document) => [...root.querySelectorAll(selector)];

function h(tag, props, ...children) {
  const el = document.createElement(tag);
  if (props) {
    for (const [key, value] of Object.entries(props)) {
      if (value == null || value === false) continue;
      if (key === 'class') el.className = value;
      else if (key === 'on') for (const [type, fn] of Object.entries(value)) el.addEventListener(type, fn);
      else if (key === 'style') for (const [prop, v] of Object.entries(value)) el.style.setProperty(prop, v);
      else if (key === 'text') el.textContent = value;
      else el.setAttribute(key, value === true ? '' : value);
    }
  }
  for (const child of children.flat(Infinity)) {
    if (child == null || child === false) continue;
    el.append(child instanceof Node ? child : String(child));
  }
  return el;
}
const SVG = 'http://www.w3.org/2000/svg';
function svg(tag, attrs = {}, ...children) {
  const el = document.createElementNS(SVG, tag);
  for (const [key, value] of Object.entries(attrs)) el.setAttribute(key, value);
  el.append(...children);
  return el;
}
function icon(path) {
  return svg('svg', { viewBox: '0 0 24 24', 'aria-hidden': 'true' }, svg('path', { d: path }));
}

/* ---------- Formatting ---------- */
const stamp = (value) => typeof value === 'number' ? value : Date.parse(value);
const DATE_STYLES = { day: { month: 'short', day: 'numeric', year: 'numeric' }, month: { month: 'long', year: 'numeric' } };
/** The one calendar-date format: "Oct 17, 2026", or "October 2026" with style 'month'. */
function date(value, style = 'day') {
  const t = stamp(value);
  return !t || Number.isNaN(t) ? '—' : new Date(t).toLocaleDateString(undefined, DATE_STYLES[style]);
}
function ago(value) {
  const t = stamp(value);
  if (!t || Number.isNaN(t)) return 'never';
  const s = Math.round((Date.now() - t) / 1000);
  if (s < 45) return 'just now';
  if (s < 3600) return `${Math.round(s / 60)}m ago`;
  if (s < 86400) return `${Math.round(s / 3600)}h ago`;
  if (s < 86400 * 30) return `${Math.round(s / 86400)}d ago`;
  return date(t);
}
function until(value) {
  const t = stamp(value);
  const s = Math.round((t - Date.now()) / 1000);
  if (s <= 0) return 'ending now';
  if (s < 3600) return `in ${Math.max(1, Math.round(s / 60))}m`;
  if (s < 86400) return `in ${Math.round(s / 3600)}h`;
  return `in ${Math.round(s / 86400)}d`;
}
function clock(value) {
  const d = new Date(stamp(value));
  const today = new Date().toDateString() === d.toDateString();
  return today ? d.toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' })
    : d.toLocaleString(undefined, { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' });
}
function span(ms) {
  const m = Math.floor(ms / 60000);
  if (m < 60) return `${m}m`;
  const hrs = Math.floor(m / 60);
  if (hrs < 48) return `${hrs}h ${m % 60}m`;
  return `${Math.floor(hrs / 24)}d ${hrs % 24}h`;
}
const fmt = (n, digits = 0) => n == null || Number.isNaN(n) ? '—' : Number(n).toLocaleString(undefined, { minimumFractionDigits: digits, maximumFractionDigits: digits });
const pretty = (text) => !text ? '' : text.toLowerCase().split(/[_-]/).join(' ').replace(/^./, (c) => c.toUpperCase());
const plural = (n, one, many = `${one}s`) => `${fmt(n)} ${n === 1 ? one : many}`;
function hue(uuid) {
  let x = 0x811c9dc5; // FNV-1a, so UUIDs that differ in one digit still get distinct colours
  for (const c of uuid || '') { x ^= c.charCodeAt(0); x = Math.imul(x, 0x01000193); }
  return (x >>> 0) % 360;
}

/* ---------- State ---------- */
const S = {
  data: null, csrf: null, user: null, view: 'overview', failures: 0,
  playersScope: 'online', reportsScope: 'OPEN', geoScope: 'all',
  drawer: { uuid: null, name: '', tab: 'profile', detail: null, at: 0 },
  newestAlert: 0, timer: null,
};

const VIEWS = [
  { id: 'overview', label: 'Overview', sub: 'Server health, live alerts and everything waiting on staff.', icon: 'M4 4h6v8H4zM14 4h6v5h-6zM14 13h6v7h-6zM4 16h6v4H4z' },
  { id: 'players', label: 'Players', sub: 'Everyone online right now, plus every account that has joined.', icon: 'M15.5 19v-1.4a3.6 3.6 0 0 0-3.6-3.6H7.1a3.6 3.6 0 0 0-3.6 3.6V19M9.5 10.5a3 3 0 1 0 0-6 3 3 0 0 0 0 6M20.5 19v-1.3a3.4 3.4 0 0 0-2.6-3.3M15.5 4.6a3 3 0 0 1 0 5.8' },
  { id: 'locations', label: 'Locations', sub: 'Where players connect from, by each account\u2019s last-known country, region and city.', icon: 'M12 21s-6.5-5.6-6.5-11a6.5 6.5 0 0 1 13 0c0 5.4-6.5 11-6.5 11ZM12 12.5a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5' },
  { id: 'alerts', label: 'Watchdog', sub: 'Anti-cheat evidence, live suspicion scores and what Watchdog did about them.', icon: 'M12 3 5 5.8v5.4c0 4.4 3 7.9 7 9.3 4-1.4 7-4.9 7-9.3V5.8L12 3ZM8.5 12.5h2l1-2.5 1.7 4.5 1.1-2h1.7' },
  { id: 'reports', label: 'Reports', sub: 'Player reports filed with /report.', icon: 'M5 21V4M5 4h12l-2 4 2 4H5' },
  { id: 'sanctions', label: 'Sanctions', sub: 'Active bans, mutes and freezes.', icon: 'M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18ZM5.7 5.7l12.6 12.6' },
  { id: 'server', label: 'Server', sub: 'Performance over the last hour and runtime details.', icon: 'M4 4.5h16v6H4zM4 13.5h16v6H4zM8 7.5h.01M8 16.5h.01' },
  { id: 'audit', label: 'Audit log', sub: 'Every staff action, in game or from this console.', icon: 'M9 6h11M9 12h11M9 18h11M4.5 6h.01M4.5 12h.01M4.5 18h.01' },
  { id: 'staff', label: 'Staff', sub: 'Who holds staff access and how it changed.', icon: 'M12 3 4.5 6.5v5c0 4.4 3.2 8 7.5 9.5 4.3-1.5 7.5-5.1 7.5-9.5v-5L12 3ZM9 12l2 2 4-4' },
];

/* ---------- Network ---------- */
async function api(path, options = {}) {
  const response = await fetch(path, { cache: 'no-store', credentials: 'same-origin', ...options });
  let body = null;
  try { body = await response.json(); } catch { /* non-JSON error page */ }
  if (!response.ok) {
    const error = new Error(body?.error || `The server answered ${response.status}.`);
    error.status = response.status;
    throw error;
  }
  return body;
}
async function loadSession() {
  const session = await api('/api/session');
  S.csrf = session.csrf; S.user = session.user;
  $('#whoami').textContent = `Signed in as ${session.user}`;
}
async function act(payload, retried = false) {
  try {
    return await api('/api/action', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'X-Watchdog-Token': S.csrf || '' },
      body: JSON.stringify(payload),
    });
  } catch (error) {
    // The token rotates when the server restarts; fetch the new one and try once more.
    if (error.status === 403 && !retried) { await loadSession(); return act(payload, true); }
    throw error;
  }
}
async function refresh() {
  clearTimeout(S.timer);
  try {
    const data = await api('/api/status');
    S.failures = 0;
    setLink('live', 'Live');
    if (data.status === 'online') {
      announceAlerts(data);
      S.data = data;
      renderAll();
      if (S.drawer.uuid && Date.now() - S.drawer.at > 15000) loadDrawer(true);
    }
  } catch (error) {
    S.failures++;
    setLink('lost', error.status === 401 ? 'Signed out' : 'Connection lost');
    $('#updated').textContent = `${error.message} Showing the last data received; retrying.`;
  } finally {
    S.timer = setTimeout(refresh, document.hidden ? 20000 : Math.min(30000, 5000 * Math.max(1, S.failures)));
  }
}
function setLink(state, label) {
  const el = $('#link-state');
  el.dataset.state = state;
  $('.label', el).textContent = label;
}

/* ---------- Shared pieces ---------- */
function avatar(name, uuid, online, large) {
  const initials = (name || '?').replace(/[^A-Za-z0-9]/g, '').slice(0, 2).toUpperCase() || '?';
  const el = h('span', { class: `avatar${large ? ' large' : ''}`, 'aria-hidden': 'true', style: { '--h': String(hue(uuid)) } }, initials);
  if (online != null) el.append(h('span', { class: `presence${online ? ' on' : ''}` }));
  return el;
}
function who(name, uuid, meta, online) {
  return h('div', { class: 'who' }, avatar(name, uuid, online),
    h('div', { class: 'shrink' }, h('div', { class: 'name' }, name || 'Unknown'), meta ? h('div', { class: 'meta' }, meta) : null));
}
function playerLink(name, uuid) {
  if (!uuid) return h('span', null, name || 'Unknown');
  return h('button', { type: 'button', class: 'inline-link', on: { click: (e) => { e.stopPropagation(); openDrawer(uuid, name); } } }, name || 'Unknown');
}
function badge(text, tone) { return h('span', { class: `badge${tone ? ` ${tone}` : ''}` }, text); }
const CHECK_TONE = { SPEED: 'amber', FLIGHT: 'blue', REACH: 'red', COMBAT_RATE: 'red', FAST_BREAK: 'violet', TIMER: 'red', BAD_PACKETS: 'red', AUTOCLICKER: 'violet', HITBOX: 'red', MODEL: 'blue' };
const OUTCOME = { KICK: ['Kicked', 'red'], AI_KICK: ['Kicked by model', 'red'], AI_BAN: ['Banned by model', 'red'], AI_FLAG: ['Model flag', 'amber'] };
const outcomeBadge = (action) => OUTCOME[action] ? badge(...OUTCOME[action]) : badge('Logged');
const pct = (p, digits = 0) => p == null || p < 0 ? '—' : `${fmt(p * 100, digits)}%`;
const ACTION_TONE = { BAN: 'red', KICK: 'red', WARNING: 'amber', MUTE: 'amber', FREEZE: 'blue', UNBAN: 'green', UNMUTE: 'green', UNFREEZE: 'green', REPORT: 'violet', REPORT_FILED: 'violet', REPORT_RESOLVED: 'green', NOTE: '' };
function meter(value, max, tones = [0.4, 0.75]) {
  const ratio = Math.max(0, Math.min(1, max ? value / max : 0));
  const tone = ratio >= tones[1] ? 'bad' : ratio >= tones[0] ? 'warn' : 'good';
  return h('div', { class: 'meter', 'data-tone': tone },
    h('div', { class: 'track' }, h('div', { class: 'fill', style: { '--w': `${(ratio * 100).toFixed(1)}%` } })),
    h('span', { class: 'val' }, fmt(value, value % 1 ? 1 : 0)));
}
function scoreMeter(score) {
  const wd = S.data?.watchdog;
  if (!wd) return meter(score, 50);
  return meter(score, wd.kickScore, [wd.alertScore / wd.kickScore, wd.setbackScore / wd.kickScore]);
}
function healthCell(health, max) {
  const ratio = max ? health / max : 1;
  return h('span', { class: ratio <= 0.3 ? 'ping-bad' : ratio <= 0.6 ? 'ping-ok' : null }, `${fmt(health, health % 1 ? 1 : 0)} / ${fmt(max)}`);
}
function pingCell(ping) {
  return h('span', { class: ping < 80 ? 'ping-good' : ping < 180 ? 'ping-ok' : 'ping-bad' }, `${fmt(ping)} ms`);
}
function empty(text) { return h('p', { class: 'empty' }, text); }
function table(columns, rows, { onRow, emptyText = 'Nothing to show.', limit = 250 } = {}) {
  if (!rows.length) return empty(emptyText);
  const body = h('tbody');
  for (const row of rows.slice(0, limit)) {
    const tr = h('tr', onRow ? { class: 'clickable', tabindex: '0', on: { click: () => onRow(row), keydown: (e) => { if (e.key === 'Enter') onRow(row); } } } : null);
    for (const col of columns) tr.append(h('td', { class: col.cls }, col.cell(row)));
    body.append(tr);
  }
  return h('div', null,
    h('div', { class: 'table-wrap' }, h('table', null, h('thead', null, h('tr', null, columns.map((c) => h('th', { class: c.cls?.includes('num') ? 'num' : null }, c.label)))), body)),
    rows.length > limit ? h('p', { class: 'more' }, `Showing ${fmt(limit)} of ${fmt(rows.length)}. Narrow the filter to see the rest.`) : null);
}
function feedRow({ lead, title, detail, when, onClick }) {
  return h('div', { class: `feed-row${onClick ? ' clickable' : ''}`, tabindex: onClick ? '0' : null, on: onClick ? { click: onClick, keydown: (e) => { if (e.key === 'Enter') onClick(); } } : null },
    lead, h('div', { class: 'shrink' }, h('div', { class: 'title' }, title), detail ? h('div', { class: 'detail' }, detail) : null),
    h('span', { class: 'when' }, when));
}
function kpi(label, value, unit, note, tone) {
  return h('div', { class: 'kpi', 'data-tone': tone || null },
    h('div', { class: 'label' }, label),
    h('div', { class: 'value' }, value, unit ? h('small', null, unit) : null),
    note ? h('div', { class: 'note' }, note) : null);
}
function fill(selector, ...nodes) { const host = $(selector); host.replaceChildren(...nodes.flat(Infinity).filter((n) => n != null && n !== false)); return host; }

/* ---------- Charts ---------- */
function chart(xs, ys, { color = 'var(--amber)', min, max, digits = 1, unit = '', threshold } = {}) {
  const box = h('div', { class: 'chart', style: { '--c': color } });
  if (!ys || ys.length < 2) { box.append(h('div', { class: 'chart-empty' }, 'Collecting samples. Charts fill in over the next few minutes.')); return box; }
  const lo = min ?? Math.min(...ys);
  let hi = max ?? Math.max(...ys);
  if (max == null) hi = hi + (hi - lo) * 0.15 || hi + 1;
  const range = hi - lo || 1;
  const W = 1000, H = 100;
  const x = (i) => (i / (ys.length - 1)) * W;
  const y = (v) => H - ((Math.max(lo, Math.min(hi, v)) - lo) / range) * H;
  const line = ys.map((v, i) => `${i ? 'L' : 'M'}${x(i).toFixed(1)} ${y(v).toFixed(1)}`).join('');
  const plot = svg('svg', { viewBox: `0 0 ${W} ${H}`, preserveAspectRatio: 'none' });
  for (const v of [hi, lo + range / 2, lo]) plot.append(svg('line', { class: 'grid-line', x1: 0, x2: W, y1: y(v), y2: y(v) }));
  if (threshold != null && threshold > lo && threshold < hi) plot.append(svg('line', { class: 'threshold', x1: 0, x2: W, y1: y(threshold), y2: y(threshold) }));
  plot.append(svg('path', { class: 'area', d: `${line}L${W} ${H}L0 ${H}Z` }), svg('path', { class: 'line', d: line }));
  box.append(plot);
  for (const v of [hi, lo + range / 2]) box.append(h('span', { class: 'axis', style: { '--y': `${(y(v) / H) * 100}%` } }, fmt(v, digits)));
  const cursor = h('div', { class: 'cursor', hidden: true });
  const dot = h('div', { class: 'dot', hidden: true });
  const tip = h('div', { class: 'tip', hidden: true });
  box.append(cursor, dot, tip);
  box.addEventListener('pointermove', (event) => {
    const rect = box.getBoundingClientRect();
    const i = Math.round(Math.max(0, Math.min(1, (event.clientX - rect.left) / rect.width)) * (ys.length - 1));
    const left = `${(x(i) / W) * 100}%`;
    cursor.style.setProperty('left', left);
    dot.style.setProperty('left', left);
    dot.style.setProperty('top', `${(y(ys[i]) / H) * 100}%`);
    tip.style.setProperty('left', left);
    tip.replaceChildren(`${fmt(ys[i], digits)}${unit}`, h('small', null, clock(xs[i])));
    cursor.hidden = dot.hidden = tip.hidden = false;
  });
  box.addEventListener('pointerleave', () => { cursor.hidden = dot.hidden = tip.hidden = true; });
  return box;
}
function chartCard(title, now, unit, chartEl) {
  return h('article', { class: 'chart-card' },
    h('header', null, h('h3', null, title), h('span', { class: 'now' }, now, unit ? h('small', null, unit) : null)), chartEl);
}

/* ---------- Navigation ---------- */
function buildNav() {
  const nav = $('#nav');
  for (const view of VIEWS) {
    nav.append(h('button', { type: 'button', class: 'nav-item', 'data-view': view.id, on: { click: () => go(view.id) } },
      icon(view.icon), h('span', { class: 'label' }, view.label), h('span', { class: 'count', hidden: true })));
  }
}
function go(id, push = true) {
  const view = VIEWS.find((v) => v.id === id) || VIEWS[0];
  S.view = view.id;
  for (const section of $$('.view')) section.hidden = section.dataset.view !== view.id;
  for (const item of $$('.nav-item')) {
    if (item.dataset.view === view.id) item.setAttribute('aria-current', 'page');
    else item.removeAttribute('aria-current');
  }
  $('#view-title').textContent = view.label;
  $('#view-sub').textContent = view.sub;
  if (push && location.hash !== `#${view.id}`) history.replaceState(null, '', `#${view.id}`);
  renderView();
}
function setCount(id, n, hot) {
  const el = $(`.nav-item[data-view="${id}"] .count`);
  el.hidden = !n;
  el.textContent = n > 99 ? '99+' : String(n);
  el.classList.toggle('hot', !!hot);
}

/* ---------- Derived data ---------- */
const openReports = () => S.data.desk.reports.filter((r) => r.status === 'OPEN');
const watched = () => S.data.watchdog.live.filter((p) => p.score >= S.data.watchdog.alertScore).sort((a, b) => b.score - a.score);
const recentEvidence = (ms) => S.data.watchdog.evidence.filter((e) => !ms || Date.now() - Date.parse(e.time) <= ms);
function knownName(uuid) {
  const d = S.data;
  if (!d) return '';
  return d.players.find((p) => p.uuid === uuid)?.name || d.directory.find((p) => p.uuid === uuid)?.name || '';
}

function renderAll() {
  const d = S.data;
  const reports = openReports().length;
  const watch = watched().length;
  setCount('reports', reports, reports > 0);
  setCount('alerts', watch, watch > 0);
  setCount('players', d.players.length, false);
  setCount('sanctions', d.bans.length + d.desk.mutes.length + d.desk.freezes.length, false);
  document.title = reports + watch ? `(${reports + watch}) Staff Console` : 'Staff Console';
  $('#updated').textContent = `Updated ${clock(d.updated)}. Refreshes every 5 seconds while this tab is open.`;
  renderView();
}
function renderView() {
  if (!S.data) return;
  ({ overview: renderOverview, players: renderPlayers, locations: renderLocations, alerts: renderAlerts, reports: renderReports, sanctions: renderSanctions,
    server: renderServer, audit: renderAudit, staff: renderStaff })[S.view]?.();
}

/* ---------- Overview ---------- */
function renderOverview() {
  const d = S.data, srv = d.server, wd = d.watchdog;
  const hot = watched();
  const reports = openReports();
  const lastHour = recentEvidence(3600e3);
  const kicks = lastHour.filter((e) => e.action === 'KICK').length;
  const slow = srv.tps1 < 18, struggling = srv.tps1 < 15;
  const severe = hot.filter((p) => p.score >= wd.setbackScore);
  let level = 'calm', headline = 'All quiet';
  if (struggling || severe.length) {
    level = 'alarm';
    headline = struggling ? 'The server is struggling' : `${severe[0].name} needs a look now`;
  } else if (hot.length || reports.length || slow) {
    level = 'watch';
    headline = hot.length ? `${plural(hot.length, 'player')} under watch`
      : reports.length ? `${plural(reports.length, 'report')} waiting` : 'The server is running slow';
  }
  $('#pulse').dataset.level = level;
  $('#pulse-state').textContent = headline;
  $('#pulse-detail').textContent = `${fmt(srv.online)} of ${fmt(srv.maxPlayers)} online, ${fmt(srv.tps1, 1)} TPS, ${plural(lastHour.length, 'flag')} in the last hour.`;
  fill('#pulse-chart', chart(d.history.t, d.history.tps, { min: 10, max: 20, threshold: 18, unit: ' TPS', color: level === 'alarm' ? 'var(--red)' : 'var(--amber)' }));

  const memPct = srv.memoryUsed / srv.memoryMax;
  fill('#kpis',
    kpi('Players online', fmt(srv.online), `/ ${fmt(srv.maxPlayers)}`, `${plural(d.directory.length, 'known account')}`),
    kpi('TPS', fmt(srv.tps1, 1), null, `5m ${fmt(srv.tps5, 1)}, 15m ${fmt(srv.tps15, 1)}`, struggling ? 'bad' : slow ? 'warn' : 'good'),
    kpi('Tick time', fmt(srv.mspt, 1), 'ms', `95th percentile ${fmt(srv.msptP95, 1)} ms`, srv.mspt > 50 ? 'bad' : srv.mspt > 40 ? 'warn' : null),
    kpi('Memory', fmt(srv.memoryUsed / 1024, 1), `/ ${fmt(srv.memoryMax / 1024, 1)} GB`, `${fmt(memPct * 100)}% of the heap`, memPct > 0.9 ? 'bad' : memPct > 0.75 ? 'warn' : null),
    kpi('Open reports', fmt(reports.length), null, reports.length ? `Oldest ${ago(reports.at(-1).time)}` : 'Nothing waiting', reports.length ? 'warn' : null),
    kpi('Flags last hour', fmt(lastHour.length), null, kicks ? `${plural(kicks, 'automatic kick')}` : 'No automatic kicks', kicks ? 'bad' : null));

  const live = [...wd.live].filter((p) => p.score > 0).sort((a, b) => b.score - a.score).slice(0, 6);
  fill('#ov-watch', live.length ? h('div', { class: 'feed' }, live.map((p) => feedRow({
    lead: avatar(p.name, p.uuid, true), title: p.name,
    detail: Object.entries(p.counts).map(([k, v]) => `${pretty(k)} ×${v}`).join(', ') || 'Score decaying',
    when: scoreMeter(p.score), onClick: () => openDrawer(p.uuid, p.name),
  }))) : empty('No one online has a suspicion score right now.'));

  const alerts = wd.evidence.slice(0, 6);
  fill('#ov-alerts', alerts.length ? h('div', { class: 'feed' }, alerts.map((e) => feedRow({
    lead: badge(pretty(e.check), CHECK_TONE[e.check]), title: e.player, detail: e.detail,
    when: OUTCOME[e.action] ? outcomeBadge(e.action) : ago(e.time), onClick: () => openDrawer(e.uuid, e.player),
  }))) : empty('Watchdog has not flagged anyone yet.'));

  fill('#ov-reports', reports.length ? h('div', { class: 'feed' }, reports.slice(0, 5).map((r) => feedRow({
    lead: avatar(r.target, r.targetId), title: r.target, detail: r.reason, when: ago(r.time),
    onClick: () => openDrawer(r.targetId, r.target),
  }))) : empty('No open reports. New ones appear here as soon as a player files one.'));

  const audit = d.desk.audit.slice(0, 6);
  fill('#ov-audit', audit.length ? h('div', { class: 'feed' }, audit.map((a) => feedRow({
    lead: badge(pretty(a.action), ACTION_TONE[a.action]), title: `${a.actor} on ${a.target}`, detail: a.detail, when: ago(a.time),
    onClick: a.targetId ? () => openDrawer(a.targetId, a.target) : null,
  }))) : empty('No staff actions recorded yet.'));
}

/* ---------- Players ---------- */
function renderPlayers() {
  const d = S.data;
  const q = $('#players-filter').value.trim().toLowerCase();
  const recordsCount = d.directory.filter((p) => p.records || p.banned).length;
  const labels = { online: ['Online', d.players.length], all: ['Everyone', d.directory.length], sanctioned: ['With records', recordsCount] };
  for (const button of $$('#players-scope button')) {
    const [label, count] = labels[button.dataset.value];
    button.replaceChildren(label, h('span', { class: 'count' }, fmt(count)));
  }
  const online = new Set(d.players.map((p) => p.uuid));
  if (S.playersScope === 'online') {
    const rows = d.players.filter((p) => !q || `${p.name} ${p.world} ${p.groups}`.toLowerCase().includes(q));
    fill('#players-table', table([
      { label: 'Player', cell: (p) => who(p.name, p.uuid, p.brand ? `${p.brand} client` : null, true) },
      { label: 'Status', cell: (p) => h('div', { class: 'badges' }, p.op ? badge('Operator', 'violet') : null, p.groups ? badge(p.groups, 'blue') : null, p.sharedAccounts ? badge(`Shared IP ×${p.sharedAccounts}`, 'red') : null, p.muted ? badge('Muted', 'amber') : null, p.frozen ? badge('Frozen', 'blue') : null, p.flying ? badge('Flying') : null) },
      { label: 'Position', cls: 'nowrap muted', cell: (p) => `${p.world} ${fmt(p.x)}, ${fmt(p.y)}, ${fmt(p.z)}` },
      { label: 'Mode', cls: 'muted', cell: (p) => pretty(p.gamemode) },
      { label: 'Health', cls: 'nowrap', cell: (p) => healthCell(p.health, p.maxHealth) },
      { label: 'Ping', cls: 'num nowrap', cell: (p) => pingCell(p.ping) },
      { label: 'Watchdog', cell: (p) => scoreMeter(p.score) },
      { label: 'Session', cls: 'num nowrap muted', cell: (p) => span(Date.now() - p.lastLogin) },
    ], rows, { onRow: (p) => openDrawer(p.uuid, p.name), emptyText: q ? 'No online player matches that filter.' : 'No one is online right now.' }));
  } else {
    const rows = d.directory.filter((p) => (S.playersScope === 'all' || p.records || p.banned) && (!q || p.name.toLowerCase().includes(q)));
    fill('#players-table', table([
      { label: 'Player', cell: (p) => who(p.name, p.uuid, null, online.has(p.uuid)) },
      { label: 'Status', cell: (p) => h('div', { class: 'badges' }, online.has(p.uuid) ? badge('Online', 'green') : null, p.banned ? badge('Banned', 'red') : null, p.records ? badge('Has records', 'amber') : null) },
      { label: 'Last seen', cls: 'nowrap muted', cell: (p) => online.has(p.uuid) ? 'Now' : ago(p.lastSeen) },
      { label: 'First joined', cls: 'nowrap muted', cell: (p) => date(p.firstPlayed) },
    ], rows, { onRow: (p) => openDrawer(p.uuid, p.name), emptyText: q ? 'No player matches that filter.' : 'No players in this list.' }));
  }
}

/* ---------- Locations ---------- */
const GEO_STATE = {
  ready: null,
  downloading: 'Downloading the location database for the first time. Locations fill in a minute or two after it finishes.',
  missing: 'No location database yet. Turn on anti-cheat.geolocation.auto-download, or put a .mmdb file in plugins/NerdSMP/geoip/ and restart.',
  error: 'The location database could not be downloaded. Locations appear once it can be; see the Location data panel for the error.',
};
function geoCredit(db) {
  if (!db?.attribution) return null;
  return h('p', { class: 'hint credit' }, h('a', { href: db.attributionUrl, target: '_blank', rel: 'noopener noreferrer' }, db.attribution), db.attribution.startsWith('IP Geolocation') ? ', licensed CC BY 4.0.' : '.');
}
function renderLocations() {
  const g = S.data.geography || {};
  if (!g.enabled) {
    fill('#geo-notice', h('p', { class: 'notice space-below' }, g.recordsAddresses === false
      ? 'Locations are off because IP recording is off in config (anti-cheat.record-ip-addresses).'
      : 'Locations are turned off in config (anti-cheat.geolocation.enabled).'));
    for (const id of ['#geo-kpis', '#geo-bars', '#geo-source', '#geo-table']) fill(id);
    return;
  }
  const db = g.database || {};
  fill('#geo-notice', GEO_STATE[db.state] ? h('p', { class: `notice space-below${db.state === 'error' ? ' red' : ''}` }, GEO_STATE[db.state]) : null);
  const all = g.countries || [];
  const online = S.geoScope === 'online';
  for (const button of $$('#geo-scope button')) {
    const n = button.dataset.value === 'online' ? g.onlineLocated : g.located;
    button.replaceChildren(button.dataset.value === 'online' ? 'Online now' : 'Everyone', h('span', { class: 'count' }, fmt(n || 0)));
  }
  const count = (c) => online ? c.online : c.players;
  const scoped = all.filter((c) => count(c) > 0).sort((a, b) => count(b) - count(a) || a.name.localeCompare(b.name));
  const total = scoped.reduce((sum, c) => sum + count(c), 0);
  const top = scoped[0];
  fill('#geo-kpis',
    kpi('Countries', fmt(scoped.length), null, online ? 'Among players online now' : 'Across every located account'),
    kpi('Located players', fmt(online ? g.onlineLocated : g.located), null, online ? `${plural(S.data.players.length, 'player')} online` : `${plural(g.unlocated || 0, 'account')} without a location`),
    kpi('Top country', top ? top.name : '—', null, top ? `${pct(count(top) / total)} of located players` : 'No locations yet'),
    kpi('Outside the top country', top ? fmt(total - count(top)) : '—', null, top ? `${pct((total - count(top)) / total)} of located players` : null));
  $('#geo-scope-label').textContent = online ? 'Online now' : 'Last-known location';
  const most = Math.max(1, ...scoped.map(count));
  fill('#geo-bars', scoped.length ? h('div', { class: 'bars wide' }, scoped.slice(0, 12).map((c) => h('div', { class: 'bar-row', title: `${c.name}: ${plural(count(c), 'player')}` },
    h('span', { class: 'clip' }, c.name), h('div', { class: 'track' }, h('div', { class: 'fill', style: { '--w': `${(count(c) / most) * 100}%` } })), h('span', { class: 'n' }, fmt(count(c))))),
    scoped.length > 12 ? h('p', { class: 'hint' }, `${plural(scoped.length - 12, 'more country', 'more countries')} in the table below.`) : null)
    : empty(online ? 'No one online has a location yet.' : 'No player has a location yet. Each account is located on its next join.'));
  fill('#geo-source', h('dl', { class: 'facts' },
    h('dt', null, 'Database'), h('dd', null, db.database || db.file || 'Not loaded'),
    h('dt', null, 'Edition'), h('dd', null, date(db.built, 'month')),
    h('dt', null, 'Updates'), h('dd', null, db.autoDownload ? (db.lastDownload ? `Monthly, last ${ago(db.lastDownload)}` : 'Monthly, automatic') : 'Manual'),
    db.error ? [h('dt', null, 'Last problem'), h('dd', null, db.error)] : null),
    h('p', { class: 'hint space-above' }, 'Looked up offline on the server, so no address leaves it. Only country, region and city are kept, never coordinates. City-level results for home connections are often off by tens of kilometres; mobile and VPN users show where their provider routes them.'),
    geoCredit(db));
  const q = $('#geo-filter').value.trim().toLowerCase();
  const places = (list) => list.map((p) => `${p.name} (${fmt(p.players)})`).join(', ');
  const rows = scoped.filter((c) => !q || `${c.name} ${c.code} ${c.regions.map((r) => r.name).join(' ')} ${c.cities.map((r) => r.name).join(' ')}`.toLowerCase().includes(q));
  fill('#geo-table', table([
    { label: 'Country', cell: (c) => h('div', null, h('div', { class: 'name' }, c.name), c.code && c.code !== c.name ? h('div', { class: 'hint' }, c.code) : null) },
    { label: 'Players', cls: 'num', cell: (c) => fmt(c.players) },
    { label: 'Online', cls: 'num', cell: (c) => c.online ? fmt(c.online) : h('span', { class: 'hint' }, '0') },
    { label: 'Share', cls: 'num', cell: (c) => pct(count(c) / total) },
    { label: 'Top regions', cls: 'wrap muted', cell: (c) => places(c.regions) || '—' },
    { label: 'Top cities', cls: 'wrap muted', cell: (c) => places(c.cities) || '—' },
  ], rows, { emptyText: q ? 'No country matches that filter.' : 'No locations to show yet.' }));
}

/* ---------- Watchdog ---------- */
function renderAlerts() {
  const wd = S.data.watchdog;
  const select = $('#wd-check');
  if (select.options.length === 1) for (const check of wd.checks) select.append(h('option', { value: check }, pretty(check)));
  const range = Number($('#wd-range').value);
  const check = select.value, outcome = $('#wd-action').value, q = $('#wd-filter').value.trim().toLowerCase();
  const inRange = recentEvidence(range);
  $('#wd-range-label').textContent = $('#wd-range').selectedOptions[0].textContent;

  fill('#wd-config', h('dl', { class: 'facts' },
    h('dt', null, 'Mode'), h('dd', null, wd.mode === 'autonomous' ? badge('Acts on its own', 'amber') : badge('Alerts only', 'blue')),
    h('dt', null, 'Alerts staff at'), h('dd', null, `score ${fmt(wd.alertScore)}`),
    h('dt', null, 'Moves player back at'), h('dd', null, `score ${fmt(wd.setbackScore)}`),
    h('dt', null, 'Kicks at'), h('dd', null, `score ${fmt(wd.kickScore)}`),
    h('dt', null, 'Score fades by'), h('dd', null, `${fmt(wd.decayPerSecond, 2)} per second`)),
    h('p', { class: 'hint space-above' }, wd.model?.autonomous ? 'Rule checks kick only on repeated evidence across checks. The combat model is validated and can kick, then ban, on its own.' : 'Rule checks kick only on repeated evidence across checks and never ban. The combat model can ban only after it passes validation below.'));

  const counts = Object.fromEntries(wd.checks.map((c) => [c, 0]));
  for (const e of inRange) counts[e.check] = (counts[e.check] || 0) + 1;
  const most = Math.max(1, ...Object.values(counts));
  fill('#wd-checks', h('div', { class: 'bars' }, Object.entries(counts).sort((a, b) => b[1] - a[1]).map(([c, n]) => h('div', { class: 'bar-row' },
    h('span', null, pretty(c)), h('div', { class: 'track' }, h('div', { class: 'fill', style: { '--w': `${(n / most) * 100}%` } })), h('span', { class: 'n' }, fmt(n))))));

  renderModel(wd);
  const live = [...wd.live].filter((p) => p.score > 0 || p.probability >= 0.5).sort((a, b) => b.score - a.score);
  fill('#wd-live', live.length ? h('div', { class: 'feed' }, live.slice(0, 8).map((p) => feedRow({
    lead: avatar(p.name, p.uuid, true), title: p.name,
    detail: [p.exempt ? 'Exempt from movement checks' : `${Object.values(p.counts).reduce((a, b) => a + b, 0)} recent flags`, p.probability >= 0 ? `model ${pct(p.probability)}` : null].filter(Boolean).join(', '),
    when: scoreMeter(p.score), onClick: () => openDrawer(p.uuid, p.name),
  }))) : empty('Every online player is at zero.'));

  const rows = inRange.filter((e) => (!check || e.check === check) && (!outcome || e.action === outcome)
    && (!q || `${e.player} ${e.detail}`.toLowerCase().includes(q)));
  fill('#wd-table', table([
    { label: 'When', cls: 'nowrap muted', cell: (e) => clock(e.time) },
    { label: 'Player', cell: (e) => who(e.player, e.uuid) },
    { label: 'Check', cell: (e) => badge(pretty(e.check), CHECK_TONE[e.check]) },
    { label: 'Evidence', cls: 'wrap', cell: (e) => e.detail },
    { label: 'Score', cls: 'num', cell: (e) => fmt(e.score, 1) },
    { label: 'Ping', cls: 'num nowrap', cell: (e) => pingCell(e.ping) },
    { label: 'Outcome', cell: (e) => outcomeBadge(e.action) },
  ], rows, { onRow: (e) => openDrawer(e.uuid, e.player), emptyText: 'No flags match these filters.' }));
}

function renderModel(wd) {
  const m = wd.model || {};
  const needs = m.requirements || {};
  const status = !m.enabled ? ['Off', ''] : m.training ? ['Training\u2026', 'blue'] : m.autonomous ? ['Acting on its own', 'red']
    : m.model ? ['Advisory', 'amber'] : ['Collecting data', ''];
  const progress = (label, have, need) => h('div', { class: 'bar-row' }, h('span', null, label),
    h('div', { class: 'track' }, h('div', { class: 'fill', style: { '--w': `${Math.min(100, need ? (have / need) * 100 : 0)}%` } })),
    h('span', { class: 'n' }, `${fmt(have)}/${fmt(need)}`));
  const metrics = m.model ? h('dl', { class: 'facts' },
    h('dt', null, 'Precision on unseen players'), h('dd', null, pct(m.model.precision, 2)),
    h('dt', null, 'Cheaters caught'), h('dd', null, pct(m.model.recall)),
    h('dt', null, 'Ranking quality (AUC)'), h('dd', null, fmt(m.model.auc, 3)),
    h('dt', null, 'Acts at confidence'), h('dd', null, pct(m.model.threshold, 1)),
    h('dt', null, 'Last trained'), h('dd', null, ago(m.model.trainedAt))) : empty('No model yet. Label fights from a player\u2019s Watchdog tab, or with /watchdog label, then train.');
  fill('#wd-model', h('div', { class: 'model-grid' },
    h('div', null,
      h('div', { class: 'badges space-below' }, badge(status[0], status[1]), m.autonomyAllowed ? null : badge('Autonomy off in config')),
      h('p', { class: 'model-note' }, m.note || ''),
      h('p', { class: 'hint space-above' }, `${fmt(m.windows || 0)} combat windows recorded, ${fmt(m.baselineSamples || 0)} in the normal-play baseline. Packet layer: ${wd.packetLayer}${wd.packetErrors ? `, ${fmt(wd.packetErrors)} unreadable packets` : ''}.`)),
    h('div', { class: 'bars' },
      progress('Cheating fights', m.cheatWindows || 0, needs.cheatWindows), progress('Cheating players', m.cheatPlayers || 0, needs.cheatPlayers),
      progress('Legit fights', m.legitWindows || 0, needs.legitWindows), progress('Legit players', m.legitPlayers || 0, needs.legitPlayers)),
    metrics));
}

/* ---------- Reports ---------- */
function renderReports() {
  const all = S.data.desk.reports;
  const open = all.filter((r) => r.status === 'OPEN');
  for (const button of $$('#reports-scope button')) {
    const n = button.dataset.value === 'OPEN' ? open.length : all.length - open.length;
    button.replaceChildren(button.dataset.value === 'OPEN' ? 'Open' : 'Resolved', h('span', { class: 'count' }, fmt(n)));
  }
  const rows = all.filter((r) => r.status === S.reportsScope);
  if (S.reportsScope === 'OPEN') rows.reverse();
  fill('#reports-list', rows.length ? rows.map((r) => h('article', { class: 'report' },
    h('div', { class: 'shrink' },
      h('div', { class: 'who' }, avatar(r.target, r.targetId), h('div', null, h('div', { class: 'name' }, playerLink(r.target, r.targetId)),
        h('div', { class: 'meta' }, 'Reported by ', playerLink(r.reporter, r.reporterId), `, ${ago(r.time)}`))),
      h('p', { class: 'reason' }, r.reason),
      h('p', { class: 'line' }, `Case ${r.shortId}`),
      r.status === 'RESOLVED' ? h('p', { class: 'resolution' }, `Resolved by ${r.resolver} ${ago(r.resolvedAt)}: ${r.resolution}`) : null),
    r.status === 'OPEN' ? h('div', { class: 'report-actions' },
      h('button', { type: 'button', class: 'secondary small', on: { click: () => openDrawer(r.targetId, r.target) } }, 'Review player'),
      h('button', { type: 'button', class: 'primary small', on: { click: () => ask('resolve', { uuid: r.targetId, name: r.target }, { report: r.id }) } }, 'Resolve')) : null))
    : [empty(S.reportsScope === 'OPEN' ? 'No open reports. Players file them with /report.' : 'No resolved reports yet.')]);
}

/* ---------- Sanctions ---------- */
function renderSanctions() {
  const d = S.data;
  fill('#sx-bans', table([
    { label: 'Player', cell: (b) => who(b.name, b.uuid, b.reason) },
    { label: 'Ends', cls: 'nowrap muted', cell: (b) => b.expires ? until(b.expires) : 'Never' },
    { label: '', cls: 'num', cell: (b) => b.uuid ? h('button', { type: 'button', class: 'secondary small', on: { click: (e) => { e.stopPropagation(); ask('unban', { uuid: b.uuid, name: b.name }); } } }, 'Unban') : '' },
  ], d.bans, { onRow: (b) => b.uuid && openDrawer(b.uuid, b.name), emptyText: 'No one is banned.' }));
  fill('#sx-mutes', table([
    { label: 'Player', cell: (m) => who(m.name, m.uuid, m.reason) },
    { label: 'Ends', cls: 'nowrap muted', cell: (m) => until(m.expires) },
    { label: '', cls: 'num', cell: (m) => h('button', { type: 'button', class: 'secondary small', on: { click: (e) => { e.stopPropagation(); ask('unmute', { uuid: m.uuid, name: m.name }); } } }, 'Unmute') },
  ], d.desk.mutes, { onRow: (m) => openDrawer(m.uuid, m.name), emptyText: 'No active mutes.' }));
  fill('#sx-freezes', table([
    { label: 'Player', cell: (f) => who(f.name, f.uuid, f.reason) },
    { label: 'Since', cls: 'nowrap muted', cell: (f) => ago(f.time) },
    { label: '', cls: 'num', cell: (f) => h('button', { type: 'button', class: 'secondary small', on: { click: (e) => { e.stopPropagation(); ask('unfreeze', { uuid: f.uuid, name: f.name }); } } }, 'Unfreeze') },
  ], d.desk.freezes, { onRow: (f) => openDrawer(f.uuid, f.name), emptyText: 'No one is frozen.' }));
}

/* ---------- Server ---------- */
function renderServer() {
  const d = S.data, srv = d.server, hist = d.history;
  fill('#srv-kpis',
    kpi('TPS', fmt(srv.tps1, 1), null, `5m ${fmt(srv.tps5, 1)}, 15m ${fmt(srv.tps15, 1)}`, srv.tps1 < 15 ? 'bad' : srv.tps1 < 18 ? 'warn' : 'good'),
    kpi('Tick time', fmt(srv.mspt, 1), 'ms', `Slowest recent tick ${fmt(srv.msptMax, 0)} ms`, srv.mspt > 50 ? 'bad' : srv.mspt > 40 ? 'warn' : null),
    kpi('Server CPU', fmt(srv.cpu, 0), '%', `Whole machine ${fmt(srv.systemCpu, 0)}%`, srv.cpu > 90 ? 'bad' : srv.cpu > 70 ? 'warn' : null),
    kpi('Memory', fmt(srv.memoryUsed), `/ ${fmt(srv.memoryMax)} MB`, `${fmt(srv.memoryAllocated)} MB reserved`),
    kpi('Entities', fmt(d.worlds.reduce((a, w) => a + w.entities, 0)), null, `${fmt(d.worlds.reduce((a, w) => a + w.tileEntities, 0))} block entities`),
    kpi('Loaded chunks', fmt(d.worlds.reduce((a, w) => a + w.chunks, 0)), null, `Across ${plural(d.worlds.length, 'world')}`));
  const last = (values) => values?.at(-1);
  fill('#srv-charts',
    chartCard('TPS', fmt(last(hist.tps), 1), null, chart(hist.t, hist.tps, { min: 0, max: 20, threshold: 18, unit: ' TPS' })),
    chartCard('Tick time', fmt(last(hist.mspt), 1), 'ms', chart(hist.t, hist.mspt, { min: 0, threshold: 50, unit: ' ms', color: 'var(--blue)' })),
    chartCard('Players online', fmt(last(hist.players)), null, chart(hist.t, hist.players, { min: 0, digits: 0, color: 'var(--green)' })),
    chartCard('Memory', fmt(last(hist.memory)), 'MB', chart(hist.t, hist.memory, { min: 0, max: srv.memoryMax, digits: 0, unit: ' MB', color: 'var(--violet)' })),
    chartCard('Server CPU', fmt(last(hist.cpu)), '%', chart(hist.t, hist.cpu, { min: 0, max: 100, digits: 0, unit: '%', color: 'var(--red)' })),
    chartCard('Entities', fmt(last(hist.entities)), null, chart(hist.t, hist.entities, { min: 0, digits: 0, color: 'var(--blue)' })));
  fill('#srv-worlds', table([
    { label: 'World', cell: (w) => h('div', null, h('div', { class: 'name' }, w.name), h('div', { class: 'hint' }, pretty(w.environment))) },
    { label: 'Players', cls: 'num', cell: (w) => fmt(w.players) },
    { label: 'Entities', cls: 'num', cell: (w) => fmt(w.entities) },
    { label: 'Block entities', cls: 'num', cell: (w) => fmt(w.tileEntities) },
    { label: 'Chunks', cls: 'num', cell: (w) => fmt(w.chunks) },
    { label: 'Weather', cls: 'muted', cell: (w) => w.storm ? 'Storm' : 'Clear' },
  ], d.worlds));
  const facts = [
    ['Server', srv.version], ['Minecraft', srv.minecraft], ['Gameplay plugin', srv.plugin], ['Java', srv.java], ['System', srv.os],
    ['CPU cores', fmt(srv.cores)], ['Uptime', span(srv.uptime)], ['Threads', fmt(srv.threads)],
    ['Garbage collection', `${fmt(srv.gcCount)} runs, ${fmt(srv.gcMillis / 1000, 1)} s total`],
    ['View distance', `${srv.viewDistance} chunks, simulation ${srv.simulationDistance}`],
    ['Whitelist', srv.whitelist ? 'On' : 'Off'], ['Account checks', srv.onlineMode ? 'Online mode' : 'Offline mode'],
  ];
  fill('#srv-info', facts.flatMap(([k, v]) => [h('dt', null, k), h('dd', null, v)]));
}

/* ---------- Audit ---------- */
function renderAudit() {
  const rows = S.data.desk.audit;
  const select = $('#audit-action');
  const actions = [...new Set(rows.map((r) => r.action))].sort();
  const current = select.value;
  if (select.options.length - 1 !== actions.length) {
    select.replaceChildren(h('option', { value: '' }, 'All actions'), actions.map((a) => h('option', { value: a }, pretty(a))));
    select.value = actions.includes(current) ? current : '';
  }
  const q = $('#audit-filter').value.trim().toLowerCase();
  const filtered = rows.filter((r) => (!select.value || r.action === select.value) && (!q || `${r.actor} ${r.target} ${r.detail}`.toLowerCase().includes(q)));
  fill('#audit-table', table([
    { label: 'When', cls: 'nowrap muted', cell: (r) => clock(r.time) },
    { label: 'Staff', cls: 'nowrap', cell: (r) => r.actor },
    { label: 'Action', cell: (r) => badge(pretty(r.action), ACTION_TONE[r.action]) },
    { label: 'Player', cell: (r) => playerLink(r.target, r.targetId) },
    { label: 'Detail', cls: 'wrap', cell: (r) => r.detail },
  ], filtered, { emptyText: 'No audit entries match.' }));
}

/* ---------- Staff ---------- */
function renderStaff() {
  const st = S.data.staff;
  fill('#staff-users', table([
    { label: 'Member', cell: (u) => who(u.name, u.uuid, null, u.online) },
    { label: 'Groups', cell: (u) => h('div', { class: 'badges' }, u.groups.split(', ').map((g) => badge(g, 'blue'))) },
    { label: 'Status', cls: 'muted', cell: (u) => u.online ? 'Online' : 'Offline' },
  ], [...st.users].sort((a, b) => b.online - a.online || a.name.localeCompare(b.name)), { onRow: (u) => openDrawer(u.uuid, u.name), emptyText: 'No one has been given a staff group yet.' }));
  fill('#staff-groups', table([
    { label: 'Group', cell: (g) => h('strong', null, g.id) },
    { label: 'Inherits', cls: 'muted', cell: (g) => g.parents || '—' },
    { label: 'Rules', cls: 'num', cell: (g) => fmt(g.permissions) },
  ], st.groups));
  fill('#staff-audit', table([
    { label: 'When', cls: 'nowrap muted', cell: (a) => clock(a.time) },
    { label: 'By', cls: 'nowrap', cell: (a) => a.actor },
    { label: 'Change', cls: 'wrap', cell: (a) => a.change },
  ], st.audit, { emptyText: 'No access changes recorded.' }));
}

/* ---------- Player drawer ---------- */
function openDrawer(uuid, name) {
  if (!uuid) return;
  const same = S.drawer.uuid === uuid;
  S.drawer = { uuid, name: name || knownName(uuid), tab: same ? S.drawer.tab : 'profile', detail: same ? S.drawer.detail : null, at: 0 };
  $('#drawer').hidden = false;
  $('#scrim').hidden = false;
  renderDrawer();
  loadDrawer();
  $('#drawer').focus?.();
}
function closeDrawer() {
  S.drawer.uuid = null;
  $('#drawer').hidden = true;
  $('#scrim').hidden = true;
}
async function loadDrawer(quiet) {
  const uuid = S.drawer.uuid;
  try {
    const detail = await api(`/api/player/${uuid}`);
    if (S.drawer.uuid !== uuid) return;
    S.drawer.detail = detail; S.drawer.name = detail.name || S.drawer.name; S.drawer.at = Date.now();
    renderDrawer();
  } catch (error) {
    if (!quiet && S.drawer.uuid === uuid) fill('#drawer-body', h('p', { class: 'notice red' }, error.message));
  }
}
function renderDrawer() {
  const { uuid, name, detail: p, tab } = S.drawer;
  const scroll = $('#drawer-body').scrollTop;
  fill('#drawer-head', avatar(name, uuid, p ? p.online : null, true),
    h('div', { class: 'title' }, h('h2', null, name || 'Loading…'), h('div', { class: 'uuid' }, uuid),
      p ? h('div', { class: 'badges' }, p.online ? badge('Online', 'green') : badge(`Last seen ${ago(p.lastSeen)}`),
        p.banned ? badge('Banned', 'red') : null, p.desk.mute ? badge('Muted', 'amber') : null, p.desk.freeze ? badge('Frozen', 'blue') : null,
        p.op ? badge('Operator', 'violet') : null, p.groups.map((g) => badge(g, 'blue'))) : null),
    h('button', { type: 'button', class: 'ghost small', on: { click: closeDrawer } }, 'Close'));
  const target = { uuid, name };
  const button = (label, action, cls = 'secondary small', disabled = false) =>
    h('button', { type: 'button', class: cls, disabled, on: { click: () => ask(action, target) } }, label);
  fill('#drawer-actions', p ? [
    button('Message', 'message', 'secondary small', !p.online),
    button('Warn', 'warn'),
    button('Add note', 'note'),
    p.desk.mute ? button('Unmute', 'unmute') : button('Mute', 'mute'),
    p.desk.freeze ? button('Unfreeze', 'unfreeze') : button('Freeze', 'freeze'),
    p.watchdog.score > 0 ? button('Clear score', 'clear-watchdog') : null,
    h('span', { class: 'spacer' }),
    button('Kick', 'kick', 'secondary small', !p.online),
    p.banned ? button('Unban', 'unban') : button('Ban', 'ban', 'danger small'),
  ] : []);
  for (const t of $$('#drawer-tabs button')) t.setAttribute('aria-selected', String(t.dataset.value === tab));
  const body = $('#drawer-body');
  if (!p) { fill('#drawer-body', h('p', { class: 'empty' }, 'Loading player…')); return; }
  ({ profile: drawerProfile, connection: drawerConnection, watchdog: drawerWatchdog, history: drawerHistory, inventory: drawerInventory, access: drawerAccess })[tab](p);
  body.scrollTop = scroll;
}
const stat = (label, value) => h('div', { class: 'stat' }, h('div', { class: 'label' }, label), h('div', { class: 'value' }, value ?? '—'));
function drawerProfile(p) {
  const live = p.live;
  const notices = [];
  if (p.ban) notices.push(h('p', { class: 'notice red' }, `Banned by ${p.ban.source || 'staff'} ${p.ban.time ? ago(p.ban.time) : ''}: ${p.ban.reason || 'No reason given'}. ${p.ban.expires ? `Ends ${until(p.ban.expires)}.` : 'Permanent.'}`));
  if (p.desk.mute) notices.push(h('p', { class: 'notice' }, `Muted by ${p.desk.mute.actor}, ends ${until(p.desk.mute.expires)}: ${p.desk.mute.reason}`));
  const alts = sharedAccounts(p);
  if (alts.length) notices.push(h('p', { class: 'notice red' }, 'Shares an IP address with ', alts.map((a, i) => [i ? ', ' : '', playerLink(a.name, a.uuid)]), '. Check the Connection tab before acting.'));
  if (p.desk.freeze) notices.push(h('p', { class: 'notice' }, `Frozen by ${p.desk.freeze.actor} ${ago(p.desk.freeze.time)}: ${p.desk.freeze.reason}`));
  fill('#drawer-body',
    notices.length ? h('div', { class: 'section stack' }, notices) : null,
    live ? h('div', { class: 'section' }, h('h3', null, 'Right now'), h('div', { class: 'stat-grid' },
      stat('World', live.world), stat('Position', `${fmt(live.x)}, ${fmt(live.y)}, ${fmt(live.z)}`),
      stat('Game mode', `${pretty(live.gamemode)}${live.flying ? ', flying' : live.allowFlight ? ', can fly' : ''}`),
      stat('Health', `${fmt(live.health, 1)} / ${fmt(live.maxHealth)}`), stat('Food', `${live.food} / 20`), stat('Level', fmt(live.level)),
      stat('Ping', `${fmt(live.ping)} ms`), stat('Client', live.brand || 'Unknown'), stat('Session', span(Date.now() - live.lastLogin)))) : null,
    live && live.effects.length ? h('div', { class: 'section' }, h('h3', null, 'Active effects'), h('div', { class: 'badges' },
      live.effects.map((e) => badge(`${pretty(e.type)} ${e.amplifier}${e.seconds >= 0 ? `, ${e.seconds}s` : ''}`, 'blue')))) : null,
    h('div', { class: 'section' }, h('h3', null, 'Account'), h('div', { class: 'stat-grid' },
      stat('First joined', date(p.firstPlayed)),
      stat('Last seen', p.online ? 'Now' : ago(p.lastSeen)),
      stat('Playtime', p.stats.playTicks ? span(p.stats.playTicks * 50) : p.connection?.trackedMillis ? span(p.connection.trackedMillis) : '—'),
      stat('Sessions', p.connection?.sessions ? fmt(p.connection.sessions) : '—'),
      stat('Average session', p.connection?.sessions ? span(p.connection.averageMillis) : '—'),
      stat('Client', p.connection?.brand || '—'),
      p.connection?.locatesAddresses ? stat('Connects from', p.connection.location?.label || 'Not located yet') : null,
      stat('Deaths', fmt(p.stats.deaths)), stat('Player kills', fmt(p.stats.playerKills)), stat('Mob kills', fmt(p.stats.mobKills)))),
    h('div', { class: 'section' }, h('h3', null, 'At a glance'), h('div', { class: 'stat-grid' },
      stat('Watchdog score', fmt(p.watchdog.score, 1)), stat('Saved flags', fmt(p.watchdog.evidence.length)),
      stat('Staff records', fmt(p.desk.history.length)))));
}
function sharedAccounts(p) {
  const seen = new Map();
  for (const row of p.connection?.addresses || []) for (const alt of row.sharedWith) seen.set(alt.uuid, alt);
  return [...seen.values()];
}
function drawerConnection(p) {
  const c = p.connection || {};
  if (!c.sessions) {
    fill('#drawer-body', empty('No connections recorded yet. Client and network details appear from this player’s next join.'));
    return;
  }
  const versionNote = !c.protocol ? '—' : c.protocol === c.serverProtocol ? 'Same as server' : `Protocol ${c.protocol} (server ${c.serverProtocol})`;
  const addressRows = c.addresses || [];
  fill('#drawer-body',
    h('div', { class: 'section' }, h('h3', null, 'Client'), h('div', { class: 'stat-grid' },
      stat('Client', c.brand || 'Not announced'), stat('Game version', versionNote), stat('Language', c.locale || '—'),
      stat('View distance', c.viewDistance ? `${c.viewDistance} chunks` : '—'), stat('Connected through', c.host || '—'),
      stat('Mod channels', fmt((c.channels || []).length)))),
    h('div', { class: 'section' }, h('h3', null, 'Mods and client features'),
      c.mods?.length ? h('div', { class: 'badges' }, c.mods.map((m) => badge(m, 'violet'))) : empty('The client has not announced any mods.'),
      h('p', { class: 'hint space-above' }, 'Detected from the brand and plugin channels the client announces. A modified client can hide or fake these, so treat them as hints, not proof.'),
      c.channels?.length ? h('details', { class: 'raw' }, h('summary', null, 'Show raw channels'), h('p', { class: 'mono-list' }, c.channels.join(', '))) : null),
    h('div', { class: 'section' }, h('h3', null, 'Network'),
      !c.recordsAddresses ? h('p', { class: 'notice' }, 'IP recording is turned off in config (anti-cheat.record-ip-addresses).') : [
        h('div', { class: 'stat-grid' },
          stat('Current address', p.online ? c.currentAddress || '—' : 'Offline'), stat('Last known address', c.lastAddress || '—'),
          stat('Addresses seen', fmt(addressRows.length)),
          c.locatesAddresses ? stat('Last-known location', c.location?.label || 'Not located yet') : null),
        addressRows.length ? h('div', { class: 'panel flush space-above' }, table([
          { label: 'Address', cls: 'nowrap', cell: (a) => a.address },
          c.locatesAddresses ? { label: 'Location', cell: (a) => a.location?.label || h('span', { class: 'hint' }, 'Unknown') } : null,
          { label: 'First seen', cls: 'nowrap muted', cell: (a) => ago(a.firstSeen) },
          { label: 'Last seen', cls: 'nowrap muted', cell: (a) => ago(a.lastSeen) },
          { label: 'Joins', cls: 'num', cell: (a) => fmt(a.joins) },
          { label: 'Also used by', cell: (a) => a.sharedWith.length ? h('div', { class: 'badges' }, a.sharedWith.map((alt) => playerLink(alt.name || alt.uuid.slice(0, 8), alt.uuid))) : h('span', { class: 'hint' }, 'No one else') },
        ].filter(Boolean), addressRows)) : null,
        c.locatesAddresses && addressRows.some((a) => a.location) ? geoCredit(S.data?.geography?.database) : null]),
    h('div', { class: 'section' }, h('h3', null, 'Sessions'), h('div', { class: 'stat-grid' },
      stat('Total playtime', p.stats.playTicks ? span(p.stats.playTicks * 50) : '—'),
      stat('Sessions', fmt(c.sessions)), stat('Average session', span(c.averageMillis)),
      stat('Longest session', span(c.longestMillis)), stat('Last joined', c.lastJoin ? ago(c.lastJoin) : '—'),
      stat('Last left', p.online ? 'Still online' : c.lastQuit ? ago(c.lastQuit) : '—')),
      h('p', { class: 'hint space-above' }, `Session counts and averages cover joins since ${date(c.trackedSince)}. Total playtime comes from the game's own statistics.`)));
}
function drawerWatchdog(p) {
  const counts = Object.entries(p.watchdog.counts);
  fill('#drawer-body',
    h('div', { class: 'section' }, h('h3', null, 'Live score'), scoreMeter(p.watchdog.score),
      counts.length ? h('div', { class: 'badges space-above' }, counts.map(([k, v]) => badge(`${pretty(k)} ×${v}`, CHECK_TONE[k]))) : null),
    drawerModelSection(p),
    h('div', { class: 'section' }, h('h3', null, 'Saved evidence'), p.watchdog.evidence.length ? h('div', { class: 'timeline' },
      p.watchdog.evidence.map((e) => h('div', { class: 'entry' },
        h('div', null, badge(pretty(e.check), CHECK_TONE[e.check]), h('div', { class: 'by space-above-xs' }, clock(e.time))),
        h('div', null, h('div', null, e.detail), h('div', { class: 'detail' }, `Score ${fmt(e.score, 1)}, ping ${e.ping} ms${e.action === 'KICK' ? ', kicked automatically' : ''}`)))))
      : empty('Watchdog has never flagged this player.')));
}
function drawerModelSection(p) {
  const model = p.watchdog.model || {};
  const windows = model.windows || [];
  const target = { uuid: p.uuid, name: p.name };
  const labelText = { 1: ['Labelled cheating', 'red'], 0: ['Labelled legit', 'green'] };
  return h('div', { class: 'section' },
    h('h3', null, 'Combat model'),
    h('div', { class: 'toolbar' },
      h('button', { type: 'button', class: 'secondary small', on: { click: () => ask('label-player', target, { label: 'cheating' }) } }, 'Label recent fights as cheating'),
      h('button', { type: 'button', class: 'secondary small', on: { click: () => ask('label-player', target, { label: 'legit' }) } }, 'Label recent fights as legit')),
    windows.length ? h('div', { class: 'timeline' }, windows.map((w) => h('div', { class: 'entry' },
      h('div', null, w.probability >= 0 ? badge(pct(w.probability), w.probability >= (model.threshold || 0.95) ? 'red' : w.probability >= 0.5 ? 'amber' : 'green') : badge(`Unusual ${fmt(w.anomaly, 1)}`, w.anomaly > 4 ? 'amber' : ''),
        h('div', { class: 'by space-above-xs' }, clock(w.time))),
      h('div', null,
        h('div', { class: 'badges' }, w.reasons.length ? w.reasons.map((r) => badge(r.feature, 'violet')) : h('span', { class: 'hint' }, 'Nothing stood out')),
        h('div', { class: 'detail space-above-xs' }, `${fmt(w.values.cps, 1)} clicks/s, longest reach ${fmt(w.values.reach, 2)}, aim ${fmt(w.values.aimOffset, 1)}\u00b0 off centre`),
        h('div', { class: 'toolbar tight' },
          labelText[w.label] ? badge(...labelText[w.label]) : null,
          h('button', { type: 'button', class: 'link', on: { click: () => labelWindow(w.id, 'cheating') } }, 'Cheating'),
          h('button', { type: 'button', class: 'link', on: { click: () => labelWindow(w.id, 'legit') } }, 'Legit'),
          w.label >= 0 ? h('button', { type: 'button', class: 'link', on: { click: () => labelWindow(w.id, 'clear') } }, 'Clear') : null)))))
      : empty('No combat recorded yet. Fights with six or more hits become windows the model scores.'),
    h('p', { class: 'hint' }, 'Bars show the model\u2019s cheating probability once it is trained; before that, how unusual the fight was compared with everyone else.'));
}
async function labelWindow(id, label) {
  try {
    const result = await act({ action: 'label-window', window: id, label });
    toast(result.message);
    loadDrawer(true);
  } catch (error) {
    toast('Label not saved', error.message, 'error');
  }
}
function drawerHistory(p) {
  const reports = p.desk.reports;
  fill('#drawer-body',
    h('div', { class: 'section' }, h('h3', null, 'Staff history'), p.desk.history.length ? h('div', { class: 'timeline' },
      p.desk.history.map((r) => h('div', { class: 'entry' },
        h('div', null, badge(pretty(r.type), ACTION_TONE[r.type])),
        h('div', null, h('div', null, r.detail), h('div', { class: 'by' }, `${r.actor}, ${clock(r.time)}`)))))
      : empty('No warnings, notes or sanctions on record.')),
    h('div', { class: 'section' }, h('h3', null, 'Reports involving this player'), reports.length ? h('div', { class: 'timeline' },
      reports.map((r) => h('div', { class: 'entry' },
        h('div', null, badge(r.status === 'OPEN' ? 'Open' : 'Resolved', r.status === 'OPEN' ? 'amber' : 'green')),
        h('div', null, h('div', null, r.reason), h('div', { class: 'by' }, `${r.reporter} reported ${r.target}, ${clock(r.time)}`),
          r.status === 'OPEN' ? h('button', { type: 'button', class: 'link space-above-xs', on: { click: () => ask('resolve', { uuid: r.targetId, name: r.target }, { report: r.id }) } }, 'Resolve') : null))))
      : empty('No reports filed by or about this player.')));
}
function drawerInventory(p) {
  if (!p.live) { fill('#drawer-body', empty('Inventories can be viewed while the player is online.')); return; }
  const slots = (items) => items.length ? h('div', { class: 'slots' }, items.map((i) => h('div', { class: 'slot', title: `Slot ${i.slot}` },
    h('span', { class: `item${i.enchanted ? ' enchanted' : ''}` }, i.name || pretty(i.type)), h('span', { class: 'amt' }, `×${i.amount}`)))) : empty('Empty.');
  fill('#drawer-body',
    h('div', { class: 'section' }, h('h3', null, `Inventory (${p.live.inventory.length} stacks)`), slots(p.live.inventory)),
    h('div', { class: 'section' }, h('h3', null, `Ender chest (${p.live.enderChest.length} stacks)`), slots(p.live.enderChest)),
    h('p', { class: 'hint' }, 'Read-only snapshot. Enchanted items are highlighted.'));
}
function drawerAccess(p) {
  const available = p.allGroups.filter((g) => !p.groups.includes(g));
  const picker = h('select', { 'aria-label': 'Group to add' }, available.map((g) => h('option', { value: g }, g)));
  fill('#drawer-body',
    h('div', { class: 'section' }, h('h3', null, 'Staff groups'), p.groups.length ? h('div', null, p.groups.map((g) => h('div', { class: 'group-row' },
      badge(g, 'blue'), h('button', { type: 'button', class: 'secondary small', on: { click: () => ask('group-remove', { uuid: p.uuid, name: p.name }, { group: g }) } }, 'Remove'))))
      : empty('This player has no staff groups.')),
    available.length ? h('div', { class: 'section' }, h('h3', null, 'Add to a group'), h('div', { class: 'toolbar' },
      h('label', { class: 'field' }, picker),
      h('button', { type: 'button', class: 'secondary', on: { click: () => ask('group-add', { uuid: p.uuid, name: p.name }, { group: picker.value }) } }, 'Add group'))) : null,
    h('p', { class: 'notice' }, 'Groups grant real permissions in game. Changes apply immediately and are recorded in the access log.'));
}

/* ---------- Action dialog ---------- */
// Same rule and wording as /staff in game (StaffManager.validReason): 3 to 180 characters.
const REASON = { min: 3, max: 180, rule: 'Add a clear reason between 3 and 180 characters.' };
const MUTE_TIMES = [['10 minutes', '10'], ['30 minutes', '30'], ['1 hour', '60'], ['6 hours', '360'], ['1 day', '1440'], ['7 days', '10080']];
const BAN_TIMES = [['1 day', '1440'], ['3 days', '4320'], ['7 days', '10080'], ['30 days', '43200'], ['Permanent', 'perm']];
const ACTIONS = {
  warn: { title: (n) => `Warn ${n}`, text: 'They see this in chat, and it is saved to their history.', reason: 'Warning', confirm: 'Send warning' },
  note: { title: (n) => `Add a note for ${n}`, text: 'Notes are visible to staff only.', reason: 'Note', staffOnly: true, confirm: 'Save note' },
  mute: { title: (n) => `Mute ${n}`, text: 'They cannot use chat until the mute ends.', reason: 'Reason', times: MUTE_TIMES, defaultTime: '30', confirm: 'Mute' },
  unmute: { title: (n) => `Unmute ${n}`, text: 'Their chat is restored right away.', confirm: 'Unmute' },
  freeze: { title: (n) => `Freeze ${n}`, text: 'They cannot move between blocks until you unfreeze them.', reason: 'Reason', optional: true, confirm: 'Freeze' },
  unfreeze: { title: (n) => `Unfreeze ${n}`, text: 'They can move again right away.', confirm: 'Unfreeze' },
  kick: { title: (n) => `Kick ${n}`, text: 'They are disconnected and see your reason. They can rejoin.', reason: 'Reason', confirm: 'Kick', danger: true },
  ban: { title: (n) => `Ban ${n}`, text: 'They are disconnected and cannot rejoin until the ban ends.', reason: 'Reason', times: BAN_TIMES, defaultTime: '1440', confirm: 'Ban', danger: true },
  unban: { title: (n) => `Unban ${n}`, text: 'They can join again right away.', confirm: 'Unban' },
  message: { title: (n) => `Message ${n}`, text: 'Sent privately in chat, marked as from staff.', message: 'Message', confirm: 'Send message' },
  'clear-watchdog': { title: (n) => `Clear ${n}'s Watchdog score`, text: 'Resets their live score to zero. Saved evidence is kept, and a note is added to their history.', confirm: 'Clear score' },
  resolve: { title: (n) => `Resolve report about ${n}`, text: 'Closes the report. Your note is saved to the case.', reason: 'Resolution', staffOnly: true, optional: true, confirm: 'Resolve report' },
  'group-add': { title: (n) => `Add ${n} to a staff group`, text: 'This grants every permission in that group.', confirm: 'Add to group' },
  'group-remove': { title: (n) => `Remove ${n} from a staff group`, text: 'They lose that group’s permissions right away.', confirm: 'Remove from group', danger: true },
  'label-player': { title: (n) => `Label ${n}\u2019s recent fights`, text: 'Every combat window from this period gets the label. The model retrains from labels about a minute later, and a note is added to their history.',
    times: [['1 hour', '1'], ['24 hours', '24'], ['7 days', '168'], ['30 days', '720']], defaultTime: '24', timeKey: 'hours', confirm: 'Save label' },
  'train-model': { title: () => 'Train the combat model', text: 'Retrains on every labelled fight and re-validates on players it has never seen. Autonomous actions switch on only if it passes.', confirm: 'Train now' },
  broadcast: { title: () => 'Broadcast to everyone', text: 'Every online player sees this in chat, marked as from staff.', message: 'Message', confirm: 'Send broadcast' },
};
let dialogState = null;
function ask(action, target, extra = {}) {
  const spec = ACTIONS[action];
  const dialog = $('#dialog');
  dialogState = { action, target, extra, time: spec.defaultTime };
  $('#dialog-title').textContent = spec.title(target?.name || 'player');
  $('#dialog-text').textContent = extra.group ? `${spec.text} Group: ${extra.group}.` : extra.label ? `${spec.text} Label: ${extra.label}.` : spec.text;
  $('#dialog-error').textContent = '';
  const fields = [];
  if (spec.times) {
    const presets = h('div', { class: 'presets', role: 'group', 'aria-label': 'Length' });
    for (const [label, value] of spec.times) {
      presets.append(h('button', { type: 'button', 'aria-pressed': String(value === spec.defaultTime), on: { click: (event) => {
        dialogState.time = value;
        for (const b of $$('button', presets)) b.setAttribute('aria-pressed', String(b === event.currentTarget));
      } } }, label));
    }
    fields.push(h('div', { class: 'field' }, h('span', null, 'Length'), presets));
  }
  if (spec.reason) {
    const count = h('span', { class: 'n' }, `0/${REASON.max}`);
    const rule = h('span', { class: 'hint rule', id: 'dialog-reason-rule' },
      h('span', null, `${REASON.min} to ${REASON.max} characters${spec.optional ? ', if you add one' : ''}.${spec.staffOnly ? '' : ' The player may see this.'}`), count);
    const input = h('textarea', { id: 'dialog-reason', maxlength: String(REASON.max), minlength: String(REASON.min), required: spec.optional ? null : true, 'aria-describedby': 'dialog-reason-rule',
      on: { input: () => {
        const n = input.value.trim().length;
        count.textContent = `${n}/${REASON.max}`;
        rule.dataset.state = n > REASON.max ? 'long' : n < REASON.min && (n || !spec.optional) ? 'short' : 'ok';
      } } });
    fields.push(h('label', { class: 'field' }, h('span', null, spec.optional ? `${spec.reason} (optional)` : spec.reason), input, rule));
  }
  if (spec.message) fields.push(h('label', { class: 'field' }, h('span', null, spec.message),
    h('textarea', { id: 'dialog-message', maxlength: '256', required: true })));
  fill('#dialog-fields', fields);
  const confirm = $('#dialog-confirm');
  confirm.textContent = spec.confirm;
  confirm.className = spec.danger ? 'danger' : 'primary';
  confirm.disabled = false;
  dialog.showModal();
  ($('textarea', dialog) || confirm).focus();
}
async function submitDialog(event) {
  event.preventDefault();
  if (!dialogState) return;
  const { action, target, extra, time } = dialogState;
  const spec = ACTIONS[action];
  const reason = $('#dialog-reason')?.value.trim();
  const message = $('#dialog-message')?.value.trim();
  if (spec.reason && (reason || !spec.optional) && (!reason || reason.length < REASON.min || reason.length > REASON.max)) { $('#dialog-error').textContent = REASON.rule; return; }
  if (spec.message && !message) { $('#dialog-error').textContent = 'Write a message first.'; return; }
  const payload = { action, ...extra };
  if (target?.uuid && action !== 'broadcast' && action !== 'train-model') payload.target = target.uuid;
  if (reason) payload.reason = reason;
  if (message) payload.message = message;
  if (spec.times) payload[spec.timeKey || 'minutes'] = time;
  const confirm = $('#dialog-confirm');
  confirm.disabled = true;
  try {
    const result = await act(payload);
    $('#dialog').close();
    toast(result.message);
    await refresh();
    if (S.drawer.uuid) loadDrawer(true);
  } catch (error) {
    $('#dialog-error').textContent = error.message;
    confirm.disabled = false;
  }
}

/* ---------- Toasts and alert notices ---------- */
function toast(title, detail, kind = 'ok', onClick) {
  const el = h('div', { class: `toast${kind === 'ok' ? '' : ` ${kind}`}`, role: kind === 'error' ? 'alert' : 'status', on: onClick ? { click: () => { onClick(); el.remove(); } } : null },
    h('div', null, h('strong', null, title), detail ? h('span', null, detail) : null));
  $('#toasts').append(el);
  setTimeout(() => el.remove(), kind === 'alert' ? 9000 : 5000);
}
function announceAlerts(data) {
  const evidence = data.watchdog.evidence;
  const newest = evidence.length ? Date.parse(evidence[0].time) : 0;
  if (S.newestAlert && newest > S.newestAlert) {
    const fresh = evidence.filter((e) => Date.parse(e.time) > S.newestAlert && (e.score >= data.watchdog.alertScore || e.action === 'KICK'));
    for (const e of fresh.slice(0, 3)) {
      toast(`${e.player}: ${pretty(e.check)}${e.action === 'KICK' ? ', kicked' : ''}`, `${e.detail}. Score ${fmt(e.score, 1)}.`, 'alert', () => openDrawer(e.uuid, e.player));
    }
  }
  S.newestAlert = Math.max(S.newestAlert, newest);
}

/* ---------- Player finder ---------- */
function finderMatches(q) {
  if (!S.data || !q) return [];
  const online = new Set(S.data.players.map((p) => p.uuid));
  const seen = new Set();
  const rows = [];
  for (const p of [...S.data.players, ...S.data.directory]) {
    if (seen.has(p.uuid) || !p.name.toLowerCase().includes(q)) continue;
    seen.add(p.uuid);
    rows.push({ uuid: p.uuid, name: p.name, online: online.has(p.uuid) });
  }
  return rows.sort((a, b) => (b.name.toLowerCase().startsWith(q) - a.name.toLowerCase().startsWith(q)) || (b.online - a.online)).slice(0, 8);
}
function renderFinder() {
  const q = $('#finder').value.trim().toLowerCase();
  const box = $('#finder-results');
  if (!q) { box.hidden = true; return; }
  const rows = finderMatches(q);
  box.replaceChildren(...(rows.length ? rows.map((r, i) => h('button', { type: 'button', role: 'option', 'aria-selected': String(i === 0), on: { mousedown: (e) => { e.preventDefault(); pick(r); } } },
    avatar(r.name, r.uuid, r.online), h('span', null, r.name))) : [h('p', { class: 'empty' }, 'No player by that name has joined.')]));
  box.hidden = false;
}
function pick(row) {
  $('#finder').value = '';
  $('#finder-results').hidden = true;
  openDrawer(row.uuid, row.name);
}

/* ---------- Theme ---------- */
function applyTheme(theme) {
  if (theme) document.documentElement.dataset.theme = theme;
  else delete document.documentElement.dataset.theme;
}
function storedTheme() { try { return localStorage.getItem('watchdog-theme'); } catch { return null; } }

/* ---------- Wiring ---------- */
function segmented(selector, onChange) {
  $(selector).addEventListener('click', (event) => {
    const button = event.target.closest('button[data-value]');
    if (!button) return;
    for (const b of $$(`${selector} button`)) b.setAttribute('aria-selected', String(b === button));
    onChange(button.dataset.value);
  });
}
function init() {
  applyTheme(storedTheme());
  buildNav();
  segmented('#players-scope', (v) => { S.playersScope = v; renderView(); });
  segmented('#reports-scope', (v) => { S.reportsScope = v; renderView(); });
  segmented('#geo-scope', (v) => { S.geoScope = v; renderView(); });
  segmented('#drawer-tabs', (v) => { S.drawer.tab = v; $('#drawer-body').scrollTop = 0; renderDrawer(); });
  for (const id of ['#players-filter', '#geo-filter', '#wd-filter', '#audit-filter']) $(id).addEventListener('input', renderView);
  for (const id of ['#wd-check', '#wd-range', '#wd-action', '#audit-action']) $(id).addEventListener('change', renderView);
  for (const link of $$('[data-goto]')) link.addEventListener('click', () => go(link.dataset.goto));
  $('#scrim').addEventListener('click', closeDrawer);
  $('#dialog-form').addEventListener('submit', submitDialog);
  $('#dialog-cancel').addEventListener('click', () => $('#dialog').close());
  $('#broadcast').addEventListener('click', () => ask('broadcast', null));
  $('#train-model').addEventListener('click', () => ask('train-model', null));
  $('#theme').addEventListener('click', () => {
    const dark = getComputedStyle(document.documentElement).colorScheme.includes('dark');
    const next = dark ? 'light' : 'dark';
    applyTheme(next);
    try { localStorage.setItem('watchdog-theme', next); } catch { /* private window */ }
  });
  const finder = $('#finder');
  finder.addEventListener('input', renderFinder);
  finder.addEventListener('blur', () => { $('#finder-results').hidden = true; });
  finder.addEventListener('keydown', (event) => {
    const options = $$('#finder-results button');
    const index = options.findIndex((o) => o.getAttribute('aria-selected') === 'true');
    if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      event.preventDefault();
      const next = (index + (event.key === 'ArrowDown' ? 1 : -1) + options.length) % options.length;
      options.forEach((o, i) => o.setAttribute('aria-selected', String(i === next)));
    } else if (event.key === 'Enter') {
      const rows = finderMatches(finder.value.trim().toLowerCase());
      if (rows[Math.max(0, index)]) pick(rows[Math.max(0, index)]);
    } else if (event.key === 'Escape') { finder.value = ''; renderFinder(); finder.blur(); }
  });
  document.addEventListener('keydown', (event) => {
    const typing = /^(INPUT|TEXTAREA|SELECT)$/.test(document.activeElement?.tagName);
    if (event.key === '/' && !typing && !$('#dialog').open) { event.preventDefault(); finder.focus(); }
    if (event.key === 'Escape' && !$('#dialog').open && S.drawer.uuid) closeDrawer();
  });
  document.addEventListener('visibilitychange', () => { if (!document.hidden) refresh(); });
  window.addEventListener('hashchange', () => go(location.hash.slice(1), false));
  go(location.hash.slice(1) || 'overview', false);
  loadSession().catch(() => setLink('lost', 'Signed out'));
  refresh();
}
init();
