import { ClipOp, Skia, StrokeJoin, type SkCanvas, type SkImage, type SkPath, type SkTypeface } from '@shopify/react-native-skia';

import type { Assets } from '../../gfx/assets';
import { BLACK, WHITE, fromHsv, rgb, toHsv } from '../../gfx/color';
import { R, bitmapPaint, drawArc, drawImage, drawText, fill, path, stroke } from '../../gfx/draw';
import { round } from '../../fmt';
import { CAUTION, DANGER, OK } from '../types';
import { TOPO_DATA } from './topoData';

const CARDS = ['N', 'NE', 'E', 'SE', 'S', 'SO', 'O', 'NO'];
const mod = (a: number, n: number) => ((a % n) + n) % n;

/** Curvas de nivel topográficas que llenan todo el círculo. */
let minor: SkPath | null = null, major: SkPath | null = null;
function topoPaths() {
  if (minor && major) return { minor, major };
  const mi = Skia.PathBuilder.Make(), ma = Skia.PathBuilder.Make();
  for (const chunk of TOPO_DATA) for (const line of chunk.split(';')) {
    if (!line.trim()) continue;
    const t = line.trim().split(' ');
    const b = t[0] === 'M' ? ma : mi;
    b.moveTo(Number(t[1]) / 10, Number(t[2]) / 10);
    for (let i = 3; i + 1 < t.length; i += 2) b.lineTo(Number(t[i]) / 10, Number(t[i + 1]) / 10);
  }
  minor = mi.build(); major = ma.build();
  return { minor, major };
}
/** Dibujar con el origen en el centro del reloj y ya recortado al círculo. */
function drawTopo(c: SkCanvas, majorColor: number, minorColor: number) {
  const { minor: mi, major: ma } = topoPaths();
  const p1 = stroke(minorColor, 0.7, 'round'); p1.setStrokeJoin(StrokeJoin.Round); c.drawPath(mi, p1);
  const p2 = stroke(majorColor, 1.3, 'round'); p2.setStrokeJoin(StrokeJoin.Round); c.drawPath(ma, p2);
}
const minorColor = (accent: number) => { const [h, s, v] = toHsv(accent); return fromHsv(h, s, v * 0.58); };

function text(c: SkCanvas, s: string, x: number, y: number, size: number, color: number, face: SkTypeface, outline = false, fakeBold = false) {
  if (outline) drawText(c, s, x, y, face, size, stroke(BLACK, 3, 'round'), 'center', fakeBold);
  drawText(c, s, x, y, face, size, fill(color), 'center', fakeBold);
}
/** Los dos relojes comparten un lienzo de 190x214 unidades que se escala a la vista. */
function frame(c: SkCanvas, w: number, h: number) {
  const sc = Math.min(w / 190, h / 214);
  c.translate((w - 190 * sc) / 2, (h - 214 * sc) / 2); c.scale(sc, sc);
}
const clipCircle = (c: SkCanvas) => c.clipPath(path((b) => b.addCircle(95, 112, 56)), ClipOp.Intersect, true);

export type GaugeData = { title: string; rear: boolean; angle: number; accent: number; vehicle: SkImage; level: number; caution: number; maxLo: number; maxHi: number };

/** Reloj de inclinación (cabeceo o alabeo). En Android, el pincel queda con extremos redondeados tras las curvas de nivel: aquí igual. */
export function drawGauge(c: SkCanvas, w: number, h: number, g: GaugeData, a: Assets) {
  const angle = Math.min(40, Math.max(-40, g.angle));                             // la escala llega a ±40°
  c.save(); frame(c, w, h);
  const col = g.level === DANGER ? WHITE : g.level === CAUTION ? g.caution : rgb(80, 200, 120);   // en peligro el fondo es rojo: cifra en blanco
  const n = round(g.angle);
  text(c, `${g.rear ? Math.abs(n) : n}°`, 95, 40, 24, col, a.monoBold, false, a.monoFake);         // el alabeo se muestra siempre en positivo
  c.drawCircle(95, 112, 56, fill(BLACK));
  c.drawCircle(95, 112, 56, stroke(rgb(68, 68, 68), 1, 'round'));
  if (g.level !== OK) c.drawCircle(95, 112, 57, stroke(g.level === DANGER ? WHITE : g.caution, 4, 'round'));   // aro de aviso

  c.save(); clipCircle(c); c.translate(95, 112);
  drawTopo(c, g.accent, minorColor(g.accent)); c.restore();                      // curvas de nivel fijas, en el color elegido

  const tilt = g.rear ? -angle : angle;
  c.save(); c.translate(95, 112);
  const sw = stroke(WHITE, 2, 'round');
  drawArc(c, R(-62, -62, 62, 62), -45, 90, sw); drawArc(c, R(-62, -62, 62, 62), 135, 90, sw);
  for (let dd = -40; dd <= 40; dd += 5) {
    const r = (dd * Math.PI) / 180, len = dd % 10 === 0 ? 8 : 4, p = stroke(WHITE, dd % 10 === 0 ? 2 : 1.2, 'round');
    for (const s of [1, -1]) c.drawLine(s * 62 * Math.cos(r), -62 * Math.sin(r), s * (62 + len) * Math.cos(r), -(62 + len) * Math.sin(r), p);
  }
  for (const s of [1, -1]) {
    text(c, '40', s * 60.5, -47, 11, WHITE, a.sans); text(c, '20', s * 74.2, -23, 11, WHITE, a.sans);
    text(c, '20', s * 74.2, 31, 11, WHITE, a.sans); text(c, '40', s * 60.5, 55, 11, WHITE, a.sans);
  }
  marker(c, Math.min(45, Math.max(-45, -tilt)), false); marker(c, Math.min(45, Math.max(-45, tilt)), true);
  c.drawCircle(0, 0, 38, fill(rgb(13, 14, 16)));
  c.drawCircle(0, 0, 38, stroke(WHITE, 2, 'round'));
  c.drawLine(-34, 0, 34, 0, stroke(rgb(230, 255, 250), 1.5, 'round'));         // horizonte de referencia
  c.restore();

  const v = g.vehicle, vw = v.width(), vh = v.height();                          // el vehículo se inclina los mismos grados
  const f = 35 / Math.hypot(vw / 2, vh / 2);                                     // encaja siempre dentro del círculo, giro incluido
  c.save(); c.translate(95, 112); c.rotate(tilt, 0, 0); c.scale(f, f);
  drawImage(c, v, R(-vw / 2, -vh / 2, vw / 2, vh / 2), bitmapPaint()); c.restore();
  const t = g.level === DANGER ? `${g.title} · PELIGRO` : g.level === CAUTION ? `${g.title} · PRECAUCIÓN` : g.title;
  text(c, t, 95, 192, 13, g.level === OK ? rgb(207, 216, 220) : col, g.level === OK ? a.sans : a.sansBold);
  const mx = g.rear ? `máx  izq ${round(g.maxLo)}° · der ${round(g.maxHi)}°` : `máx  -${round(g.maxLo)}° · +${round(g.maxHi)}°`;
  text(c, mx, 95, 209, 11, g.level === DANGER ? rgb(240, 220, 220) : rgb(140, 150, 155), a.sans);   // máximos, discretos
  c.restore();
}

function marker(c: SkCanvas, deg: number, mirror: boolean) {
  c.save(); if (mirror) c.scale(-1, 1); c.rotate(-deg, 0, 0);
  c.drawPath(path((b) => { b.moveTo(61, 0); b.lineTo(69, -4.5); b.lineTo(69, 4.5); b.close(); }), fill(rgb(229, 57, 53)));
  c.restore();
}

export type CompassData = { heading: number; hasHeading: boolean; kmh: number; moving: boolean; altitude: number | null; accent: number; vehicle: SkImage };

/** Brújula con el rumbo del GPS. El mapa de curvas de nivel y la rosa giran; el vehículo (vista superior) queda fijo. */
export function drawCompass(c: SkCanvas, w: number, h: number, k: CompassData, a: Assets) {
  c.save(); frame(c, w, h);                                                        // mismo lienzo que los relojes de inclinación
  const { heading } = k;
  const live = k.hasHeading && k.moving;
  const hh = mod(round(heading), 360);
  const label = k.hasHeading ? `${String(hh).padStart(3, '0')}° ${CARDS[mod(Math.trunc((heading + 22.5) / 45), 8)]}` : '---°';
  text(c, label, 95, 40, 24, live ? k.accent : rgb(140, 150, 155), a.sansBold);

  c.drawCircle(95, 112, 56, fill(BLACK));
  c.drawCircle(95, 112, 56, stroke(rgb(68, 68, 68), 1, 'round'));
  c.save(); clipCircle(c); c.translate(95, 112); c.rotate(-heading, 0, 0);
  drawTopo(c, k.accent, minorColor(k.accent)); c.restore();

  c.save(); c.translate(95, 112);
  for (let dd = 0; dd < 360; dd += 10) {
    const an = ((dd - heading) * Math.PI) / 180, sx = Math.sin(an), sy = -Math.cos(an);
    const ln = dd % 30 === 0 ? 8 : 4;
    c.drawLine(62 * sx, 62 * sy, (62 + ln) * sx, (62 + ln) * sy, stroke(WHITE, dd % 30 === 0 ? 2 : 1.2, 'round'));
  }
  c.save(); c.rotate(-heading, 0, 0);
  c.drawPath(path((b) => { b.moveTo(0, -61); b.lineTo(-4.5, -69); b.lineTo(4.5, -69); b.close(); }), fill(rgb(229, 57, 53))); c.restore();   // norte
  c.drawPath(path((b) => { b.moveTo(0, -62); b.lineTo(-5, -72); b.lineTo(5, -72); b.close(); }), fill(WHITE));                                // rumbo (fijo)
  for (const [dd, t] of [[0, 'N'], [90, 'E'], [180, 'S'], [270, 'O']] as const) {
    const an = ((dd - heading) * Math.PI) / 180;
    text(c, t, 47 * Math.sin(an), -47 * Math.cos(an) + 5, 14, t === 'N' ? rgb(255, 90, 80) : WHITE, a.sansBold, true);
  }
  c.drawCircle(0, 0, 38, fill(rgb(13, 14, 16)));
  c.drawCircle(0, 0, 38, stroke(WHITE, 2, 'round'));
  c.restore();

  const v = k.vehicle, vw = v.width(), vh = v.height();                          // vista superior, ajustada al círculo
  const f = 62 / vh;                                                             // alto fijo: el vehículo siempre ocupa lo mismo
  const fw = Math.min(f, 62 / vw);                                               // si fuese muy ancho, se limita al ancho
  c.save(); c.translate(95, 112); c.scale(fw, fw);
  drawImage(c, v, R(-vw / 2, -vh / 2, vw / 2, vh / 2), bitmapPaint()); c.restore();
  const st = !k.hasHeading ? 'esperando GPS' : !k.moving ? 'parado' : `${round(k.kmh)} km/h`;
  const foot = k.altitude !== null ? `${st} · ${round(k.altitude)} m` : `Brújula · ${st}`;   // con GPS, también la altitud
  text(c, foot, 95, 192, 13, rgb(207, 216, 220), a.sans);
  c.restore();
}
