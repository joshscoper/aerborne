// Runs before the homepage paints: decides whether to play the fly-through-the-clouds intro.
// Plays once per browser tab, never with reduced motion. Add ?intro=1 to the URL to replay it.
(function () {
  try {
    var root = document.documentElement;
    var force = /[?&]intro=1(&|$)/.test(window.location.search);
    if (!force && window.matchMedia && window.matchMedia("(prefers-reduced-motion: reduce)").matches) return;
    if (!force && window.sessionStorage.getItem("aerborne-intro")) return;
    root.className += " intro-pending";
  } catch (e) {
    // Storage blocked (private mode): skip the intro rather than replaying it on every visit.
  }
})();
