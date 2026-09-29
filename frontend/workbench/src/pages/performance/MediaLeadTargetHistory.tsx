import { Alert, Button, Empty, Modal, Spin } from 'antd'
import { useState } from 'react'
import BusinessTable from '../../components/BusinessTable'
import { mediaLeadApi, type Target, type TargetRevision } from '../../services/mediaLeadAnalysis'

function targetValue(snapshot: string | null): string {
  if (snapshot == null) return '未设置'
  try {
    const value = JSON.parse(snapshot) as { manual?: boolean; targetCount?: number }
    if (value.manual === false) return '自动汇总'
    return typeof value.targetCount === 'number' ? `${value.targetCount} 人` : '未设置'
  } catch {
    return '历史快照不可解析'
  }
}

export default function MediaLeadTargetHistory({ target }: { target: Target }) {
  const [open, setOpen] = useState(false)
  const [rows, setRows] = useState<TargetRevision[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  const load = async () => {
    if (target.id == null) return
    setLoading(true)
    setError('')
    try {
      setRows(await mediaLeadApi.revisions(target.id))
    } catch (cause) {
      setRows([])
      setError(cause instanceof Error ? cause.message : '修订记录加载失败')
    } finally {
      setLoading(false)
    }
  }

  if (target.id == null) return null
  return <>
    <Button size="small" onClick={() => { setOpen(true); void load() }}>修订记录</Button>
    <Modal title={`${target.name} · 指标修订记录`} open={open} footer={null} width={760} onCancel={() => setOpen(false)}>
      {loading ? <Spin /> : error ? <Alert type="error" showIcon title={error}
        action={<Button size="small" onClick={() => void load()}>重试</Button>} />
        : rows.length === 0 ? <Empty description="暂无修订记录" />
          : <BusinessTable tableKey="media-lead-target-revisions" mode="compact" columnMode="native"
              rowKey="id" size="small" pagination={{ pageSize: 10 }} scroll={{ x: 650 }} dataSource={rows}
              columns={[
                { title: '修改时间', dataIndex: 'createTime', render: (value: string) => value?.replace('T', ' ').slice(0, 19) },
                { title: '修改前', dataIndex: 'beforeJson', render: (value: string | null) => targetValue(value) },
                { title: '修改后', dataIndex: 'afterJson', render: (value: string) => targetValue(value) },
                { title: '原因', dataIndex: 'reason' },
                { title: '操作人内部ID', dataIndex: 'operatorId' }
              ]} />}
    </Modal>
  </>
}
