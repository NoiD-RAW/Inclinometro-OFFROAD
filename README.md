# Inclinometro-OFFROAD
Inclinómetro para 4x4: muestra en tiempo real el cabeceo (delante/detrás) y el alabeo (lateral) del vehículo con los sensores del móvil o la tablet, además de brújula, altitud y velocidad por GPS.

Una sola app con dos temas a elegir en Ajustes → Tema (se cambia al momento, sin perder la calibración):
- **Industrial**: acero cepillado, tornillos y biseles.
- **Topográfico**: tres relojes sobre curvas de nivel.

Se puede modificar el vehículo con tan solo 2/3 fotos del mismo iguales a la original de la app, con cualquier IA se pueden subir las fotos de tu vehículo y pedirle que te elimine el fondo y/o que le de un estilo concreto. En los ajustes de la app se pueden modificar los colores y el montaje del sensor, por si la tablet/smartphone va en vertical u horizontal. Cada tema guarda sus propias imágenes y su color.

## Funciones
- **Calibración** (Ajustes → Calibración): con el vehículo en llano, «Calibrar a 0°» (pide confirmación). «Restablecer calibración» vuelve al cero de fábrica. No hay botón de calibrar en la pantalla principal para no tocarlo en marcha.
- **Máximos**: se registran el cabeceo máximo (morro arriba/abajo) y el alabeo máximo (izquierda/derecha). Un ángulo solo cuenta si se mantiene el tiempo del «filtro de baches» (0,5 s por defecto). Se resetean en Ajustes.
- **Límites y avisos**: en Ajustes se indica el ángulo límite del vehículo. Al 80 % (configurable) el reloj se marca en ámbar con «PRECAUCIÓN»; al llegar al límite el fondo pasa a rojo con «PELIGRO». Vibración y pitido opcionales.
- **Salidas**: botón «Iniciar salida» en la pantalla principal (punto rojo y tiempo mientras graba; pide confirmación al finalizar). Guarda cabeceo, alabeo y, con GPS, posición, altitud y velocidad. Sigue grabando aunque se cambie a otra app (por ejemplo, la música).
- **Histórico** (icono de reloj junto a la rueda de Ajustes): lista de salidas con distancia, máximos y veces en peligro; renombrar, borrar y exportar a CSV o GPX. El detalle muestra el gráfico enlazado con el mapa del recorrido, coloreado por inclinación. El «Mapa de puntos críticos» reúne los lugares más inclinados de todas las salidas.
- Los mapas son de OpenStreetMap (gratis, sin clave). Sin cobertura, la lista, los datos y el gráfico funcionan igual; el mapa muestra lo que tenga en caché.

Para crear un tema nuevo, ver [TEMAS.md](TEMAS.md).

## Cómo probar sin salir con el coche (emulador de Android)
Con el emulador de Android Studio (o `emulator` de la línea de comandos):
- **Inclinación**: en «Extended controls → Virtual sensors» se gira el dispositivo. Por terminal, `adb emu sensor set acceleration X:Y:Z` (m/s²). Con el montaje vertical, el alabeo es `atan2(X, Y)`: por ejemplo, `5.8:7.9:0.5` da unos 36° (peligro con el límite de 30°) y `4.3:8.8:0.5` unos 26° (precaución).
- **GPS**: en «Extended controls → Location» se puede cargar un GPX o KML y reproducirlo como ruta. Sin interfaz:
  ```
  adb shell appops set com.android.shell android:mock_location allow
  adb shell cmd location providers add-test-provider gps
  adb shell cmd location providers set-test-provider-enabled gps true
  adb shell cmd location providers set-test-provider-location gps --location 40.4168,-3.7038 --accuracy 5
  ```
  Repite la última orden cambiando la posición para simular un recorrido.
- **Segundo plano**: con una salida en curso, pulsa Inicio y abre otra app. La notificación «Grabando salida» indica que sigue grabando.

## Limitaciones conocidas
- Con la app en segundo plano, la grabación continúa gracias a un servicio en primer plano. Algunas unidades con «ahorro de batería» agresivo pueden cerrarlo: si pasa, la app retoma la misma salida al volver a abrirla. Conviene excluir la app de la optimización de batería.
- La pantalla se mantiene encendida mientras la app está visible. Si otra app ocupa la pantalla, el apagado depende de esa app.
- La altitud del GPS es poco precisa (±10–20 m).
- Una salida ocupa unos 0,3 MB por hora a 1 muestra por segundo. El histórico muestra el total y permite borrar las salidas antiguas.

## Publicar una versión
Al subir una etiqueta `v*` (por ejemplo `git tag v2.0.1 && git push origin v2.0.1`), GitHub Actions compila el APK y publica la release. Si la etiqueta lleva guion (`v2.1.0-beta.1`), se publica como versión preliminar.
Para que las actualizaciones se instalen encima de la versión anterior, el APK debe ir siempre firmado con la misma clave. Añade estos secretos en el repositorio (Settings → Secrets → Actions): `KEYSTORE_BASE64` (el `.jks` en base64), `KEYSTORE_PASSWORD`, `KEY_ALIAS` y `KEY_PASSWORD`. Sin ellos se publica un APK firmado en modo depuración.

NO está 100% testeada por lo que es posible que tenga fallos, se pueden comentar y dar opiniones con respeto.  

NO AUTORIZO LA COPIA PARCIAL O TOTAL DEL DISEÑO DE LAS APLICACIONES NI SU COMERCIALIZACIÓN. 
