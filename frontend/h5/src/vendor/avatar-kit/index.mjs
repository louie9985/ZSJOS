/** Avatar Kit v1 - deterministic, dependency-free SVG avatars. */
export const VERSION = 'v1';
export const VARIANTS = Object.freeze(['geometric', 'abstract', 'character', 'collection']);

const PALETTES = Object.freeze([
  { name: 'blue', light: ['#E9EEF6', '#607BA5', '#ACC2DE'], dark: ['#29384D', '#ACC3E6', '#6688B2'] },
  { name: 'sage', light: ['#E7EFE9', '#567D6A', '#ADC8B6'], dark: ['#2A3D34', '#ACD0B9', '#668B75'] },
  { name: 'sand', light: ['#F5EADF', '#A77950', '#DEC09B'], dark: ['#45382E', '#E3BC94', '#A78059'] },
  { name: 'lilac', light: ['#EEE9F4', '#89739F', '#C7B7D9'], dark: ['#3B3249', '#CFB9E5', '#947BAA'] },
  { name: 'rose', light: ['#F5E7E9', '#A56D7B', '#DFB6BE'], dark: ['#472F38', '#E3B0BE', '#AD7A8D'] },
  { name: 'teal', light: ['#E2EFEE', '#4C8080', '#9ECAC4'], dark: ['#263E40', '#A2D6CC', '#5F9390'] },
]);

function hash32(value) {
  let hash = 2166136261;
  for (let i = 0; i < value.length; i++) hash = Math.imul(hash ^ value.charCodeAt(i), 16777619);
  hash ^= hash >>> 16;
  hash = Math.imul(hash, 0x85ebca6b);
  hash ^= hash >>> 13;
  hash = Math.imul(hash, 0xc2b2ae35);
  return (hash ^ (hash >>> 16)) >>> 0;
}

const circle = (x, y, r, fill) => `<circle cx="${x}" cy="${y}" r="${r}" fill="${fill}"/>`;
const rect = (x, y, w, h, radius, fill) => `<rect x="${x}" y="${y}" width="${w}" height="${h}" rx="${radius}" fill="${fill}"/>`;
const path = (d, fill) => `<path d="${d}" fill="${fill}"/>`;
const line = (d, stroke, width = 3) => `<path d="${d}" stroke="${stroke}" stroke-width="${width}" stroke-linecap="round" stroke-linejoin="round" fill="none"/>`;

function geometric(pick, [base, main, accent]) {
  const form = pick('form', 8);
  let art;
  switch (form) {
    case 0:
      art = [0, 90, 180, 270].map((angle, i) => `<g transform="rotate(${angle} 32 32)">${path('M32 32C15 32 12 18 21 13C30 8 39 18 32 32Z', i % 2 ? accent : main)}</g>`).join('');
      break;
    case 1:
      art = path('M13 51V31A19 19 0 0 1 51 31V51H40V31A8 8 0 0 0 24 31V51Z', main) + circle(32, 43, 4, accent);
      break;
    case 2:
      art = circle(25, 24, 13, main) + circle(42, 41, 12, accent) + circle(20, 45, 6, main);
      break;
    case 3:
      art = `<g transform="rotate(45 32 32)">${rect(15, 15, 34, 34, 6, main)}${rect(28, 15, 8, 34, 2, accent)}</g>`;
      break;
    case 4:
      art = `<g transform="rotate(25 32 32)">${rect(16, 16, 8, 32, 4, main)}${rect(28, 11, 8, 42, 4, accent)}${rect(40, 16, 8, 32, 4, main)}</g>`;
      break;
    case 5:
      art = circle(32, 32, 20, main) + circle(37, 23, 14, base) + circle(39, 26, 5, accent);
      break;
    case 6:
      art = path('M13 13H31V31H13Z', main) + path('M34 13A18 18 0 0 1 52 31H34Z', accent) + path('M13 34H31V52A18 18 0 0 1 13 34Z', accent) + rect(34, 34, 18, 18, 4, main);
      break;
    default:
      art = path('M32 10L39 24L54 32L39 40L32 54L25 40L10 32L25 24Z', main) + circle(32, 32, 6, accent);
  }
  const rotation = (pick('detail', 3) - 1) * 8;
  return `<g transform="rotate(${rotation} 32 32)">${art}</g>`;
}

function abstract(pick, [base, main, accent]) {
  const form = pick('form', 5);
  const rotation = pick('rotation', 4) * 90;
  const offset = pick('offset', 9) - 4;
  let art;
  switch (form) {
    case 0:
      art = circle(12 + offset, 56, 37, main) + circle(58, 8, 28, accent);
      break;
    case 1:
      art = path('M26 -8C10 10 35 21 24 39C14 55 31 73 72 70V-8Z', main) + circle(8, 66 + offset, 29, accent);
      break;
    case 2:
      art = path('M-6 2C24 -2 18 23 42 20C66 17 76 31 72 40H-6Z', main) + circle(48 + offset, 59, 27, accent);
      break;
    case 3:
      art = rect(-8, 6 + offset, 48, 60, 23, main) + path('M38 0H64V64H38A32 32 0 0 0 38 0Z', accent);
      break;
    default:
      art = path('M-8 40C19 4 49 1 74 21V70H-8Z', main) + path('M-5 61C14 25 42 37 70 51V70H-5Z', accent);
  }
  return `<g transform="rotate(${rotation} 32 32)">${art}</g>`;
}

function character(pick, colors, lightColors) {
  const [, , face] = lightColors;
  const [, main] = colors;
  const ink = '#293344';
  const form = pick('body', 4);
  const gaze = pick('gaze', 5) - 2;
  const tilt = (pick('tilt', 3) - 1) * 5;
  const bodies = [
    'M9 65V35C9 7 55 7 55 35V65Z',
    'M12 65V26Q12 15 24 15H40Q52 15 52 26V65Z',
    'M8 65L14 30Q16 14 32 14Q48 14 50 30L56 65Z',
    'M10 65V33Q8 19 20 20L23 11Q26 6 30 13L34 20Q57 10 55 35V65Z',
  ];
  let eyes = circle(24 + gaze, 33, 2.6, ink) + circle(41 + gaze, 33, 2.6, ink);
  if (pick('eyes', 4) === 0) {
    eyes = line(`M19 ${34}Q24 28 29 34M36 34Q41 28 46 34`, ink, 2.3);
  } else if (pick('accessory', 5) === 0) {
    eyes += line('M17 29H30V37H17ZM35 29H48V37H35ZM30 32H35', ink, 1.8);
  }
  const mouths = [line('M28 44Q32 48 37 43', ink, 2.1), line('M29 44H36', ink, 2.1), path('M28 42H38Q38 49 33 49Q28 49 28 42Z', ink)];
  return `<g transform="rotate(${tilt} 32 40)">${path(bodies[form], face)}${path('M12 59Q32 48 53 59V70H12Z', main)}${eyes}${mouths[pick('mouth', mouths.length)]}</g>`;
}

const COLLECTION = Object.freeze([
  { name: 'sprout', palette: 1, draw: ([b, m, a]) => line('M32 49V27', m, 3) + path('M31 34C10 37 13 15 17 17C31 18 35 28 31 34Z', m) + path('M33 29C29 10 50 12 48 16C47 28 39 32 33 29Z', a) },
  { name: 'mountain', palette: 0, draw: ([b, m, a]) => circle(44, 20, 7, a) + path('M6 51L25 17L47 51Z', m) + path('M31 51L45 30L60 51Z', a) },
  { name: 'sunrise', palette: 2, draw: ([b, m, a]) => circle(32, 30, 14, a) + path('M0 43Q18 28 34 40T66 37V64H0Z', m) + line('M14 50H47', b, 2.5) },
  { name: 'moon', palette: 3, draw: ([b, m, a]) => circle(29, 32, 19, m) + circle(38, 23, 16, b) + path('M46 28L49 34L55 37L49 40L46 46L43 40L37 37L43 34Z', a) },
  { name: 'waves', palette: 5, draw: ([b, m, a]) => path('M0 24Q12 12 25 24T50 24T75 24V64H0Z', a) + path('M0 39Q12 27 25 39T50 39T75 39V64H0Z', m) },
  { name: 'blossom', palette: 4, draw: ([b, m, a]) => circle(24, 23, 11, m) + circle(41, 23, 11, a) + circle(24, 41, 11, a) + circle(41, 41, 11, m) + circle(32, 32, 6, b) },
  { name: 'orbit', palette: 0, draw: ([b, m, a]) => `<ellipse cx="32" cy="32" rx="25" ry="11" transform="rotate(-35 32 32)" stroke="${m}" stroke-width="3"/>` + circle(32, 32, 12, a) + circle(48, 17, 5, m) },
  { name: 'kite', palette: 2, draw: ([b, m, a]) => path('M32 9L48 29L32 45L16 29Z', m) + path('M32 9L48 29L32 29Z', a) + line('M32 44Q41 51 32 57', m, 2) },
  { name: 'pebbles', palette: 1, draw: ([b, m, a]) => rect(17, 38, 34, 13, 7, m) + rect(12, 23, 34, 12, 6, a) + rect(23, 11, 22, 10, 5, m) },
  { name: 'archway', palette: 3, draw: ([b, m, a]) => path('M12 53V30A20 20 0 0 1 52 30V53Z', m) + path('M22 53V32A10 10 0 0 1 42 32V53Z', b) + circle(32, 36, 6, a) },
  { name: 'leaves', palette: 5, draw: ([b, m, a]) => path('M15 49C6 28 24 8 45 13C42 35 26 53 15 49Z', m) + path('M28 49C27 33 39 26 53 31C51 43 42 52 28 49Z', a) + line('M16 48L35 23', b, 2) },
  { name: 'spark', palette: 4, draw: ([b, m, a]) => path('M30 9L36 24L51 30L36 36L30 51L24 36L9 30L24 24Z', m) + path('M49 39L52 45L58 48L52 51L49 57L46 51L40 48L46 45Z', a) },
]);

function normalizeSeed(seed) {
  if ((typeof seed !== 'string' && typeof seed !== 'number') ||
      (typeof seed === 'number' && !Number.isFinite(seed)) || !String(seed).trim()) {
    throw new TypeError('seed must be a nonempty string or a finite number.');
  }
  return String(seed);
}

/** Return an SVG and an <img>-ready data URI. The SVG itself has a square canvas. */
export function createAvatar(options = {}) {
  const seed = normalizeSeed(options.seed);
  const variant = options.variant ?? 'geometric';
  const namespace = options.namespace ?? 'app';
  const theme = options.theme ?? 'light';
  const size = options.size ?? 40;
  if (!VARIANTS.includes(variant)) throw new RangeError(`Unknown avatar variant: ${variant}`);
  if (!['light', 'dark'].includes(theme)) throw new RangeError('theme must be light or dark.');
  if (typeof namespace !== 'string') throw new TypeError('namespace must be a string.');
  if (!Number.isInteger(size) || size < 1 || size > 2048) throw new RangeError('size must be an integer from 1 to 2048.');
  const identity = JSON.stringify([VERSION, namespace, seed]);
  const pick = (key, count) => hash32(JSON.stringify([identity, key])) % count;
  const collectionIndex = variant === 'collection' ? pick('collection', COLLECTION.length) : null;
  const preset = collectionIndex === null ? null : COLLECTION[collectionIndex];
  const paletteIndex = preset ? preset.palette : pick('palette', PALETTES.length);
  const palette = PALETTES[paletteIndex];
  const colors = palette[theme];
  let art;
  if (variant === 'geometric') art = geometric(pick, colors);
  if (variant === 'abstract') art = abstract(pick, colors);
  if (variant === 'character') art = character(pick, colors, palette.light);
  if (preset) art = preset.draw(colors);
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="${size}" height="${size}" viewBox="0 0 64 64" fill="none">${rect(0, 0, 64, 64, 0, colors[0])}${art}</svg>`;
  return Object.freeze({
    version: VERSION, seed, variant, size, theme, palette: palette.name,
    collectionName: preset?.name ?? null,
    svg, dataUri: 'data:image/svg+xml;charset=UTF-8,' + encodeURIComponent(svg),
  });
}
