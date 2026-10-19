(function () {
  'use strict';
  var directory = document.querySelector('[data-forum-directory]');
  if (!directory) return;
  var search = directory.querySelector('[data-forum-search]');
  var input = search.querySelector('input');
  var status = directory.querySelector('[data-forum-results]');
  var groups = directory.querySelectorAll('[data-forum-group]');
  search.hidden = false;
  input.addEventListener('input', function () {
    var query = input.value.trim().toLowerCase();
    var count = 0;
    Array.prototype.forEach.call(groups, function (group) {
      var visible = 0;
      Array.prototype.forEach.call(group.querySelectorAll('[data-forum-node]'), function (node) {
        node.hidden = node.querySelector('.node-main').textContent.toLowerCase().indexOf(query) === -1;
        if (!node.hidden) visible++;
      });
      group.hidden = visible === 0;
      count += visible;
    });
    status.hidden = !query;
    directory.querySelector('.forum-jumps').hidden = !!query;
    status.textContent = count ? count + ' forum' + (count === 1 ? '' : 's') + ' found' : 'No forums match. Try help, trading or guides.';
  });
})();
