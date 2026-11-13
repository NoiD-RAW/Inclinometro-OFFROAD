import {
  Camera, GeoJSONSource, Layer, Map, ViewAnnotation, type CameraRef, type LngLatBounds, type ViewStateChangeEvent,
} from '@maplibre/maplibre-react-native';
import type { StyleSpecification } from '@maplibre/maplibre-gl-style-spec';
import { useEffect, useMemo, useRef, useState } from 'react';
import { Linking, Pressable, StyleSheet, Text, View, type NativeSyntheticEvent, type StyleProp, type ViewStyle } from 'react-native';

import type { Hotspot, Samples, Trip } from '../../modules/inclinometro-core';
import { blue, css, green, red, rgb, withAlpha } from '../gfx/color';
import { Fmt } from '../fmt';
import type { Theme } from '../themes/types';
import { toast } from './dialogs';

const OSM_COPYRIGHT_URL = 'https://www.openstreetmap.org/copyright';

/** Mapas OpenStreetMap: gratis y sin clave. Las teselas vistas se guardan en la caché del mapa para zonas sin cobertura. */
const OSM: StyleSpecification = {
  version: 8,
  sources: { osm: { type: 'raster', tiles: ['https://tile.openstreetmap.org/{z}/{x}/{y}.png'], tileSize: 256, maxzoom: 19, attribution: `© <a href="${OSM_COPYRIGHT_URL}">OpenStreetMap contributors</a>` } },
  layers: [{ id: 'osm', type: 'raster', source: 'osm' }],
};

/** Encuadre de los puntos (o centrado si solo hay uno). */
function initialView(lats: number[], lons: number[]) {
  if (!lats.length) return { center: [-3.7, 40.4] as [number, number], zoom: 6 };
  const n = Math.max(...lats), s = Math.min(...lats), e = Math.max(...lons), w = Math.min(...lons);
  if (n - s < 0.0005 && e - w < 0.0005) return { center: [(e + w) / 2, (n + s) / 2] as [number, number], zoom: 17 };
  const dy = ((n - s) * 0.25) / 2, dx = ((e - w) * 0.25) / 2;                    // un 25 % de margen
  return { bounds: [w - dx, s - dy, e + dx, n + dy] as LngLatBounds };
}

/** Color según la inclinación respecto al límite: verde → ámbar (zona de precaución) → rojo (límite). */
export function inclineColor(ratio: number, caution: number, amber: number) {
  const g = rgb(60, 200, 90), r = rgb(235, 40, 40);
  if (ratio >= 1) return r;
  if (ratio >= caution) return lerp(amber, r, (ratio - caution) / (1 - caution));
  if (ratio >= caution * 0.5) return lerp(g, amber, (ratio - caution * 0.5) / (caution * 0.5));
  return g;
}
function lerp(a: number, b: number, t: number) {
  const k = Math.min(1, Math.max(0, t));
  return rgb(Math.trunc(red(a) + (red(b) - red(a)) * k), Math.trunc(green(a) + (green(b) - green(a)) * k), Math.trunc(blue(a) + (blue(b) - blue(a)) * k));
}

type Viewport = { zoom: number; bounds: LngLatBounds | null };
const metersPerDp = (lat: number, zoom: number) => (156543.03392 * Math.cos((lat * Math.PI) / 180)) / Math.pow(2, zoom) / 2;   // teselas de 256 escaladas a 512 px lógicos

function BaseMap({ children, lats, lons, onView, cameraRef, onPress, style }: {
  children?: React.ReactNode; lats: number[]; lons: number[]; onView?: (v: Viewport) => void; cameraRef?: React.Ref<CameraRef>;
  onPress?: () => void; style?: StyleProp<ViewStyle>;
}) {
  const init = useMemo(() => initialView(lats, lons), [lats, lons]);
  const changed = (e: NativeSyntheticEvent<ViewStateChangeEvent>) => onView?.({ zoom: e.nativeEvent.zoom, bounds: e.nativeEvent.bounds });
  return (
    <View style={[styles.map, style]}>
      <Map style={StyleSheet.absoluteFill} mapStyle={OSM} touchRotate={false} touchPitch={false} compass={false} logo={false} attribution attributionPosition={{ bottom: 4, right: 4 }}
        onRegionDidChange={changed} onPress={onPress}>
        <Camera ref={cameraRef} initialViewState={init} minZoom={3} maxZoom={19} />
        {children}
      </Map>
      <Pressable accessibilityRole="link" accessibilityLabel="OpenStreetMap contributors: licencia y atribución" hitSlop={6} style={styles.attribution}
        onPress={() => { void Linking.openURL(OSM_COPYRIGHT_URL).catch(() => toast('No se ha podido abrir la licencia de OpenStreetMap.', true)); }}>
        <Text style={styles.attributionText}>© OpenStreetMap contributors</Text>
      </Pressable>
    </View>
  );
}

/** Recorrido de una salida coloreado por inclinación, con marcadores en los máximos y el instante seleccionado.
 *  Tocar el recorrido selecciona el instante más cercano. */
export function TrackMap({ s, trip, theme, accent, cursor, onTap, style }: {
  s: Samples; trip: Trip; theme: Theme; accent: number; cursor: number; onTap: (i: number) => void; style?: StyleProp<ViewStyle>;
}) {
  const camera = useRef<CameraRef>(null);
  const [view, setView] = useState<Viewport>({ zoom: 14, bounds: null });
  const n = s.t.length;
  const has = (i: number) => s.lat[i] !== null && s.lon[i] !== null;
  const ratio = (i: number) => Math.max(Math.abs(s.pitch[i]) / trip.limitPitch, Math.abs(s.roll[i]) / trip.limitRoll);
  const idx = useMemo(() => { const out: number[] = []; for (let i = 0; i < n; i++) if (has(i)) out.push(i); return out; }, [s]);   // eslint-disable-line react-hooks/exhaustive-deps
  const lats = useMemo(() => idx.map((i) => s.lat[i]!), [idx, s]), lons = useMemo(() => idx.map((i) => s.lon[i]!), [idx, s]);

  const segments = useMemo<GeoJSON.FeatureCollection>(() => {
    const f: GeoJSON.Feature[] = []; let prev = -1;
    for (let i = 0; i < n; i++) {
      if (!has(i)) { prev = -1; continue; }
      if (prev >= 0 && s.t[i] - s.t[prev] < 30000)                                // sin unir huecos largos (sin GPS)
        f.push({ type: 'Feature', properties: { c: css(inclineColor(Math.max(ratio(prev), ratio(i)), trip.cautionPct, theme.caution)) },
          geometry: { type: 'LineString', coordinates: [[s.lon[prev]!, s.lat[prev]!], [s.lon[i]!, s.lat[i]!]] } });
      prev = i;
    }
    return { type: 'FeatureCollection', features: f };
  }, [s, trip, theme]);   // eslint-disable-line react-hooks/exhaustive-deps
  const points = useMemo<GeoJSON.FeatureCollection>(() => ({
    type: 'FeatureCollection', features: idx.map((i) => ({ type: 'Feature', properties: { i }, geometry: { type: 'Point', coordinates: [s.lon[i]!, s.lat[i]!] } })),
  }), [idx, s]);

  /** Muestra con más cabeceo arriba/abajo y más alabeo izq./der. (solo las que tienen posición). */
  const maxIdx = useMemo(() => {
    const best = [0, 0, 0, 0], out = [-1, -1, -1, -1];
    for (const i of idx) { const v = [s.pitch[i], -s.pitch[i], s.roll[i], -s.roll[i]]; for (let k = 0; k < 4; k++) if (v[k] > best[k]) { best[k] = v[k]; out[k] = i; } }
    return out;
  }, [idx, s]);
  const labels = ['▲', '▼', '◄', '►'], names = ['Cab.', 'Cab.', 'Alab.', 'Alab.'];
  const placed: { i: number }[] = [];
  const markers = maxIdx.map((i, k) => {
    if (i < 0) return null;
    const v = k < 2 ? Math.abs(s.pitch[i]) : Math.abs(s.roll[i]); if (v < 1) return null;
    const mpd = metersPerDp(s.lat[i]!, view.zoom);
    const near = (j: number) => {                                                   // si coinciden, una etiqueta encima de otra
      const dx = ((s.lon[j]! - s.lon[i]!) * 111320 * Math.cos((s.lat[i]! * Math.PI) / 180)) / mpd, dy = ((s.lat[j]! - s.lat[i]!) * 110540) / mpd;
      return Math.abs(dx) < 60 && Math.abs(dy) < 30;
    };
    const stack = placed.filter((p) => near(p.i)).length; placed.push({ i });
    const col = css(inclineColor(ratio(i), trip.cautionPct, theme.caution));
    return (
      <ViewAnnotation key={`m${k}`} id={`m${k}`} lngLat={[s.lon[i]!, s.lat[i]!]} anchor="bottom" offset={[0, 5]}>
        <View style={styles.marker} pointerEvents="none">
          <View style={[styles.mBox, { borderColor: col }]}><Text style={styles.mText}>{`${names[k]} ${labels[k]} ${Fmt.deg(v)}`}</Text></View>
          <View style={{ width: 2, height: 14 + stack * 28, backgroundColor: col }} />
          <View style={[styles.mDot, { backgroundColor: col }]} />
        </View>
      </ViewAnnotation>
    );
  });

  // Instante elegido en el gráfico fuera de la vista: el mapa se mueve hasta él
  const bounds = useRef<LngLatBounds | null>(null);
  useEffect(() => { bounds.current = view.bounds; }, [view.bounds]);
  useEffect(() => {
    const b = bounds.current;
    if (cursor < 0 || s.lat[cursor] === null || !b) return;
    const [w, so, e, no] = b, lon = s.lon[cursor]!, lat = s.lat[cursor]!;
    if (lon < w || lon > e || lat < so || lat > no) camera.current?.easeTo({ center: [lon, lat], duration: 500 });
  }, [cursor, s]);
  const cur = cursor >= 0 && cursor < n && has(cursor) ? cursor : -1;

  return (
    <BaseMap lats={lats} lons={lons} onView={setView} cameraRef={camera} style={style}>
      <GeoJSONSource id="track" data={segments}>
        <Layer id="track-line" type="line" layout={{ 'line-cap': 'round', 'line-join': 'round' }} paint={{ 'line-color': ['get', 'c'], 'line-width': 6 }} />
      </GeoJSONSource>
      <GeoJSONSource id="track-pts" data={points} hitbox={{ top: 40, right: 40, bottom: 40, left: 40 }} onPress={(e) => {
        const [lon, lat] = e.nativeEvent.lngLat; let best = -1, bd = Infinity;
        for (const f of e.nativeEvent.features) {
          const i = (f.properties as { i: number }).i, k = Math.cos((lat * Math.PI) / 180), dd = ((s.lon[i]! - lon) * k) ** 2 + (s.lat[i]! - lat) ** 2;
          if (dd < bd) { bd = dd; best = i; }
        }
        if (best >= 0) { e.stopPropagation?.(); onTap(best); }
      }}>
        <Layer id="track-hit" type="circle" paint={{ 'circle-radius': 4, 'circle-opacity': 0 }} />
      </GeoJSONSource>
      {markers}
      {cur >= 0 && (
        <GeoJSONSource id="cursor" data={{ type: 'Feature', properties: {}, geometry: { type: 'Point', coordinates: [s.lon[cur]!, s.lat[cur]!] } }}>
          <Layer id="cursor-o" type="circle" paint={{ 'circle-radius': 13, 'circle-color': '#ffffff' }} />
          <Layer id="cursor-i" type="circle" paint={{ 'circle-radius': 9, 'circle-color': css(accent) }} />
        </GeoJSONSource>
      )}
    </BaseMap>
  );
}

/** Puntos críticos de todas las salidas, coloreados y con tamaño según la inclinación. */
export function HotspotMap({ pts, theme, caution, selected, onTap, style }: {
  pts: Hotspot[]; theme: Theme; caution: number; selected: Hotspot | null; onTap: (h: Hotspot) => void; style?: StyleProp<ViewStyle>;
}) {
  const lats = useMemo(() => pts.map((h) => h.lat), [pts]), lons = useMemo(() => pts.map((h) => h.lon), [pts]);
  const data = useMemo<GeoJSON.FeatureCollection>(() => ({
    type: 'FeatureCollection',
    features: pts.map((h, k) => ({ type: 'Feature', properties: {                     // los más fuertes, encima (vienen ordenados)
      k, r: 6 + (6 * Math.min(1, Math.max(0, h.ratio - caution))) / (1.2 - caution),
      c: css(withAlpha(inclineColor(h.ratio, caution, theme.caution), 210)),
    }, geometry: { type: 'Point', coordinates: [h.lon, h.lat] } })),
  }), [pts, caution, theme]);
  return (
    <BaseMap lats={lats} lons={lons} style={style}>
      <GeoJSONSource id="hot" data={data} hitbox={{ top: 36, right: 36, bottom: 36, left: 36 }} onPress={(e) => {
        const [lon, lat] = e.nativeEvent.lngLat; let best: Hotspot | null = null, bd = Infinity;
        for (const f of e.nativeEvent.features) {
          const h = pts[(f.properties as { k: number }).k], k = Math.cos((lat * Math.PI) / 180), dd = ((h.lon - lon) * k) ** 2 + (h.lat - lat) ** 2;
          if (dd < bd) { bd = dd; best = h; }
        }
        if (best) { e.stopPropagation?.(); onTap(best); }
      }}>
        <Layer id="hot-c" type="circle" paint={{ 'circle-radius': ['get', 'r'], 'circle-color': ['get', 'c'], 'circle-stroke-width': 1.5, 'circle-stroke-color': 'rgba(0,0,0,0.78)' }} />
      </GeoJSONSource>
      {selected && (
        <GeoJSONSource id="hot-sel" data={{ type: 'Feature', properties: {}, geometry: { type: 'Point', coordinates: [selected.lon, selected.lat] } }}>
          <Layer id="hot-sel-c" type="circle" paint={{ 'circle-radius': 16, 'circle-color': 'rgba(0,0,0,0)', 'circle-stroke-width': 3, 'circle-stroke-color': '#ffffff' }} />
        </GeoJSONSource>
      )}
    </BaseMap>
  );
}

const styles = StyleSheet.create({
  map: { flex: 1, borderRadius: 0 },
  attribution: { position: 'absolute', bottom: 6, left: 6, paddingHorizontal: 5, paddingVertical: 3, borderRadius: 3, backgroundColor: 'rgba(255,255,255,0.94)' },
  attributionText: { color: '#17212d', fontSize: 11, lineHeight: 15 },
  marker: { alignItems: 'center' },
  mBox: { backgroundColor: 'rgba(20,20,22,0.9)', borderWidth: 2, borderRadius: 6, height: 24, paddingHorizontal: 7, justifyContent: 'center' },
  mText: { color: '#fff', fontSize: 13 },
  mDot: { width: 10, height: 10, borderRadius: 5 },
});
