import { useEffect, useState } from 'react'
import { Alert, Button, Descriptions, Empty, Space, Spin, Tag } from 'antd'
import { DownloadOutlined, EyeOutlined, ReloadOutlined } from '@ant-design/icons'
import { studentInfoApi, studentInfoError, studentInfoStatus, type StudentInfoDetail } from '../services/studentInfo'
import { formatTimestamp } from '../services/time'

export default function StudentInfoPanel({ leadId }: { leadId: number }) {
  const [data, setData] = useState<StudentInfoDetail>()
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [full, setFull] = useState(false)
  const load = async (sensitive = false) => {
    setBusy(true); setError('')
    try { setData(await studentInfoApi.detail(leadId, sensitive)); setFull(sensitive) }
    catch (e) { setError(studentInfoError(e)); setData(undefined) }
    finally { setBusy(false) }
  }
  useEffect(() => { setData(undefined); setFull(false); void load() }, [leadId])
  const download = async () => {
    setBusy(true)
    try { await studentInfoApi.export(leadId) } catch (e) { setError(studentInfoError(e)) }
    finally { setBusy(false) }
  }
  return <section className="lead-detail-tab-content">
    <Space wrap style={{ marginBottom: 16 }}>
      <Tag>{studentInfoStatus[data?.status || 'NONE']}</Tag>
      <Button icon={<ReloadOutlined />} loading={busy} onClick={() => void load()}>刷新</Button>
      {data?.status === 'SUBMITTED' && data.canReadSensitive && !full && <Button icon={<EyeOutlined />} disabled={busy} onClick={() => void load(true)}>查看完整敏感字段</Button>}
      {data?.status === 'SUBMITTED' && data.canExport && <Button icon={<DownloadOutlined />} disabled={busy} onClick={() => void download()}>导出</Button>}
    </Space>
    {error && <Alert type="error" title={error} action={<Button onClick={() => void load()}>重试</Button>} />}
    {busy ? <Spin /> : data?.status !== 'SUBMITTED' ? !error && <Empty description="暂无已提交的学员信息" /> : <Descriptions column={{ xs: 1, sm: 2 }} items={[
      { key: 'submitted', label: '提交时间', children: formatTimestamp(data.submittedAt) },
      { key: 'version', label: '表单版本', children: `V${data.configVersion}` },
      ...data.fields.map(f => ({ key: f.key, label: f.label, span: f.type === 'textarea' ? 2 : 1,
        children: <div style={{ whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }}>{data.values[f.key] || '-'}{f.note && <div style={{ color: 'var(--crm-text-secondary)' }}>{f.note}</div>}</div> })),
    ]} />}
  </section>
}
