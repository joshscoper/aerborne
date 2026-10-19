// Aerborne site behaviour: menu, live status, copy buttons and the hero parallax.
(function () {
  "use strict";

  document.documentElement.classList.add("js");

  var REFRESH_MS = 60000;
  var reduceMotion = window.matchMedia && window.matchMedia("(prefers-reduced-motion: reduce)").matches;

  function bars(latency) {
    if (latency < 150) return 5;
    if (latency < 300) return 4;
    if (latency < 600) return 3;
    if (latency < 1000) return 2;
    return 1;
  }

  function ago(epochSeconds) {
    if (!epochSeconds) return "";
    var seconds = Math.max(0, Math.round(Date.now() / 1000 - epochSeconds));
    if (seconds < 5) return "just now";
    if (seconds < 60) return seconds + " seconds ago";
    var minutes = Math.round(seconds / 60);
    if (minutes < 60) return minutes === 1 ? "1 minute ago" : minutes + " minutes ago";
    return new Date(epochSeconds * 1000).toLocaleString();
  }

  function describe(data) {
    if (data.state === "unconfigured") return "The public address is coming soon.";
    if (data.state === "maintenance") return "Under maintenance. " + (data.message || "Check back soon.");
    if (data.state === "offline") return "The server isn't answering right now. It may be restarting; check back in a few minutes.";
    if (data.state !== "online") return "Live status couldn't load. Try refreshing in a moment.";
    var who = data.players === 1 ? "1 player" : data.players + " players";
    return "Online with " + who + (data.version ? " on " + data.version : "") + ". Checked " + ago(data.checked_at) + ".";
  }

  function render(card, data) {
    var text = card.querySelector("[data-status-text]");
    var players = card.querySelector("[data-players]");
    var icon = card.querySelector(".server-icon");
    var available = data.online && data.state === "online";
    var states = { online: "Online", offline: "Offline", maintenance: "Maintenance", unconfigured: "Coming soon", unknown: "Status unavailable" };
    card.setAttribute("data-state", data.state);
    card.querySelector('[data-status-state]').textContent = states[data.state] || states.unknown;
    card.querySelector('[data-status-latency]').textContent = available ? data.latency_ms + ' ms' : '';
    if (available) {
      card.setAttribute("data-bars", String(bars(data.latency_ms)));
      players.textContent = data.players + "/" + data.max;
      icon.src = data.favicon || icon.getAttribute("data-default-icon");
    } else {
      card.removeAttribute("data-bars");
      players.textContent = "";
      icon.src = icon.getAttribute("data-default-icon");
    }
    text.textContent = describe(data);
    fillFacts(data);
  }

  // The status page has a fact list alongside the card.
  function fillFacts(data) {
    var set = function (name, value) {
      var el = document.querySelector('[data-field="' + name + '"]');
      if (el) el.textContent = value;
    };
    var states = { online: "Online", offline: "Offline", maintenance: "Maintenance", unconfigured: "Not announced yet" };
    var available = data.online && data.state === "online";
    set("state", states[data.state] || "Unknown");
    set("players", available ? data.players + " of " + data.max : "–");
    set("version", available && data.version ? data.version : "–");
    set("latency", available ? data.latency_ms + " ms" : "–");
    set("checked", data.checked_at ? ago(data.checked_at) : "–");
    set("last-online", data.last_online_at ? ago(data.last_online_at) : (data.online ? "Now" : "Not since this check started"));
  }

  function refresh(card) {
    if (card.getAttribute('aria-busy') === 'true') return;
    var button = card.querySelector('[data-status-refresh]');
    var controller = window.AbortController ? new AbortController() : null;
    var timer = controller ? setTimeout(function () { controller.abort(); }, 10000) : null;
    card.setAttribute('aria-busy', 'true');
    button.disabled = true;
    button.textContent = 'Checking…';
    fetch(card.getAttribute("data-status-url"), { headers: { Accept: "application/json" }, cache: 'no-store', signal: controller ? controller.signal : undefined })
      .then(function (res) {
        if (!res.ok) throw new Error("status " + res.status);
        return res.json();
      })
      .then(function (data) { render(card, data); })
      .catch(function () {
          render(card, {state: 'unknown', online: false});
        })
        .then(function () {
          if (timer) clearTimeout(timer);
          card.setAttribute('aria-busy', 'false');
          button.disabled = false;
          button.textContent = 'Refresh status';
      });
  }

  function copy(button) {
    var value = button.getAttribute("data-copy");
    var label = button.textContent;
    var done = function (message) {
      button.textContent = message;
      button.setAttribute("data-copied", "");
      setTimeout(function () {
        button.textContent = label;
        button.removeAttribute("data-copied");
      }, 1800);
    };
    if (navigator.clipboard && window.isSecureContext) {
      navigator.clipboard.writeText(value).then(function () { done("Copied"); }, function () { select(button, done); });
    } else {
      select(button, done);
    }
  }

  // Without clipboard access, select the address so Ctrl+C works.
  function select(button, done) {
    var code = button.parentNode.querySelector("[data-address]");
    if (!code || !window.getSelection) return;
    var range = document.createRange();
    range.selectNodeContents(code);
    var sel = window.getSelection();
    sel.removeAllRanges();
    sel.addRange(range);
    done("Press Ctrl+C");
  }

  function menu() {
    var toggle = document.querySelector(".menu-toggle");
    var panel = document.getElementById("site-menu");
    if (!toggle || !panel) return;
    toggle.addEventListener("click", function () {
      var open = panel.classList.toggle("open");
      toggle.setAttribute("aria-expanded", open ? "true" : "false");
      toggle.textContent = open ? "Close" : "Menu";
    });
    document.addEventListener("keydown", function (event) {
      if (event.key === "Escape" && panel.classList.contains("open")) {
        panel.classList.remove("open");
        toggle.setAttribute("aria-expanded", "false");
        toggle.textContent = "Menu";
        toggle.focus();
      }
    });
  }

  // Grouped nav menus: click or tap to open, hover on desktop, Escape or outside click to close.
  function navGroups() {
    var groups = Array.prototype.slice.call(document.querySelectorAll(".nav-group"));
    var hover = window.matchMedia && window.matchMedia("(hover: hover) and (min-width: 901px)").matches;
    var set = function (group, open) {
      group.classList.toggle("open", open);
      group.querySelector(".nav-trigger").setAttribute("aria-expanded", open ? "true" : "false");
    };
    groups.forEach(function (group) {
      var trigger = group.querySelector(".nav-trigger");
      var timer;
      var byHover = false;
      trigger.addEventListener("click", function () {
        // A click right after hovering open should keep the menu open, not close it.
        var open = byHover || !group.classList.contains("open");
        byHover = false;
        groups.forEach(function (other) { if (other !== group) set(other, false); });
        set(group, open);
      });
      if (hover) {
        group.addEventListener("mouseenter", function () {
          clearTimeout(timer);
          byHover = !group.classList.contains("open");
          groups.forEach(function (o) { set(o, o === group); });
        });
        group.addEventListener("mouseleave", function () { timer = setTimeout(function () { set(group, false); }, 180); });
      }
    });
    document.addEventListener("click", function (event) {
      groups.forEach(function (group) { if (!group.contains(event.target)) set(group, false); });
    });
    document.addEventListener("keydown", function (event) {
      if (event.key !== "Escape") return;
      groups.forEach(function (group) {
        if (group.classList.contains("open")) {
          set(group, false);
          group.querySelector(".nav-trigger").focus();
        }
      });
    });
  }

  // Slow layered drift as the hero scrolls away.
  function parallax() {
    var layers = document.querySelectorAll("[data-parallax]");
    if (!layers.length || reduceMotion) return;
    var ticking = false;
    var update = function () {
      var y = window.scrollY || window.pageYOffset;
      if (y < window.innerHeight * 1.2) {
        Array.prototype.forEach.call(layers, function (el) {
          el.style.transform = "translate3d(0," + (y * parseFloat(el.getAttribute("data-parallax"))).toFixed(1) + "px,0)";
        });
      }
      ticking = false;
    };
    window.addEventListener("scroll", function () {
      if (!ticking) {
        ticking = true;
        window.requestAnimationFrame(update);
      }
    }, { passive: true });
  }

  // The cloud intro ends by itself through CSS; this lets people skip it and remembers it played.
  function intro() {
    var root = document.documentElement;
    if (!root.classList.contains("intro-pending")) return;
    var done = false;
    var finish = function () {
      if (done) return;
      done = true;
      root.classList.remove("intro-pending");
      try { window.sessionStorage.setItem("aerborne-intro", "1"); } catch (e) { /* storage blocked */ }
    };
    setTimeout(finish, 3000);
    var overlay = document.querySelector(".intro");
    if (overlay) overlay.addEventListener("click", finish);
    window.addEventListener("keydown", finish, { once: true });
    window.addEventListener("wheel", finish, { once: true, passive: true });
    window.addEventListener("touchmove", finish, { once: true, passive: true });
  }

  // Sparkles stream out from behind the pointer, opposite to the way it's moving, like a comet tail.
  // Capped, throttled and mouse-only.
  function cursorSparkles() {
    if (!document.body.classList.contains("fun-cursor") || reduceMotion) return;
    if (!window.matchMedia || !window.matchMedia("(pointer: fine)").matches) return;
    var colours = ["#F4C76B", "#F4C76B", "#8CE8ED", "#C59AFA", "#F4F9FF"];
    var MAX = 18;
    var alive = 0, lastX = null, lastY = null, lastAt = 0;
    // The arrow's body sits below-right of its tip (the hotspot), so sparkles start there and
    // look as if they come from behind the pointer.
    var BODY_X = 7, BODY_Y = 12;

    function spawn(x, y, dirX, dirY) {
      var spark = document.createElement("span");
      spark.className = "cursor-spark";
      spark.setAttribute("aria-hidden", "true");
      var size = 5 + Math.random() * 5;
      var reach = 14 + Math.random() * 14;
      var spread = (Math.random() - 0.5) * 12; // sideways wobble, perpendicular to the motion
      spark.style.left = (x + BODY_X + (Math.random() - 0.5) * 4) + "px";
      spark.style.top = (y + BODY_Y + (Math.random() - 0.5) * 4) + "px";
      spark.style.width = spark.style.height = size.toFixed(1) + "px";
      spark.style.setProperty("--spark", colours[Math.floor(Math.random() * colours.length)]);
      spark.style.setProperty("--dx", (-dirX * reach - dirY * spread).toFixed(1) + "px");
      spark.style.setProperty("--dy", (-dirY * reach + dirX * spread).toFixed(1) + "px");
      spark.style.setProperty("--turn", Math.round(Math.random() * 180 - 90) + "deg");
      alive++;
      var gone = false;
      var done = function () {
        if (gone) return;
        gone = true;
        if (spark.parentNode) spark.parentNode.removeChild(spark);
        alive--;
      };
      // animationend doesn't fire in background tabs, so a timer cleans up too.
      spark.addEventListener("animationend", done);
      setTimeout(done, 800);
      document.body.appendChild(spark);
    }

    document.addEventListener("mousemove", function (event) {
      var now = Date.now();
      if (lastX === null) { lastX = event.clientX; lastY = event.clientY; return; }
      var dx = event.clientX - lastX, dy = event.clientY - lastY;
      var dist = Math.sqrt(dx * dx + dy * dy);
      if (now - lastAt < 30 || dist < 9 || alive >= MAX) return;
      lastAt = now; lastX = event.clientX; lastY = event.clientY;
      var dirX = dx / dist, dirY = dy / dist;
      spawn(event.clientX, event.clientY, dirX, dirY);
      // Faster movement makes a fuller tail.
      if (dist > 40 && alive < MAX) spawn(event.clientX - dx * 0.5, event.clientY - dy * 0.5, dirX, dirY);
    }, { passive: true });
  }

  document.addEventListener("DOMContentLoaded", function () {
    intro();
    cursorSparkles();
    menu();
    navGroups();
    parallax();
    var cards = document.querySelectorAll(".server-card[data-status-url]");
    if (cards.length && window.fetch) {
      Array.prototype.forEach.call(cards, function (card) {
        card.querySelector('[data-status-refresh]').addEventListener('click', function () { refresh(card); });
      });
      var tick = function () {
        if (!document.hidden) Array.prototype.forEach.call(cards, refresh);
      };
      tick();
      setInterval(tick, REFRESH_MS);
      document.addEventListener('visibilitychange', function () { if (!document.hidden) tick(); });
    }
    Array.prototype.forEach.call(document.querySelectorAll("form[data-confirm]"), function (form) {
      form.addEventListener("submit", function (event) {
        if (!window.confirm(form.getAttribute("data-confirm"))) event.preventDefault();
      });
    });
    // Relative times ("3 minutes ago") for leaderboard and profile timestamps.
    Array.prototype.forEach.call(document.querySelectorAll("time[data-ago]"), function (el) {
      var t = Date.parse(el.getAttribute("data-ago"));
      if (!isNaN(t)) el.textContent = ago(Math.round(t / 1000));
    });
    Array.prototype.forEach.call(document.querySelectorAll("[data-copy]"), function (button) {
      button.addEventListener("click", function () { copy(button); });
    });
  });
})();
