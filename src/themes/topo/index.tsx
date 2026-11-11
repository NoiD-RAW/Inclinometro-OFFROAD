import { useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';

import { useAssets } from '../../gfx/assets';
import { Chrome } from '../../gfx/chrome';
import { argb, css, rgb } from '../../gfx/color';
import { SkiaView } from '../../gfx/SkiaView';
import { clock } from '../../fmt';
import { DANGER, KINDS, level, type DashProps, type Theme, type ThemeUi } from '../types';
import { drawCompass, drawGauge } from './draw';

const CAUTION_C = rgb(255, 170, 40), DANGER_C = rgb(160, 22, 22);
const background = rgb(16, 16, 18), text = rgb(235, 235, 238), muted = rgb(165, 170, 176);

/** Botón de icono (rueda dentada…). Nunca más pequeño que 48×48 dp; al pulsarlo encoge un poco. */
function IconButton({ kind, accent, label, onPress }: { kind: 'gear' | 'history'; accent: number; label: string; onPress: () => void }) {
  const [down, setDown] = useState(false);
  return (
    <Pressable onPress={onPress} onPressIn={() => setDown(true)} onPressOut={() => setDown(false)} accessibilityRole="button" accessibilityLabel={label} style={styles.icon}>
      <SkiaView style={StyleSheet.absoluteFill} deps={[kind, down, accent]} draw={(c, w, h) => {
        const r = 0.36 * Math.min(w, h) * (down ? 0.9 : 1);
        if (kind === 'gear') Chrome.drawGear(c, w / 2, h / 2, r, accent); else Chrome.drawHistory(c, w / 2, h / 2, r, accent);
      }} />
    </Pressable>
  );
}

/** Pantalla principal: barra superior con la rueda de Ajustes, cabeceo, brújula y alabeo en fila o en columna (según el espacio) y la fila de botones debajo. */
function TopoDash({ state: s, accent, vehicle, actions, pip }: DashProps) {
  const a = useAssets();
  const [horizontal, setHorizontal] = useState(true);
  // la brújula conserva el último rumbo conocido
  const [heading, setHeading] = useState<number | null>(null);
  if (s.heading !== null && s.heading !== heading) setHeading(s.heading);
  const moving = s.gps && s.kmh >= 4;
  const bg = level(s) === DANGER ? DANGER_C : background;                         // zona de peligro: fondo rojo
  const rec = s.recording;
  const gauge = (title: string, rear: boolean) => (
    <SkiaView style={horizontal ? styles.cellH : styles.cellV} deps={[rear ? s.roll : s.pitch, rear ? s.rollLevel : s.pitchLevel, s.max, accent, vehicle, a]}
      draw={(c, w, h) => drawGauge(c, w, h, {
        title, rear, angle: rear ? s.roll : s.pitch, accent, vehicle: rear ? vehicle.rear : vehicle.side,
        level: rear ? s.rollLevel : s.pitchLevel, caution: CAUTION_C,
        maxLo: rear ? s.max[2] : s.max[1], maxHi: rear ? s.max[3] : s.max[0],
      }, a)} />
  );
  return (
    <View style={[StyleSheet.absoluteFill, { backgroundColor: css(bg) }]}>
      {!pip && (
        <View style={styles.topBar}>
          {rec && (
            <Text style={styles.rec}><Text style={{ color: css(Chrome.REC) }}>●</Text>{'  ' + clock(s.elapsed)}</Text>
          )}
          <View style={{ flex: 1 }} />
          <IconButton kind="history" accent={accent} label="Historial de salidas" onPress={actions.onHistory} />
          <IconButton kind="gear" accent={accent} label="Ajustes" onPress={actions.onSettings} />
        </View>
      )}
      <View style={{ flex: 1, flexDirection: horizontal ? 'row' : 'column' }} onLayout={(e) => {   // en pantalla dividida elige fila o columna
        const { width: w, height: h } = e.nativeEvent.layout;
        if (w > 0 && h > 0) {
          const hz = Math.min(w / 3 / 190, h / 214) >= Math.min(w / 190, h / 3 / 214);
          if (hz !== horizontal) setHorizontal(hz);
        }
      }}>
        {gauge('Cabeceo', false)}
        <SkiaView style={horizontal ? styles.cellH : styles.cellV} deps={[heading, s.kmh, moving, s.altitude, accent, vehicle, a]}
          draw={(c, w, h) => drawCompass(c, w, h, {
            heading: heading ?? 0, hasHeading: heading !== null, kmh: s.kmh, moving, altitude: s.altitude, accent, vehicle: vehicle.top,
          }, a)} />
        {gauge('Alabeo', true)}
      </View>
      {!pip && (
        <View style={styles.buttonRow}>
          {/* Botón grande de salidas (la calibración se hace desde Ajustes, para no tocarla en marcha) */}
          <TopoUi.Button text={rec ? '■  Finalizar salida' : '●  Iniciar salida'} filled={!rec} accent={accent} onPress={actions.onTrip} textSize={18} style={styles.tripBtn} />
        </View>
      )}
    </View>
  );
}

const TopoUi: ThemeUi = {
  background, text, muted,
  Background: ({ style }) => <View style={[StyleSheet.absoluteFill, { backgroundColor: css(background) }, style]} />,
  Label: ({ text: t, size, color, style, lines }) => (
    <Text numberOfLines={lines} style={[{ fontSize: size, color: css(color), fontWeight: 'bold', paddingTop: 16, paddingBottom: 4 }, style]}>{t}</Text>
  ),
  Note: ({ text: t, color, style }) => <Text style={[{ fontSize: 13, color: css(color ?? muted), paddingBottom: 8 }, style]}>{t}</Text>,
  Button: ({ text: t, filled, accent, onPress, style, textSize, label }) => (
    <Pressable onPress={onPress} accessibilityRole="button" accessibilityLabel={label ?? t}
      style={[styles.btn, filled ? { backgroundColor: css(accent) } : { backgroundColor: css(rgb(34, 36, 40)), borderWidth: 2, borderColor: css(accent) }, style]}>
      <Text style={{ fontWeight: 'bold', textAlign: 'center', fontSize: textSize ?? 14, color: css(filled ? rgb(14, 22, 24) : rgb(235, 235, 238)) }}>{t}</Text>
    </Pressable>
  ),
  Section: ({ title, accent, children, style }) => (
    <View style={style}>
      <TopoUi.Label text={title.toUpperCase()} size={14} color={accent} />
      {children}
    </View>
  ),
};

const styles = StyleSheet.create({
  topBar: { flexDirection: 'row', alignItems: 'center' },
  rec: { marginLeft: 10, fontSize: 20, fontWeight: 'bold', color: '#fff', paddingLeft: 14, paddingRight: 16, paddingVertical: 6, borderRadius: 20, overflow: 'hidden', backgroundColor: css(argb(230, 16, 16, 18)) },
  icon: { width: 56, height: 56 },
  cellH: { flex: 1, height: '100%' },
  cellV: { flex: 1, width: '100%' },
  buttonRow: { flexDirection: 'row', justifyContent: 'center' },
  tripBtn: { flex: 1, height: 56, marginLeft: 8, marginRight: 8, marginTop: 4, marginBottom: 8 },
  btn: { minHeight: 48, borderRadius: 12, alignItems: 'center', justifyContent: 'center', paddingHorizontal: 12, paddingVertical: 4 },
});

/** Tema "Topográfico": tres relojes sobre curvas de nivel (antes, la app Inclinómetro 3 Relojes). */
export const TopoTheme: Theme = {
  id: 'topo',
  name: 'Topográfico',
  defaultAccent: rgb(63, 208, 178),
  accentPresets: [rgb(63, 208, 178), rgb(255, 122, 26), rgb(230, 57, 53), rgb(255, 196, 30), rgb(76, 200, 80), rgb(45, 130, 255), rgb(150, 90, 255), rgb(255, 80, 160)],
  pipAspect: [7, 3],                                                              // tres relojes en fila, dentro del límite del sistema
  vehicleKinds: KINDS,
  caution: CAUTION_C,
  danger: DANGER_C,
  ui: TopoUi,
  Dash: TopoDash,
};
