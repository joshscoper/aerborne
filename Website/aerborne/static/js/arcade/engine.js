// Shared run lifecycle. Game simulation pauses independently of request and reward state.
(function () {
  "use strict";
  var factories = {};
  window.Arcade = { register: function (slug, factory) { factories[slug] = factory; } };
  function loadImages(base, slug, progress) {
    var images = {}, waits = [], completed = 0, failed = [];
    var catalog = window.ArcadeSpriteCatalog && window.ArcadeSpriteCatalog[slug];
    if (!catalog) return Promise.reject(new Error("The sprite catalog is unavailable. Reload the page and try again."));
    var total = Object.keys(catalog).length + 1;
    function add(name, file, required) {
      waits.push(new Promise(function (resolve) {
        var im = new Image(), settled = false;
        function done(ok) {
          if (settled) return;
          settled = true; images[name] = ok ? im : null;
          if (!ok && required) failed.push(name);
          completed++; progress(completed, total); resolve();
        }
        im.onload = function () { done(true); };
        im.onerror = function () { done(false); };
        im.src = base + file;
        setTimeout(function () { done(false); }, 10000);
      }));
    }
    Object.keys(catalog).forEach(function (name) { add(name, catalog[name].file, true); });
    add("landscape", "brand/hero-960.webp", false);
    return Promise.all(waits).then(function () {
      if (failed.length) throw new Error("Some game art could not load. Check your connection and try again.");
      return images;
    });
  }
  function post(url, csrf, body) {
    var controller = typeof AbortController !== "undefined" ? new AbortController() : null;
    var timeout = setTimeout(function () { if (controller) controller.abort(); }, 12000);
    return fetch(url, {
      method: "POST", credentials: "same-origin",
      headers: { "Content-Type": "application/json", "X-CSRF-Token": csrf, Accept: "application/json" },
      body: JSON.stringify(body || {}), signal: controller ? controller.signal : undefined
    }).then(function (res) {
      return res.json().catch(function () { throw new Error("Reload the page to refresh your session."); }).then(function (data) {
        if (!res.ok) throw new Error(data.message || "Reload the page to refresh your session.");
        return data;
      });
    }).then(function (data) { clearTimeout(timeout); return data; }, function (err) { clearTimeout(timeout); throw err; });
  }
  document.addEventListener("DOMContentLoaded", function () {
    var root = document.querySelector("[data-arcade]");
    if (!root) return;
    var canvas = root.querySelector("canvas"), ctx = canvas.getContext("2d");
    var overlay = root.querySelector("[data-overlay]"), title = root.querySelector("[data-title]"), text = root.querySelector("[data-text]");
    var button = root.querySelector("[data-play]"), pauseButton = root.querySelector("[data-pause]"), finishButton = root.querySelector("[data-finish-run]");
    var scoreEl = root.querySelector("[data-score]"), stat = root.querySelector("[data-stat]"), estimate = root.querySelector("[data-estimate]");
    var label = root.querySelector("[data-overlay-label]"), notice = root.querySelector("[data-run-notice]"), mode = root.querySelector("[data-mode]");
    var actionTray = root.querySelector("[data-game-actions]"), actions = [];
    var csrf = root.dataset.csrf, slug = root.dataset.arcade, reduced = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    var dpr = Math.min(window.devicePixelRatio || 1, 2);
    canvas.width = 480 * dpr; canvas.height = 640 * dpr;
    if (!ctx || !factories[slug]) { text.textContent = "This browser cannot run the game. Try an up-to-date browser."; button.disabled = true; return; }
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    var images = null, current = null, token = null, score = 0, listeners = [], running = false, paused = false, saving = false, retry = false;
    var practiceMessage = "";
    root.dataset.state = "ready";
    function marks(n) { return n.toLocaleString() + (n === 1 ? " Mark" : " Marks"); }
    function cleanup() {
      listeners.forEach(function (l) { l[0].removeEventListener(l[1], l[2], l[3]); }); listeners = [];
      actions.forEach(function (action) { action.button.disabled = true; });
    }
    function show(t, body, caption) {
      title.textContent = t; text.textContent = body; button.textContent = caption;
      button.disabled = false; overlay.hidden = false; button.focus({ preventScroll: true });
    }
    function report(n) {
      score = Math.max(0, Math.floor(n));
      scoreEl.textContent = score.toLocaleString();
      var reward = Math.min(Number(root.dataset.cap), Math.floor(score / Number(root.dataset.perMark)) * Number(root.dataset.multiplier));
      estimate.textContent = token ? marks(reward) + " · before daily limits" : "Up to " + root.dataset.cap + " Marks / run";
    }
    function save() {
      if (saving) return;
      saving = true; retry = false; button.disabled = true;
      text.textContent = "Saving your score…";
      post(root.dataset.finish.replace("TOKEN", token), csrf, { score: score }).then(function (res) {
        saving = false; label.textContent = res.ok ? "Run complete" : "Score not accepted";
        var lines = [];
        if (res.marks > 0) lines.push("+" + marks(res.marks) + " in your reward queue.");
        if (res.message) lines.push(res.message);
        if (!res.marks && !res.message) lines.push("Score saved. Keep going to earn Marks.");
        if (res.cap != null) lines.push("Today: " + res.earned_today + " / " + res.cap + " Marks.");
        show("Score: " + res.score.toLocaleString(), lines.join(" "), "Play again");
        document.querySelectorAll("[data-best]").forEach(function (el) { el.textContent = res.best == null ? "—" : res.best.toLocaleString(); });
        document.querySelectorAll("[data-earned]").forEach(function (el) { el.textContent = res.earned_today.toLocaleString(); });
        document.querySelectorAll("[data-allowance]").forEach(function (el) { el.value = res.earned_today; });
        document.querySelectorAll("[data-pending]").forEach(function (el) { el.hidden = !res.pending; el.textContent = marks(res.pending || 0) + " awaiting delivery"; });
        if (res.marks > 0) estimate.textContent = marks(res.marks) + " earned";
      }).catch(function (err) {
        saving = false; retry = true;
        show("Score: " + score.toLocaleString(), "Your score is still here. Check your connection and retry saving. " + err.message, "Retry saving");
      });
    }
    function end(reason) {
      if (!running) return;
      running = false; paused = false; pauseButton.disabled = true; pauseButton.textContent = "Pause"; finishButton.hidden = true;
      root.dataset.state = "finished";
      current.stop(); cleanup(); stat.textContent = reason || "Run complete"; label.textContent = "Run complete";
      if (token) { show("Score: " + score.toLocaleString(), "Saving…", "Play again"); save(); }
      else show("Score: " + score.toLocaleString(), practiceMessage || "Practice complete. Sign in to save scores and link Minecraft to earn Marks.", "Play again");
    }
    function setPaused(value) {
      if (!running) return;
      paused = value; pauseButton.textContent = paused ? "Resume" : "Pause";
      root.dataset.state = paused ? "paused" : "playing";
      actions.forEach(function (action) { action.button.disabled = paused || !action.enabled; });
      if (paused) {
        if (current.release) current.release();
        label.textContent = "Game paused"; finishButton.hidden = false;
        show("Taking a breather", "Resume when you're ready, or finish now to save your current score. Runs expire 30 minutes after starting.", "Resume game");
      } else { overlay.hidden = true; finishButton.hidden = true; canvas.focus({ preventScroll: true }); }
    }
    function begin() {
      if (retry) { save(); return; }
      if (running && paused) { setPaused(false); return; }
      if (saving) return;
      button.disabled = true; button.textContent = "Getting ready…";
      var ready = images ? Promise.resolve(images) : loadImages(root.dataset.imgBase, slug, function (loaded, total) {
        button.textContent = "Loading art… " + loaded + "/" + total;
      });
      // Load art before issuing a timed run token; slow assets must not cost playing time.
      ready.then(function (loaded) {
        images = loaded;
        return post(root.dataset.start, csrf).catch(function (err) { return { practice: true, message: "Practice mode: scores cannot be saved right now. " + err.message }; });
      }).then(function (res) {
        cleanup(); token = res.practice ? null : res.token; practiceMessage = res.message || "";
        actions = []; actionTray.textContent = ""; actionTray.hidden = true;
        retry = false; notice.hidden = !practiceMessage; notice.textContent = practiceMessage;
        mode.textContent = token ? "Score saving on" : "Practice run";
        report(0); overlay.hidden = true; running = true; paused = false; finishButton.hidden = true;
        root.dataset.state = "playing";
        pauseButton.disabled = false; pauseButton.textContent = "Pause";
        var env = {
          ctx: ctx, W: 480, H: 640, canvas: canvas, reduced: reduced,
          paused: function () { return paused || !running; },
          img: function (name) { return images[name]; }, score: report, end: end,
          bounds: function (name) { var sprite = window.ArcadeSpriteCatalog[slug][name]; return sprite && sprite.box; },
          action: function (label, shortcut, callback) {
            var el = document.createElement("button"), action = { button: el, enabled: true };
            el.type = "button"; el.className = "btn btn-ghost"; el.textContent = label;
            el.setAttribute("aria-keyshortcuts", shortcut); actionTray.appendChild(el); actionTray.hidden = false;
            var handler = function () { if (running && !paused && action.enabled) { callback(); canvas.focus({ preventScroll: true }); } };
            el.addEventListener("click", handler); listeners.push([el, "click", handler]); actions.push(action);
            return function (caption, enabled) { el.textContent = caption; action.enabled = enabled; el.disabled = !enabled || paused || !running; };
          },
          status: function (value) { if (stat.textContent !== value) stat.textContent = value; },
          on: function (target, type, fn, opts) {
            target.addEventListener(type, fn, opts); listeners.push([target, type, fn, opts]);
          }
        };
        current = factories[slug](env); current.start();
        canvas.focus({ preventScroll: true }); root.scrollIntoView({ block: "start", behavior: "auto" });
      }).catch(function (err) {
        running = false; if (current) current.stop(); cleanup(); pauseButton.disabled = true;
        show("Couldn't start", err.message || "Reload this page and try again.", "Try again");
      });
    }
    button.addEventListener("click", begin);
    pauseButton.addEventListener("click", function () { setPaused(!paused); });
    finishButton.addEventListener("click", function () { end("Run finished"); });
    document.addEventListener("visibilitychange", function () { if (document.hidden) setPaused(true); });
    window.addEventListener("blur", function () { setPaused(true); });
    window.addEventListener("keydown", function (e) {
      if (running && e.code === "KeyP" && !e.repeat) { e.preventDefault(); setPaused(!paused); }
      if (running && !paused && document.activeElement === canvas && (e.code === "Space" || e.code.indexOf("Arrow") === 0)) e.preventDefault();
    });
    window.addEventListener("beforeunload", function (e) {
      if (retry) { e.preventDefault(); e.returnValue = ""; }
    });
    var g = ctx.createLinearGradient(0,0,480,640);
    g.addColorStop(0,"#253c61"); g.addColorStop(1,"#101d32"); ctx.fillStyle = g; ctx.fillRect(0,0,480,640);
    for (var i=0; i<28; i++) { ctx.fillStyle = "#8bcfff44"; ctx.fillRect((i*137)%480,(i*83)%640,2,2); }
  });
})();
