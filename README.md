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
Requisitos: Node 22.13 o superior y npm. Para compilar para las tiendas se usa Expo Build (EAS), sin necesidad de instalar Xcode ni Android Studio. El desarrollo nativo local requiere el SDK de Android y un JDK compatible, o un Mac con Xcode 26.4 o superior y CocoaPods para iOS.

```
npm ci
npx expo prebuild          # genera android/ e ios/
npx expo run:ios           # simulador o iPhone conectado
npx expo run:android       # emulador o móvil conectado
```

Comprobar el código: `npm run check` (TypeScript, lint y pruebas de configuración de firma). Comprobar textos, identificadores, imágenes y la política web: `npm run store:check`.

No se versionan `android/`, `ios/`, cachés, credenciales ni carpetas `build/` de los módulos. El código nativo propio de `modules/inclinometro-core/android/` y `modules/inclinometro-core/ios/` sí se conserva. Los cambios nativos de la app se hacen mediante `app.json` y plugins.

## Publicar una versión
El identificador de las dos plataformas es **`dev.arklay.inclinometro`**. No lo cambies para una actualización: un identificador diferente crea otra app y no hereda los datos de la anterior.

La versión visible se cambia en `app.json` y `package.json` (actualmente `2.2.0`). `eas.json` usa versiones de compilación remotas e incrementa automáticamente `versionCode` y `buildNumber` en producción. Al vincular el proyecto por primera vez, comprueba que la numeración remota sea mayor que la última subida a cada tienda; puedes inicializarla con `npx eas-cli@latest build:version:set`. En builds fuera de EAS debes incrementar los valores locales de `app.json`.

### Compilar con Expo Build

Con una cuenta de Expo y las cuentas de desarrollador de las tiendas:

```sh
npx eas-cli@latest login
npx eas-cli@latest build:configure
npx eas-cli@latest build --platform all --profile production
```

Vincula la cuenta/proyecto reales cuando lo solicite la CLI; el repositorio no contiene identificadores ni credenciales inventados. Expo gestiona las credenciales de distribución. Si ya existe una app Android publicada, importa su clave de subida mediante `npx eas-cli@latest credentials --platform android` en lugar de generar otra distinta.

Los perfiles preparados son `production` (AAB para Play y build de tienda para Apple), `preview` (distribución interna, APK en Android) y `simulator` (iOS Simulator). Todos conservan el mismo identificador. El perfil de envío Android apunta a una versión **borrador del canal interno**; no publica automáticamente en producción.

### Android y Google Play
Sube primero un AAB firmado a la prueba interna de Play Console y revisa los informes de compatibilidad. La primera subida se hace manualmente en Play Console. Para las siguientes puedes configurar una cuenta de servicio de Google en Expo y ejecutar `npx eas-cli@latest submit --platform android --profile production`.

También se conserva el flujo de GitHub Actions: una etiqueta `v*` compila APK y AAB firmados y publica los archivos en GitHub Releases, sin enviarlos a Play. Necesita los cuatro secretos `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` y `KEY_PASSWORD`. Si falta alguno, el flujo falla antes de compilar: no se publica una release con la clave de depuración. El APK de prueba de `main` permite esa clave únicamente con `INCLINOMETRO_PREVIEW_BUILD=1` y no sirve para subir a Play.

En local, con las mismas variables de entorno (`KEYSTORE_FILE` con la ruta del `.jks`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`):
```
npx expo prebuild --platform android
cd android && ./gradlew bundleRelease     # requiere las cuatro variables de firma
```

Expo SDK 58 apunta por defecto a Android 16 (API 36). Antes del envío valida el AAB final en Play Console, incluida la compatibilidad de 16 KB de sus bibliotecas nativas. La app anterior a la 2.1 usaba `com.example.inclinometro`, que Android trata como una app distinta. Las cuentas personales de Play creadas después del 13 de noviembre de 2023 necesitan la prueba cerrada exigida por Google (12 personas inscritas durante 14 días continuos) antes de solicitar acceso a producción.

### iPhone y App Store
Registra `dev.arklay.inclinometro` en tu cuenta de Apple Developer y crea la app en App Store Connect. Compila con el perfil `production` y ejecuta `npx eas-cli@latest submit --platform ios --profile production` para enviarla a App Store Connect/TestFlight. La revisión y publicación de la ficha se completan después en App Store Connect.

La configuración mantiene soporte para iPhone e iPad. Usa capturas reales sin alfa y añade las de iPad de 13 pulgadas. El manifiesto de privacidad de iOS declara el uso de preferencias, fechas de archivos y tiempo de arranque para los ajustes, las imágenes y el cálculo de intervalos del sensor.

Textos e imágenes para las tiendas: `store/ficha.md` (comprobar con `python3 store/comprobar_ficha.py`), `store/google-play/` y `store/app-store/`.

### Antes de enviar a revisión

- El correo público de soporte y privacidad es `inclinometro@palaueb.com`. Revisa el nombre del desarrollador de la ficha; se conservan los créditos y enlaces existentes de NoiD y Palaueb.
- Ejecuta `npm run store:privacy` y publica `store/privacidad.html` en `https://palaueb.com/inclinometro/privacidad.html`. La política incluida en Ajustes funciona sin conexión; la versión web debe describir las mismas prácticas. La URL actual puede contener una versión anterior.
- Completa Seguridad de los datos y Privacidad de la app teniendo en cuenta las peticiones al proveedor del mapa, además del almacenamiento local. Prepara la declaración y el vídeo del servicio de grabación de Android.
- Prueba en un Android y un iPhone/iPad reales: permisos concedidos y denegados, pantalla bloqueada, salida y retorno a otra app, finalizar/reabrir una salida, exportaciones y eliminación. El simulador de iOS no comprueba los sensores.
- En Android 15+, comprueba el timeout de grabación sin GPS con `adb shell am compat enable FGS_INTRODUCE_TIME_LIMITS dev.arklay.inclinometro` y, en un dispositivo de pruebas, reduce temporalmente `data_sync_fgs_timeout_duration` con `adb shell device_config put activity_manager data_sync_fgs_timeout_duration 60000`. Inicia una salida sin ubicación y minimiza. Debe finalizar y conservarse en el histórico. Al terminar restaura el valor con `adb shell device_config delete activity_manager data_sync_fgs_timeout_duration` y desactiva el cambio con `adb shell am compat disable FGS_INTRODUCE_TIME_LIMITS dev.arklay.inclinometro`.

Referencias: [Expo SDK 58](https://docs.expo.dev/versions/v58.0.0/), [versiones remotas](https://docs.expo.dev/build-reference/app-versions/), [envío Android](https://docs.expo.dev/submit/android/), [envío iOS](https://docs.expo.dev/submit/ios/), [política de ubicación de Google](https://support.google.com/googleplay/android-developer/answer/9799150), [timeouts de Android](https://developer.android.com/develop/background-work/services/fgs/timeout).

`npm audit` todavía puede señalar [CVE-2026-118764 de minimatch](https://github.com/advisories/GHSA-g3hv-75wg-m249) a través de las herramientas de fingerprint de Expo. `@expo/fingerprint` 0.21.10 usa el nivel de optimización 1; el aviso requiere patrones controlados por un atacante con nivel 2 o superior, y la propia implementación de Expo documenta que no se activa en ese uso. No se ha aplicado `npm audit fix --force`, que propone bajar a un SDK anterior incompatible con esta preparación.

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
- Sin ubicación, Android usa un servicio `dataSync` para la grabación: Android 15 o superior limita su tiempo en segundo plano. Cuando vence el límite, la app guarda y finaliza la salida; no la reinicia automáticamente.
- La pantalla se mantiene encendida mientras la app está visible. Si otra app ocupa la pantalla, el apagado depende de esa app.
- La altitud del GPS es poco precisa (±10–20 m).
- Una salida ocupa unos 0,3 MB por hora a 1 muestra por segundo. El histórico muestra el total y permite borrar las salidas antiguas.

NO está 100% testeada por lo que es posible que tenga fallos, se pueden comentar y dar opiniones con respeto.

NO AUTORIZO LA COPIA PARCIAL O TOTAL DEL DISEÑO DE LAS APLICACIONES NI SU COMERCIALIZACIÓN.
