import Core, { type Config } from '../modules/inclinometro-core';

/** Guarda un ajuste (la parte nativa los tiene separados por tipo). */
export function setConfig<K extends keyof Config>(key: K, value: Config[K]) {
  if (typeof value === 'boolean') Core.setBool(key, value);
  else if (typeof value === 'number') Core.setNumber(key, value);
  else Core.setString(key, String(value));
}
