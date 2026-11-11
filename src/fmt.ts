import Core from '../modules/inclinometro-core';

/** Formatos comunes. Los textos van en castellano (Locale es-ES), como en la app de Android. */
const DAYS = ['dom.', 'lun.', 'mar.', 'mié.', 'jue.', 'vie.', 'sáb.'];
const p2 = (n: number) => String(n).padStart(2, '0');
/** Separador decimal del teléfono: lo usan los textos que en Android dependían del idioma del sistema. */
const sep = () => Core.decimalSeparator;

/** "%.Nf" con separador [s]. */
export function fixed(v: number, digits: number, s = ','): string {
  const t = v.toFixed(digits);
  return (Object.is(v, -0) || (v < 0 && !t.startsWith('-')) ? '-' + t : t).replace('.', s);
}
/** "%+.1f" con el separador del teléfono. */
export const signed1 = (v: number) => { const t = fixed(v, 1, sep()); return t.startsWith('-') ? t : '+' + t; };
/** Math.round de Kotlin (roundToInt): redondea .5 hacia arriba. */
export const round = (v: number) => Math.floor(v + 0.5);

export const Fmt = {
  date(ms: number) {
    const d = new Date(ms);
    const day = DAYS[d.getDay()];
    return `${day.charAt(0).toUpperCase()}${day.slice(1)} ${p2(d.getDate())}/${p2(d.getMonth() + 1)}/${d.getFullYear()} ${p2(d.getHours())}:${p2(d.getMinutes())}`;
  },
  time(ms: number) { const d = new Date(ms); return `${p2(d.getHours())}:${p2(d.getMinutes())}:${p2(d.getSeconds())}`; },
  km(m: number) { return m < 1000 ? `${round(m)} m` : `${fixed(m / 1000, 1)} km`; },
  mb(b: number) { return `${fixed(b / 1048576, 1)} MB`; },
  deg(v: number) { return `${round(v)}°`; },
  /** "dd/MM/yyyy HH:mm" para el nombre de una salida nueva. */
  short(ms: number) { const d = new Date(ms); return `${p2(d.getDate())}/${p2(d.getMonth() + 1)}/${d.getFullYear()} ${p2(d.getHours())}:${p2(d.getMinutes())}`; },
};

/** "1:02:03" o "12:03". */
export function clock(ms: number) {
  const s = Math.floor(ms / 1000);
  return s >= 3600 ? `${Math.floor(s / 3600)}:${p2(Math.floor(s / 60) % 60)}:${p2(s % 60)}` : `${p2(Math.floor(s / 60))}:${p2(s % 60)}`;
}

/** Miles con punto, como String.format(Locale("es","ES"), "%,d"). */
export function thousands(n: number) {
  const neg = n < 0; const t = String(Math.abs(n)).replace(/\B(?=(\d{3})+(?!\d))/g, '.');
  return neg ? '-' + t : t;
}

/** Intervalo entre muestras: "segundo", "0,5 s", "2 s"… (en Android: "%.1f s" con el idioma del sistema). */
export function sampleLabel(ms: number) {
  if (ms === 1000) return 'segundo';
  const s = sep();
  return `${fixed(ms / 1000, 1, s)} s`.replace('.0 ', ' ').replace('.', ',');
}

/** Filtro de baches: "sin filtro", "0,25 s", "0,50 s", "1,0 s", "2,0 s" (en Android: "%.2g s"). */
export function windowLabel(ms: number) {
  if (ms === 0) return 'sin filtro';
  const v = ms / 1000;
  const digits = Math.max(0, 1 - Math.floor(Math.log10(Math.abs(v))));
  return `${fixed(v, digits, sep())} s`.replace('.', ',');
}
