import type { SkImage } from '@shopify/react-native-skia';
import type { ComponentType, ReactNode } from 'react';
import type { StyleProp, TextStyle, ViewStyle } from 'react-native';

import type { EngineState } from '../../modules/inclinometro-core';

export const OK = 0, CAUTION = 1, DANGER = 2;

/** Vistas del vehículo: "side" (lateral, morro a la izquierda), "rear" (trasera o frontal) y "top" (superior, morro arriba). */
export type Kind = 'side' | 'rear' | 'top';
export const KINDS: Kind[] = ['side', 'rear', 'top'];
export type Vehicle = { side: SkImage; rear: SkImage; top: SkImage };

/** Datos que muestra la pantalla principal, iguales para todos los temas. */
export type DashState = {
  roll: number; pitch: number;
  heading: number | null; altitude: number | null;
  kmh: number;
  /** Hay posición GPS reciente (velocidad válida). */
  gps: boolean;
  /** Nivel de aviso de cada eje: OK, CAUTION o DANGER. */
  pitchLevel: number; rollLevel: number;
  /** Máximos registrados en grados positivos: morro arriba, morro abajo, alabeo izquierda, alabeo derecha. */
  max: number[];
  /** Hay una salida grabándose y cuánto lleva (para mostrar punto rojo y tiempo). */
  recording: boolean; elapsed: number;
};
export const level = (s: DashState) => Math.max(s.pitchLevel, s.rollLevel);

export function fromEngine(e: EngineState, now = Date.now()): DashState {
  return {
    roll: e.roll, pitch: e.pitch, heading: e.heading, altitude: e.altitude, kmh: e.kmh, gps: e.gps,
    pitchLevel: e.pitchLevel, rollLevel: e.rollLevel, max: e.max,
    recording: e.recording, elapsed: e.recording ? now - e.recordStart : 0,
  };
}

/** Valores de ejemplo para las miniaturas del selector de tema. */
export const demoState = (): DashState => ({
  roll: 12, pitch: -8, heading: 45, altitude: 852, kmh: 23, gps: true, pitchLevel: OK, rollLevel: OK,
  max: [14, 9, 18, 11], recording: false, elapsed: 0,
});

/** Lo que pasa al tocar los controles de la pantalla principal. */
export type DashActions = { onSettings: () => void; onHistory: () => void; onTrip: () => void };

/** La pantalla principal de un tema. Solo dibuja: los datos le llegan en [state]. */
export type DashProps = {
  state: DashState;
  accent: number;
  vehicle: Vehicle;
  actions: DashActions;
  /** En la mini ventana (PiP): conviene ocultar los botones. */
  pip: boolean;
};

/** Piezas de interfaz con el estilo del tema, para Ajustes e histórico. */
export type ThemeUi = {
  background: number;                // color de fondo de las pantallas
  text: number;                      // color de texto normal
  muted: number;                     // color de texto secundario
  /** Fondo de una pantalla completa (fijo, no se desplaza con el contenido). */
  Background: ComponentType<{ style?: StyleProp<ViewStyle> }>;
  Label: ComponentType<{ text: string; size: number; color: number; style?: StyleProp<TextStyle>; lines?: number }>;
  Note: ComponentType<{ text: string; color?: number; style?: StyleProp<TextStyle> }>;
  Button: ComponentType<{ text: string; filled: boolean; accent: number; onPress: () => void; style?: StyleProp<ViewStyle>; textSize?: number; label?: string }>;
  /** Un bloque de Ajustes con su título. */
  Section: ComponentType<{ title: string; accent: number; children?: ReactNode; style?: StyleProp<ViewStyle> }>;
};

/** Un tema define todo el aspecto de la app (pantalla principal, Ajustes, histórico) sin tocar la lógica.
 *  Para añadir uno nuevo: crea una carpeta en src/themes/, define este objeto y añádelo a THEMES. Ver TEMAS.md. */
export type Theme = {
  id: string;                        // se usa en las claves guardadas: no cambiarlo una vez publicado
  name: string;                      // nombre visible en el selector de tema
  defaultAccent: number;
  accentPresets: number[];           // 8 colores para el selector de Ajustes
  pipAspect: [number, number];       // proporción de la mini ventana
  caution: number;                   // aviso de precaución (ámbar)
  danger: number;                    // aviso de peligro (rojo): fondo de la app en zona de peligro
  ui: ThemeUi;
  /** Vistas del vehículo que usa la pantalla del tema: solo esas se piden en Ajustes. */
  vehicleKinds: Kind[];
  Dash: ComponentType<DashProps>;
};
