/* Profile badges: hovering over, focusing or tapping a badge shows what it's for in the caption below the grid. */
(function () {
  "use strict";
  var grid = document.querySelector("[data-badges]");
  var caption = document.querySelector("[data-badge-caption]");
  if (!grid || !caption) return;
  var badges = Array.prototype.slice.call(grid.querySelectorAll(".badge"));
  function show(badge) {
    badges.forEach(function (b) {
      b.classList.toggle("is-active", b === badge);
      b.setAttribute("aria-pressed", String(b === badge));
    });
    caption.innerHTML = badge.querySelector(".badge-info").innerHTML;
  }
  badges.forEach(function (badge) {
    badge.removeAttribute("title"); // The caption replaces the browser's own tooltip.
    badge.addEventListener("mouseenter", function () { show(badge); });
    badge.addEventListener("focus", function () { show(badge); });
    badge.addEventListener("click", function () { show(badge); });
  });
})();
