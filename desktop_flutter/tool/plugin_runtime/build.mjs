import { build } from 'esbuild';
import { readFile, writeFile, mkdir } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const directory = path.dirname(fileURLToPath(import.meta.url));
const assets = path.resolve(directory, '../../assets/music_plugins');
await mkdir(assets, { recursive: true });
await build({
  entryPoints: [path.join(directory, 'entry.js')], bundle: true, platform: 'browser',
  format: 'iife', target: 'es2020', minify: true, legalComments: 'eof',
  outfile: path.join(assets, 'runtime.js'),
});
const manifest = JSON.parse(await readFile(path.join(directory, 'package-lock.json'), 'utf8'));
const licenses = [];
for (const [location, metadata] of Object.entries(manifest.packages)) {
  if (!location || metadata.dev) continue;
  const moduleDirectory = path.join(directory, location);
  let license;
  for (const name of ['LICENSE', 'LICENSE.md', 'LICENSE.txt', 'license', 'license.md', 'LICENCE']) {
    try { license = await readFile(path.join(moduleDirectory, name), 'utf8'); break; } catch {}
  }
  if (!license) {
    const description = JSON.parse(await readFile(path.join(moduleDirectory, 'package.json'), 'utf8'));
    license = `License: ${description.license}\nAuthor: ${typeof description.author === 'object' ? description.author.name : description.author || ''}`;
  }
  licenses.push(`${location.replace(/^node_modules\//, '')} ${metadata.version}\n${license.trim()}`);
}
await writeFile(path.join(assets, 'runtime-LICENSES.txt'), licenses.join('\n\n--------------------\n\n') + '\n');
