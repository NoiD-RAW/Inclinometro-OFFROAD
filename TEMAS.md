# Cómo crear un tema nuevo

Un tema cambia **solo el aspecto** de la app. La lectura de sensores, la calibración, los máximos, los avisos y las salidas
son comunes y no hay que tocarlos. Un tema funciona igual en Android y en iOS.

Cada tema vive en su propia carpeta: `src/themes/<tema>/`. Los dos que trae la app sirven de ejemplo:

| Tema | Carpeta | Pantalla principal |
|---|---|---|
| Industrial | `src/themes/industrial/` | Un solo lienzo de Skia que lo dibuja todo (`drawDash.ts`) |
| Topográfico | `src/themes/topo/` | Vistas de React Native con tres lienzos (reloj y brújula en `draw.ts`) y botones |

## Pasos

1. **Crea la carpeta** `src/themes/mitema/` con un objeto `Theme` (el tipo está en `src/themes/types.ts`). Define:
   - `id`: identificador corto y fijo (`"mitema"`). Las preferencias del usuario se guardan con él: no lo cambies una vez publicado.
   - `name`: nombre que verá el usuario en el selector.
   - `defaultAccent` y `accentPresets` (8 colores para el selector de Ajustes), como enteros `0xAARRGGBB` (`rgb()` de `src/gfx/color.ts`).
   - `pipAspect`: proporción de la mini ventana de Android (entre 1:2,39 y 2,39:1).
   - `caution` y `danger`: colores de aviso. El ámbar marca el reloj en precaución; el rojo es el fondo de la pantalla en peligro.
   - `ui`: piezas de interfaz (`ThemeUi`) para Ajustes y el histórico: fondo, títulos, notas, botones y bloques.
   - `vehicleKinds`: vistas del vehículo que usa la pantalla (`side`, `rear`, `top`); solo esas se piden en Ajustes.
   - `Dash`: el componente de la pantalla principal.

2. **Haz la pantalla principal** (`Dash`). Recibe:
   - `state` (`DashState`): ángulos, rumbo, altitud, velocidad, nivel de aviso de cada eje, máximos y si hay una salida grabándose. Llega unas 60 veces por segundo mientras cambia.
   - `vehicle`: las imágenes del vehículo ya preparadas (imágenes de Skia).
   - `accent`: color principal elegido por el usuario.
   - `actions`: `onSettings`, `onHistory` y `onTrip` para los botones.
   - `pip`: está en la mini ventana; conviene ocultar los botones.

   Para dibujar como en un `Canvas` usa `SkiaView` (`src/gfx/SkiaView.tsx`): da el lienzo en píxeles físicos.
   Para la rueda de Ajustes y el historial usa `Chrome.drawGear` y `Chrome.drawHistory` (`src/gfx/chrome.ts`), con un área táctil de al menos 48 dp.
   Los márgenes seguros (notch, barra de estado) ya los aplica la app alrededor de tu pantalla.

3. **Regístralo** añadiéndolo a la lista `THEMES` en `src/themes/index.ts`. No hay que tocar nada más: el selector de Ajustes genera
   la miniatura dibujando tu pantalla principal con datos de ejemplo.

4. **Recursos**: pon las imágenes en `assets/img/` con un prefijo propio (`mitema_…`) y cárgalas en `src/gfx/assets.tsx`; las fuentes, en `assets/fonts/`.
