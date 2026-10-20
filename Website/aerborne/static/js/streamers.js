/* Featured creators: refresh public statuses without loading third-party embeds. */
(function () {
  'use strict';
  var root = document.querySelector('[data-streamers]');
  if (!root) return;
  var cards = Array.prototype.slice.call(root.querySelectorAll('[data-stream-card]'));
  if (!cards.length) return;
  var grid = root.querySelector('[data-stream-grid]');
  var platform = document.getElementById('stream-platform');
  var liveOnly = document.getElementById('stream-live-only');
  var summary = root.querySelector('[data-stream-summary]');
  var empty = root.querySelector('[data-stream-empty]');
  var busy = false;
  var states = {live: 'Live now', offline: 'Offline', checking: 'Checking…',
    unavailable: 'Status unavailable', unconfigured: 'Live status unavailable'};
  root.querySelector('[data-stream-filters]').hidden = false;

  function filter() {
    var visible = 0, live = 0, checking = 0, unknown = 0;
    cards.forEach(function (card) {
      var state = card.getAttribute('data-state');
      var available = card.getAttribute('data-available') !== '0';
      var matches = platform.value === 'all' || card.getAttribute('data-platform') === platform.value;
      if (available && matches && state === 'live') live++;
      if (available && matches && state === 'checking') checking++;
      if (available && matches && (state === 'unconfigured' || state === 'unavailable')) unknown++;
      card.hidden = !available || !matches || (liveOnly.checked && state !== 'live');
      if (!card.hidden) visible++;
    });
    empty.hidden = visible > 0;
    var message = live + ' live now';
    if (checking) message += ' · Checking ' + checking + '…';
    if (unknown) message += ' · ' + unknown + ' status unavailable';
    if (summary.textContent !== message) summary.textContent = message;
  }

  function apply(card, data) {
    var state = data && states[data.state] ? data.state : 'unavailable';
    card.setAttribute('data-state', state);
    card.querySelector('[data-stream-badge]').textContent = states[state];
    var title = card.querySelector('[data-stream-title]');
    title.textContent = state === 'live' ? data.title || '' : '';
    title.hidden = !title.textContent;
    var details = card.querySelector('[data-stream-details]');
    var message = state === 'offline' ? 'Catch their next stream on this channel' :
      state === 'checking' ? 'Checking the channel’s current status…' :
      state === 'live' ? 'Streaming now' : 'Visit the channel for its current live status.';
    if (state === 'live' && typeof data.viewers === 'number') message = data.viewers.toLocaleString() + ' watching';
    if ((state === 'live' || state === 'offline') && data.checked_at) {
      message += ' · Checked ' + new Date(data.checked_at * 1000).toLocaleTimeString([], {hour: '2-digit', minute: '2-digit'});
    }
    details.textContent = message;
    var watch = card.querySelector('[data-watch-link]');
    watch.href = state === 'live' && data.watch_url ? data.watch_url : watch.getAttribute('data-channel-url');
    watch.querySelector('[data-watch-label]').textContent = state === 'live' ? 'Watch live' : 'Visit channel';
  }

  function order() {
    cards.sort(function (a, b) {
      var liveA = a.getAttribute('data-state') === 'live', liveB = b.getAttribute('data-state') === 'live';
      return liveA !== liveB ? (liveA ? -1 : 1) : Number(a.getAttribute('data-order')) - Number(b.getAttribute('data-order'));
    });
    var focus = document.activeElement;
    cards.forEach(function (card, index) {
      if (grid.children[index] !== card) grid.insertBefore(card, grid.children[index] || null);
    });
    if (focus && root.contains(focus) && document.activeElement !== focus) focus.focus({preventScroll: true});
  }

  function refresh() {
    if (busy || document.hidden) return;
    busy = true;
    var controller = window.AbortController ? new AbortController() : null;
    var timer = controller ? setTimeout(function () { controller.abort(); }, 20000) : null;
    fetch(root.getAttribute('data-status-url'), {cache: 'no-store', signal: controller ? controller.signal : undefined})
      .then(function (response) { if (!response.ok) throw new Error('unavailable'); return response.json(); })
      .then(function (data) {
        if (!data.channels || typeof data.channels !== 'object') throw new Error('unavailable');
        cards.forEach(function (card) {
          var item = data.channels[card.getAttribute('data-slug')];
          card.setAttribute('data-available', item ? '1' : '0');
          apply(card, item || {state: 'unavailable'});
        });
      }).catch(function () {
        cards.forEach(function (card) { apply(card, {state: 'unavailable'}); });
      }).then(function () {
        if (timer) clearTimeout(timer);
        busy = false;
        order();
        filter();
      });
  }
  platform.addEventListener('change', filter);
  liveOnly.addEventListener('change', filter);
  document.addEventListener('visibilitychange', function () { if (!document.hidden) refresh(); });
  filter();
  refresh();
  setInterval(refresh, 60000);
})();
