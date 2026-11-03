import { http, unwrap } from './api'
import type { ColumnsState } from '@ant-design/pro-components'
export interface SummaryColumn { key: string; label: string; group: string; filters?: { text: string; value: string }[] }
export interface SummaryRow { serviceRelationId: number; values: Record<string, string> }
export interface SummaryQuery { pageNo: number; pageSize: number; classId?: number; classIds?: number[]; keyword?: string; filled?: boolean }
export function visibleSummaryColumns(columns: SummaryColumn[], state: Record<string, ColumnsState>): string[] {
  return columns.map((column, index) => ({ column, index, config: state[column.key] }))
    .filter(item => item.config?.show !== false)
    .sort((a, b) => {
      const rank = (fixed: ColumnsState['fixed']) => fixed === 'left' ? 0 : fixed === 'right' ? 2 : 1
      return rank(a.config?.fixed) - rank(b.config?.fixed) || (a.config?.order ?? a.index) - (b.config?.order ?? b.index)
    }).map(item => item.column.key)
}
const base = '/zsjos/student-summary'
export const studentSummaryApi = {
  columns: async () => unwrap<SummaryColumn[]>(await http.get(base + '/columns')),
  classes: async () => unwrap<{ value: number; label: string }[]>(await http.get(base + '/classes')),
  coverage: async (params: SummaryQuery) => unwrap<{ submitted: number; members: number }>(await http.get(base + '/coverage', { params, paramsSerializer: { indexes: null } })),
  page: async (params: SummaryQuery) => unwrap<{ list: SummaryRow[]; total: number }>(await http.get(base + '/page', { params, paramsSerializer: { indexes: null } })),
  export: async (query: SummaryQuery, columns: string[]) => {
    const response = await http.post(base + '/export', { ...query, columns }, { responseType: 'blob', timeout: 300000 })
    if (response.data.type.includes('json')) {
      const payload = JSON.parse(await response.data.text()) as { msg?: string }
      throw new Error(payload.msg || '导出失败，请重试')
    }
    const url = URL.createObjectURL(response.data)
    const anchor = document.createElement('a'); anchor.href = url; anchor.download = '学员档案汇总.xlsx'; anchor.click()
    setTimeout(() => URL.revokeObjectURL(url), 1000)
  },
}
