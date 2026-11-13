/** Texto disponible sin conexión; la política publicada debe describir las mismas prácticas. */
export const PRIVACY_UPDATED = '13 de noviembre de 2026';
export const PRIVACY_URL = 'https://palaueb.com/inclinometro/privacidad.html';
export const SUPPORT_URL = 'https://palaueb.com/inclinometro/';
export const SUPPORT_EMAIL = 'inclinometro@palaueb.com';
export const SUPPORT_EMAIL_URL = `mailto:${SUPPORT_EMAIL}`;
export const OSM_PRIVACY_URL = 'https://osmfoundation.org/wiki/Privacy_Policy';

export const BACKGROUND_RECORDING_DISCLOSURE =
  'Durante una salida, Inclinómetro OFFROAD guarda la inclinación y, si permites la ubicación, tu posición, altitud y velocidad. ' +
  'La grabación continúa al cambiar de app o bloquear la pantalla mientras el sistema lo permita; puedes detenerla con «Finalizar salida». ' +
  'Las salidas se guardan en este dispositivo. Al abrir un mapa se solicitan las zonas visibles a OpenStreetMap, que recibe tu dirección IP y las zonas consultadas. ' +
  'Puedes leer la política completa en Ajustes → Privacidad y soporte.';

export const PRIVACY_SECTIONS: readonly { title: string; body: string }[] = [
  {
    title: 'La app y el contacto',
    body: 'Esta política describe Inclinómetro OFFROAD (dev.arklay.inclinometro), con los créditos del proyecto NoiD y Palaueb. ' +
      `Para soporte o consultas sobre privacidad, escribe a ${SUPPORT_EMAIL}. No necesitas crear una cuenta para usar la app. ` +
      'Si nos escribes, recibiremos tu dirección de correo y el contenido del mensaje para atender la consulta. Puedes solicitar su eliminación en esa misma dirección.',
  },
  {
    title: 'Sensores, ubicación y salidas',
    body: 'Los sensores de movimiento miden cabeceo y alabeo. La brújula y la ubicación permiten mostrar rumbo, altitud y velocidad. ' +
      'Al iniciar una salida se guardan fecha, hora e inclinación y, si has concedido permiso de ubicación, posición, altitud y velocidad. ' +
      'Conservamos también en el dispositivo la calibración, los máximos y tus ajustes. Estos datos se usan para los relojes, los avisos y el histórico; ' +
      'la app no sube la base de datos de salidas a un servidor propio.',
  },
  {
    title: 'Grabación en segundo plano y permisos',
    body: 'La grabación de una salida que tú inicias puede continuar cuando cambias de app o bloqueas la pantalla. ' +
      'En Android se usa un servicio en primer plano con el aviso de grabación; en iOS se usa la ubicación en segundo plano con el indicador del sistema. ' +
      'Su continuidad depende de los permisos, del GPS y de las restricciones del sistema. Puedes detenerla con «Finalizar salida». ' +
      'Puedes denegar o revocar ubicación, movimiento, fotos y notificaciones desde los ajustes del sistema. Sin ubicación se pueden medir inclinaciones y grabar salidas sin posición; ' +
      'en iOS una salida sin ubicación se pausa al salir de la app. Revocar un permiso no elimina las salidas guardadas.',
  },
  {
    title: 'Fotos y exportaciones',
    body: 'Las fotos que seleccionas para personalizar el vehículo se procesan y se copian al almacenamiento de la app en tu dispositivo. ' +
      'La app no las envía a un servicio de imágenes. Al exportar una salida a CSV o GPX, el archivo incluye el recorrido y las mediciones disponibles. ' +
      'Tú eliges dónde guardarlo o con qué app compartirlo; ese destino puede enviar o almacenar el archivo según su propia política. ' +
      'Las copias exportadas quedan fuera del control de Inclinómetro OFFROAD.',
  },
  {
    title: 'Mapas y conexiones con terceros',
    body: 'Cuando abres un mapa, la app descarga por HTTPS las teselas de la zona visible desde tile.openstreetmap.org. ' +
      'OpenStreetMap Foundation y su infraestructura de distribución reciben tu dirección IP, información técnica de la petición y los identificadores de las zonas del mapa solicitadas, ' +
      'que pueden corresponder a tu recorrido. No se envía el archivo completo de la salida ni sus inclinaciones al proveedor del mapa. ' +
      'El proveedor puede conservar registros de acceso conforme a su política de privacidad, enlazada más abajo. ' +
      'Las zonas ya vistas pueden permanecer en la caché local. Al abrir los enlaces de soporte o privacidad, tu navegador contacta también con esos sitios.',
  },
  {
    title: 'Conservación, eliminación y seguridad',
    body: 'Las salidas y los ajustes se conservan en el almacenamiento de la app hasta que los elimines o borres sus datos. ' +
      'Puedes borrar salidas desde el histórico, restablecer las fotos originales y borrar máximos y calibración desde Ajustes. ' +
      'El sistema operativo protege el acceso al almacenamiento privado de la app; los archivos exportados dependen del destino que elijas. ' +
      'Las copias de seguridad de iOS o Android pueden incluir datos de la app según la configuración de tu dispositivo y tu cuenta; ' +
      'debes gestionar o eliminar esas copias y los archivos exportados por separado. La app no incorpora anuncios, analítica de uso ni seguimiento publicitario, y no vende tus datos.',
  },
  {
    title: 'Cambios y consultas',
    body: 'La fecha indicada identifica esta versión de la política. Si cambian las funciones o los proveedores, se actualizarán la política incluida en la app y la versión web. ' +
      `Para consultas sobre tus datos o esta política, escribe a ${SUPPORT_EMAIL}. Para detener el acceso futuro a los sensores o a la ubicación, puedes revocar los permisos del sistema.`,
  },
];
