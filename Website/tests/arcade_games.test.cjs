// Deterministic simulation tests; run with node --test tests/arcade_games.test.cjs.
const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const source = fs.readFileSync(path.join(__dirname, '../aerborne/static/js/arcade/games.js'), 'utf8');

function harness(slug) {
  let seed = 827, frames = new Map(), nextId = 1, now = 0, paused = false, score = 0, ended = null, status = '';
  const math = Object.create(Math);
  math.random = () => ((seed = (seed * 16807) % 2147483647) - 1) / 2147483646;
  const factories = {}, listeners = [];
  const ctx = new Proxy({}, {get: (_, key) => key === 'createLinearGradient' || key === 'createRadialGradient'
    ? () => ({addColorStop() {}}) : () => {}, set: () => true});
  const canvas = {getBoundingClientRect: () => ({left: 0, top: 0, width: 480, height: 640}), setPointerCapture() {}};
  const win = {};
  const sandbox = {Math: math, Set, module: {exports: {}}, window: win,
    Arcade: {register: (name, factory) => {factories[name] = factory;}},
    requestAnimationFrame: fn => {const id = nextId++; frames.set(id, fn); return id;},
    cancelAnimationFrame: id => frames.delete(id)};
  vm.runInNewContext(source, sandbox);
  let game;
  const env = {ctx, canvas, W: 480, H: 640, reduced: true, paused: () => paused,
    img: () => null, score: n => {score = n;}, status: s => {status = s;},
    end: why => {ended = why; game.stop();},
    on: (target, name, fn) => listeners.push({target, name, fn})};
  function input(name, values = {}, target = canvas) {
    const e = {preventDefault() {}, pointerId: 1, pointerType: 'mouse', ...values};
    listeners.filter(l => l.target === target && l.name === name).forEach(l => l.fn(e));
  }
  function advance(seconds) {
    const count = Math.ceil(seconds * 120);
    for (let i = 0; i < count; i++) {
      now += 1000 / 120;
      const ready = [...frames.values()]; frames.clear(); ready.forEach(fn => fn(now));
    }
  }
  return {rules: sandbox.module.exports, factories,
    start() {game = factories[slug](env); game.start(); advance(.02);},
    input, advance, pause: value => {paused = value; if (value && game.release) game.release();},
    state: () => ({score, ended, status, frames: frames.size})};
}

test('fresh match-three boards have no opening matches and always have a legal move', () => {
  const {rules, factories} = harness();
  assert.equal(Object.keys(factories).length, 6);
  for (let i = 0; i < 1000; i++) {
    const board = rules.board(7), original = [...board];
    assert.equal(rules.matches(board, 7).length, 0);
    assert.ok(rules.legal(board, 7));
    assert.deepEqual([...board], original, 'hint search must not alter the board');
  }
  assert.deepEqual([...rules.matches([0,0,0,0,1,2,3,4,5], 3)], [0,1,2]);
});

test('match-three accepts a legal swap, resolves cascades, and times out', () => {
  const h = harness('gem-rush'), original = h.rules.board;
  let board;
  h.rules.board = n => {board = original(n); return board;};
  h.start();
  for (const i of h.rules.legal(board, 7)) h.input('pointerdown', {clientX: 60 + i % 7 * 60, clientY: 160 + Math.floor(i / 7) * 60});
  h.advance(2);
  assert.ok(h.state().score >= 60);
  h.pause(true); const before = h.state(); h.advance(5); assert.deepEqual(h.state(), before);
  h.pause(false); h.advance(61);
  assert.equal(h.state().ended, "Time's up"); assert.equal(h.state().frames, 0);
});

test('memory awards pairs before completion and retains partial scores at timeout', () => {
  const h = harness('codex-memory'); h.start(); h.advance(2.6);
  // Work through all pairs using normal selections; never inspect the shuffled card state.
  for (let a = 0; a < 18; a++) for (let b = a + 1; b < 18; b++) {
    for (const i of [a,b]) h.input('pointerdown', {clientX: 102 + i % 3 * 138, clientY: 162 + Math.floor(i / 3) * 78});
    h.advance(.75);
    if (h.state().ended) break;
  }
  h.advance(121);
  assert.ok(h.state().score > 0 && h.state().score <= 600);
  assert.ok(h.state().ended); assert.equal(h.state().frames, 0);
});

test('forge rewards accurate timing, ignores held-key repeats, and ends at the round limit', () => {
  const h = harness('forge-strike'); h.start(); h.advance(1.02);
  h.input('keydown', {code: 'Space'});
  assert.equal(h.state().score, 20);
  for (let i = 0; i < 20; i++) {h.advance(.03); h.input('keydown', {code: 'Space', repeat: true});}
  assert.equal(h.state().score, 20);
  h.advance(61); assert.equal(h.state().ended, "Time's up");
});

test('sky hop waits for the first flap and collisions terminate the animation loop', () => {
  const h = harness('sky-hop'); h.start(); h.advance(10);
  assert.equal(h.state().ended, null);
  h.input('keydown', {code: 'Space'}); h.advance(4);
  assert.equal(h.state().ended, 'Flight complete'); assert.equal(h.state().frames, 0);
});

test('cloud catch ends and releases movement on pause', () => {
  const h = harness('cloud-catch'); h.start(); h.input('keydown', {code: 'ArrowRight'});
  h.pause(true); const before = h.state(); h.advance(3); assert.deepEqual(h.state(), before);
  h.pause(false); h.advance(91);
  assert.ok(h.state().ended); assert.ok(h.state().score <= 400); assert.equal(h.state().frames, 0);
});

test('rune breaker launches, breaks runes, pauses, and finishes at the round limit', () => {
  const h = harness('rune-breaker'); h.start(); h.input('keydown', {code: 'Space'}); h.advance(1.2);
  assert.ok(h.state().score >= 10, 'launch should reach and break the wall');
  h.pause(true); const before = h.state(); h.advance(3); assert.deepEqual(h.state(), before);
  h.pause(false);
  for (let i = 0; i < 90 && !h.state().ended; i++) {h.input('keydown', {code: 'Space'}); h.advance(1);}
  assert.ok(h.state().ended); assert.ok(h.state().score <= 10000); assert.equal(h.state().frames, 0);
});
