import { useFonts } from 'expo-font';
import { StatusBar } from 'expo-status-bar';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { BackHandler, Platform, StyleSheet, View } from 'react-native';
import { SafeAreaProvider, useSafeAreaInsets } from 'react-native-safe-area-context';

import { setConfig } from './src/config';
import Core from './modules/inclinometro-core';
import { AppCtx, useEngine, type App, type Route } from './src/AppState';
import { AssetsProvider, useAssets } from './src/gfx/assets';
import { css } from './src/gfx/color';
import { clock } from './src/fmt';
import { BACKGROUND_RECORDING_DISCLOSURE } from './src/privacy';
import { HistoryScreen } from './src/screens/HistoryScreen';
import { HotspotsScreen } from './src/screens/HotspotsScreen';
import { SettingsScreen } from './src/screens/SettingsScreen';
import { TripDetailScreen } from './src/screens/TripDetailScreen';
import { accentOf, byId, setAccentOf, useVehicle } from './src/themes';
import { fromEngine } from './src/themes/types';
import { Dialogs, ToastHost, toast } from './src/ui/dialogs';

const android = Platform.OS === 'android';

/** Inclinómetro OFFROAD. La pantalla principal la dibuja el tema activo; los datos vienen de la parte nativa (Engine).
 *  En Android, al minimizar desde la pantalla principal pasa sola a mini ventana (PiP), si está activado. */
export default function Root() {
  const [fonts] = useFonts({ gunplay: require('./assets/fonts/gunplay.otf') });
  return (
    <SafeAreaProvider>
      <StatusBar style="light" />
      <View style={styles.root}>
        {fonts && (
          <AssetsProvider>
            <Main />
          </AssetsProvider>
        )}
        <ToastHost />
      </View>
    </SafeAreaProvider>
  );
}

function Main() {
  const a = useAssets();
  const insets = useSafeAreaInsets();
  const [themeId, setThemeId] = useState(() => Core.getConfig().theme);
  const theme = byId(themeId);
  const [accent, setAccentState] = useState(() => accentOf(theme));
  const [vehicleVersion, setVehicleVersion] = useState(0);
  const vehicle = useVehicle(theme, a, vehicleVersion);
  const [base, setBase] = useState<'dash' | 'settings'>('dash');
  const [stack, setStack] = useState<Route[]>([]);
  const [, setRebuild] = useState(0);
  const [pip, setPip] = useState(() => Core.isInPip());
  const top = stack[stack.length - 1];
  const onDash = base === 'dash' && !top;
  const engine = useEngine(onDash || pip);
  const state = useMemo(() => fromEngine(engine), [engine]);

  // Sin permiso de ubicación la app funciona igual, pero sin rumbo GPS, altitud ni velocidad: se pide al abrir mientras no esté concedido.
  useEffect(() => { if (!Core.hasLocationPermission()) Core.requestPermissions(true, false); }, []);
  useEffect(() => {
    const subs = [Core.addListener('onPip', (e) => setPip(e.pip)), Core.addListener('onToast', (e) => toast(e.text, e.long))];
    return () => subs.forEach((s) => s.remove());
  }, []);
  // Mini ventana solo desde la pantalla principal; pantalla siempre encendida en la principal y en Ajustes, y en las demás solo grabando.
  useEffect(() => { Core.setPip(onDash, theme.pipAspect[0], theme.pipAspect[1]); }, [onDash, theme]);
  useEffect(() => { Core.keepScreenOn(!top || engine.recording); }, [top, engine.recording]);

  const app: App = useMemo(() => ({
    theme, ui: theme.ui, accent, vehicle, vehicleVersion,
    applyTheme: (id) => { setConfig('theme', id); setThemeId(id); setAccentState(accentOf(byId(id))); },
    setAccent: (c) => { setAccentOf(theme, c); setAccentState(c >>> 0); },
    refreshVehicle: () => setVehicleVersion((v) => v + 1),
    rebuild: () => setRebuild((v) => v + 1),
    showDash: () => setBase('dash'),
    showSettings: () => setBase('settings'),
    push: (r) => setStack((s) => [...s, r]),
    pop: () => setStack((s) => s.slice(0, -1)),
    enterPip: (silent) => { setBase('dash'); setStack([]); setTimeout(() => Core.enterPip(silent), 50); },
  }), [theme, accent, vehicle, vehicleVersion]);

  // Atrás: de Ajustes vuelve a la pantalla principal; de las otras pantallas, a la anterior.
  useEffect(() => {
    const sub = BackHandler.addEventListener('hardwareBackPress', () => {
      if (stack.length) { app.pop(); return true; }
      if (base === 'settings') { app.showDash(); return true; }
      return false;
    });
    return () => sub.remove();
  }, [stack, base, app]);

  // ---------- salidas ----------
  const tripDisclosureAccepted = useRef(false);
  const beginTrip = useCallback(async () => {
    try {
      await Core.startTrip();
    } catch {
      toast('No se pudo iniciar la grabación. Revisa los permisos y el espacio libre e inténtalo con la app abierta.', true);
    }
  }, []);
  const finishTrip = useCallback(async () => {
    try {
      await Core.stopTrip();
      toast('Salida guardada en el histórico', true);
    } catch {
      toast('No se pudo guardar la salida. Revisa el espacio libre del dispositivo e inténtalo de nuevo.', true);
    }
  }, []);
  const toggleTrip = useCallback(async () => {
    const s = Core.getState();
    if (s.recording) {
      if (await Dialogs.confirm('Finalizar salida', `La salida lleva ${clock(Date.now() - s.recordStart)}. Se guardará en el histórico.`, 'Finalizar')) {
        await finishTrip();
      }
      return;
    }
    // También se muestra si el permiso del panel ya estaba concedido: grabar añade uso en segundo plano.
    if (!tripDisclosureAccepted.current) {
      if (!(await Dialogs.confirm('Grabación de una salida', BACKGROUND_RECORDING_DISCLOSURE, 'Continuar'))) return;
      tripDisclosureAccepted.current = true;
    }
    const noLoc = !Core.hasLocationPermission();
    const noNotif = android && !Core.hasNotificationPermission();      // aviso de «grabando» en la barra de notificaciones
    if (!noLoc && !noNotif) { beginTrip(); return; }
    if (noLoc) {
      const yes = await Dialogs.choice('Ubicación para la salida',
        'La ubicación se usa para guardar el recorrido (posición, altitud y velocidad) y verlo después en el mapa con las inclinaciones. ' +
        'La base de datos de salidas se guarda en este dispositivo. Al abrir el mapa, OpenStreetMap recibe las zonas consultadas y tu dirección IP. ' +
        'Sin ubicación puedes grabar inclinaciones sin posición.', 'Permitir', 'Grabar sin GPS');
      if (yes) await Core.requestPermissions(true, noNotif);
      else if (noNotif) await Core.requestPermissions(false, true);
    } else await Core.requestPermissions(false, true);
    beginTrip();
  }, [beginTrip, finishTrip]);

  // Simulador de iOS: -demo settings | history | detail | hotspots | trip-start | trip-stop
  useEffect(() => {
    const d = Core.demoArg();
    if (!d) return;
    const t = setTimeout(async () => {
      if (d === 'settings') app.showSettings();
      else if (d === 'history') app.push({ name: 'history' });
      else if (d === 'hotspots') app.push({ name: 'hotspots' });
      else if (d === 'detail') { const id = Core.trips(false)[0]?.id; if (id !== undefined) { app.push({ name: 'history' }); app.push({ name: 'detail', id }); } }
      else if (d === 'trip-start' && !Core.getState().recording) beginTrip();
      else if (d === 'trip-stop' && Core.getState().recording) await finishTrip();
    }, 1500);
    return () => clearTimeout(t);
  }, []);   // eslint-disable-line react-hooks/exhaustive-deps

  const actions = useMemo(() => ({ onSettings: app.showSettings, onHistory: () => app.push({ name: 'history' }), onTrip: toggleTrip }), [app, toggleTrip]);
  const Dash = theme.Dash;
  const pad = pip ? { paddingTop: 0, paddingBottom: 0, paddingLeft: 0, paddingRight: 0 }
    : { paddingTop: insets.top, paddingBottom: insets.bottom, paddingLeft: insets.left, paddingRight: insets.right };

  return (
    <AppCtx.Provider value={app}>
      <View style={[styles.root, { backgroundColor: css(theme.ui.background) }]}>
        {/* Deja libres la barra de estado y el notch para cualquier tema (en mini ventana no hace falta) */}
        <View style={[styles.root, pad]} pointerEvents={onDash ? 'auto' : 'none'} accessibilityElementsHidden={!onDash}
          importantForAccessibility={onDash ? 'auto' : 'no-hide-descendants'}>
          <View style={styles.root}>
            <Dash state={state} accent={accent} vehicle={vehicle} actions={actions} pip={pip} />
          </View>
        </View>
        {base === 'settings' && <View style={StyleSheet.absoluteFill}><SettingsScreen /></View>}
        {stack.map((r, i) => (
          <View key={i} style={StyleSheet.absoluteFill}>
            {r.name === 'history' && <HistoryScreen visible={i === stack.length - 1} />}
            {r.name === 'detail' && <TripDetailScreen id={r.id} />}
            {r.name === 'hotspots' && <HotspotsScreen />}
          </View>
        ))}
      </View>
    </AppCtx.Provider>
  );
}

const styles = StyleSheet.create({ root: { flex: 1, backgroundColor: '#101012' } });
