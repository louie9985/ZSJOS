import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

const leadSearchPages = [
  'src/pages/LeadManagementPage.tsx',
  'src/pages/LeadClaimPoolPage.tsx',
  'src/pages/LeadAgingPoolPage.tsx',
  'src/pages/SubordinateSalesPage.tsx'
]

describe('lead keyword search', () => {
  it('advertises the business lead number in every lead keyword entry', () => {
    for (const page of leadSearchPages) {
      const source = readFileSync(page, 'utf8')
      expect(source).toContain('搜索客资编号 / 姓名 / 手机号 / 微信号')
    }
  })
})
