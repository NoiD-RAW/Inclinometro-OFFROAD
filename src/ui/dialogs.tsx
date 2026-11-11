import { useEffect, useState } from 'react';
import { ActionSheetIOS, Alert, Animated, Platform, StyleSheet, Text } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import Core from '../../modules/inclinometro-core';

/** Diálogos comunes. En Android son los mismos de antes (nativos, con botones grandes); en iOS, los del sistema.
 *  Las acciones que no se pueden deshacer siempre piden confirmación. */
const android = Platform.OS === 'android';

export const Dialogs = {
  confirm(title: string, message: string, ok = 'Confirmar'): Promise<boolean> {
    if (android) return Core.confirm(title, message, ok);
    return new Promise((res) => Alert.alert(title, message, [
      { text: 'Cancelar', style: 'cancel', onPress: () => res(false) },
      { text: ok, style: /^(Borrar|Restablecer|Resetear|Finalizar)/.test(ok) ? 'destructive' : 'default', onPress: () => res(true) },
    ], { cancelable: true, onDismiss: () => res(false) }));
  },

  /** Dos opciones (por ejemplo «Permitir» / «Grabar sin GPS»): true si se elige la primera. */
  choice(title: string, message: string, yes: string, no: string): Promise<boolean> {
    if (android) return Core.choice(title, message, yes, no);
    return new Promise((res) => Alert.alert(title, message, [
      { text: no, onPress: () => res(false) },
      { text: yes, style: 'default', onPress: () => res(true) },
    ], { cancelable: false }));
  },

  /** Lista de opciones: devuelve la posición elegida o -1. */
  pick(title: string, items: string[]): Promise<number> {
    if (android) return Core.pick(title, items);
    return new Promise((res) => ActionSheetIOS.showActionSheetWithOptions(
      { title, options: [...items, 'Cancelar'], cancelButtonIndex: items.length },
      (i) => res(i === items.length ? -1 : i)));
  },

  /** Pide un texto (por ejemplo, el nombre de una salida): devuelve el texto o null. */
  prompt(title: string, initial: string, ok = 'Guardar'): Promise<string | null> {
    if (android) return Core.prompt(title, initial, ok);
    return new Promise((res) => Alert.prompt(title, undefined, [
      { text: 'Cancelar', style: 'cancel', onPress: () => res(null) },
      { text: ok, onPress: (v?: string) => { const t = (v ?? '').trim(); res(t ? t : null); } },
    ], 'plain-text', initial));
  },
};

// ---------- avisos breves ----------
type ToastMsg = { text: string; long: boolean; key: number };
let show: ((m: ToastMsg) => void) | null = null;
let seq = 0;

/** Aviso breve: Toast de Android; en iOS, una pastilla abajo que desaparece sola. */
export function toast(text: string, long = false) {
  if (android) Core.toast(text, long);
  else show?.({ text, long, key: ++seq });
}

/** Contenedor de los avisos en iOS (va una vez, encima de todo). */
export function ToastHost() {
  const [msg, setMsg] = useState<ToastMsg | null>(null);
  const [op] = useState(() => new Animated.Value(0));
  const insets = useSafeAreaInsets();
  useEffect(() => { show = setMsg; return () => { show = null; }; }, []);
  useEffect(() => {
    if (!msg) return;
    op.setValue(0);
    const a = Animated.sequence([
      Animated.timing(op, { toValue: 1, duration: 150, useNativeDriver: true }),
      Animated.delay(msg.long ? 3500 : 2000),
      Animated.timing(op, { toValue: 0, duration: 250, useNativeDriver: true }),
    ]);
    a.start(({ finished }) => { if (finished) setMsg(null); });
    return () => a.stop();
  }, [msg, op]);
  if (!msg) return null;
  return (
    <Animated.View pointerEvents="none" style={[styles.toast, { opacity: op, bottom: insets.bottom + 64 }]}>
      <Text style={styles.text}>{msg.text}</Text>
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  toast: { position: 'absolute', alignSelf: 'center', maxWidth: '86%', backgroundColor: 'rgba(48,48,52,0.96)', borderRadius: 22, paddingHorizontal: 18, paddingVertical: 11 },
  text: { color: '#fff', fontSize: 15, textAlign: 'center' },
});
