const assert = require('node:assert/strict');
const { test } = require('node:test');
const { configureReleaseSigning } = require('./withInclinometro');

const template = `android {
  signingConfigs {
    debug { storeFile file('debug.keystore') }
  }
  buildTypes {
    debug { signingConfig signingConfigs.debug }
    release { signingConfig signingConfigs.debug }
  }
}`;

test('release requires distribution credentials unless a preview was explicitly requested', () => {
  const output = configureReleaseSigning(template);
  assert.match(output, /debug \{ signingConfig signingConfigs\.debug \}/);
  assert.match(output, /release \{ signingConfig System.getenv\('INCLINOMETRO_PREVIEW_BUILD'\) == '1' \? signingConfigs.debug : signingConfigs.release \}/);
  assert.match(output, /storeFile file\(System.getenv\('KEYSTORE_FILE'\)\)/);
  assert.match(output, /preReleaseBuild.*dependsOn\(validateInclinometroReleaseSigning\)/);
  assert.match(output, /throw new GradleException/);
  assert.match(output, /signing.storePassword == null/);
  assert.match(output, /signing.keyPassword == null/);
  assert.equal(configureReleaseSigning(output), output);
});

test('prebuild upgrades an existing project with the old debug signing fallback', () => {
  const legacy = `// KEYSTORE_FILE\nrelease { signingConfig System.getenv('KEYSTORE_FILE') ? signingConfigs.release : signingConfigs.debug }`;
  const output = configureReleaseSigning(legacy);
  assert.doesNotMatch(output, /KEYSTORE_FILE'\) \? signingConfigs.release : signingConfigs.debug/);
  assert.match(output, /INCLINOMETRO_PREVIEW_BUILD/);
  assert.equal(configureReleaseSigning(output), output);
});

test('an unrecognized native template fails instead of retaining unsafe signing', () => {
  assert.throws(() => configureReleaseSigning('android { buildTypes { release {} } }'), /firma/);
  assert.throws(() => configureReleaseSigning('// KEYSTORE_FILE\nandroid {}'), /firma/);
});
