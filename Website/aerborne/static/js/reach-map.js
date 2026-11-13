// The Wardens' Reach map: drag to pan, scroll or pinch to zoom, toggle layers, search, and pick a marker for details.
// Markers are drawn in screen pixels and rescaled on every zoom so they stay the same size while the land grows.
(function () {
  "use strict";
  const figure = document.querySelector(".reach-map");
  if (!figure) return;
  const svg = figure.querySelector("svg");
  const base = {
    x: Number(figure.dataset.minX), y: Number(figure.dataset.minZ),
    w: Number(figure.dataset.width), h: Number(figure.dataset.height),
  };
  let data = { markers: [], regions: [] };
  try { data = JSON.parse(document.getElementById("reach-data").textContent); } catch (e) { /* the map still pans */ }
  const byId = new Map();
  data.markers.forEach((m) => byId.set(m.id, m));
  data.regions.forEach((r) => byId.set(r.id, Object.assign({ isRegion: true }, r)));

  const view = Object.assign({}, base);
  const MIN_W = base.w / 9;
  const scaled = Array.from(svg.querySelectorAll("[data-x][data-z]"));
  const tools = figure.querySelector(".reach-map-tools");
  tools.hidden = false;

  function clamp() {
    view.w = Math.min(base.w, Math.max(MIN_W, view.w));
    view.h = view.w * base.h / base.w;
    view.x = Math.min(base.x + base.w - view.w, Math.max(base.x, view.x));
    view.y = Math.min(base.y + base.h - view.h, Math.max(base.y, view.y));
  }

  let frame = 0;
  function draw() {
    if (frame) return;
    frame = requestAnimationFrame(() => {
      frame = 0;
      clamp();
      svg.setAttribute("viewBox", `${view.x} ${view.y} ${view.w} ${view.h}`);
      const k = view.w / Math.max(1, svg.clientWidth);
      for (const el of scaled) el.setAttribute("transform", `translate(${el.dataset.x} ${el.dataset.z}) scale(${k})`);
      const zoom = base.w / view.w;
      figure.classList.toggle("is-near", zoom > 2.2);
      figure.classList.toggle("is-close", zoom > 4.5);
    });
  }

  // A screen point as a world point.
  function toWorld(clientX, clientY) {
    const r = svg.getBoundingClientRect();
    return { x: view.x + (clientX - r.left) / r.width * view.w, y: view.y + (clientY - r.top) / r.height * view.h };
  }

  function zoomAt(factor, clientX, clientY) {
    const r = svg.getBoundingClientRect();
    const cx = clientX ?? r.left + r.width / 2, cy = clientY ?? r.top + r.height / 2;
    const before = toWorld(cx, cy);
    view.w = Math.min(base.w, Math.max(MIN_W, view.w / factor));
    view.h = view.w * base.h / base.w;
    view.x = before.x - (cx - r.left) / r.width * view.w;
    view.y = before.y - (cy - r.top) / r.height * view.h;
    draw();
  }

  function centre(x, z, width) {
    view.w = Math.min(base.w, Math.max(MIN_W, width));
    view.h = view.w * base.h / base.w;
    view.x = x - view.w / 2;
    view.y = z - view.h / 2;
    draw();
  }

  // The wheel scrolls the page unless Ctrl (or Cmd) is held, so the map never traps scrolling.
  const wheelHint = figure.querySelector("[data-wheel-hint]");
  let hintTimer = 0;
  svg.addEventListener("wheel", (e) => {
    if (!(e.ctrlKey || e.metaKey)) {
      wheelHint.hidden = false;
      clearTimeout(hintTimer);
      hintTimer = setTimeout(() => { wheelHint.hidden = true; }, 1400);
      return;
    }
    e.preventDefault();
    zoomAt(e.deltaY < 0 ? 1.25 : 0.8, e.clientX, e.clientY);
  }, { passive: false });

  // Dragging with a mouse, a pen or one finger; two fingers pinch.
  const pointers = new Map();
  let drag = null, pinch = null, moved = false;
  svg.addEventListener("pointerdown", (e) => {
    pointers.set(e.pointerId, { x: e.clientX, y: e.clientY });
    svg.setPointerCapture(e.pointerId);
    moved = false;
    if (pointers.size === 1) drag = { x: e.clientX, y: e.clientY, vx: view.x, vy: view.y };
    if (pointers.size === 2) {
      const [a, b] = Array.from(pointers.values());
      pinch = { d: Math.hypot(a.x - b.x, a.y - b.y) };
      drag = null;
    }
  });
  svg.addEventListener("pointermove", (e) => {
    if (!pointers.has(e.pointerId)) return;
    pointers.set(e.pointerId, { x: e.clientX, y: e.clientY });
    const r = svg.getBoundingClientRect();
    if (pinch && pointers.size === 2) {
      const [a, b] = Array.from(pointers.values());
      const d = Math.hypot(a.x - b.x, a.y - b.y);
      if (d > 0) {
        const mid = { x: (a.x + b.x) / 2, y: (a.y + b.y) / 2 };
        zoomAt(d / pinch.d, mid.x, mid.y);
        pinch.d = d;
      }
      moved = true;
      return;
    }
    if (!drag) return;
    const dx = e.clientX - drag.x, dy = e.clientY - drag.y;
    if (Math.abs(dx) + Math.abs(dy) > 4) moved = true;
    view.x = drag.vx - dx / r.width * view.w;
    view.y = drag.vy - dy / r.height * view.h;
    draw();
  });
  const release = (e) => {
    pointers.delete(e.pointerId);
    if (pointers.size < 2) pinch = null;
    if (pointers.size === 0) drag = null;
  };
  svg.addEventListener("pointerup", release);
  svg.addEventListener("pointercancel", release);

  svg.addEventListener("keydown", (e) => {
    const step = view.w / 8;
    const keys = { ArrowLeft: [-step, 0], ArrowRight: [step, 0], ArrowUp: [0, -step], ArrowDown: [0, step] };
    if (keys[e.key]) { view.x += keys[e.key][0]; view.y += keys[e.key][1]; draw(); e.preventDefault(); }
    else if (e.key === "+" || e.key === "=") { zoomAt(1.25); e.preventDefault(); }
    else if (e.key === "-") { zoomAt(0.8); e.preventDefault(); }
  });

  tools.addEventListener("click", (e) => {
    const button = e.target.closest("button[data-zoom]");
    if (!button) return;
    if (button.dataset.zoom === "in") zoomAt(1.5);
    else if (button.dataset.zoom === "out") zoomAt(1 / 1.5);
    else { Object.assign(view, base); draw(); }
  });

  // Layers.
  document.querySelectorAll("[data-layer-toggle]").forEach((box) => {
    const apply = () => svg.querySelectorAll(`[data-layer="${box.dataset.layerToggle}"]`).forEach((g) => g.classList.toggle("is-hidden", !box.checked));
    box.addEventListener("change", apply);
    apply();
  });

  // Picking something: the panel, the highlight and the address bar.
  const panel = document.querySelector("[data-reach-detail]");
  const hint = document.querySelector("[data-reach-hint]");
  const field = (name) => panel.querySelector(`[data-detail-${name}]`);
  let selected = null;

  function select(id, fly) {
    const item = byId.get(id);
    if (!item) return;
    if (selected) selected.classList.remove("is-selected");
    selected = svg.querySelector(`[data-select="${CSS.escape(id)}"]`);
    if (selected) selected.classList.add("is-selected");
    if (!item.isRegion) {
      // Make sure its layer is showing.
      const layer = selected && selected.closest("[data-layer]");
      const toggle = layer && document.querySelector(`[data-layer-toggle="${layer.dataset.layer}"]`);
      if (toggle && !toggle.checked) { toggle.checked = true; toggle.dispatchEvent(new Event("change")); }
    }
    field("sub").textContent = item.isRegion
      ? (item.levels ? `Region · levels ${item.levels}` : "Region")
      : [item.sub, item.area].filter(Boolean).join(" · ");
    field("name").textContent = item.name;
    field("about").textContent = item.isRegion
      ? `${item.count} place${item.count === 1 ? "" : "s"} of interest. Open the list under the map for everything here.`
      : item.about;
    field("where").textContent = `X ${item.x}, Z ${item.z}`;
    const link = field("link");
    if (item.link) { link.href = item.link; link.hidden = false; } else link.hidden = true;
    panel.hidden = false;
    if (hint) hint.hidden = true;
    if (fly) centre(item.x, item.z, item.isRegion ? base.w / 3 : base.w / 6);
    history.replaceState(null, "", `#${id}`);
  }

  panel.querySelector("[data-detail-close]").addEventListener("click", () => {
    panel.hidden = true;
    if (selected) selected.classList.remove("is-selected");
    selected = null;
    history.replaceState(null, "", location.pathname);
  });

  svg.addEventListener("click", (e) => {
    if (moved) return;
    const target = e.target.closest("[data-select]");
    if (target) select(target.dataset.select, false);
  });
  document.querySelectorAll(".reach-index [data-select]").forEach((button) => button.addEventListener("click", () => {
    select(button.dataset.select, true);
    figure.scrollIntoView({ behavior: "smooth", block: "start" });
  }));

  // Search: filters the index and jumps to the best match on Enter.
  const search = document.querySelector("[data-reach-search]");
  const entries = Array.from(document.querySelectorAll(".reach-index li[data-search]"));
  search.addEventListener("input", () => {
    const q = search.value.trim().toLowerCase();
    entries.forEach((li) => { li.hidden = q !== "" && !li.dataset.search.includes(q); });
    svg.querySelectorAll(".reach-marker").forEach((m) => {
      const item = byId.get(m.dataset.select);
      m.classList.toggle("is-match", q !== "" && item && (item.name + " " + item.sub).toLowerCase().includes(q));
    });
  });
  search.addEventListener("keydown", (e) => {
    if (e.key !== "Enter") return;
    e.preventDefault();
    const q = search.value.trim().toLowerCase();
    const hit = data.markers.find((m) => m.name.toLowerCase().includes(q)) || data.regions.find((r) => r.name.toLowerCase().includes(q));
    if (hit) select(hit.id, true);
  });

  window.addEventListener("resize", draw);
  draw();
  const start = decodeURIComponent(location.hash.slice(1));
  if (byId.has(start)) select(start, true);
})();
