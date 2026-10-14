import { useState } from 'react'
import { Alert, Button, Input, Modal, message } from 'antd'
import { api, type SalesOrder } from '../services/api'
import { useSubmissionGuard } from '../services/submissionGuard'

export default function SalesOrderVoidButton({ order, onSuccess }: { order: SalesOrder; onSuccess: () => void }) {
  const [open, setOpen] = useState(false)
  const [reason, setReason] = useState('')
  const [error, setError] = useState('')
  const { submitting, run, resetIntent } = useSubmissionGuard()
  if (!order.canVoid || order.status !== 'effective') return null
  const submit = async () => {
    if (!reason.trim()) { setError('请填写作废原因'); return }
    setError('')
    try {
      await run(async ({ idempotencyKey, complete }) => {
        await api.voidSalesOrder(order.id, { reason: reason.trim(), orderVersion: order.version, idempotencyKey })
        complete()
        setOpen(false)
        message.success('订单已作废，请刷新相关业绩报表')
        onSuccess()
      })
    } catch (failure) { setError(failure instanceof Error ? failure.message : '作废失败，请重试') }
  }
  return <>
    <Button danger onClick={() => { setReason(''); setError(''); resetIntent(); setOpen(true) }}>作废订单</Button>
    <Modal title={`作废订单 ${order.orderNo}`} open={open} okText="确认作废" okButtonProps={{ danger: true }}
      confirmLoading={submitting} closable={!submitting} maskClosable={!submitting}
      cancelButtonProps={{ disabled: submitting }} onCancel={() => setOpen(false)} onOk={() => void submit()}>
      <Alert type="warning" showIcon title="作废后原订单不再计入业绩，关联返现、礼品采购和资金分配失效。"
        description="已收付款和提现历史保留，不自动退款或追回款项。原报名及服务不变，销售可按资格重新录单，新单重新审批并生成报名履约任务。"/>
      <Input.TextArea aria-label="作废原因" placeholder="请填写作废原因" value={reason} maxLength={1000} showCount rows={4}
        disabled={submitting} onChange={event => { setReason(event.target.value); setError(''); resetIntent() }}/>
      {error && <Alert type="error" showIcon title={error}/>}
    </Modal>
  </>
}
