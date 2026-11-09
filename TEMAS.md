# Cómo crear un tema nuevo

Un tema cambia **solo el aspecto** de la app. La lectura de sensores, la calibración, los máximos, los avisos y las salidas
son comunes y no hay que tocarlos.

Cada tema vive en su propio paquete: `app/src/main/java/com/example/inclinometro/themes/<tema>/`.
Los dos que trae la app sirven de ejemplo:

| Tema | Carpeta | Pantalla principal |
|---|---|---|
| Industrial | `themes/industrial/` | Una sola vista (`DashView`) que lo dibuja todo en un `Canvas` |
| Topográfico | `themes/topo/` | Un `LinearLayout` con tres vistas (`GaugeView`, `CompassView`) y botones |

## Pasos

1. **Crea el paquete** `themes/mitema/` con un `object MiTema : Theme` (la interfaz está en `Theme.kt`). Define:
   - `id`: identificador corto y fijo (`"mitema"`). Las preferencias del usuario se guardan con él: no lo cambies una vez publicado.
   - `name`: nombre que verá el usuario en el selector.
   - `defaultAccent` y `accentPresets` (8 colores para el selector de Ajustes).
   - `pipAspect`: proporción de la mini ventana (entre 1:2,39 y 2,39:1).
   - `caution` y `danger`: colores de aviso. Ver «Colores de aviso» más abajo.
   - `ui`: piezas de interfaz (`ThemeUi`) para Ajustes y el histórico: fondo, títulos, notas, botones y bloques.
   - `vehicleDefault(kind)` (opcional): imágenes del vehículo propias del tema, si no quieres las de serie.
   - `createDash(ctx, actions)`: devuelve la pantalla principal.

2. **Haz la pantalla principal** implementando `Dash`:
   - `update(s: DashState)` se llama en cada lectura del sensor (unas 50 veces por segundo). Solo guarda los valores y llama a `invalidate()`.
     `DashState` trae los ángulos, rumbo, altitud, velocidad, nivel de aviso de cada eje y máximos registrados.
   - `setVehicle(side, rear, top)` recibe las imágenes del vehículo ya preparadas.
   - `accent`: color principal elegido por el usuario.
   - `setPip(pip)`: oculta los botones en la mini ventana.
   - Los botones de la pantalla llaman a las funciones de `actions`.
   - Para la rueda dentada de Ajustes usa los ayudantes comunes de `Chrome.kt` (`Chrome.drawGear` si dibujas en `Canvas`,
     `IconButtonView` si usas vistas). Así se comporta igual en todos los temas: esquina superior derecha y área táctil mínima de 48 dp.
     Los márgenes seguros (notch, barra de estado) ya los aplica la app alrededor de tu pantalla.

3. **Regístralo** añadiéndolo a la lista `Themes.all` en `Theme.kt`. No hay que tocar nada más: el selector de Ajustes genera
   la miniatura automáticamente dibujando tu pantalla principal con datos de ejemplo.

4. **Recursos**: pon las imágenes del tema en `res/drawable-nodpi/` con un prefijo propio (`mitema_…`) y las fuentes en `assets/fonts/`.

## Colores de aviso

La app avisa en dos niveles, en todos los temas (`DashState.pitchLevel` / `rollLevel`, con valores de `Alerts`):

- **Precaución** (por defecto, al 80 % del límite del vehículo): el reloj afectado se marca con `caution` (ámbar) y el texto «PRECAUCIÓN».
- **Peligro** (al llegar al límite): el fondo de la pantalla pasa a `danger` (rojo) y aparece el texto «PELIGRO».

La histéresis (no oscilar cuando el ángulo ronda el umbral) ya la aplica `Alerts`: el tema solo dibuja el nivel que recibe.

Cada tema elige sus tonos, pero:
- `caution` debe ser un ámbar o amarillo claro y `danger` un rojo, para que el significado sea el de siempre.
- Además del color, el aviso siempre lleva texto. Así se entiende aunque el color principal del usuario se parezca al ámbar (naranja, amarillo).
- Los textos y relojes deben seguir leyéndose sobre el fondo `danger`: usa paneles oscuros o texto blanco encima.

## Comprobar el tema

- Ajustes → Tema: debe aparecer la miniatura y cambiar al momento sin perder la calibración.
- Gira la pantalla (vertical y horizontal) y abre la mini ventana.
- Simula inclinaciones en el emulador (ver README) para comprobar precaución y peligro.
