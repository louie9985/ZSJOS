import { createIdempotencyKey } from '../services/idempotency'
import { Alert, Button, Checkbox, Form, Input, Select, Space, Spin, Typography } from 'antd'
import { useEffect, useRef, useState } from 'react'
import { studentEntry, type EntryOptions, type EntryResult, type EntryType, type StudentEntry } from '../services/studentEntry'

export default function StudentEntryForm({ type, classId, onSaved, onBusy }: { type: EntryType; classId?: number; onSaved: (result: EntryResult) => void; onBusy?: (busy: boolean) => void }) {
  const [form] = Form.useForm<StudentEntry>()
  const [options, setOptions] = useState<EntryOptions>()
  const [loading, setLoading] = useState(true), [busy, setBusy] = useState(false), [error, setError] = useState('')
  const pending = useRef(false)
  const command = useRef<{ intent: string; payload: StudentEntry } | undefined>(undefined)
  const selectedClassId = Form.useWatch('classId', form)
  const selectedClass = options?.classes.find(c => c.id === selectedClassId)
  const [check, setCheck] = useState<Awaited<ReturnType<typeof studentEntry.check>>>()
  const [confirmReuse, setConfirmReuse] = useState(false)
  const active = useRef(true)
  const loadVersion = useRef(0)
  const load = async () => {
    const version = ++loadVersion.current; setLoading(true); setError('')
    try { const value = await studentEntry.options(); if (active.current && version === loadVersion.current) setOptions(value) }
    catch (e) { if (active.current && version === loadVersion.current) setError(e instanceof Error ? e.message : '录入选项加载失败') }
    finally { if (active.current && version === loadVersion.current) setLoading(false) }
  }
  useEffect(() => { active.current = true; void load(); return () => { active.current = false; loadVersion.current++ } }, [])
  const submit = async () => {
    if (pending.current || !options) return
    const values = await form.validateFields().catch(() => undefined)
    if (!values || pending.current) return
    pending.current = true; setBusy(true); onBusy?.(true); setError('')
    try {
      const contacts = { mobile: values.mobile?.trim() || undefined, wechatId: values.wechatId?.trim() || undefined }
      const intentValues = { ...contacts, name: values.name.trim(), entryType: type, skuRef: values.skuRef,
        directorUserId: type === 'media' ? values.directorUserId : undefined,
        teachingDelivery: type === 'teaching' || !!values.teachingDelivery, classId: type === 'teaching' ? values.classId : undefined }
      const intent = JSON.stringify(intentValues)
      // The previous create may have committed before its response was lost. Re-checking
      // contacts would turn "new" into "reuse" and incorrectly create another service.
      if (command.current?.intent === intent) {
        onSaved(await studentEntry.create(command.current.payload))
        return
      }
      const result = await studentEntry.check(contacts)
      setCheck(result)
      if (result.status === 'occupied' || result.status === 'conflict') { setError('联系方式已被占用或存在冲突，请由有权人员核实'); return }
      if (result.status === 'reuse' && (!confirmReuse || check?.personId !== result.personId)) { setConfirmReuse(false); return }
      const payload = { ...intentValues, reusePersonId: result.status === 'reuse' ? result.personId : undefined,
        confirmReuse: result.status === 'reuse', idempotencyKey: createIdempotencyKey() }
      command.current = { intent, payload }
      const created = await studentEntry.create(payload)
      onSaved(created)
    } catch (e) {
      // Business rejection rolls back the command; a transport failure has an unknown result.
      if (e && typeof e === 'object' && 'code' in e && Number(e.code) >= 1_000_000) command.current = undefined
      setError(e instanceof Error ? e.message : '添加学员失败，请重试')
    }
    finally { pending.current = false; setBusy(false); onBusy?.(false) }
  }
  if (loading) return <Spin tip="正在加载录入选项"><div style={{ minHeight: 120 }} /></Spin>
  if (!options) return <Alert type="error" showIcon message={error || '录入选项不可用'} action={<Button onClick={() => void load()}>重试</Button>} />
  const contactRule = { validator: async () => { if (!form.getFieldValue('mobile')?.trim() && !form.getFieldValue('wechatId')?.trim()) throw new Error('手机号或微信号至少填写一项') } }
  return <Form form={form} layout="vertical" initialValues={{ teachingDelivery: false, classId }} disabled={busy}
    onValuesChange={() => { setCheck(undefined); setConfirmReuse(false) }} onFinish={() => void submit()}>
    <Form.Item name="name" label="学员姓名" rules={[{ required: true, whitespace: true, message: '请填写学员姓名' }]}><Input maxLength={100} /></Form.Item>
    <Form.Item name="mobile" label="手机号" rules={[contactRule]}><Input maxLength={32} /></Form.Item>
    <Form.Item name="wechatId" label="微信号" rules={[contactRule]}><Input maxLength={64} /></Form.Item>
    <Form.Item name="skuRef" label="课程 SKU（选填）" extra="未选择时保留为空，不生成成交订单。"><Select styles={{ popup: { root: { maxWidth: 'calc(100vw - 24px)' } } }} allowClear showSearch optionFilterProp="label" placeholder="未填写课程" options={options.courses.map(c => ({ value: c.skuRef, label: c.name }))} notFoundContent="暂无可选课程" /></Form.Item>
    {type === 'media' ? <>
      <Form.Item name="directorUserId" label="责任编导" rules={[{ required: true, message: '请选择责任编导' }]}><Select styles={{ popup: { root: { maxWidth: 'calc(100vw - 24px)' } } }} showSearch optionFilterProp="label" options={options.directors.map(d => ({ value: d.id, label: d.name }))} notFoundContent="暂无有效编导，请联系管理员维护人员岗位" /></Form.Item>
      <Form.Item name="teachingDelivery" valuePropName="checked"><Checkbox>同时建立教学服务，加入待分班</Checkbox></Form.Item>
    </> : <>
      <Form.Item name="classId" label="班级" rules={[{ required: true, message: '请选择班级或待分班' }]}><Select styles={{ popup: { root: { maxWidth: 'calc(100vw - 24px)' } } }} showSearch optionFilterProp="label" options={options.classes.map(c => ({ value: c.id, label: `${c.className}${c.homeroomUserName ? ` · ${c.homeroomUserName}` : ''}` }))} notFoundContent="暂无可入班班级，请联系管理员" /></Form.Item>
      <Typography.Paragraph>教学负责人：{selectedClass?.homeroomUserName || '未分配'}；正式班由班主任接收后开始首联。</Typography.Paragraph>
    </>}
    {check?.status === 'reuse' && <Alert type="info" showIcon message={`已有人员：${check.name || '姓名未提供'}（${check.personNo || '编号未提供'}）`} description={<>
      <div>当前可见服务 {check.services.length} 条；本次将另建服务，原资料及进度保持原值。</div>
      {check.services.map(s => <div key={s.serviceRelationId}>{s.studentName || '姓名未记录'} · {s.statusLabel}</div>)}
      <Checkbox checked={confirmReuse} onChange={e => setConfirmReuse(e.target.checked)}>确认复用此人员并新增服务</Checkbox>
    </>} />}
    {error && <Alert style={{ marginTop: 12 }} type="error" showIcon message={error} action={<Button disabled={busy} onClick={() => void load()}>刷新选项</Button>} />}
    <Space style={{ marginTop: 16 }}><Button type="primary" htmlType="submit" loading={busy}>添加学员</Button></Space>
  </Form>
}
