import { Canvas, Picture, Skia, type SkCanvas } from '@shopify/react-native-skia';
import { useEffect, useMemo, useState, type ReactNode } from 'react';
import { PixelRatio, StyleSheet, View, type LayoutChangeEvent, type StyleProp, type ViewProps, type ViewStyle } from 'react-native';

export type Draw = (c: SkCanvas, w: number, h: number, density: number) => void;

/** Lienzo que dibuja en píxeles físicos, como un View.onDraw de Android: [draw] recibe ancho y alto en px y la densidad.
 *  [deps] decide cuándo se vuelve a dibujar. */
export function SkiaView({ draw, deps, onDrawn, style, children, ...rest }: { draw: Draw; deps: unknown[]; onDrawn?: () => void; style?: StyleProp<ViewStyle>; children?: ReactNode } & ViewProps) {
  const [size, setSize] = useState({ w: 0, h: 0 });
  const d = PixelRatio.get();
  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (width !== size.w || height !== size.h) setSize({ w: width, h: height });
  };
  const picture = useMemo(() => {
    if (size.w <= 0 || size.h <= 0) return null;
    const rec = Skia.PictureRecorder();
    const c = rec.beginRecording(Skia.XYWHRect(0, 0, size.w, size.h));
    c.scale(1 / d, 1 / d);
    draw(c, size.w * d, size.h * d, d);
    return rec.finishRecordingAsPicture();
    // [deps] lo da quien usa el lienzo: es lo que cambia el dibujo (una lista literal no sirve para un componente genérico)
    // eslint-disable-next-line react-hooks/exhaustive-deps, react-hooks/use-memo
  }, [size.w, size.h, d, ...deps]);
  // Permite sincronizar controles nativos con el dibujo después de confirmar el render.
  useEffect(() => { if (picture) onDrawn?.(); }, [picture, onDrawn]);
  return (
    <View style={style} onLayout={onLayout} {...rest}>
      {picture && (
        <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
          <Picture picture={picture} />
        </Canvas>
      )}
      {children}
    </View>
  );
}
