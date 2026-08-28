import fs from 'node:fs'
import path from 'node:path'
import { describe, expect, it } from 'vitest'

const read = (relativePath: string) => fs.readFileSync(path.resolve(process.cwd(), relativePath), 'utf8')

describe('production ticket positioning handoff', () => {
  const studentsPage = read('src/pages/MediaStudentsPage.tsx')
  const ticketsPage = read('src/pages/MediaFeaturePage.tsx')
  const api = read('src/services/api.ts')

  it('renders the positioning snapshot in both creation and ticket detail surfaces', () => {
    expect(studentsPage).toContain('<ProductionTicketPositioningCard snapshot={ticketContext.positioning} />')
    expect(ticketsPage).toContain('<ProductionTicketPositioningCard snapshot={context.positioning} title="完整定位卡" />')
    expect(studentsPage).not.toMatch(/JSON\.stringify\(ticketContext\.positioning/)
    expect(ticketsPage).not.toMatch(/JSON\.stringify\(context\.positioning/)
  })

  it('keeps the optional operator remark in the typed create contract and frozen detail', () => {
    expect(api).toContain('operatorRemark?: string;')
    expect(studentsPage).toContain('name="operatorRemark" label="运营备注"')
    expect(studentsPage).toContain('maxLength={500} showCount')
    expect(ticketsPage).toContain("context.operatorRemark || '未填写'")
  })
})
