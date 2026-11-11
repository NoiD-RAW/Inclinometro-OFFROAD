import { FontWeight, Skia, useImage, useTypeface, type SkImage, type SkTypeface } from '@shopify/react-native-skia';
import { createContext, useContext, useMemo, type ReactNode } from 'react';
import { Platform } from 'react-native';

/** Recursos gráficos extraídos del diseño original (res/drawable-nodpi de la app Android) y tipografías. */
export type Assets = {
  bg: SkImage; screw: SkImage; pill: SkImage; zero: SkImage; compass: SkImage; badge: SkImage;
  /** Esquinas y bordes del panel metálico: tl, tr, bl, br, top, bottom, left, right. */
  edges: SkImage[];
  van: { side: SkImage; rear: SkImage; top: SkImage };
  gunplay: SkTypeface;
  /** Equivalentes de Typeface.DEFAULT, DEFAULT_BOLD, MONOSPACE en negrita y sans-serif-condensed en negrita. */
  sans: SkTypeface; sansBold: SkTypeface; monoBold: SkTypeface; condBold: SkTypeface;
  /** La monoespaciada del sistema no tiene negrita propia: hay que engrosarla. */
  monoFake: boolean;
};

const Ctx = createContext<Assets | null>(null);
export const useAssets = () => { const a = useContext(Ctx); if (!a) throw new Error('Recursos sin cargar'); return a; };

function systemFace(names: string[], weight: FontWeight, condensed = false): SkTypeface {
  const mgr = Skia.FontMgr.System();
  for (const n of names) {
    try {
      const t = mgr.matchFamilyStyle(n, { weight, width: condensed ? 3 : 5 });
      if (t) return t;
    } catch { /* siguiente */ }
  }
  return mgr.matchFamilyStyle(names[names.length - 1], { weight });
}

const ios = Platform.OS === 'ios';

/** Carga todo antes de mostrar la app (es muy poco y viene dentro de la app). */
export function AssetsProvider({ children }: { children: ReactNode }) {
  const imgs = [
    useImage(require('../../assets/img/bg_metal.png')), useImage(require('../../assets/img/screw.png')),
    useImage(require('../../assets/img/pill.png')), useImage(require('../../assets/img/btn_zero.png')),
    useImage(require('../../assets/img/compass_face.png')), useImage(require('../../assets/img/alt_badge.png')),
    useImage(require('../../assets/img/panel_tl.png')), useImage(require('../../assets/img/panel_tr.png')),
    useImage(require('../../assets/img/panel_bl.png')), useImage(require('../../assets/img/panel_br.png')),
    useImage(require('../../assets/img/panel_top.png')), useImage(require('../../assets/img/panel_bottom.png')),
    useImage(require('../../assets/img/panel_left.png')), useImage(require('../../assets/img/panel_right.png')),
    useImage(require('../../assets/img/van_side.png')), useImage(require('../../assets/img/van_rear.png')),
    useImage(require('../../assets/img/van_top.png')),
  ];
  const gunplay = useTypeface(require('../../assets/fonts/gunplay.otf'));
  const ready = imgs.every(Boolean) && gunplay;
  const value = useMemo<Assets | null>(() => {
    if (!ready) return null;
    const i = imgs as SkImage[];
    return {
      bg: i[0], screw: i[1], pill: i[2], zero: i[3], compass: i[4], badge: i[5],
      edges: i.slice(6, 14), van: { side: i[14], rear: i[15], top: i[16] }, gunplay: gunplay!,
      sans: systemFace(ios ? ['Helvetica Neue', 'Helvetica'] : ['sans-serif', 'Roboto'], FontWeight.Normal),
      sansBold: systemFace(ios ? ['Helvetica Neue', 'Helvetica'] : ['sans-serif', 'Roboto'], FontWeight.Bold),
      monoBold: systemFace(ios ? ['Menlo', 'Courier New', 'Courier'] : ['monospace', 'Droid Sans Mono'], FontWeight.Bold),
      monoFake: !ios,
      condBold: systemFace(ios ? ['Helvetica Neue', 'Helvetica'] : ['sans-serif-condensed', 'sans-serif'], FontWeight.Bold, true),
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [ready]);
  if (!value) return null;
  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}
