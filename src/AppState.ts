import { createContext, useContext, useEffect, useRef, useState } from 'react';

import Core, { type EngineState } from '../modules/inclinometro-core';
import type { Theme, ThemeUi, Vehicle } from './themes/types';

/** Pantallas que se abren encima de la principal (en Android eran actividades aparte). */
export type Route = { name: 'history' } | { name: 'detail'; id: number } | { name: 'hotspots' };

export type App = {
  theme: Theme;
  ui: ThemeUi;
  accent: number;
  vehicle: Vehicle;
  /** Cambia cada vez que se suben o restablecen imágenes del vehículo. */
  vehicleVersion: number;
  /** Cambia de tema al momento: la calibración y el resto de datos no dependen de la pantalla, así que no se pierde nada. */
  applyTheme: (id: string) => void;
  setAccent: (c: number) => void;
  /** Vuelve a leer las imágenes del vehículo (tras subirlas o restablecerlas). */
  refreshVehicle: () => void;
  /** Vuelve a pintar Ajustes con los valores guardados (equivale a rebuildSettings). */
  rebuild: () => void;
  showDash: () => void;
  showSettings: () => void;
  push: (r: Route) => void;
  pop: () => void;
  enterPip: (silent: boolean) => void;
};

export const AppCtx = createContext<App | null>(null);
export const useApp = () => { const a = useContext(AppCtx); if (!a) throw new Error('Sin app'); return a; };

/** Lectura de sensores en cada fotograma, solo mientras [active]. */
export function useEngine(active: boolean): EngineState {
  const [s, setS] = useState<EngineState>(() => Core.getState());
  const last = useRef('');
  useEffect(() => {
    if (!active) return;
    let id = 0;
    const tick = () => {
      const n = Core.getState();
      // Solo se vuelve a pintar si algo ha cambiado (incluido el segundo del cronómetro de la salida)
      const key = `${n.roll.toFixed(2)}|${n.pitch.toFixed(2)}|${n.heading?.toFixed(1)}|${n.altitude?.toFixed(0)}|${n.kmh.toFixed(1)}|${n.gps}|${n.pitchLevel}|${n.rollLevel}|${n.max.join()}|${n.recording}|${n.recording ? Math.floor((Date.now() - n.recordStart) / 1000) : 0}`;
      if (key !== last.current) { last.current = key; setS(n); }
      id = requestAnimationFrame(tick);
    };
    id = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(id);
  }, [active]);
  return s;
}
