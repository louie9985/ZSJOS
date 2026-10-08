import { describe, expect, it, vi } from 'vitest'
import { renderToStaticMarkup } from 'react-dom/server'
import MediaStudentInboxCard, { inboxAccountName, mediaStudentAccountHref, studentOperators } from './MediaStudentInboxCard'
import type { MediaStudentListItem } from '../services/api'
vi.mock('./LeadDetailOverview', () => ({ NameAvatar: () => <span>头像</span> }))
const student: MediaStudentListItem = { personId: 1, name: '示例学员', personNo: 'XY001', mobile: 'phone-secret', wechatId: 'wechat-secret', services: [], accounts: [
  { id: 2, accountNo: 'AC002', nickname: '账号一', platformValue: 'douyin', platformLabel: '历史抖音标签', homepageUrl: 'https://www.douyin.com/user/example' },
  { id: 3, nickname: '账号二', platformValue: 'custom', platformLabel: '自定义平台' },
] }
const render = (changes = {}) => renderToStaticMarkup(<MediaStudentInboxCard student={student} selected collapsed={false} canOpenAccount onOpen={() => {}} {...changes} />)
describe('media student inbox accounts', () => {
  it('keeps separate SVG homepage and internal account links without contacts or nested links', () => {
    const html = render()
    expect(html).toContain('历史抖音标签'); expect(html).toContain('自定义平台')
    expect(html).not.toContain('已改名'); expect(html).not.toContain('phone-secret'); expect(html).not.toContain('wechat-secret')
    expect(html).toContain('https://www.douyin.com/user/example'); expect(html).toContain('accountId=2'); expect(html).toContain('accountId=3')
    expect(html).toContain('target="_blank"'); expect(html).toContain('noopener noreferrer')
    expect(html.match(/<a /g)).toHaveLength(3)
    expect(html).toContain('<svg')
    expect(html).not.toContain('media-students-platform-label')
    expect(html).not.toContain('media-students-external-action')
    expect(html).not.toContain('anticon-right')
    expect(html).not.toContain('<img')
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
  it.each([undefined, '', 'javascript:alert(1)', '/internal', '//example.com', 'https://user:pass@example.com', 'not a url'])('does not link invalid/missing homepage %s', homepageUrl => {
    const html = render({ student: { ...student, accounts: [{ ...student.accounts[0], homepageUrl }] } })
    expect(html).not.toContain('target="_blank"')
    expect(html).toContain('accountId=2')
    expect(html.match(/<a /g)).toHaveLength(1)
  })
  it('deduplicates operators by ID, keeps same-name people and labels missing assignments', () => {
    const services = [
      { serviceRelationId: 1, status: 'active', operatorUserId: 20, operatorUserName: '运营甲' },
      { serviceRelationId: 2, status: 'completed', operatorUserId: 20, operatorUserName: '运营甲' },
      { serviceRelationId: 3, status: 'active', operatorUserId: 21, operatorUserName: '运营甲' },
      { serviceRelationId: 4, status: 'active', operatorUserId: 22 },
    ]
    expect(studentOperators({ ...student, services })).toEqual([{ id: 20, name: '运营甲' }, { id: 21, name: '运营甲' }, { id: 22, name: '姓名未提供' }])
    expect(render()).toContain('未指派')
    const html = render({ student: { ...student, services }, onOperatorFilter: () => {}, operatorFilterId: 20 })
    expect(html.match(/aria-pressed="true"/g)).toHaveLength(1)
    expect(html).toContain('姓名未提供')
    expect(render({ collapsed: true, student: { ...student, services } })).not.toContain('当前运营')
  })
})
