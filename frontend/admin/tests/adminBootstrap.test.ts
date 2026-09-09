import assert from 'node:assert/strict'
import test from 'node:test'
import { resolveBootstrapLoginUrl } from '../src/utils/adminBootstrap.ts'

test('preserves the original admin location in the login redirect', () => {
  assert.equal(
    resolveBootstrapLoginUrl({
      pathname: '/zsjos/leads/manage',
      search: '?status=pending',
      hash: '#detail'
    }),
    '/login?redirect=%2Fzsjos%2Fleads%2Fmanage%3Fstatus%3Dpending%23detail'
  )
})

test('falls back to the root path when the location is empty', () => {
  assert.equal(
    resolveBootstrapLoginUrl({ pathname: '', search: '', hash: '' }),
    '/login?redirect=%2F'
  )
})
