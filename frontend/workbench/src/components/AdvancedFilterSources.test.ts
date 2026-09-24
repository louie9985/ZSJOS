import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

const read = (path: string) => readFileSync(new URL(path, import.meta.url), 'utf8')
describe('advanced filter option scope boundaries', () => {
  it('both Admin consumers reject unresolved personnel and unknown option sources', () => {
    for (const path of ['../../../admin/src/views/zsjos/components/ZsjosAdvancedFilter.vue', '../../../admin/src/views/zsjos/advancedFilterTemplate/index.vue']) {
      const source = read(path)
      expect(source).not.toContain('getSimpleUserList')
      expect(source).toContain('throw new Error(`不支持的筛选选项来源: ${source}`)')
      expect(source).toContain('DictDataApi.getDictDataByType')
    }
  })
  it('finance pages use catalog options instead of their own lifecycle choice arrays', () => {
    const react = read('../pages/ManagementPages.tsx')
    expect(react).toContain("financeOptions.options('status')")
    expect(react).not.toContain('const withdrawalStatuses =')
    for (const scene of ['cashback', 'withdrawal']) {
      const vue = read(`../../../admin/src/views/zsjos/${scene}/index.vue`)
      expect(vue).toContain(`useFinanceFilterOptions('${scene}')`)
      expect(vue).not.toContain('const statuses = [')
      expect(vue).toContain('reloadOptions')
    }
  })
})
