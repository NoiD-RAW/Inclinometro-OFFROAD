# Inclinometro-OFFROAD
Inclinómetro para 4x4: muestra en tiempo real el cabeceo (delante/detrás) y el alabeo (lateral) del vehículo con los sensores del móvil o la tablet, además de brújula, altitud y velocidad por GPS. Una sola app para Android y para iPhone/iPad.

Dos temas a elegir en Ajustes → Tema (se cambia al momento, sin perder la calibración):
- **Industrial**: acero cepillado, tornillos y biseles.
- **Topográfico**: tres relojes sobre curvas de nivel.

Se puede modificar el vehículo con tan solo 2/3 fotos del mismo iguales a la original de la app, con cualquier IA se pueden subir las fotos de tu vehículo y pedirle que te elimine el fondo y/o que le de un estilo concreto. En los ajustes de la app se pueden modificar los colores y el montaje del sensor, por si la tablet/smartphone va en vertical u horizontal. Cada tema guarda sus propias imágenes y su color.

La versión anterior, solo para Android y escrita en Kotlin, se conserva en la rama `KOTLIN`.

## Funciones
- **Calibración** (Ajustes → Calibración): con el vehículo en llano, «Calibrar a 0°» (pide confirmación). «Restablecer calibración» vuelve al cero de fábrica. No hay botón de calibrar en la pantalla principal para no tocarlo en marcha.
- **Máximos**: se registran el cabeceo máximo (morro arriba/abajo) y el alabeo máximo (izquierda/derecha). Un ángulo solo cuenta si se mantiene el tiempo del «filtro de baches» (0,5 s por defecto). Se resetean en Ajustes.
- **Límites y avisos**: en Ajustes se indica el ángulo límite del vehículo. Al 80 % (configurable) el reloj se marca en ámbar con «PRECAUCIÓN»; al llegar al límite el fondo pasa a rojo con «PELIGRO». Vibración y pitido opcionales («Probar aviso» para comprobarlos). Con la app en segundo plano solo vibra.
- **Salidas**: botón «Iniciar salida» en la pantalla principal (punto rojo y tiempo mientras graba; pide confirmación al finalizar). Guarda cabeceo, alabeo y, con GPS, posición, altitud y velocidad. Sigue grabando aunque se cambie a otra app (por ejemplo, la música).
- **Histórico** (icono de reloj junto a la rueda de Ajustes): lista de salidas con distancia, máximos y veces en peligro; renombrar, borrar y exportar a CSV o GPX. El detalle muestra el gráfico enlazado con el mapa del recorrido, coloreado por inclinación. El «Mapa de puntos críticos» reúne los lugares más inclinados de todas las salidas.
- **Mini ventana** (solo Android): al pulsar Inicio desde la pantalla principal, la app pasa a una ventana pequeña con los relojes.
- Los mapas son de OpenStreetMap (gratis, sin clave). Sin cobertura, la lista, los datos y el gráfico funcionan igual; el mapa muestra lo que tenga en caché.

### Diferencias entre Android e iOS
- La mini ventana y «Cargar desde carpeta de la app» solo existen en Android.
- Los iPad no vibran: Ajustes lo indica y propone el pitido.
- En iOS, con el móvil en silencio, la vibración depende de los ajustes de vibración del propio iPhone (en Android va marcada como alarma).
- En iOS la grabación en segundo plano necesita el permiso de ubicación (iOS muestra el indicador azul). Sin permiso, la salida se pausa al cambiar de app y sigue al volver.

## Cómo está hecha
- **Interfaz** (común a las dos plataformas), en TypeScript con React Native y Expo: `App.tsx` y `src/`.
  - `src/themes/industrial` y `src/themes/topo`: los dos temas. Los relojes se dibujan con Skia (en píxeles físicos, con las medidas de la versión Kotlin).
  - `src/screens`: Ajustes, histórico, detalle de una salida y mapa de puntos críticos.
  - `src/ui`: gráfico, mapas (MapLibre con teselas de OpenStreetMap) y diálogos.
- **Parte nativa** (`modules/inclinometro-core`): sensores, GPS, brújula, avisos, grabación de salidas, base de datos, exportación e imágenes del vehículo.
  - Android: el código Kotlin de la versión anterior, casi sin cambios. Usa las mismas preferencias, base de datos e imágenes, así que al actualizar desde la 2.1 se conservan la calibración, los máximos, las salidas y el vehículo.
  - iOS: la misma lógica en Swift (CoreMotion, CoreLocation, SQLite).
- `app.json` y `plugins/withInclinometro.js`: nombre, identificador (`dev.arklay.inclinometro`), iconos, permisos, la mini ventana y la firma de Android.
- Las carpetas `android/` e `ios/` no se guardan en el repositorio: las genera `npx expo prebuild`.

Para crear un tema nuevo, ver [TEMAS.md](TEMAS.md).

## Compilar
Requisitos: Node 20 o superior. Para iOS, un Mac con Xcode 26 y CocoaPods. Para Android, el SDK de Android y JDK 17.

```
npm install
npx expo prebuild          # genera android/ e ios/
npx expo run:ios           # simulador o iPhone conectado
npx expo run:android       # emulador o móvil conectado
```

Comprobar el código: `npx tsc --noEmit` y `npx expo lint`.

## Publicar una versión
La versión se cambia en `app.json`: `version`, `android.versionCode` e `ios.buildNumber` (cada subida a las tiendas necesita un número mayor que la anterior).

### Android y Google Play
Al subir una etiqueta `v*` (por ejemplo `git tag v2.2.0 && git push origin v2.2.0`), GitHub Actions compila el APK y publica la release. Si la etiqueta lleva guion (`v2.2.0-beta.1`), se publica como versión preliminar. En cada push a `main` también se compila un APK de prueba (en «Actions»).

Para que las actualizaciones se instalen encima de la versión anterior, el APK debe ir siempre firmado con la misma clave. Añade estos secretos en el repositorio (Settings → Secrets → Actions): `KEYSTORE_BASE64` (el `.jks` en base64), `KEYSTORE_PASSWORD`, `KEY_ALIAS` y `KEY_PASSWORD`. Con ellos también se genera el `.aab`, que es el formato que pide Google Play. Sin ellos se publica un APK firmado con la clave de depuración.

En local, con las mismas variables de entorno (`KEYSTORE_FILE` con la ruta del `.jks`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`):
```
npx expo prebuild --platform android
cd android && ./gradlew bundleRelease     # android/app/build/outputs/bundle/release/app-release.aab
```

El identificador de la app es `dev.arklay.inclinometro` (hasta la 2.0 era `com.example.inclinometro`). Android las trata como apps distintas: la nueva no se instala encima de los APK antiguos y no hereda su calibración ni sus salidas. Conviene desinstalar la antigua. La app apunta a Android 16 (API 36), el mínimo que exige Google Play para apps nuevas.

### iPhone y App Store
Con una cuenta de desarrollador de Apple:
1. Registrar el identificador `dev.arklay.inclinometro` y crear la app en App Store Connect.
2. `npx expo prebuild --platform ios`, abrir `ios/InclinometroOFFROAD.xcworkspace` en Xcode y elegir el equipo en «Signing & Capabilities».
3. *Product → Archive* y subirla desde el organizador (primero a TestFlight para probarla en un iPhone).

Textos e imágenes para las tiendas: `store/ficha.md` (comprobar con `python3 store/comprobar_ficha.py`), `store/google-play/` y `store/app-store/`.

## Cómo probar sin salir con el coche
### Android (emulador)
- **Inclinación**: en «Extended controls → Virtual sensors» se gira el dispositivo. Por terminal, `adb emu sensor set acceleration X:Y:Z` (m/s²). Con el montaje vertical, el alabeo es `atan2(X, Y)`: por ejemplo, `5.8:7.9:0.5` da unos 36° (peligro con el límite de 30°) y `4.3:8.8:0.5` unos 26° (precaución).
- **GPS**: en «Extended controls → Location» se puede cargar un GPX o KML y reproducirlo como ruta. Sin interfaz, `adb emu geo fix lon lat alt`; repítelo cambiando la posición para simular un recorrido.
- **Segundo plano**: con una salida en curso, pulsa Inicio y abre otra app. La notificación «Grabando salida» indica que sigue grabando.

### iOS (simulador)
El simulador no tiene sensores de movimiento. Solo en las compilaciones para el simulador, la app acepta estos argumentos de arranque:
```
xcrun simctl launch booted dev.arklay.inclinometro -simGravity "4.3,8.8,0.5" -simHeading 18 -simAltitude 900
```
- `-simGravity "x,y,z"`: gravedad en m/s², con los mismos ejes que el emulador de Android.
- `-simHeading` y `-simAltitude`: rumbo de la brújula y altitud.
- `-demo settings|history|detail|hotspots|trip-start|trip-stop`: abre esa pantalla o inicia/finaliza una salida (el simulador no deja automatizar toques).
- GPS: `xcrun simctl location booted set 42.1,1.8` o una ruta con `xcrun simctl location booted start --speed=12 lat,lon lat,lon …`.

Con la app abierta, la inclinación se cambia con `xcrun simctl spawn booted defaults write dev.arklay.inclinometro simGravity "5.8,7.9,0.5"` (si se arrancó sin `-simGravity`).

## Limitaciones conocidas
- En Android, con la app en segundo plano, la grabación continúa gracias a un servicio en primer plano. Algunas unidades con «ahorro de batería» agresivo pueden cerrarlo: si pasa, la app retoma la misma salida al volver a abrirla. Conviene excluir la app de la optimización de batería.
- La pantalla se mantiene encendida mientras la app está visible. Si otra app ocupa la pantalla, el apagado depende de esa app.
- La altitud del GPS es poco precisa (±10–20 m).
- Una salida ocupa unos 0,3 MB por hora a 1 muestra por segundo. El histórico muestra el total y permite borrar las salidas antiguas.

NO está 100% testeada por lo que es posible que tenga fallos, se pueden comentar y dar opiniones con respeto.

NO AUTORIZO LA COPIA PARCIAL O TOTAL DEL DISEÑO DE LAS APLICACIONES NI SU COMERCIALIZACIÓN.
