import { useEffect, useMemo, useState } from 'react';
import { StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import Core from '../../modules/inclinometro-core';
import { useApp } from '../AppState';
import { css, rgb } from '../gfx/color';
import { Fmt, clock, round, signed1 } from '../fmt';
import { TripChart } from '../ui/TripChart';
import { TrackMap } from '../ui/maps';
import { duration } from './HistoryScreen';

/** Detalle de una salida: gráfico de cabeceo y alabeo enlazado con el mapa del recorrido.
 *  Tocar o arrastrar en el gráfico marca la posición en el mapa; tocar el recorrido marca ese instante en el gráfico.
 *  Sin cobertura, el gráfico y los datos funcionan igual aunque el mapa no cargue. */
export function TripDetailScreen({ id }: { id: number }) {
  const app = useApp();
  const { ui, theme, accent } = app;
  const insets = useSafeAreaInsets();
  const { width, height } = useWindowDimensions();
  const trip = useMemo(() => Core.trip(id), [id]);
  const s = useMemo(() => Core.samples(id), [id]);
  const [cursor, setCursor] = useState(-1);
  useEffect(() => { if (!trip) app.pop(); }, [trip, app]);                      // la salida ya no existe: se cierra
  if (!trip) return null;
  const n = s.t.length;
  const hasMap = n > 0 && s.lat.some((v) => v !== null);

  /** Datos del instante [i], con sus ángulos exactos. */
  const info = cursor < 0 ? 'Toca o arrastra sobre el gráfico para ver un instante' : (() => {
    const i = cursor, pos = s.lat[i] !== null;
    return `${Fmt.time(s.t[i])}  ·  Cabeceo ${signed1(s.pitch[i])}°  ·  Alabeo ${signed1(s.roll[i])}°` +
      (s.kmh[i] !== null ? `  ·  ${round(s.kmh[i]!)} km/h` : '') + (s.alt[i] !== null ? `  ·  ${round(s.alt[i]!)} m` : '') +
      (!pos && hasMap ? '  ·  sin GPS en este instante' : '');
  })();

  const land = width > height;
  const chart = <TripChart s={s} trip={trip} theme={theme} accent={accent} cursor={cursor} onCursor={setCursor} style={land ? styles.half : { flex: 0.42 }} />;
  const map = hasMap
    ? <TrackMap s={s} trip={trip} theme={theme} accent={accent} cursor={cursor} onTap={setCursor} style={land ? [styles.half, { marginLeft: 10 }] : { flex: 0.58, marginTop: 10 }} />
    : (
      <View style={[styles.noMap, land ? [styles.half, { marginLeft: 10 }] : { flex: 0.58, marginTop: 10 }]}>
        <Text style={[styles.noMapText, { color: css(ui.muted) }]}>Esta salida no tiene posiciones GPS: solo se muestra el gráfico.</Text>
      </View>
    );
  return (
    <View style={StyleSheet.absoluteFill}>
      <ui.Background />
      <View style={{ flex: 1, paddingTop: insets.top + 10, paddingBottom: insets.bottom + 12, paddingLeft: insets.left + 12, paddingRight: insets.right + 12 }}>
        <View style={styles.header}>
          <ui.Button text="←  Volver" filled={false} accent={accent} onPress={app.pop} style={styles.back} label="Volver" />
          <ui.Label text={trip.name} size={20} color={ui.text} lines={1} style={{ paddingLeft: 16, paddingTop: 0, paddingBottom: 0, flex: 1 }} />
        </View>
        <ui.Note text={`${Fmt.date(trip.start)}  ·  ${clock(duration(trip))}` + (trip.hasGps ? `  ·  ${Fmt.km(trip.distance)}` : '') +
          `  ·  cabeceo máx ${Fmt.deg(Math.max(trip.max[0], trip.max[1]))}  ·  alabeo máx ${Fmt.deg(Math.max(trip.max[2], trip.max[3]))}  ·  peligro ${trip.danger}×`} />
        <ui.Label text={info} size={16} color={ui.text} style={{ paddingTop: 4, paddingBottom: 4 }} />
        <View style={{ flex: 1, flexDirection: land ? 'row' : 'column' }}>{chart}{map}</View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  header: { flexDirection: 'row', alignItems: 'center' },
  back: { width: 130, height: 50 },
  half: { flex: 1 },
  noMap: { backgroundColor: css(rgb(22, 22, 25)), borderRadius: 10, alignItems: 'center', justifyContent: 'center', padding: 12 },
  noMapText: { fontSize: 15, textAlign: 'center' },
});
