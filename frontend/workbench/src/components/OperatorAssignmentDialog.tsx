import { Alert, App, Button, Empty, Form, Input, Modal, Select, Skeleton } from 'antd'
import { useEffect, useRef, useState } from 'react'
import { api, ApiError, type StudentContactContext, type StudyPlanner } from '../services/api'

export function operatorReasonRequired(context: Pick<StudentContactContext, 'operatorUserId' | 'operatorAssignmentConflict'> | undefined, userId?: number, serverRequired = false) {
  return serverRequired || Boolean(context?.operatorAssignmentConflict)
    || (context?.operatorUserId != null && userId != null && context.operatorUserId !== userId)
}

type Values = { userId: number; correctionReason?: string }

// Mount per assignment session so late requests cannot update another student's form.
export default function OperatorAssignmentDialog({ relationId, onCancel, onSaved }: {
  relationId: number; onCancel: () => void; onSaved: () => Promise<void>
}) {
  const { message } = App.useApp()
  const [form] = Form.useForm<Values>()
  const userId = Form.useWatch('userId', form)
  const [context, setContext] = useState<StudentContactContext>()
  const [candidates, setCandidates] = useState<StudyPlanner[]>([])
  const [loading, setLoading] = useState(true), [saving, setSaving] = useState(false)
  const [loadError, setLoadError] = useState(''), [submitError, setSubmitError] = useState('')
  const [serverRequired, setServerRequired] = useState(false), [reloadRequired, setReloadRequired] = useState(false)
  const alive = useRef(false), run = useRef(0), lock = useRef(false)
  const pending = useRef<{ fingerprint: string; key: string } | undefined>(undefined)
  const required = operatorReasonRequired(context, userId, serverRequired)
  const allowed = context?.availableActions.some(action => action === 'ASSIGN_OPERATOR' || action === 'DIRECTOR_OPERATOR_ASSIGN')
  const blocked = loading || Boolean(loadError) || !allowed || !candidates.length || reloadRequired
  const errorText = (cause: unknown) => cause instanceof Error ? cause.message : '请求失败，请重试'

  const load = async () => {
    const current = ++run.current
    setLoading(true); setLoadError(''); setContext(undefined); setCandidates([])
    try {
      const [nextContext, nextCandidates] = await Promise.all([
        api.studentContactContext(relationId), api.studentCollaboratorCandidates(relationId, 'operator'),
      ])
      if (!alive.current || current !== run.current) return
      setContext(nextContext); setCandidates(nextCandidates); setReloadRequired(false)
      const selected = form.getFieldValue('userId')
      if (selected != null && !nextCandidates.some(item => item.id === selected)) {
        form.setFields([{ name: 'userId', errors: ['该运营已不可用，请重新选择'] }])
      }
    } catch (cause) {
      if (alive.current && current === run.current) setLoadError(errorText(cause))
    } finally {
      if (alive.current && current === run.current) setLoading(false)
    }
  }
  useEffect(() => {
    alive.current = true
    void load()
    return () => { alive.current = false; run.current++ }
    // The parent keys this component by the selected service and closes it between sessions.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [relationId])

  const submit = async () => {
    if (lock.current || blocked || !context) return
    lock.current = true
    try {
      const values = await form.validateFields()
      if (!alive.current) return
      setSaving(true); setSubmitError('')
      const body = { collaboratorType: 'operator' as const, userId: values.userId, version: context.version,
        correctionReason: values.correctionReason?.trim() || undefined }
      const fingerprint = JSON.stringify(body)
      if (pending.current?.fingerprint !== fingerprint) pending.current = { fingerprint, key: crypto.randomUUID() }
      await api.studentAssignCollaborator(relationId, { ...body, idempotencyKey: pending.current.key })
      if (!alive.current) return
      message.success('运营指派已保存')
      await onSaved()
    } catch (cause) {
      if (!alive.current || (cause as { errorFields?: unknown })?.errorFields) return
      if (cause instanceof ApiError && cause.code === 1900010035) {
        setServerRequired(true)
        form.setFields([{ name: 'correctionReason', errors: ['变更已有运营时请填写原因'] }])
        form.scrollToField('correctionReason', { focus: true })
      } else if (cause instanceof ApiError && [1900010024, 1900010033].includes(cause.code)) {
        setReloadRequired(true)
        setSubmitError(cause.code === 1900010024 ? '学员服务已被修改，请刷新运营信息后重新确认提交。' : '所选运营已不可用，请刷新候选后重新选择。')
      } else {
        setSubmitError(cause instanceof ApiError && [403, 1900010037].includes(cause.code)
          ? '无权为该学员指派运营，请联系管理员确认权限。' : errorText(cause))
      }
    } finally {
      lock.current = false
      if (alive.current) setSaving(false)
    }
  }

  return <Modal open title="指派运营" mask={{ closable: false }} closable={!saving} keyboard={!saving}
    onCancel={onCancel} onOk={() => void submit()} okText="确认指派" cancelText="取消" confirmLoading={saving}
    okButtonProps={{ disabled: blocked }} cancelButtonProps={{ disabled: saving }}>
    <Alert type="info" showIcon message="本次指派将统一该学员有效且已接收服务的运营，并同步相关定位卡和账号的运营归属。" />
    {loading && <Skeleton active paragraph={{ rows: 2 }} />}
    {loadError && <Alert type="error" showIcon message={loadError} action={<Button onClick={() => void load()}>重试加载</Button>} />}
    {!loading && context && !allowed && <Alert type="warning" showIcon message="当前无权为该学员指派运营" />}
    {!loading && !loadError && allowed && !candidates.length && <Empty description="暂无可用运营，请联系管理员配置人员关系" />}
    {context?.operatorAssignmentConflict && <Alert type="warning" showIcon message="学员运营归属不一致，本次将统一归属，请填写变更原因。" />}
    {submitError && <Alert type="error" showIcon message={submitError} action={reloadRequired ? <Button disabled={loading} onClick={() => void load()}>刷新运营信息</Button> : undefined} />}
    <Form form={form} layout="vertical" disabled={loading || saving || Boolean(loadError) || !allowed}>
      <Form.Item name="userId" label="运营负责人" rules={[{ required: true, message: '请选择运营负责人' },
        { validator: (_, value) => value == null || candidates.some(item => item.id === value) ? Promise.resolve() : Promise.reject(new Error('该运营已不可用，请重新选择')) }]}>
        <Select showSearch optionFilterProp="label" placeholder="请选择运营负责人" options={candidates.map(user => ({ value: user.id, label: user.nickname }))} />
      </Form.Item>
      <Form.Item name="correctionReason" label="变更原因" extra={required ? '更换已有运营必须填写原因' : '首次指派或保持原运营可不填写'}
        rules={[{ required, whitespace: true, message: '变更已有运营时请填写原因' }, { max: 500, message: '变更原因不能超过 500 字' }]}>
        <Input.TextArea rows={3} maxLength={500} showCount />
      </Form.Item>
    </Form>
  </Modal>
}
