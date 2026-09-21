import { describe, expect, it } from 'vitest'
import { renderToStaticMarkup } from 'react-dom/server'
import type { TableColumnsType } from 'antd'
import BusinessTable from './index'
import { columnKey, fromNativeColumns, parseColumnWidths } from './columns'

describe('BusinessTable compatibility and states', () => {
  it('rejects malformed storage and clamps valid widths without coercing strings', () => {
    for (const value of ['null', '[]', 'broken', '123']) expect(parseColumnWidths(value)).toEqual({})
    expect(parseColumnWidths('{"name":12,"amount":240.5,"text":"200","bad":null}')).toEqual({ name: 80, amount: 241 })
  })
  it('preserves existing ProTable column setting keys', () => {
    expect(columnKey({ dataIndex: ['customer', 'name'] }, 2)).toBe('customer,name')
    expect(columnKey({ title: '说明' }, 2)).toBe('2')
    expect(columnKey({ key: 'owner', dataIndex: 'name' }, 2)).toBe('owner')
  })
  it('native render gets the actual nested value, including zero and undefined', () => {
    type Row = { id: number; nested?: { amount: number } }
    const columns: TableColumnsType<Row> = [{ title: '金额', children: [{ dataIndex: ['nested', 'amount'], render: (value, row, index) => `${typeof value}:${value}:${row.id}:${index}` }] }]
    const converted = fromNativeColumns(columns)
    const render = converted[0].children![0].render!
    const call = (row: Row) => Reflect.apply(render, undefined, ['formatted', row, 3, {}, {}])
    expect(call({ id: 4, nested: { amount: 0 } })).toBe('number:0:4:3')
    expect(call({ id: 4 })).toBe('undefined:undefined:4:3')
  })
  it('keeps raw booleans and multi-line editors intact through native-column migration', () => {
    const html = renderToStaticMarkup(<BusinessTable tableKey="native" columnMode="native" mode="compact" rowKey="id" pagination={false}
      dataSource={[{ id: 1, enabled: false }]} columns={[{ dataIndex: 'enabled', title: '状态', ellipsis: false, render: value => <textarea defaultValue={value ? '启用' : '停用'} /> }]} />)
    expect(html).toContain('停用</textarea>')
    expect(html).toContain('business-table--compact')
    expect(html).not.toContain('ant-pro-table-list-toolbar-setting-item')
  })
  it('denial hides cached records and does not offer a meaningless retry', () => {
    const html = renderToStaticMarkup(<BusinessTable tableKey="denied" unauthorized error="权限不足" onReload={() => {}} rowKey="id"
      dataSource={[{ id: 1, name: 'cached-secret' }]} columns={[{ title: '姓名', dataIndex: 'name' }]} />)
    expect(html).toContain('无权查看此表格')
    expect(html).not.toContain('cached-secret')
    expect(html).not.toContain('重试')
  })
  it('distinguishes retryable failure from an empty result', () => {
    const render = (error?: string) => renderToStaticMarkup(<BusinessTable tableKey="state" rowKey="id" dataSource={[]} columns={[]} error={error} emptyText="没有匹配记录" onReload={() => {}} />)
    expect(render()).toContain('没有匹配记录')
    const failed = render('网络中断')
    expect(failed).toContain('网络中断')
    expect(failed).toContain('重试')
    expect(failed).not.toContain('没有匹配记录')
  })
})
