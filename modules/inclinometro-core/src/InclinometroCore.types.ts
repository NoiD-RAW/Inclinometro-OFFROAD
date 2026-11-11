export type InclinometroCoreModuleEvents = {
  /** Android: la app entra o sale de la mini ventana (PiP). */
  onPip: (params: { pip: boolean }) => void;
  /** iOS: aviso breve que la interfaz debe mostrar (en Android la parte nativa usa Toast). */
  onToast: (params: { text: string; long: boolean }) => void;
};

/** Lectura actual de sensores y GPS, nivel de aviso y salida en curso. */
export type EngineState = {
  roll: number;
  pitch: number;
  heading: number | null;
  altitude: number | null;
  kmh: number;
  /** Hay posición GPS reciente (velocidad válida). */
  gps: boolean;
  pitchLevel: number;
  rollLevel: number;
  /** Máximos en grados positivos: morro arriba, morro abajo, alabeo izquierda, alabeo derecha. */
  max: number[];
  recording: boolean;
  recordStart: number;
  activeTrip: number;
};

export type Config = {
  flat: boolean;
  rot: number;
  invertPitch: boolean;
  offRoll: number;
  offPitch: number;
  limitPitch: number;
  limitRoll: number;
  cautionPct: number;
  vibrate: boolean;
  beep: boolean;
  maxWindowMs: number;
  sampleMs: number;
  autoPip: boolean;
  theme: string;
};

export type Trip = {
  id: number;
  name: string;
  start: number;
  end: number;
  sampleMs: number;
  max: number[];
  danger: number;
  distance: number;
  limitPitch: number;
  limitRoll: number;
  cautionPct: number;
  hasGps: boolean;
  samples: number;
};

/** Muestras de una salida en columnas. Sin GPS, lat/lon son null. */
export type Samples = {
  t: number[];
  pitch: number[];
  roll: number[];
  lat: (number | null)[];
  lon: (number | null)[];
  alt: (number | null)[];
  kmh: (number | null)[];
};

export type Hotspot = {
  trip: number;
  tripName: string;
  t: number;
  lat: number;
  lon: number;
  pitch: number;
  roll: number;
  ratio: number;
};
