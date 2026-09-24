import { describe, expect, it, vi } from 'vitest'
import { renderToStaticMarkup } from 'react-dom/server'
import MediaStudentInboxCard, { inboxAccountName, mediaStudentAccountHref } from './MediaStudentInboxCard'
import type { MediaStudentListItem } from '../services/api'
vi.mock('./LeadDetailOverview', () => ({ NameAvatar: () => <span>头像</span> }))
const student: MediaStudentListItem = { personId: 1, name: '示例学员', personNo: 'XY001', mobile: 'phone-secret', wechatId: 'wechat-secret', services: [], accounts: [
  { id: 2, accountNo: 'AC002', nickname: '账号一', platformValue: 'douyin', platformLabel: '历史抖音标签' },
  { id: 3, nickname: '账号二', platformValue: 'custom', platformLabel: '自定义平台' },
] }
const render = (changes = {}) => renderToStaticMarkup(<MediaStudentInboxCard student={student} selected collapsed={false} canOpenAccount platforms={[{ value: 'douyin', label: '已改名', colorType: 'danger', dictType: 'zsjos_account_platform' }]} onOpen={() => {}} {...changes} />)
describe('media student inbox accounts', () => {
  it('shows all snapshot labels and internal account links without contacts or nested links', () => {
    const html = render()
    expect(html).toContain('历史抖音标签'); expect(html).toContain('自定义平台')
    expect(html).not.toContain('已改名'); expect(html).not.toContain('phone-secret'); expect(html).not.toContain('wechat-secret')
    expect(html).toContain('accountId=2'); expect(html).toContain('accountId=3')
    expect(html.indexOf('</button>')).toBeLessThan(html.indexOf('<a '))
  })
  it('does not expose links without feature permission or when collapsed', () => {
    expect(render({ canOpenAccount: false })).not.toContain('<a ')
    expect(render({ collapsed: true })).not.toContain('账号一')
  })
  it('distinguishes empty from unavailable account projections', () => {
    expect(render({ student: { ...student, accounts: [] } })).toContain('暂无可见账号')
    expect(render({ student: { ...student, accounts: undefined } })).toContain('账号信息未加载')
  })
  it('uses business numbers for unnamed/duplicate accounts and never internal ids', () => {
    expect(inboxAccountName({ id: 345 }, [])).toBe('未命名账号')
    const accounts = [{ id: 1, nickname: '同名', platformValue: 'p', accountNo: 'AC001' }, { id: 2, nickname: '同名', platformValue: 'p', accountNo: 'AC002' }]
    expect(inboxAccountName(accounts[0], accounts)).toBe('同名 · AC001')
    expect(mediaStudentAccountHref(1, 2)).toBe('/zsjos/media-students?personId=1&accountId=2')
  })
})
