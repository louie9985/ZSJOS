import { describe, expect, it } from 'vitest'
import { pmsShareTargets } from './PmsShareMessageLinks'

describe('PMS share targets', () => {
  const params = { publicUrl: 'https://public.example.test/public-api/pms/kb/document-share/page?token=fixture', documentRoute: '/pms/kb/library/1/document/2' }
  it('retains both snapshot destinations', () => expect(pmsShareTargets(params)).toEqual(params))
  it.each(['javascript:alert(1)', '//evil.test', 'https://u:p@evil.test', '/internal'])('rejects unsafe public target %s', publicUrl => expect(pmsShareTargets({ ...params, publicUrl })).toBeUndefined())
  it.each(['/system/user', '//evil.test', '/pms/kb/library/1/document/2?redirect=x'])('rejects unrelated internal target %s', documentRoute => expect(pmsShareTargets({ ...params, documentRoute })).toBeUndefined())
  it('does not invent links for historical messages', () => expect(pmsShareTargets({ route: '/pms/kb/document/share/old' })).toBeUndefined())
})
