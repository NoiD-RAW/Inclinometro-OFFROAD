/* global __dirname */
const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const { join } = require('node:path');
const { test } = require('node:test');
const vm = require('node:vm');
const ts = require('typescript');

function loadTypeScript(path, imports = {}) {
  const code = ts.transpileModule(readFileSync(path, 'utf8'), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX, target: ts.ScriptTarget.ES2022 },
    fileName: path,
  }).outputText;
  const module = { exports: {} };
  const factory = vm.runInThisContext(`(function(require, module, exports) { ${code}\n})`, { filename: path });
  factory((name) => name in imports ? imports[name] : require(name), module, module.exports);
  return module.exports;
}

const helpers = loadTypeScript(join(__dirname, 'accessibility.ts'));
const emptyRect = () => ({ left: 0, top: 0, right: 0, bottom: 0 });
const geometry = (density = 3) => ({
  width: 360 * density, height: 720 * density, density,
  hits: {
    menu: { left: 312 * density, top: 0, right: 360 * density, bottom: 48 * density },
    hist: { left: 264 * density, top: 0, right: 312 * density, bottom: 48 * density },
    tripX: 180 * density, tripY: 630 * density, tripR: 54 * density,
  },
});
const state = (changes = {}) => ({
  roll: -15.3, pitch: 47.2, heading: 359, altitude: 900, kmh: 12, gps: true,
  pitchLevel: 2, rollLevel: 1, max: [47, 0, 16, 0], recording: false, elapsed: 0,
  ...changes,
});

test('native focus and touch bounds follow renderer hits at each density', () => {
  for (const density of [1, 1.5, 2, 3]) {
    const { controls, readings } = helpers.industrialAccessibility(geometry(density), false);
    assert.equal(controls.length, 3);
    assert.deepEqual(controls.find((control) => control.id === 'settings').bounds, { left: 312, top: 0, width: 48, height: 48 });
    assert.deepEqual(controls.find((control) => control.id === 'history').bounds, { left: 264, top: 0, width: 48, height: 48 });
    assert.deepEqual(controls.find((control) => control.id === 'trip').bounds, { left: 126, top: 576, width: 108, height: 108 });
    assert.deepEqual(readings, { left: 0, top: 48, width: 360, height: 528 });
  }
});

test('miniature and PiP do not expose missing or hidden controls', () => {
  const miniature = { width: 240, height: 150, density: 1, hits: { menu: emptyRect(), hist: emptyRect(), tripX: 0, tripY: 0, tripR: 0 } };
  assert.deepEqual(helpers.industrialAccessibility(miniature, false), { controls: [], readings: { left: 0, top: 0, width: 240, height: 150 } });
  assert.deepEqual(helpers.industrialAccessibility(geometry(), true, true), { controls: [], readings: { left: 0, top: 0, width: 360, height: 720 } });
  assert.deepEqual(helpers.industrialAccessibility(null, false), { controls: [], readings: null });
});

test('native frames stay within the canvas and layout changes are detected', () => {
  const original = geometry();
  assert.equal(helpers.sameIndustrialGeometry(original, geometry()), true);
  assert.equal(helpers.sameIndustrialGeometry(original, { ...geometry(), width: 2000 }), false);
  const changed = geometry();
  changed.hits.tripR += 1;
  assert.equal(helpers.sameIndustrialGeometry(original, changed), false);
  const overhanging = geometry(1);
  overhanging.hits.menu = { left: 340, top: -10, right: 400, bottom: 48 };
  assert.deepEqual(helpers.industrialAccessibility(overhanging, false).controls.find((control) => control.id === 'settings').bounds,
    { left: 340, top: 0, width: 20, height: 48 });
});

test('labels expose current readings, warnings, recording state, and unavailable GPS', () => {
  const label = helpers.industrialReadings(state());
  assert.match(label, /Cabeceo: 47 grados, peligro/);
  assert.match(label, /Alabeo: -15 grados, precaución/);
  assert.match(label, /Brújula: norte, 359 grados/);
  assert.match(label, /Altitud: 900 metros/);
  assert.match(label, /Velocidad: 12 kilómetros por hora/);
  assert.match(label, /Sin salida en curso/);
  const recording = helpers.industrialReadings(state({ recording: true, elapsed: 65000, gps: false, heading: null, altitude: null, pitch: -58 }));
  assert.match(recording, /Cabeceo: -58 grados/);
  assert.match(recording, /Brújula: no disponible/);
  assert.match(recording, /Altitud: no disponible/);
  assert.match(recording, /GPS no disponible/);
  assert.match(recording, /Grabando salida, 1 minutos y 5 segundos/);
  assert.equal(helpers.industrialAccessibility(geometry(), true).controls.find((control) => control.id === 'trip').label, 'Finalizar salida');
});

function descendants(element) {
  if (!element || typeof element !== 'object') return [];
  const children = element.props?.children;
  return [element, ...(Array.isArray(children) ? children.flat(Infinity) : [children]).flatMap(descendants)];
}

test('the dashboard renders three independently actionable native buttons and a quiet readings element', () => {
  let stateIndex = 0;
  const pressed = [];
  const module = loadTypeScript(join(__dirname, 'index.tsx'), {
    react: {
      useState: (initial) => { const index = stateIndex++; return [index === 1 ? geometry() : initial, (value) => pressed.push(value)]; },
      useRef: (initial) => ({ current: initial }), useCallback: (callback) => callback,
    },
    'react-native': { View: 'View', Text: 'Text', Pressable: 'Pressable', StyleSheet: { absoluteFill: {}, create: (styles) => styles } },
    'expo-linear-gradient': { LinearGradient: 'LinearGradient' },
    '../../gfx/assets': { useAssets: () => ({}) },
    '../../gfx/color': { BLACK: 0, rgb: () => 0, css: () => '#000', shade: () => 0 },
    '../../gfx/draw': {}, '../../gfx/SkiaView': { SkiaView: 'SkiaView' },
    '../types': { KINDS: ['side', 'rear', 'top'] }, './accessibility': helpers,
    './drawDash': { IND_CAUTION: 0, IND_DANGER: 0, drawIndustrial: () => geometry().hits },
  });
  const activated = [];
  const actions = { onSettings: () => activated.push('settings'), onHistory: () => activated.push('history'), onTrip: () => activated.push('trip') };
  const tree = descendants(module.IndustrialTheme.Dash({ state: state({ recording: true }), accent: 0, vehicle: {}, actions, pip: false }));
  const buttons = tree.filter((element) => element.type === 'Pressable');
  assert.equal(buttons.length, 3);
  for (const [label, action] of [['Ajustes', 'settings'], ['Histórico de salidas', 'history'], ['Finalizar salida', 'trip']]) {
    const button = buttons.find((element) => element.props.accessibilityLabel === label);
    assert.equal(button.props.accessibilityRole, 'button');
    assert.equal(button.props.accessible, true);
    button.props.onPress();
    assert.equal(activated.at(-1), action);
  }
  const trip = buttons.find((element) => element.props.accessibilityLabel === 'Finalizar salida');
  trip.props.onPressIn(); trip.props.onPressOut();
  assert.deepEqual(pressed, [true, false]);
  const readings = tree.find((element) => element.props.accessibilityRole === 'text');
  assert.match(readings.props.accessibilityLabel, /Cabeceo: 47 grados/);
  assert.equal(readings.props.accessibilityLiveRegion, 'none');
  const canvas = tree.find((element) => element.type === 'SkiaView');
  assert.equal(canvas.props.accessibilityElementsHidden, true);
  assert.equal(canvas.props.importantForAccessibility, 'no-hide-descendants');
  assert.equal(canvas.props.pointerEvents, 'none');
  assert.equal(tree[0].props.accessible, false);
});

test('SkiaView reports drawn geometry after commit, never while rendering', () => {
  const effects = [], calls = [];
  const module = loadTypeScript(join(__dirname, '../../gfx/SkiaView.tsx'), {
    react: { useState: () => [{ w: 100, h: 200 }, () => {}], useMemo: (callback) => callback(), useEffect: (effect) => effects.push(effect) },
    'react-native': { View: 'View', PixelRatio: { get: () => 3 }, StyleSheet: { absoluteFill: {} } },
    '@shopify/react-native-skia': {
      Canvas: 'Canvas', Picture: 'Picture',
      Skia: { XYWHRect: () => ({}), PictureRecorder: () => ({ beginRecording: () => ({ scale: () => {} }), finishRecordingAsPicture: () => ({}) }) },
    },
  });
  module.SkiaView({ deps: [], draw: (_canvas, width, height, density) => calls.push(['draw', width, height, density]), onDrawn: () => calls.push(['drawn']) });
  assert.deepEqual(calls, [['draw', 300, 600, 3]]);
  for (const effect of effects) effect();
  assert.deepEqual(calls, [['draw', 300, 600, 3], ['drawn']]);
});
