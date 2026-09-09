import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

const source = readFileSync('src/pages/DeliveryClassPage.tsx', 'utf8')

describe('delivery class review fixes', () => {
  it('enters student management with the selected class filter', () => {
    expect(source).toContain("navigate(APP_ROUTES.MY_STUDENTS, { state: { classId: row.id } })")
    expect(source).toContain('delivery-class-grid')
  })

  it('loads classes incrementally and separates the pending class', () => {
    expect(source).toContain('IntersectionObserver')
    expect(source).toContain('const pendingClass = rows.find(row => row.systemClass)')
    expect(source).toContain('setHasMore(targetPage * CLASS_PAGE_SIZE < page.total)')
  })

  it('warns on rough schedules without exposing student rows', () => {
    expect(source).toContain('row.scheduleType === \'ROUGH\'')
    expect(source).toContain('未设置精确考期')
    expect(source).not.toContain('loadStudents')
  })

  it('reloads exam options with current attributes and restores edit snapshots', () => {
    expect(source).toContain('selectedAttrs: attrs')
    expect(source).toContain('loadExams(row.categoryId, row.productId, attrs)')
    expect(source).toContain('onChange={value => void attrsChanged(attr.attrKey!, value as string | undefined)}')
  })
})
