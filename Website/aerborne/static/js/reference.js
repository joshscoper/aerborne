(function () {
  'use strict';
  var navigation = document.querySelector('[data-handbook-nav]');
  if (navigation && window.matchMedia('(max-width: 900px)').matches) navigation.open = false;
  var prose = document.querySelector('[data-guide-prose]');
  var toc = document.querySelector('[data-page-toc]');
  if (!prose || !toc) return;
  var headings = prose.querySelectorAll('h2, h3');
  if (headings.length < 2) return;
  var list = toc.querySelector('ol');
  Array.prototype.forEach.call(headings, function (heading, index) {
    var slug = heading.textContent.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '');
    heading.id = 'guide-' + (slug || 'section') + '-' + (index + 1);
    var item = document.createElement('li');
    var link = document.createElement('a');
    link.href = '#' + heading.id;
    link.textContent = heading.textContent;
    item.appendChild(link);
    list.appendChild(item);
  });
  toc.hidden = false;
})();
