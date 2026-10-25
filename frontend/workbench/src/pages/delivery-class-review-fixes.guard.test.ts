import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

const source = readFileSync('src/pages/DeliveryClassPage.tsx', 'utf8')
const grouped = readFileSync('src/pages/DeliveryClassGroups.tsx', 'utf8')

describe('delivery class review fixes', () => {
  it('delegates class listing and navigation to the grouped list', () => {
    expect(source).toContain('<DeliveryClassGroups')
    expect(grouped).toContain("navigate(APP_ROUTES.MY_STUDENTS, { state: { classId: r.id } })")
  })
  it('keeps root and child pagination separate in the grouped card and table views', () => {
    expect(grouped).toContain('IntersectionObserver')
    expect(grouped).toContain('classGroups.page(')
    expect(grouped).toContain('classGroups.children(')
    expect(grouped).toContain('delivery-class-grid')
    expect(grouped).toContain('pagination={{ current: page')
  })
  it('requires server-authorized object actions for class maintenance', () => {
    expect(grouped).toContain("r.availableActions?.includes('UPDATE')")
    expect(grouped).toContain("r.availableActions?.includes('MOVE_GROUP')")
    expect(grouped).toContain("r.availableActions?.includes('COMPLETE')")
    expect(grouped).not.toContain('delivery-class-pending-entry')
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
