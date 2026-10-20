import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

const source = readFileSync('src/pages/DeliveryClassPage.tsx', 'utf8')

describe('delivery class review fixes', () => {
  it('enters student management with the selected class filter', () => {
    expect(source).toContain("navigate(APP_ROUTES.MY_STUDENTS, { state: { classId: row.id } })")
    expect(source).toContain('delivery-class-grid')
  })

  it('loads pending and formal classes in the same server-paginated grid', () => {
    expect(source).toContain('IntersectionObserver')
    expect(source).not.toContain('excludeSystemClass: true')
    expect(source).toContain('delivery-class-grid">{rows.map(row => <Card')
    expect(source).toContain('className="delivery-class-card"')
    expect(source).toContain('setHasMore(targetPage * CLASS_PAGE_SIZE < page.total)')
  })

  it('uses the same class navigation without system-class maintenance actions', () => {
    expect(source).toContain('hoverable onClick={() => navigate(APP_ROUTES.MY_STUDENTS, { state: { classId: row.id } })}')
    expect(source).not.toContain('delivery-class-pending-entry')
    expect(source).toContain('无固定班主任')
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
