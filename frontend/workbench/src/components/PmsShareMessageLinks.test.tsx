import { describe, expect, it } from 'vitest'
import { pmsShareTargets } from './PmsShareMessageLinks'

describe('PMS share targets', () => {
  const params = { publicUrl: 'https://public.example.test/public-api/pms/kb/document-share/page?token=fixture', documentRoute: '/pms/kb/library/1/document/2' }
  it('uses only the public snapshot destination', () => expect(pmsShareTargets(params)).toEqual({ publicUrl: params.publicUrl }))
  it.each(['javascript:alert(1)', '//evil.test', 'https://u:p@evil.test', '/internal'])('rejects unsafe public target %s', publicUrl => expect(pmsShareTargets({ ...params, publicUrl })).toBeUndefined())
  it.each([undefined, '/system/user', '//evil.test'])('does not require or use an internal target %s', documentRoute => expect(pmsShareTargets({ ...params, documentRoute })).toEqual({ publicUrl: params.publicUrl }))
  it('does not invent links for historical messages', () => expect(pmsShareTargets({ route: '/pms/kb/document/share/old' })).toBeUndefined())
})
