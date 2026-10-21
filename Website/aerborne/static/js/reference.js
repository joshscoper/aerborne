(function () {
  'use strict';
  var navigation = document.querySelector('[data-handbook-nav]');
  if (navigation && window.matchMedia('(max-width: 900px)').matches) navigation.open = false;
  var reader = document.querySelector('[data-guide-reader]');
  if (!reader) return;
  var sections = Array.prototype.slice.call(reader.querySelectorAll('[data-guide-step]'));
  var links = Array.prototype.slice.call(reader.querySelectorAll('[data-section-link]'));
  var tools = reader.querySelector('[data-reader-tools]');
  var position = reader.querySelector('[data-reader-position]');
  var progress = reader.querySelector('[data-reader-progress]');
  var controls = reader.querySelector('[data-step-controls]');
  var back = reader.querySelector('[data-step-back]');
  var next = reader.querySelector('[data-step-next]');
  var finished = reader.querySelector('[data-guide-finished]');
  var contents = reader.querySelector('[data-guide-contents]');
  var announcement = reader.querySelector('[data-guide-announcement]');
  var mode = 'all';
  var current = 0;
  if (!sections.length) return;
  if (window.matchMedia('(max-width: 760px)').matches) contents.open = false;

  function readerOffset() {
    var masthead = document.querySelector('.masthead');
    var offset = (masthead ? masthead.getBoundingClientRect().height : 68) + 20;
    if (window.matchMedia('(max-width: 760px)').matches) offset += tools.getBoundingClientRect().height + 12;
    return offset;
  }

  function indexFromHash() {
    var id;
    try { id = decodeURIComponent(window.location.hash.slice(1)); } catch (error) { return -1; }
    var target = document.getElementById(id);
    var section = target && target.closest('[data-guide-step]');
    return sections.indexOf(section);
  }
  function update(index, focus, push) {
    current = Math.max(0, Math.min(index, sections.length - 1));
    sections.forEach(function (section, i) { section.hidden = mode === 'steps' && i !== current; });
    links.forEach(function (link, i) {
      if (i === current) link.setAttribute('aria-current', 'location');
      else link.removeAttribute('aria-current');
    });
    position.textContent = 'Section ' + (current + 1) + ' of ' + sections.length;
    progress.value = current + 1;
    controls.hidden = mode !== 'steps';
    reader.querySelectorAll('.related, .guide-next-guides').forEach(function (element) {
      element.hidden = mode === 'steps' && current < sections.length - 1;
    });
    back.hidden = current === 0;
    next.hidden = current === sections.length - 1;
    finished.hidden = current !== sections.length - 1;
    if (push && window.location.hash !== '#' + sections[current].id) {
      // Embedded previews may deny history changes; section navigation still works.
      try { window.history.pushState(null, '', '#' + sections[current].id); } catch (error) {}
    }
    if (focus) {
      sections[current].querySelector('h2').focus({preventScroll: true});
      window.requestAnimationFrame(function () {
        window.scrollTo({top: window.scrollY + sections[current].getBoundingClientRect().top - readerOffset(), behavior: 'auto'});
      });
    }
  }
  reader.querySelectorAll('[data-reader-mode]').forEach(function (button) {
    button.addEventListener('click', function () {
      mode = button.getAttribute('data-reader-mode');
      reader.querySelectorAll('[data-reader-mode]').forEach(function (choice) {
        choice.setAttribute('aria-pressed', String(choice === button));
      });
      update(current, false, false);
    });
  });
  links.forEach(function (link, index) {
    link.addEventListener('click', function (event) {
      event.preventDefault();
      update(index, true, true);
    });
  });
  reader.querySelectorAll('a[href="#guide-contents"]').forEach(function (link) {
    link.addEventListener('click', function () { contents.open = true; });
  });
  back.addEventListener('click', function () { update(current - 1, true, true); });
  next.addEventListener('click', function () { update(current + 1, true, true); });
  window.addEventListener('hashchange', function () {
    var index = indexFromHash();
    if (index >= 0 || !window.location.hash) update(Math.max(index, 0), true, false);
  });
  var scheduled = false;
  window.addEventListener('scroll', function () {
    if (mode === 'steps' || scheduled) return;
    scheduled = true;
    window.requestAnimationFrame(function () {
      scheduled = false;
      if (mode === 'steps') return;
      var index = 0;
      var threshold = readerOffset() + 30;
      sections.forEach(function (section, i) { if (section.getBoundingClientRect().top <= threshold) index = i; });
      if (index !== current) update(index, false, false);
    });
  }, {passive: true});

  function el(tag, className, text) {
    var element = document.createElement(tag);
    if (className) element.className = className;
    if (text !== undefined) element.textContent = text;
    return element;
  }
  function copyContents(from, to) {
    Array.prototype.forEach.call(from.childNodes, function (node) { to.appendChild(node.cloneNode(true)); });
  }
  var lookupLabels = {
    'installed gem tier': 'Choose the tier of the gem you want to recover',
    'installed tier': 'Choose the tier of the gem you want to recover',
    'stage': 'Choose an island stage',
    'station': 'Choose a machine',
    'spawner': 'Choose a spawner',
    'skill': 'Choose a skill',
    'crop': 'Choose a crop',
    'different aspects': 'Choose how many different Aspects to use',
    'exact recipe': 'Choose how many different Aspects to use',
    'tier': 'Choose an enchantment tier',
    'message': 'Choose the message you are seeing',
    'action': 'Choose what you want to do'
  };
  reader.querySelectorAll('.guide-table').forEach(function (wrapper, tableIndex) {
    var table = wrapper.querySelector('table');
    var headers = Array.prototype.map.call(table.querySelectorAll('thead th'), function (cell) { return cell.textContent.trim(); });
    var rows = Array.prototype.slice.call(table.querySelectorAll('tbody tr'));
    var key = (headers[0] || '').toLowerCase();
    if (!rows.length || headers.length < 2 || rows.some(function (row) { return row.cells.length !== headers.length; })) return;
    var label = lookupLabels[key];
    if (label) {
      var sourceCells = rows.map(function (row) {
        return Array.prototype.map.call(row.cells, function (cell) { return cell.cloneNode(true); });
      });
      var isGem = key === 'installed gem tier' || key === 'installed tier';
      var widget = el('div', 'guide-lookup' + (isGem ? ' is-gem' : ''));
      var labelElement = el('label', 'guide-lookup-label', label);
      var result = el('div', 'guide-lookup-result');
      result.setAttribute('aria-live', 'polite');
      result.setAttribute('aria-atomic', 'true');
      var buttons = [];
      function choose(index) {
        var cells = sourceCells[index];
        while (result.firstChild) result.removeChild(result.firstChild);
        result.appendChild(el('h3', '', (isGem ? 'Gem tier ' : '') + cells[0].textContent.trim()));
        var facts = el('dl');
        headers.slice(1).forEach(function (header, i) {
          var fact = el('div');
          fact.appendChild(el('dt', '', header));
          var value = el('dd');
          copyContents(cells[i + 1], value);
          fact.appendChild(value);
          facts.appendChild(fact);
        });
        result.appendChild(facts);
        enhanceCommands(result);
        buttons.forEach(function (button, i) { button.setAttribute('aria-pressed', String(i === index)); });
      }
      if (isGem && rows.length <= 4) {
        // A group label, rather than a label targeting no form control.
        var groupLabel = el('p', 'guide-lookup-label', label);
        groupLabel.id = 'lookup-label-' + tableIndex;
        widget.appendChild(groupLabel);
        var group = el('div', 'guide-lookup-buttons');
        group.setAttribute('role', 'group');
        group.setAttribute('aria-labelledby', groupLabel.id);
        rows.forEach(function (row, index) {
          var button = el('button', '', row.cells[0].textContent.trim());
          button.type = 'button';
          button.setAttribute('aria-label', 'Gem tier ' + row.cells[0].textContent.trim());
          button.addEventListener('click', function () { choose(index); });
          buttons.push(button);
          group.appendChild(button);
        });
        widget.appendChild(group);
      } else {
        var select = el('select');
        select.id = 'guide-lookup-' + tableIndex;
        labelElement.htmlFor = select.id;
        rows.forEach(function (row, index) {
          var option = el('option', '', row.cells[0].textContent.trim());
          option.value = String(index);
          select.appendChild(option);
        });
        select.addEventListener('change', function () { choose(Number(select.value)); });
        widget.appendChild(labelElement);
        widget.appendChild(select);
      }
      widget.appendChild(result);
      choose(0);
      var comparison = el('details', 'guide-comparison');
      comparison.appendChild(el('summary', '', 'Compare all ' + rows.length + ' options'));
      wrapper.parentNode.insertBefore(widget, wrapper);
      wrapper.parentNode.insertBefore(comparison, wrapper);
      comparison.appendChild(wrapper);
    } else if (headers.length === 2 && table.querySelector('code')) {
      var list = el('dl', 'guide-command-list');
      rows.forEach(function (row) {
        var entry = el('div', 'guide-command-row');
        entry.setAttribute('data-search', row.textContent.toLowerCase());
        var term = el('dt');
        var description = el('dd');
        copyContents(row.cells[0], term);
        copyContents(row.cells[1], description);
        entry.appendChild(term);
        entry.appendChild(description);
        list.appendChild(entry);
      });
      wrapper.parentNode.replaceChild(list, wrapper);
      if (rows.length >= 8) {
        var filterLabel = el('label', 'guide-table-search', 'Find a command in this section');
        var filter = el('input');
        filter.type = 'search';
        filter.placeholder = 'Search a command or what it does';
        filterLabel.appendChild(filter);
        var count = el('p', 'guide-filter-result');
        count.setAttribute('role', 'status');
        list.parentNode.insertBefore(filterLabel, list);
        list.parentNode.insertBefore(count, list);
        filter.addEventListener('input', function () {
          var words = filter.value.toLowerCase().trim().split(/\s+/);
          var shown = 0;
          list.querySelectorAll('.guide-command-row').forEach(function (entry) {
            var text = entry.getAttribute('data-search');
            entry.hidden = !words.every(function (word) { return text.indexOf(word) !== -1; });
            if (!entry.hidden) shown++;
          });
          count.textContent = shown ? shown + ' matching command' + (shown === 1 ? '' : 's') : 'No matching commands. Try another word or clear the search.';
        });
      }
    }
  });
  function enhanceCommands(root) {
    root.querySelectorAll('code').forEach(function (code) {
      var command = code.textContent.trim();
      if (command.charAt(0) !== '/' || command.indexOf('\n') !== -1 || code.closest('pre') || code.closest('.guide-command')) return;
      var wrap = el('span', 'guide-command');
      code.parentNode.insertBefore(wrap, code);
      wrap.appendChild(code);
      var template = /[<\[]/.test(command);
      var button = el('button', 'guide-copy', 'Copy');
      button.type = 'button';
      button.setAttribute('aria-label', 'Copy ' + (template ? 'command template ' : '') + command);
      button.addEventListener('click', function () {
        if (!navigator.clipboard || !navigator.clipboard.writeText) {
          announcement.textContent = 'Select the command text and use your device’s copy action.';
          return;
        }
        navigator.clipboard.writeText(command).then(function () {
          button.textContent = 'Copied';
          announcement.textContent = template ? 'Command template copied. Replace the placeholders before using it in game.' : 'Copied ' + command;
          window.setTimeout(function () { button.textContent = 'Copy'; }, 1600);
        }).catch(function () { announcement.textContent = 'Copy was blocked. Select the command text and copy it manually.'; });
      });
      wrap.appendChild(button);
    });
  }
  reader.querySelectorAll('.guide-prose').forEach(enhanceCommands);
  tools.hidden = false;
  var initial = indexFromHash();
  update(initial >= 0 ? initial : 0, initial >= 0, false);
})();
