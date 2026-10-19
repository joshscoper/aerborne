(function () {
  'use strict';
  var form = document.querySelector('[data-profile-editor]');
  if (!form) return;
  var tabs = form.querySelector('[data-editor-tabs]');
  var buttons = Array.prototype.slice.call(tabs.querySelectorAll('[data-editor-tab]'));
  var panels = Array.prototype.slice.call(form.querySelectorAll('[data-editor-panel]'));
  tabs.hidden = false;
  tabs.setAttribute('role', 'tablist');
  function activate(button) {
    buttons.forEach(function (b) { b.setAttribute('aria-selected', String(b === button)); b.tabIndex = b === button ? 0 : -1; });
    panels.forEach(function (panel) { panel.hidden = panel.id !== button.getAttribute('data-editor-tab'); });
  }
  buttons.forEach(function (button, index) {
    var target = document.getElementById(button.getAttribute('data-editor-tab'));
    button.setAttribute('role', 'tab');
    button.setAttribute('aria-controls', target.id);
    target.setAttribute('role', 'tabpanel');
    target.setAttribute('aria-labelledby', button.id);
    button.addEventListener('click', function () { activate(button); });
    button.addEventListener('keydown', function (e) {
      var next = index;
      if (e.key === 'ArrowRight') next = (index + 1) % buttons.length;
      else if (e.key === 'ArrowLeft') next = (index + buttons.length - 1) % buttons.length;
      else if (e.key === 'Home') next = 0;
      else if (e.key === 'End') next = buttons.length - 1;
      else return;
      e.preventDefault(); activate(buttons[next]); buttons[next].focus();
    });
  });
  activate(buttons[0]);
  var initial = new URLSearchParams(new FormData(form)).toString();
  function updatePreview() {
    ['avatar', 'banner'].forEach(function (name) {
      var radio = form.querySelector('input[name="' + name + '"]:checked');
      if (!radio) return;
      var source = radio.closest('label').querySelector('img');
      document.querySelector('[data-preview-' + name + ']').src = source.src;
    });
    var avatar = form.querySelector('input[name="avatar"]:checked');
    var frame = document.querySelector('[data-preview-frame]');
    if (avatar && frame) frame.classList.toggle('framed-sprite', avatar.value.indexOf('sprite:') === 0);
    document.querySelector('[data-preview-headline]').textContent = form.elements.headline.value.trim() || 'Your next adventure starts here.';
    var dirty = new URLSearchParams(new FormData(form)).toString() !== initial;
    form.querySelector('[data-save-state]').textContent = dirty ? 'You have unsaved changes.' : 'Changes appear on your profile after saving.';
  }
  form.addEventListener('input', updatePreview);
  form.addEventListener('change', updatePreview);
  form.addEventListener('invalid', function (e) {
    var panel = e.target.closest('[data-editor-panel]');
    if (!panel) return;
    var button = buttons.filter(function (b) { return b.getAttribute('data-editor-tab') === panel.id; })[0];
    if (button) activate(button);
  }, true);
  form.addEventListener('submit', function () { form.querySelector('[data-save-state]').textContent = 'Saving your profile…'; });
})();
