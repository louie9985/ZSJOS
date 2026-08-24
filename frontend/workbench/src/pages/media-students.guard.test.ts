import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

describe('content director My Students', () => {
  const page = readFileSync('src/pages/MediaStudentsPage.tsx', 'utf8')
  const style = readFileSync('src/styles/pages/media-students.css', 'utf8')

  it('uses the workbench master-detail recipe', () => {
    expect(page).toContain('workspace-page media-students-page')
    expect(page).toContain('media-students-inbox-layout')
    expect(page).toContain('<aside className="media-students-list-pane">')
    expect(page).toContain('<main className="media-students-detail-pane">')
    expect(style).toContain('grid-template-columns: var(--crm-list-pane-w) minmax(0, 1fr)')
    expect(style).toContain('grid-template-columns: repeat(12, minmax(0, 1fr))')
  })

  it('does not render the removed implementation note', () => {
    expect(page).not.toContain('仅展示分配给当前编导')
    expect(page).not.toContain('不包含学习规划师')
    expect(page).not.toContain('我的学员（编导）')
  })

  it('loads the director-owned business detail projection', () => {
    expect(page).toContain('api.mediaStudents.get(personId)')
    expect(page).toContain('api.managedLead(service.leadId)')
    expect(page).toContain('<LeadDetail')
    expect(page).toContain('<LeadDetailOverview')
    expect(page).toContain("baseTabs={['overview']}")
    expect(page).toContain('service.serviceRelationId')
    expect(page).toContain('service.leadId')
    expect(page).toContain('第三方平台账号')
    expect(page).toContain('定位历史')
    expect(page).toContain('内容生产历史')
    expect(page).toContain('交谈记录')
    expect(page).toContain('最新内容')
    expect(page).toContain('操作时间线')
    expect(page).toContain('detail.taskLine')
    expect(page).not.toContain('最近联系')
    expect(page).not.toContain('客资流转')
    expect(page).toContain('待处理')
  })
})
