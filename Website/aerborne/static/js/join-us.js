(function () {
  'use strict';
  var role = document.querySelector('#role');
  if (!role) return;
  function updateRole() {
    var option = role.options[role.selectedIndex];
    document.querySelector('[data-role-description]').textContent = option.dataset.description || 'Pick the closest fit. You can mention other interests in your answers.';
    document.querySelector('[data-role-prompt]').textContent = option.dataset.prompt || 'How would you approach your first contribution in this role?';
  }
  role.addEventListener('change', updateRole);
  updateRole();
  var errors = document.querySelector('[data-error-summary]');
  if (errors) errors.focus();
})();
