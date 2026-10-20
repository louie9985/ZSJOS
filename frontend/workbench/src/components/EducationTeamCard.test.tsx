import { renderToStaticMarkup } from 'react-dom/server'
import { describe, expect, it } from 'vitest'
import EducationTeamCard from './EducationTeamCard'
import type { EducationTeamMember } from '../services/educationTeam'

const member = { userId: 2, name: '测试教务', username: 'education', accountStatus: 0,
  todayNewIntentionCount: 5, todayOldIntentionFollowUpRecordCount: 12, todayReceivedCount: 3,
  todayOrderAmount: 2000, todayFollowUpRemainingCount: 2, todayFollowUpTotalCount: 4,
  todayStudentContactCount: 7 } as EducationTeamMember
const render = (row: Partial<EducationTeamMember> = {}) => renderToStaticMarkup(
  <EducationTeamCard member={{ ...member, ...row }} selected={false} onSelect={() => {}} />)

describe('education daily work', () => {
  it('renders six education metrics without the old sales qualification metrics', () => {
    const html = render()
    for (const label of ['今日状态', '新意向', '老意向跟进次数', '接收客资数', '今日成交额', '今日待跟进未完成', '今日学员联系数']) expect(html).toContain(label)
    for (const label of ['今日判定有效', '剩余未判定', '今日跟进记录']) expect(html).not.toContain(label)
    expect(html.match(/<strong>/g)?.length).toBe(7)
    expect(html).toContain('2 / 4')
    expect(html).toContain('>12</strong>')
    expect(html).toContain('>7</strong>')
  })
  it('keeps missing metrics distinct from true zero and completed status', () => {
    const html = render({ todayNewIntentionCount: undefined, todayStudentContactCount: 0,
      todayFollowUpRemainingCount: undefined, todayFollowUpTotalCount: undefined })
    expect(html).toContain('>—</strong>')
    expect(html).toContain('>0</strong>')
    expect(html).toContain('状态待更新')
    expect(html).not.toContain('已完成')
  })
})
