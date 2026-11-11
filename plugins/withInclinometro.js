// Ajustes nativos que no cubre app.json: mini ventana (PiP) y cambios de tamaño en Android,
// y firma de la versión final con la clave de Google Play (variables de entorno, como en GitHub Actions).
const { withAndroidManifest, withAppBuildGradle } = require('expo/config-plugins');

function withPip(config) {
  return withAndroidManifest(config, (c) => {
    const app = c.modResults.manifest.application[0];
    const main = app.activity.find((a) => a.$['android:name'] === '.MainActivity');
    main.$['android:supportsPictureInPicture'] = 'true';
    main.$['android:resizeableActivity'] = 'true';
    const changes = new Set((main.$['android:configChanges'] || '').split('|').filter(Boolean));
    for (const k of ['orientation', 'screenSize', 'smallestScreenSize', 'screenLayout', 'density', 'keyboardHidden']) changes.add(k);
    main.$['android:configChanges'] = [...changes].join('|');
    return c;
  });
}

/** Con KEYSTORE_FILE, KEYSTORE_PASSWORD, KEY_ALIAS y KEY_PASSWORD, la versión final se firma con esa clave; sin ellas, con la de depuración. */
function withReleaseSigning(config) {
  return withAppBuildGradle(config, (c) => {
    let g = c.modResults.contents;
    if (g.includes('KEYSTORE_FILE')) return c;
    g = g.replace(/signingConfigs\s*\{/, `signingConfigs {
        release {
            if (System.getenv('KEYSTORE_FILE')) {
                storeFile file(System.getenv('KEYSTORE_FILE'))
                storePassword System.getenv('KEYSTORE_PASSWORD')
                keyAlias System.getenv('KEY_ALIAS')
                keyPassword System.getenv('KEY_PASSWORD')
            }
        }`);
    g = g.replace(/(release\s*\{[^}]*?)signingConfig signingConfigs\.debug/,
      "$1signingConfig System.getenv('KEYSTORE_FILE') ? signingConfigs.release : signingConfigs.debug");
    c.modResults.contents = g;
    return c;
  });
}

module.exports = function withInclinometro(config) {
  return withReleaseSigning(withPip(config));
};
