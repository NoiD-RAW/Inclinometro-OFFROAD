import { Canvas, Image as SkiaImage, type SkImage } from '@shopify/react-native-skia';
import { useState, type ReactNode } from 'react';
import { Keyboard, Platform, Pressable, ScrollView, StyleSheet, Text, TextInput, View, useWindowDimensions } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { setConfig } from '../config';
import Core, { type Config } from '../../modules/inclinometro-core';
import { useApp } from '../AppState';
import { useAssets } from '../gfx/assets';
import { WHITE, css, hex, parseHex, rgb } from '../gfx/color';
import { Fmt, round, sampleLabel, signed1, windowLabel } from '../fmt';
import { THEMES, accentOf, useVehicle } from '../themes';
import { demoState, type Kind, type Theme } from '../themes/types';
import { Dialogs, toast } from '../ui/dialogs';

const android = Platform.OS === 'android';
const SAMPLES = [500, 1000, 2000, 5000];
const WINDOWS = [0, 250, 500, 1000, 2000];
const CAPTION: Record<Kind, string> = { side: 'Lateral', rear: 'Trasera / frontal', top: 'Superior' };
const noop = () => {};
const DEMO = demoState();

/** Pantalla de Ajustes, construida con las piezas del tema activo. Se vuelve a pintar entera al cambiar algo. */
export function SettingsScreen() {
  const app = useApp();
  const { ui, theme: t, accent } = app;
  const { width } = useWindowDimensions();
  const insets = useSafeAreaInsets();
  const cfg = Core.getConfig();
  const B = ui.Button, N = ui.Note, S = ui.Section, L = ui.Label;
  const btn = (text: string, filled: boolean, onPress: () => void) => <B text={text} filled={filled} accent={accent} onPress={onPress} style={styles.btn} />;
  const set = <K extends keyof Config>(k: K, v: Config[K]) => { setConfig(k, v); app.rebuild(); };

  const pickChain = async (kinds: Kind[]) => { await Core.pickVehicle(t.id, kinds); app.refreshVehicle(); app.rebuild(); };
  const resetCal = () => Core.resetCal().then(app.rebuild);

  const themes = <S title="Tema" accent={accent}><ThemePicker /></S>;
  const vehicle = (
    <S title="Personaliza tu vehículo" accent={accent}>
      <View style={styles.row}>{t.vehicleKinds.map((k) => <Slot key={k} kind={k} onPress={() => pickChain([k])} />)}</View>
      <N text={'PNG sin fondo. Lateral: con el morro a la izquierda. Trasera o frontal: vista recta.' +
        (t.vehicleKinds.includes('top') ? ' Superior: con el morro hacia arriba.' : '') + ' La app recorta los márgenes vacíos y ajusta el tamaño sola. Cada tema guarda sus propias imágenes.'} />
      {btn('Subir fotos', true, () => pickChain(t.vehicleKinds))}
      {android && btn('Cargar desde carpeta de la app', false, async () => { await Core.loadFolder(t.id); app.refreshVehicle(); app.rebuild(); })}
      {android && <N text="Sin selector de archivos: copia lateral.png, trasera.png y superior.png a la carpeta de la app." />}
      {btn('Restablecer vehículo original', false, () => { Core.resetVehicle(t.id); app.refreshVehicle(); app.rebuild(); })}
    </S>
  );
  const color = <S title="Color principal" accent={accent}><Swatches /><HexRow /></S>;
  const calibration = (
    <S title="Calibración" accent={accent}>
      <N text="Pon el cero con el vehículo en una superficie plana. Se guarda al cerrar la app y es la misma para todos los temas." />
      <N text={cfg.offRoll === 0 && cfg.offPitch === 0 ? 'Sin calibrar (cero de fábrica).' : `Corrección guardada: alabeo ${signed1(cfg.offRoll)}°, cabeceo ${signed1(cfg.offPitch)}°.`} />
      {btn('Calibrar a 0°', true, async () => {
        if (await Dialogs.confirm('Calibrar a 0°', 'Coloca el vehículo en una superficie plana y pulsa Confirmar.')) {
          await Core.calibrate(); toast('Calibrado a 0°'); app.rebuild();
        }
      })}
      {btn('Restablecer calibración', false, async () => {
        if (await Dialogs.confirm('Restablecer calibración', 'Se borrará la calibración guardada y se volverá al cero de fábrica.', 'Restablecer')) {
          await Core.resetCal(); toast('Calibración restablecida'); app.rebuild();
        }
      })}
    </S>
  );
  const canVibrate = Core.canVibrate();
  const limits = (
    <S title="Límites del vehículo y avisos" accent={accent}>
      <N text="Ángulo máximo que admite tu vehículo. Al acercarte (zona de precaución) el reloj se marca en ámbar; al llegar al límite el fondo pasa a rojo." />
      <Stepper title="Límite de cabeceo" value={() => `${round(Core.getConfig().limitPitch)}°`}
        change={(d) => setConfig('limitPitch', Math.min(60, Math.max(5, Core.getConfig().limitPitch + d)))} />
      <Stepper title="Límite de alabeo" value={() => `${round(Core.getConfig().limitRoll)}°`}
        change={(d) => setConfig('limitRoll', Math.min(60, Math.max(5, Core.getConfig().limitRoll + d)))} />
      <Stepper title="Zona de precaución" value={() => `${round(Core.getConfig().cautionPct * 100)} % del límite`}
        change={(d) => setConfig('cautionPct', Math.min(95, Math.max(50, round(Core.getConfig().cautionPct * 100) + 5 * d)) / 100)} />
      {btn('Usar máximos registrados como límites', false, () => maximaAsLimits(app.rebuild))}
      {canVibrate ? btn(cfg.vibrate ? 'Vibración al entrar en peligro: sí' : 'Vibración al entrar en peligro: no', false, () => set('vibrate', !cfg.vibrate))
        : <N text="Esta unidad no tiene vibración. Usa el pitido para los avisos." />}
      {btn(cfg.beep ? 'Pitido al entrar en peligro: sí' : 'Pitido al entrar en peligro: no', false, () => set('beep', !cfg.beep))}
      {btn('Probar aviso', false, async () => { if (!(await Core.testAlert())) toast('Activa la vibración o el pitido para probarlo'); })}
    </S>
  );
  const m = Core.getState().max;
  const maxima = (
    <S title="Máximos registrados" accent={accent}>
      <L text={`Cabeceo:  morro arriba ${round(m[0])}° · morro abajo ${round(m[1])}°`} size={15} color={ui.text} />
      <L text={`Alabeo:  izquierda ${round(m[2])}° · derecha ${round(m[3])}°`} size={15} color={ui.text} />
      <N text="Para que un bache no deje un pico falso, un ángulo solo cuenta como máximo si se mantiene el tiempo del filtro." />
      {btn(`Filtro de baches: ${windowLabel(cfg.maxWindowMs)}  (toca para cambiar)`, false, () => {
        const i = WINDOWS.indexOf(cfg.maxWindowMs); set('maxWindowMs', WINDOWS[(i + 1) % WINDOWS.length]);
      })}
      {btn('Resetear máximos', false, async () => {
        if (await Dialogs.confirm('Resetear máximos', 'Se borrarán los máximos registrados de cabeceo y alabeo.', 'Resetear')) { await Core.resetMaxima(); app.rebuild(); }
      })}
    </S>
  );
  const trips = (
    <S title="Salidas" accent={accent}>
      <N text="Durante una salida se guardan cabeceo, alabeo y, con GPS, posición, altitud y velocidad. Sigue grabando aunque cambies de app." />
      {btn('Ver histórico de salidas', true, () => app.push({ name: 'history' }))}
      {btn(`Guardar una muestra cada ${sampleLabel(cfg.sampleMs)}  (toca para cambiar)`, false, () => {
        const i = SAMPLES.indexOf(cfg.sampleMs); set('sampleMs', SAMPLES[(i + 1) % SAMPLES.length]);
      })}
      <N text={`Las salidas ocupan ${Fmt.mb(Core.dbSize())}. Puedes liberar espacio desde el histórico.`} />
    </S>
  );
  const sensor = (
    <S title="Montaje del sensor" accent={accent}>
      <N text="Cambiar el montaje o los ejes restablece la calibración." />
      {btn(cfg.flat ? 'Montaje: plano' : 'Montaje: vertical', false, () => { setConfig('flat', !cfg.flat); resetCal(); })}
      {btn(`Ejes: ${cfg.rot * 90}°  (toca para girar 90°)`, false, () => { setConfig('rot', (cfg.rot + 1) % 4); resetCal(); })}
      {btn(cfg.invertPitch ? 'Cabeceo: invertido' : 'Cabeceo: normal', false, () => { setConfig('invertPitch', !cfg.invertPitch); resetCal(); })}
    </S>
  );
  const pip = android ? (
    <S title="Mini ventana (PiP)" accent={accent}>
      {btn('Abrir mini ventana ahora', false, () => app.enterPip(false))}
      {btn(cfg.autoPip ? 'Mini ventana al pulsar Inicio: sí' : 'Mini ventana al pulsar Inicio: no', false, () => set('autoPip', !cfg.autoPip))}
    </S>
  ) : null;

  const wide = width >= 840;
  const gap = (key: string, v: ReactNode, first = false) => v && <View key={key} style={first ? null : styles.gap}>{v}</View>;
  const body = wide ? (                                                         // pantallas anchas: dos columnas
    <View style={styles.row}>
      <View style={[styles.col, { marginRight: 8 }]}>{gap('t', themes, true)}{gap('s', trips)}{gap('v', vehicle)}{gap('c', color)}{gap('p', pip)}</View>
      <View style={[styles.col, { marginLeft: 8 }]}>{gap('k', calibration, true)}{gap('l', limits)}{gap('m', maxima)}{gap('n', sensor)}</View>
    </View>
  ) : (
    <View>
      {gap('t', themes, true)}
      {[trips, calibration, limits, maxima, sensor, vehicle, color, pip].map((v, i) => gap(String(i), v))}
    </View>
  );
  return (
    <View style={StyleSheet.absoluteFill}>
      <ui.Background />
      <ScrollView contentContainerStyle={{ paddingTop: insets.top + 12, paddingBottom: insets.bottom + 28, paddingLeft: insets.left + 16, paddingRight: insets.right + 16, alignItems: 'center' }}>
        <View style={[styles.header, { width: '100%' }]}>
          <B text="←  Volver" filled={false} accent={accent} onPress={app.showDash} style={styles.back} label="Volver" />
          <L text="Ajustes" size={22} color={ui.text} style={styles.title} />
        </View>
        <View style={{ width: !wide && width > 700 ? 640 : '100%' }}>{body}</View>
      </ScrollView>
    </View>
  );
}

function maximaAsLimits(rebuild: () => void) {
  const m = Core.getState().max;
  const p = round(Math.max(m[0], m[1])), r = round(Math.max(m[2], m[3]));
  if (p < 5 && r < 5) { toast('Aún no hay máximos registrados (mínimo 5°)', true); return; }
  Dialogs.confirm('Usar máximos como límites', `Límite de cabeceo: ${p >= 5 ? `${p}°` : 'sin cambios'}\nLímite de alabeo: ${r >= 5 ? `${r}°` : 'sin cambios'}`, 'Usar').then((ok) => {
    if (!ok) return;
    if (p >= 5) setConfig('limitPitch', Math.min(p, 60));
    if (r >= 5) setConfig('limitRoll', Math.min(r, 60));
    rebuild();
  });
}

/** Fila «− valor +» con botones grandes; el valor se actualiza sin volver a pintar la pantalla. */
function Stepper({ title, value, change }: { title: string; value: () => string; change: (d: number) => void }) {
  const { ui, accent } = useApp();
  const [, setTick] = useState(0);
  const v = value();                                                             // siempre el valor guardado (también tras «Usar máximos…»)
  const press = (d: number) => { change(d); setTick((t) => t + 1); };
  return (
    <View>
      <ui.Note text={title} />
      <View style={styles.stepRow}>
        <ui.Button text="−" filled={false} accent={accent} onPress={() => press(-1)} textSize={24} style={styles.stepBtn} label={`Bajar ${title}`} />
        <ui.Label text={v} size={22} color={ui.text} style={styles.stepVal} />
        <ui.Button text="+" filled={false} accent={accent} onPress={() => press(1)} textSize={24} style={styles.stepBtn} label={`Subir ${title}`} />
      </View>
    </View>
  );
}

// ---------- selector de tema: miniatura de la pantalla real de cada tema ----------
function ThemePicker() {
  const a = useAssets();
  const app = useApp();
  // las imágenes del vehículo de cada tema (cada uno guarda las suyas)
  const vehicles = [useVehicle(THEMES[0], a, app.vehicleVersion), useVehicle(THEMES[1], a, app.vehicleVersion)];
  return (
    <View style={styles.row}>
      {THEMES.map((th, i) => {
        const sel = th.id === app.theme.id;
        return (
          <Pressable key={th.id} onPress={() => { if (!sel) app.applyTheme(th.id); }} accessibilityLabel={`Tema ${th.name}`}
            style={[styles.card, { borderWidth: sel ? 4 : 2, borderColor: css(sel ? app.accent : rgb(90, 90, 96)) }]}>
            <Preview theme={th} vehicle={th.id === app.theme.id ? app.vehicle : vehicles[i]} />
            <Text style={[styles.cardText, { color: css(sel ? app.accent : app.ui.text) }]}>{sel ? `✓ ${th.name}` : th.name}</Text>
          </Pressable>
        );
      })}
    </View>
  );
}

/** Dibuja la pantalla principal real del tema, a tamaño de pantalla, con datos de ejemplo, y la reduce. */
function Preview({ theme, vehicle }: { theme: Theme; vehicle: ReturnType<typeof useVehicle> }) {
  const screen = useWindowDimensions();
  const [w, setW] = useState(0);
  const k = w / screen.width;
  const D = theme.Dash;
  return (
    <View onLayout={(e) => setW(e.nativeEvent.layout.width)} style={{ width: '100%', height: w ? screen.height * k : 1, overflow: 'hidden', backgroundColor: css(rgb(16, 16, 18)) }} pointerEvents="none">
      {w > 0 && (
        <View style={{ width: screen.width, height: screen.height, transform: [{ translateX: -(screen.width * (1 - k)) / 2 }, { translateY: -(screen.height * (1 - k)) / 2 }, { scale: k }] }}>
          <D state={DEMO} accent={accentOf(theme)} vehicle={vehicle} actions={{ onSettings: noop, onHistory: noop, onTrip: noop }} pip={false} />
        </View>
      )}
    </View>
  );
}

// ---------- imágenes del vehículo ----------
function Slot({ kind, onPress }: { kind: Kind; onPress: () => void }) {
  const { vehicle, ui } = useApp();
  const img: SkImage = vehicle[kind];
  const [size, setSize] = useState({ w: 0, h: 0 });
  return (
    <View style={styles.slot}>
      <Pressable onPress={onPress} style={styles.slotBox} accessibilityLabel={`Imagen ${CAPTION[kind]}`}>
        <View style={styles.slotImg} onLayout={(e) => setSize({ w: e.nativeEvent.layout.width, h: e.nativeEvent.layout.height })} pointerEvents="none">
          {size.w > 0 && (
            <Canvas style={StyleSheet.absoluteFill}>
              <SkiaImage image={img} x={0} y={0} width={size.w} height={size.h} fit="contain" />
            </Canvas>
          )}
        </View>
      </Pressable>
      <ui.Note text={CAPTION[kind]} style={{ textAlign: 'center' }} />
    </View>
  );
}

// ---------- color principal ----------
function Swatches() {
  const app = useApp();
  return (
    <View>
      {[0, 1].map((row) => (
        <View key={row} style={styles.swRow}>
          {app.theme.accentPresets.slice(row * 4, row * 4 + 4).map((c) => (
            <Pressable key={c} onPress={() => app.setAccent(c)} accessibilityLabel={`Color ${hex(c)}`}
              style={[styles.swatch, { backgroundColor: css(c), borderWidth: c === app.accent ? 4 : 2, borderColor: css(c === app.accent ? WHITE : rgb(20, 20, 22)) }]} />
          ))}
        </View>
      ))}
    </View>
  );
}

function HexRow() {
  const app = useApp();
  const [text, setText] = useState(hex(app.accent));
  const [shown, setShown] = useState(app.accent);                               // último color válido escrito
  const [lastAccent, setLastAccent] = useState(app.accent);
  if (lastAccent !== app.accent) { setLastAccent(app.accent); setText(hex(app.accent)); setShown(app.accent); }   // al elegir otro color o tema
  const onText = (t: string) => { setText(t); const c = parseHex(t); if (c !== null) setShown(c); };
  const apply = () => {
    Keyboard.dismiss();
    const c = parseHex(text);
    if (c === null) toast('Código no válido. Ejemplo: #3FD0B2', true); else app.setAccent(c);
  };
  return (
    <View>
      <app.ui.Note text="Código de color HEX (por ejemplo #3FD0B2):" />
      <View style={styles.hexRow}>
        <View style={[styles.dot, { backgroundColor: css(shown) }]} />
        <TextInput value={text} onChangeText={onText} maxLength={7} autoCapitalize="characters" autoCorrect={false} spellCheck={false}
          placeholder="#3FD0B2" placeholderTextColor="#888" returnKeyType="done" onSubmitEditing={apply} style={styles.hexField} />
        <app.ui.Button text="Aplicar" filled accent={app.accent} onPress={apply} style={styles.hexBtn} />
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  btn: { height: 50, marginTop: 8 },
  back: { width: 130, height: 50 },
  header: { flexDirection: 'row', alignItems: 'center', marginBottom: 12 },
  row: { flexDirection: 'row' },
  col: { flex: 1 },
  gap: { marginTop: 14 },
  stepRow: { flexDirection: 'row', alignItems: 'center' },
  stepBtn: { width: 72, height: 56 },
  title: { paddingLeft: 16, paddingTop: 0, paddingBottom: 0 },
  stepVal: { flex: 1, textAlign: 'center', paddingTop: 0, paddingBottom: 0 },
  card: { flex: 1, margin: 4, padding: 6, borderRadius: 10, backgroundColor: css(rgb(22, 22, 25)), alignItems: 'center' },
  cardText: { fontSize: 15, fontWeight: 'bold', textAlign: 'center', paddingTop: 8, paddingBottom: 4 },
  slot: { flex: 1, paddingHorizontal: 4 },
  slotBox: { height: 92, padding: 6, borderRadius: 10, borderWidth: 2, borderStyle: 'dashed', borderColor: css(rgb(90, 90, 96)), backgroundColor: css(rgb(22, 22, 25)), overflow: 'hidden' },
  slotImg: { flex: 1 },
  swRow: { flexDirection: 'row', justifyContent: 'center' },
  swatch: { width: 44, height: 44, borderRadius: 22, marginHorizontal: 8, marginVertical: 6 },
  hexRow: { flexDirection: 'row', alignItems: 'center' },
  dot: { width: 40, height: 40, borderRadius: 20, borderWidth: 2, borderColor: '#fff', marginRight: 10 },
  hexField: { flex: 1, color: '#fff', fontSize: 18, backgroundColor: css(rgb(24, 26, 29)), borderWidth: 2, borderColor: css(rgb(90, 92, 98)), borderRadius: 10, paddingHorizontal: 12, paddingVertical: 10 },
  hexBtn: { width: 110, height: 48, marginLeft: 10 },
});
