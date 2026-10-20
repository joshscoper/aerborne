// Deterministic simulation tests; run with node --test tests/arcade_games.test.cjs.
const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const source = fs.readFileSync(path.join(__dirname, '../aerborne/static/js/arcade/games.js'), 'utf8');
const assetSource = fs.readFileSync(path.join(__dirname, '../aerborne/static/js/arcade/assets.js'), 'utf8');

function harness(slug) {
  let seed = 827, frames = new Map(), nextId = 1, now = 0, paused = false, score = 0, ended = null, status = '';
  const math = Object.create(Math);
  math.random = () => ((seed = (seed * 16807) % 2147483647) - 1) / 2147483646;
  const factories = {}, listeners = [], actions = [], drawn = new Set();
  let rendered = [], tx = 0, ty = 0;
  const transforms = [];
  const ctx = new Proxy({}, {get: (_, key) => {
    if (key === 'createLinearGradient' || key === 'createRadialGradient') return () => ({addColorStop() {}});
    if (key === 'save') return () => transforms.push([tx,ty]);
    if (key === 'restore') return () => { [tx,ty] = transforms.pop() || [0,0]; };
    if (key === 'translate') return (x,y) => {tx += x; ty += y;};
    if (key === 'drawImage') return image => {if (image.name) rendered.push({name:image.name,x:tx,y:ty});};
    return () => {};
  }, set: () => true});
  const canvas = {getBoundingClientRect: () => ({left: 0, top: 0, width: 480, height: 640}), setPointerCapture() {}};
  const win = {};
  const sandbox = {Math: math, Set, module: {exports: {}}, window: win,
    Arcade: {register: (name, factory) => {factories[name] = factory;}},
    requestAnimationFrame: fn => {const id = nextId++; frames.set(id, fn); return id;},
    cancelAnimationFrame: id => frames.delete(id)};
  vm.runInNewContext(assetSource, sandbox);
  vm.runInNewContext(source, sandbox);
  let game;
  const catalog = win.ArcadeSpriteCatalog[slug] || {};
  const env = {ctx, canvas, W: 480, H: 640, reduced: true, paused: () => paused,
    img: name => {
      if (name === 'landscape') return {width: 960, height: 540};
      if (name === 'cloud_soft' && !catalog[name]) return null;
      assert.ok(catalog[name], 'Unknown sprite in ' + slug + ': ' + name);
      drawn.add(name); return {width: 256, height: 256, name};
    }, bounds: name => catalog[name] && catalog[name].box,
    action: (label, shortcut, fn) => {
      const item = {label, shortcut, fn, enabled: true}; actions.push(item);
      return (caption, enabled) => {item.label = caption; item.enabled = enabled;};
    }, score: n => {score = n;}, status: s => {status = s;},
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
      const ready = [...frames.values()]; frames.clear();
      if (ready.length) rendered = [];
      ready.forEach(fn => fn(now));
    }
  }
  return {rules: sandbox.module.exports, factories, actions, drawn, visible: () => rendered.slice(),
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
  h.pause(false); h.advance(66);
  assert.equal(h.state().ended, "Time's up"); assert.equal(h.state().frames, 0);
});

test('gem matches charge a usable burst, which cannot be spent while paused', () => {
  const h = harness('gem-rush'), original = h.rules.board;
  let board;
  h.rules.board = n => {board = original(n); return board;};
  h.start();
  assert.equal(h.actions[0].enabled, false);
  for (let move = 0; move < 25 && !h.actions[0].enabled && !h.state().ended; move++) {
    const pair = h.rules.legal(board, 7);
    for (const i of pair) h.input('pointerdown', {clientX: 60 + i % 7 * 60, clientY: 160 + Math.floor(i / 7) * 60});
    h.advance(1.8);
  }
  assert.equal(h.actions[0].enabled, true, 'eight cleared match groups should charge a burst');
  const before = h.state().score;
  h.pause(true); h.actions[0].fn(); assert.equal(h.state().score, before);
  h.pause(false); h.input('keydown', {code: 'KeyB'});
  assert.ok(h.state().score >= before + 80);
  assert.equal(h.actions[0].enabled, false);
});

test('rune armour, shields, special bricks and explosions have distinct effects', () => {
  const {rules} = harness();
  const bricks = rules.runeWall(1);
  const armoured = bricks.findIndex(b => b.type === 'enchanted');
  assert.equal(rules.damageRunes(bricks, armoured).points, 10);
  assert.equal(bricks[armoured].alive, true);
  assert.equal(bricks[armoured].hp, 1);
  assert.equal(rules.damageRunes(bricks, armoured).points, 20);
  assert.equal(bricks[armoured].alive, false);
  const shield = bricks.findIndex(b => b.type === 'shield');
  assert.equal(rules.damageRunes(bricks, shield).shield, false);
  assert.equal(rules.damageRunes(bricks, shield).shield, true);
  const special = bricks.findIndex(b => b.type === 'special');
  const reward = rules.damageRunes(bricks, special);
  assert.equal(reward.points, 30); assert.equal(reward.wide, true);
  const explosive = bricks.findIndex(b => b.type === 'explosive');
  const blast = rules.damageRunes(bricks, explosive);
  assert.equal(blast.blast, true); assert.ok(blast.broken.length > 1);
  assert.equal(new Set(blast.broken).size, blast.broken.length);
  assert.ok(bricks.some(b => b.alive), 'an explosion affects neighbours, not the whole wall');
});

test('the complete catalog has 115 distinct sprites with valid measured source bounds', () => {
  const window = {};
  vm.runInNewContext(assetSource, {window});
  assert.equal(Object.keys(window.ArcadeSpriteCatalog).length, 6);
  let count = 0;
  for (const catalog of Object.values(window.ArcadeSpriteCatalog)) {
    for (const asset of Object.values(catalog)) {
      const [x,y,w,h] = asset.box;
      assert.ok(w > 0 && h > 0 && x >= 0 && y >= 0 && x+w <= 512 && y+h <= 512);
      assert.ok(fs.existsSync(path.join(__dirname, '../aerborne/static/img/', asset.file)));
      count++;
    }
  }
  assert.equal(count, 115);
});

test('eight-pair memory awards matches before completion and retains partial scores at timeout', () => {
  const h = harness('codex-memory'); h.start(); h.advance(2.6);
  // Work through all pairs using normal selections; never inspect the shuffled card state.
  assert.equal(h.rules.memoryFaces.length, 8);
  assert.equal(new Set(h.rules.memoryFaces).size, 8);
  for (let a = 0; a < 16; a++) for (let b = a + 1; b < 16; b++) {
    for (const i of [a,b]) h.input('pointerdown', {clientX: 84 + i % 4 * 104, clientY: 169 + Math.floor(i / 4) * 118});
    h.advance(.75);
    if (h.state().ended) break;
  }
  h.advance(121);
  assert.ok(h.state().score > 0 && h.state().score <= 600);
  assert.ok(h.state().ended); assert.equal(h.state().frames, 0);
  assert.ok(h.drawn.has('card_back') && h.drawn.has('card_water') && h.drawn.has('card_wind'));
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

test('sky hop can clear several visible island gaps and collect their rewards', () => {
  const h = harness('sky-hop'); h.start(); h.input('keydown', {code:'Space'});
  let previousY = 315, cooldown = 0;
  for (let frame = 0; frame < 1500 && !h.state().ended && h.state().score < 8; frame++) {
    const view = h.visible();
    const bird = view.find(p => p.name.startsWith('pufflet_flight_pose_'));
    // Navigate the visible gap until the entire column passes, even after its star is collected.
    const columns = new Map();
    for (const sprite of view.filter(p => (p.name === 'island_tall' || p.name === 'island_large') && p.x > 80)) {
      if (!columns.has(sprite.x)) columns.set(sprite.x, {x:sprite.x,upper:-Infinity,lower:Infinity});
      const column = columns.get(sprite.x);
      if (sprite.name === 'island_tall') column.upper = Math.max(column.upper,sprite.y+74);
      else column.lower = Math.min(column.lower,sprite.y-59);
    }
    const target = [...columns.values()].filter(p => isFinite(p.upper) && isFinite(p.lower)).sort((a,b) => a.x-b.x)[0];
    const targetY = target ? (target.upper+target.lower)/2 : 315;
    if (bird && cooldown <= 0 && bird.y > targetY + 25 && bird.y >= previousY) {
      h.input('keydown', {code:'Space'}); cooldown = .16;
    }
    if (bird) previousY = bird.y;
    cooldown -= 1/60; h.advance(1/60);
  }
  assert.equal(h.state().ended, null, 'the visible centre line should stay clear of island collisions');
  assert.ok(h.state().score >= 8, 'successful navigation and collectibles must add points');
});

test('cloud catch ends and releases movement on pause', () => {
  const h = harness('cloud-catch'); h.start(); h.input('keydown', {code: 'ArrowRight'});
  h.pause(true); const before = h.state(); h.advance(3); assert.deepEqual(h.state(), before);
  h.pause(false); h.advance(91);
  assert.ok(h.state().ended); assert.ok(h.state().score <= 3000); assert.equal(h.state().frames, 0);
});

test('cloud power-ups can be caught and activate the timed shield and magnet', () => {
  const h = harness('cloud-catch'); h.start();
  let shield = false, magnet = false;
  for (let frame = 0; frame < 1600 && !h.state().ended && !(shield && magnet); frame++) {
    const view = h.visible();
    const hazards = view.filter(p => (p.name === 'cloud_storm' || p.name === 'cloud_thunder') && p.y > 400);
    const power = view.find(p => (p.name === 'power_shield' || p.name === 'power_magnet') && p.y > 475 && p.y < 590);
    const lanes = [48,120,200,280,360,432];
    const safest = lanes.sort((a,b) => {
      const clearance = x => Math.min(500, ...hazards.map(p => Math.abs(p.x-x)));
      return clearance(b)-clearance(a);
    })[0];
    h.input('pointermove', {clientX:power ? power.x : safest, clientY:560});
    h.advance(1/30);
    shield ||= h.state().status.includes('Shield');
    magnet ||= h.state().status.includes('Magnet');
  }
  assert.equal(shield, true, 'the first power-up should activate a shield');
  assert.equal(magnet, true, 'the next power-up should activate a magnet');
});

test('rune breaker launches, breaks runes, pauses, and finishes at the round limit', () => {
  const h = harness('rune-breaker'); h.start(); h.input('keydown', {code: 'Space'}); h.advance(1.2);
  assert.ok(h.state().score >= 10, 'launch should reach and break the wall');
  h.pause(true); const before = h.state(); h.advance(3); assert.deepEqual(h.state(), before);
  h.pause(false);
  for (let i = 0; i < 90 && !h.state().ended; i++) {h.input('keydown', {code: 'Space'}); h.advance(1);}
  assert.ok(h.state().ended); assert.ok(h.state().score <= 10000); assert.equal(h.state().frames, 0);
});
