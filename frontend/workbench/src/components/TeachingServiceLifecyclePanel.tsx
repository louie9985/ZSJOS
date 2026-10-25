import { PoweroffOutlined, RollbackOutlined } from '@ant-design/icons'
import OverflowToolbar, { type ToolbarAction } from './OverflowToolbar'
import { useCallback, useEffect, useRef, useState, type ReactNode } from 'react'
import { Alert, Button, Collapse, Empty, Form, Input, Modal, Select, Skeleton, Timeline, message } from 'antd'
import { hasPermission } from '../services/managementAccess'
import { type DeliveryClassOption } from '../services/api'
import { createIdempotencyKey } from '../services/idempotency'
import { teachingServiceLifecycleApi as api, type TeachingServiceLifecycle } from '../services/teachingServiceLifecycle'

const errorMessage = (error: unknown) => error instanceof Error ? error.message : '请求失败，请重试'

export default function TeachingServiceLifecyclePanel({ relationId, version, permissions, onChanged, children }: {
  relationId: number; version: number; permissions: string[]; onChanged: () => Promise<void>; children?: (actions: ToolbarAction[]) => ReactNode
}) {
  const [detail, setDetail] = useState<TeachingServiceLifecycle>()
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [mode, setMode] = useState<'close' | 'restore'>()
  const [busy, setBusy] = useState(false)
  const [targets, setTargets] = useState<DeliveryClassOption[]>([])
  const [targetLoading, setTargetLoading] = useState(false)
  const [targetError, setTargetError] = useState('')
  const [form] = Form.useForm<{ reason: string; targetClassId?: number }>()
  const requestKey = useRef('')
  const generation = useRef(0)
  const targetGeneration = useRef(0)
  const submitLock = useRef(false)
  const load = useCallback(async () => {
    const token = ++generation.current
    setLoading(true); setError(''); setDetail(undefined)
    try { const value = await api.detail(relationId); if (token === generation.current) setDetail(value) }
    catch (e) { if (token === generation.current) setError(errorMessage(e)) }
    finally { if (token === generation.current) setLoading(false) }
  }, [relationId])
  useEffect(() => {
    setMode(undefined); ++targetGeneration.current
    void load()
    return () => { ++generation.current; ++targetGeneration.current }
  }, [load, version])
  const loadTargets = async () => {
    const token = ++targetGeneration.current
    setTargetLoading(true); setTargetError(''); setTargets([])
    try {
      const rows = await api.targets(relationId)
      if (token !== targetGeneration.current) return
      setTargets(rows)
      if (form.getFieldValue('targetClassId') == null && rows.some(row => row.id === detail?.previousClassId)) form.setFieldValue('targetClassId', detail?.previousClassId)
    } catch (e) { if (token === targetGeneration.current) setTargetError(errorMessage(e)) }
    finally { if (token === targetGeneration.current) setTargetLoading(false) }
  }
  const dismiss = () => { if (!submitLock.current) { ++targetGeneration.current; setMode(undefined) } }
  const open = (action: 'close' | 'restore') => {
    form.resetFields(); requestKey.current = createIdempotencyKey(); setMode(action)
    if (action === 'restore') void loadTargets()
  }
  const submit = async () => {
    if (!mode || submitLock.current) return
    submitLock.current = true
    let values: { reason: string; targetClassId?: number }
    try { values = await form.validateFields() } catch { submitLock.current = false; return }
    setBusy(true)
    try {
      await api[mode](relationId, { ...values, reason: values.reason.trim(), version, idempotencyKey: requestKey.current })
      message.success(mode === 'close' ? '服务已关闭，已转入异常结业班' : '服务已恢复，原任务保持已取消')
      setMode(undefined); await onChanged(); await load()
    } catch (e) { message.error(errorMessage(e)) }
    finally { submitLock.current = false; setBusy(false) }
  }
  const actions: ToolbarAction[] = []
  if (detail?.availableActions.includes('CLOSE_SERVICE') && hasPermission(permissions, 'zsjos:student-service:close')) actions.push({ key: 'student-close-service', label: '关闭服务', icon: <PoweroffOutlined />, danger: true, disabled: busy, onClick: () => open('close') })
  if (detail?.availableActions.includes('RESTORE_SERVICE') && hasPermission(permissions, 'zsjos:student-service:restore')) actions.push({ key: 'student-restore-service', label: '恢复服务', icon: <RollbackOutlined />, disabled: busy, onClick: () => open('restore') })
  return <>
    {loading && <Skeleton active paragraph={{ rows: 1 }} />}
    {!!error && <Alert type="error" showIcon title="服务状态加载失败" description={error} action={<Button onClick={() => void load()}>重试</Button>} />}
    {detail?.closed && <Alert type="warning" showIcon title="已关闭 · 异常结业" description="该课程服务的教学信息只读。恢复后不会重新启用已取消的任务或审批。" />}
    {detail && !!detail.history.length && <Collapse size="small" items={[{ key: 'history', label: '关闭与恢复记录', children: <Timeline items={detail.history.map((event, index) => ({ key: index, children: <><strong>{event.action === 'close' ? '关闭服务' : '恢复服务'}</strong> · {event.operatorName || '人员姓名未记录'} · {event.occurredAt}<div>{event.reason}</div><div>{event.targetClassName} · {event.targetOwnerName}</div></> }))} /> }]} />}
    {children ? children(actions) : actions.length > 0 && <OverflowToolbar actions={actions} />}
    <Modal title={mode === 'close' ? '关闭服务' : '恢复服务'} open={!!mode} onCancel={dismiss} onOk={() => void submit()} confirmLoading={busy} okText="确认" cancelText="取消" closable={!busy} maskClosable={!busy} keyboard={!busy} cancelButtonProps={{ disabled: busy }}
      okButtonProps={{ danger: mode === 'close', disabled: mode === 'restore' && (targetLoading || !!targetError || !targets.length) }} destroyOnHidden>
      <Form form={form} layout="vertical" disabled={busy}>
        <Alert showIcon type="warning" title={mode === 'close' ? '关闭后转入本人异常结业班，取消当前教学待办、联系延期和教学调班审批，教学信息仅可查看。' : '恢复到所选班级并跟随该班主任。原待办和审批不会重新启用，可按原阶段主动补办或记录联系。'} />
        {mode === 'restore' && <>
          {targetError && <Alert type="error" title="恢复班级加载失败" description={targetError} action={<Button onClick={() => void loadTargets()}>重试</Button>} />}
          {!targetLoading && !targetError && !targets.length && <Empty description="暂无可恢复的服务中班级，请先配置有效班级" />}
          <Form.Item name="targetClassId" label="恢复班级" rules={[{ required: true, message: '请选择有效的服务中班级' }]}>
            <Select loading={targetLoading} showSearch optionFilterProp="label" placeholder="原班不可用时，请另选班级" options={targets.map(row => ({ value: row.id, label: `${row.className} · ${row.classNo} · ${row.homeroomUserName || '班主任姓名未记录'}` }))} />
          </Form.Item>
        </>}
        <Form.Item name="reason" label={mode === 'close' ? '关闭原因' : '恢复原因'} rules={[{ required: true, whitespace: true, max: 500, message: '请填写原因（最多 500 字）' }]}><Input.TextArea rows={3} maxLength={500} showCount /></Form.Item>
      </Form>
    </Modal>
  </>
}
