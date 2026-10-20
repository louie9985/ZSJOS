import { useEffect, useRef, useState } from 'react'
import { Alert, Button, Descriptions, Empty, Space, Spin, Tag, Image } from 'antd'
import { DownloadOutlined, EyeOutlined, ReloadOutlined } from '@ant-design/icons'
import { studentInfoApi, studentInfoError, studentInfoStatus, type StudentInfoDetail } from '../services/studentInfo'
import { formatTimestamp } from '../services/time'

export default function StudentInfoPanel({ caseId }: { caseId: number }) {
  const [data, setData] = useState<StudentInfoDetail>()
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [full, setFull] = useState(false)
  const [imageUrls, setImageUrls] = useState<Record<number, string>>({})
  const generation = useRef(0)
  const load = async (sensitive = false) => {
    const current = ++generation.current
    setBusy(true); setError('')
    try { const value = await studentInfoApi.detail(caseId, sensitive); if (current === generation.current) { setData(value); setFull(sensitive) } }
    catch (e) { if (current === generation.current) { setError(studentInfoError(e)); setData(undefined) } }
    finally { if (current === generation.current) setBusy(false) }
  }
  useEffect(() => { setData(undefined); setFull(false); setImageUrls({}); void load(); return () => { generation.current++ } }, [caseId])
  useEffect(() => () => { Object.values(imageUrls).forEach(URL.revokeObjectURL) }, [imageUrls])
  const loadImages = async () => {
    if (!data) return; setBusy(true)
    const current = generation.current
    try { const urls: Record<number, string> = Object.fromEntries(await Promise.all(data.images.map(async image => [image.id, await studentInfoApi.image(caseId, image)] as const))); if (current === generation.current) setImageUrls(urls); else Object.values(urls).forEach(URL.revokeObjectURL) }
    catch (e) { if (current === generation.current) setError(studentInfoError(e)) } finally { if (current === generation.current) setBusy(false) }
  }
  const download = async () => {
    setBusy(true)
    try { await studentInfoApi.export(caseId) } catch (e) { setError(studentInfoError(e)) }
    finally { setBusy(false) }
  }
  return <section className="lead-detail-tab-content">
    <Space wrap style={{ marginBottom: 16 }}>
      <Tag>{studentInfoStatus[data?.status || 'NONE']}</Tag>
      <Button icon={<ReloadOutlined />} loading={busy} onClick={() => void load()}>刷新</Button>
      {data?.status === 'SUBMITTED' && data.canReadSensitive && !full && <Button icon={<EyeOutlined />} disabled={busy} onClick={() => void load(true)}>查看完整敏感字段</Button>}
      {data?.status === 'SUBMITTED' && data.canExport && <Button icon={<DownloadOutlined />} disabled={busy} onClick={() => void download()}>导出</Button>}
      {data?.status === 'SUBMITTED' && data.canReadSensitive && data.images?.length > 0 && <Button disabled={busy} onClick={() => void loadImages()}>查看材料附件</Button>}
    </Space>
    {error && <Alert type="error" title={error} action={<Button onClick={() => void load()}>重试</Button>} />}
    {busy ? <Spin /> : data?.status !== 'SUBMITTED' ? !error && <Empty description="暂无已提交的学员信息" /> : <Descriptions column={{ xs: 1, sm: 2 }} items={[
      { key: 'submitted', label: '提交时间', children: formatTimestamp(data.submittedAt) },
      { key: 'version', label: '表单版本', children: `V${data.configVersion}` },
      ...data.fields.map(f => ({ key: f.key, label: f.label, span: f.type === 'textarea' ? 2 : 1,
        children: <div style={{ whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }}>{f.type === 'images' ? <Image.PreviewGroup>{data.images?.filter(image => image.fieldKey === f.key).map(image => <span key={image.id}>{imageUrls[image.id] ? <span>{image.contentType.startsWith("image/") ? <Image width={80} src={imageUrls[image.id]} alt={f.label + image.side} /> : <a href={imageUrls[image.id]} download={image.name}>{image.name}</a>}</span> : image.name}</span>)}</Image.PreviewGroup> : data.values[f.key] || '-'}{f.note && <div style={{ color: 'var(--crm-text-secondary)' }}>{f.note}</div>}</div> })),
    ]} />}
  </section>
}
