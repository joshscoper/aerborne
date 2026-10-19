// Aerborne Arcade engine: canvas setup, image loading, the start/finish round trip and the overlay.
// Each game registers a factory with Arcade.register(slug, factory). The factory gets an env object:
//   env.ctx, env.W, env.H  - 2D context and logical size (480 x 640)
//   env.img(name)         - a preloaded image (gems by Aspect slug, plus "pufflet", "kitekit", "crest")
//   env.score(n)          - report the current score
//   env.end()             - finish the run
//   env.on(target, type, fn) - add a listener that's removed when the run ends
//   env.reduced           - true when the visitor prefers reduced motion
// and returns { start(), stop() }.
(function () {
  "use strict";
  var factories = {};
  window.Arcade = { register: function (slug, factory) { factories[slug] = factory; } };

  var W = 480, H = 640;

  function loadImages(base) {
    var names = ["might", "precision", "aegis", "ward", "celerity", "vitality", "resolve", "affinity", "fortune"];
    var images = {};
    var waits = [];
    function add(name, src) {
      var im = new Image();
      images[name] = im;
      waits.push(new Promise(function (resolve) { im.onload = im.onerror = resolve; }));
      im.src = src;
    }
    names.forEach(function (n) { add(n, base + "aspects/" + n + "-96.webp"); });
    add("pufflet", base + "companions/pufflet-avatar.webp");
    add("kitekit", base + "companions/kitekit-avatar.webp");
    add("crest", base + "brand/skycrest-192.webp");
    return Promise.all(waits).then(function () { return images; });
  }

  function post(url, csrf, body) {
    return fetch(url, {
      method: "POST",
      credentials: "same-origin",
      headers: { "Content-Type": "application/json", "X-CSRF-Token": csrf, Accept: "application/json" },
      body: JSON.stringify(body || {})
    }).then(function (res) {
      return res.json().catch(function () { throw new Error("Invalid response"); }).then(function (data) {
        if (!res.ok) throw new Error(data.message || "Your session expired. Reload the page and try again.");
        return data;
      });
    });
  }

  document.addEventListener("DOMContentLoaded", function () {
    var root = document.querySelector("[data-arcade]");
    if (!root) return;
    var slug = root.getAttribute("data-arcade");
    var canvas = root.querySelector("canvas");
    var overlay = root.querySelector("[data-overlay]");
    var title = overlay.querySelector("[data-title]");
    var text = overlay.querySelector("[data-text]");
    var button = overlay.querySelector("[data-play]");
    var pauseButton = root.querySelector("[data-pause]");
    var scoreEl = root.querySelector("[data-score]");
    var csrf = root.getAttribute("data-csrf");
    var startUrl = root.getAttribute("data-start");
    var finishBase = root.getAttribute("data-finish");
    var reduced = window.matchMedia && window.matchMedia("(prefers-reduced-motion: reduce)").matches;

    var dpr = Math.min(window.devicePixelRatio || 1, 2);
    canvas.width = W * dpr;
    canvas.height = H * dpr;
    var ctx = canvas.getContext("2d");
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);

    var images = null, current = null, token = null, score = 0, listeners = [], running = false, paused = false;

    function cleanup() {
      listeners.forEach(function (l) { l[0].removeEventListener(l[1], l[2]); });
      listeners = [];
    }

    function show(t, body, label) {
      title.textContent = t;
      text.textContent = body;
      button.textContent = label;
      overlay.hidden = false;
      button.disabled = false;
      button.focus();
    }

    function end() {
      if (!running) return;
      running = false;
      paused = false;
      pauseButton.disabled = true;
      if (current) current.stop();
      cleanup();
      var final = Math.max(0, Math.floor(score));
      if (!token) {
        show("Score: " + final, "Practice run. Sign in and link your Minecraft account to earn Marks.", "Play again");
        return;
      }
      show("Score: " + final, "Saving…", "Play again");
      button.disabled = true;
      post(finishBase.replace("TOKEN", token), csrf, { score: final }).then(function (res) {
        var lines = [];
        if (res.marks > 0) lines.push("+" + res.marks + " Marks added to your reward queue." + (res.delivery_enabled ? " The server collects rewards automatically." : " Delivery starts when the server connection is ready."));
        if (res.message) lines.push(res.message);
        if (res.best != null) lines.push("Best: " + res.best + ".");
        if (res.cap != null) lines.push("Today: " + res.earned_today + " / " + res.cap + " Marks.");
        text.textContent = lines.join(" ") || "Saved.";
        button.disabled = false;
      }).catch(function () {
        text.textContent = "Your score couldn't be saved. Check your connection.";
        button.disabled = false;
      });
    }

    function begin() {
      if (running && paused) { setPaused(false); return; }
      button.disabled = true;
      var ready = images ? Promise.resolve(images) : loadImages(root.getAttribute("data-img-base"));
      Promise.all([ready, post(startUrl, csrf).catch(function () { return { practice: true }; })]).then(function (results) {
        images = results[0];
        token = results[1].practice ? null : results[1].token;
        score = 0;
        scoreEl.textContent = "0";
        overlay.hidden = true;
        running = true;
        paused = false;
        pauseButton.disabled = false;
        pauseButton.textContent = "Pause";
        var env = {
          ctx: ctx, W: W, H: H, reduced: reduced, canvas: canvas,
          paused: function () { return paused; },
          img: function (name) { return images[name]; },
          score: function (n) { score = n; scoreEl.textContent = String(Math.floor(n)); },
          end: end,
          on: function (target, type, fn, opts) { target.addEventListener(type, fn, opts); listeners.push([target, type, fn]); }
        };
        current = factories[slug](env);
        current.start();
        canvas.focus({ preventScroll: true });
        root.scrollIntoView({ block: "start", behavior: "auto" });
      }).catch(function () {
        running = false;
        if (current) current.stop();
        cleanup();
        pauseButton.disabled = true;
        show("Couldn't start", "Reload this page and try again.", "Try again");
      });
    }

    function setPaused(value) {
      if (!running) return;
      paused = value;
      pauseButton.textContent = paused ? "Resume" : "Pause";
      if (paused) show("Taking a breather", "Your game is paused. Ready for one more go?", "Resume game");
      else {
        overlay.hidden = true;
        canvas.focus({ preventScroll: true });
        root.scrollIntoView({ block: "start", behavior: "auto" });
      }
    }

    button.addEventListener("click", begin);
    pauseButton.addEventListener("click", function () { setPaused(!paused); });
    document.addEventListener("visibilitychange", function () { if (document.hidden) setPaused(true); });
    // Stop the page scrolling while Space or the arrows are used to play.
    window.addEventListener("keydown", function (e) {
      if (running && e.code === "KeyP" && !e.repeat) { e.preventDefault(); setPaused(!paused); }
      if (running && (e.code === "Space" || e.code.indexOf("Arrow") === 0)) e.preventDefault();
    });
    ctx.fillStyle = "#17243B";
    ctx.fillRect(0, 0, W, H);
  });
})();
