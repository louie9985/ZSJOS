import { describe, expect, it } from 'vitest'
import { profileDifferences } from './OrderProfileConfirmModal'
describe('order profile comparison', () => {
  it('compares with current Person instead of the originating Lead and ignores outer whitespace', () => {
    expect(profileDifferences({ personId: 1, name: '旧姓名', mobile: '13800000001', wechatId: 'same', fingerprint: 'v' },
      { name: '新姓名', mobile: ' 13800000001 ', wechatId: 'same' }).map(f => f.key)).toEqual(['name'])
  })
  it('includes missing contact but does not invent differences for a new customer', () => {
    const incoming = { name: '姓名', mobile: '13800000001', wechatId: 'demo' }
    expect(profileDifferences({ personId: 1, name: '姓名', mobile: '13800000001', fingerprint: 'v' }, incoming).map(f => f.key)).toEqual(['wechatId'])
    expect(profileDifferences({ fingerprint: 'new' }, incoming)).toEqual([])
  })
})
