# Ficha de la tienda

Textos para Google Play y App Store. Los límites están comprobados con `python3 store/comprobar_ficha.py`.

## Común

- **Nombre** (máx. 30): Inclinómetro OFFROAD
- **Política de privacidad**: https://palaueb.com/inclinometro/privacidad.html
- **Web / soporte**: https://palaueb.com/inclinometro/
- **Contacto de soporte y privacidad**: inclinometro@palaueb.com
- **Categoría**: Google Play: Mapas y navegación · App Store: Navegación

## Google Play

- **Descripción breve** (máx. 80): Cabeceo y alabeo de tu 4x4 en tiempo real, con avisos y rutas grabadas.
- **Correo de soporte**: inclinometro@palaueb.com

### Descripción completa (máx. 4.000)

<!-- descripcion -->
Convierte tu móvil o tu tablet en el panel de un todoterreno. Inclinómetro OFFROAD muestra en tiempo real cuánto se inclina tu vehículo hacia los lados (alabeo) y hacia delante o atrás (cabeceo), y te avisa antes de llegar al límite.

PENSADA PARA LA PISTA
Relojes grandes que se leen de un vistazo, con la silueta de tu vehículo inclinándose igual que él. Además, brújula, velocidad y altitud por GPS.

AVISOS DE PRECAUCIÓN Y PELIGRO
Indica el ángulo máximo que admite tu vehículo. Al acercarte, el reloj se marca en ámbar. Al llegar al límite, la pantalla pasa a rojo y, si quieres, vibra y pita. Avisa hacia cualquier lado y en los dos ejes.

MÁXIMOS SIN FALSOS PICOS
Registra el cabeceo y el alabeo máximos hacia cada lado. Un filtro de baches evita que un golpe seco cuente como máximo.

GRABA TUS SALIDAS
Guarda la ruta con la inclinación, la posición, la altitud y la velocidad, también con la app en segundo plano mientras usas la música o el navegador. En el histórico verás el gráfico enlazado con el mapa del recorrido, coloreado por inclinación, y un mapa con los puntos más críticos de todas tus salidas. Exporta a CSV o GPX.

DOS TEMAS
Industrial, de acero cepillado con tornillos y biseles, o Topográfico, con tres relojes sobre curvas de nivel. Se cambian al momento sin perder la calibración, y cada uno con el color que elijas.

TU VEHÍCULO EN LA PANTALLA
Sube dos o tres fotos de tu vehículo sin fondo (de lado, por detrás y desde arriba) y aparecerá en los relojes.

PARA MÓVIL, TABLET Y RADIO DE COCHE
Funciona en vertical y en horizontal, con el sensor montado en vertical o en plano. Si sales de la app, pasa a una mini ventana para seguir viendo los ángulos.

PRIVADA Y SIN CUENTAS
Sin anuncios, sin registro y sin estadísticas. Tus salidas se guardan en el almacenamiento de la app; las copias de seguridad dependen del sistema. Al abrir un mapa, OpenStreetMap recibe tu IP y las zonas consultadas para descargar el mapa por HTTPS. Sin cobertura se muestra la caché disponible. La política completa está en Ajustes.

Antes de salir, calibra la app con el vehículo en llano. Los ángulos son orientativos: la estabilidad real depende de la carga, el terreno y la velocidad. Conduce con prudencia y no manipules el móvil en marcha.
<!-- /descripcion -->

## App Store

- **Subtítulo** (máx. 30): Cabeceo y alabeo para tu 4x4
- **Palabras clave** (máx. 100 bytes, separadas por comas, sin espacios): inclinometro,4x4,offroad,todoterreno,alabeo,cabeceo,furgoneta,pendiente,nivel,brujula,altimetro
- **Copyright**: 2026 NoiD y Palaueb
- **URL de soporte**: https://palaueb.com/inclinometro/

### Descripción de App Store (máx. 4.000)

<!-- descripcion-ios -->
Convierte tu iPhone o iPad en el panel de un todoterreno. Inclinómetro OFFROAD muestra cuánto se inclina tu vehículo hacia los lados (alabeo) y hacia delante o atrás (cabeceo), con avisos según los límites que configures.

PENSADA PARA LA PISTA
Relojes grandes, con la silueta de tu vehículo inclinándose igual que él. Además, brújula, velocidad y altitud por GPS. Funciona en vertical y en horizontal, con el sensor montado en vertical o en plano.

AVISOS Y MÁXIMOS
Configura los límites de cabeceo y alabeo y la zona de precaución. Los relojes cambian de color al acercarte al límite; puedes activar pitidos y vibración en los dispositivos compatibles. El filtro de baches evita que un golpe seco cuente como máximo.

GRABA TUS SALIDAS
Guarda inclinación, posición, altitud y velocidad. Con permiso de ubicación, una salida que inicias puede seguir grabándose al cambiar de app o bloquear la pantalla, mientras iOS lo permita. Puedes finalizarla desde la app. Sin ubicación se graban inclinaciones y la grabación se pausa al salir de la app.

Revisa el histórico, el gráfico enlazado con el mapa del recorrido y los puntos críticos de todas tus salidas. Renombra o borra tus salidas y exporta los datos a CSV o GPX.

DOS TEMAS Y TU VEHÍCULO
Elige Industrial o Topográfico sin perder tu calibración. Personaliza los colores y añade las fotos de tu vehículo sin fondo, de lado, por detrás o desde arriba según el tema.

PRIVACIDAD
Sin anuncios, sin registro y sin estadísticas. Las salidas, fotos y ajustes se guardan en el almacenamiento de la app; las copias de seguridad dependen de iOS. Al abrir un mapa, OpenStreetMap recibe tu IP y las zonas consultadas para descargar el mapa por HTTPS. Sin cobertura se muestra la caché disponible. Puedes leer la política completa en Ajustes.

Calibra con el vehículo en llano antes de salir. Los ángulos son orientativos: la estabilidad real depende de la carga, el terreno y la velocidad. Conduce con prudencia y no manipules el móvil en marcha.
<!-- /descripcion-ios -->

## Imágenes

| Archivo | Para | Tamaño |
|---|---|---|
| `google-play/icono-512.png` | Icono de Google Play | 512 × 512, PNG con alfa |
| `google-play/grafico-funciones-1024x500.jpg` | Gráfico de funciones | 1024 × 500, sin alfa |
| `google-play/capturas/*.jpg` | Capturas de teléfono (8) | 1080 × 2160, proporción 2:1, sin alfa |

Las capturas de App Store están en `app-store/`: iPhone de 1179 × 2556 y 1260 × 2736, e iPad de 13 pulgadas. Deben ser capturas reales, sin canal alfa. El comprobador valida los tamaños, la opacidad y la presencia de capturas de iPad mientras `supportsTablet` esté activado.

La pantalla de Ajustes ha cambiado: vuelve a capturar cualquier imagen de Ajustes que no coincida con la compilación que vas a subir.

## Formulario de Google Play

- **Seguridad de los datos**: las salidas, ajustes y fotos se procesan en el dispositivo y la app no sube esa base de datos a un servidor propio. Esto no permite afirmar que no se transmite nada: los mapas hacen peticiones HTTPS a OpenStreetMap, cuyo proveedor recibe IP, información técnica y zonas solicitadas, y puede conservar registros. Completa las categorías de datos, finalidades, conservación y destinatarios según esas prácticas y la versión del SDK incluida; no copies una respuesta de «no se recoge nada». La exportación a CSV/GPX es una acción explícita del usuario y el destino elegido aplica su propia política.
- **Servicio en primer plano** (`location` y `dataSync`): se usa solo mientras el usuario graba una salida, que él inicia y finaliza con un botón, y se muestra una notificación fija. Google pide un vídeo corto: iniciar salida, ir a otra app, ver la notificación «Grabando salida», volver y finalizar.
- **Permiso de ubicación**: `ACCESS_FINE_LOCATION` y `ACCESS_COARSE_LOCATION`, sin `ACCESS_BACKGROUND_LOCATION`. La grabación minimizada usa un servicio de ubicación iniciado por el usuario y no queda exenta por ello de la revisión de ubicación de Google. La app explica el uso en segundo plano antes de iniciar la salida. Prepara el vídeo y la declaración de servicios en primer plano. La grabación sin GPS usa `dataSync` y finaliza guardando la salida cuando Android aplica su límite de tiempo.
- **Público**: mayores de 13 años. Sin anuncios.

## Política web y revisión

`src/privacy.ts` es el texto de la política incluida en la app. Ejecuta `npm run store:privacy` para generar `store/privacidad.html` y publica ese archivo en la URL de privacidad indicada arriba. La versión web y la incluida en la app deben coincidir; antes del envío verifica que tanto privacidad como soporte son públicos y accesibles sin iniciar sesión.

En App Store Connect completa también la ficha de privacidad según las conexiones con OpenStreetMap; no confundas el almacenamiento local con las peticiones al proveedor. La app no usa seguimiento publicitario ni pide ATT. Los motivos de acceso a preferencias, fechas de archivos y tiempo de arranque se declaran en `ios.privacyManifests`.

Notas para revisión: no se necesita cuenta. La ubicación es opcional para los relojes; se necesita para grabar el recorrido. Inicia una salida desde la pantalla principal, cambia de app, vuelve y finalízala. Revisa el histórico y exporta CSV/GPX. En iOS no hay mini ventana. Los sensores y la continuidad de la grabación deben probarse en un dispositivo físico.
