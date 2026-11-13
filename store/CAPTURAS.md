# Capturas de App Store

Las capturas se toman de la app real en iOS Simulator. No se estiran capturas de teléfono para crear imágenes de iPad, ni se dibuja encima de la interfaz.

## Preparar la app y el simulador

Hace falta macOS, Xcode, CocoaPods y las dependencias del proyecto. Desde el proyecto, o desde una copia temporal para conservar el directorio de trabajo sin `ios/`:

```sh
npm ci
npx expo run:ios --configuration Release --device generic --output /private/tmp/inclinometro-capture-app --no-bundler
xcrun simctl list devices available
xcrun simctl list devicetypes
```

Expo genera el proyecto nativo. No se editan a mano sus archivos. La compilación `Release` para simulador incluye JavaScript y no necesita Metro; este binario de simulador no se puede subir a App Store Connect.

Usa un simulador dedicado, vacío, para no modificar salidas ni permisos personales. Se probaron iPad Pro de 13 pulgadas M5 (2064 × 2752), iPhone 15 (1179 × 2556) e iPhone Air (1260 × 2736), con iOS 27.0. Elige un tipo y un runtime disponibles en tu Xcode, crea el dispositivo con `xcrun simctl create`, y arráncalo con `xcrun simctl boot` y `xcrun simctl bootstatus ... -b`.

## Capturar

Sustituye el UDID y la ruta del `.app` por los que muestran los comandos anteriores:

```sh
python3 store/capturar_ios.py \
  --device UDID_DEL_IPAD \
  --app /private/tmp/inclinometro-capture-app/InclinometroOFFROAD.app \
  --output store/app-store/ipad-13-2064x2752
```

Por defecto se capturan Industrial, Topográfico y Ajustes. El programa instala la app, configura el permiso de ubicación del simulador y usa los argumentos de demostración que solo funcionan en simulador. La inclinación, el rumbo y la altitud son datos de ejemplo; la interfaz y el tamaño son los reales del dispositivo.

Para actualizar Ajustes y el detalle de una salida en uno de los teléfonos:

```sh
python3 store/capturar_ios.py \
  --device UDID_DEL_IPHONE \
  --app /private/tmp/inclinometro-capture-app/InclinometroOFFROAD.app \
  --output store/app-store/iphone-1179x2556 \
  --screens ajustes salida-mapa --settle-seconds 12
```

`salida-mapa` graba una salida corta mediante la propia app y posiciones GPS inventadas en CoreLocation, la finaliza y abre el detalle. No modifica directamente SQLite ni utiliza recorridos personales. Comprueba que se hayan cargado las teselas y que el crédito visible de OpenStreetMap aparezca.

Si ya había un diálogo de permisos abierto, añade `--restart`: reinicia únicamente el simulador indicado después de configurar el permiso. Esto cierra el diálogo del sistema que puede quedar visible aunque el permiso ya esté concedido. Esta opción se debe usar solo con el simulador dedicado.

Cada ejecución guarda `captura.json` junto a las imágenes con dispositivo, runtime, versión de la app, tamaños y método de captura. Las capturas PNG se guardan sin alfa. La conversión elimina exclusivamente un canal alfa completamente opaco y comprueba que los píxeles RGB sean idénticos; no cambia dimensiones, colores ni contenido. Los resultados se escriben primero en un archivo temporal para evitar restricciones de CoreSimulator al sobrescribir capturas.

## Verificar y limpiar

```sh
python3 store/comprobar_ficha.py
npm run store:check
```

El comprobador valida textos, identificadores `dev.arklay.inclinometro`, dimensiones, ausencia de alfa y al menos una captura de iPad de 13 pulgadas mientras `supportsTablet` esté activado. Abre también las imágenes para verificar que no haya errores, diálogos de permisos o contenido cortado; el comprobador no interpreta su contenido visual.

Al terminar, apaga y elimina únicamente los simuladores dedicados que hayas creado mediante `xcrun simctl shutdown` y `xcrun simctl delete`, usando sus UDID. No uses `erase all`, `shutdown all` ni elimines dispositivos existentes.
