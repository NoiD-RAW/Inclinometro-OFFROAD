import { Skia, type SkCanvas, type SkColorFilter, type SkImage } from '@shopify/react-native-skia';

import { Chrome } from '../../gfx/chrome';
import { BLACK, WHITE, argb, blue, green, red, rgb, shade, toHsv, withAlpha } from '../../gfx/color';
import {
  EMPTY, R, bitmapPaint, drawArc, drawImage, drawRect, drawRoundRect, drawText, fill, linear, path, rcx, rcy, rh, rw, stroke,
  textWidth, tileShader, type Align, type RectF,
} from '../../gfx/draw';
import type { Assets } from '../../gfx/assets';
import { clock, round, thousands } from '../../fmt';
import { CAUTION, DANGER, OK, type DashState, type Vehicle } from '../types';

export const IND_CAUTION = rgb(255, 184, 0);
export const IND_DANGER = rgb(170, 18, 18);

/** Zonas táctiles que deja el dibujo (en píxeles). */
export type Hits = { menu: RectF; hist: RectF; tripX: number; tripY: number; tripR: number };

const CARDS = ['N', 'NE', 'E', 'SE', 'S', 'SO', 'O', 'NO'];
const silverTop = rgb(248, 248, 252), silverBot = rgb(158, 158, 166);
const mod = (a: number, n: number) => ((a % n) + n) % n;

/** Pantalla principal con estética industrial metálica (paneles de acero cepillado, tornillos, biseles).
 *  Se reorganiza sola: vertical, horizontal o minivista (PiP / ventana pequeña). El color principal es configurable.
 *  Traducción directa de DashView.kt: medidas en píxeles físicos. */
export function drawIndustrial(c: SkCanvas, w: number, h: number, d: number, st: DashState, accent: number, vehicle: Vehicle, a: Assets, tripDown: boolean): Hits {
  const roll = Math.min(40, Math.max(-40, st.roll)), pitch = Math.min(40, Math.max(-40, st.pitch));
  const { heading, altitude, kmh, gps, pitchLevel, rollLevel, max: maxv, recording, elapsed } = st;
  const hits: Hits = { menu: EMPTY, hist: EMPTY, tripX: 0, tripY: 0, tripR: 0 };
  const aLight = shade(accent, 0.62, 1.1), aDark = shade(accent, 1, 0.7);
  const titleFont = a.gunplay;
  let ui = 1;                                                       // escala respecto al diseño original (1440 px de ancho)
  const bp = bitmapPaint();
  const danger = () => Math.max(pitchLevel, rollLevel) === DANGER;

  // ---------- utilidades de dibujo ----------
  const tileFill = (r: RectF, rad: number, alpha: number) => {
    const p = fill(BLACK, tileShader(a.bg, ui)); if (alpha < 255) p.setAlphaf(alpha / 255);
    if (rad > 0) drawRoundRect(c, r, rad, rad, p); else drawRect(c, r, p);
  };
  const img = (b: SkImage, cx: number, cy: number, scale: number, f: SkColorFilter | null = null) =>
    drawImage(c, b, R(cx - (b.width() * scale) / 2, cy - (b.height() * scale) / 2, cx + (b.width() * scale) / 2, cy + (b.height() * scale) / 2), f ? bitmapPaint(f) : bp);
  const text = (s: string, x: number, y: number, size: number, color: number, align: Align = 'center') =>
    drawText(c, s.toUpperCase(), x, y, titleFont, size, fill(color), align);
  const metalText = (s: string, x: number, y: number, size: number, top: number, bot: number, align: Align = 'left') =>
    drawText(c, s.toUpperCase(), x, y, titleFont, size, fill(BLACK, linear(0, y - size * 0.75, 0, y, [top, bot])), align);
  const tw = (s: string, size: number) => textWidth(titleFont, size, s.toUpperCase());
  const fit = (s: string, size: number, maxW: number) => { const wd = tw(s, size); return wd > maxW ? (size * maxW) / wd : size; };

  /** Gira el tono de los gráficos naranjas (el gris metálico no se altera) hacia el color principal elegido. */
  const accentFilter = (darker: boolean): SkColorFilter => {
    const r = ((toHsv(accent)[0] - 27) * Math.PI) / 180, co = Math.cos(r), s = Math.sin(r);
    const k = darker ? 0.8 : 1;
    return Skia.ColorFilter.MakeMatrix([
      k * (0.213 + 0.787 * co - 0.213 * s), k * (0.715 - 0.715 * co - 0.715 * s), k * (0.072 - 0.072 * co + 0.928 * s), 0, 0,
      k * (0.213 - 0.213 * co + 0.143 * s), k * (0.715 + 0.285 * co + 0.14 * s), k * (0.072 - 0.072 * co - 0.283 * s), 0, 0,
      k * (0.213 - 0.213 * co - 0.787 * s), k * (0.715 - 0.715 * co + 0.715 * s), k * (0.072 + 0.928 * co + 0.072 * s), 0, 0,
      0, 0, 0, 1, 0,
    ]);
  };

  /** Panel de acero: marco con esquinas y bordes recortados del original, pantalla interior hundida y cuatro tornillos. */
  const metalPanel = (r: RectF) => {
    const t = 14 * ui, s = 44 * ui;
    const dd = R(r.left + t, r.top + t, r.right - t, r.bottom - t);
    drawRect(c, dd, fill(BLACK, linear(0, dd.top, 0, dd.bottom, [rgb(27, 27, 30), rgb(14, 14, 16)])));
    tileFill(dd, 0, 60);
    if (danger()) drawRect(c, dd, fill(withAlpha(IND_DANGER, 120)));
    const e = a.edges;
    drawImage(c, e[4], R(r.left + s, r.top, r.right - s, r.top + t), bp);
    drawImage(c, e[5], R(r.left + s, r.bottom - t, r.right - s, r.bottom), bp);
    drawImage(c, e[6], R(r.left, r.top + s, r.left + t, r.bottom - s), bp);
    drawImage(c, e[7], R(r.right - t, r.top + s, r.right, r.bottom - s), bp);
    drawImage(c, e[0], R(r.left, r.top, r.left + s, r.top + s), bp);
    drawImage(c, e[1], R(r.right - s, r.top, r.right, r.top + s), bp);
    drawImage(c, e[2], R(r.left, r.bottom - s, r.left + s, r.bottom), bp);
    drawImage(c, e[3], R(r.right - s, r.bottom - s, r.right, r.bottom), bp);
    const ix = 37 * ui, iy = 35 * ui, sr = 20 * ui;
    for (const fx of [0, 1]) for (const fy of [0, 1]) img(a.screw, fx === 0 ? r.left + ix : r.right - ix, fy === 0 ? r.top + iy : r.bottom - iy, (2 * sr) / 48);
  };

  const inset = (b: RectF) => {
    drawRoundRect(c, b, 10, 10, fill(BLACK, linear(0, b.top, 0, b.bottom, [rgb(8, 8, 10), rgb(26, 26, 29)])));
    drawRoundRect(c, b, 10, 10, stroke(rgb(78, 78, 84), 3));
    drawRoundRect(c, R(b.left + 2, b.top + 2, b.right - 2, b.bottom - 2), 8, 8, stroke(BLACK, 1.5));
  };

  // ---------- piezas ----------
  const header = (hh: number) => {
    drawRect(c, R(0, 0, w, hh), fill(BLACK, linear(0, 0, 0, hh, [rgb(48, 48, 52), rgb(24, 24, 26)])));
    tileFill(R(0, 0, w, hh), 0, 255);
    if (danger()) drawRect(c, R(0, 0, w, hh), fill(withAlpha(IND_DANGER, 170)));
    c.drawLine(0, hh, w, hh, stroke(rgb(6, 6, 8), 3));
    c.drawLine(0, hh + 2, w, hh + 2, stroke(argb(120, 130, 130, 136), 1.5));
    if (!recording) img(a.screw, 0.04 * w, 0.2 * hh, (0.2 * hh) / 48);
    let ts = hh * 0.52; const t1 = 'INCLINÓMETRO ', t2 = 'OFFROAD';
    for (let i = 0; i < 10; i++) { if (2 * hh * 0.36 + hh * 0.2 + tw(t1, ts) + tw(t2, ts) <= w - 5.6 * hh) break; ts *= 0.9; }   // a cada lado, sitio para dos iconos
    const w1 = tw(t1, ts), w2 = tw(t2, ts), lr = hh * 0.38;
    let x = (w - (2 * lr + hh * 0.2 + w1 + w2)) / 2; const lcx = x + lr, lcy = hh / 2;
    c.drawCircle(lcx, lcy, lr, stroke(accent, Math.max(2, lr * 0.12)));
    c.drawPath(path((b) => {
      b.moveTo(lcx, lcy - lr * 0.9); b.lineTo(lcx + lr * 0.2, lcy - lr * 0.2); b.lineTo(lcx + lr * 0.9, lcy); b.lineTo(lcx + lr * 0.2, lcy + lr * 0.2);
      b.lineTo(lcx, lcy + lr * 0.9); b.lineTo(lcx - lr * 0.2, lcy + lr * 0.2); b.lineTo(lcx - lr * 0.9, lcy); b.lineTo(lcx - lr * 0.2, lcy - lr * 0.2); b.close();
    }), fill(BLACK, linear(lcx, lcy - lr, lcx, lcy + lr, [aLight, aDark])));
    x += 2 * lr + hh * 0.2; const by = lcy + ts * 0.34;
    metalText(t1, x, by, ts, silverTop, silverBot); metalText(t2, x + w1, by, ts, aLight, accent);
    const tz = Math.max(hh, Chrome.minTouch(d));                                // rueda dentada de Ajustes: área táctil de al menos 48 dp
    const iw = Math.max(hh * 1.3, tz);
    Chrome.drawGear(c, w - iw / 2, lcy, hh * 0.32, accent);
    hits.menu = R(w - iw, 0, w, tz);
    Chrome.drawHistory(c, w - iw * 1.5, lcy, hh * 0.32, accent);               // historial de salidas, junto a la rueda
    hits.hist = R(w - 2 * iw, 0, w - iw, tz);
    if (recording) {                                                            // punto rojo + tiempo, a la izquierda
      c.drawCircle(hh * 0.5, lcy, hh * 0.16, fill(Chrome.REC));
      metalText(clock(elapsed), hh * 0.85, lcy + hh * 0.15, fit('0:00:00', hh * 0.42, hh * 1.9), silverTop, silverBot);
    }
  };

  const gauge = (r: RectF, title: string, v: number, isRear: boolean, mini: boolean) => {
    metalPanel(r);
    const pw = rw(r), ph = rh(r), cx = rcx(r), cy = r.top + ph * 0.93;
    const Rr = Math.min(pw * 0.378, ph * (mini ? 0.754 : 0.68)), k = 2.0, sweep = v * k * (isRear ? -1 : 1);   // el indicador del alabeo gira en el mismo sentido que el vehículo
    const lvl = isRear ? rollLevel : pitchLevel;
    const warn = lvl === DANGER ? rgb(255, 72, 60) : lvl === CAUTION ? IND_CAUTION : 0;
    if (lvl !== OK) {                                                           // aviso: marco de color en el reloj afectado
      const bw = Math.max(4, 0.012 * Math.min(pw, ph));
      drawRoundRect(c, R(r.left + bw / 2, r.top + bw / 2, r.right - bw / 2, r.bottom - bw / 2), 14 * ui + bw, 14 * ui + bw, stroke(warn, bw));
    }
    if (!mini) {
      const t = lvl === DANGER ? `${title} · PELIGRO` : lvl === CAUTION ? `${title} · PRECAUCIÓN` : title;
      const ts = fit(t, 0.08 * ph, 0.56 * pw);
      if (lvl === OK) metalText(t, cx, r.top + 0.105 * ph, ts, silverTop, silverBot, 'center');   // rótulo centrado
      else text(t, cx, r.top + 0.105 * ph, ts, lvl === DANGER ? WHITE : warn);
      // máximos registrados, discretos en las esquinas superiores (cada uno en el lado de la escala que le corresponde)
      const lo = isRear ? maxv[2] : maxv[1], hi = isRear ? maxv[3] : maxv[0];
      const sLo = isRear ? `${round(lo)}°` : `-${round(lo)}°`, sHi = isRear ? `${round(hi)}°` : `+${round(hi)}°`;
      const mx = 0.06 * pw, ls = 0.045 * ph, vs = 0.07 * ph, my = r.top + 0.2 * ph;
      text('MÁX', r.left + mx, my, ls, rgb(150, 150, 156), 'left'); text('MÁX', r.right - mx, my, ls, rgb(150, 150, 156), 'right');
      metalText(sLo, r.left + mx, my + vs * 1.1, vs, silverTop, silverBot, 'left'); metalText(sHi, r.right - mx, my + vs * 1.1, vs, silverTop, silverBot, 'right');
    }
    const oval = R(cx - Rr, cy - Rr, cx + Rr, cy + Rr);
    drawArc(c, oval, 270 - 80, 160, stroke(rgb(96, 96, 102), Math.max(2, 0.01 * Rr)));
    if (Math.abs(v) > 0.5) {
      drawArc(c, oval, 270, sweep, stroke(argb(70, red(accent), green(accent), blue(accent)), Math.max(8, 0.05 * Rr)));
      drawArc(c, oval, 270, sweep, stroke(accent, Math.max(3, 0.02 * Rr)));
    }
    for (let t = -40; t <= 40; t += 2) {
      const an = (t * k * Math.PI) / 180, s = Math.sin(an), co = Math.cos(an);
      const major = t % 10 === 0, edge = Math.abs(t) === 40;
      const p = stroke(edge ? accent : major ? rgb(232, 232, 236) : rgb(150, 150, 156),
        edge ? Math.max(4, 0.03 * Rr) : major ? Math.max(2.5, 0.016 * Rr) : Math.max(1.2, 0.008 * Rr));
      const r1 = Rr * 0.985, r2 = r1 - (major ? 0.135 * Rr : 0.065 * Rr);
      c.drawLine(cx + r1 * s, cy - r1 * co, cx + r2 * s, cy - r2 * co, p);
    }
    const ls = Math.max(9, 0.085 * Rr);
    for (let t = -40; t <= 40; t += 10) {
      const an = (t * k * Math.PI) / 180, rr = Rr * 1.13;
      const lab = t === 0 ? '0°' : isRear ? `${Math.abs(t)}°` : t > 0 ? `+${t}°` : `${t}°`;
      text(lab, cx + rr * Math.sin(an), cy - rr * Math.cos(an) + ls * 0.35, ls, Math.abs(t) === 40 ? accent : rgb(226, 226, 230));
    }
    c.save(); c.rotate(sweep, cx, cy);
    c.drawPath(path((b) => { b.moveTo(cx, cy - Rr * 0.99); b.lineTo(cx - 0.05 * Rr, cy - Rr * 1.07); b.lineTo(cx + 0.05 * Rr, cy - Rr * 1.07); b.close(); }), fill(accent));
    c.restore();
    const bmp = isRear ? vehicle.rear : vehicle.side, tilt = isRear ? -v : v;
    const vcx = cx, vcy = cy - 0.52 * Rr;
    const bw = bmp.width(), bh = bmp.height();
    const f = Math.min((0.74 * Rr) / bw, (0.42 * Rr) / bh);
    c.save(); c.translate(vcx, vcy); c.rotate(tilt, 0, 0); c.scale(f, f);
    drawImage(c, bmp, R(-bw / 2, -bh / 2, bw / 2, bh / 2), bp); c.restore();
    c.save(); c.translate(vcx, vcy); c.rotate(tilt, 0, 0); const gy = (bh * f) / 2 + 0.03 * Rr;
    c.drawLine(-0.5 * Rr, gy, 0.5 * Rr, gy, stroke(BLACK, Math.max(2, 0.014 * Rr), 'butt',
      linear(-0.5 * Rr, 0, 0.5 * Rr, 0, [withAlpha(accent, 0), accent, accent, withAlpha(accent, 0)], [0, 0.25, 0.75, 1])));
    c.restore();
    const pcy = r.top + ph * 0.887, tsz = 0.09 * ph, vi = round(v);           // cifras algo más pequeñas
    // Alabeo: solo el valor, sin signo. Cabeceo: negativo cuando el morro está abajo, positivo cuando está arriba.
    const s = isRear ? `${Math.abs(vi)}°` : vi === 0 ? '0°' : vi > 0 ? `+${vi}°` : `${vi}°`;
    const pwid = Math.max(0.16 * pw, 2.4 * tsz);
    img(a.pill, cx, pcy, pwid / 368);                                            // pastilla hexagonal del original
    if (lvl === OK) metalText(s, cx, pcy + tsz * 0.36, tsz, aLight, accent, 'center');
    else text(s, cx, pcy + tsz * 0.36, tsz, warn);                              // la cifra toma el color del aviso
  };

  const compassCard = (r: RectF) => {
    metalPanel(r); const pw = rw(r), ph = rh(r);
    const rd = Math.min(0.37 * ph, 0.2 * pw), dcx = r.left + 0.263 * pw, dcy = r.top + 0.5 * ph;
    img(a.compass, dcx, dcy, (2 * rd) / 286);                                   // esfera con bisel (sin aguja)
    const fr = rd * 0.87;
    c.save(); c.translate(dcx, dcy); c.rotate(heading ?? 0, 0, 0);              // aguja: gira con el rumbo
    c.drawPath(path((b) => { b.moveTo(0, -0.74 * fr); b.lineTo(0.12 * fr, 0); b.lineTo(-0.12 * fr, 0); b.close(); }), fill(BLACK, linear(0, -0.74 * fr, 0, 0, [aLight, accent])));
    c.drawPath(path((b) => { b.moveTo(0, 0.74 * fr); b.lineTo(0.12 * fr, 0); b.lineTo(-0.12 * fr, 0); b.close(); }), fill(rgb(74, 74, 80)));
    c.drawCircle(0, 0, 0.1 * fr, fill(rgb(205, 205, 210))); c.drawCircle(0, 0, 0.04 * fr, fill(rgb(40, 40, 44))); c.restore();
    metalText('BRÚJULA', r.left + 0.5 * pw, r.top + 0.225 * ph, 0.145 * ph, silverTop, silverBot);
    const box = R(r.left + 0.515 * pw, r.top + 0.36 * ph, r.right - 0.043 * pw, r.top + 0.73 * ph); inset(box);
    const hd = heading;
    const s1 = hd === null ? '---' : CARDS[mod(Math.trunc((hd + 22.5) / 45), 8)] + ' ';
    const s2 = hd === null ? '' : `${String(mod(round(hd), 360)).padStart(3, '0')}°`;
    const sz = fit(s1 + s2, 0.2 * ph, rw(box) * 0.88), sx = rcx(box) - tw(s1 + s2, sz) / 2, by = rcy(box) + sz * 0.36;
    metalText(s1, sx, by, sz, aLight, accent); metalText(s2, sx + tw(s1, sz), by, sz, silverTop, silverBot);
    const sp = gps ? `${round(kmh)} km/h` : '--- km/h';                          // velocidad GPS
    metalText(sp, rcx(box), r.top + 0.86 * ph, fit(sp, 0.11 * ph, rw(box)), silverTop, silverBot, 'center');
  };

  const altCard = (r: RectF) => {
    metalPanel(r); const pw = rw(r), ph = rh(r);
    const rd = Math.min(0.37 * ph, 0.2 * pw), bcx = r.left + 0.263 * pw, bcy = r.top + 0.5 * ph;
    img(a.badge, bcx, bcy, (2 * rd) / 244);                                     // insignia con montaña
    metalText('ALTÍMETRO', r.left + 0.5 * pw, r.top + 0.225 * ph, 0.145 * ph, silverTop, silverBot);
    const box = R(r.left + 0.515 * pw, r.top + 0.36 * ph, r.right - 0.043 * pw, r.top + 0.73 * ph); inset(box);
    if (altitude === null) metalText('---', rcx(box), rcy(box) + 0.07 * ph, 0.2 * ph, aLight, accent, 'center');
    else {
      const n = thousands(round(altitude)), sz = fit(`${n} m`, 0.2 * ph, rw(box) * 0.88);
      const wn = tw(`${n} `, sz), wm = tw('m', sz), sx = rcx(box) - (wn + wm) / 2, by = rcy(box) + sz * 0.36;
      metalText(n, sx, by, sz, aLight, accent); metalText('m', sx + wn, by, sz, silverTop, silverBot);
    }
  };

  /** Botón de salidas con el diseño del antiguo botón ZERO: punto para iniciar, cuadrado para finalizar. */
  const tripButton = (cx: number, cy: number, r: number) => {
    hits.tripX = cx; hits.tripY = cy; hits.tripR = r;
    img(a.zero, cx, cy, (2 * r) / 428, accentFilter(tripDown));               // el naranja toma el color elegido
    const ink = rgb(32, 22, 14), my = cy - r * 0.14, ar = r * 0.3;
    if (recording) drawRect(c, R(cx - ar * 0.8, my - ar * 0.8, cx + ar * 0.8, my + ar * 0.8), fill(ink));
    else { c.drawCircle(cx, my, ar * 1.05, fill(ink)); c.drawCircle(cx, my, ar * 0.8, fill(Chrome.REC)); }
    const label = recording ? 'FINALIZAR' : 'INICIAR';
    text(label, cx, cy + 0.62 * r, fit(label, 0.24 * r, 1.1 * r), ink);
  };

  // ---------- composición ----------
  ui = Math.max(Math.min(w, h * 0.5625) / 1440, 0.2);
  tileFill(R(0, 0, w, h), 0, 255);
  if (danger()) drawRect(c, R(0, 0, w, h), fill(IND_DANGER));                   // zona de peligro: fondo rojo
  const m0 = 0.015 * Math.min(w, h);
  if (w / d < 300 || h / d < 220) {                                             // minivista: solo los dos relojes
    if (w >= h * 1.2) { const gw = (w - 3 * m0) / 2; gauge(R(m0, m0, m0 + gw, h - m0), 'ALABEO', roll, true, true); gauge(R(2 * m0 + gw, m0, w - m0, h - m0), 'CABECEO', pitch, false, true); }
    else { const gh = (h - 3 * m0) / 2; gauge(R(m0, m0, w - m0, m0 + gh), 'ALABEO', roll, true, true); gauge(R(m0, 2 * m0 + gh, w - m0, h - m0), 'CABECEO', pitch, false, true); }
    if (recording) { const rr = 0.035 * Math.min(w, h); c.drawCircle(m0 + 2.2 * rr, m0 + 2.2 * rr, rr, fill(Chrome.REC)); }   // grabando
    return hits;
  }
  if (h > w * 0.9) {                                                            // vertical: proporciones del diseño de referencia
    const m = 0.0222 * w, hh = 0.0586 * h;
    header(hh);
    gauge(R(m, 0.0695 * h, w - m, 0.3398 * h), 'ALABEO', roll, true, false);
    gauge(R(m, 0.3535 * h, w - m, 0.623 * h), 'CABECEO', pitch, false, false);
    compassCard(R(m, 0.636 * h, 0.492 * w, 0.779 * h)); altCard(R(0.515 * w, 0.636 * h, w - m, 0.779 * h));
    const y4 = 0.793 * h, zh = 0.973 * h - y4;
    const zr = Math.min(zh / 2, 0.15 * w); if (zr > 20) tripButton(w / 2, y4 + zh / 2, zr);
    if (recording) {                                                            // a la izquierda del botón: tiempo de la salida en grande
      const tx = (w / 2 - zr) / 2, ty = y4 + zh / 2, sz = fit('00:00:00', 0.07 * h, w / 2 - zr - 2 * m);
      c.drawCircle(tx, ty - sz * 0.95, sz * 0.22, fill(Chrome.REC));
      metalText(clock(elapsed), tx, ty + sz * 0.35, sz, silverTop, silverBot, 'center');
      text('GRABANDO', tx, ty + sz * 0.95, sz * 0.32, rgb(200, 200, 205));
    }
  } else {                                                                      // horizontal
    const m = m0, hh = 0.1 * h, gh = 0.56 * h, gw = (w - 3 * m) / 2;
    header(hh);
    gauge(R(m, hh + m, m + gw, hh + m + gh), 'ALABEO', roll, true, false);
    gauge(R(2 * m + gw, hh + m, w - m, hh + m + gh), 'CABECEO', pitch, false, false);
    const y3 = hh + 2 * m + gh, ch = h - y3 - m, cw = 0.34 * w;
    compassCard(R(m, y3, m + cw, y3 + ch)); altCard(R(2 * m + cw, y3, 2 * m + 2 * cw, y3 + ch));
    const zx0 = 3 * m + 2 * cw, zr = Math.min(ch / 2, (w - m - zx0) / 2);
    if (zr > 20) tripButton((zx0 + w - m) / 2, y3 + ch / 2, zr);
  }
  return hits;
}

