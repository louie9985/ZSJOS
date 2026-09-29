import { useEffect, useRef, useState } from 'react'
import { Alert, App, Button, Descriptions, Form, Input, Modal } from 'antd'
import BusinessTable from './BusinessTable'
import { managementApi, type Cashback, type CashbackControlLog } from '../services/managementApi'
import { hasPermission } from '../services/managementAccess'
import { useFinanceFilterOptions } from '../hooks/useFinanceFilterOptions'
import { formatTimestamp } from '../services/time'
import { money } from '../pages/financeTableColumns'

export function CashbackControl({ row, permissions, onSuccess }: { row: Cashback; permissions: string[]; onSuccess: () => void }) {
  const action = row.status === 'blocked' ? 'unblock' : 'block'
  const eligible = ['pending_settlement', 'available', 'blocked'].includes(row.status)
  const allowed = eligible && hasPermission(permissions, 'zsjos:cashback:finance-query') && hasPermission(permissions, `zsjos:cashback:${action}`)
  const [open, setOpen] = useState(false), [saving, setSaving] = useState(false), [error, setError] = useState('')
  const [form] = Form.useForm<{ reason: string }>()
  const { message } = App.useApp()
  const busy = useRef(false)
  const options = useFinanceFilterOptions('cashback', open)
  const title = action === 'block' ? '禁止提现' : '恢复提现'
  const submit = async () => {
    if (busy.current) return
    const values = await form.validateFields().catch(() => undefined)
    if (!values || busy.current) return
    busy.current = true; setSaving(true); setError('')
    try {
      await managementApi.controlCashback(row.id, action, { version: row.version, reason: values.reason.trim() })
      setOpen(false); message.success(`${title}成功`); onSuccess()
    } catch (e) { setError(e instanceof Error ? e.message : '操作失败，请重试') }
    finally { busy.current = false; setSaving(false) }
  }
  if (!allowed) return null
  return <><Button type="link" danger={action === 'block'} onClick={() => { form.resetFields(); setError(''); setOpen(true) }}>{title}</Button>
    <Modal title={title} open={open} onOk={() => void submit()} onCancel={() => setOpen(false)} confirmLoading={saving} closable={!saving} maskClosable={!saving} keyboard={!saving} cancelButtonProps={{ disabled: saving }} destroyOnHidden>
      <Alert type="warning" showIcon title={action === 'block' ? '操作后，该笔返现不能发起提现，原返现金额保留。' : '恢复后按原结算条件判断是否可提现，不重新计算金额。'} />
      <Descriptions column={1} items={[
        { key: 'no', label: '返现编号', children: row.cashbackNo }, { key: 'beneficiary', label: '受益人', children: row.beneficiaryName || '历史归属信息缺失' },
        { key: 'amount', label: '返现金额', children: money(row.amount) }, { key: 'status', label: '当前状态', children: options.options('status').find(x => x.value === row.status)?.label || '状态暂不可用' }
      ]} />
      {options.error && <Alert type="error" title={options.error} action={<Button onClick={() => void options.reload()}>重试</Button>} />}
      {error && <Alert type="error" title={error} />}
      <Form form={form} layout="vertical"><Form.Item name="reason" label="操作原因" extra={action === 'block' ? '该原因将展示给兼职' : undefined} rules={[{ required: true, whitespace: true, message: '请填写操作原因' }, { max: 500, message: '原因最多500字' }]}><Input.TextArea rows={4} maxLength={500} showCount disabled={saving} /></Form.Item></Form>
    </Modal></>
}

export function CashbackControlHistory({ id, revision }: { id: number; revision: number }) {
  const [rows, setRows] = useState<CashbackControlLog[]>([]), [page, setPage] = useState(1), [total, setTotal] = useState(0), [loading, setLoading] = useState(false), [error, setError] = useState(''), [retry, setRetry] = useState(0)
  useEffect(() => { setPage(1) }, [id, revision])
  useEffect(() => {
    let active = true; setLoading(true); setError(''); setRows([])
    managementApi.cashbackControlHistory(id, page).then(result => { if (active) { setRows(result.list); setTotal(result.total) } }).catch(e => { if (active) setError(e instanceof Error ? e.message : '操作记录加载失败') }).finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [id, revision, page, retry])
  return <>{error && <Alert type="error" title={error} action={<Button onClick={() => setRetry(x => x + 1)}>重试</Button>} />}<BusinessTable<CashbackControlLog> tableKey="cashback-control-history" mode="compact" scroll={{ x: 600 }} rowKey="id" loading={loading} dataSource={rows} columns={[
    { title: '操作', dataIndex: 'action', render: (_, row) => row.action === 'block' ? '禁止提现' : '恢复提现' }, { title: '原因', dataIndex: 'reason' },
    { title: '操作人', dataIndex: 'operatorName' }, { title: '时间', dataIndex: 'occurredAt', render: (_, row) => formatTimestamp(row.occurredAt) }
  ]} pagination={{ current: page, total, pageSize: 10, onChange: setPage }} locale={{ emptyText: error || '暂无操作记录' }} /></>
}
