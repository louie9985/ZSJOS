import { test } from 'node:test'
import assert from 'node:assert/strict'
import { parseVersionManifest, hasDifferentBuild, buildRefreshUrl } from '../src/utils/version.ts'

const current = {
  app: 'zsjos-partner-h5', version: '0.1.0', buildId: 'build-a',
  publishedAt: '2026-09-10T00:00:00Z', releases: []
}

test('版本比较支持同版本重新构建与部署回滚', () => {
  assert.equal(hasDifferentBuild(current, null), false)
  assert.equal(hasDifferentBuild(current, { ...current }), false)
  assert.equal(hasDifferentBuild(current, { ...current, buildId: 'build-b' }), true)
  assert.equal(hasDifferentBuild(current, { ...current, version: '0.0.9', buildId: 'old' }), true)
})

test('拒绝其他应用、损坏清单和无效说明', () => {
  for (const value of [null, {}, '<html>', { ...current, app: 'other' },
    { ...current, buildId: '' }, { ...current, publishedAt: 'invalid' },
    { ...current, releases: [{ version: '1', date: 'today', summary: '', notes: [42] }] }]) {
    assert.throws(() => parseVersionManifest(value), /版本信息格式不正确/)
  }
  assert.deepEqual(parseVersionManifest(current), current)
})

test('刷新保留路径、重复查询参数和 hash，不叠加缓存参数', () => {
  const original = 'https://h5.example/profile/version-update?code=a%2Bb&tag=1&tag=2&_h5_build=old#details'
  const result = new URL(buildRefreshUrl(original, 'new/build'))
  assert.equal(result.pathname, '/profile/version-update')
  assert.equal(result.searchParams.get('code'), 'a+b')
  assert.deepEqual(result.searchParams.getAll('tag'), ['1', '2'])
  assert.deepEqual(result.searchParams.getAll('_h5_build'), ['new/build'])
  assert.equal(result.hash, '#details')
})
