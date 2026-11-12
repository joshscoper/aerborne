// Runs before the homepage paints: decides whether to play the fly-through-the-clouds intro.
// Fresh visits get the entrance; anchors, history restoration and reduced motion skip it.
(function () {
  try {
    var root = document.documentElement;
    var force = /[?&]intro=1(&|$)/.test(window.location.search);
    if (window.matchMedia && window.matchMedia("(prefers-reduced-motion: reduce)").matches) return;
    var navigation = window.performance && window.performance.getEntriesByType ? window.performance.getEntriesByType("navigation")[0] : null;
    if (!force && (window.location.hash || (navigation && navigation.type === "back_forward"))) return;
    root.classList.add("intro-pending");
  } catch (e) {
    // Keep the page immediately usable if the entrance cannot initialize.
  }
})();
