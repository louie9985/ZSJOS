import { useEffect, useRef, useState } from 'react'
import { Alert, Button, Empty, Modal, Popconfirm, QRCode, Space, Spin, Tag, Typography } from 'antd'
import { CopyOutlined, ReloadOutlined, StopOutlined } from '@ant-design/icons'
import { studentInfoApi, studentInfoError, studentInfoStatus, type StudentInfoLink } from '../services/studentInfo'
import { formatTimestamp } from '../services/time'

export default function StudentInfoLinkModal({ leadId, mode, onClose }: {
  leadId: number; mode: 'generate' | 'view'; onClose: () => void
}) {
  const [data, setData] = useState<StudentInfoLink | null>(null)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const requestSequence = useRef(0)
  const load = async () => {
    const sequence = ++requestSequence.current
    setBusy(true); setError('')
    try {
      const result = mode === 'generate' ? await studentInfoApi.generate(leadId) : await studentInfoApi.link(leadId)
      if (sequence === requestSequence.current) setData(result)
    }
    catch (e) { if (sequence === requestSequence.current) setError(studentInfoError(e)) }
    finally { if (sequence === requestSequence.current) setBusy(false) }
  }
  useEffect(() => { void load(); return () => { requestSequence.current++ } }, [leadId, mode])
  const mutate = async (revoke: boolean) => {
    if (!data) return
    requestSequence.current++
    setBusy(true); setError('')
    try {
      if (revoke) { await studentInfoApi.revoke(leadId, data.formId); setData({ ...data, status: 'REVOKED', url: undefined, canRevoke: false }) }
      else setData(await studentInfoApi.regenerate(leadId, data.formId))
    } catch (e) { setError(studentInfoError(e)) }
    finally { setBusy(false) }
  }
  return <Modal title="信息收集表" open onCancel={onClose} footer={<Button onClick={onClose}>关闭</Button>} width={480}>
    {error && <Alert type="error" title={error} action={<Button onClick={() => void load()}>重试</Button>} />}
    {busy ? <Spin /> : data ? <Space orientation="vertical" style={{ width: '100%' }}>
      <Tag>{studentInfoStatus[data.status]}</Tag>
      <Typography.Text>生成时间：{formatTimestamp(data.createdAt)}</Typography.Text>
      <Typography.Text>有效期至：{formatTimestamp(data.expiresAt)}</Typography.Text>
      {data.url && <>
        <QRCode value={data.url} size={200} />
        <Typography.Paragraph style={{ overflowWrap: 'anywhere' }}>{data.url}</Typography.Paragraph>
        <Button icon={<CopyOutlined />} onClick={() => void navigator.clipboard.writeText(data.url!).catch(() => setError('复制失败，请选中链接复制'))}>复制链接</Button>
      </>}
      <Space wrap>
        {data.canRegenerate && <Popconfirm title="重新生成后，旧链接立即失效" onConfirm={() => mutate(false)}><Button icon={<ReloadOutlined />}>重新生成链接</Button></Popconfirm>}
        {data.canRevoke && <Popconfirm title="撤销此链接？" onConfirm={() => mutate(true)}><Button danger icon={<StopOutlined />}>撤销链接</Button></Popconfirm>}
      </Space>
    </Space> : !error && <Empty description="尚未生成信息收集表" />}
  </Modal>
}
