import { Alert, Button, Modal, Select, Space, Spin, Typography } from 'antd'
import { useEffect, useState } from 'react'
import BusinessTable from './BusinessTable'
import { classExamProgress, type ExamProgressStudent, type ExamProgressView } from '../services/classExamProgress'

const actionLabels: Record<string, string> = { SPRINT: '进入考前冲刺', EXAM: '完成考试', END: '结束服务', BACK: '回退上一阶段' }
export default function ClassExamProgressDialog({ id, name, onClose }: { id: number; name: string; onClose: () => void }) {
  const [view, setView] = useState<ExamProgressView>()
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [blockers, setBlockers] = useState<ExamProgressStudent[]>([])
  const load = async () => {
    setBusy(true); setError('')
    try { setView(await classExamProgress.get(id)) }
    catch (e) { setError(e instanceof Error ? e.message : '考试进度加载失败') }
    finally { setBusy(false) }
  }
  useEffect(() => { void load() }, [id])
  const transition = async (action: string) => {
    if (!view || busy) return
    setBusy(true); setError(''); setNotice(''); setBlockers([])
    try {
      const result = await classExamProgress.transition(id, action, view.version)
      setView(result.view); setNotice(result.message); setBlockers(result.blockers)
    } catch (e) { setError(e instanceof Error ? e.message : '状态更新失败') }
    finally { setBusy(false) }
  }
  const outcome = async (student: ExamProgressStudent, value: string | null) => {
    if (!view || busy) return
    setBusy(true); setError(''); setNotice(''); setBlockers([])
    try { setView(await classExamProgress.outcome(id, student.serviceRelationId, value, view.version)) }
    catch (e) { setError(e instanceof Error ? e.message : '结果更新失败') }
    finally { setBusy(false) }
  }
  const columns = [
    { title: '学员', dataIndex: 'name', render: (_: unknown, s: ExamProgressStudent) => s.name || '姓名未记录' },
    { title: '当前阶段', dataIndex: 'stageLabel' },
    { title: '考后结果', dataIndex: 'outcomeLabel', render: (_: unknown, s: ExamProgressStudent) =>
      view?.stage === 'EXAM' && view.actions.length > 0 && s.stage === 'EXAM'
        ? <Space wrap><Select labelInValue aria-label={`${s.name}考后结果`} style={{ minWidth: 130 }} placeholder="选择考后结果" disabled={busy} value={s.outcome ? { value: s.outcome, label: s.outcomeLabel } : undefined} options={view.outcomes} onChange={option => void outcome(s, option.value)} />
          {s.outcome && <Button disabled={busy} onClick={() => void outcome(s, null)}>回退至完成考试</Button>}</Space>
        : s.outcomeLabel || '尚未填写' },
  ]
  return <Modal title={`${name} · 考试进度`} open onCancel={onClose} footer={<Button onClick={onClose}>关闭</Button>} width={900}>
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      {error && <Alert type="error" showIcon message={error} action={<Button disabled={busy} onClick={() => void load()}>重新加载</Button>} />}
      {busy && !view && <Spin />}
      {view && <>
        <Typography.Text strong>班级阶段：{view.stageLabel}</Typography.Text>
        <Typography.Text type="secondary">整班推进或回退只更新考试进度，不触发其他业务操作。结束服务前，所有学员须填写考后结果。</Typography.Text>
        <Space wrap>{view.actions.map(action => <Button key={action} disabled={busy} onClick={() => void transition(action)}>{actionLabels[action] || action}</Button>)}</Space>
        {notice && <Alert type={blockers.length ? 'warning' : 'success'} showIcon message={notice} description={blockers.length ? `共 ${blockers.length} 名学员需要处理：${blockers.map(s => `${s.name || '姓名未记录'}（${s.stageLabel}）`).join('、')}` : undefined} />}
        <BusinessTable<ExamProgressStudent> tableKey="class-exam-progress" mode="compact" rowKey="serviceRelationId" columns={columns} dataSource={view.students} loading={busy} pagination={{ pageSize: 10 }} />
      </>}
    </Space>
  </Modal>
}
