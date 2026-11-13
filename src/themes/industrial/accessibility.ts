import type { DashState } from '../types';
import type { Hits } from './drawDash';

export type IndustrialGeometry = { width: number; height: number; density: number; hits: Hits };
export type NativeBounds = { left: number; top: number; width: number; height: number };
export type IndustrialControl = { id: 'settings' | 'history' | 'trip'; label: string; bounds: NativeBounds };

/** Se compara la geometría, no las lecturas: no se actualiza el layout con cada muestra del sensor. */
export function sameIndustrialGeometry(a: IndustrialGeometry | null, b: IndustrialGeometry) {
  if (!a || a.width !== b.width || a.height !== b.height || a.density !== b.density) return false;
  const ah = a.hits, bh = b.hits;
  return ah.menu.left === bh.menu.left && ah.menu.top === bh.menu.top && ah.menu.right === bh.menu.right && ah.menu.bottom === bh.menu.bottom &&
    ah.hist.left === bh.hist.left && ah.hist.top === bh.hist.top && ah.hist.right === bh.hist.right && ah.hist.bottom === bh.hist.bottom &&
    ah.tripX === bh.tripX && ah.tripY === bh.tripY && ah.tripR === bh.tripR;
}

/** Convierte las zonas reales del dibujo (px) a controles nativos (dp), sin repetir sus fórmulas. */
export function industrialAccessibility(geometry: IndustrialGeometry | null, recording: boolean, pip = false): {
  controls: IndustrialControl[]; readings: NativeBounds | null;
} {
  if (!geometry || !Number.isFinite(geometry.density) || geometry.density <= 0 || geometry.width <= 0 || geometry.height <= 0) {
    return { controls: [], readings: null };
  }
  const { width, height, density, hits } = geometry;
  const bounds = (left: number, top: number, right: number, bottom: number): NativeBounds | null => {
    if (![left, top, right, bottom].every(Number.isFinite)) return null;
    left = Math.max(0, left); top = Math.max(0, top);
    right = Math.min(width, right); bottom = Math.min(height, bottom);
    return right > left && bottom > top
      ? { left: left / density, top: top / density, width: (right - left) / density, height: (bottom - top) / density }
      : null;
  };
  const menu = bounds(hits.menu.left, hits.menu.top, hits.menu.right, hits.menu.bottom);
  const history = bounds(hits.hist.left, hits.hist.top, hits.hist.right, hits.hist.bottom);
  const trip = hits.tripR > 0 ? bounds(hits.tripX - hits.tripR, hits.tripY - hits.tripR, hits.tripX + hits.tripR, hits.tripY + hits.tripR) : null;
  const controls: IndustrialControl[] = [];
  if (!pip) {
    if (history) controls.push({ id: 'history', label: 'Histórico de salidas', bounds: history });
    if (menu) controls.push({ id: 'settings', label: 'Ajustes', bounds: menu });
    if (trip) controls.push({ id: 'trip', label: recording ? 'Finalizar salida' : 'Iniciar salida', bounds: trip });
  }
  const top = pip ? 0 : Math.max(menu ? hits.menu.bottom : 0, history ? hits.hist.bottom : 0);
  const bottom = !pip && trip ? hits.tripY - hits.tripR : height;
  return { controls, readings: bounds(0, top, width, bottom) };
}

/** Etiqueta consultable al enfocar el panel; no es una región de anuncios automáticos. */
export function industrialReadings(state: DashState) {
  const number = (value: number | null) => value !== null && Number.isFinite(value) ? String(Math.round(value)) : 'no disponible';
  const level = (value: number) => value === 2 ? 'peligro' : value === 1 ? 'precaución' : 'normal';
  const headings = ['norte', 'noreste', 'este', 'sureste', 'sur', 'suroeste', 'oeste', 'noroeste'];
  const heading = state.heading !== null && Number.isFinite(state.heading)
    ? `${headings[((Math.round(state.heading / 45) % 8) + 8) % 8]}, ${((Math.round(state.heading) % 360) + 360) % 360} grados`
    : 'no disponible';
  const seconds = Math.max(0, Math.floor(state.elapsed / 1000));
  const recording = state.recording ? `Grabando salida, ${Math.floor(seconds / 60)} minutos y ${seconds % 60} segundos.` : 'Sin salida en curso.';
  return `Cabeceo: ${number(state.pitch)} grados, ${level(state.pitchLevel)}. Alabeo: ${number(state.roll)} grados, ${level(state.rollLevel)}. ` +
    `Brújula: ${heading}. Altitud: ${state.altitude === null ? 'no disponible' : `${number(state.altitude)} metros`}. ` +
    `Velocidad: ${state.gps ? `${number(state.kmh)} kilómetros por hora` : 'GPS no disponible'}. ${recording}`;
}
