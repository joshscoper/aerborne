// The five Aerborne Arcade games. Each draws on a 480 x 640 canvas through the engine's env.
(function () {
  "use strict";
  var GEMS = ["might", "precision", "aegis", "ward", "celerity", "vitality", "resolve", "affinity", "fortune"];

  function sky(ctx, W, H, top, bottom) {
    var g = ctx.createLinearGradient(0, 0, 0, H);
    g.addColorStop(0, top);
    g.addColorStop(1, bottom);
    ctx.fillStyle = g;
    ctx.fillRect(0, 0, W, H);
  }

  function cloud(ctx, x, y, s, alpha) {
    ctx.fillStyle = "rgba(244, 249, 255," + alpha + ")";
    [[0, 0, 22], [24, -10, 28], [52, 0, 22], [26, 8, 24]].forEach(function (c) {
      ctx.beginPath();
      ctx.arc(x + c[0] * s, y + c[1] * s, c[2] * s, 0, Math.PI * 2);
      ctx.fill();
    });
  }

  function text(ctx, value, x, y, size, colour) {
    ctx.font = "800 " + size + "px Nunito, sans-serif";
    ctx.textAlign = "center";
    ctx.fillStyle = colour || "#F4F9FF";
    ctx.fillText(value, x, y);
  }

  function loop(step, env) {
    var id = 0, last = 0, alive = true;
    function frame(t) {
      if (!alive) return;
      if (env.paused()) { last = t; id = requestAnimationFrame(frame); return; }
      var dt = last ? Math.min(0.05, (t - last) / 1000) : 0;
      last = t;
      step(dt);
      if (alive) id = requestAnimationFrame(frame);
    }
    return {
      start: function () { id = requestAnimationFrame(frame); },
      stop: function () { alive = false; cancelAnimationFrame(id); }
    };
  }

  // 1. Sky Hop: flap through gaps between floating islands.
  Arcade.register("sky-hop", function (env) {
    var ctx = env.ctx, W = env.W, H = env.H;
    var bird = { x: 120, y: H / 2, vy: 0 }, pillars = [], timer = 0, score = 0, clouds = [], dead = false;
    for (var i = 0; i < 5; i++) clouds.push({ x: Math.random() * W, y: 40 + Math.random() * 500, s: 0.6 + Math.random() * 0.8 });
    function flap() { if (!dead && !env.paused()) bird.vy = -330; }
    env.on(env.canvas, "pointerdown", function (e) { e.preventDefault(); flap(); });
    env.on(window, "keydown", function (e) { if (e.code === "Space" || e.code === "ArrowUp") flap(); });
    var gap = 170;
    var l = loop(function (dt) {
      timer -= dt;
      if (timer <= 0) {
        timer = 1.55;
        pillars.push({ x: W + 40, top: 80 + Math.random() * (H - gap - 200), scored: false });
      }
      bird.vy += 900 * dt;
      bird.y += bird.vy * dt;
      pillars.forEach(function (p) { p.x -= 170 * dt; });
      pillars = pillars.filter(function (p) { return p.x > -90; });
      clouds.forEach(function (c) { c.x -= 30 * c.s * dt; if (c.x < -120) c.x = W + 40; });
      sky(ctx, W, H, "#8FD3F8", "#EAF6FF");
      clouds.forEach(function (c) { cloud(ctx, c.x, c.y, c.s, 0.75); });
      pillars.forEach(function (p) {
        // Each pillar is a floating island above and below the gap.
        [[0, p.top], [p.top + gap, H - p.top - gap]].forEach(function (seg) {
          ctx.fillStyle = "#8A6A55";
          ctx.fillRect(p.x, seg[0], 70, seg[1]);
          ctx.fillStyle = "#6CC27A";
          ctx.fillRect(p.x - 4, seg[0] === 0 ? seg[1] - 14 : seg[0], 78, 14);
        });
        if (!p.scored && p.x + 70 < bird.x) { p.scored = true; score++; env.score(score); }
        var hitX = bird.x + 20 > p.x && bird.x - 20 < p.x + 70;
        if (hitX && (bird.y - 18 < p.top || bird.y + 18 > p.top + gap)) dead = true;
      });
      if (bird.y > H - 10 || bird.y < -40) dead = true;
      ctx.save();
      ctx.translate(bird.x, bird.y);
      ctx.rotate(Math.max(-0.5, Math.min(0.9, bird.vy / 600)));
      ctx.drawImage(env.img("pufflet"), -24, -24, 48, 48);
      ctx.restore();
      text(ctx, String(score), W / 2, 70, 48);
      if (dead) env.end();
    }, env);
    return { start: function () { flap(); l.start(); }, stop: l.stop };
  });

  // 2. Gem Rush: swap neighbours to make rows of three. 60 seconds.
  Arcade.register("gem-rush", function (env) {
    var ctx = env.ctx, W = env.W, H = env.H, N = 7, S = 62, ox = (W - N * S) / 2, oy = 150;
    var kinds = GEMS.slice(0, 6), grid = [], pick = null, score = 0, time = 60, busy = 0, cursor = null;
    function rnd() { return kinds[Math.floor(Math.random() * kinds.length)]; }
    for (var r = 0; r < N; r++) { grid.push([]); for (var c = 0; c < N; c++) grid[r].push(rnd()); }
    function matches() {
      var hit = {};
      for (var r = 0; r < N; r++) for (var c = 0; c < N; c++) {
        var k = grid[r][c];
        if (!k) continue;
        if (c < N - 2 && grid[r][c + 1] === k && grid[r][c + 2] === k) { hit[r + "," + c] = hit[r + "," + (c + 1)] = hit[r + "," + (c + 2)] = 1; }
        if (r < N - 2 && grid[r + 1][c] === k && grid[r + 2][c] === k) { hit[r + "," + c] = hit[(r + 1) + "," + c] = hit[(r + 2) + "," + c] = 1; }
      }
      return Object.keys(hit);
    }
    // Clear any starting rows silently.
    for (var guard = 0; guard < 20; guard++) { var m0 = matches(); if (!m0.length) break; m0.forEach(function (k) { var p = k.split(","); grid[p[0]][p[1]] = rnd(); }); }
    function resolve() {
      var found = matches(), chain = 0;
      while (found.length && chain < 10) {
        chain++;
        score += found.length * 10 * chain;
        found.forEach(function (k) { var p = k.split(","); grid[p[0]][p[1]] = null; });
        for (var c = 0; c < N; c++) {
          var col = [];
          for (var r = N - 1; r >= 0; r--) if (grid[r][c]) col.push(grid[r][c]);
          for (var r2 = N - 1, i = 0; r2 >= 0; r2--, i++) grid[r2][c] = i < col.length ? col[i] : rnd();
        }
        found = matches();
      }
      env.score(score);
      return chain > 0;
    }
    function choose(r, c) {
      if (time <= 0 || env.paused()) return;
      cursor = [r, c];
      if (!pick) { pick = [r, c]; return; }
      if (Math.abs(pick[0] - r) + Math.abs(pick[1] - c) === 1) {
        var t = grid[r][c]; grid[r][c] = grid[pick[0]][pick[1]]; grid[pick[0]][pick[1]] = t;
        if (!resolve()) { t = grid[r][c]; grid[r][c] = grid[pick[0]][pick[1]]; grid[pick[0]][pick[1]] = t; busy = 0.25; }
        pick = null;
      } else pick = [r, c];
    }
    env.on(env.canvas, "pointerdown", function (e) {
      var rect = env.canvas.getBoundingClientRect();
      var x = (e.clientX - rect.left) * W / rect.width, y = (e.clientY - rect.top) * H / rect.height;
      var c = Math.floor((x - ox) / S), r = Math.floor((y - oy) / S);
      if (r < 0 || c < 0 || r >= N || c >= N) { pick = null; return; }
      choose(r, c);
    });
    env.on(env.canvas, "keydown", function (e) {
      if (env.paused()) return;
      if (!cursor) cursor = [0, 0];
      if (e.code === "ArrowLeft") cursor[1] = Math.max(0, cursor[1] - 1);
      if (e.code === "ArrowRight") cursor[1] = Math.min(N - 1, cursor[1] + 1);
      if (e.code === "ArrowUp") cursor[0] = Math.max(0, cursor[0] - 1);
      if (e.code === "ArrowDown") cursor[0] = Math.min(N - 1, cursor[0] + 1);
      if (e.code === "Enter" || e.code === "Space") { e.preventDefault(); choose(cursor[0], cursor[1]); }
    });
    var l = loop(function (dt) {
      time -= dt;
      busy = Math.max(0, busy - dt);
      sky(ctx, W, H, "#241C45", "#283752");
      text(ctx, String(score), W / 2, 70, 44);
      text(ctx, Math.max(0, Math.ceil(time)) + "s", W / 2, 110, 22, time < 10 ? "#F4C76B" : "#8E9AAF");
      for (var r = 0; r < N; r++) for (var c = 0; c < N; c++) {
        var x = ox + c * S, y = oy + r * S;
        ctx.fillStyle = (r + c) % 2 ? "rgba(255,255,255,0.05)" : "rgba(255,255,255,0.09)";
        ctx.fillRect(x, y, S, S);
        if (pick && pick[0] === r && pick[1] === c) { ctx.strokeStyle = "#F4C76B"; ctx.lineWidth = 3; ctx.strokeRect(x + 2, y + 2, S - 4, S - 4); }
        if (cursor && cursor[0] === r && cursor[1] === c) { ctx.strokeStyle = "#F4F9FF"; ctx.lineWidth = 2; ctx.strokeRect(x + 5, y + 5, S - 10, S - 10); }
        if (grid[r][c]) ctx.drawImage(env.img(grid[r][c]), x + 7, y + 7, S - 14, S - 14);
      }
      if (busy) { ctx.fillStyle = "rgba(240,122,122," + busy + ")"; ctx.fillRect(ox, oy, N * S, N * S); }
      if (time <= 0) env.end();
    }, env);
    return { start: l.start, stop: l.stop };
  });

  // 3. Cloud Catch: catch falling gems, dodge storm clouds.
  Arcade.register("cloud-catch", function (env) {
    var ctx = env.ctx, W = env.W, H = env.H;
    var basket = { x: W / 2 }, items = [], spawn = 0, score = 0, lives = 3, speed = 160, keys = {}, flash = 0;
    function aim(e) {
      if (env.paused()) return;
      var rect = env.canvas.getBoundingClientRect();
      basket.x = Math.max(40, Math.min(W - 40, (e.clientX - rect.left) * W / rect.width));
    }
    env.on(env.canvas, "pointermove", aim);
    env.on(env.canvas, "pointerdown", aim);
    env.on(window, "keydown", function (e) { if (!env.paused()) keys[e.code] = true; });
    env.on(window, "keyup", function (e) { keys[e.code] = false; });
    env.on(window, "blur", function () { keys = {}; });
    env.on(document, "visibilitychange", function () { if (document.hidden) keys = {}; });
    var l = loop(function (dt) {
      speed += 4 * dt;
      spawn -= dt;
      if (spawn <= 0) {
        spawn = Math.max(0.32, 0.9 - score / 120);
        var storm = Math.random() < Math.min(0.35, 0.12 + score / 300);
        items.push({ x: 30 + Math.random() * (W - 60), y: -30, storm: storm, gem: GEMS[Math.floor(Math.random() * GEMS.length)], v: speed * (0.8 + Math.random() * 0.5) });
      }
      if (keys.ArrowLeft) basket.x = Math.max(40, basket.x - 380 * dt);
      if (keys.ArrowRight) basket.x = Math.min(W - 40, basket.x + 380 * dt);
      flash = Math.max(0, flash - dt);
      sky(ctx, W, H, "#7FC6F2", "#DDF1FF");
      items.forEach(function (it) {
        it.y += it.v * dt;
        if (it.storm) {
          cloud(ctx, it.x - 30, it.y, 0.7, 1);
          ctx.fillStyle = "#56607A";
          ctx.fillRect(it.x - 26, it.y + 8, 52, 6);
          ctx.fillStyle = "#F4C76B";
          ctx.beginPath(); ctx.moveTo(it.x, it.y + 14); ctx.lineTo(it.x - 8, it.y + 34); ctx.lineTo(it.x + 2, it.y + 30); ctx.lineTo(it.x - 4, it.y + 48); ctx.lineTo(it.x + 10, it.y + 24); ctx.lineTo(it.x, it.y + 28); ctx.fill();
        } else {
          ctx.drawImage(env.img(it.gem), it.x - 18, it.y - 18, 36, 36);
        }
        if (it.y > H - 80 && it.y < H - 40 && Math.abs(it.x - basket.x) < 48) {
          it.done = true;
          if (it.storm) { lives--; flash = 0.3; } else { score++; env.score(score); }
        }
        if (it.y > H + 40) it.done = true;
      });
      items = items.filter(function (it) { return !it.done; });
      ctx.fillStyle = "#B08A5E";
      ctx.fillRect(basket.x - 44, H - 64, 88, 26);
      ctx.fillStyle = "#7A5236";
      ctx.fillRect(basket.x - 44, H - 64, 88, 6);
      ctx.drawImage(env.img("kitekit"), basket.x - 20, H - 104, 40, 40);
      text(ctx, String(score), W / 2, 60, 40, "#17243B");
      text(ctx, "♥".repeat(Math.max(0, lives)), W / 2, 96, 26, "#E5484D");
      if (flash) { ctx.fillStyle = "rgba(240,122,122," + flash + ")"; ctx.fillRect(0, 0, W, H); }
      if (lives <= 0) env.end();
    }, env);
    return { start: l.start, stop: l.stop };
  });

  // 4. Codex Memory: find the nine pairs.
  Arcade.register("codex-memory", function (env) {
    var ctx = env.ctx, W = env.W, H = env.H, cols = 3, rows = 6, CW = 120, CH = 68, gx = 18, gy = 12;
    var ox = (W - (cols * CW + (cols - 1) * gx)) / 2, oy = 120;
    var cards = GEMS.concat(GEMS).map(function (g) { return { gem: g, up: false, done: false }; });
    for (var i = cards.length - 1; i > 0; i--) { var j = Math.floor(Math.random() * (i + 1)); var t = cards[i]; cards[i] = cards[j]; cards[j] = t; }
    var open = [], moves = 0, time = 0, found = 0, wait = 0, finished = false, cursor = null;
    function current() { return Math.max(0, Math.round(300 - moves * 6 - time * 1.5)); }
    function choose(k) {
      if (wait > 0 || finished || env.paused()) return;
      var card = cards[k];
      if (card.up || card.done) return;
      card.up = true;
      open.push(card);
      if (open.length === 2) {
        moves++;
        if (open[0].gem === open[1].gem) { open[0].done = open[1].done = true; open = []; found++; }
        else wait = 0.8;
      }
    }
    env.on(env.canvas, "pointerdown", function (e) {
      var rect = env.canvas.getBoundingClientRect();
      var x = (e.clientX - rect.left) * W / rect.width, y = (e.clientY - rect.top) * H / rect.height;
      for (var k = 0; k < cards.length; k++) {
        var cx = ox + (k % cols) * (CW + gx), cy = oy + Math.floor(k / cols) * (CH + gy);
        if (x > cx && x < cx + CW && y > cy && y < cy + CH) {
          choose(k);
          return;
        }
      }
    });
    env.on(env.canvas, "keydown", function (e) {
      if (env.paused()) return;
      if (cursor === null) cursor = 0;
      if (e.code === "ArrowLeft" && cursor % cols > 0) cursor--;
      if (e.code === "ArrowRight" && cursor % cols < cols - 1) cursor++;
      if (e.code === "ArrowUp") cursor = Math.max(0, cursor - cols);
      if (e.code === "ArrowDown") cursor = Math.min(cards.length - 1, cursor + cols);
      if (e.code === "Enter" || e.code === "Space") { e.preventDefault(); choose(cursor); }
    });
    var l = loop(function (dt) {
      if (!finished) time += dt;
      if (wait > 0) { wait -= dt; if (wait <= 0) { open.forEach(function (c) { c.up = false; }); open = []; } }
      sky(ctx, W, H, "#1C1838", "#2A2350");
      text(ctx, found + " / 9 pairs", W / 2, 60, 28);
      text(ctx, moves + " moves · " + Math.floor(time) + "s", W / 2, 92, 18, "#C6D0E0");
      cards.forEach(function (card, k) {
        var cx = ox + (k % cols) * (CW + gx), cy = oy + Math.floor(k / cols) * (CH + gy);
        ctx.fillStyle = card.done ? "rgba(112,219,195,0.18)" : card.up ? "#33456A" : "#C59AFA";
        ctx.fillRect(cx, cy, CW, CH);
        if (cursor === k) { ctx.strokeStyle = "#F4C76B"; ctx.lineWidth = 3; ctx.strokeRect(cx + 2, cy + 2, CW - 4, CH - 4); }
        if (card.up || card.done) ctx.drawImage(env.img(card.gem), cx + CW / 2 - 28, cy + CH / 2 - 28, 56, 56);
        else { ctx.globalAlpha = 0.5; ctx.drawImage(env.img("crest"), cx + CW / 2 - 22, cy + CH / 2 - 22, 44, 44); ctx.globalAlpha = 1; }
      });
      env.score(found === 9 ? current() : 0);
      if (found === 9 && !finished) { finished = true; env.end(); }
    }, env);
    return { start: l.start, stop: l.stop };
  });

  // 5. Forge Strike: hit when the spark crosses the gold zone.
  Arcade.register("forge-strike", function (env) {
    var ctx = env.ctx, W = env.W, H = env.H;
    var pos = 0, dir = 1, speed = 0.7, zone = 0.22, centre = 0.5, misses = 0, score = 0, pulse = 0, pulseHit = false;
    function newZone() { centre = 0.18 + Math.random() * 0.64; zone = Math.max(0.08, 0.22 - score * 0.006); }
    function strike() {
      if (env.paused() || pulse > 0) return;
      var hit = Math.abs(pos - centre) <= zone / 2;
      pulse = 0.35; pulseHit = hit;
      if (hit) { score++; env.score(score); speed = Math.min(2.4, speed + 0.06); newZone(); }
      else misses++;
    }
    env.on(env.canvas, "pointerdown", function (e) { e.preventDefault(); strike(); });
    env.on(window, "keydown", function (e) { if (e.code === "Space") strike(); });
    var l = loop(function (dt) {
      pos += dir * speed * dt;
      if (pos > 1) { pos = 1; dir = -1; }
      if (pos < 0) { pos = 0; dir = 1; }
      pulse = Math.max(0, pulse - dt);
      sky(ctx, W, H, "#2B1B14", "#17243B");
      text(ctx, String(score), W / 2, 90, 54);
      text(ctx, "Misses: " + misses + " / 3", W / 2, 128, 18, "#C6D0E0");
      var bx = 50, bw = W - 100, by = 300;
      ctx.fillStyle = "#33456A"; ctx.fillRect(bx, by, bw, 28);
      ctx.fillStyle = "#F4C76B"; ctx.fillRect(bx + (centre - zone / 2) * bw, by, zone * bw, 28);
      var sx = bx + pos * bw;
      ctx.fillStyle = "#FFF6D8";
      ctx.beginPath(); ctx.arc(sx, by + 14, 12, 0, Math.PI * 2); ctx.fill();
      // Anvil.
      ctx.fillStyle = "#56607A";
      ctx.fillRect(W / 2 - 90, 440, 180, 34); ctx.fillRect(W / 2 - 40, 474, 80, 50); ctx.fillRect(W / 2 - 80, 524, 160, 26);
      if (pulse) {
        ctx.fillStyle = pulseHit ? "rgba(244,199,107," + pulse * 2 + ")" : "rgba(240,122,122," + pulse * 2 + ")";
        ctx.beginPath(); ctx.arc(W / 2, 440, 60 + (0.35 - pulse) * 120, 0, Math.PI * 2); ctx.fill();
      }
      if (misses >= 3) env.end();
    }, env);
    return { start: l.start, stop: l.stop };
  });
})();
