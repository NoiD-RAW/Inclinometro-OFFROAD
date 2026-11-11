import { NativeModule, requireNativeModule } from 'expo';

import { Config, EngineState, Hotspot, InclinometroCoreModuleEvents, Samples, Trip } from './InclinometroCore.types';

/** Parte nativa: sensores, GPS, avisos, salidas, base de datos, imágenes del vehículo y (en Android) mini ventana y diálogos. */
declare class InclinometroCoreModule extends NativeModule<InclinometroCoreModuleEvents> {
  readonly platform: 'android' | 'ios';
  readonly decimalSeparator: string;

  getState(): EngineState;
  getConfig(): Config;
  setBool(key: string, value: boolean): void;
  setNumber(key: string, value: number): void;
  setString(key: string, value: string): void;
  getAccent(theme: string, def: number): number;
  setAccent(theme: string, color: number): void;
  calibrate(): Promise<void>;
  resetCal(): Promise<void>;
  resetMaxima(): Promise<void>;

  canVibrate(): boolean;
  /** Vibra y/o pita según los ajustes. Devuelve false si no hay nada activado que se pueda probar. */
  testAlert(): Promise<boolean>;

  hasLocationPermission(): boolean;
  hasNotificationPermission(): boolean;
  /** Devuelve si hay permiso de ubicación al terminar. */
  requestPermissions(location: boolean, notifications: boolean): Promise<boolean>;

  startTrip(): Promise<void>;
  stopTrip(): Promise<void>;
  trips(byAngle: boolean): Trip[];
  trip(id: number): Trip | null;
  samples(id: number): Samples;
  hotspots(): Hotspot[];
  renameTrip(id: number, name: string): void;
  deleteTrip(id: number): void;
  deleteOlderThan(before: number, keep: number): number;
  dbSize(): number;
  exportTrip(id: number, ext: 'csv' | 'gpx'): Promise<void>;

  /** Imagen subida por el usuario (file://…), o null si se usa la del tema. */
  vehicleUri(theme: string, kind: string): string | null;
  resetVehicle(theme: string): void;
  /** Devuelve cuántas imágenes se han guardado. */
  pickVehicle(theme: string, kinds: string[]): Promise<number>;
  /** Solo Android. */
  loadFolder(theme: string): Promise<number>;

  /** Solo Android. */
  setPip(allowed: boolean, w: number, h: number): void;
  enterPip(silent: boolean): Promise<void>;
  isInPip(): boolean;

  /** Solo en el simulador de iOS (argumento -demo); en el resto, null. */
  demoArg(): string | null;
  keepScreenOn(on: boolean): void;
  /** Solo Android (en iOS el aviso lo dibuja la interfaz). */
  toast(text: string, long: boolean): void;
  confirm(title: string, message: string, ok: string): Promise<boolean>;
  choice(title: string, message: string, yes: string, no: string): Promise<boolean>;
  pick(title: string, items: string[]): Promise<number>;
  prompt(title: string, initial: string, ok: string): Promise<string | null>;
}

export default requireNativeModule<InclinometroCoreModule>('InclinometroCore');
