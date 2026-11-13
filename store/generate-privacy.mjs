import { readFile, writeFile } from 'node:fs/promises';
import {
  OSM_PRIVACY_URL, PRIVACY_SECTIONS, PRIVACY_UPDATED, PRIVACY_URL, SUPPORT_EMAIL, SUPPORT_EMAIL_URL, SUPPORT_URL,
} from '../src/privacy.ts';

const escape = (value) => value.replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;').replaceAll('"', '&quot;');
const sections = PRIVACY_SECTIONS.map(({ title, body }) => `    <section><h2>${escape(title)}</h2><p>${escape(body)}</p></section>`).join('\n');
const html = `<!doctype html>
<html lang="es">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Política de privacidad · Inclinómetro OFFROAD</title>
  <link rel="canonical" href="${PRIVACY_URL}">
  <style>
    :root { color-scheme: light dark; font: 18px/1.65 system-ui, sans-serif; }
    body { max-width: 48rem; margin: 0 auto; padding: 2rem 1.25rem 4rem; }
    h1 { line-height: 1.2; } h2 { margin-top: 2rem; font-size: 1.3rem; }
    a { overflow-wrap: anywhere; } footer { margin-top: 2rem; }
  </style>
</head>
<body>
  <main>
    <h1>Política de privacidad</h1>
    <p>Inclinómetro OFFROAD · dev.arklay.inclinometro<br>Actualizada: ${escape(PRIVACY_UPDATED)}</p>
${sections}
    <footer>
      <p>Soporte y consultas de privacidad: <a href="${SUPPORT_EMAIL_URL}">${escape(SUPPORT_EMAIL)}</a></p>
      <p><a href="${SUPPORT_URL}">Web de soporte</a></p>
      <p><a href="${OSM_PRIVACY_URL}">Política de privacidad de OpenStreetMap Foundation</a></p>
      <p>Mapas: © <a href="https://www.openstreetmap.org/copyright">OpenStreetMap contributors</a>.</p>
    </footer>
  </main>
</body>
</html>
`;
const output = new URL('./privacidad.html', import.meta.url);
if (process.argv.includes('--check')) {
  if (await readFile(output, 'utf8') !== html) {
    throw new Error('La política web está desactualizada. Ejecuta npm run store:privacy.');
  }
  console.log('La política web coincide con la incluida en la app.');
} else {
  await writeFile(output, html);
  console.log('Generada store/privacidad.html para publicar en ' + PRIVACY_URL);
}
