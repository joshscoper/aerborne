const { test } = require('node:test');
const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const { join } = require('node:path');
const vm = require('node:vm');

const source = name => readFileSync(join(__dirname, '../aerborne/static/js', name), 'utf8');
function element(top = 0, height = 300) {
  const values = new Map();
  return {
    top, offsetHeight: height, listeners: {}, attributes: {},
    style: { setProperty: (k, v) => values.set(k, v), getPropertyValue: k => values.get(k) || '' },
    setAttribute(k, v) { this.attributes[k] = v; },
    addEventListener(k, fn) { this.listeners[k] = fn; },
    getBoundingClientRect() {
      const y = parseFloat(values.get('--reveal-y')) || 0;
      const scale = parseFloat(values.get('--reveal-scale')) || 1;
      return { top: this.top + y + height * (1 - scale) / 2, height: height * scale };
    },
    scrollIntoView(options) { this.scrollOptions = options; },
    focus(options) { this.focusOptions = options; }
  };
}

function controller(reduced = false) {
  const classes = new Set();
  const root = element();
  root.classList = { add: (...v) => v.forEach(x => classes.add(x)), remove: (...v) => v.forEach(x => classes.delete(x)) };
  const reveal = element(1000, 400), hero = element(68, 732);
  const landscape = element(), copy = element(), cue = element(), destination = element();
  const joinSection = element(4000, 600);
  hero.querySelector = () => copy;
  const motion = { matches: reduced, addEventListener: (_, fn) => { motion.changed = fn; } };
  const events = {}, frames = [];
  const context = {
    document: {
      documentElement: root, hidden: false,
      querySelector: s => ({ '.home-hero': hero, '[data-home-landscape]': landscape, '.home-scroll-cue': cue })[s],
      querySelectorAll: s => s === '[data-home-reveal]' ? [reveal] : [],
      getElementById: id => id === 'join' ? joinSection : destination, addEventListener: () => {}
    },
    window: { innerHeight: 800, scrollY: 0, matchMedia: () => motion,
      requestAnimationFrame: fn => frames.push(fn), addEventListener: (name, fn) => { events[name] = fn; } }
  };
  vm.runInNewContext(source('home.js'), context);
  const flush = () => { while (frames.length) frames.shift()(); };
  flush();
  return { classes, root, reveal, hero, landscape, cue, destination, joinSection, motion, events, flush };
}

test('scroll reveals progress gradually and stop at full visibility', () => {
  const h = controller();
  const opacity = () => Number(h.reveal.style.getPropertyValue('--reveal-opacity'));
  assert.equal(opacity(), 0);
  h.reveal.top = 542; h.events.scroll(); h.flush();
  assert.equal(opacity(), .5);
  // Re-measuring an animated element must not feed its transform back into the reveal.
  h.events.scroll(); h.flush();
  assert.equal(opacity(), .5);
  h.reveal.top = 100; h.events.scroll(); h.flush();
  assert.equal(opacity(), 1);
  assert.equal(h.reveal.style.getPropertyValue('--reveal-y'), '0.0px');
});

test('reduced motion keeps content visible and disables the entrance', () => {
  const h = controller(true);
  assert.equal(h.classes.has('home-motion'), false);
  assert.equal(h.reveal.style.getPropertyValue('--reveal-opacity'), '');
  h.motion.matches = false; h.motion.changed();
  assert.equal(h.classes.has('home-motion'), true);
  h.classes.add('intro-pending');
  h.motion.matches = true; h.motion.changed();
  assert.equal(h.classes.has('home-motion'), false);
  assert.equal(h.classes.has('intro-pending'), false);
});

test('the discover cue preserves keyboard focus and respects motion preferences', () => {
  for (const reduced of [false, true]) {
    const h = controller(reduced);
    let prevented = false;
    h.cue.listeners.click({ preventDefault: () => { prevented = true; } });
    assert.ok(prevented);
    assert.equal(h.destination.scrollOptions.behavior, reduced ? 'auto' : 'smooth');
    assert.equal(h.destination.attributes.tabindex, '-1');
    assert.equal(h.destination.focusOptions.preventScroll, true);
  }
});

test('joining stays within reach after the hero and yields to the full join section', () => {
  for (const reduced of [false, true]) {
    const h = controller(reduced);
    assert.equal(h.classes.has('home-away'), false);
    h.hero.top = -800; h.events.scroll(); h.flush();
    assert.equal(h.classes.has('home-away'), true);
    h.joinSection.top = 500; h.events.scroll(); h.flush();
    assert.equal(h.classes.has('home-away'), false);
    h.hero.top = 68; h.joinSection.top = 4000; h.events.scroll(); h.flush();
    assert.equal(h.classes.has('home-away'), false);
  }
});

function gate({ reduced = false, search = '', hash = '', type = 'navigate' } = {}) {
  const classes = new Set();
  vm.runInNewContext(source('intro-gate.js'), {
    document: { documentElement: { classList: { add: value => classes.add(value) } } },
    window: { matchMedia: () => ({ matches: reduced }), location: { search, hash },
      performance: { getEntriesByType: () => [{ type }] } }
  });
  return classes.has('intro-pending');
}

test('fresh visits and reloads play the cloud entrance', () => {
  assert.ok(gate());
  assert.ok(gate({ type: 'reload' }));
});

test('anchors and history restores bypass the entrance', () => {
  assert.equal(gate({ hash: '#discover' }), false);
  assert.equal(gate({ type: 'back_forward' }), false);
  assert.ok(gate({ hash: '#discover', search: '?intro=1' }));
});

test('reduced motion wins even when replay is requested', () => {
  assert.equal(gate({ reduced: true, search: '?intro=1' }), false);
});
