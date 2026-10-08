import { describe, expect, it } from 'vitest'
import { readTableSort, sortableColumns, sortChoices } from './businessListSort'
import type { ProColumns } from '@ant-design/pro-components'

describe('business list server sorting', () => {
  const columns: ProColumns<{ amount: number; name: string }>[] = [
    { title: '金额', dataIndex: 'amount' }, { title: '姓名', dataIndex: 'name' },
    { title: '操作', key: 'action' }, { title: '无排序契约的展示列' },
  ]
  it('declares remote sort without a local comparator and preserves action columns', () => {
    const result = sortableColumns(columns, { sortField: 'amount', sortOrder: 'descend' })
    expect(result[0]).toMatchObject({ sorter: true, sortOrder: 'descend' })
    expect(result[1].sortOrder).toBeNull()
    expect(result[2]).toBe(columns[2]); expect(result[3]).toBe(columns[3])
    expect(sortChoices(columns)).toEqual([{ field: 'amount', label: '金额' }, { field: 'name', label: '姓名' }])
  })
  it('clears both request fields when native sorting is cancelled', () => {
    expect(readTableSort({ columnKey: 'amount', order: 'ascend' })).toEqual({ sortField: 'amount', sortOrder: 'ascend' })
    expect(readTableSort({ columnKey: 'amount', order: undefined })).toEqual({})
  })
})
