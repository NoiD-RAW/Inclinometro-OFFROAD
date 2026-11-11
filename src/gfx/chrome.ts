import { FillType, type SkCanvas } from '@shopify/react-native-skia';

import { rgb } from './color';
import { R, drawArc, fill, path, stroke } from './draw';

/** Controles comunes de la pantalla principal (iconos de ajustes e historial), para que se vean y respondan igual en todos los temas. */
export const Chrome = {
  /** Área táctil mínima recomendada: 48 dp. */
  minTouch: (density: number) => 48 * density,

  /** Rueda dentada de 8 dientes con agujero central, de radio exterior [r]. */
  drawGear(c: SkCanvas, cx: number, cy: number, r: number, color: number) {
    const teeth = 8, ri = r * 0.74;
    const p = path((b) => {
      b.setFillType(FillType.EvenOdd);
      for (let i = 0; i < teeth * 4; i++) {                          // cada diente: base, punta, punta, base
        const a = (2 * Math.PI * (i - 1.5)) / (teeth * 4);
        const rr = i % 4 === 1 || i % 4 === 2 ? r : ri;
        const x = cx + rr * Math.sin(a), y = cy - rr * Math.cos(a);
        if (i === 0) b.moveTo(x, y); else b.lineTo(x, y);
      }
      b.close(); b.addCircle(cx, cy, r * 0.32);
    });
    c.drawPath(p, fill(color));
  },

  /** Historial: reloj con una flecha que gira hacia atrás. */
  drawHistory(c: SkCanvas, cx: number, cy: number, r: number, color: number) {
    const sw = r * 0.17, rr = r * 0.8;
    const s = stroke(color, sw, 'round');
    drawArc(c, R(cx - rr, cy - rr, cx + rr, cy + rr), 215, 290, s);                    // esfera abierta por arriba a la izquierda
    c.drawLine(cx, cy, cx, cy - rr * 0.55, s); c.drawLine(cx, cy, cx + rr * 0.4, cy + rr * 0.25, s);   // agujas
    const a = (215 * Math.PI) / 180, ax = cx + rr * Math.cos(a), ay = cy + rr * Math.sin(a);
    c.drawPath(path((b) => { b.moveTo(ax - r * 0.42, ay - r * 0.05); b.lineTo(ax + r * 0.12, ay - r * 0.12); b.lineTo(ax - r * 0.1, ay + r * 0.42); b.close(); }), fill(color));
  },

  /** Rojo del punto de grabación. */
  REC: rgb(255, 59, 48),
};
