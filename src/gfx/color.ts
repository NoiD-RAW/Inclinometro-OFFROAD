import { Skia, type SkColor } from '@shopify/react-native-skia';

/** Colores como enteros 0xAARRGGBB, igual que android.graphics.Color, para que el dibujo se lea igual que en Kotlin. */
export const rgb = (r: number, g: number, b: number) => argb(255, r, g, b);
export const argb = (a: number, r: number, g: number, b: number) => (((a & 255) << 24) | ((r & 255) << 16) | ((g & 255) << 8) | (b & 255)) >>> 0;
export const alpha = (c: number) => (c >>> 24) & 255;
export const red = (c: number) => (c >>> 16) & 255;
export const green = (c: number) => (c >>> 8) & 255;
export const blue = (c: number) => c & 255;
export const withAlpha = (c: number, a: number) => argb(a, red(c), green(c), blue(c));
export const WHITE = rgb(255, 255, 255);
export const BLACK = rgb(0, 0, 0);
export const GRAY = rgb(136, 136, 136);

/** Para estilos de React Native. */
export const css = (c: number) => `rgba(${red(c)},${green(c)},${blue(c)},${(alpha(c) / 255).toFixed(3)})`;

const cache = new Map<number, SkColor>();
/** Para Skia. */
export function sk(c: number): SkColor {
  let v = cache.get(c);
  if (!v) { v = Skia.Color(css(c)); if (cache.size > 512) cache.clear(); cache.set(c, v); }
  return v;
}

/** Color.colorToHSV de Android: [tono 0..360, saturación 0..1, valor 0..1]. */
export function toHsv(c: number): [number, number, number] {
  const r = red(c) / 255, g = green(c) / 255, b = blue(c) / 255;
  const max = Math.max(r, g, b), min = Math.min(r, g, b), d = max - min;
  let h = 0;
  if (d !== 0) {
    if (max === r) h = ((g - b) / d) % 6;
    else if (max === g) h = (b - r) / d + 2;
    else h = (r - g) / d + 4;
    h *= 60; if (h < 0) h += 360;
  }
  return [h, max === 0 ? 0 : d / max, max];
}

/** Color.HSVToColor de Android. */
export function fromHsv(h: number, s: number, v: number, a = 255): number {
  const c = v * s, x = c * (1 - Math.abs(((h / 60) % 2) - 1)), m = v - c;
  let r = 0, g = 0, b = 0;
  if (h < 60) [r, g, b] = [c, x, 0];
  else if (h < 120) [r, g, b] = [x, c, 0];
  else if (h < 180) [r, g, b] = [0, c, x];
  else if (h < 240) [r, g, b] = [0, x, c];
  else if (h < 300) [r, g, b] = [x, 0, c];
  else [r, g, b] = [c, 0, x];
  return argb(a, Math.round((r + m) * 255), Math.round((g + m) * 255), Math.round((b + m) * 255));
}

const clamp01 = (v: number) => Math.min(1, Math.max(0, v));
/** Mismo color con la saturación y el brillo multiplicados (como hsv()/shade() de los temas). */
export function shade(c: number, sk: number, vk: number) {
  const [h, s, v] = toHsv(c);
  return fromHsv(h, clamp01(s * sk), clamp01(v * vk));
}

/** "#3FD0B2" o "#3fd" → color, o null si no es válido. */
export function parseHex(s: string): number | null {
  let t = s.trim().replace(/^#/, '');
  if (t.length === 3) t = t.split('').map((ch) => ch + ch).join('');
  if (t.length !== 6 || !/^[0-9a-fA-F]{6}$/.test(t)) return null;
  return (0xff000000 | parseInt(t, 16)) >>> 0;
}
export const hex = (c: number) => '#' + (c & 0xffffff).toString(16).toUpperCase().padStart(6, '0');
