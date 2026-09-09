import test from 'node:test';
import assert from 'node:assert/strict';
import { AVATAR_VERSIONS, VARIANTS, createAvatar } from '../index.mjs';

test('v1 remains the default and v2 is opt-in', () => {
  assert.deepEqual(AVATAR_VERSIONS, ['v1', 'v2']);
  assert.equal(createAvatar({ seed: 'stable-user', variant: 'geometric' }).version, 'v1');
  assert.equal(createAvatar({ seed: 'stable-user', variant: 'geometric', version: 'v2' }).version, 'v2');
});

test('v2 is deterministic and keeps composition stable across size and theme', () => {
  for (const variant of VARIANTS) {
    const options = { seed: 'L202609090001', namespace: 'zsjos:subject:lead', variant, version: 'v2' };
    assert.deepEqual(createAvatar(options), createAvatar(options));
    const small = createAvatar({ ...options, size: 24 });
    const large = createAvatar({ ...options, size: 56 });
    assert.equal(small.svg.replace('width="24" height="24"', 'width="56" height="56"'), large.svg);
    const dark = createAvatar({ ...options, theme: 'dark' });
    assert.equal(small.collectionName, dark.collectionName);
    assert.equal(small.palette, dark.palette);
  }
});

test('v2 expands the collection while preserving every v1 composition', () => {
  const samples = Array.from({ length: 4000 }, (_, seed) => createAvatar({ seed, variant: 'collection', version: 'v2' }));
  const names = new Set(samples.map((avatar) => avatar.collectionName));
  assert.equal(names.size, 18);
  for (const name of ['sprout', 'mountain', 'sunrise', 'moon', 'waves', 'blossom', 'orbit', 'kite', 'pebbles', 'archway', 'leaves', 'spark']) {
    assert(names.has(name), `v2 collection should retain ${name}`);
  }
});

test('v2 produces a larger set of procedural compositions for each style', () => {
  for (const variant of ['geometric', 'abstract', 'character']) {
    const outputs = new Set(Array.from({ length: 1000 }, (_, seed) => createAvatar({ seed, variant, version: 'v2' }).svg));
    assert(outputs.size > 24, `${variant} should expose more than 24 combinations in v2`);
  }
});

test('unknown avatar versions fail explicitly', () => {
  assert.throws(() => createAvatar({ seed: 'x', version: 'v3' }), /Unknown avatar version/);
});
