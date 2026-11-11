import { Skia, StrokeJoin, type SkCanvas } from '@shopify/react-native-skia';
import { useRef } from 'react';
import { PixelRatio, StyleSheet, type StyleProp, type ViewStyle } from 'react-native';

import type { Samples, Trip } from '../../modules/inclinometro-core';
import { useAssets } from '../gfx/assets';
import { WHITE, argb, blue, green, red, rgb } from '../gfx/color';
import { R, drawRect, drawText, fill, stroke, textWidth, type Align } from '../gfx/draw';
import { SkiaView } from '../gfx/SkiaView';
import { Fmt, clock } from '../fmt';
import type { Theme } from '../themes/types';

/** Gráfico de cabeceo y alabeo en el tiempo, con las líneas de límite y las zonas de peligro sombreadas.
 *  Al tocar o arrastrar marca un instante y avisa con [onCursor]. */
export function TripChart({ s, trip, theme, accent, cursor, onCursor, style }: {
  s: Samples; trip: Trip; theme: Theme; accent: number; cursor: number; onCursor: (i: number) => void; style?: StyleProp<ViewStyle>;
}) {
  const a = useAssets();
  const d = PixelRatio.get();
  const n = s.t.length;
  const t0 = n > 0 ? s.t[0] : 0;
  const span = n > 1 ? Math.max(1, s.t[n - 1] - t0) : 1;
  let m = Math.max(trip.limitPitch, trip.limitRoll);
  for (let i = 0; i < n; i++) m = Math.max(m, Math.abs(s.pitch[i]), Math.abs(s.roll[i]));
  const yMax = Math.max(10, Math.ceil((m * 1.15) / 10) * 10);
  const padL = 44 * d, padR = 10 * d, padT = 30 * d, padB = 24 * d;
  const widthRef = useRef(0);

  /** Instante más cercano a la coordenada x (búsqueda binaria por tiempo). */
  const indexAt = (px: number) => {
    const target = t0 + Math.min(1, Math.max(0, (px - padL) / (widthRef.current - padL - padR))) * span;
    let lo = 0, hi = n - 1;
    while (lo < hi) { const mid = (lo + hi) >> 1; if (s.t[mid] < target) lo = mid + 1; else hi = mid; }
    return lo > 0 && Math.abs(s.t[lo - 1] - target) < Math.abs(s.t[lo] - target) ? lo - 1 : lo;
  };
  const touch = (x: number) => { if (n < 2 || widthRef.current <= 0) return; const i = indexAt(x * d); if (i !== cursor) onCursor(i); };

  const draw = (c: SkCanvas, w: number, h: number) => {
    widthRef.current = w;
    const rollColor = rgb(235, 235, 238), grid = rgb(64, 64, 70), muted = theme.ui.muted;
    const x = (i: number) => padL + ((w - padL - padR) * (s.t[i] - t0)) / span;
    const y = (v: number) => padT + (h - padT - padB) * (0.5 - Math.min(yMax, Math.max(-yMax, v)) / (2 * yMax));
    const danger = (i: number) => Math.abs(s.pitch[i]) >= trip.limitPitch || Math.abs(s.roll[i]) >= trip.limitRoll;
    const text = (t: string, xx: number, yy: number, size: number, color: number, align: Align) => drawText(c, t, xx, yy, a.sans, size, fill(color), align);
    const top = padT, bot = h - padB, left = padL, right = w - padR;
    drawRect(c, R(left, top, right, bot), fill(rgb(14, 14, 16)));
    if (n < 2) { text('Sin datos suficientes', (left + right) / 2, (top + bot) / 2, 14 * d, muted, 'center'); return; }

    // zonas de peligro, sombreadas en todo el alto
    const dz = fill(argb(110, red(theme.danger), green(theme.danger), blue(theme.danger)));
    for (let i = 0; i < n;) {
      if (!danger(i)) { i++; continue; }
      const a0 = i; while (i < n && danger(i)) i++;
      drawRect(c, R(x(a0), top, Math.max(x(Math.min(i, n - 1)), x(a0) + 2 * d), bot), dz);
    }
    // rejilla cada 10°
    for (let g = -yMax; g <= yMax + 0.1; g += 10) {
      c.drawLine(left, y(g), right, y(g), stroke(g === 0 ? rgb(130, 130, 136) : grid, 1 * d));
      text(g > 0 ? `+${g}°` : `${g}°`, left - 6 * d, y(g) + 4 * d, 11 * d, muted, 'right');
    }
    // líneas de límite (discontinuas, del color de cada serie)
    const dashed = (color: number) => { const p = stroke(color, 1.5 * d); p.setPathEffect(Skia.PathEffect.MakeDash([8 * d, 6 * d], 0)); return p; };
    const pl = dashed(accent), rl = dashed(rollColor);
    c.drawLine(left, y(trip.limitPitch), right, y(trip.limitPitch), pl); c.drawLine(left, y(-trip.limitPitch), right, y(-trip.limitPitch), pl);
    c.drawLine(left, y(trip.limitRoll), right, y(trip.limitRoll), rl); c.drawLine(left, y(-trip.limitRoll), right, y(-trip.limitRoll), rl);
    // series: por cada columna de píxeles, mínimo y máximo, así no se pierden los picos en salidas largas
    const series = (v: number[], color: number) => {
      const b = Skia.PathBuilder.Make(); const cols = Math.max(1, Math.trunc(w - padL - padR)); let first = true;
      const per = Math.max(1, Math.trunc(n / cols));
      for (let i = 0; i < n;) {
        let lo = v[i], hi = v[i]; const end = Math.min(n, i + per);
        for (let j = i; j < end; j++) { lo = Math.min(lo, v[j]); hi = Math.max(hi, v[j]); }
        const xx = x(i);
        if (first) { b.moveTo(xx, y(v[i])); first = false; }
        if (per === 1) b.lineTo(xx, y(v[i])); else { b.lineTo(xx, y(hi)); b.lineTo(xx, y(lo)); }
        i = end;
      }
      const p = stroke(color, 2 * d); p.setStrokeJoin(StrokeJoin.Round); c.drawPath(b.build(), p);
    };
    series(s.pitch, accent); series(s.roll, rollColor);
    // eje de tiempo y leyenda
    text(Fmt.time(s.t[0]).substring(0, 5), left, h - 6 * d, 11 * d, muted, 'left');
    text(Fmt.time(s.t[n - 1]).substring(0, 5), right, h - 6 * d, 11 * d, muted, 'right');
    text(clock(span), (left + right) / 2, h - 6 * d, 11 * d, muted, 'center');
    // leyenda
    let lx = left; const yy = 18 * d, sz = 12 * d;
    for (const [label, col] of [['Cabeceo', accent], ['Alabeo', rollColor]] as const) {
      c.drawLine(lx, yy - 4 * d, lx + 18 * d, yy - 4 * d, stroke(col, 3 * d));
      text(label, lx + 24 * d, yy, sz, theme.ui.text, 'left'); lx += 24 * d + textWidth(a.sans, sz, label) + 18 * d;
    }
    drawRect(c, R(lx, yy - 11 * d, lx + 18 * d, yy + 1 * d), fill(argb(160, red(theme.danger), green(theme.danger), blue(theme.danger))));
    text('Peligro', lx + 24 * d, yy, sz, theme.ui.text, 'left'); lx += 24 * d + textWidth(a.sans, sz, 'Peligro') + 18 * d;
    const lp = stroke(muted, 1.5 * d); lp.setPathEffect(Skia.PathEffect.MakeDash([5 * d, 4 * d], 0));
    c.drawLine(lx, yy - 4 * d, lx + 18 * d, yy - 4 * d, lp); text('Límite', lx + 24 * d, yy, sz, theme.ui.text, 'left');
    // instante seleccionado
    if (cursor >= 0 && cursor < n) {
      const cx = x(cursor); c.drawLine(cx, top, cx, bot, stroke(WHITE, 2 * d));
      c.drawCircle(cx, y(s.pitch[cursor]), 5 * d, fill(accent)); c.drawCircle(cx, y(s.roll[cursor]), 5 * d, fill(rollColor));
    }
  };

  return (
    <SkiaView style={[styles.chart, style]} deps={[s, trip, theme, accent, cursor, a]} draw={draw}
      onStartShouldSetResponder={() => n >= 2} onMoveShouldSetResponder={() => n >= 2}
      onResponderTerminationRequest={() => false}
      onResponderGrant={(e) => touch(e.nativeEvent.locationX)} onResponderMove={(e) => touch(e.nativeEvent.locationX)} />
  );
}

const styles = StyleSheet.create({ chart: { overflow: 'hidden' } });
