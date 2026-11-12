// A continuous scroll reveal, using native scrolling and one frame per scroll event.
(function () {
  "use strict";

  var root = document.documentElement;
  var motion = window.matchMedia("(prefers-reduced-motion: reduce)");
  var hero = document.querySelector(".home-hero");
  if (!hero) return;

  var landscape = document.querySelector("[data-home-landscape]");
  var copy = hero.querySelector(".home-hero-copy");
  var cue = document.querySelector(".home-scroll-cue");
  var join = document.getElementById("join");
  // Keep all optional sections in the same visual rhythm, including CMS content.
  var sections = document.querySelectorAll(".home-news .home-section-heading, .home-news .news-card, .home-showcases .wrap");
  Array.prototype.forEach.call(sections, function (element) {
    element.setAttribute("data-home-reveal", "");
  });
  var reveals = Array.prototype.slice.call(document.querySelectorAll("[data-home-reveal]"));
  var queued = false;

  function clamp(value) { return Math.max(0, Math.min(1, value)); }

  function update() {
    queued = false;
    if (document.hidden) return;
    var height = window.innerHeight;
    var heroBox = hero.getBoundingClientRect();
    var joinBox = join ? join.getBoundingClientRect() : null;
    var atJoin = joinBox && joinBox.top < height && joinBox.top + joinBox.height > 80;
    // Keep joining within reach after the hero, including with reduced motion.
    // The fixed link steps aside while the full joining instructions are on screen.
    if (heroBox.top + heroBox.height < 80 && !atJoin) root.classList.add("home-away");
    else root.classList.remove("home-away");
    if (motion.matches) return;
    var travel = clamp(-heroBox.top / heroBox.height);
    // Read all geometry before applying styles to avoid repeated layout work.
    var frames = reveals.map(function (element) {
      var box = element.getBoundingClientRect();
      var elementHeight = element.offsetHeight;
      // Measure the layout position, excluding our previous translation and scale.
      // Otherwise the animation feeds into its next measurement and jumps at rest.
      var currentY = parseFloat(element.style.getPropertyValue("--reveal-y")) || 0;
      var currentScale = parseFloat(element.style.getPropertyValue("--reveal-scale")) || 1;
      var top = box.top - currentY - elementHeight * (1 - currentScale) / 2;
      var distance = Math.min(height * .7, elementHeight * .65 + 160);
      var progress = clamp((height * .94 - top) / distance);
      // Smoothstep eases both ends while keeping the reveal tied to scroll position.
      var eased = progress * progress * (3 - 2 * progress);
      return { element: element, opacity: eased, y: (1 - eased) * 64, scale: .97 + eased * .03 };
    });
    landscape.style.setProperty("--home-drift", (travel * heroBox.height * .2).toFixed(1) + "px");
    landscape.style.setProperty("--home-scale", (1 + travel * .06).toFixed(3));
    copy.style.setProperty("--home-copy-drift", (-travel * 45).toFixed(1) + "px");
    frames.forEach(function (frame) {
      frame.element.style.setProperty("--reveal-opacity", frame.opacity.toFixed(3));
      frame.element.style.setProperty("--reveal-y", frame.y.toFixed(1) + "px");
      frame.element.style.setProperty("--reveal-scale", frame.scale.toFixed(3));
    });
  }

  function schedule() {
    if (!queued) { queued = true; window.requestAnimationFrame(update); }
  }

  function measureHeader() {
    // Includes announcement/staff bars so the landing still fills exactly one screen.
    root.style.setProperty("--home-header-height", Math.max(0, hero.getBoundingClientRect().top + window.scrollY).toFixed(1) + "px");
    schedule();
  }

  function preferencesChanged() {
    if (motion.matches) {
      root.classList.remove("home-motion", "intro-pending");
    } else {
      root.classList.add("home-motion");
    }
    update();
  }

  function goToSection(event, destination) {
    if (!destination) return;
    event.preventDefault();
    destination.scrollIntoView({ behavior: motion.matches ? "auto" : "smooth", block: "start" });
    destination.setAttribute("tabindex", "-1");
    destination.focus({ preventScroll: true });
  }
  cue.addEventListener("click", function (event) {
    goToSection(event, document.getElementById("discover"));
  });
  Array.prototype.forEach.call(document.querySelectorAll(".home-hero-copy a[href^='#'], .home-paths a[href^='#']"), function (link) {
    link.addEventListener("click", function (event) {
      goToSection(event, document.getElementById(link.getAttribute("href").slice(1)));
    });
  });
  window.addEventListener("scroll", schedule, { passive: true });
  window.addEventListener("resize", measureHeader, { passive: true });
  window.addEventListener("load", measureHeader, { once: true });
  window.addEventListener("pageshow", function (event) {
    if (event.persisted) root.classList.remove("intro-pending");
    measureHeader();
  });
  document.addEventListener("visibilitychange", schedule);
  if (motion.addEventListener) motion.addEventListener("change", preferencesChanged);
  else if (motion.addListener) motion.addListener(preferencesChanged);
  if (window.ResizeObserver) new ResizeObserver(measureHeader).observe(document.querySelector(".masthead"));
  measureHeader();
  preferencesChanged();
})();
