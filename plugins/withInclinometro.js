// Nombre visible independiente del nombre ASCII del proyecto, mini ventana (PiP) y cambios de tamaño en Android,
// y firma de Android (credenciales de Expo Build o variables de entorno de GitHub Actions).
const { AndroidConfig, withAndroidManifest, withAppBuildGradle, withInfoPlist, withStringsXml } = require('expo/config-plugins');

function withDisplayName(config, displayName) {
  config = withInfoPlist(config, (c) => {
    c.modResults.CFBundleDisplayName = displayName;
    return c;
  });
  return withStringsXml(config, (c) => {
    c.modResults = AndroidConfig.Strings.setStringItem([
      { $: { name: 'app_name' }, _: displayName },
    ], c.modResults);
    return c;
  });
}

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

/** Producción requiere una clave de distribución. La firma debug solo se permite en un APK de prueba explícito. */
function configureReleaseSigning(contents) {
  let g = contents;
  const signing = "signingConfig System.getenv('INCLINOMETRO_PREVIEW_BUILD') == '1' ? signingConfigs.debug : signingConfigs.release";
  // Migrar también proyectos ya generados con la antigua alternativa de firma debug.
  if (g.includes('KEYSTORE_FILE')) {
    g = g.replace("signingConfig System.getenv('KEYSTORE_FILE') ? signingConfigs.release : signingConfigs.debug", signing);
  } else {
    if (!/signingConfigs\s*\{/.test(g) || !/signingConfig signingConfigs\.debug/.test(g)) {
      throw new Error('No se reconoce la configuración de firma de Android. Revisa el plugin tras actualizar Expo.');
    }
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
      "$1signingConfig System.getenv('INCLINOMETRO_PREVIEW_BUILD') == '1' ? signingConfigs.debug : signingConfigs.release");
  }
  if (!g.includes(signing)) throw new Error('No se ha configurado la firma de producción de Android.');
  if (!g.includes('// Inclinometro production signing guard')) {
    // Se comprueba al ejecutar la tarea: Expo Build ya habrá inyectado sus credenciales.
    g += `
// Inclinometro production signing guard
def validateInclinometroReleaseSigning = tasks.register('validateInclinometroReleaseSigning') {
    doLast {
        if (System.getenv('INCLINOMETRO_PREVIEW_BUILD') == '1') return
        def signing = android.buildTypes.release.signingConfig
        if (signing == null || signing.name == 'debug' || signing.storeFile == null ||
            !signing.storeFile.exists() || signing.storePassword == null || !signing.keyAlias || signing.keyPassword == null) {
            throw new GradleException('La versión de producción requiere credenciales de distribución. Usa Expo Build o configura KEYSTORE_FILE, KEYSTORE_PASSWORD, KEY_ALIAS y KEY_PASSWORD.')
        }
    }
}
tasks.configureEach { task ->
    if (task.name == 'preReleaseBuild') task.dependsOn(validateInclinometroReleaseSigning)
}
`;
  }
  return g;
}

function withReleaseSigning(config) {
  return withAppBuildGradle(config, (c) => {
    c.modResults.contents = configureReleaseSigning(c.modResults.contents);
    return c;
  });
}

module.exports = function withInclinometro(config, { displayName = config.name } = {}) {
  return withDisplayName(withReleaseSigning(withPip(config)), displayName);
};

module.exports.configureReleaseSigning = configureReleaseSigning;
