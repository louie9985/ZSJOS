import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

const source = readFileSync('src/pages/DeliveryClassPage.tsx', 'utf8')

describe('delivery class review fixes', () => {
  it('enters student management with the selected class filter', () => {
    expect(source).toContain("navigate(APP_ROUTES.MY_STUDENTS, { state: { classId: row.id } })")
    expect(source).toContain('delivery-class-grid')
  })

  it('loads classes incrementally and places the pending class first in the same grid', () => {
    expect(source).toContain('IntersectionObserver')
    expect(source).toContain('const orderedRows = [...rows].sort((left, right) => Number(right.systemClass) - Number(left.systemClass))')
    expect(source).toContain('delivery-class-grid">{orderedRows.map(row => <Card')
    expect(source).toContain("row.systemClass ? ' delivery-class-pending' : ''")
    expect(source).toContain('setHasMore(targetPage * CLASS_PAGE_SIZE < page.total)')
  })

  it('makes the pending class a standard clickable card without management actions', () => {
    expect(source).toContain('hoverable onClick={() => navigate(APP_ROUTES.MY_STUDENTS, { state: { classId: row.id } })}')
    expect(source).toContain('点击查看待分班学员')
    expect(source).toContain('manage && !row.systemClass')
  })

  it('warns on rough schedules without exposing student rows', () => {
    expect(source).toContain('row.scheduleType === \'ROUGH\'')
    expect(source).toContain('未设置精确考期')
    expect(source).not.toContain('loadStudents')
  })

  it('reloads exam options with selected SKUs and restores edit snapshots', () => {
    expect(source).toContain('selectedAttrs: attrs')
    expect(source).toContain('loadExams(row.categoryId, row.productId, attrs, row.selectedSkus.map(sku => sku.id))')
    expect(source).toContain('onChange={value => void skuChanged(value as number[])}')
  })
})
