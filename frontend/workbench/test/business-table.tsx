// UTF-8. Isolated presentation fixture; no business requests or production fallback data.
import { useState } from 'react'
import { createRoot } from 'react-dom/client'
import { App, Button, Input, Space } from 'antd'
import BusinessTable from '../src/components/BusinessTable'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import { useTheme } from '../src/components/Theme/ThemeContext'
import '../src/styles/index.css'

const rows = Array.from({ length: 35 }, (_, index) => ({ id: index + 1, name: `验收记录${index + 1}`, enabled: false, amount: index }))
function Fixture() {
  const theme = useTheme()
  const [keyword, setKeyword] = useState('')
  const [selection, setSelection] = useState<React.Key[]>([])
  const [page, setPage] = useState(1)
  const [size, setSize] = useState(20)
  const [sorts, setSorts] = useState(0)
  const [refreshes, setRefreshes] = useState(0)
  const [status, setStatus] = useState('ready')
  const [editor, setEditor] = useState('编辑内容')
  const data = rows.filter(row => row.name.includes(keyword))
  return <main style={{ padding: 16, maxWidth: '100%' }}>
    <Space wrap>
      <Button onClick={() => setStatus('error')}>模拟错误</Button>
      <Button onClick={() => setStatus('denied')}>模拟无权限</Button>
      <Button onClick={() => setStatus('ready')}>恢复</Button>
      <Button onClick={() => theme.setFontScale('large')}>大字号</Button>
      <Button onClick={() => theme.setPreset('default-dark')}>暗色</Button>
      <span id="fixture-state">排序 {sorts} 刷新 {refreshes} 已选 {selection.length} 第 {page} 页 每页 {size}</span>
    </Space>
    <BusinessTable tableKey="fixture-main" rowKey="id" dataSource={status === 'empty' ? [] : data.slice((page - 1) * size, page * size)}
      error={status === 'error' ? '测试网络错误' : undefined} unauthorized={status === 'denied'}
      onReload={() => { setRefreshes(value => value + 1); setStatus('ready') }}
      filters={<Input.Search aria-label="搜索记录" value={keyword} onChange={event => { setKeyword(event.target.value); setPage(1) }} />}
      batchActions={<Button disabled={!selection.length}>批量操作</Button>}
      rowSelection={{ selectedRowKeys: selection, preserveSelectedRowKeys: true, onChange: setSelection }}
      pagination={{ current: page, pageSize: size, total: data.length, showSizeChanger: true, onChange: (next, nextSize) => { setPage(nextSize === size ? next : 1); setSize(nextSize) } }}
      onChange={(_, __, ___, extra) => { if (extra.action === 'sort') setSorts(value => value + 1) }}
      columns={[
        { key: 'name', title: '名称', dataIndex: 'name', width: 220, sorter: true },
        { key: 'amount', title: '金额', dataIndex: 'amount', width: 220 },
        { key: 'note', title: '说明', width: 280, render: () => '这是一段用于验证单行省略和主题字号的较长说明文字' },
        { key: 'action', title: '操作', width: 100, render: () => <Button type="link">详细</Button> },
      ]} />
    <BusinessTable tableKey="fixture-compact" mode="compact" columnMode="native" rowKey="id" dataSource={rows.slice(0, 1)} pagination={false}
      columns={[
        { key: 'bool', title: '原始布尔值', dataIndex: 'enabled', render: value => value ? '错误' : '正确：停用' },
        { key: 'edit', title: '编辑', ellipsis: false, width: 300, render: () => <Input.TextArea aria-label="编辑内容" value={editor} onChange={event => setEditor(event.target.value)} /> },
      ]} />
  </main>
}
createRoot(document.getElementById('root')!).render(<ThemeProvider><App><Fixture /></App></ThemeProvider>)
