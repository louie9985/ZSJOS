import { useCallback, useEffect, useRef, useState } from 'react'
import { Alert, Button, Empty, Pagination, Popconfirm, Skeleton, Space, Table, Tag, Typography, message } from 'antd'
import { DownloadOutlined, ReloadOutlined, StopOutlined } from '@ant-design/icons'
import { api, type ExportTask } from '../services/api'
import { formatTimestamp } from '../services/time'

const labels: Record<ExportTask['status'], string> = { queued: '排队中', prechecking: '校验中', generating: '生成中', ready: '可下载', failed: '失败', cancelled: '已取消', expired: '已过期' }
export default function ExportTaskPage() {
  const [items, setItems] = useState<ExportTask[]>([]), [total, setTotal] = useState(0), [pageNo, setPageNo] = useState(1)
  const [loading, setLoading] = useState(true), [error, setError] = useState('')
  const loadGeneration = useRef(0)
  const load = useCallback(async () => {
    const generation = ++loadGeneration.current
    setLoading(true); setError('')
    try {
      const page = await api.exportTaskPage({ pageNo, pageSize: 20 })
      if (generation !== loadGeneration.current) return
      setItems(page.list); setTotal(page.total)
    } catch (e) {
      if (generation === loadGeneration.current) setError(e instanceof Error ? e.message : '导出任务加载失败')
    } finally {
      if (generation === loadGeneration.current) setLoading(false)
    }
  }, [pageNo])
  useEffect(() => { void load() }, [load])
  const download = async (id: number) => { try { window.location.href = await api.exportDownloadUrl(id) } catch (e) { message.error(e instanceof Error ? e.message : '下载地址获取失败') } }
  return <section className="workspace-page export-task-page"><div className="page-heading"><div><Typography.Title level={4}>导出任务</Typography.Title><Typography.Text type="secondary">异步生成并下载业务台账</Typography.Text></div><Button icon={<ReloadOutlined/>} onClick={() => void load()}>刷新</Button></div>
    {error && <Alert type="error" showIcon message={error} action={<Button size="small" onClick={() => void load()}>重试</Button>}/>} {loading ? <Skeleton active/> : <Table rowKey="id" pagination={false} dataSource={items} locale={{ emptyText: <Empty description="暂无导出任务"/> }} columns={[
      { title: '任务编号', dataIndex: 'taskNo' }, { title: '类型', dataIndex: 'exportType' },
      { title: '状态', render: (_, row: ExportTask) => <Tag color={row.status === 'ready' ? 'green' : row.status === 'failed' ? 'red' : 'default'}>{labels[row.status]}</Tag> },
      { title: '创建时间', render: (_, row: ExportTask) => formatTimestamp(row.createTime) },
      { title: '结果', render: (_, row: ExportTask) => row.failureMessage || row.resultFileName || '-' },
      { title: '操作', render: (_, row: ExportTask) => <Space>{row.status === 'ready' && <Button type="text" icon={<DownloadOutlined/>} onClick={() => void download(row.id)}>下载</Button>}{['queued','prechecking','generating'].includes(row.status) && <Popconfirm title="确认取消该导出任务？" onConfirm={async () => { try { await api.cancelExportTask(row.id); message.success('已取消'); await load() } catch (e) { message.error(e instanceof Error ? e.message : '导出任务取消失败') } }}><Button type="text" danger icon={<StopOutlined/>}>取消</Button></Popconfirm>}</Space> }
    ]}/>} {total > 20 && <Pagination current={pageNo} pageSize={20} total={total} showSizeChanger={false} onChange={setPageNo}/>}</section>
}
