import { describe, expect, it, vi } from 'vitest'
import { renderToStaticMarkup } from 'react-dom/server'
import { studentServiceColumnDefinitions, studentColumnDefaults, StudentServiceSummaryDetail, type ServiceStudent } from './StudentServiceColumns'
vi.mock('./LeadDetailOverview', () => ({ NameAvatar: () => <span /> }))
const row: ServiceStudent = { serviceRelationId: 2, personId: 7, personNo: 'XY7', name: '学员', services: [{ serviceRelationId: 2, status: 'active', ownerUserId: 8, ownerUserName: '本课程老师', acceptanceStatus: 'pending', courseName: '课程乙' }], summary: { deliveryStageLabel: '常规督学', currentTask: { id: 3, type: 'student_contact', status: 'pending', overdue: true }, orderHandoff: { access: 'denied', remark: '不应泄露' }, infoSubmission: { access: 'denied', status: 'SUBMITTED' } } }
describe('student service management projection', () => {
  it('renders current service owner, task and denied states without restricted values', () => {
    const html = renderToStaticMarkup(<StudentServiceSummaryDetail student={row} />)
    expect(html).toContain('本课程老师'); expect(html).toContain('已逾期'); expect(html).toContain('无权查看')
    expect(html).not.toContain('不应泄露'); expect(html).toContain('待接收')
  })
  it('only enables implemented remote sorts and keeps optional fields hidden', () => {
    const cols = studentServiceColumnDefinitions()
    expect(new Set(cols.map(c => c.key)).size).toBe(cols.length)
    expect(studentColumnDefaults.ownerUserName.show).toBe(true)
    expect(studentColumnDefaults.materialDeliveryContact.show).toBe(false)
    expect(cols.find(c => c.key === 'taskDueAt')?.sorter).toBe(true)
    expect(cols.find(c => c.key === 'orderRemark')?.sorter).toBe(false)
  })
  it('distinguishes a missing assigned name, independent service and missing history', () => {
    const independent = { ...row, services: [{ ...row.services[0], ownerUserName: undefined }], summary: { orderHandoff: { access: 'absent' as const }, infoSubmission: { access: 'absent' as const } } }
    const html = renderToStaticMarkup(<StudentServiceSummaryDetail student={independent} />)
    expect(html).toContain('人员姓名未提供'); expect(html).toContain('独立服务'); expect(html).toContain('历史未记录')
  })
})
