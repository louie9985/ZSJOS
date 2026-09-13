import test from 'node:test'
import assert from 'node:assert/strict'
import { createAvatar, VARIANTS } from '../src/vendor/avatar-kit/index.mjs'

test('same identity produces identical SVGs across all styles and themes', () => {
  for (const variant of VARIANTS) {
    for (const theme of ['light', 'dark']) {
      const options = { seed: '用户-107', namespace: 'team:42', variant, theme }
      assert.deepEqual(createAvatar(options), createAvatar(options))
    }
  }
})

test('zero is a valid identity; numeric and string IDs share one identity', () => {
  assert.deepEqual(createAvatar({ seed: 0 }), createAvatar({ seed: '0' }))
})

test('identity text cannot insert SVG elements or external requests', () => {
  const avatar = createAvatar({ seed: '\"><script>alert(1)</script><image href="https://example.invalid/x"/>' })
  assert.doesNotMatch(avatar.svg, /script|href|https:|id=/i)
  assert.equal(decodeURIComponent(avatar.dataUri.split(',')[1]), avatar.svg)
})

test('invalid API arguments fail explicitly', () => {
  for (const seed of [undefined, null, '', '   ', {}, NaN, Infinity]) assert.throws(() => createAvatar({ seed }))
  for (const size of [0, -1, 2.5, 4096, NaN, '40']) assert.throws(() => createAvatar({ seed: 'a', size }))
  assert.throws(() => createAvatar({ seed: 'a', variant: 'missing' }))
  assert.throws(() => createAvatar({ seed: 'a', theme: 'auto' }))
})

test('resizing preserves the same composition; themes preserve the selected identity', () => {
  for (const variant of VARIANTS) {
    const sizes = [24, 32, 40, 56].map(size => createAvatar({ seed: 'user-204', variant, size }))
    for (const avatar of sizes) {
      assert.equal(sizes[0].svg.replace('width="24" height="24"', `width="${avatar.size}" height="${avatar.size}"`), avatar.svg)
      assert.match(avatar.dataUri, /^data:image\/svg\+xml;charset=UTF-8,/)
    }
    const dark = createAvatar({ seed: 'user-204', variant, theme: 'dark' })
    assert.equal(sizes[0].palette, dark.palette)
    assert.equal(sizes[0].collectionName, dark.collectionName)
  }
})

test('anonymous fallback remains stable within a tenant namespace', () => {
  const options = { seed: 'anonymous', namespace: 'zsjos:partner-h5:tenant:1' }
  assert.equal(createAvatar(options).dataUri, createAvatar(options).dataUri)
})

test('the fixed collection contains twelve compositions and intentionally allows repetition', () => {
  const samples = Array.from({ length: 1000 }, (_, seed) => createAvatar({ seed, variant: 'collection' }))
  assert.equal(new Set(samples.map(x => x.collectionName)).size, 12)
  assert.equal(new Set(samples.map(x => x.svg)).size, 12)
})

test('namespace encoding distinguishes ambiguous separator combinations', () => {
  const a = createAvatar({ namespace: 'a:b', seed: 'c', variant: 'character' })
  const b = createAvatar({ namespace: 'a', seed: 'b:c', variant: 'character' })
  assert.notEqual(a.svg, b.svg)
})
