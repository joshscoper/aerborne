(function () {
  'use strict';
  document.querySelectorAll('[data-list-search]').forEach(function (input) {
    var key = input.getAttribute('data-list-search');
    var list = document.querySelector('[data-search-list="' + key + '"]');
    if (!list) return;
    var empty = document.querySelector('[data-search-empty="' + key + '"]');
    input.addEventListener('input', function () {
      var query = input.value.trim().toLowerCase(), count = 0;
      list.querySelectorAll('[data-search-item]').forEach(function (item) {
        var match = !query || item.textContent.toLowerCase().indexOf(query) !== -1;
        item.hidden = !match;
        if (match) count++;
        if (query && match) item.open = true;
        else if (!query) item.open = false;
      });
      if (empty) empty.hidden = count > 0;
    });
  });
  document.querySelectorAll('[data-filter-tabs]').forEach(function (tabs) {
    var key = tabs.getAttribute('data-filter-tabs');
    var list = document.querySelector('[data-filter-list="' + key + '"]');
    var empty = document.querySelector('[data-filter-empty="' + key + '"]');
    if (!list) return;
    tabs.addEventListener('click', function (event) {
      var button = event.target.closest('button[data-filter]');
      if (!button) return;
      tabs.querySelectorAll('button').forEach(function (b) { b.setAttribute('aria-pressed', String(b === button)); });
      var count = 0;
      list.querySelectorAll('[data-filter-item]').forEach(function (item) {
        item.hidden = button.dataset.filter !== 'all' && item.dataset.filterItem !== button.dataset.filter;
        if (!item.hidden) count++;
      });
      if (empty) empty.hidden = count > 0;
    });
  });
  var search = document.querySelector('[data-ranking-search]');
  if (search) search.addEventListener('input', function () {
    var query = search.value.trim().toLowerCase(), count = 0;
    var rows = document.querySelectorAll('[data-ranking-name]');
    rows.forEach(function (row) { row.hidden = !!query && row.dataset.rankingName.indexOf(query) === -1; if (!row.hidden) count++; });
    document.querySelector('[data-ranking-count]').textContent = count + ' of ' + rows.length + ' entries shown · Original ranks preserved';
    document.querySelector('[data-ranking-empty]').hidden = count > 0;
  });
})();
