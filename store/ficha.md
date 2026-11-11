# Ficha de la tienda

Textos para Google Play y App Store. Los límites están comprobados con `python3 store/comprobar_ficha.py`.

## Común

- **Nombre** (máx. 30): Inclinómetro OFFROAD
- **Política de privacidad**: https://palaueb.com/inclinometro/privacidad.html
- **Web / soporte**: https://palaueb.com/inclinometro/
- **Categoría**: Google Play: Mapas y navegación · App Store: Navegación

## Google Play

- **Descripción breve** (máx. 80): Cabeceo y alabeo de tu 4x4 en tiempo real, con avisos y rutas grabadas.
- **Correo de soporte**: el de la cuenta de desarrollador.

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
Sin anuncios, sin registro y sin estadísticas. Tus salidas se guardan solo en tu dispositivo. Los mapas son de OpenStreetMap y funcionan sin cobertura con las zonas ya vistas.

Antes de salir, calibra la app con el vehículo en llano. Los ángulos son orientativos: la estabilidad real depende de la carga, el terreno y la velocidad. Conduce con prudencia y no manipules el móvil en marcha.
<!-- /descripcion -->

## App Store

- **Subtítulo** (máx. 30): Cabeceo y alabeo para tu 4x4
- **Palabras clave** (máx. 100 bytes, separadas por comas, sin espacios): inclinometro,4x4,offroad,todoterreno,alabeo,cabeceo,furgoneta,pendiente,nivel,brujula,altimetro
- **Copyright**: 2026 NoiD y Palaueb
- **URL de soporte**: https://palaueb.com/inclinometro/
- **Descripción**: la misma que la de Google Play, quitando la frase de la mini ventana y la de la radio de coche si la versión de iPhone no las tiene.

## Imágenes

| Archivo | Para | Tamaño |
|---|---|---|
| `google-play/icono-512.png` | Icono de Google Play | 512 × 512, PNG con alfa |
| `google-play/grafico-funciones-1024x500.jpg` | Gráfico de funciones | 1024 × 500, sin alfa |
| `google-play/capturas/*.jpg` | Capturas de teléfono (8) | 1080 × 2160, proporción 2:1, sin alfa |

Las capturas de App Store (1179 × 2556 para iPhone) se harán desde la versión de iPhone.

## Formulario de Google Play

- **Seguridad de los datos**: la app no recoge ni comparte datos. La ubicación y las salidas se procesan y guardan solo en el dispositivo, así que no cuentan como datos recogidos. No hay cifrado en tránsito que declarar porque no se envía nada; los mapas se descargan de OpenStreetMap sin datos del usuario.
- **Servicio en primer plano** (`location` y `dataSync`): se usa solo mientras el usuario graba una salida, que él inicia y finaliza con un botón, y se muestra una notificación fija. Google pide un vídeo corto: iniciar salida, ir a otra app, ver la notificación «Grabando salida», volver y finalizar.
- **Permiso de ubicación**: solo en primer plano (`ACCESS_FINE_LOCATION`), sin ubicación en segundo plano. La grabación con la app minimizada funciona gracias al servicio en primer plano.
- **Público**: mayores de 13 años. Sin anuncios.
