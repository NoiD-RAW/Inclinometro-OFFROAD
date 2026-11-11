import { useMemo, useState } from 'react';
import { StyleSheet, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import Core, { type Hotspot } from '../../modules/inclinometro-core';
import { useApp } from '../AppState';
import { Fmt, signed1 } from '../fmt';
import { HotspotMap } from '../ui/maps';

/** Mapa de puntos críticos: los lugares de todas las salidas donde se superó la zona de precaución, coloreados por intensidad. */
export function HotspotsScreen() {
  const app = useApp();
  const { ui, theme, accent } = app;
  const insets = useSafeAreaInsets();
  const cfg = useMemo(() => Core.getConfig(), []);
  const pts = useMemo(() => Core.hotspots(), []);
  const [sel, setSel] = useState<Hotspot | null>(null);
  return (
    <View style={StyleSheet.absoluteFill}>
      <ui.Background />
      <View style={{ flex: 1, paddingTop: insets.top + 10, paddingBottom: insets.bottom + 12, paddingLeft: insets.left + 12, paddingRight: insets.right + 12 }}>
        <View style={styles.header}>
          <ui.Button text="←  Volver" filled={false} accent={accent} onPress={app.pop} style={styles.back} label="Volver" />
          <ui.Label text="Puntos críticos" size={20} color={ui.text} style={{ paddingLeft: 16, paddingTop: 0, paddingBottom: 0, flex: 1 }} />
        </View>
        <ui.Note text={pts.length === 0 ? 'Aún no hay puntos por encima de la zona de precaución en salidas con GPS.'
          : `${pts.length} puntos por encima del ${Math.round(cfg.cautionPct * 100)} % de los límites actuales (${Fmt.deg(cfg.limitPitch)} cabeceo / ${Fmt.deg(cfg.limitRoll)} alabeo). Ámbar: precaución · rojo: límite superado. Toca un punto.`} />
        <HotspotMap pts={pts} theme={theme} caution={cfg.cautionPct} selected={sel} onTap={setSel} style={{ flex: 1 }} />
        {sel && (                                                                  // datos del punto tocado y enlace a su salida
          <View>
            <ui.Label text={`Cabeceo ${signed1(sel.pitch)}°  ·  Alabeo ${signed1(sel.roll)}°`} size={18} color={ui.text} style={{ paddingTop: 10, paddingBottom: 0 }} />
            <ui.Note text={`${Fmt.date(sel.t)}  ·  ${sel.tripName}`} />
            <ui.Button text="Ver salida" filled accent={accent} onPress={() => app.push({ name: 'detail', id: sel.trip })} style={{ height: 52 }} />
          </View>
        )}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  header: { flexDirection: 'row', alignItems: 'center' },
  back: { width: 130, height: 50 },
});
