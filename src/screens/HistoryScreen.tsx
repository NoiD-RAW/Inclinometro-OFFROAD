import { useState } from 'react';
import { ScrollView, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import Core, { type Trip } from '../../modules/inclinometro-core';
import { useApp } from '../AppState';
import { Chrome } from '../gfx/chrome';
import { css } from '../gfx/color';
import { Fmt, clock } from '../fmt';
import { Dialogs, toast } from '../ui/dialogs';

const maxPitch = (t: Trip) => Math.max(t.max[0], t.max[1]);
const maxRoll = (t: Trip) => Math.max(t.max[2], t.max[3]);
/** La salida llegó a los límites del vehículo que había configurados al grabarla. */
export const exceeded = (t: Trip) => t.danger > 0 || maxPitch(t) >= t.limitPitch || maxRoll(t) >= t.limitRoll;
export const duration = (t: Trip) => Math.max(0, t.end - t.start);

/** Histórico de salidas: lista con filtro/orden, renombrar, borrar y exportar; acceso al detalle y al mapa de puntos críticos. */
export function HistoryScreen({ visible }: { visible: boolean }) {
  const app = useApp();
  const { ui, theme, accent } = app;
  const insets = useSafeAreaInsets();
  const { width } = useWindowDimensions();
  const [byAngle, setByAngle] = useState(false);
  const [onlyExceeded, setOnlyExceeded] = useState(false);
  const [, setV] = useState(0);
  const render = () => setV((v) => v + 1);
  void visible;                                                                 // al volver de otra pantalla cambia y se vuelve a leer la lista
  const B = ui.Button, N = ui.Note;
  const button = (t: string, filled: boolean, onPress: () => void) => <B text={t} filled={filled} accent={accent} onPress={onPress} style={styles.cell} />;
  const row = (...v: React.ReactNode[]) => <View style={styles.row}>{v.map((x, i) => <View key={i} style={[styles.cellBox, i > 0 && { marginLeft: 8 }]}>{x}</View>)}</View>;

  const trips = Core.trips(byAngle).filter((t) => !onlyExceeded || exceeded(t));
  const size = Core.dbSize();
  const st = Core.getState();

  const card = (t: Trip) => {
    const recording = st.recording && st.activeTrip === t.id;
    const dur = recording ? Date.now() - t.start : duration(t);
    return (
      <ui.Section key={t.id} title={t.name} accent={accent} style={{ marginTop: 14 }}>
        {recording && <Text style={[styles.rec, { color: css(Chrome.REC) }]}>●  Grabando ahora</Text>}
        {exceeded(t) && <View style={[styles.badge, { backgroundColor: css(theme.danger) }]}><Text style={styles.badgeText}>⚠  Superó los límites del vehículo</Text></View>}
        <N text={`${Fmt.date(t.start)}  ·  ${clock(dur)}` + (t.hasGps ? `  ·  ${Fmt.km(t.distance)}` : '  ·  sin GPS')} />
        <ui.Label text={`Cabeceo máx ${Fmt.deg(maxPitch(t))}  (arriba ${Fmt.deg(t.max[0])} · abajo ${Fmt.deg(t.max[1])})`} size={15} color={ui.text} />
        <ui.Label text={`Alabeo máx ${Fmt.deg(maxRoll(t))}  (izq. ${Fmt.deg(t.max[2])} · der. ${Fmt.deg(t.max[3])})`} size={15} color={ui.text} />
        <N text={`Zona de peligro: ${t.danger === 0 ? 'ninguna vez' : t.danger === 1 ? '1 vez' : `${t.danger} veces`}  ·  límites ${Fmt.deg(t.limitPitch)} / ${Fmt.deg(t.limitRoll)}`} />
        {row(button('Ver', true, () => app.push({ name: 'detail', id: t.id })),
          button('Renombrar', false, async () => { const n = await Dialogs.prompt('Nombre de la salida', t.name); if (n) { Core.renameTrip(t.id, n); render(); } }))}
        {row(button('Exportar', false, () => exportTrip(t)),
          button('Borrar', false, async () => {
            if (recording) toast('Finaliza la salida antes de borrarla', true);
            else if (await Dialogs.confirm('Borrar salida', `Se borrará «${t.name}» con todos sus datos. No se puede deshacer.`, 'Borrar')) { Core.deleteTrip(t.id); render(); }
          }))}
      </ui.Section>
    );
  };

  // ---------- exportar ----------
  const exportTrip = async (t: Trip) => {
    const i = await Dialogs.pick(`Exportar «${t.name}»`, ['CSV: todos los datos, para hoja de cálculo', 'GPX: el recorrido, para mapas y navegadores GPS']);
    if (i === 0) await Core.exportTrip(t.id, 'csv');
    else if (i === 1) {
      if (!t.hasGps) toast('Esta salida no tiene posiciones GPS: exporta en CSV', true); else await Core.exportTrip(t.id, 'gpx');
    }
  };

  // ---------- liberar espacio ----------
  const freeSpace = async () => {
    const day = 24 * 3600 * 1000, now = Date.now();
    const options: [string, number][] = [['Borrar las de más de 3 meses', now - 90 * day], ['Borrar las de más de 1 año', now - 365 * day], ['Borrar todas', Number.MAX_SAFE_INTEGER]];
    const i = await Dialogs.pick('Liberar espacio', options.map((o) => o[0]));
    if (i < 0) return;
    const [label, before] = options[i];
    if (await Dialogs.confirm(label, 'Se borrarán esas salidas con todos sus datos (la que se esté grabando no). No se puede deshacer.', 'Borrar')) {
      const n = Core.deleteOlderThan(before, Core.getState().activeTrip);
      toast(`Borradas ${n} salidas`, true); render();
    }
  };

  return (
    <View style={StyleSheet.absoluteFill}>
      <ui.Background />
      <ScrollView contentContainerStyle={{ alignItems: 'center', paddingTop: insets.top + 12, paddingBottom: insets.bottom + 28, paddingLeft: insets.left, paddingRight: insets.right }}>
        <View style={{ width: width > 760 ? 720 : '100%', paddingHorizontal: 16 }}>
          <View style={styles.header}>
            <B text="←  Volver" filled={false} accent={accent} onPress={app.pop} style={styles.back} label="Volver" />
            <ui.Label text="Salidas" size={22} color={ui.text} style={{ paddingLeft: 16, paddingTop: 0, paddingBottom: 0 }} />
          </View>
          {row(button('Mapa de puntos críticos', true, () => app.push({ name: 'hotspots' })))}
          {row(button(byAngle ? 'Orden: mayor ángulo' : 'Orden: más recientes', false, () => setByAngle(!byAngle)),
            button(onlyExceeded ? 'Ver: solo sobre límite' : 'Ver: todas', false, () => setOnlyExceeded(!onlyExceeded)))}
          <N text={`Las salidas ocupan ${Fmt.mb(size)} (unos ${Fmt.mb((90 * 3600 * 1000) / Core.getConfig().sampleMs)} por hora de grabación).`} />
          {size > 200 * 1048576 && <N text="Ocupan bastante espacio: puedes borrar las antiguas con «Liberar espacio»." color={theme.caution} />}
          {row(button('Liberar espacio', false, freeSpace))}
          {trips.length === 0 && <N text={onlyExceeded ? 'Ninguna salida ha superado los límites.' : 'Aún no hay salidas. Inicia una desde la pantalla principal.'} style={{ paddingTop: 24 }} />}
          {trips.map(card)}
        </View>
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  header: { flexDirection: 'row', alignItems: 'center' },
  back: { width: 130, height: 50 },
  row: { flexDirection: 'row' },
  cellBox: { flex: 1, height: 52, marginTop: 8 },
  cell: { flex: 1 },
  rec: { fontSize: 15, fontWeight: 'bold' },
  badge: { alignSelf: 'flex-start', borderRadius: 6, paddingHorizontal: 10, paddingVertical: 6, marginBottom: 6 },
  badgeText: { color: '#fff', fontSize: 14, fontWeight: 'bold' },
});
