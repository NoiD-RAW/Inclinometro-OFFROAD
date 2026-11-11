import { Skia, type SkImage } from '@shopify/react-native-skia';
import { useEffect, useMemo, useState } from 'react';

import Core from '../../modules/inclinometro-core';
import type { Assets } from '../gfx/assets';
import { IndustrialTheme } from './industrial';
import { TopoTheme } from './topo';
import { KINDS, type Kind, type Theme, type Vehicle } from './types';

export const THEMES: Theme[] = [IndustrialTheme, TopoTheme];
export const byId = (id: string) => THEMES.find((t) => t.id === id) ?? THEMES[0];
/** Color principal elegido para el tema (cada tema guarda el suyo). */
export const accentOf = (t: Theme) => Core.getAccent(t.id, t.defaultAccent | 0) >>> 0;
export const setAccentOf = (t: Theme, c: number) => Core.setAccent(t.id, c | 0);

/** Imágenes del vehículo del tema: las que ha subido el usuario o, si no hay, las que trae la app.
 *  [version] cambia al subir o restablecer imágenes para volver a leerlas. */
export function useVehicle(theme: Theme, a: Assets, version: number): Vehicle {
  const [custom, setCustom] = useState<{ theme: string; img: Partial<Record<Kind, SkImage>> }>({ theme: theme.id, img: {} });
  useEffect(() => {
    let alive = true;
    (async () => {
      const out: Partial<Record<Kind, SkImage>> = {};
      for (const k of KINDS) {
        const uri = Core.vehicleUri(theme.id, k);
        if (!uri) continue;
        try {
          const data = await Skia.Data.fromURI(uri);
          const img = Skia.Image.MakeImageFromEncoded(data);
          if (img) out[k] = img;
        } catch { /* se usa la del tema */ }
      }
      if (alive) setCustom({ theme: theme.id, img: out });
    })();
    return () => { alive = false; };
  }, [theme.id, version]);
  return useMemo(() => {
    const c = custom.theme === theme.id ? custom.img : {};                        // sin restos del tema anterior mientras se cargan
    return { side: c.side ?? a.van.side, rear: c.rear ?? a.van.rear, top: c.top ?? a.van.top };
  }, [custom, a, theme.id]);
}
