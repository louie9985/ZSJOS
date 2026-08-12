import { useCallback, useEffect, useState } from 'react'
import { Alert, Button, Empty, Form, Input, Modal, Select, Space, Spin, Table, Tabs, Tag, Typography, message } from 'antd'
import { ReloadOutlined } from '@ant-design/icons'
import { api, type AssignmentUser, type LeadQualificationException } from '../services/api'
import { LEAD_HANDLING_STAGE_LABELS } from '../constants'
import { formatTimestamp } from '../services/time'

type ExceptionType = 'suspended' | 'recycle_pending'
type Action = 'restore' | 'transfer' | 'recycle' | 'release'

export default function LeadQualificationExceptionPage() {
  const [type, setType] = useState<ExceptionType>('suspended')
  const [items, setItems] = useState<LeadQualificationException[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [selected, setSelected] = useState<LeadQualificationException>()
  const [action, setAction] = useState<Action>()
  const [reason, setReason] = useState('')
  const [salesUserId, setSalesUserId] = useState<number>()
  const [candidates, setCandidates] = useState<AssignmentUser[]>([])
  const [saving, setSaving] = useState(false)

  const load = useCallback(async () => {
    setLoading(true); setError('')
    try { setItems((await api.qualificationExceptionPage(type, { pageNo: 1, pageSize: 100 })).list) }
    catch (loadError) { setError(loadError instanceof Error ? loadError.message : '异常客资加载失败'); setItems([]) }
    finally { setLoading(false) }
  }, [type])

  useEffect(() => { void load() }, [load])

  const openAction = async (lead: LeadQualificationException, nextAction: Action) => {
    setSelected(lead); setAction(nextAction); setReason(''); setSalesUserId(undefined); setCandidates([])
    if (nextAction === 'transfer') {
      try { setCandidates(await api.leadTransferCandidates(lead.id)) }
      catch (loadError) { message.error(loadError instanceof Error ? loadError.message : '转派销售加载失败') }
    }
  }

  const submit = async () => {
    if (!selected || !action || !reason.trim()) { message.warning('请填写处置理由'); return }
    if (action === 'transfer' && !salesUserId) { message.warning('请选择目标销售'); return }
    setSaving(true)
    const command = { reason: reason.trim(), idempotencyKey: crypto.randomUUID() }
    try {
      if (action === 'restore') await api.restoreLead(selected.id, command)
      if (action === 'transfer') await api.transferLead(selected.id, { ...command, salesUserId: salesUserId! })
      if (action === 'recycle') await api.recycleLead(selected.id, command)
      if (action === 'release') await api.releaseLeadToClaimPool(selected.id, command)
      message.success('异常客资已处理')
      setAction(undefined); setSelected(undefined)
      await load()
    } finally { setSaving(false) }
  }

  return <section className="workspace-page">
    <div className="workspace-page-heading">
      <div><Typography.Title level={3}>异常客资</Typography.Title><Typography.Text type="secondary">处理判定超时挂起和已回收待重新分配的客资</Typography.Text></div>
      <Button icon={<ReloadOutlined/>} onClick={() => void load()}>刷新</Button>
    </div>
    <Tabs activeKey={type} onChange={key => setType(key as ExceptionType)} items={[
      { key: 'suspended', label: '挂起客资' },
      { key: 'recycle_pending', label: '回收待处理' }
    ]}/>
    {error && <Alert type="error" showIcon message={error} action={<Button size="small" onClick={() => void load()}>重试</Button>}/>} 
    <Spin spinning={loading}>
      <Table rowKey="id" dataSource={items} pagination={false} locale={{ emptyText: <Empty description="暂无异常客资"/> }} scroll={{ x: 860 }} columns={[
        { title: '客户', key: 'name', render: (_, row) => <div><strong>{row.submittedName}</strong><div>{row.submittedMobile || '无手机号'}</div></div> },
        { title: '阶段', dataIndex: 'handlingStage', render: value => <Tag color="warning">{LEAD_HANDLING_STAGE_LABELS[value] || value}</Tag> },
        { title: type === 'suspended' ? '当前销售' : '回收来源销售', key: 'owner', render: (_, row) => type === 'suspended' ? row.ownerUserName || `用户 #${row.ownerUserId}` : row.recycleSourceOwnerUserName || `用户 #${row.recycleSourceOwnerUserId}` },
        { title: '判定截止', dataIndex: 'qualificationDeadlineAt', render: value => formatTimestamp(value) },
        { title: '挂起时间', dataIndex: 'suspendedAt', render: value => formatTimestamp(value) },
        { title: '操作', key: 'actions', fixed: 'right', width: 250, render: (_, row) => <Space wrap>
          {type === 'suspended' && <Button size="small" onClick={() => void openAction(row, 'restore')}>恢复</Button>}
          <Button size="small" onClick={() => void openAction(row, 'transfer')}>转派</Button>
          {type === 'suspended' && <Button size="small" onClick={() => void openAction(row, 'recycle')}>回收</Button>}
          <Button size="small" type="primary" onClick={() => void openAction(row, 'release')}>释放</Button>
        </Space> }
      ]}/>
    </Spin>
    <Modal open={Boolean(action)} title={{ restore: '恢复原销售', transfer: '转派客资', recycle: '回收客资', release: '释放到抢单池' }[action || 'restore']} confirmLoading={saving} onOk={() => void submit()} onCancel={() => setAction(undefined)} okText="确认处理">
      <Space direction="vertical" size="middle" style={{ width: '100%' }}>
        {action === 'transfer' && <Form.Item label="目标销售" required><Select showSearch optionFilterProp="label" value={salesUserId} onChange={setSalesUserId} placeholder={candidates.length ? '选择目标销售' : '暂无可转派销售'} options={candidates.map(user => ({ value: user.id, label: `${user.nickname}${user.deptName ? ` · ${user.deptName}` : ''}` }))} style={{ width: '100%' }}/></Form.Item>}
        <Form.Item label="处置理由" required><Input.TextArea value={reason} onChange={event => setReason(event.target.value)} rows={4} maxLength={500} showCount placeholder="填写本次处置理由"/></Form.Item>
      </Space>
    </Modal>
  </section>
}
