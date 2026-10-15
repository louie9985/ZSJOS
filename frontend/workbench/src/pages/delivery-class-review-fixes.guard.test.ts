import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

const source = readFileSync('src/pages/DeliveryClassPage.tsx', 'utf8')

describe('delivery class review fixes', () => {
  it('enters student management with the selected class filter', () => {
    expect(source).toContain("navigate(APP_ROUTES.MY_STUDENTS, { state: { classId: row.id } })")
    expect(source).toContain('delivery-class-grid')
  })

  it('loads formal classes incrementally and separates the pending entry', () => {
    expect(source).toContain('IntersectionObserver')
    expect(source).toContain('excludeSystemClass: true')
    expect(source).toContain('delivery-class-grid">{rows.map(row => <Card')
    expect(source).toContain("row.systemClass ? ' delivery-class-pending' : ''")
    expect(source).toContain('setHasMore(targetPage * CLASS_PAGE_SIZE < page.total)')
  })

  it('keeps a pending navigation entry independent of formal filters', () => {
    expect(source).toContain('hoverable onClick={() => navigate(APP_ROUTES.MY_STUDENTS, { state: { classId: row.id } })}')
    expect(source).toContain('查看待分班学员')
    expect(source).toContain('classId: pending.id')
    expect(source).toContain('manage && !row.systemClass')
  })

  it('accepts definite multi-day schedules without an uncertain-date warning', () => {
    expect(source).not.toContain('ROUGH')
    expect(source).not.toContain('未设置精确考期')
    expect(source).not.toContain('loadStudents')
  })

  it('keeps calendar lookup optional for independently editable dates', () => {
    expect(source).toContain('DeliveryClassExamCalendar')
    expect(source).toContain('name="startDate"')
    expect(source).not.toContain('api.deliveryClasses.exams()')
    expect(source).not.toContain('selectedSkuIds')
  })
})
