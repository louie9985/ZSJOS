import { Alert, Button, Checkbox, Divider, Modal, Space, Typography } from 'antd'
import type { OrderProfile, OrderProfileField } from '../services/api'

export const profileFields: Array<{ key: OrderProfileField; label: string }> = [
  { key: 'name', label: '姓名' }, { key: 'mobile', label: '手机号' }, { key: 'wechatId', label: '微信号' },
]
export function profileDifferences(profile: OrderProfile, incoming: Record<OrderProfileField, string>) {
  return profile.personId ? profileFields.filter(({ key }) => (profile[key]?.trim() || '') !== incoming[key].trim()) : []
}

export default function OrderProfileConfirmModal({ open, profile, incoming, fields, onFieldsChange, onCancel, onConfirm, loading }: {
  open: boolean; profile?: OrderProfile; incoming: Record<OrderProfileField, string>; fields: OrderProfileField[]
  onFieldsChange: (fields: OrderProfileField[]) => void; onCancel: () => void; onConfirm: () => void; loading: boolean
}) {
  const differences = profile ? profileDifferences(profile, incoming) : []
  const replacements = differences.filter(({ key }) => profile?.[key]?.trim())
  const updates = differences.filter(({ key }) => !profile?.[key]?.trim() || fields.includes(key))
  return <Modal open={open} title="确认提交订单" width={640} onCancel={onCancel} mask={{ closable: false }}
    closable={!loading} keyboard={!loading} footer={<Space wrap><Button disabled={loading} onClick={onCancel}>返回修改</Button>
      <Button type="primary" loading={loading} disabled={!profile} onClick={onConfirm}>确认提交审批</Button></Space>}>
    <Typography.Paragraph>{differences.length ? '以下信息与客户档案不同，请核对要更新的项目。'
      : profile?.personId ? '本次学员信息与客户档案一致，无需更新。' : '将使用本次填写的信息建立客户档案。'}</Typography.Paragraph>
    {differences.map(({ key, label }) => <div key={key}>
      <Space direction="vertical" style={{ width: '100%', overflowWrap: 'anywhere' }}>
        <Typography.Text strong>{label}</Typography.Text>
        <Typography.Text type="secondary">档案现有：{profile?.[key] || '未填写'}</Typography.Text>
        <Typography.Text>本次订单：{incoming[key]}</Typography.Text>
        {profile?.[key]?.trim() ? <Checkbox checked={fields.includes(key)} disabled={loading}
          onChange={e => onFieldsChange(e.target.checked ? [...fields, key] : fields.filter(f => f !== key))}>
          用本次订单的{label}更新档案</Checkbox> : <Typography.Text type="secondary">空项将在提交成功后自动补齐</Typography.Text>}
      </Space><Divider style={{ margin: '12px 0' }}/>
    </div>)}
    {!!replacements.length && <Space wrap style={{ marginBottom: 16 }}><Button size="small" disabled={loading} onClick={() => onFieldsChange(replacements.map(f => f.key))}>全部更新档案</Button>
      <Button size="small" disabled={loading} onClick={() => onFieldsChange([])}>保留已有值</Button></Space>}
    <Typography.Paragraph type="secondary">订单保存本次填写的信息；原客资及历史订单保持原样。</Typography.Paragraph>
    <Alert type="info" showIcon title={!profile?.personId ? '提交时建立客户档案' : updates.length
      ? `提交成功后，档案将更新：${updates.map(f => f.label).join('、')}。` : '提交成功后，档案保持原样。'}/>
  </Modal>
}
