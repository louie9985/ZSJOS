import { useEffect, useRef, useState } from 'react'
import { Alert, Button, Empty, Modal, Popconfirm, Space, Spin, Tag, Typography } from 'antd'
import StudentInfoQrCard from './StudentInfoQrCard'
import ResourceLink from './ResourceLink'
import { ReloadOutlined, StopOutlined } from '@ant-design/icons'
import { studentInfoApi, studentInfoError, studentInfoStatus, type StudentInfoLink } from '../services/studentInfo'
import { formatTimestamp } from '../services/time'

export type StudentInfoLinkClient = Pick<typeof studentInfoApi, 'generate' | 'link' | 'regenerate' | 'revoke'>
export default function StudentInfoLinkModal({ caseId, version, mode, onClose, onChanged, client = studentInfoApi }: {
  caseId: number; version: number; mode: 'generate' | 'view'; onClose: () => void; onChanged?: () => void
  client?: StudentInfoLinkClient
}) {
  const [data, setData] = useState<StudentInfoLink | null>(null)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const requestSequence = useRef(0)
  const generationCommand = useRef<{ caseId: number; version: number; idempotencyKey: string } | null>(null)
  const load = async () => {
    const sequence = ++requestSequence.current
    setBusy(true); setError('')
    try {
      // Effect replay and retries belong to the same command, even after the parent refreshes its version.
      if (mode === 'generate' && generationCommand.current?.caseId !== caseId) {
        generationCommand.current = { caseId, version, idempotencyKey: crypto.randomUUID() }
      }
      const result = mode === 'generate' ? await client.generate(caseId, generationCommand.current!) : await client.link(caseId)
      if (sequence === requestSequence.current) setData(result)
      if (mode === 'generate') onChanged?.()
    }
    catch (e) { if (sequence === requestSequence.current) setError(studentInfoError(e)) }
    finally { if (sequence === requestSequence.current) setBusy(false) }
  }
  useEffect(() => { void load(); return () => { requestSequence.current++ } }, [caseId, mode])
  const mutate = async (revoke: boolean) => {
    if (!data) return
    requestSequence.current++
    setBusy(true); setError('')
    try {
      const command = { version: data.registrationVersion, formId: data.formId, idempotencyKey: crypto.randomUUID() }
      if (revoke) setData(await client.revoke(caseId, command))
      else setData(await client.regenerate(caseId, command))
    } catch (e) { setError(studentInfoError(e)) }
    finally { setBusy(false); onChanged?.() }
  }
  return <Modal title="信息收集表" open onCancel={onClose} footer={<Button onClick={onClose}>关闭</Button>} width={480}>
    {error && <Alert type="error" title={error} action={<Button onClick={() => void load()}>重试</Button>} />}
    {busy ? <Spin /> : data ? <Space orientation="vertical" style={{ width: '100%' }}>
      <Tag>{studentInfoStatus[data.status]}</Tag>
      <Typography.Text>生成时间：{formatTimestamp(data.createdAt)}</Typography.Text>
      <Typography.Text>有效期至：{formatTimestamp(data.expiresAt)}</Typography.Text>
      {data.url && <>
        <StudentInfoQrCard url={data.url} onError={setError} />
        <ResourceLink href={data.url} title="学员报名信息收集表" variant="resource" />
      </>}
      <Space wrap>
        {data.canRegenerate && <Popconfirm title="重新生成后，旧链接立即失效" onConfirm={() => mutate(false)}><Button icon={<ReloadOutlined />}>重新生成链接</Button></Popconfirm>}
        {data.canRevoke && <Popconfirm title="撤销此链接？" onConfirm={() => mutate(true)}><Button danger icon={<StopOutlined />}>撤销链接</Button></Popconfirm>}
      </Space>
    </Space> : !error && <Empty description="尚未生成信息收集表" />}
  </Modal>
}
