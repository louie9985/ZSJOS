import fs from 'node:fs'
import path from 'node:path'
import { describe, expect, it } from 'vitest'
import { expectSourceToContainTokens } from '../test/sourceGuard'

const read = (relativePath: string) => fs.readFileSync(path.resolve(process.cwd(), relativePath), 'utf8')

describe('production ticket positioning handoff', () => {
  const studentsPage = read('src/pages/MediaStudentsPage.tsx')
  const ticketsPage = read('src/pages/MediaFeaturePage.tsx')
  const api = read('src/services/api.ts')

  it('shows the frozen positioning snapshot when a student account creates a production ticket', () => {
    expectSourceToContainTokens(studentsPage, '<ProductionTicketPositioningCard snapshot={ticketContext.positioning} />')
    expectSourceToContainTokens(ticketsPage, '<ProductionTicketPositioningCard snapshot={context.positioning} title="完整定位卡" />')
    expect(studentsPage).not.toMatch(/JSON\.stringify\(ticketContext\.positioning/)
    expect(ticketsPage).not.toMatch(/JSON\.stringify\(context\.positioning/)
  })

  it('keeps the optional operator remark in the typed create contract and frozen detail', () => {
    expect(api).toContain('operatorRemark?: string;')
    expect(studentsPage).toContain('name="operatorRemark"')
    expectSourceToContainTokens(ticketsPage, "context.operatorRemark || '未填写'")
  })
})
