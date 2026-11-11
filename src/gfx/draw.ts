import {
  FilterMode, MipmapMode, PaintStyle, Skia, StrokeCap, StrokeJoin, TileMode,
  type SkCanvas, type SkColorFilter, type SkFont, type SkImage, type SkPaint, type SkRect, type SkShader, type SkTypeface,
} from '@shopify/react-native-skia';

import { sk } from './color';

/** Ayudantes con la misma forma que android.graphics.Canvas/Paint, para que el dibujo se pueda copiar de Kotlin casi línea a línea.
 *  Todo se dibuja en píxeles físicos (el lienzo se escala por 1/densidad), como en Android. */

export type RectF = { left: number; top: number; right: number; bottom: number };
export const R = (left: number, top: number, right: number, bottom: number): RectF => ({ left, top, right, bottom });
export const rw = (r: RectF) => r.right - r.left;
export const rh = (r: RectF) => r.bottom - r.top;
export const rcx = (r: RectF) => (r.left + r.right) / 2;
export const rcy = (r: RectF) => (r.top + r.bottom) / 2;
export const skr = (r: RectF): SkRect => Skia.XYWHRect(r.left, r.top, r.right - r.left, r.bottom - r.top);
export const contains = (r: RectF, x: number, y: number) => x >= r.left && x < r.right && y >= r.top && y < r.bottom;
export const isEmpty = (r: RectF) => r.left >= r.right || r.top >= r.bottom;
export const EMPTY: RectF = R(0, 0, 0, 0);

export function fill(color: number, shader?: SkShader | null): SkPaint {
  const p = Skia.Paint(); p.setAntiAlias(true); p.setStyle(PaintStyle.Fill); p.setColor(sk(color));
  if (shader) p.setShader(shader);
  return p;
}
export function stroke(color: number, width: number, cap: 'butt' | 'round' = 'butt', shader?: SkShader | null): SkPaint {
  const p = Skia.Paint(); p.setAntiAlias(true); p.setStyle(PaintStyle.Stroke); p.setColor(sk(color)); p.setStrokeWidth(width);
  p.setStrokeCap(cap === 'round' ? StrokeCap.Round : StrokeCap.Butt); p.setStrokeJoin(StrokeJoin.Miter);
  if (shader) p.setShader(shader);
  return p;
}
/** Pincel para bitmaps con filtrado (Paint.FILTER_BITMAP_FLAG). */
export function bitmapPaint(filter?: SkColorFilter | null, alpha = 255): SkPaint {
  const p = Skia.Paint(); p.setAntiAlias(true); if (filter) p.setColorFilter(filter); if (alpha < 255) p.setAlphaf(alpha / 255);
  return p;
}

export function linear(x0: number, y0: number, x1: number, y1: number, colors: number[], pos: number[] | null = null): SkShader {
  return Skia.Shader.MakeLinearGradient({ x: x0, y: y0 }, { x: x1, y: y1 }, colors.map(sk), pos, TileMode.Clamp);
}

export function drawRect(c: SkCanvas, r: RectF, p: SkPaint) { c.drawRect(skr(r), p); }
export function drawRoundRect(c: SkCanvas, r: RectF, rx: number, ry: number, p: SkPaint) { c.drawRRect(Skia.RRectXY(skr(r), rx, ry), p); }
export function drawArc(c: SkCanvas, oval: RectF, start: number, sweep: number, p: SkPaint) { c.drawArc(skr(oval), start, sweep, false, p); }

/** Bitmap estirado a un rectángulo (drawBitmap(b, null, rect, p)). */
export function drawImage(c: SkCanvas, img: SkImage, dst: RectF, p: SkPaint) {
  c.drawImageRectOptions(img, Skia.XYWHRect(0, 0, img.width(), img.height()), skr(dst), FilterMode.Linear, MipmapMode.Linear, p);
}

/** Relleno con una textura repetida (BitmapShader REPEAT escalado). */
const tiles = new Map<string, SkShader>();
export function tileShader(img: SkImage, scale: number): SkShader {
  const key = `${img.width()}x${img.height()}:${scale.toFixed(4)}`;
  const hit = tiles.get(key); if (hit) return hit;
  const sh = makeTile(img, scale); if (tiles.size > 32) tiles.clear(); tiles.set(key, sh); return sh;
}
function makeTile(img: SkImage, scale: number): SkShader {
  const m = Skia.Matrix(); m.scale(scale, scale);
  return img.makeShaderOptions(TileMode.Repeat, TileMode.Repeat, FilterMode.Nearest, MipmapMode.None, m);   // como un Paint sin FILTER_BITMAP
}

// ---------- texto ----------
const fonts = new Map<string, SkFont>();
/** [bold]: negrita sintética, como hace Android cuando la fuente no tiene versión en negrita (la monoespaciada). */
export function font(face: SkTypeface, size: number, bold = false): SkFont {
  const key = `${faceKey(face)}:${size.toFixed(2)}:${bold}`;
  let f = fonts.get(key);
  if (!f) { f = Skia.Font(face, size); f.setSubpixel(true); if (bold) f.setEmbolden(true); if (fonts.size > 400) fonts.clear(); fonts.set(key, f); }
  return f;
}
const faceIds = new WeakMap<SkTypeface, number>(); let faceSeq = 0;
function faceKey(face: SkTypeface) { let id = faceIds.get(face); if (id === undefined) { id = ++faceSeq; faceIds.set(face, id); } return id; }

export type Align = 'left' | 'center' | 'right';
/** Avance del texto (lo que mide Paint.measureText en Android), espacios incluidos. */
export function textWidth(face: SkTypeface, size: number, s: string) {
  const f = font(face, size);
  return f.getGlyphWidths(f.getGlyphIDs(s)).reduce((a, b) => a + b, 0);
}
/** drawText con Paint.Align: [x] es el borde izquierdo, el centro o el borde derecho del texto. */
export function drawText(c: SkCanvas, s: string, x: number, y: number, face: SkTypeface, size: number, p: SkPaint, align: Align = 'left', bold = false) {
  const w = align === 'left' ? 0 : textWidth(face, size, s);
  const f = font(face, size, bold);
  c.drawText(s, align === 'center' ? x - w / 2 : align === 'right' ? x - w : x, y, p, f);
}

// ---------- caminos ----------
export function path(build: (b: ReturnType<typeof Skia.PathBuilder.Make>) => void) {
  const b = Skia.PathBuilder.Make(); build(b); return b.build();
}

/** Rotación en grados alrededor de (px, py), como Canvas.rotate(deg, px, py). */
export function rotate(c: SkCanvas, deg: number, px = 0, py = 0) { c.rotate(deg, px, py); }
