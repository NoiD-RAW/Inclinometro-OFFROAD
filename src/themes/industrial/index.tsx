import { LinearGradient } from 'expo-linear-gradient';
import { useRef, useState } from 'react';
import { PixelRatio, Pressable, StyleSheet, Text, type GestureResponderEvent, type StyleProp, type ViewStyle } from 'react-native';

import { useAssets } from '../../gfx/assets';
import { BLACK, css, rgb, shade } from '../../gfx/color';
import { R, contains, drawImage, drawRect, fill, isEmpty, linear, bitmapPaint, tileShader, type RectF } from '../../gfx/draw';
import { SkiaView } from '../../gfx/SkiaView';
import { KINDS, type DashProps, type Theme, type ThemeUi } from '../types';
import { IND_CAUTION, IND_DANGER, drawIndustrial, type Hits } from './drawDash';

/** Pantalla principal: todo dibujado en un lienzo, con la rueda de Ajustes, el historial y el botón de salidas como zonas táctiles. */
function IndustrialDash({ state, accent, vehicle, actions }: DashProps) {
  const a = useAssets();
  const [tripDown, setTripDown] = useState(false);
  const hits = useRef<Hits | null>(null);
  const d = PixelRatio.get();

  const where = (e: GestureResponderEvent) => {
    const x = e.nativeEvent.locationX * d, y = e.nativeEvent.locationY * d, h = hits.current;
    if (!h) return { menu: false, hist: false, trip: false };
    return {
      menu: !isEmpty(h.menu) && contains(h.menu, x, y),
      hist: !isEmpty(h.hist) && contains(h.hist, x, y),
      trip: h.tripR > 0 && Math.hypot(x - h.tripX, y - h.tripY) <= h.tripR,
    };
  };
  return (
    <SkiaView
      style={StyleSheet.absoluteFill}
      deps={[state, accent, vehicle, a, tripDown]}
      draw={(c, w, h, dd) => { hits.current = drawIndustrial(c, w, h, dd, state, accent, vehicle, a, tripDown); }}
      onStartShouldSetResponder={(e) => { const t = where(e); if (t.trip) setTripDown(true); return t.menu || t.hist || t.trip; }}
      onResponderRelease={(e) => {
        setTripDown(false);
        const t = where(e);
        if (t.menu) actions.onSettings(); else if (t.hist) actions.onHistory(); else if (t.trip) actions.onTrip();
      }}
      onResponderTerminate={() => setTripDown(false)}
      accessible
      accessibilityLabel="Inclinómetro. Ajustes, historial y botón de salidas"
    />
  );
}

// ---------- piezas de interfaz para Ajustes e histórico ----------
const background = rgb(16, 16, 18), text = rgb(235, 235, 238), muted = rgb(165, 165, 171);

/** Fondo de acero cepillado (la textura a su tamaño real, repetida). */
function Background({ style }: { style?: StyleProp<ViewStyle> }) {
  const a = useAssets();
  return <SkiaView style={[StyleSheet.absoluteFill, style]} deps={[a]} draw={(c, w, h) => drawRect(c, R(0, 0, w, h), fill(BLACK, tileShader(a.bg, 1)))} />;
}

/** Panel metálico (marco, pantalla hundida y tornillos) con los mismos recursos que la pantalla principal. */
function drawPanel(c: Parameters<Parameters<typeof SkiaView>[0]['draw']>[0], r: RectF, d: number, a: ReturnType<typeof useAssets>) {
  const ui = Math.min(1.3, Math.max(0.4, d * 0.45)), t = 14 * ui, s = 44 * ui;
  const dd = R(r.left + t, r.top + t, r.right - t, r.bottom - t);
  drawRect(c, dd, fill(BLACK, linear(0, dd.top, 0, dd.bottom, [rgb(27, 27, 30), rgb(14, 14, 16)])));
  const tile = fill(BLACK, tileShader(a.bg, ui)); tile.setAlphaf(60 / 255); drawRect(c, dd, tile);
  const bp = bitmapPaint(), e = a.edges;
  drawImage(c, e[4], R(r.left + s, r.top, r.right - s, r.top + t), bp);
  drawImage(c, e[5], R(r.left + s, r.bottom - t, r.right - s, r.bottom), bp);
  drawImage(c, e[6], R(r.left, r.top + s, r.left + t, r.bottom - s), bp);
  drawImage(c, e[7], R(r.right - t, r.top + s, r.right, r.bottom - s), bp);
  drawImage(c, e[0], R(r.left, r.top, r.left + s, r.top + s), bp);
  drawImage(c, e[1], R(r.right - s, r.top, r.right, r.top + s), bp);
  drawImage(c, e[2], R(r.left, r.bottom - s, r.left + s, r.bottom), bp);
  drawImage(c, e[3], R(r.right - s, r.bottom - s, r.right, r.bottom), bp);
  const ix = 37 * ui, iy = 35 * ui, sr = 20 * ui;
  for (const fx of [0, 1]) for (const fy of [0, 1]) {
    const cx = fx === 0 ? r.left + ix : r.right - ix, cy = fy === 0 ? r.top + iy : r.bottom - iy;
    drawImage(c, a.screw, R(cx - sr, cy - sr, cx + sr, cy + sr), bp);
  }
}

const font = { fontFamily: 'gunplay' };

const IndustrialUi: ThemeUi = {
  background, text, muted,
  Background,
  Label: ({ text: t, size, color, style, lines }) => (
    <Text numberOfLines={lines} style={[font, { fontSize: size, color: css(color), paddingTop: 2, paddingBottom: 8 }, style]}>{t.toUpperCase()}</Text>
  ),
  Note: ({ text: t, color, style }) => (
    <Text style={[font, { fontSize: 11, color: css(color ?? muted), paddingTop: 6, paddingBottom: 4 }, style]}>{t.toUpperCase()}</Text>
  ),
  Button: ({ text: t, filled, accent, onPress, style, textSize, label }) => (
    <Pressable onPress={onPress} accessibilityRole="button" accessibilityLabel={label ?? t} style={[styles.btn, style]}>
      <LinearGradient
        colors={filled ? [css(shade(accent, 0.62, 1.1)), css(shade(accent, 1, 0.7))] : [css(rgb(66, 66, 71)), css(rgb(30, 30, 33))]}
        style={[styles.btnFill, { borderColor: css(filled ? rgb(8, 8, 10) : rgb(96, 96, 102)) }]}>
        <Text style={[font, styles.btnText, { color: css(filled ? rgb(30, 20, 10) : rgb(235, 235, 238)), fontSize: textSize ?? 14 }]}>{t.toUpperCase()}</Text>
      </LinearGradient>
    </Pressable>
  ),
  Section: ({ title, accent, children, style }) => {
    const a = useAssets();
    return (
      <SkiaView style={[styles.section, style]} deps={[a]} draw={(c, w, h, d) => drawPanel(c, R(0, 0, w, h), d, a)}>
        <IndustrialUi.Label text={title} size={15} color={accent} />
        {children}
      </SkiaView>
    );
  },
};

const styles = StyleSheet.create({
  btn: { minHeight: 48 },
  btnFill: { flex: 1, borderRadius: 12, borderWidth: 2, alignItems: 'center', justifyContent: 'center', paddingHorizontal: 12, paddingVertical: 4 },
  btnText: { textAlign: 'center' },
  section: { paddingHorizontal: 26, paddingTop: 26, paddingBottom: 24 },
});

/** Tema "Industrial": acero cepillado, tornillos y biseles (antes, la app Inclinómetro Offroad de cuatro relojes). */
export const IndustrialTheme: Theme = {
  id: 'industrial',
  name: 'Industrial',
  defaultAccent: rgb(255, 122, 26),
  accentPresets: [rgb(255, 122, 26), rgb(230, 57, 53), rgb(255, 196, 30), rgb(76, 200, 80), rgb(0, 200, 220), rgb(45, 130, 255), rgb(150, 90, 255), rgb(255, 80, 160)],
  pipAspect: [16, 9],
  vehicleKinds: KINDS.filter((k) => k !== 'top'),
  caution: IND_CAUTION,
  danger: IND_DANGER,
  ui: IndustrialUi,
  Dash: IndustrialDash,
};

